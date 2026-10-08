package dev.bluehouse.enablevolte

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Reapplies every saved per-SIM profile through whichever privilege mode is active. */
internal object CarrierConfigPersistence {
    fun reapplyAll(context: Context): Boolean {
        val ready = when (PrivilegeManager.activeMode) {
            PrivilegeMode.ROOT -> PrivilegeManager.isRootReady()
            PrivilegeMode.SHIZUKU -> ShizukuBootReapply.isEnabled(context) && checkShizukuGrantedQuietly()
        }
        if (!ready) return false
        val carrierModer = CarrierModer(context)
        return carrierModer.subscriptions
            .filter { RootCarrierConfigStore(context).hasProfile(it.subscriptionId) }
            .all { SubscriptionModer(context, it.subscriptionId).reapplyPersistedCarrierConfig() }
    }

    /** Unlike checkShizukuPermission, never raises a permission prompt from the background. */
    private fun checkShizukuGrantedQuietly(): Boolean =
        runCatching {
            rikka.shizuku.Shizuku.pingBinder() &&
                rikka.shizuku.Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
}

internal object RootCarrierConfigPersistence {
    fun reapplyAll(context: Context): Boolean =
        PrivilegeManager.activeMode == PrivilegeMode.ROOT && CarrierConfigPersistence.reapplyAll(context)

    fun schedule(context: Context, delaySeconds: Long) {
        if (PrivilegeManager.selectedMode(context) != PrivilegeMode.ROOT) return
        val request = OneTimeWorkRequestBuilder<RootCarrierConfigPersistenceWorker>()
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private const val WORK_NAME = "root_carrier_config_persistence"
}

class RootCarrierConfigPersistenceWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        if (PrivilegeManager.selectedMode(applicationContext) != PrivilegeMode.ROOT) return Result.success()
        // Runs without HomeActivity, which is where the exemptions are normally added;
        // without them the hidden telephony interfaces fail to load at boot.
        org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("L", "I")
        PrivilegeManager.activate(applicationContext, PrivilegeMode.ROOT)

        val completed = CountDownLatch(1)
        val applied = AtomicBoolean(false)
        PrivilegeManager.connectRoot(applicationContext, reapplyPersistedConfig = false) { ready, _ ->
            if (ready) applied.set(RootCarrierConfigPersistence.reapplyAll(applicationContext))
            completed.countDown()
        }
        if (!completed.await(25, TimeUnit.SECONDS)) return Result.retry()
        return if (applied.get()) Result.success() else if (runAttemptCount < 2) Result.retry() else Result.failure()
    }
}

class RootCarrierConfigBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val delay = if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) 3L else 20L
        RootCarrierConfigPersistence.schedule(context.applicationContext, delay)
        ShizukuBootReapply.schedule(context.applicationContext, delay)
    }
}
