package dev.bluehouse.enablevolte

import android.annotation.SuppressLint
import android.content.Intent
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.wifi.IWifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import android.os.ServiceManager
import android.telephony.ims.ImsMmTelManager
import android.util.Log
import com.android.internal.statusbar.IStatusBarService
import com.android.internal.telephony.ITelephony
import com.topjohnwu.superuser.ipc.RootService
import org.lsposed.hiddenapibypass.HiddenApiBypass
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer

/**
 * Minimal root-side Binder bridge. Calls are forwarded from UID 0 so system_server sees root as
 * the caller. Only the fixed set of telephony-related services used by this app is exposed.
 */
class PrivilegedService : RootService() {
    private val forwardedServices = ConcurrentHashMap<String, IBinder>()
    private val iconMonitorLock = Any()
    private var iconMonitor: ScheduledExecutorService? = null
    @Volatile
    private var monitoredSubscriptionIds = intArrayOf()

    private val binder = object : IPrivilegedService.Stub() {
        override fun getSystemService(name: String): IBinder? {
            if (name !in ALLOWED_SERVICES) return null
            return forwardedServices.getOrPut(name) {
                ForwardingBinder(ServiceManager.getService(name) ?: error("Service $name is unavailable"))
            }
        }

        override fun getServiceUid(): Int = Process.myUid()

        override fun getAllowedSystemProperty(name: String): String? {
            if (name !in ALLOWED_PROPERTIES) return null
            return runCommand("/system/bin/getprop", name).takeIf { it.exitCode == 0 }?.output?.trim()
        }

        override fun setAllowedSystemProperty(name: String, value: String): Boolean {
            if (name !in ALLOWED_PROPERTIES || value !in ALLOWED_PROPERTY_VALUES) return false
            return runCommand("/system/bin/setprop", name, value).exitCode == 0
        }

        override fun getTelephonyDiagnosticSnapshot(kind: String): String {
            Log.i(TAG, "Reading root telephony diagnostic: $kind")
            val command = DIAGNOSTIC_COMMANDS[kind] ?: return "Unsupported diagnostic source"
            val result = runCommand(*command)
            if (result.exitCode != 0) return "Diagnostic source failed (${result.exitCode})"
            return runCatching {
                when (kind) {
                    "physical" -> extractPhysicalChannels(result.output)
                    "properties" -> extractRadioProperties(result.output)
                    "carrier" -> extractCarrierConfig(result.output)
                    else -> sanitizeDiagnostic(result.output)
                }
            }.getOrElse {
                Log.w(TAG, "Diagnostic parser failed for $kind", it)
                "Diagnostic parser failed: ${it.javaClass.simpleName}"
            }
        }

        override fun getRegionalModemPatchStatus(): String =
            regionalPatchStatus(message = regionalPatchStatusMessage()).toString()

        override fun installRegionalModemPatch(): String =
            runCatching { installRegionalPatch() }
                .getOrElse {
                    Log.e(TAG, "Regional modem patch installation failed", it)
                    regionalPatchStatus(message = it.message ?: "Patch installation failed.").toString()
                }

        override fun scheduleRegionalModemPatchRemoval(): String =
            runCatching {
                val moduleDir = File(REGIONAL_MODULE_DIR)
                check(moduleDir.isDirectory) { "The regional modem patch is not installed." }
                File(moduleDir, "remove").apply {
                    check(createNewFile() || isFile) { "Unable to create the Magisk removal marker." }
                }
                regionalPatchStatus(
                    rebootRequired = true,
                    message = "Removal scheduled. Reboot to restore the stock modem carrier database.",
                ).toString()
            }.getOrElse {
                Log.e(TAG, "Unable to schedule regional modem patch removal", it)
                regionalPatchStatus(message = it.message ?: "Unable to schedule removal.").toString()
            }

        override fun getWifiEnabledState(): Int =
            IWifiManager.Stub.asInterface(
                ServiceManager.getService("wifi") ?: error("Wi-Fi service is unavailable"),
            ).wifiEnabledState

        override fun setWifiEnabled(enabled: Boolean): Boolean =
            IWifiManager.Stub.asInterface(
                ServiceManager.getService("wifi") ?: error("Wi-Fi service is unavailable"),
            ).setWifiEnabled("com.android.shell", enabled)

        override fun getRootVoWifiStatus(subscriptionId: Int): String =
            readRootVoWifiStatus(subscriptionId).toString()

        override fun applyRootVoWifiRepair(subscriptionId: Int): String =
            runCatching {
                validateSubscriptionId(subscriptionId)
                val before = readRootVoWifiStatus(subscriptionId)
                check(before.optBoolean("available")) {
                    before.optString("message", "Unable to read the IMS Wi-Fi calling service.")
                }
                val snapshot = voWifiSnapshotFile(subscriptionId)
                if (!snapshot.isFile) {
                    snapshot.parentFile?.let { parent ->
                        check(parent.mkdirs() || parent.isDirectory) { "Unable to create the VoWiFi snapshot directory." }
                    }
                    snapshot.writeText(
                        JSONObject()
                            .put("settingEnabled", before.optBoolean("settingEnabled"))
                            .put("roamingEnabled", before.optBoolean("roamingEnabled"))
                            .put("mode", before.optInt("mode", -1))
                            .put("roamingMode", before.optInt("roamingMode", -1))
                            .toString(),
                    )
                }

                val manager = ImsMmTelManager.createForSubscriptionId(subscriptionId)
                invokeImsSetter(manager, "setVoWiFiSettingEnabled", true)
                invokeImsSetter(manager, "setVoWiFiRoamingSettingEnabled", true)
                invokeImsSetter(manager, "setVoWiFiModeSetting", WIFI_MODE_WIFI_PREFERRED)
                invokeImsSetter(manager, "setVoWiFiRoamingModeSetting", WIFI_MODE_WIFI_PREFERRED)

                readRootVoWifiStatus(
                    subscriptionId,
                    operationSucceeded = true,
                    message = "VoWiFi is enabled and Wi-Fi preferred. Connect to Wi-Fi and refresh to verify IWLAN.",
                ).toString()
            }.getOrElse {
                Log.e(TAG, "Root VoWiFi repair failed", it)
                readRootVoWifiStatus(
                    subscriptionId,
                    operationSucceeded = false,
                    message = it.message ?: "Root VoWiFi repair failed.",
                ).toString()
            }

        override fun restoreRootVoWifiRepair(subscriptionId: Int): String =
            runCatching {
                validateSubscriptionId(subscriptionId)
                val snapshot = voWifiSnapshotFile(subscriptionId)
                check(snapshot.isFile) { "No pre-repair VoWiFi snapshot is available for this SIM." }
                val original = JSONObject(snapshot.readText())
                val manager = ImsMmTelManager.createForSubscriptionId(subscriptionId)
                invokeImsSetter(manager, "setVoWiFiSettingEnabled", original.optBoolean("settingEnabled"))
                invokeImsSetter(manager, "setVoWiFiRoamingSettingEnabled", original.optBoolean("roamingEnabled"))
                original.optInt("mode", -1).takeIf { it >= 0 }?.let {
                    invokeImsSetter(manager, "setVoWiFiModeSetting", it)
                }
                original.optInt("roamingMode", -1).takeIf { it >= 0 }?.let {
                    invokeImsSetter(manager, "setVoWiFiRoamingModeSetting", it)
                }
                check(snapshot.delete()) { "Settings were restored, but the snapshot could not be removed." }
                readRootVoWifiStatus(
                    subscriptionId,
                    operationSucceeded = true,
                    message = "The pre-repair VoWiFi user settings were restored.",
                ).toString()
            }.getOrElse {
                Log.e(TAG, "Root VoWiFi restore failed", it)
                readRootVoWifiStatus(
                    subscriptionId,
                    operationSucceeded = false,
                    message = it.message ?: "Root VoWiFi restore failed.",
                ).toString()
            }

        override fun setImsStatusBarMonitoring(
            enabled: Boolean,
            subscriptionIds: IntArray,
        ): Boolean =
            runCatching {
                subscriptionIds.forEach(::validateSubscriptionId)
                synchronized(iconMonitorLock) {
                    monitoredSubscriptionIds = subscriptionIds.distinct().toIntArray()
                    Log.i(
                        TAG,
                        "IMS status-bar monitor enabled=$enabled subscriptions=${monitoredSubscriptionIds.contentToString()}",
                    )
                    if (!enabled || monitoredSubscriptionIds.isEmpty()) {
                        iconMonitor?.shutdownNow()
                        iconMonitor = null
                        clearImsStatusBarIcons()
                    } else {
                        if (iconMonitor?.isShutdown != false) {
                            iconMonitor = Executors.newSingleThreadScheduledExecutor().apply {
                                scheduleWithFixedDelay(
                                    { updateImsStatusBarIcons() },
                                    0,
                                    STATUS_ICON_POLL_SECONDS,
                                    TimeUnit.SECONDS,
                                )
                            }
                        } else {
                            updateImsStatusBarIcons()
                        }
                    }
                }
                true
            }.onFailure {
                Log.e(TAG, "Unable to configure IMS status-bar monitor", it)
            }.getOrDefault(false)
    }

    override fun onBind(intent: Intent): IBinder {
        HiddenApiBypass.addHiddenApiExemptions(
            "Landroid/telephony/",
            "Landroid/telephony/ims/",
            "Landroid/os/TelephonyServiceManager",
        )
        runCatching { bootstrapTelephonyFramework() }
            .onFailure { Log.w(TAG, "Unable to bootstrap the root telephony framework", it) }
        // RootService runs in a fresh app_process where the telephony framework registerers are
        // not necessarily bootstrapped yet. Resolve both services before ImsMmTelManager is used.
        getSystemService(Context.TELEPHONY_SERVICE)
        getSystemService(Context.TELEPHONY_IMS_SERVICE)
        return binder
    }

    override fun onDestroy() {
        synchronized(iconMonitorLock) {
            iconMonitor?.shutdownNow()
            iconMonitor = null
            clearImsStatusBarIcons()
        }
        super.onDestroy()
    }

    private fun updateImsStatusBarIcons() {
        val subscriptions = monitoredSubscriptionIds
        if (subscriptions.isEmpty()) {
            clearImsStatusBarIcons()
            return
        }
        val phone = ITelephony.Stub.asInterface(
            ServiceManager.getService(Context.TELEPHONY_SERVICE)
                ?: error("Telephony service is unavailable"),
        )
        var volteActive = false
        var vowifiActive = false
        subscriptions.forEach { subscriptionId ->
            val registered = runCatching { phone.isImsRegistered(subscriptionId) }.getOrDefault(false)
            if (!registered) return@forEach
            val technology = runCatching {
                phone.getImsRegTechnologyForMmTel(subscriptionId)
            }.getOrDefault(-1)
            when (technology) {
                IMS_REGISTRATION_TECH_LTE,
                IMS_REGISTRATION_TECH_NR,
                -> volteActive = true
                IMS_REGISTRATION_TECH_IWLAN -> vowifiActive = true
            }
        }
        Log.d(TAG, "IMS status-bar state VoLTE=$volteActive VoWiFi=$vowifiActive")
        val statusBar = statusBarService()
        updateStatusBarIcon(
            statusBar = statusBar,
            slot = STATUS_SLOT_VOLTE,
            visible = volteActive,
            drawable = R.drawable.ic_status_volte,
            description = "VoLTE active",
        )
        updateStatusBarIcon(
            statusBar = statusBar,
            slot = STATUS_SLOT_VOWIFI,
            visible = vowifiActive,
            drawable = R.drawable.ic_status_vowifi,
            description = "VoWiFi active",
        )
    }

    private fun clearImsStatusBarIcons() {
        runCatching {
            statusBarService().apply {
                setIconVisibility(STATUS_SLOT_VOLTE, false)
                setIconVisibility(STATUS_SLOT_VOWIFI, false)
                removeIcon(STATUS_SLOT_VOLTE)
                removeIcon(STATUS_SLOT_VOWIFI)
            }
        }.onFailure { Log.w(TAG, "Unable to remove IMS status-bar icons", it) }
    }

    private fun statusBarService(): IStatusBarService =
        IStatusBarService.Stub.asInterface(
            ServiceManager.getService(Context.STATUS_BAR_SERVICE)
                ?: error("Status-bar service is unavailable"),
        )

    private fun updateStatusBarIcon(
        statusBar: IStatusBarService,
        slot: String,
        visible: Boolean,
        drawable: Int,
        description: String,
    ) {
        if (visible) {
            statusBar.setIcon(slot, packageName, drawable, 0, description)
            statusBar.setIconVisibility(slot, true)
        } else {
            statusBar.setIconVisibility(slot, false)
            statusBar.removeIcon(slot)
        }
    }

    private class ForwardingBinder(
        private val target: IBinder,
    ) : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean =
            target.transact(code, data, reply, flags)
    }

    companion object {
        private const val TAG = "PrivilegedService"
        private data class CommandResult(val exitCode: Int, val output: String)

        private const val WIFI_MODE_WIFI_PREFERRED = 2
        private const val VOWIFI_SNAPSHOT_DIR = "/data/local/tmp/pixelims5g"
        private const val STATUS_ICON_POLL_SECONDS = 3L
        private const val STATUS_SLOT_VOLTE = "pixelims_volte"
        private const val STATUS_SLOT_VOWIFI = "pixelims_vowifi"
        private const val IMS_REGISTRATION_TECH_LTE = 0
        private const val IMS_REGISTRATION_TECH_IWLAN = 1
        private const val IMS_REGISTRATION_TECH_NR = 3

        @SuppressLint("BlockedPrivateApi")
        private fun bootstrapTelephonyFramework() {
            val initializer = Class.forName("android.telephony.TelephonyFrameworkInitializer")
            val getManager = initializer.getDeclaredMethod("getTelephonyServiceManager").apply {
                isAccessible = true
            }
            if (getManager.invoke(null) != null) return
            val managerClass = Class.forName("android.os.TelephonyServiceManager")
            val manager = managerClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
            initializer.getDeclaredMethod("setTelephonyServiceManager", managerClass)
                .apply { isAccessible = true }
                .invoke(null, manager)
        }

        private fun validateSubscriptionId(subscriptionId: Int) {
            require(subscriptionId in 0..9999) { "Invalid subscription ID." }
        }

        private fun voWifiSnapshotFile(subscriptionId: Int): File {
            validateSubscriptionId(subscriptionId)
            return File(VOWIFI_SNAPSHOT_DIR, "vowifi-$subscriptionId.json")
        }

        private fun invokeImsSetter(manager: ImsMmTelManager, name: String, value: Any) {
            val parameter = when (value) {
                is Boolean -> Boolean::class.javaPrimitiveType
                is Int -> Int::class.javaPrimitiveType
                else -> error("Unsupported IMS setting type.")
            }
            val method = manager.javaClass.getDeclaredMethod(name, parameter).apply { isAccessible = true }
            method.invoke(manager, value)
        }

        private fun readRootVoWifiStatus(
            subscriptionId: Int,
            operationSucceeded: Boolean = false,
            message: String = "",
        ): JSONObject {
            return runCatching {
                validateSubscriptionId(subscriptionId)
                val manager = ImsMmTelManager.createForSubscriptionId(subscriptionId)
                val registrationState = AtomicInteger(-1)
                val transportType = AtomicInteger(-1)
                val latch = CountDownLatch(2)
                val directExecutor = java.util.concurrent.Executor { command -> command.run() }
                runCatching {
                    manager.getRegistrationState(
                        directExecutor,
                        Consumer {
                            registrationState.set(it)
                            latch.countDown()
                        },
                    )
                }.onFailure { latch.countDown() }
                runCatching {
                    manager.getRegistrationTransportType(
                        directExecutor,
                        Consumer {
                            transportType.set(it)
                            latch.countDown()
                        },
                    )
                }.onFailure { latch.countDown() }
                latch.await(2, TimeUnit.SECONDS)

                val roamingMode = runCatching {
                    manager.javaClass.getDeclaredMethod("getVoWiFiRoamingModeSetting")
                        .apply { isAccessible = true }
                        .invoke(manager) as Int
                }.getOrDefault(-1)
                val wifiState = runCatching {
                    IWifiManager.Stub.asInterface(
                        ServiceManager.getService("wifi") ?: error("Wi-Fi service is unavailable"),
                    ).wifiEnabledState
                }.getOrDefault(0)
                JSONObject()
                    .put("available", true)
                    .put("subscriptionId", subscriptionId)
                    .put("settingEnabled", manager.isVoWiFiSettingEnabled)
                    .put("roamingEnabled", manager.isVoWiFiRoamingSettingEnabled)
                    .put("mode", manager.voWiFiModeSetting)
                    .put("roamingMode", roamingMode)
                    .put("registrationState", registrationState.get())
                    .put("transportType", transportType.get())
                    .put("wifiState", wifiState)
                    .put("snapshotAvailable", voWifiSnapshotFile(subscriptionId).isFile)
                    .put("operationSucceeded", operationSucceeded)
                    .put("failureReason", recentVoWifiFailure(subscriptionId))
                    .put("message", message)
            }.getOrElse {
                Log.w(TAG, "Unable to read root VoWiFi status for subId=$subscriptionId", it)
                JSONObject()
                    .put("available", false)
                    .put("subscriptionId", subscriptionId)
                    .put("settingEnabled", false)
                    .put("roamingEnabled", false)
                    .put("mode", -1)
                    .put("roamingMode", -1)
                    .put("registrationState", -1)
                    .put("transportType", -1)
                    .put("wifiState", 0)
                    .put("snapshotAvailable", voWifiSnapshotFile(subscriptionId).isFile)
                    .put("operationSucceeded", false)
                    .put("failureReason", recentVoWifiFailure(subscriptionId))
                    .put("message", message.ifBlank { it.message ?: "Unable to read root VoWiFi status." })
            }
        }

        private fun recentVoWifiFailure(subscriptionId: Int): String {
            val radio = runCommand(
                "/system/bin/logcat",
                "-b",
                "radio",
                "-d",
                "-v",
                "brief",
                "-t",
                "1800",
            ).takeIf { it.exitCode == 0 }?.output.orEmpty()
            val selectedSubHasNoProfile = Regex(
                "NO_SUITABLE_DATA_PROFILE[\\s\\S]{0,1400}mSubId\\s*=\\s*$subscriptionId\\b",
                RegexOption.IGNORE_CASE,
            ).containsMatchIn(radio)
            return when {
                selectedSubHasNoProfile ->
                    "Selected-SIM evidence: Android found no suitable IMS data profile for IWLAN " +
                        "(NO_SUITABLE_DATA_PROFILE). The user setting is open, but the active carrier " +
                        "provisioning/APN profile cannot create the VoWiFi IMS bearer."
                radio.contains("IWLAN_IKE_INIT_TIMEOUT", ignoreCase = true) ->
                    "Recent modem-wide evidence: ePDG IKE tunnel setup timed out (IWLAN_IKE_INIT_TIMEOUT). " +
                        "Try another Wi-Fi network, turn off VPN/Private DNS, and confirm the carrier permits VoWiFi. " +
                        "The app cannot make an unreachable carrier ePDG answer."
                radio.contains("IWLAN_IKE_AUTH_FAILED", ignoreCase = true) ||
                    radio.contains("IKE_AUTH_FAILED", ignoreCase = true) ->
                    "Recent modem-wide evidence: the carrier ePDG rejected IKE authentication. " +
                        "SIM VoWiFi entitlement or carrier credentials are the likely blocker."
                radio.contains("IWLAN_DNS_RESOLUTION_NAME_FAILURE", ignoreCase = true) ->
                    "Recent modem-wide evidence: the carrier ePDG hostname could not be resolved. " +
                        "Turn off Private DNS/VPN and test a different Wi-Fi network."
                radio.contains("NO_SUITABLE_DATA_PROFILE", ignoreCase = true) ->
                    "Recent modem-wide evidence: Android found no suitable IMS profile for IWLAN. " +
                        "Carrier provisioning or the IMS APN/profile is incomplete."
                else -> ""
            }
        }

        private fun detectModuleManager(): ModuleManager? = when {
            findExecutable(MAGISK_CANDIDATES) != null -> ModuleManager.MAGISK
            File(KSUD).canExecute() -> ModuleManager.KERNELSU
            File(APD).canExecute() -> ModuleManager.APATCH
            else -> null
        }

        /** The domain this root service runs in, e.g. `magisk` or `su`. */
        private fun currentSelinuxDomain(): String? =
            runCatching { File("/proc/self/attr/current").readText().trim().trimEnd('\u0000') }
                .getOrNull()
                ?.split(':')
                ?.getOrNull(2)
                ?.takeIf { it.matches(Regex("[a-z0-9_]+")) }

        /**
         * SQLiteDatabase reads a Settings.Global flag through the app's content
         * resolver the first time it opens anything. This service has no app
         * identity, so on some builds that lookup throws "Unable to find app for
         * caller" and the patch never installs (issue #15). Initialising the
         * flags with no value marks them read and skips the lookup.
         */
        private fun disableSqliteGlobalSettingsLookup() {
            val walFlags = "android.database.sqlite.SQLiteCompatibilityWalFlags"
            runCatching {
                Class.forName(walFlags).getMethod("init", String::class.java).invoke(null, null)
            }.recoverCatching {
                HiddenApiBypass.invoke(Class.forName(walFlags), null, "init", null)
            }.onFailure { Log.w(TAG, "Unable to pre-initialise SQLite compatibility flags", it) }
        }

        private fun runCommand(vararg command: String): CommandResult =
            runCatching {
                val process = ProcessBuilder(*command).redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().use { it.readText() }
                CommandResult(process.waitFor(), output)
            }.getOrElse { CommandResult(-1, it.message.orEmpty()) }

        private fun installRegionalPatch(): String {
            val device = Build.DEVICE.lowercase()
            check(device in TENSOR_DEVICES) {
                "Unsupported device '$device'. This patch is limited to known Google Tensor Pixels."
            }
            val manager = detectModuleManager()
                ?: error("No module manager (Magisk, KernelSU or APatch) was detected. The modem database patch needs one.")
            val source = File(REGIONAL_SOURCE_DB)
            check(source.isFile) { "The Tensor carrier database was not found at $REGIONAL_SOURCE_DB." }

            // Each manager runs this service in its own SELinux domain and ships its
            // own live-policy tool, so the read rule is written for whichever is here.
            val readRule = "allow ${currentSelinuxDomain() ?: manager.defaultDomain} vendor_fw_file file { getattr open read map }"
            val policyApplied = when (manager) {
                ModuleManager.KERNELSU -> runCommand(KSUD, "sepolicy", "patch", readRule).exitCode == 0
                else -> findExecutable(MAGISK_POLICY_CANDIDATES)?.let { runCommand(it, "--live", readRule).exitCode == 0 } == true
            }
            if (!policyApplied) Log.w(TAG, "Live SELinux rule not applied for ${manager.label}; trying the read anyway")
            check(source.canRead()) {
                "SELinux refused read access to the stock carrier database under ${manager.label}."
            }

            val workDir = File(cacheDirectory(), "regional-modem-patch").apply {
                deleteRecursively()
                check(mkdirs()) { "Unable to create the patch staging directory." }
            }
            val stagedDatabase = File(workDir, "cfg.db")
            source.copyTo(stagedDatabase, overwrite = true)
            val sourceSha = stagedDatabase.sha256()

            patchAndValidateCarrierDatabase(stagedDatabase)
            val patchedSha = stagedDatabase.sha256()
            check(sourceSha != patchedSha) {
                "The database already uses the requested wildcard profile; no patch was staged."
            }

            val pendingModule = File("$REGIONAL_MODULE_DIR.new").apply {
                deleteRecursively()
                check(mkdirs()) { "Unable to create the Magisk module staging directory." }
            }
            File(pendingModule, "module.prop").writeText(
                """
                id=pixelims5g_region
                name=Pixel IMS 5G Regional Modem Compatibility
                version=1
                versionCode=1
                author=Nadeeja Nirmala
                description=Systemless, reversible Tensor cfg.db wildcard/PTCRB compatibility mapping.
                """.trimIndent() + "\n",
            )
            File(pendingModule, "service.sh").writeText(
                """
                #!/system/bin/sh
                rm -f /data/adb/modules/pixelims5g_region/.pending_reboot
                """.trimIndent() + "\n",
            )
            File(pendingModule, ".pending_reboot").writeText("")
            File(pendingModule, "source.sha256").writeText("$sourceSha\n")
            File(pendingModule, "patched.sha256").writeText("$patchedSha\n")
            val targetDatabase = File(
                pendingModule,
                "system/vendor/firmware/carrierconfig/cfg.db",
            ).apply {
                check(parentFile?.mkdirs() == true || parentFile?.isDirectory == true) {
                    "Unable to create the systemless vendor overlay."
                }
            }
            stagedDatabase.copyTo(targetDatabase, overwrite = true)

            // magisk_file only exists in Magisk's policy; KernelSU and APatch label
            // their module tree themselves.
            if (manager == ModuleManager.MAGISK) {
                check(
                    runCommand(
                        "/system/bin/chcon",
                        "-R",
                        "u:object_r:magisk_file:s0",
                        pendingModule.path,
                    ).exitCode == 0,
                ) {
                    "Unable to assign the Magisk module SELinux label."
                }
            }
            check(runCommand("/system/bin/chmod", "0755", File(pendingModule, "service.sh").path).exitCode == 0) {
                "Unable to make the module boot service executable."
            }
            check(runCommand("/system/bin/chmod", "0644", targetDatabase.path).exitCode == 0) {
                "Unable to set modem database permissions."
            }
            check(
                runCommand(
                    "/system/bin/chcon",
                    "u:object_r:vendor_fw_file:s0",
                    targetDatabase.path,
                ).exitCode == 0,
            ) {
                "Unable to assign the vendor firmware SELinux label."
            }

            val moduleDir = File(REGIONAL_MODULE_DIR)
            if (moduleDir.exists()) {
                check(moduleDir.canonicalPath == REGIONAL_MODULE_DIR) { "Unsafe module target path." }
                check(moduleDir.deleteRecursively()) { "Unable to replace the previous regional patch." }
            }
            check(pendingModule.renameTo(moduleDir)) { "Unable to activate the staged Magisk module." }
            workDir.deleteRecursively()

            return regionalPatchStatus(
                rebootRequired = true,
                message = "Validated systemless modem patch installed. Reboot to load it." +
                    if (manager == ModuleManager.KERNELSU) {
                        " KernelSU needs a mounting metamodule (for example mountify or meta-overlayfs) to apply it."
                    } else {
                        ""
                    },
            ).toString()
        }

        private fun patchAndValidateCarrierDatabase(databaseFile: File) {
            disableSqliteGlobalSettingsLookup()
            val database = SQLiteDatabase.openDatabase(
                databaseFile.path,
                null,
                SQLiteDatabase.OPEN_READWRITE,
            )
            try {
                check(database.rawQuery("PRAGMA integrity_check", null).use { it.moveToFirst() && it.getString(0) == "ok" }) {
                    "The stock carrier database failed SQLite integrity checking."
                }
                val tableNames = database.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('confnames','confmap')",
                    null,
                ).use { cursor ->
                    buildSet {
                        while (cursor.moveToNext()) add(cursor.getString(0))
                    }
                }
                check(tableNames == setOf("confnames", "confmap")) {
                    "Unsupported carrier database schema: required tables are missing."
                }
                val profileExists = database.rawQuery(
                    "SELECT COUNT(*) FROM confmap WHERE carrier_id=(" +
                        "SELECT carrier_id FROM confnames WHERE name='it_iliad' LIMIT 1)",
                    null,
                ).use { it.moveToFirst() && it.getInt(0) == 1 }
                check(profileExists) {
                    "This firmware has no validated permissive Tensor carrier profile (it_iliad)."
                }

                database.beginTransaction()
                try {
                    val profile = "(SELECT confman FROM confmap WHERE carrier_id=(" +
                        "SELECT carrier_id FROM confnames WHERE name='it_iliad' LIMIT 1))"
                    database.execSQL(
                        "UPDATE confmap SET confman=$profile WHERE carrier_id IN (0,20001,20005)",
                    )
                    database.execSQL(
                        "INSERT OR IGNORE INTO confmap (carrier_id,confman) VALUES (20001,$profile)",
                    )
                    database.execSQL(
                        "INSERT OR IGNORE INTO confmap (carrier_id,confman) VALUES (20005,$profile)",
                    )
                    val mapped = database.rawQuery(
                        "SELECT COUNT(*) FROM confmap WHERE carrier_id IN (0,20001,20005) " +
                            "AND confman=$profile",
                        null,
                    ).use { it.moveToFirst() && it.getInt(0) == 3 }
                    check(mapped) { "The wildcard/PTCRB carrier mappings were not retained." }
                    database.setTransactionSuccessful()
                } finally {
                    database.endTransaction()
                }
                check(database.rawQuery("PRAGMA integrity_check", null).use { it.moveToFirst() && it.getString(0) == "ok" }) {
                    "The patched carrier database failed SQLite integrity checking."
                }
            } finally {
                database.close()
            }
        }

        private fun regionalPatchStatus(
            rebootRequired: Boolean? = null,
            message: String,
        ): JSONObject {
            val device = Build.DEVICE.lowercase()
            val module = File(REGIONAL_MODULE_DIR)
            val database = File(module, "system/vendor/firmware/carrierconfig/cfg.db")
            val removalPending = File(module, "remove").isFile
            val pendingReboot = File(module, ".pending_reboot").isFile
            return JSONObject()
                .put("supported", device in TENSOR_DEVICES)
                .put("magiskAvailable", detectModuleManager() != null)
                .put("rootManager", detectModuleManager()?.label.orEmpty())
                .put("sourceAvailable", File(REGIONAL_SOURCE_DB).isFile)
                .put("installed", database.isFile)
                .put("removalPending", removalPending)
                .put("rebootRequired", rebootRequired ?: (pendingReboot || removalPending))
                .put("device", device)
                .put("sourceSha256", File(module, "source.sha256").readSmallText())
                .put("patchedSha256", File(module, "patched.sha256").readSmallText())
                .put("message", message)
        }

        private fun regionalPatchStatusMessage(): String {
            val device = Build.DEVICE.lowercase()
            val module = File(REGIONAL_MODULE_DIR)
            return when {
                device !in TENSOR_DEVICES -> "This device is not in the validated Tensor Pixel list."
                detectModuleManager() == null ->
                    "A module manager (Magisk, KernelSU or APatch) is required; Shizuku cannot overlay vendor firmware."
                !File(REGIONAL_SOURCE_DB).isFile -> "The Tensor carrier database was not found on this firmware."
                File(module, "remove").isFile -> "Removal is scheduled. Reboot to restore the stock modem database."
                File(module, ".pending_reboot").isFile -> "Patch installed. Reboot is required before it becomes active."
                File(module, "system/vendor/firmware/carrierconfig/cfg.db").isFile ->
                    "The systemless regional modem compatibility patch is installed."
                else -> "Ready for schema validation and systemless installation."
            }
        }

        private fun cacheDirectory(): File =
            File("/data/local/tmp/pixelims5g").apply {
                check(mkdirs() || isDirectory) { "Unable to access the root staging directory." }
            }

        private fun findExecutable(paths: List<String>): String? =
            paths.firstOrNull { File(it).canExecute() }

        private fun File.readSmallText(): String =
            runCatching { if (isFile && length() < 512) readText().trim() else "" }.getOrDefault("")

        private fun File.sha256(): String {
            val digest = MessageDigest.getInstance("SHA-256")
            inputStream().buffered().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        private val ALLOWED_SERVICES = setOf(
            "carrier_config",
            "phone",
            "iphonesubinfo",
            "isub",
            "power",
            "activity",
            "wifi",
            "statusbar",
            "telephony.oem.oemrilhook",
        )

        private val ALLOWED_PROPERTIES = setOf(
            "persist.dbg.ims_volte_enable",
            "persist.dbg.volte_avail_ovr",
            "persist.dbg.wfc_avail_ovr",
            "persist.dbg.vt_avail_ovr",
            "persist.radio.is_vonr_enabled_0",
            "persist.radio.is_vonr_enabled_1",
        )
        private val ALLOWED_PROPERTY_VALUES = setOf("", "0", "1", "false", "true")

        private const val REGIONAL_SOURCE_DB = "/vendor/firmware/carrierconfig/cfg.db"
        private const val REGIONAL_MODULE_DIR = "/data/adb/modules/pixelims5g_region"
        private val MAGISK_CANDIDATES = listOf(
            "/system_ext/bin/magisk",
            "/sbin/magisk",
            "/data/adb/magisk/magisk",
        )
        private val MAGISK_POLICY_CANDIDATES = listOf(
            "/system_ext/bin/magiskpolicy",
            "/sbin/magiskpolicy",
            "/data/adb/magisk/magiskpolicy",
            "/data/adb/ap/bin/magiskpolicy",
        )
        private const val KSUD = "/data/adb/ksud"
        private const val APD = "/data/adb/apd"
        private val TENSOR_DEVICES = setOf(
            "oriole", "raven", "bluejay",
            "panther", "cheetah", "lynx", "tangorpro", "felix",
            "shiba", "husky",
            "tokay", "caiman", "komodo", "comet",
            "mustang", "blazer", "rango",
        )

        private val DIAGNOSTIC_COMMANDS = mapOf(
            "registry" to arrayOf("/system/bin/dumpsys", "telephony.registry"),
            "physical" to arrayOf("/system/bin/dumpsys", "telephony.registry"),
            "phone" to arrayOf("/system/bin/dumpsys", "activity", "service", "com.android.phone"),
            "carrier" to arrayOf("/system/bin/dumpsys", "carrier_config"),
            "properties" to arrayOf("/system/bin/getprop"),
            "radio" to arrayOf("/system/bin/logcat", "-b", "radio", "-d", "-v", "threadtime", "-t", "1200"),
        )
        private val DIAGNOSTIC_TERMS = Regex(
            "nr|5g|endc|en-dc|dcnr|dual.?connect|scg|secondary.?cell|reject|fail|cause|" +
                "registration|servicestate|physicalchannel|carrier.?aggregation|iwlan|wfc|" +
                "vowifi|epdg|ike|qns|ims",
            RegexOption.IGNORE_CASE,
        )
        private val LONG_IDENTIFIER = Regex("(?<![A-Za-z])\\+?\\d[\\d -]{8,}\\d")
        private val SENSITIVE_FIELD = Regex(
            "(?i)\\b(m?iccid|m?imsi|imei|meid|msisdn|phoneNumber|line1Number|" +
                "subscriberId)\\s*[=:]\\s*[^,\\s}]+",
        )
        private val IPV4 = Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")
        private val IPV6 = Regex("\\b(?:[0-9a-fA-F]{1,4}:){2,}[0-9a-fA-F:]{0,39}\\b")
        private val RADIO_PROPERTY = Regex(
            "(?i)^\\[(?:gsm\\.(?:operator|sim\\.operator)\\.(?:numeric|iso-country)|" +
                "gsm\\.version\\.baseband|persist\\.(?:radio|vendor\\.radio|vendor\\.ril|vendor\\.modem)\\.|" +
                "ro\\.(?:baseband|build\\.expect\\.baseband|carrier|vendor\\.config\\.build_carrier)|" +
                "vendor\\.ril\\.(?:modem\\.cfg|sim)\\.carrier_id|vendor\\.radio\\.|ril\\.)",
        )
        private val CARRIER_CONFIG_DIAGNOSTIC = Regex(
            "(?i)5g|\\bnr[_ -]|endc|en-dc|dcnr|vonr|volte|wfc|wifi.?call|ims|" +
                "carrier.?id|subscription|sub.?id|config.?package|lte.?plus|lte_ca|" +
                "advanced.?band|unmetered.?nr|rrc",
        )

        /**
         * Root radio output can contain subscriber identifiers. Return only relevant lines and
         * redact long numeric values before the data enters the app process or a shared report.
         */
        private fun sanitizeDiagnostic(raw: String): String =
            raw.lineSequence()
                .filter { DIAGNOSTIC_TERMS.containsMatchIn(it) }
                .flatMap { line ->
                    if (line.length <= 700) {
                        sequenceOf(line)
                    } else {
                        DIAGNOSTIC_TERMS.findAll(line).take(12).map { match ->
                            line.substring(
                                (match.range.first - 140).coerceAtLeast(0),
                                (match.range.last + 320).coerceAtMost(line.length),
                            )
                        }
                    }
                }
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(260)
                .joinToString("\n") {
                    IPV6.replace(
                        IPV4.replace(
                            SENSITIVE_FIELD.replace(
                                LONG_IDENTIFIER.replace(it, "[redacted-id]"),
                            ) { match -> "${match.groupValues[1]}=[redacted]" },
                            "[redacted-ip]",
                        ),
                        "[redacted-ip]",
                    )
                }
                .take(180_000)

        private fun extractRadioProperties(raw: String): String =
            raw.lineSequence()
                .map(String::trim)
                .filter(RADIO_PROPERTY::containsMatchIn)
                .map { line ->
                    SENSITIVE_FIELD.replace(LONG_IDENTIFIER.replace(line, "[redacted-id]")) { match ->
                        "${match.groupValues[1]}=[redacted]"
                    }
                }
                .distinct()
                .sorted()
                .take(180)
                .joinToString("\n")
                .ifBlank { "No matching modem/carrier properties were exposed." }

        private fun extractCarrierConfig(raw: String): String =
            raw.lineSequence()
                .map(String::trim)
                .filter { it.isNotBlank() && CARRIER_CONFIG_DIAGNOSTIC.containsMatchIn(it) }
                .map { line ->
                    SENSITIVE_FIELD.replace(LONG_IDENTIFIER.replace(line, "[redacted-id]")) { match ->
                        "${match.groupValues[1]}=[redacted]"
                    }
                }
                .distinct()
                .take(500)
                .joinToString("\n")
                .take(180_000)
                .ifBlank { "No matching active CarrierConfig evidence was exposed." }

        private fun extractPhysicalChannels(raw: String): String {
            val scope = "scope=device-global TelephonyRegistry; subscription/phone provenance is not exposed by this API"
            val entries = Regex("\\{mConnectionStatus=[^}]+\\}")
                .findAll(raw)
                .map { it.value }
                .distinct()
                .toList()
            if (entries.isEmpty()) return "$scope\nNo active PhysicalChannelConfig entries were reported."
            fun field(entry: String, name: String): String =
                Regex("$name=([^,}]+)").find(entry)?.groupValues?.get(1)?.trim().orEmpty().ifBlank { "—" }
            return scope + "\n" + entries.mapIndexed { index, entry ->
                listOf(
                    "entry=${index + 1}",
                    "status=${field(entry, "mConnectionStatus")}",
                    "RAT=${field(entry, "mNetworkType")}",
                    "band=${field(entry, "mBand")}",
                    "PCI=${field(entry, "mPhysicalCellId")}",
                    "DL-channel=${field(entry, "mDownlinkChannelNumber")}",
                    "UL-channel=${field(entry, "mUplinkChannelNumber")}",
                    "DL-kHz=${field(entry, "mCellBandwidthDownlinkKhz")}",
                    "UL-kHz=${field(entry, "mCellBandwidthUplinkKhz")}",
                    "DL-frequency-kHz=${field(entry, "mDownlinkFrequency")}",
                    "UL-frequency-kHz=${field(entry, "mUplinkFrequency")}",
                ).joinToString(", ")
            }.joinToString("\n")
        }
    }
}

/** The root solutions that can mount a systemless module from /data/adb/modules. */
private enum class ModuleManager(val label: String, val defaultDomain: String) {
    MAGISK("Magisk", "magisk"),
    KERNELSU("KernelSU", "su"),
    APATCH("APatch", "su"),
}
