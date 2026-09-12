package dev.bluehouse.enablevolte

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What kind of thing happened on the radio. */
enum class CellEventKind {
    REGISTERED,
    HANDOVER,
    BAND_CHANGE,
    ENDC,
    SIGNAL_DROP,
    LOST,
}

/**
 * One entry in the cell log.
 *
 * A reading tells you where you are now; this tells you how you got here, which
 * is usually the thing you actually wanted to know when 5G dropped.
 */
data class CellEvent(
    val at: Long,
    val kind: CellEventKind,
    val title: String,
    val detail: String,
) {
    val clock: String get() = TIME.format(Date(at))

    companion object {
        private val TIME = SimpleDateFormat("HH:mm:ss", Locale.US)
    }
}

data class NetworkState(
    val sampling: Boolean = false,
    val loading: Boolean = true,
    val available: Boolean = false,
    val message: String? = null,
    val serviceState: String = "",
    val serving: SubscriptionModer.CellSnapshot? = null,
    val anchor: SubscriptionModer.CellSnapshot? = null,
    val neighbours: List<SubscriptionModer.CellSnapshot> = emptyList(),
    val rsrpHistory: List<Int> = emptyList(),
    val events: List<CellEvent> = emptyList(),
    val sampleCount: Long = 0,
    val lastSampleAt: Long = 0,
) {
    /** Whether the phone currently has an NR leg, on either NSA or SA. */
    val nrAttached: Boolean get() = serving?.type?.contains("NR", ignoreCase = true) == true ||
        neighbours.any { it.registered && it.type.contains("NR", ignoreCase = true) }

    val historyMin: Int? get() = rsrpHistory.minOrNull()
    val historyMax: Int? get() = rsrpHistory.maxOrNull()
    val historyMean: Int? get() = if (rsrpHistory.isEmpty()) null else rsrpHistory.average().toInt()
}

/**
 * Samples the radio continuously and keeps what it saw.
 *
 * The app could already read every one of these fields, but only as a one-shot
 * snapshot taken when you happened to open a page. Monitoring means sampling on
 * an interval, keeping a window of history so a change has a shape, and
 * noticing when the serving cell is no longer the one it was.
 */
class NetworkMonitorViewModel(
    application: Application,
    private val subId: Int,
) : AndroidViewModel(application) {
    private val moder = SubscriptionModer(application, subId)

    private val _state = MutableStateFlow(NetworkState())
    val state: StateFlow<NetworkState> = _state.asStateFlow()

    private var pump: Job? = null

    init {
        start()
    }

    fun start() {
        if (pump?.isActive == true) return
        _state.value = _state.value.copy(sampling = true)
        pump = viewModelScope.launch {
            while (isActive) {
                sampleOnce()
                delay(SAMPLE_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        pump?.cancel()
        pump = null
        _state.value = _state.value.copy(sampling = false)
    }

    fun toggle() = if (_state.value.sampling) stop() else start()

    fun clearLog() {
        _state.value = _state.value.copy(events = emptyList())
    }

    private suspend fun sampleOnce() {
        val diagnostics = withContext(Dispatchers.IO) {
            runCatching { moder.getRadioDiagnostics() }.getOrNull()
        }
        val previous = _state.value

        if (diagnostics == null) {
            _state.value = previous.copy(
                loading = false,
                available = false,
                message = "The radio did not answer. Root or Shizuku has to be connected to read cell information.",
            )
            return
        }

        val cells = diagnostics.cells
        // The serving cell is the registered one; prefer NR when the phone has
        // both legs up, because on NSA the NR leg is the one carrying the data.
        val registered = cells.filter { it.registered }
        val serving = registered.firstOrNull { it.type.contains("NR", ignoreCase = true) }
            ?: registered.firstOrNull()
            ?: cells.firstOrNull()
        val anchor = registered.firstOrNull { !it.type.contains("NR", ignoreCase = true) }
            .takeIf { serving?.type?.contains("NR", ignoreCase = true) == true }

        val rsrp = serving?.rsrp ?: serving?.dbm
        val history = (previous.rsrpHistory + listOfNotNull(rsrp)).takeLast(HISTORY_LENGTH)
        val events = (detectChanges(previous, serving, anchor) + previous.events).take(EVENT_LIMIT)

        _state.value = previous.copy(
            loading = false,
            available = true,
            message = null,
            serviceState = diagnostics.serviceStateSummary,
            serving = serving,
            anchor = anchor,
            neighbours = cells.sortedWith(compareByDescending<SubscriptionModer.CellSnapshot> { it.registered }.thenByDescending { it.dbm }),
            rsrpHistory = history,
            events = events,
            sampleCount = previous.sampleCount + 1,
            lastSampleAt = System.currentTimeMillis(),
        )
    }

    /**
     * Compares this sample with the last one and reports what moved.
     *
     * Only real transitions are logged. A reading that wobbles by a few dB is
     * not an event, and a log that records every sample is a log nobody reads.
     */
    private fun detectChanges(
        previous: NetworkState,
        serving: SubscriptionModer.CellSnapshot?,
        anchor: SubscriptionModer.CellSnapshot?,
    ): List<CellEvent> {
        val now = System.currentTimeMillis()
        val out = mutableListOf<CellEvent>()
        val was = previous.serving

        if (serving == null) {
            if (was != null) {
                out += CellEvent(now, CellEventKind.LOST, "Service lost", "No registered cell reported")
            }
            return out
        }

        if (was == null) {
            out += CellEvent(
                now,
                CellEventKind.REGISTERED,
                "Registered",
                "${serving.type} ${serving.band} · ${serving.channel} · PCI ${serving.pci}",
            )
            return out
        }

        if (was.pci != serving.pci || was.cellId != serving.cellId) {
            out += CellEvent(
                now,
                CellEventKind.HANDOVER,
                "Handover",
                "PCI ${was.pci} → ${serving.pci}" + if (was.tac != serving.tac) " · TAC ${was.tac} → ${serving.tac}" else " · same TAC",
            )
        }
        if (was.band != serving.band || was.channel != serving.channel) {
            out += CellEvent(
                now,
                CellEventKind.BAND_CHANGE,
                "Band change",
                "${was.band} (${was.channel}) → ${serving.band} (${serving.channel})",
            )
        }

        val hadNr = was.type.contains("NR", ignoreCase = true)
        val hasNr = serving.type.contains("NR", ignoreCase = true)
        if (!hadNr && hasNr) {
            out += CellEvent(
                now,
                CellEventKind.ENDC,
                "NR leg up",
                if (anchor != null) "anchor ${anchor.band} → NR ${serving.band}" else "NR ${serving.band}",
            )
        } else if (hadNr && !hasNr) {
            out += CellEvent(now, CellEventKind.ENDC, "NR leg down", "fell back to ${serving.type} ${serving.band}")
        }

        val before = was.rsrp ?: was.dbm
        val after = serving.rsrp ?: serving.dbm
        if (before - after >= SIGNAL_DROP_DB) {
            out += CellEvent(now, CellEventKind.SIGNAL_DROP, "Signal dropped", "$before → $after dBm on ${serving.band}")
        }
        return out
    }

    override fun onCleared() {
        super.onCleared()
        pump?.cancel()
    }

    companion object {
        private const val SAMPLE_INTERVAL_MS = 2_000L
        private const val HISTORY_LENGTH = 90
        private const val EVENT_LIMIT = 60
        private const val SIGNAL_DROP_DB = 12

        fun factory(
            application: Application,
            subId: Int,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = NetworkMonitorViewModel(application, subId) as T
            }
    }
}
