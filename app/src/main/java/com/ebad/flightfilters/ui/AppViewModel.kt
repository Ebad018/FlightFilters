package com.ebad.flightfilters.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ebad.flightfilters.FlightFiltersApp
import com.ebad.flightfilters.data.AppSettings
import com.ebad.flightfilters.data.DealsResult
import com.ebad.flightfilters.data.FareSource
import com.ebad.flightfilters.data.Itinerary
import com.ebad.flightfilters.data.LiveCheck
import com.ebad.flightfilters.data.Preset
import com.ebad.flightfilters.data.Query
import com.ebad.flightfilters.data.ScanResult
import com.ebad.flightfilters.data.SettingsStore
import com.ebad.flightfilters.data.FxRates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface Load<out T> {
    data object Idle : Load<Nothing>
    data object Loading : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

enum class ChatAction { NONE, OPEN_NETWORK, OPEN_DEALS }

data class ChatItem(
    val id: Long,
    val fromUser: Boolean,
    val text: String,
    val pending: Boolean = false,
    val error: Boolean = false,
    val action: ChatAction = ChatAction.NONE,
    /** Shown while working: "Scanning BSL · ZRH → Lisbon…" */
    val status: String? = null,
)

data class LastMinute(val items: List<Itinerary>, val sources: Set<FareSource>, val fetchedAt: Long)

class AppViewModel(private val app: FlightFiltersApp) : ViewModel() {
    private val store = app.store
    private val repo = app.repository

    val settings: StateFlow<AppSettings?> = store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val query: StateFlow<Query> = store.query.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsStore.defaultQuery())
    val presets: StateFlow<List<Preset>> = store.presets.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _scan = MutableStateFlow<Load<ScanResult>>(Load.Idle)
    val scan = _scan.asStateFlow()

    private val _lastMinute = MutableStateFlow<Load<LastMinute>>(Load.Idle)
    val lastMinute = _lastMinute.asStateFlow()

    private val _live = MutableStateFlow<Map<String, Load<LiveCheck>>>(emptyMap())
    val live = _live.asStateFlow()

    private val _eurRate = MutableStateFlow(FxRates.FALLBACK)
    val eurRate = _eurRate.asStateFlow()

    /** True once the ECB rate has loaded; false means [eurRate] is the offline approximation. */
    private val _eurRateLive = MutableStateFlow(false)
    val eurRateLive = _eurRateLive.asStateFlow()

    init {
        viewModelScope.launch {
            app.fx.chfToEur()?.let { _eurRate.value = it; _eurRateLive.value = true }
        }
        // Earlier versions seeded every plan with Basel + Zürich. Drop the unasked-for Zürich line once.
        viewModelScope.launch {
            val s = store.settings.first()
            if (!s.migratedHomeOnly) {
                val q = store.query.first()
                if (q.origins.map { it.iata } == listOf(s.homeIata, "ZRH")) store.saveQuery(q.copy(origins = q.origins.take(1)))
                store.saveSettings(s.copy(migratedHomeOnly = true))
            }
        }
    }

    private suspend fun currentSettings(): AppSettings = settings.value ?: store.settings.first()

    fun updateSettings(transform: (AppSettings) -> AppSettings) = viewModelScope.launch {
        store.saveSettings(transform(currentSettings()))
    }

    fun updateQuery(transform: (Query) -> Query) = viewModelScope.launch {
        store.saveQuery(transform(query.value))
    }

    fun runScan(q: Query = query.value) = viewModelScope.launch {
        if (q.activeOrigins.isEmpty()) {
            _scan.value = Load.Failed("Switch on at least one departure airport in Plan.")
            return@launch
        }
        _scan.value = Load.Loading
        _live.value = emptyMap()
        _scan.value = runCatching { Load.Ready(repo.scan(q, currentSettings())) }
            .getOrElse { Load.Failed(it.message ?: "The scan failed") }
    }

    private var lastMinuteKey = ""

    fun loadLastMinute(force: Boolean = false) = viewModelScope.launch {
        val s = currentSettings()
        val key = listOf(dealOrigins(s), dealDests(s), s.lastMinuteDays).toString()
        if (!force && _lastMinute.value is Load.Ready && key == lastMinuteKey) return@launch
        lastMinuteKey = key
        _lastMinute.value = Load.Loading
        _lastMinute.value = runCatching {
            val items = repo.lastMinute(dealOrigins(s), dealDests(s), s, s.lastMinuteDays)
            Load.Ready(LastMinute(items, items.map { it.fare.source }.toSet(), System.currentTimeMillis()))
        }.getOrElse { Load.Failed(it.message ?: "Couldn't load departures") }
    }

    /** Home only, unless the owner switched on nearby airports. */
    fun dealOrigins(s: AppSettings? = settings.value): List<String> {
        val home = s?.homeIata ?: "BSL"
        return if (s?.includeNearby == true) (listOf(home) + com.ebad.flightfilters.data.Airports.nearby.map { it.code }).distinct() else listOf(home)
    }

    /** The Plan's destinations, when Deals is set to follow the Plan; otherwise anywhere. */
    fun dealDests(s: AppSettings? = settings.value): List<String> =
        if (s?.dealsFollowPlan != false) query.value.dests else emptyList()

    private fun placeWords(code: String): List<String> {
        val e = com.ebad.flightfilters.data.AirportDb.get(app).get(code)
        val generic = setOf("airport", "international", "regional", "municipal", "airfield", "(all", "airports)")
        return listOfNotNull(e?.city?.takeIf { it.isNotBlank() } ?: com.ebad.flightfilters.data.Airports.cityOf(code)) +
            (e?.name?.split(' ', '-', '/')?.filter { it.length >= 5 && it.lowercase() !in generic } ?: emptyList())
    }

    private var dealsKey = ""

    fun loadDeals(from: java.time.LocalDate, until: java.time.LocalDate, force: Boolean = false) = viewModelScope.launch {
        val s = currentSettings()
        val q = query.value
        val key = listOf(from, until, dealOrigins(s), dealDests(s), s.dealsReturn, q.nightsMin, q.nightsMax).toString()
        if (!force && _deals.value is Load.Ready && dealsKey == key) return@launch
        dealsKey = key
        _deals.value = Load.Loading
        _deals.value = runCatching {
            Load.Ready(repo.deals(dealOrigins(s), dealDests(s), !s.dealsReturn, q.nightsMin..maxOf(q.nightsMin, q.nightsMax), s, from, until, ::placeWords))
        }.getOrElse { Load.Failed(it.message ?: "Couldn't load deals") }
    }

    // ---- Loops ----

    private val _loops = MutableStateFlow<Load<List<com.ebad.flightfilters.data.LoopTrip>>>(Load.Idle)
    val loops = _loops.asStateFlow()
    private val _loopProgress = MutableStateFlow<String?>(null)
    val loopProgress = _loopProgress.asStateFlow()
    private var loopJob: kotlinx.coroutines.Job? = null

    fun findLoops(req: com.ebad.flightfilters.data.LoopRequest) {
        loopJob?.cancel()
        loopJob = viewModelScope.launch {
            _loops.value = Load.Loading
            _loopProgress.value = "Starting…"
            _loops.value = runCatching { repo.loops.find(req) { p -> _loopProgress.value = p } }
                .fold(
                    { if (it.isEmpty()) Load.Failed("No loop closed back home with those settings. Try more nights per city, a wider start window, fewer cities, or nearby airports.") else Load.Ready(it) },
                    { if (it is kotlinx.coroutines.CancellationException) throw it; Load.Failed(it.message ?: "Loop search failed") },
                )
            _loopProgress.value = null
        }
    }

    private val _chat = MutableStateFlow<List<ChatItem>>(emptyList())
    val chat = _chat.asStateFlow()
    private var nextId = 1L

    /**
     * Ask the assistant. It decides between a search, a deals scan or a plain answer; the app runs
     * the scan with live sources and the model only explains those real results.
     */
    fun ask(question: String) = viewModelScope.launch {
        val q = question.trim()
        if (q.isEmpty() || _chat.value.any { it.pending }) return@launch
        val s = currentSettings()
        val history = _chat.value.filter { !it.pending && !it.error }.takeLast(10)
            .map { com.ebad.flightfilters.data.ChatTurn(if (it.fromUser) "user" else "assistant", it.text) } +
            com.ebad.flightfilters.data.ChatTurn("user", q)
        val user = ChatItem(nextId++, true, q)
        val replyId = nextId++
        _chat.update { it + user + ChatItem(replyId, false, "", pending = true, status = "Thinking…") }
        fun set(t: (ChatItem) -> ChatItem) = _chat.update { list -> list.map { if (it.id == replyId) t(it) else it } }

        try {
            val intent = app.assistant.understand(s, history)
            when (intent.action.lowercase()) {
                "search" -> {
                    val next = app.assistant.toQuery(intent, query.value)
                    set { it.copy(text = intent.reply, status = "Scanning ${routeTitle(next)}…") }
                    store.saveQuery(next)
                    _scan.value = Load.Loading
                    _live.value = emptyMap()
                    val result = repo.scan(next, s)
                    _scan.value = Load.Ready(result)
                    set { it.copy(status = "Reading the results…") }
                    val advice = runCatching { app.assistant.explain(s, q, app.assistant.scanDigest(result)) }
                        .getOrElse { app.assistant.plainScanSummary(result) + aiNote() }
                    set { it.copy(text = listOf(intent.reply, advice).filter { t -> t.isNotBlank() }.joinToString("\n\n"), pending = false, status = null, action = ChatAction.OPEN_NETWORK) }
                }
                "deals" -> {
                    val horizon = intent.horizonDays?.coerceIn(7, 365) ?: 30
                    set { it.copy(text = intent.reply, status = "Scanning ${dealOrigins(s).joinToString(" · ")} for drops and promos…") }
                    val qq = query.value
                    val d = repo.deals(dealOrigins(s), dealDests(s), !s.dealsReturn, qq.nightsMin..maxOf(qq.nightsMin, qq.nightsMax), s, java.time.LocalDate.now(), java.time.LocalDate.now().plusDays(horizon.toLong()), ::placeWords)
                    dealsKey = ""
                    _deals.value = Load.Ready(d)
                    set { it.copy(status = "Reading the results…") }
                    val advice = runCatching { app.assistant.explain(s, q, app.assistant.dealsDigest(d)) }
                        .getOrElse { app.assistant.plainDealsSummary(d) + aiNote() }
                    set { it.copy(text = listOf(intent.reply, advice).filter { t -> t.isNotBlank() }.joinToString("\n\n"), pending = false, status = null, action = ChatAction.OPEN_DEALS) }
                }
                else -> set { it.copy(text = intent.reply.ifBlank { "I'm not sure how to help with that. Try asking for flights or deals." }, pending = false, status = null) }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (_scan.value is Load.Loading) _scan.value = Load.Failed(e.message ?: "Scan failed")
            set { it.copy(text = e.message ?: "Something went wrong.", pending = false, error = true, status = null) }
        }
    }

    private fun aiNote() = "\n\n(The AI service didn't answer, so this summary is the app's own. A free Gemini or Groq key in Settings is more reliable.)"

    fun clearChat() { _chat.value = emptyList() }

    /** Drop the failed reply and the question it answered, so a retry doesn't duplicate them. */
    fun clearLastError() {
        val list = _chat.value
        if (list.lastOrNull()?.error == true) _chat.value = list.dropLast(1).let { l -> if (l.lastOrNull()?.fromUser == true) l.dropLast(1) else l }
    }

    /** One round-trip to the selected AI provider, for the Settings "Test" button. */
    suspend fun testAi(): String = runCatching {
        app.ai.complete(currentSettings(), "Reply with exactly: OK", listOf(com.ebad.flightfilters.data.ChatTurn("user", "ping")))
    }.fold({ "Connected: ${it.take(40)}" }, { "Failed: ${it.message}" })

    private val _deals = MutableStateFlow<Load<DealsResult>>(Load.Idle)
    val deals = _deals.asStateFlow()

    /**
     * Turn a deal into a plan: that origin and destination, a day either side, the same trip type
     * (and for a return, the same stay), then scan every airline.
     */
    fun planAround(item: Itinerary) = viewModelScope.launch {
        val f = item.fare
        val d = f.departAt.toLocalDate()
        val w = com.ebad.flightfilters.data.TimeWindow(d.minusDays(1).toString(), d.plusDays(1).toString(), 0, 24)
        val q = query.value
        val nights = f.returnAt?.let { java.time.temporal.ChronoUnit.DAYS.between(d, it.toLocalDate()).toInt() }
        val next = q.copy(
            origins = listOf(com.ebad.flightfilters.data.OriginLine(f.originAirport, w)),
            oneWay = f.returnAt == null,
            nightsMin = nights?.let { (it - 1).coerceAtLeast(1) } ?: q.nightsMin,
            nightsMax = nights?.let { it + 1 } ?: q.nightsMax,
        ).withDests(listOf(f.destinationAirport))
        store.saveQuery(next)
        runScan(next)
    }

    fun checkLive(item: Itinerary) = viewModelScope.launch {
        val key = item.fare.key
        _live.update { it + (key to Load.Loading) }
        val result = repo.liveCheck(item.fare, currentSettings(), query.value.filters.cabin)
        _live.update { it + (key to Load.Ready(result)) }
    }

    fun savePreset(name: String) = viewModelScope.launch {
        store.savePresets(presets.value + Preset(UUID.randomUUID().toString(), name.trim(), query.value))
    }

    fun renamePreset(id: String, name: String) = viewModelScope.launch {
        store.savePresets(presets.value.map { if (it.id == id) it.copy(name = name.trim()) else it })
    }

    fun deletePreset(id: String) = viewModelScope.launch {
        store.savePresets(presets.value.filterNot { it.id == id })
    }

    fun applyPreset(preset: Preset) = viewModelScope.launch {
        store.saveQuery(preset.query)
        runScan(preset.query)
    }

    fun clearCache() = viewModelScope.launch {
        repo.clearCache()
        _scan.value = Load.Idle
        _lastMinute.value = Load.Idle
        _deals.value = Load.Idle
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AppViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as FlightFiltersApp)
            }
        }
    }
}
