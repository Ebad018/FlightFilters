package com.ebad.flightfilters.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import com.ebad.flightfilters.data.Airlines
import com.ebad.flightfilters.data.Airports
import com.ebad.flightfilters.data.AppSettings
import com.ebad.flightfilters.data.Itinerary
import com.ebad.flightfilters.data.Recommender
import java.time.LocalDate

/** The station's departure board: the cheapest ways out in the next few days, from every line. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DeparturesScreen(vm: AppViewModel, settings: AppSettings, onOpenSettings: () -> Unit) {
    val query by vm.query.collectAsState()
    val state by vm.lastMinute.collectAsState()
    val live by vm.live.collectAsState()
    val eurRate by vm.eurRate.collectAsState()
    var hidden by rememberSaveable { mutableStateOf(setOf<String>()) }
    var cap by rememberSaveable { mutableStateOf<Int?>(null) }
    var openKey by rememberSaveable { mutableStateOf<String?>(null) }
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    val origins = remember(settings.includeNearby, settings.homeIata) { vm.dealOrigins(settings) }
    val dests = if (settings.dealsFollowPlan) query.dests else emptyList()

    LaunchedEffect(settings.includeNearby, settings.dealsFollowPlan, query.dests, settings.lastMinuteDays) { vm.loadLastMinute() }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Departures", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Next ${settings.lastMinuteDays} days · " +
                                (if (origins.size == 1) "from ${Airports.cityOf(origins.first())}" else "${origins.size} airports near home") +
                                (if (dests.isNotEmpty()) " to " + dests.joinToString(" · ") { Airports.cityOf(it) } else ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { vm.loadLastMinute(force = true) }) { Icon(Icons.Outlined.Refresh, "Refresh departures") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, "Settings") }
                },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.topAppBarColors(scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
    ) { pad ->
        when (val s = state) {
            Load.Idle, Load.Loading -> Column(Modifier.padding(pad).padding(16.dp)) {
                ScanningDiagram(query.copy(origins = query.origins.map { it.copy(enabled = true) }))
                Text("Reading the board…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is Load.Failed -> Column(Modifier.padding(pad)) {
                EmptyLine("The board is dark", s.message) { Button(onClick = { vm.loadLastMinute(force = true) }) { Text("Try again") } }
            }
            is Load.Ready -> {
                val board = remember(s.value, hidden, cap) {
                    s.value.items
                        .filter { (it.fare.origin.takeIf { o -> o in origins } ?: it.fare.originAirport) !in hidden }
                        .filter { cap == null || it.fare.priceChf <= cap!! }
                        .groupBy { it.fare.departAt.toLocalDate() }
                        // Anywhere: one row per destination per day. Named destinations: every departure.
                        .mapValues { (_, v) -> v.sortedBy { it.fare.priceChf }.let { l -> if (dests.isEmpty()) l.distinctBy { it.fare.destination } else l }.take(12).sortedBy { it.fare.departAt } }
                        .toSortedMap()
                }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 24.dp)) {
                    item("filters") {
                        Spacer(Modifier.height(8.dp))
                        ScopeChips(settings, query.dests, "", vm::updateSettings, showTripType = false)
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (origins.size > 1) items(origins) { code ->
                                val on = code !in hidden
                                FilterChip(
                                    selected = on,
                                    onClick = { hidden = if (on) hidden + code else hidden - code },
                                    label = { Text(code) },
                                    leadingIcon = { LineDot(lineColor(query, code)) },
                                )
                            }
                            items(listOf(80, 150)) { c ->
                                FilterChip(selected = cap == c, onClick = { cap = if (cap == c) null else c }, label = { Text("Under CHF $c") })
                            }
                        }
                        SourceStamp(s.value.sources, s.value.fetchedAt, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    }
                    if (board.isEmpty()) {
                        item("empty") {
                            EmptyLine("Nothing leaving soon", "No cheap departures from these lines in the next ${settings.lastMinuteDays} days. Try more days in Settings or switch a line back on.")
                        }
                    }
                    board.forEach { (day, rows) ->
                        stickyHeader(key = "h-$day") { DayHeader(day) }
                        items(rows, key = { "d-" + it.fare.key }) { item ->
                            val code = item.fare.origin.takeIf { o -> o in origins } ?: item.fare.originAirport
                            if (openKey == item.fare.key) {
                                ItineraryStrip(
                                    item, lineColor(query, code), true, eurRate, settings.showEur, live[item.fare.key],
                                    onClick = { openKey = null }, onCheckLive = { vm.checkLive(item) },
                                )
                            } else {
                                DepartureRow(item, code, lineColor(query, code), onClick = { openKey = item.fare.key }, eurRate = eurRate, showEur = settings.showEur)
                            }
                            HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeader(day: LocalDate) {
    val today = LocalDate.now()
    val label = when (day) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> day.format(dayFmt)
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

@Composable
private fun DepartureRow(item: Itinerary, code: String, ink: androidx.compose.ui.graphics.Color, onClick: () -> Unit, eurRate: Double, showEur: Boolean) {
    val f = item.fare
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(f.departAt.format(timeFmt), style = MaterialTheme.typography.titleLarge, modifier = Modifier.widthIn(min = 64.dp))
        LineRoundel(code, ink)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(Airports.cityOf(f.destination), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${f.destinationAirport} · ${Airlines.name(f.airline)} · ${Recommender.stopsLabel(f.transfers)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PriceBlock(f.priceChf, eurRate, showEur)
    }
}

@Composable
fun LineDot(color: androidx.compose.ui.graphics.Color) {
    androidx.compose.foundation.Canvas(Modifier.size(10.dp)) { drawCircle(color) }
}
