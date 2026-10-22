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
        val captureDurationMs: Long,
        val radio: SubscriptionModer.RadioDiagnostics,
    )

    private data class SampleEvidenceQuality(
        val selectedRegisteredCells: Int,
        val foreignRegisteredCells: Int,
        val channelAgreement: Boolean?,
        val bandAgreement: Boolean?,
    )

    private data class PhysicalEvidenceQuality(
        val entries: Int,
        val matchingSelectedServingCells: Int,
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
        wifiIsolation: String,
        onProgress: (Int, Int) -> Unit,
    ): FieldTestResult {
        val moder = SubscriptionModer(context, subscription.subscriptionId)
        val samples = mutableListOf<TimedSample>()
        val eventLog = CopyOnWriteArrayList<String>()
        val telephonyManager = context.getSystemService(TelephonyManager::class.java)
            .createForSubscriptionId(subscription.subscriptionId)
        val rootAtStart = withContext(Dispatchers.IO) {
            if (PrivilegeManager.activeMode == PrivilegeMode.ROOT && PrivilegeManager.isRootReady()) {
                captureRootEvidence()
            } else {
                emptyMap()
            }
        }
        val startedAtWall = System.currentTimeMillis()
        val startedAt = SystemClock.elapsedRealtime()
        val callback = FieldEventCallback(startedAt, eventLog)
        val callbackRegistered = runCatching {
            withContext(Dispatchers.Main) {
                telephonyManager.registerTelephonyCallback(context.mainExecutor, callback)
            }
        }.isSuccess
        var skippedScheduleSlots = 0
        try {
            for (index in 0 until SAMPLE_COUNT) {
                val scheduledAt = startedAt + index * SAMPLE_INTERVAL_MS
                val beforeDelay = SystemClock.elapsedRealtime()
                if (
                    index > 0 &&
                    index < SAMPLE_COUNT - 1 &&
                    beforeDelay >= scheduledAt + SAMPLE_INTERVAL_MS
                ) {
                    skippedScheduleSlots += 1
                    onProgress(index + 1, SAMPLE_COUNT)
                    continue
                }
                delay((scheduledAt - beforeDelay).coerceAtLeast(0L))
                val captured = withContext(Dispatchers.IO) {
                    val captureStartedAt = SystemClock.elapsedRealtime()
                    val radio = moder.getRadioDiagnostics()
                    TimedSample(
                        capturedAt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date()),
                        elapsedMs = SystemClock.elapsedRealtime() - startedAt,
                        captureDurationMs = SystemClock.elapsedRealtime() - captureStartedAt,
                        radio = radio,
                    )
                }
                samples += captured
                onProgress(index + 1, SAMPLE_COUNT)
            }
        } finally {
            if (callbackRegistered) {
                withContext(Dispatchers.Main) {
                    runCatching { telephonyManager.unregisterTelephonyCallback(callback) }
                }
            }
        }
        val finishedAtWall = System.currentTimeMillis()

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
                wifiIsolation,
                startedAtWall,
                finishedAtWall,
                skippedScheduleSlots,
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
        val sawConnectedNr = radios.any {
            it.displayTechnology.startsWith("5G") || it.nrBands.isNotEmpty()
        }
        val sawNrAvailableFlag = radios.any { it.nrAvailable == true }
        val sawEndc = radios.any { it.endcAvailable == true }
        val sawCa = radios.any { it.usingCarrierAggregation }
        val sawVoWifi = radios.any { it.imsRegistered && it.imsTransport == "IWLAN" }
        val nrFrequencies = radios.flatMap { radio ->
            radio.cells.filter { it.type == "NR" }.mapNotNull { it.frequencyKhz }
        }.distinct()
        val summary = listOfNotNull(
            if (sawConnectedNr) "5G connected" else null,
            if (!sawConnectedNr && sawEndc) "EN-DC advertised; no NR connection" else null,
            if (!sawConnectedNr && !sawEndc && sawNrAvailableFlag) {
                "NR availability flag set; EN-DC unavailable"
            } else {
                null
            },
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
        wifiIsolation: String,
        startedAtWall: Long,
        finishedAtWall: Long,
        skippedScheduleSlots: Int,
    ): String {
        val radios = samples.map { it.radio }
        val first = radios.first()
        val gates = moder.getRootForceReport(first)
        val bands = moder.getBandSelection()
        val nrModes = moder.getIntArrayValue(CarrierConfigManager.KEY_CARRIER_NR_AVAILABILITIES_INT_ARRAY)
        val allCells = radios.flatMap { it.cells }
            .distinctBy { "${it.type}:${it.operator}:${it.channel}:${it.pci}:${it.cellId}" }
        val cellInfoLteBands = radios.flatMap { it.lteBands.toList() }.distinct().sorted()
        val cellInfoNrBands = radios.flatMap { it.nrBands.toList() }.distinct().sorted()
        val servingLteBands = radios.flatMap { it.servingLteBands.toList() }.distinct().sorted()
        val servingNrBands = radios.flatMap { it.servingNrBands.toList() }.distinct().sorted()
        val nrChannels = allCells.filter { it.type == "NR" }
            .map { "${it.band}/NRARFCN ${it.channel}/${it.frequencyKhz?.let { khz -> "%.3f MHz".format(Locale.US, khz / 1000.0) } ?: "unknown frequency"}" }
            .distinct()
        val created = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())
        val homePlmn = "${subscription.mccString.orEmpty()}${subscription.mncString.orEmpty()}"
        val operationalPlmn = radios.asSequence()
            .flatMap { sequenceOf(it.registeredPlmn, it.operatorNumeric) }
            .mapNotNull { it?.let(::normalizePlmn) }
            .filter(String::isNotBlank)
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: homePlmn
        val evidenceQuality = radios.map { sampleEvidenceQuality(it, operationalPlmn) }
        val physicalQuality = physicalEvidenceQuality(rootAtEnd["physical"], radios, operationalPlmn)
        val connectedSaSamples = radios.count { it.dataRat == "NR" }
        val connectedNsaSamples = radios.count { it.displayTechnology == "5G NSA" }
        val nrAvailableFlagSamples = radios.count { it.nrAvailable == true }
        val endcSamples = radios.count { it.endcAvailable == true }
        val caSamples = radios.count { it.usingCarrierAggregation }
        val imsSamples = radios.count { it.imsRegistered }
        val voWifiSamples = radios.count { it.imsRegistered && it.imsTransport == "IWLAN" }
        val readableNrGates = gates.gates.filter { it.mask != null }
        val deviceNrGatesOpen = readableNrGates.isNotEmpty() && readableNrGates.all { it.nrAllowed }
        val nrDualConnectivity = moder.getNrDualConnectivityEnabled()
        val tensorCa = moder.getTensorLteCaEnabled()
        val volteAvailable = moder.getBooleanValue(CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL)
        val voWifiAvailable = moder.getBooleanValue(CarrierConfigManager.KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL)
        val voNrEnabled = moder.getBooleanValue(CarrierConfigManager.KEY_VONR_ENABLED_BOOL)
        val showVoWifiIcon =
            moder.getBooleanValue(CarrierConfigManager.KEY_SHOW_WIFI_CALLING_ICON_IN_STATUS_BAR_BOOL)
        val hideLtePlusIcon =
            moder.getBooleanValue(CarrierConfigManager.KEY_HIDE_LTE_PLUS_DATA_ICON_BOOL)
        val nrHalCapability = runCatching {
            context.getSystemService(TelephonyManager::class.java)
                .createForSubscriptionId(subscription.subscriptionId)
                .isRadioInterfaceCapabilitySupported(
                    "CAPABILITY_NR_DUAL_CONNECTIVITY_CONFIGURATION_AVAILABLE",
                )
        }.getOrNull()
        val regionalPatch =
            if (PrivilegeManager.activeMode == PrivilegeMode.ROOT && PrivilegeManager.isRootReady()) {
                PrivilegeManager.getRegionalModemPatchStatus()
            } else {
                null
            }
        val rootVoWifi =
            if (PrivilegeManager.activeMode == PrivilegeMode.ROOT && PrivilegeManager.isRootReady()) {
                PrivilegeManager.getRootVoWifiStatus(subscription.subscriptionId)
            } else {
                null
            }
        val testStart = formatWallTime(startedAtWall)
        val testEnd = formatWallTime(finishedAtWall)
        val servingSignal = radios.flatMap { radio ->
            radio.cells.filter { cell ->
                cell.registered && cellBelongsToSelectedPlmn(cell, operationalPlmn)
            }
        }
        val rsrpValues = servingSignal.mapNotNull { it.rsrp }
        val sinrValues = servingSignal.mapNotNull { it.sinr }
        val emptyCellSamples = radios.count { it.cells.isEmpty() }
        val slowSampleCount = samples.count { it.captureDurationMs >= SAMPLE_INTERVAL_MS }
        val captureDurations = samples.map { it.captureDurationMs.toInt() }
        val osDistribution = OsDistributionDetector.detect(context)

        return buildString {
            appendLine("Pixel IMS 5G field-test report")
            appendLine("Created: $created")
            appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("Android: ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
            appendLine("OS distribution: ${osDistribution.name}")
            appendLine("OS detection evidence: ${osDistribution.evidence}")
            appendLine(
                "Build ID/display/incremental: ${Build.ID} / ${Build.DISPLAY} / " +
                    Build.VERSION.INCREMENTAL,
            )
            appendLine("Build fingerprint: ${Build.FINGERPRINT}")
            appendLine("Security patch: ${Build.VERSION.SECURITY_PATCH}")
            appendLine("Baseband: ${Build.getRadioVersion().orEmpty()}")
            appendLine("Privilege backend: ${PrivilegeManager.activeMode}")
            appendLine("Test mode: $modeLabel")
            appendLine("Connectivity isolation: $wifiIsolation")
            appendLine("Test window: $testStart to $testEnd")
            appendLine()
            appendLine("SIM AND CARRIER (phone number, IMSI and ICCID intentionally excluded)")
            appendLine("Name: ${subscription.uniqueName}")
            appendLine("Subscription ID: ${subscription.subscriptionId}")
            appendLine("Slot: ${subscription.simSlotIndex}")
            appendLine("MCC-MNC: ${subscription.mccString.orEmpty()}-${subscription.mncString.orEmpty()}")
            appendLine("Carrier ID: ${subscription.carrierId}")
            appendLine("Country: ${subscription.countryIso}")
            appendLine()
            appendLine("EVIDENCE PROVENANCE AND QUALITY")
            appendLine(
                "Selected subscription: subId=${subscription.subscriptionId}, slot=${subscription.simSlotIndex}, " +
                    "home-PLMN=${homePlmn.ifBlank { "unknown" }}, " +
                    "operational-PLMN=${operationalPlmn.ifBlank { "unknown" }}",
            )
            if (homePlmn.isNotBlank() && operationalPlmn.isNotBlank() && homePlmn != operationalPlmn) {
                appendLine(
                    "PLMN relationship: the SIM home PLMN differs from the registered network. Serving-cell " +
                        "validation uses the operational PLMN; this can be legitimate for shared networks, " +
                        "national roaming, or migrated carrier arrangements.",
                )
            }
            appendLine(
                "ServiceState, NetworkRegistrationInfo, IMS state, and live callbacks: requested for the " +
                    "selected subscription.",
            )
            appendLine(
                "CellInfo: requested through the selected subscription, but Android/Tensor may return stale or " +
                    "device-wide DSDS cells; every sample below includes consistency checks.",
            )
            appendLine(
                "Root TelephonyRegistry, phone-service, PhysicalChannelConfig, and radio logs: device-global, " +
                    "may contain both SIMs and historical buffer entries, and are not used alone as proof for " +
                    "the selected SIM.",
            )
            appendLine(
                "CellInfo consistency: missing selected-PLMN serving cells=" +
                    "${evidenceQuality.count { it.selectedRegisteredCells == 0 }}/${samples.size}; " +
                    "channel disagreements=${evidenceQuality.count { it.channelAgreement == false }}" +
                    "/${samples.size}; band disagreements=${evidenceQuality.count { it.bandAgreement == false }}" +
                    "/${samples.size}; foreign registered cells=${evidenceQuality.sumOf { it.foreignRegisteredCells }}",
            )
            appendLine(
                "PhysicalChannelConfig consistency: entries=${physicalQuality.entries}; " +
                    "matching selected-SIM serving cells=${physicalQuality.matchingSelectedServingCells}; " +
                    "scope=device-global",
            )
            appendLine()
            appendLine("CONFIGURATION")
            appendLine("Carrier NR modes: ${nrModes.joinToString().ifBlank { "none" }} (1=NSA, 2=SA)")
            appendLine("VoLTE available: $volteAvailable")
            appendLine("VoWiFi available: $voWifiAvailable")
            appendLine("VoNR enabled: $voNrEnabled")
            appendLine("Show VoWiFi icon: $showVoWifiIcon")
            appendLine("Hide LTE+ icon: $hideLtePlusIcon")
            appendLine("Tensor CA node: ${tensorCa ?: "unavailable"}")
            appendLine("NR dual connectivity enabled: ${nrDualConnectivity ?: "unavailable"}")
            appendLine("Radio HAL EN-DC control capability: ${nrHalCapability ?: "unavailable"}")
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
                val lteState = it.mask?.let { _ -> it.lteAllowed.toString() } ?: "unknown"
                val nrState = it.mask?.let { _ -> it.nrAllowed.toString() } ?: "unknown"
                appendLine(
                    "Allowed reason ${it.reason} (${it.label}): mask=${it.mask ?: "unreadable"}, " +
                        "LTE=$lteState, NR=$nrState",
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
                appendLine("Regional modem compatibility patch:")
                appendLine(
                    "  device=${regionalPatch?.device}; supported=${regionalPatch?.supported}; " +
                        "Magisk=${regionalPatch?.magiskAvailable}; stock-db=${regionalPatch?.sourceAvailable}",
                )
                appendLine(
                    "  installed=${regionalPatch?.installed}; removal-pending=${regionalPatch?.removalPending}; " +
                        "reboot-required=${regionalPatch?.rebootRequired}",
                )
                appendLine(
                    "  source-sha256=${regionalPatch?.sourceSha256?.ifBlank { "not recorded" }}; " +
                        "patched-sha256=${regionalPatch?.patchedSha256?.ifBlank { "not recorded" }}",
                )
                appendLine("  status=${regionalPatch?.message}")
                appendLine("Root VoWiFi state:")
                appendLine(
                    "  setting=${rootVoWifi?.settingEnabled}; roaming=${rootVoWifi?.roamingEnabled}; " +
                        "mode=${rootVoWifi?.modeLabel}; roaming-mode=${rootVoWifi?.roamingModeLabel}",
                )
                appendLine(
                    "  IMS=${rootVoWifi?.registrationLabel}; transport=${rootVoWifi?.transportLabel}; " +
                        "active-IWLAN=${rootVoWifi?.isVoWifiActive}; Wi-Fi-state=${rootVoWifi?.wifiState}",
                )
                if (!rootVoWifi?.failureReason.isNullOrBlank()) {
                    appendLine("  recent modem-wide failure=${rootVoWifi?.failureReason}")
                }
            }
            appendLine()
            appendLine("TWO-MINUTE OBSERVATION")
            appendLine(
                "Samples captured: ${samples.size}/$SAMPLE_COUNT; skipped schedule slots: " +
                    "$skippedScheduleSlots; target interval: ${SAMPLE_INTERVAL_MS / 1000}s; " +
                    "actual test window: ${(finishedAtWall - startedAtWall) / 1000.0}s",
            )
            appendLine(
                "Sample acquisition duration: ${rangeText(captureDurations, "ms")}; " +
                    "captures at least ${SAMPLE_INTERVAL_MS}ms=$slowSampleCount/${samples.size}",
            )
            appendLine("Telephony callback registered: $callbackRegistered")
            appendLine(
                "Serving-registration LTE bands: " +
                    servingLteBands.joinToString { "B$it" }.ifBlank { "none" },
            )
            appendLine(
                "Serving-registration NR bands: " +
                    servingNrBands.joinToString { "n$it" }.ifBlank { "none" },
            )
            appendLine(
                "CellInfo LTE bands exposed: " +
                    cellInfoLteBands.joinToString { "B$it" }.ifBlank { "none" },
            )
            appendLine(
                "CellInfo NR bands exposed: " +
                    cellInfoNrBands.joinToString { "n$it" }.ifBlank { "none" },
            )
            appendLine("NR channels/frequencies observed: ${nrChannels.joinToString().ifBlank { "none" }}")
            appendLine("Unique cells exposed by Android: ${allCells.size}")
            appendLine(
                "CellInfo visibility: empty=$emptyCellSamples/${samples.size}; " +
                    if (emptyCellSamples == samples.size) {
                        "Android exposed no CellInfo records; serving bands below come from ServiceState."
                    } else {
                        "non-empty=${samples.size - emptyCellSamples}/${samples.size}."
                    },
            )
            appendLine(
                "State counts: 5G-SA=$connectedSaSamples/${samples.size}; " +
                    "5G-NSA=$connectedNsaSamples/${samples.size}; " +
                    "NR-availability-flag=$nrAvailableFlagSamples/${samples.size}; " +
                    "EN-DC=$endcSamples/${samples.size}; " +
                    "LTE-CA=$caSamples/${samples.size}; IMS=$imsSamples/${samples.size}; " +
                    "IMS-over-IWLAN=$voWifiSamples/${samples.size}",
            )
            appendLine(
                "Selected serving-cell signal: RSRP=${rangeText(rsrpValues, "dBm")}; " +
                    "SINR=${rangeText(sinrValues, "dB")}",
            )
            samples.forEachIndexed { index, sample ->
                val radio = sample.radio
                val quality = evidenceQuality[index]
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
                appendLine(
                    "Evidence check: selected-PLMN registered cells=${quality.selectedRegisteredCells}; " +
                        "foreign registered cells=${quality.foreignRegisteredCells}; " +
                        "ServiceState/CellInfo channel agreement=${quality.channelAgreement ?: "unknown"}; " +
                        "band agreement=${quality.bandAgreement ?: "unknown"}",
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
            }
            appendLine()
            appendLine("LIVE TELEPHONY CALLBACK TIMELINE")
            if (eventLog.isEmpty()) {
                appendLine(
                    "No callback events were delivered. Registration succeeded, but Android callbacks report " +
                        "changes rather than guaranteeing an initial snapshot; the timed polling samples remain " +
                        "the primary evidence.",
                )
            } else {
                eventLog.forEach(::appendLine)
            }
            appendLine()
            appendLine("AUTOMATIC INTERPRETATION")
            when {
                connectedSaSamples > 0 -> {
                    appendLine("Result code: CONNECTED_5G_SA")
                    appendLine("5G SA was connected in $connectedSaSamples/${samples.size} samples.")
                }
                connectedNsaSamples > 0 -> {
                    appendLine("Result code: CONNECTED_5G_NSA")
                    appendLine("5G NSA was connected in $connectedNsaSamples/${samples.size} samples.")
                }
                endcSamples > 0 -> {
                    appendLine("Result code: ENDC_ADVERTISED_NO_NR_CONNECTION")
                    appendLine(
                        "The selected LTE registration advertised EN-DC in $endcSamples/${samples.size} samples, " +
                            "but Android did not confirm an NR connection. No network rejection cause should be " +
                            "claimed unless a time-matched SCG/RRC rejection appears in the test-window evidence.",
                    )
                }
                nrAvailableFlagSamples > 0 -> {
                    appendLine("Result code: NR_AVAILABLE_FLAG_NO_ENDC")
                    appendLine(
                        "Android's DataSpecificRegistrationInfo reported isNrAvailable=true in " +
                            "$nrAvailableFlagSamples/${samples.size} samples, but the selected LTE registration " +
                            "never exposed EN-DC. This flag is not proof of a measurable NR cell, NSA eligibility, " +
                            "or an attempted SCG addition. No NSA rejection should be claimed because no EN-DC " +
                            "attachment path was exposed.",
                    )
                }
                else -> {
                    appendLine("Result code: LTE_ONLY_NO_NR_ADVERTISEMENT")
                    appendLine(
                        "No NR eligibility, EN-DC, or connected NR cell was exposed by the selected subscription " +
                            "during this test. This does not mean 5G is unsupported or unavailable elsewhere; it " +
                            "only describes this serving LTE anchor and test window.",
                    )
                }
            }
            appendLine(
                "Device-side NR gates: readable reasons open=$deviceNrGatesOpen; " +
                    "carrier NSA=${nrModes.contains(1)}; carrier SA=${nrModes.contains(2)}; " +
                    "NR dual connectivity=${nrDualConnectivity ?: "unavailable"}; " +
                    "radio HAL EN-DC control=${nrHalCapability ?: "unavailable"}; " +
                    "regional modem patch installed=${regionalPatch?.installed ?: "not applicable"}, " +
                        "active=${regionalPatch?.active ?: "not applicable"}, " +
                        "method=${regionalPatch?.mountMethod.orEmpty()}, " +
                        "boot-result=${regionalPatch?.bootMountStatus.orEmpty()}",
            )
            appendLine(
                if (caSamples > 0) {
                    "LTE carrier aggregation was active in $caSamples/${samples.size} samples; LTE+ is valid only " +
                        "for those samples."
                } else {
                    "LTE carrier aggregation was not active in the captured samples."
                },
            )
            appendLine(
                if (voWifiSamples > 0) {
                    "IMS registered through IWLAN in $voWifiSamples/${samples.size} samples; VoWiFi was active."
                } else {
                    "No IMS-over-IWLAN registration was captured. IMS registration over LTE does not prove VoWiFi."
                },
            )
            appendLine(
                when {
                    imsSamples == samples.size ->
                        "IMS remained registered for every sample."
                    imsSamples == 0 ->
                        "IMS was unregistered for every sample. CarrierConfig availability flags do not prove " +
                            "carrier provisioning or successful IMS registration."
                    else ->
                        "IMS was registered in $imsSamples/${samples.size} samples, indicating intermittent or " +
                            "transitioning IMS registration during the test."
                },
            )
            if (
                evidenceQuality.any { it.selectedRegisteredCells == 0 } ||
                evidenceQuality.any { it.channelAgreement == false || it.bandAgreement == false } ||
                evidenceQuality.any { it.foreignRegisteredCells > 0 } ||
                (
                    physicalQuality.entries > 0 &&
                        physicalQuality.matchingSelectedServingCells == 0
                    )
            ) {
                appendLine(
                    "Evidence warning: Android returned conflicting or cross-SIM CellInfo in part of this test. " +
                        "The selected subscription's ServiceState/NetworkRegistrationInfo drives the verdict; " +
                        "conflicting CellInfo and device-global root physical channels are retained for debugging " +
                        "but not treated as selected-SIM proof.",
                )
            }
            appendLine()
            appendLine("ROOT-ONLY TELEPHONY EVIDENCE")
            if (PrivilegeManager.activeMode == PrivilegeMode.ROOT && PrivilegeManager.isRootReady()) {
                appendLine(
                    "Scope: DEVICE-GLOBAL. These sources may contain both SIMs. Radio output is a historical " +
                        "ring buffer; only distinct lines that appeared between the start and end snapshots are " +
                        "shown as test-window candidates. They are supporting evidence, not selected-SIM proof.",
                )
                appendLine("Selected test window: $testStart to $testEnd")
                listOf("phone", "registry", "radio").forEach { source ->
                    appendLine("[$source-test-window-delta-device-global]")
                    appendLine(
                        evidenceDelta(rootAtStart[source], rootAtEnd[source])
                            .ifBlank {
                                "No new distinct matching lines were isolated. This is not proof that no event occurred."
                            },
                    )
                }
                listOf("physical", "carrier", "properties", "overlay").forEach { source ->
                    appendLine("[$source-end-snapshot-device-global]")
                    appendLine(rootAtEnd[source]?.ifBlank { "No matching lines." } ?: "Unavailable")
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

    private fun sampleEvidenceQuality(
        radio: SubscriptionModer.RadioDiagnostics,
        selectedPlmn: String,
    ): SampleEvidenceQuality {
        val registered = radio.cells.filter { it.registered }
        val selected = registered.filter { cellBelongsToSelectedPlmn(it, selectedPlmn) }
        val foreign = registered.count {
            normalizePlmn(it.operator).isNotBlank() && !cellBelongsToSelectedPlmn(it, selectedPlmn)
        }
        val channelAgreement = when {
            radio.channelNumber == null || selected.isEmpty() -> null
            else -> selected.any { it.channel == radio.channelNumber }
        }
        val serviceBands = buildSet {
            radio.servingLteBands.forEach { add("LTE:$it") }
            radio.servingNrBands.forEach { add("NR:$it") }
        }
        val cellBands = selected.mapNotNull { cell ->
            bandNumber(cell.band)?.let { "${cell.type}:$it" }
        }.toSet()
        val bandAgreement = when {
            serviceBands.isEmpty() || cellBands.isEmpty() -> null
            else -> serviceBands.any(cellBands::contains)
        }
        return SampleEvidenceQuality(
            selectedRegisteredCells = selected.size,
            foreignRegisteredCells = foreign,
            channelAgreement = channelAgreement,
            bandAgreement = bandAgreement,
        )
    }

    private fun physicalEvidenceQuality(
        physical: String?,
        radios: List<SubscriptionModer.RadioDiagnostics>,
        selectedPlmn: String,
    ): PhysicalEvidenceQuality {
        val selectedServingKeys = radios.flatMap { radio ->
            radio.cells.filter {
                it.registered && cellBelongsToSelectedPlmn(it, selectedPlmn)
            }.mapNotNull { cell ->
                bandNumber(cell.band)?.let { band -> "${cell.type}:$band:${cell.pci}" }
            }
        }.toSet()
        val entries = physical.orEmpty().lineSequence().mapNotNull { line ->
            val rat = Regex("""\bRAT=([^,]+)""").find(line)?.groupValues?.get(1)?.trim()
            val band = Regex("""\bband=([^,]+)""").find(line)?.groupValues?.get(1)?.trim()
            val pci = Regex("""\bPCI=([^,]+)""").find(line)?.groupValues?.get(1)?.trim()
            val normalizedRat = when {
                rat.equals("LTE", ignoreCase = true) -> "LTE"
                rat.equals("NR", ignoreCase = true) -> "NR"
                else -> null
            }
            val normalizedBand = band?.let(::bandNumber)
            if (normalizedRat == null || normalizedBand == null || pci.isNullOrBlank()) {
                null
            } else {
                "$normalizedRat:$normalizedBand:$pci"
            }
        }.toList()
        return PhysicalEvidenceQuality(
            entries = entries.size,
            matchingSelectedServingCells = entries.count(selectedServingKeys::contains),
        )
    }

    private fun cellBelongsToSelectedPlmn(
        cell: SubscriptionModer.CellSnapshot,
        selectedPlmn: String,
    ): Boolean {
        val cellPlmn = normalizePlmn(cell.operator)
        return selectedPlmn.isNotBlank() && cellPlmn == selectedPlmn
    }

    private fun normalizePlmn(value: String): String = value.filter(Char::isDigit)

    private fun bandNumber(value: String): Int? =
        value.dropWhile { !it.isDigit() }.takeWhile(Char::isDigit).toIntOrNull()

    private fun formatWallTime(epochMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).format(Date(epochMs))

    private fun rangeText(values: List<Int>, unit: String): String =
        if (values.isEmpty()) {
            "unavailable"
        } else {
            "${values.minOrNull()} to ${values.maxOrNull()} $unit"
        }

    private fun evidenceDelta(start: String?, end: String?): String {
        if (end == null) return "Unavailable"
        val startLines = start.orEmpty().lineSequence().map(String::trim).filter(String::isNotBlank).toSet()
        return end.lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .filterNot(startLines::contains)
            .distinct()
            .take(220)
            .joinToString("\n")
            .take(ROOT_SNAPSHOT_LIMIT)
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
        "overlay",
    )

    private val REPORT_SENSITIVE_FIELD = Regex(
        "(?i)\\b(m?iccid|m?imsi|imei|meid|msisdn|phoneNumber|line1Number|" +
            "subscriberId)\\s*[=:]\\s*[^,\\s}]+",
    )
}
