package dev.bluehouse.enablevolte

import android.content.Context
import android.content.pm.PackageManager
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

/**
 * Opt-in "reapply after reboot" for Shizuku mode (issue #6).
 *
 * Shizuku overrides are session-only: the platform drops them on reboot. With
 * this on, SIM Config changes are saved per SIM exactly as Root mode saves
 * them, and a worker reapplies them once Shizuku comes back after boot. Shizuku
 * (or a fork that starts on boot) has to be running; the worker waits for it
 * with backoff rather than failing on the first try.
 */
object ShizukuBootReapply {
    private const val PREFS = "pixel_ims_boot"
    private const val KEY = "shizuku_reapply_on_boot"
    private const val WORK_NAME = "shizuku_carrier_config_reapply"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, enabled).apply()
    }

    fun schedule(context: Context, delaySeconds: Long) {
        if (PrivilegeManager.selectedMode(context) != PrivilegeMode.SHIZUKU || !isEnabled(context)) return
        val request = OneTimeWorkRequestBuilder<ShizukuReapplyWorker>()
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}

class ShizukuReapplyWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        val context = applicationContext
        if (PrivilegeManager.selectedMode(context) != PrivilegeMode.SHIZUKU || !ShizukuBootReapply.isEnabled(context)) {
            return Result.success()
        }
        // This runs without HomeActivity, which is where the exemptions are normally added.
        HiddenApiBypass.addHiddenApiExemptions("L", "I")
        PrivilegeManager.activate(context, PrivilegeMode.SHIZUKU)

        // Shizuku may still be starting; about ten minutes of linear backoff covers a slow boot.
        if (!Shizuku.pingBinder()) {
            return if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return Result.failure()

        val applied = runCatching { CarrierConfigPersistence.reapplyAll(context) }
            .onFailure { Log.w(TAG, "Unable to reapply the saved Shizuku SIM profile", it) }
            .getOrDefault(false)
        return when {
            applied -> Result.success()
            runAttemptCount < MAX_ATTEMPTS -> Result.retry()
            else -> Result.failure()
        }
    }

    private companion object {
        const val TAG = "ShizukuReapplyWorker"
        const val MAX_ATTEMPTS = 6
    }
}
