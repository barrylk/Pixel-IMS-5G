package dev.bluehouse.enablevolte

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.wifi.IWifiManager
import android.os.IBinder
import android.os.ServiceManager
import android.util.Log
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import rikka.shizuku.ShizukuBinderWrapper
import java.util.concurrent.TimeUnit

enum class PrivilegeMode {
    SHIZUKU,
    ROOT,
}

object PrivilegeManager {
    private const val TAG = "PrivilegeManager"
    private const val PREFS = "pixel_ims_privilege"
    private const val MODE = "mode"

    @Volatile
    private var rootBridge: IPrivilegedService? = null
    private var rootConnection: ServiceConnection? = null
    private val rootRequests = RootRequestQueue<RootConnectionResult>()

    private data class RootConnectionResult(
        val ready: Boolean,
        val error: String?,
    )

    @Volatile
    var activeMode: PrivilegeMode = PrivilegeMode.SHIZUKU
        private set

    fun selectedMode(context: Context): PrivilegeMode? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(MODE, null)
            ?.let { runCatching { PrivilegeMode.valueOf(it) }.getOrNull() }

    fun activate(context: Context, mode: PrivilegeMode) {
        activeMode = mode
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(MODE, mode.name).apply()
        InterfaceCache.cache.clear()
    }

    fun clearMode(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(MODE).apply()
        disconnectRoot()
        InterfaceCache.cache.clear()
    }

    fun isRootReady(): Boolean = runCatching { rootBridge?.serviceUid == 0 }.getOrDefault(false)

    fun getRootSystemProperty(name: String): String? =
        if (activeMode == PrivilegeMode.ROOT && isRootReady()) {
            runCatching { rootBridge?.getAllowedSystemProperty(name) }.getOrNull()
        } else {
            null
        }

    fun setRootSystemProperty(name: String, value: String): Boolean =
        activeMode == PrivilegeMode.ROOT &&
            isRootReady() &&
            runCatching { rootBridge?.setAllowedSystemProperty(name, value) == true }.getOrDefault(false)

    fun getRootTelephonyDiagnostic(kind: String): String? {
        if (activeMode != PrivilegeMode.ROOT || !isRootReady()) return null
        return try {
            rootBridge?.getTelephonyDiagnosticSnapshot(kind)
        } catch (error: Throwable) {
            Log.w(TAG, "Root diagnostic '$kind' failed", error)
            rootBridge = null
            rootConnection = null
            InterfaceCache.cache.clear()
            null
        }
    }

    fun getRegionalModemPatchStatus(): RegionalModemPatchStatus =
        rootRegionalPatchCall { it.getRegionalModemPatchStatus() }

    fun installRegionalModemPatch(): RegionalModemPatchStatus =
        rootRegionalPatchCall { it.installRegionalModemPatch() }

    fun scheduleRegionalModemPatchRemoval(): RegionalModemPatchStatus =
        rootRegionalPatchCall { it.scheduleRegionalModemPatchRemoval() }

    fun getRootVoWifiStatus(subscriptionId: Int): RootVoWifiStatus =
        rootVoWifiCall(subscriptionId) { it.getRootVoWifiStatus(subscriptionId) }

    fun applyRootVoWifiRepair(subscriptionId: Int): RootVoWifiStatus =
        rootVoWifiCall(subscriptionId) { it.applyRootVoWifiRepair(subscriptionId) }

    fun restoreRootVoWifiRepair(subscriptionId: Int): RootVoWifiStatus =
        rootVoWifiCall(subscriptionId) { it.restoreRootVoWifiRepair(subscriptionId) }

    fun setRootImsStatusBarMonitoring(
        enabled: Boolean,
        subscriptionIds: IntArray,
    ): Boolean =
        activeMode == PrivilegeMode.ROOT &&
            isRootReady() &&
            runCatching {
                rootBridge?.setImsStatusBarMonitoring(enabled, subscriptionIds) == true
            }.onFailure {
                Log.w(TAG, "Unable to update root IMS status-bar monitoring", it)
            }.getOrDefault(false)

    private fun rootVoWifiCall(
        subscriptionId: Int,
        call: (IPrivilegedService) -> String,
    ): RootVoWifiStatus {
        if (activeMode != PrivilegeMode.ROOT || !isRootReady()) {
            return RootVoWifiStatus.unavailable(subscriptionId, "Root mode is required.")
        }
        return runCatching {
            RootVoWifiStatus.fromJson(call(requireNotNull(rootBridge)))
        }.getOrElse {
            Log.w(TAG, "Root VoWiFi operation failed", it)
            RootVoWifiStatus.unavailable(subscriptionId, it.message ?: "Root service operation failed.")
        }
    }

    private fun rootRegionalPatchCall(call: (IPrivilegedService) -> String): RegionalModemPatchStatus {
        if (activeMode != PrivilegeMode.ROOT || !isRootReady()) {
            return RegionalModemPatchStatus.unavailable("Root mode is required.")
        }
        return runCatching {
            RegionalModemPatchStatus.fromJson(call(requireNotNull(rootBridge)))
        }.getOrElse {
            Log.w(TAG, "Regional modem patch operation failed", it)
            RegionalModemPatchStatus.unavailable(it.message ?: "Root service operation failed.")
        }
    }

    fun connectRoot(
        context: Context,
        reapplyPersistedConfig: Boolean = true,
        result: (Boolean, String?) -> Unit,
    ) {
        val shouldStart = rootRequests.enqueue { connectionResult ->
            result(connectionResult.ready, connectionResult.error)
        }
        if (!shouldStart) {
            return
        }
        if (isRootReady()) {
            completeRootConnection(context, reapplyPersistedConfig)
            return
        }
        // Verify a root shell before queuing a RootService bind. KernelSU authorizes the app
        // UID in its manager; a denied shell must not leave libsu's service bind pending.
        requestRootShell(context, reapplyPersistedConfig)
    }

    private fun requestRootShell(
        context: Context,
        reapplyPersistedConfig: Boolean,
    ) {
        Thread {
            val opened = runCatching {
                Shell.Builder.create()
                    .setContext(context.applicationContext)
                    .setTimeout(30)
                    .build(*rootShellCommand())
            }
            context.mainExecutor.execute {
                val shell = opened.getOrNull()
                if (shell?.isRoot != true) {
                    Thread { runCatching { shell?.waitAndClose(2, TimeUnit.SECONDS) } }.start()
                    Log.w(TAG, "Root shell startup failed", opened.exceptionOrNull())
                    finishRootConnection(
                        ready = false,
                        error = "Root shell could not start. Enable Superuser for Pixel IMS 5G in your root " +
                            "manager and retry. " + (opened.exceptionOrNull()?.message ?: "su did not return UID 0"),
                    )
                    return@execute
                }
                bindRootService(context, reapplyPersistedConfig, shell)
            }
        }.start()
    }

    private fun bindRootService(
        context: Context,
        reapplyPersistedConfig: Boolean,
        shell: Shell,
    ) {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                rootBridge = IPrivilegedService.Stub.asInterface(service)
                val ready = isRootReady()
                Log.i(TAG, "Root service connected: component=$name ready=$ready")
                if (!ready) rootBridge = null
                if (ready) {
                    completeRootConnection(context, reapplyPersistedConfig)
                } else {
                    finishRootConnection(false, "The root service did not start as UID 0")
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                Log.w(TAG, "Root service disconnected: $name")
                rootBridge = null
                rootConnection = null
                InterfaceCache.cache.clear()
                finishRootConnection(false, "Root service disconnected before setup completed")
            }

            override fun onNullBinding(name: ComponentName) {
                rootBridge = null
                rootConnection = null
                finishRootConnection(
                    false,
                    "The root shell was verified, but the root service returned no Binder. Check the root manager " +
                        "profile and service startup logs.",
                )
            }

            override fun onBindingDied(name: ComponentName) {
                Log.w(TAG, "Root service binding died: $name")
                rootBridge = null
                rootConnection = null
                InterfaceCache.cache.clear()
                finishRootConnection(false, "Root service binding died before setup completed")
            }
        }
        rootConnection = connection
        runCatching {
            val intent = Intent(context, PrivilegedService::class.java)
            // Execute the launch task on the verified shell; RootService.bind() would create
            // another main shell using libsu's direct Runtime.exec("su") path.
            val task = RootService.bindOrTask(intent, context.mainExecutor, connection)
            Thread {
                try {
                    if (task != null) shell.execTask(task)
                } catch (error: Exception) {
                    Log.w(TAG, "Root service launch failed", error)
                    context.mainExecutor.execute {
                        runCatching { RootService.unbind(connection) }
                        finishRootConnection(false, "Root service launch failed: ${error.message}")
                    }
                } finally {
                    runCatching { shell.waitAndClose(2, TimeUnit.SECONDS) }
                }
            }.start()
        }.onFailure {
            Thread { runCatching { shell.waitAndClose(2, TimeUnit.SECONDS) } }.start()
            Log.w(TAG, "Unable to bind root service", it)
            rootConnection = null
            finishRootConnection(false, it.message ?: "Unable to request root access")
        }
    }

    private fun completeRootConnection(
        context: Context,
        reapplyPersistedConfig: Boolean,
    ) {
        if (!reapplyPersistedConfig) {
            finishRootConnection(true, null)
            return
        }
        Thread {
            val reapplied = runCatching {
                RootCarrierConfigPersistence.reapplyAll(context.applicationContext)
            }.onFailure {
                Log.w(TAG, "Unable to reapply the saved Root CarrierConfig profile", it)
            }.getOrDefault(false)
            context.mainExecutor.execute {
                finishRootConnection(
                    true,
                    if (reapplied) null else "The saved Root SIM profile could not be fully reapplied",
                )
            }
        }.start()
    }

    private fun finishRootConnection(ready: Boolean, error: String?) {
        if (!ready) {
            rootBridge = null
            rootConnection = null
            InterfaceCache.cache.clear()
        }
        val connectionResult = RootConnectionResult(ready, error)
        rootRequests.complete(connectionResult).forEach { callback -> callback(connectionResult) }
    }

    fun disconnectRoot() {
        rootConnection?.let { runCatching { RootService.unbind(it) } }
        rootConnection = null
        rootBridge = null
        finishRootConnection(false, "Root service was disconnected")
    }

    fun getPrivilegedWifiState(): Int =
        when (activeMode) {
            PrivilegeMode.ROOT ->
                requireNotNull(rootBridge) { "Root service is not connected" }.wifiEnabledState
            PrivilegeMode.SHIZUKU ->
                shizukuWifiService().wifiEnabledState
        }

    fun setPrivilegedWifiEnabled(enabled: Boolean): Boolean =
        when (activeMode) {
            PrivilegeMode.ROOT ->
                requireNotNull(rootBridge) { "Root service is not connected" }.setWifiEnabled(enabled)
            PrivilegeMode.SHIZUKU ->
                shizukuWifiService().setWifiEnabled("com.android.shell", enabled)
        }

    private fun shizukuWifiService(): IWifiManager {
        val directBinder = ServiceManager.getService(Context.WIFI_SERVICE)
            ?: error("Wi-Fi service is unavailable")
        return IWifiManager.Stub.asInterface(ShizukuBinderWrapper(directBinder))
    }

    fun wrapService(name: String, directBinder: IBinder): IBinder =
        when (activeMode) {
            PrivilegeMode.ROOT ->
                rootBridge?.getSystemService(name)
                    ?: error("Root service is not connected")
            PrivilegeMode.SHIZUKU -> ShizukuBinderWrapper(directBinder)
        }
}
