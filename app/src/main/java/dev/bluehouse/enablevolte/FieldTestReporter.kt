package dev.bluehouse.enablevolte

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.telephony.CarrierConfigManager
import android.telephony.CellInfo
import android.telephony.ServiceState
import android.telephony.SignalStrength
import android.telephony.SubscriptionInfo
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

data class FieldTestResult(
    val uri: Uri,
    val fileName: String,
    val summary: String,
)

object FieldTestReporter {
    const val SAMPLE_COUNT = 24
    private const val SAMPLE_INTERVAL_MS = 5_000L
    private const val ROOT_SNAPSHOT_LIMIT = 48_000

    private data class TimedSample(
        val capturedAt: String,
        val elapsedMs: Long,
        val radio: SubscriptionModer.RadioDiagnostics,
        val physicalChannels: String?,
        val telephonyRegistry: String?,
    )

    private class FieldEventCallback(
        private val startedAt: Long,
        private val events: CopyOnWriteArrayList<String>,
    ) : TelephonyCallback(),
        TelephonyCallback.ServiceStateListener,
        TelephonyCallback.DisplayInfoListener,
        TelephonyCallback.CellInfoListener,
        TelephonyCallback.SignalStrengthsListener {
        private fun record(message: String) {
            events += "+${SystemClock.elapsedRealtime() - startedAt}ms $message"
        }

        override fun onServiceStateChanged(serviceState: ServiceState) {
            record("ServiceState ${serviceState.toString().replace('\n', ' ')}")
        }

        override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
            record(
                "DisplayInfo network=${telephonyDisplayInfo.networkType}, " +
                    "override=${telephonyDisplayInfo.overrideNetworkType}",
            )
        }

        override fun onCellInfoChanged(cellInfo: List<CellInfo>) {
            val types = cellInfo.groupingBy { it.javaClass.simpleName }.eachCount()
            record("CellInfo count=${cellInfo.size}, types=$types")
        }

        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
            record("Signal level=${signalStrength.level}/4, dBm=${signalStrength.dbm}")
        }
    }

    suspend fun capture(
        context: Context,
        subscription: SubscriptionInfo,
        modeLabel: String = "Standard diagnostic",
        onProgress: (Int, Int) -> Unit,
    ): FieldTestResult {
        val moder = SubscriptionModer(context, subscription.subscriptionId)
        val samples = mutableListOf<TimedSample>()
        val eventLog = CopyOnWriteArrayList<String>()
        val startedAt = SystemClock.elapsedRealtime()
        val telephonyManager = context.getSystemService(TelephonyManager::class.java)
            .createForSubscriptionId(subscription.subscriptionId)
        val callback = FieldEventCallback(startedAt, eventLog)
        val callbackRegistered = runCatching {
            withContext(Dispatchers.Main) {
                telephonyManager.registerTelephonyCallback(context.mainExecutor, callback)
            }
        }.isSuccess
        val rootAtStart = withContext(Dispatchers.IO) {
            if (PrivilegeManager.activeMode == PrivilegeMode.ROOT && PrivilegeManager.isRootReady()) {
                captureRootEvidence()
            } else {
                emptyMap()
            }
        }
        try {
            repeat(SAMPLE_COUNT) { index ->
                val captured = withContext(Dispatchers.IO) {
                    val radio = moder.getRadioDiagnostics()
                    val rootReady =
                        PrivilegeManager.activeMode == PrivilegeMode.ROOT && PrivilegeManager.isRootReady()
                    TimedSample(
                        capturedAt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date()),
                        elapsedMs = SystemClock.elapsedRealtime() - startedAt,
                        radio = radio,
                        physicalChannels = if (rootReady) {
                            PrivilegeManager.getRootTelephonyDiagnostic("physical")?.take(ROOT_SNAPSHOT_LIMIT)
                        } else {
                            null
                        },
                        telephonyRegistry = if (rootReady) {
                            PrivilegeManager.getRootTelephonyDiagnostic("registry")?.take(ROOT_SNAPSHOT_LIMIT)
                        } else {
                            null
                        },
                    )
                }
                samples += captured
                onProgress(index + 1, SAMPLE_COUNT)
                if (index < SAMPLE_COUNT - 1) {
                    val nextSampleAt = startedAt + (index + 1L) * SAMPLE_INTERVAL_MS
                    delay((nextSampleAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
                }
            }
        } finally {
            if (callbackRegistered) {
                withContext(Dispatchers.Main) {
                    runCatching { telephonyManager.unregisterTelephonyCallback(callback) }
                }
            }
        }

        val rootAtEnd = withContext(Dispatchers.IO) {
            if (PrivilegeManager.activeMode == PrivilegeMode.ROOT && PrivilegeManager.isRootReady()) {
                captureRootEvidence()
            } else {
                emptyMap()
            }
        }
        val report = withContext(Dispatchers.IO) {
            buildReport(
                context,
                moder,
                subscription,
                samples,
                eventLog.toList(),
                callbackRegistered,
                rootAtStart,
                rootAtEnd,
                modeLabel,
            )
        }
        val safeReport = REPORT_SENSITIVE_FIELD.replace(report) { match ->
            "${match.groupValues[1]}=[redacted]"
        }
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val safeCarrier = subscription.uniqueName.replace(Regex("[^A-Za-z0-9._-]+"), "-")
        val fileName = "PixelIMS5G-${safeCarrier.ifBlank { "SIM" }}-$timestamp.txt"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(
                MediaStore.Downloads.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/Pixel IMS 5G",
            )
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val createdUri = requireNotNull(
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values),
            ) { "Android could not create the diagnostic report in Downloads" }
            try {
                resolver.openOutputStream(createdUri, "w")?.bufferedWriter(Charsets.UTF_8)?.use {
                    it.write(safeReport)
                } ?: error("Android could not open the diagnostic report")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(createdUri, values, null, null)
            } catch (error: Throwable) {
                resolver.delete(createdUri, null, null)
                throw error
            }
            createdUri
        }

        val radios = samples.map { it.radio }
        val sawNr = radios.any { it.displayTechnology.startsWith("5G") || it.nrBands.isNotEmpty() }
        val sawEndc = radios.any { it.endcAvailable == true }
        val sawCa = radios.any { it.usingCarrierAggregation }
        val sawVoWifi = radios.any { it.imsRegistered && it.imsTransport == "IWLAN" }
        val nrFrequencies = radios.flatMap { radio ->
            radio.cells.filter { it.type == "NR" }.mapNotNull { it.frequencyKhz }
        }.distinct()
        val summary = listOfNotNull(
            if (sawNr) "5G observed" else null,
            if (sawEndc) "EN-DC observed" else null,
            if (sawCa) "LTE+ observed" else null,
            if (sawVoWifi) "VoWiFi observed" else null,
            nrFrequencies.takeIf { it.isNotEmpty() }?.joinToString(
                prefix = "NR ",
                postfix = " MHz",
            ) { "%.3f".format(Locale.US, it / 1000.0) },
        ).joinToString().ifBlank { "No 5G, EN-DC, LTE+, or VoWiFi event was observed" }
        return FieldTestResult(uri, fileName, summary)
    }

    private fun buildReport(
        context: Context,
        moder: SubscriptionModer,
        subscription: SubscriptionInfo,
        samples: List<TimedSample>,
        eventLog: List<String>,
        callbackRegistered: Boolean,
        rootAtStart: Map<String, String?>,
        rootAtEnd: Map<String, String?>,
        modeLabel: String,
    ): String {
        val radios = samples.map { it.radio }
        val first = radios.first()
        val gates = moder.getRootForceReport(first)
        val bands = moder.getBandSelection()
        val nrModes = moder.getIntArrayValue(CarrierConfigManager.KEY_CARRIER_NR_AVAILABILITIES_INT_ARRAY)
        val allCells = radios.flatMap { it.cells }
            .distinctBy { "${it.type}:${it.operator}:${it.channel}:${it.pci}:${it.cellId}" }
        val lteBands = radios.flatMap { it.lteBands.toList() }.distinct().sorted()
        val nrBands = radios.flatMap { it.nrBands.toList() }.distinct().sorted()
        val nrChannels = allCells.filter { it.type == "NR" }
            .map { "${it.band}/NRARFCN ${it.channel}/${it.frequencyKhz?.let { khz -> "%.3f MHz".format(Locale.US, khz / 1000.0) } ?: "unknown frequency"}" }
            .distinct()
        val created = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())

        return buildString {
            appendLine("Pixel IMS 5G field-test report")
            appendLine("Created: $created")
            appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("Android: ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
            appendLine("Build fingerprint: ${Build.FINGERPRINT}")
            appendLine("Security patch: ${Build.VERSION.SECURITY_PATCH}")
            appendLine("Baseband: ${Build.getRadioVersion().orEmpty()}")
            appendLine("Privilege backend: ${PrivilegeManager.activeMode}")
            appendLine("Test mode: $modeLabel")
            appendLine()
            appendLine("SIM AND CARRIER (phone number, IMSI and ICCID intentionally excluded)")
            appendLine("Name: ${subscription.uniqueName}")
            appendLine("Subscription ID: ${subscription.subscriptionId}")
            appendLine("Slot: ${subscription.simSlotIndex}")
            appendLine("MCC-MNC: ${subscription.mccString.orEmpty()}-${subscription.mncString.orEmpty()}")
            appendLine("Carrier ID: ${subscription.carrierId}")
            appendLine("Country: ${subscription.countryIso}")
            appendLine()
            appendLine("CONFIGURATION")
            appendLine("Carrier NR modes: ${nrModes.joinToString().ifBlank { "none" }} (1=NSA, 2=SA)")
            appendLine("VoLTE available: ${moder.getBooleanValue(CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL)}")
            appendLine("VoWiFi available: ${moder.getBooleanValue(CarrierConfigManager.KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL)}")
            appendLine("VoNR enabled: ${moder.getBooleanValue(CarrierConfigManager.KEY_VONR_ENABLED_BOOL)}")
            appendLine("Show VoWiFi icon: ${moder.getBooleanValue(CarrierConfigManager.KEY_SHOW_WIFI_CALLING_ICON_IN_STATUS_BAR_BOOL)}")
            appendLine("Hide LTE+ icon: ${moder.getBooleanValue(CarrierConfigManager.KEY_HIDE_LTE_PLUS_DATA_ICON_BOOL)}")
            appendLine("Tensor CA node: ${moder.getTensorLteCaEnabled() ?: "unavailable"}")
            appendLine("NR dual connectivity enabled: ${moder.getNrDualConnectivityEnabled() ?: "unavailable"}")
            appendLine(
                "Radio HAL EN-DC control capability: " +
                    runCatching {
                        context.getSystemService(TelephonyManager::class.java)
                            .createForSubscriptionId(subscription.subscriptionId)
                            .isRadioInterfaceCapabilitySupported(
                                "CAPABILITY_NR_DUAL_CONNECTIVITY_CONFIGURATION_AVAILABLE",
                            )
                    }.getOrNull(),
            )
            appendLine(
                "Band restriction: LTE=${bands.lteBands.joinToString().ifBlank { "automatic" }}; " +
                    "NR=${bands.nrBands.joinToString().ifBlank { "automatic" }}",
            )
            appendLine(
                "Band readback: available=${bands.modemReadbackAvailable}; " +
                    "selection-known=${bands.knownSelection}; " +
                    "source=${when {
                        bands.modemReadbackAvailable -> "live modem"
                        bands.knownSelection -> "callback-confirmed cache"
                        else -> "automatic assumed"
                    }}",
            )
            gates.gates.forEach {
                appendLine(
                    "Allowed reason ${it.reason} (${it.label}): mask=${it.mask ?: "unreadable"}, " +
                        "LTE=${it.lteAllowed}, NR=${it.nrAllowed}",
                )
            }
            appendLine("Relevant effective CarrierConfig values:")
            RELEVANT_CARRIER_CONFIG_KEYS.forEach { key ->
                appendLine("  $key=${formatConfigValue(runCatching { moder.getValue(key) }.getOrNull())}")
            }
            if (PrivilegeManager.activeMode == PrivilegeMode.ROOT && PrivilegeManager.isRootReady()) {
                appendLine("Root radio/IMS properties:")
                ROOT_DIAGNOSTIC_PROPERTIES.forEach { key ->
                    appendLine("  $key=${PrivilegeManager.getRootSystemProperty(key) ?: "unavailable"}")
                }
                val regionalPatch = PrivilegeManager.getRegionalModemPatchStatus()
                appendLine("Regional modem compatibility patch:")
                appendLine(
                    "  device=${regionalPatch.device}; supported=${regionalPatch.supported}; " +
                        "Magisk=${regionalPatch.magiskAvailable}; stock-db=${regionalPatch.sourceAvailable}",
                )
                appendLine(
                    "  installed=${regionalPatch.installed}; removal-pending=${regionalPatch.removalPending}; " +
                        "reboot-required=${regionalPatch.rebootRequired}",
                )
                appendLine(
                    "  source-sha256=${regionalPatch.sourceSha256.ifBlank { "not recorded" }}; " +
                        "patched-sha256=${regionalPatch.patchedSha256.ifBlank { "not recorded" }}",
                )
                appendLine("  status=${regionalPatch.message}")
            }
            appendLine()
            appendLine("TWO-MINUTE OBSERVATION")
            appendLine(
                "Samples: ${samples.size}, target interval: ${SAMPLE_INTERVAL_MS / 1000}s, " +
                    "actual duration: ${samples.last().elapsedMs / 1000.0}s",
            )
            appendLine("Telephony callback registered: $callbackRegistered")
            appendLine("LTE bands observed: ${lteBands.joinToString { "B$it" }.ifBlank { "none" }}")
            appendLine("NR bands observed: ${nrBands.joinToString { "n$it" }.ifBlank { "none" }}")
            appendLine("NR channels/frequencies observed: ${nrChannels.joinToString().ifBlank { "none" }}")
            appendLine("Unique cells exposed by Android: ${allCells.size}")
            samples.forEachIndexed { index, sample ->
                val radio = sample.radio
                appendLine()
                appendLine("Sample ${index + 1} @ ${sample.capturedAt} (+${sample.elapsedMs}ms)")
                appendLine(
                    "Service=${radio.serviceState}; display=${radio.displayTechnology}; RAT=${radio.dataRat}; " +
                        "CA=${radio.usingCarrierAggregation}; NR-state=${radio.nrState}; " +
                        "NR-available=${radio.nrAvailable}; EN-DC=${radio.endcAvailable}; " +
                        "DCNR-restricted=${radio.dcNrRestricted}",
                )
                appendLine(
                    "Operator=${radio.operatorName}; PLMN=${radio.operatorNumeric}; " +
                        "registered-PLMN=${radio.registeredPlmn}; roaming=${radio.roaming}; " +
                        "channel=${radio.channelNumber}; duplex=${radio.duplexMode}",
                )
                appendLine(
                    "Registration reject-cause=${radio.registrationRejectCause}; " +
                        "services=${radio.registrationServices}; searching=${radio.networkSearching}; " +
                        "non-terrestrial=${radio.nonTerrestrialNetwork}",
                )
                appendLine("IMS registered=${radio.imsRegistered}; transport=${radio.imsTransport}")
                appendLine(
                    "Serving LTE=${radio.servingLteBands.joinToString { "B$it" }.ifBlank { "none" }}; " +
                        "Serving NR=${radio.servingNrBands.joinToString { "n$it" }.ifBlank { "none" }}",
                )
                appendLine("NetworkRegistrationInfo=${radio.registrationSummary.replace('\n', ' ')}")
                appendLine("ServiceState=${radio.serviceStateSummary.replace('\n', ' ')}")
                radio.cells.forEach { cell ->
                    appendLine(
                        "CELL type=${cell.type}, serving=${cell.registered}, operator=${cell.operator}, " +
                            "band=${cell.band}, all-bands=${cell.allBands}, channel=${cell.channel}, " +
                            "frequency-kHz=${cell.frequencyKhz}, PCI=${cell.pci}, TAC=${cell.tac}, " +
                            "cellId=${cell.cellId}, dBm=${cell.dbm}, RSRP=${cell.rsrp}, " +
                            "RSRQ=${cell.rsrq}, SINR=${cell.sinr}, RSSI=${cell.rssi}, CQI=${cell.cqi}, " +
                            "CSI-RSRP=${cell.csiRsrp}, CSI-RSRQ=${cell.csiRsrq}, CSI-SINR=${cell.csiSinr}, " +
                            "CSI-CQI-table=${cell.csiCqiTable}, CSI-CQI-report=${cell.csiCqiReport}, " +
                            "TA=${cell.timingAdvance}, bandwidth-kHz=${cell.bandwidthKhz}, " +
                            "connection-status=${cell.connectionStatus}",
                    )
                }
                appendLine("[physical-channels]")
                appendLine(sample.physicalChannels ?: "Unavailable")
                appendLine("[telephony-registry-change-source]")
                appendLine(sample.telephonyRegistry ?: "Unavailable")
            }
            appendLine()
            appendLine("LIVE TELEPHONY CALLBACK TIMELINE")
            if (eventLog.isEmpty()) {
                appendLine("No callback events were captured.")
            } else {
                eventLog.forEach(::appendLine)
            }
            appendLine()
            appendLine("AUTOMATIC INTERPRETATION")
            when {
                radios.any { it.dataRat == "NR" } ->
                    appendLine("5G SA was connected during the test.")
                radios.any { it.displayTechnology == "5G NSA" } ->
                    appendLine("5G NSA was connected during the test.")
                radios.any { it.endcAvailable == true } ->
                    appendLine("The LTE anchor advertised EN-DC, so NSA was available but not confirmed connected.")
                radios.any { it.nrAvailable == true } ->
                    appendLine(
                        "Android reported NR eligibility, but the serving registration reported EN-DC=false. " +
                            "The LTE anchor/network did not expose an NSA attachment path; NR was not connected.",
                    )
                else ->
                    appendLine("No NR eligibility, EN-DC, or connected NR cell was exposed during this test.")
            }
            appendLine(
                if (radios.any { it.usingCarrierAggregation }) {
                    "LTE carrier aggregation was active; the app should display LTE+."
                } else {
                    "LTE carrier aggregation was not active in the captured samples."
                },
            )
            appendLine(
                if (radios.any { it.imsRegistered && it.imsTransport == "IWLAN" }) {
                    "IMS registered through IWLAN; the app should display VoWiFi."
                } else {
                    "No IMS-over-IWLAN registration was captured."
                },
            )
            appendLine()
            appendLine("ROOT-ONLY TELEPHONY EVIDENCE")
            if (PrivilegeManager.activeMode == PrivilegeMode.ROOT && PrivilegeManager.isRootReady()) {
                appendLine(
                    "Start and end radio-log snapshots may overlap because the app intentionally does not clear " +
                        "Android's radio buffer. Compare timestamps with the two-minute sample timeline.",
                )
                rootAtStart.forEach { (source, evidence) ->
                    appendLine("[$source-start]")
                    appendLine(evidence?.ifBlank { "No matching lines." } ?: "Unavailable")
                }
                rootAtEnd.forEach { (source, evidence) ->
                    appendLine("[$source-end]")
                    appendLine(evidence?.ifBlank { "No matching NR/EN-DC/SCG lines were present." } ?: "Unavailable")
                }
            } else {
                appendLine(
                    "Unavailable in Shizuku mode. ServiceStateTracker/NetworkRegistrationManager live internals " +
                        "belong to the phone process; root is required for sanitized dumpsys/radio-log evidence.",
                )
            }
            appendLine()
            appendLine("LIMITATION")
            appendLine(
                "This report contains every cell Android exposed during the test, not every transmitter on air. " +
                    "Tensor modem firmware or network policy may hide rejected or unmeasured cells. A local setting " +
                    "cannot force a tower to advertise EN-DC or force the network to accept an NSA SCG request.",
            )
        }
    }

    private fun formatConfigValue(value: Any?): String = when (value) {
        is IntArray -> value.joinToString(prefix = "[", postfix = "]")
        is LongArray -> value.joinToString(prefix = "[", postfix = "]")
        is BooleanArray -> value.joinToString(prefix = "[", postfix = "]")
        is Array<*> -> value.joinToString(prefix = "[", postfix = "]")
        null -> "not exposed"
        else -> value.toString()
    }

    private fun captureRootEvidence(): Map<String, String?> =
        ROOT_EVIDENCE_SOURCES.associateWith { source ->
            PrivilegeManager.getRootTelephonyDiagnostic(source)?.take(ROOT_SNAPSHOT_LIMIT)
        }

    private val RELEVANT_CARRIER_CONFIG_KEYS = listOf(
        CarrierConfigManager.KEY_CARRIER_NR_AVAILABILITIES_INT_ARRAY,
        CarrierConfigManager.KEY_VONR_ENABLED_BOOL,
        CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL,
        CarrierConfigManager.KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL,
        "5g_icon_configuration_string",
        "5g_icon_display_grace_period_sec_int",
        "lte_endc_using_user_data_for_rrc_detection_bool",
        "nr_timers_reset_if_non_endc_and_rrc_idle_bool",
        "additional_nr_advanced_bands_int_array",
        "nr_advanced_threshold_bandwidth_khz_int",
        "nr_advanced_capable_pco_id_int",
        "unmetered_nr_nsa_bool",
        "unmetered_nr_nsa_mmwave_bool",
        "unmetered_nr_sa_bool",
    )

    private val ROOT_DIAGNOSTIC_PROPERTIES = listOf(
        "persist.dbg.ims_volte_enable",
        "persist.dbg.volte_avail_ovr",
        "persist.dbg.wfc_avail_ovr",
        "persist.dbg.vt_avail_ovr",
        "persist.radio.is_vonr_enabled_0",
        "persist.radio.is_vonr_enabled_1",
    )

    private val ROOT_EVIDENCE_SOURCES = listOf(
        "properties",
        "carrier",
        "phone",
        "registry",
        "physical",
        "radio",
    )

    private val REPORT_SENSITIVE_FIELD = Regex(
        "(?i)\\b(m?iccid|m?imsi|imei|meid|msisdn|phoneNumber|line1Number|" +
            "subscriberId)\\s*[=:]\\s*[^,\\s}]+",
    )
}
