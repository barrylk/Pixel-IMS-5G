package dev.bluehouse.enablevolte

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * "Reapply after reboot" for Shizuku mode (issues #6 and #26).
 *
 * Since Android 14 only system apps may make a CarrierConfig override persistent, so
 * Shizuku overrides are dropped on every reboot. With this on (the default), SIM Config
 * changes are saved per SIM exactly as Root mode saves them and reapplied once per boot,
 * as soon as Shizuku is available: from the boot worker when Shizuku starts on boot, or
 * the moment Shizuku connects later, for example after starting it by hand through
 * wireless debugging. The boot counter keeps it to once per boot, so opening the app
 * does not push the same profile again and restart IMS registration each time.
 */
object ShizukuBootReapply {
    private const val PREFS = "pixel_ims_boot"
    private const val KEY = "shizuku_reapply_on_boot"
    private const val KEY_APPLIED_BOOT = "shizuku_reapplied_boot"
    private const val WORK_NAME = "shizuku_carrier_config_reapply"
    private const val TAG = "ShizukuBootReapply"

    private val watching = AtomicBoolean(false)

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, enabled).apply()
    }

    /** Pure decision, kept apart from Android so it can be unit tested. */
    fun shouldReapply(enabled: Boolean, shizukuMode: Boolean, appliedBoot: Int, currentBoot: Int): Boolean =
        enabled && shizukuMode && (currentBoot < 0 || appliedBoot != currentBoot)

    private fun currentBoot(context: Context): Int =
        runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT) }.getOrDefault(-1)

    private fun appliedBoot(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_APPLIED_BOOT, Int.MIN_VALUE)

    internal fun pendingForThisBoot(context: Context): Boolean =
        shouldReapply(
            enabled = isEnabled(context),
            shizukuMode = PrivilegeManager.selectedMode(context) == PrivilegeMode.SHIZUKU,
            appliedBoot = appliedBoot(context),
            currentBoot = currentBoot(context),
        )

    internal fun markAppliedThisBoot(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_APPLIED_BOOT, currentBoot(context)).apply()
    }

    fun schedule(context: Context, delaySeconds: Long) {
        if (!pendingForThisBoot(context)) return
        val request = OneTimeWorkRequestBuilder<ShizukuReapplyWorker>()
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /**
     * Reapplies as soon as this process sees Shizuku, however late after boot it was
     * started. The sticky listener also fires at once if Shizuku is already running.
     */
    fun watch(context: Context) {
        val app = context.applicationContext
        if (!watching.compareAndSet(false, true)) return
        Shizuku.addBinderReceivedListenerSticky {
            runCatching {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) schedule(app, 0)
            }.onFailure { Log.w(TAG, "Shizuku connected but the reapply could not be scheduled", it) }
        }
        Shizuku.addRequestPermissionResultListener { _, result ->
            if (result == PackageManager.PERMISSION_GRANTED) schedule(app, 0)
        }
    }
}

class ShizukuReapplyWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        val context = applicationContext
        if (!ShizukuBootReapply.pendingForThisBoot(context)) return Result.success()
        // This runs without HomeActivity, which is where the exemptions are normally added.
        HiddenApiBypass.addHiddenApiExemptions("L", "I")
        PrivilegeManager.activate(context, PrivilegeMode.SHIZUKU)

        // Shizuku may still be starting. If it never comes up here, the watcher in the app
        // process reapplies later, when Shizuku is started and the app is opened.
        if (!Shizuku.pingBinder()) {
            return if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return Result.failure()

        val applied = runCatching { CarrierConfigPersistence.reapplyAll(context) }
            .onFailure { Log.w(TAG, "Unable to reapply the saved Shizuku SIM profile", it) }
            .getOrDefault(false)
        if (applied) ShizukuBootReapply.markAppliedThisBoot(context)
        return when {
            applied -> Result.success()
            runAttemptCount < MAX_ATTEMPTS -> Result.retry()
            else -> Result.failure()
        }
    }

    private companion object {
        const val TAG = "ShizukuReapplyWorker"

        // Linear 30 s backoff: twelve attempts wait about 39 minutes in total.
        const val MAX_ATTEMPTS = 12
    }
}
