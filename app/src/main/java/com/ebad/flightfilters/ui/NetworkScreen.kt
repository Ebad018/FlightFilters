package com.ebad.flightfilters.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import com.ebad.flightfilters.data.Airlines
import com.ebad.flightfilters.data.AppSettings
import com.ebad.flightfilters.data.Recommender
import kotlinx.coroutines.launch
import com.ebad.flightfilters.data.Itinerary
import com.ebad.flightfilters.data.ScanResult
import java.time.LocalDate

private enum class Sort(val label: String) { BEST("Best"), CHEAPEST("Cheapest"), FASTEST("Fastest") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkScreen(vm: AppViewModel, settings: AppSettings, onOpenPlan: () -> Unit, onOpenSettings: () -> Unit) {
    val query by vm.query.collectAsState()
    val scan by vm.scan.collectAsState()
    val live by vm.live.collectAsState()
    val eurRate by vm.eurRate.collectAsState()
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var sort by rememberSaveable { mutableStateOf(Sort.BEST) }
    var day by rememberSaveable { mutableStateOf<String?>(null) }
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()

    LaunchedEffect(Unit) { if (scan is Load.Idle) vm.runScan() }

    androidx.compose.material3.Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(routeTitle(query), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(routeSubtitle(query), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.runScan() }) { Icon(Icons.Outlined.Refresh, "Scan again") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, "Settings") }
                },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.topAppBarColors(scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
    ) { pad ->
        when (val s = scan) {
            Load.Idle, Load.Loading -> Column(Modifier.padding(pad).padding(horizontal = 16.dp, vertical = 8.dp)) {
                ScanningDiagram(query)
                Spacer(Modifier.height(16.dp))
                Text(
                    "Scanning ${query.activeOrigins.size} lines across ${spanDays(query)} days of departures…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is Load.Failed -> Column(Modifier.padding(pad)) {
                EmptyLine("The scan didn't get through", s.message) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.runScan() }) { Text("Try again") }
                        OutlinedButton(onClick = onOpenPlan) { Text("Open Plan") }
                    }
                }
            }
            is Load.Ready -> Results(
                result = s.value,
                pad = pad,
                settings = settings,
                eurRate = eurRate,
                live = live,
                selectedKey = selectedKey,
                sort = sort,
                day = day?.let(LocalDate::parse),
                onSort = { sort = it },
                onDay = { d -> day = if (day == d?.toString()) null else d?.toString() },
                onSelect = { selectedKey = if (selectedKey == it.fare.key) null else it.fare.key },
                onTrace = { selectedKey = it.fare.key },
                onCheckLive = vm::checkLive,
                onOpenPlan = onOpenPlan,
            )
        }
    }
}

@Composable
private fun Results(
    result: ScanResult,
    pad: PaddingValues,
    settings: AppSettings,
    eurRate: Double,
    live: Map<String, Load<com.ebad.flightfilters.data.LiveCheck>>,
    selectedKey: String?,
    sort: Sort,
    day: LocalDate?,
    onSort: (Sort) -> Unit,
    onDay: (LocalDate?) -> Unit,
    onSelect: (Itinerary) -> Unit,
    onTrace: (Itinerary) -> Unit,
    onCheckLive: (Itinerary) -> Unit,
    onOpenPlan: () -> Unit,
) {
    val q = result.query
    if (result.itineraries.isEmpty()) {
        Column(Modifier.padding(pad)) {
            EmptyLine(
                "No fares match these filters",
                "Nothing came back for ${routeTitle(q)}. Widen the time windows, allow a change, or raise the price cap.",
            ) { Button(onClick = onOpenPlan) { Text("Adjust in Plan") } }
        }
        return
    }
    val sorted = remember(result, sort, day) {
        val base = if (day == null) result.itineraries else result.itineraries.filter { it.fare.departAt.toLocalDate() == day }
        when (sort) {
            Sort.BEST -> base.sortedBy { it.effectiveCostChf }
            Sort.CHEAPEST -> base.sortedBy { it.fare.priceChf }
            Sort.FASTEST -> base.sortedBy { it.totalMin }
        }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val traced = result.itineraries.firstOrNull { it.fare.key == selectedKey } ?: result.itineraries.first()
    // Rows above the itinerary list: diagram, summary, stamp, days, notices (title + n), list head.
    val headerCount = 5 + if (result.notices.isEmpty()) 0 else result.notices.size + 1
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 24.dp)) {
        item("diagram") {
            RouteDiagram(
                query = q,
                itineraries = result.itineraries,
                selectedKey = traced.fare.key,
                onSelect = onTrace,
                eurRate = eurRate,
                showEur = settings.showEur,
                modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp),
            )
        }
        item("summary") {
            TracedSummary(
                item = traced,
                best = result.itineraries.first(),
                ink = lineColor(q, traced.fare.origin.takeIf { o -> q.origins.any { it.iata == o } } ?: traced.fare.originAirport),
                onDetails = {
                    onTrace(traced)
                    val idx = sorted.take(60).indexOfFirst { it.fare.key == traced.fare.key }
                    if (idx >= 0) scope.launch { listState.animateScrollToItem(headerCount + idx) }
                },
            )
        }
        item("stamp") {
            SourceStamp(result.sources, result.oldestSeenAt, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            result.priceLevel?.let { level ->
                Text(
                    "Google says prices for this route are currently $level.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = when (level) {
                        "low" -> com.ebad.flightfilters.ui.theme.LocalLineInks.current.green
                        "high" -> com.ebad.flightfilters.ui.theme.LocalLineInks.current.scarlet
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (result.failedOrigins.isNotEmpty()) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(6.dp))
                    Text("Couldn't scan ${result.failedOrigins.joinToString()}; showing the other lines.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        item("days") { DayStrip(result, day, onDay) }
        if (result.notices.isNotEmpty()) {
            item("notices-title") { SectionTitle("Worth a look") }
            items(result.notices, key = { "n-" + it.kind + it.itinerary.fare.key }) { n ->
                ServiceNotice(n, onClick = { onTrace(n.itinerary) }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
        }
        item("list-head") {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${sorted.size} itineraries" + (day?.let { " on ${it.format(dayFmt)}" } ?: ""),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                SingleChoiceSegmentedButtonRow {
                    Sort.entries.forEachIndexed { i, s ->
                        SegmentedButton(
                            selected = sort == s,
                            onClick = { onSort(s) },
                            shape = SegmentedButtonDefaults.itemShape(i, Sort.entries.size),
                            icon = {},
                            label = { Text(s.label, style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }
            }
        }
        items(sorted.take(60), key = { it.fare.key }) { item ->
            ItineraryStrip(
                item = item,
                ink = lineColor(q, item.fare.origin.takeIf { o -> q.origins.any { it.iata == o } } ?: item.fare.originAirport),
                selected = item.fare.key == selectedKey,
                eurRate = eurRate,
                showEur = settings.showEur,
                live = live[item.fare.key],
                onClick = { onSelect(item) },
                onCheckLive = { onCheckLive(item) },
            )
            HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/** What the traced line is, in words, right under the diagram, so tracing answers in place. */
@Composable
private fun TracedSummary(item: Itinerary, best: Itinerary, ink: androidx.compose.ui.graphics.Color, onDetails: () -> Unit) {
    val f = item.fare
    val arrive = f.departAt.plusMinutes(f.durationMin.toLong())
    val vsBest = item.effectiveCostChf - best.effectiveCostChf
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineRoundel(f.originAirport, ink)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${f.departAt.format(dayFmt)} · ${f.departAt.format(timeFmt)}–${arrive.format(timeFmt)} · ${Airlines.name(f.airline)}",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (item.fare.key == best.fare.key) "Best trip cost on the network · ${Recommender.stopsLabel(f.transfers)}"
                else "${chf(vsBest)} more than the best once time is counted · ${Recommender.stopsLabel(f.transfers)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onDetails, modifier = Modifier.heightIn(min = 48.dp)) { Text("Details") }
    }
}

/** Cheapest fare per departure day, the user's window in full ink and the flex days around it dimmer. */
@Composable
private fun DayStrip(result: ScanResult, selected: LocalDate?, onDay: (LocalDate?) -> Unit) {
    val q = result.query
    val windowFrom = q.activeOrigins.minOf { it.window.from }
    val windowTo = q.activeOrigins.maxOf { it.window.to }
    val cheapest = result.dayPrices.values.minOrNull()
    val days = generateSequence(windowFrom.minusDays(q.flexDays.toLong())) { it.plusDays(1) }
        .takeWhile { !it.isAfter(windowTo.plusDays(q.flexDays.toLong())) }.toList()
    val scheme = MaterialTheme.colorScheme
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(days, key = { it.toString() }) { d ->
            val price = result.dayPrices[d]
            val inWindow = !d.isBefore(windowFrom) && !d.isAfter(windowTo)
            val isCheapest = price != null && price == cheapest
            val isSel = d == selected
            val bg = when {
                isCheapest -> scheme.primary
                inWindow -> scheme.surfaceContainerHigh
                else -> scheme.surface
            }
            val fg = if (isCheapest) scheme.onPrimary else if (inWindow) scheme.onSurface else scheme.onSurfaceVariant
            Column(
                Modifier
                    .width(56.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(bg)
                    .then(if (isSel) Modifier.border(2.dp, scheme.secondary, MaterialTheme.shapes.small) else Modifier)
                    .clickable(enabled = price != null) { onDay(d) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(d.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall, color = fg)
                Text(d.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium, color = fg)
                Text(
                    price?.let { "${it.toInt()}" } ?: "–",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isCheapest) FontWeight.Bold else FontWeight.Medium,
                    color = fg,
                )
            }
        }
    }
}

private fun spanDays(q: com.ebad.flightfilters.data.Query): Int {
    val o = q.activeOrigins
    if (o.isEmpty()) return 0
    val from = o.minOf { it.window.from }.minusDays(q.flexDays.toLong())
    val to = o.maxOf { it.window.to }.plusDays(q.flexDays.toLong())
    return (java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1).toInt()
}
