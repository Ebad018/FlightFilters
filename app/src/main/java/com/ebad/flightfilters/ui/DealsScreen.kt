package com.ebad.flightfilters.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DepartureBoard
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.ebad.flightfilters.data.Airlines
import com.ebad.flightfilters.data.Airports
import com.ebad.flightfilters.data.AppSettings
import com.ebad.flightfilters.data.CountryLow
import com.ebad.flightfilters.data.Itinerary
import com.ebad.flightfilters.data.OriginLine
import com.ebad.flightfilters.data.PriceDrop
import com.ebad.flightfilters.data.PromoDeal
import com.ebad.flightfilters.data.Query
import com.ebad.flightfilters.data.TimeWindow
import com.ebad.flightfilters.ui.theme.LocalLineInks
import java.time.LocalDate

private val horizons = listOf(14 to "2 weeks", 30 to "1 month", 90 to "3 months", 182 to "6 months")

private fun utcMillis(d: LocalDate): Long = d.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
private fun utcDate(ms: Long): LocalDate = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneOffset.UTC).toLocalDate()

/** Any departure window: from tomorrow out to a year ahead. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeDialog(from: LocalDate, to: LocalDate, title: String, onDismiss: () -> Unit, onPick: (LocalDate, LocalDate) -> Unit) {
    val today = LocalDate.now()
    val state = androidx.compose.material3.rememberDateRangePickerState(
        initialSelectedStartDateMillis = utcMillis(from),
        initialSelectedEndDateMillis = utcMillis(to),
        yearRange = today.year..today.year + 1,
        selectableDates = object : androidx.compose.material3.SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val d = utcDate(utcTimeMillis)
                return !d.isBefore(today) && !d.isAfter(today.plusYears(1))
            }
        },
    )
    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            androidx.compose.material3.TextButton(
                enabled = state.selectedStartDateMillis != null,
                onClick = {
                    val s = utcDate(state.selectedStartDateMillis!!)
                    onPick(s, state.selectedEndDateMillis?.let(::utcDate) ?: s)
                },
            ) { Text("Use these dates") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        androidx.compose.material3.DateRangePicker(
            state = state,
            title = { Text(title, modifier = Modifier.padding(start = 24.dp, top = 16.dp)) },
            showModeToggle = true,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Deals: fares that are unusually low for their route, the cheapest country to reach, and hand-picked
 * promo and error fares, scanned from every airport within reach of home.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DealsScreen(vm: AppViewModel, settings: AppSettings, onPlanned: () -> Unit, onOpenSettings: () -> Unit, onOpenDepartures: () -> Unit) {
    val state by vm.deals.collectAsState()
    val live by vm.live.collectAsState()
    val eurRate by vm.eurRate.collectAsState()
    val query by vm.query.collectAsState()
    // A preset span (days ahead), or 0 for custom dates.
    var horizon by rememberSaveable { mutableIntStateOf(30) }
    var customFrom by rememberSaveable { mutableStateOf(LocalDate.now().plusDays(14).toString()) }
    var customTo by rememberSaveable { mutableStateOf(LocalDate.now().plusDays(60).toString()) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val spanFrom = if (horizon == 0) LocalDate.parse(customFrom) else LocalDate.now()
    val spanTo = if (horizon == 0) LocalDate.parse(customTo) else LocalDate.now().plusDays(horizon.toLong())
    var openKey by rememberSaveable { mutableStateOf<String?>(null) }
    var nearOnly by rememberSaveable { mutableStateOf(true) }
    val origins = remember(settings.includeNearby, settings.homeIata) { vm.dealOrigins(settings) }
    val dests = query.dests
    val following = settings.dealsFollowPlan && dests.isNotEmpty()
    val inks = LocalLineInks.current
    val inkOf: (String) -> Color = { code -> inks.forIndex(origins.indexOf(code).coerceAtLeast(0)) }
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()

    LaunchedEffect(spanFrom, spanTo, settings.includeNearby, settings.dealsReturn, settings.dealsFollowPlan, dests, query.nightsMin, query.nightsMax) {
        vm.loadDeals(spanFrom, spanTo)
    }
    if (picking) {
        DateRangeDialog(
            from = LocalDate.parse(customFrom), to = LocalDate.parse(customTo), title = "Departing between",
            onDismiss = { picking = false },
            onPick = { a, b -> customFrom = a.toString(); customTo = b.toString(); horizon = 0; picking = false },
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Deals", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "From " + (if (origins.size == 1) Airports.cityOf(origins.first()) else "${origins.size} airports near home") +
                                " to " + (if (following) dests.joinToString(" · ") { Airports.cityOf(it) } else "anywhere") +
                                if (settings.dealsReturn) " · return" else " · one way",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenDepartures) { Icon(Icons.Outlined.DepartureBoard, "Leaving soon (last-minute departures)") }
                    IconButton(onClick = { vm.loadDeals(spanFrom, spanTo, force = true) }) { Icon(Icons.Outlined.Refresh, "Refresh deals") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, "Settings") }
                },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.topAppBarColors(scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 24.dp)) {
            item("horizon") {
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    horizons.forEach { (days, label) ->
                        FilterChip(selected = horizon == days, onClick = { horizon = days }, label = { Text(label) })
                    }
                    FilterChip(
                        selected = horizon == 0,
                        onClick = { picking = true },
                        leadingIcon = { Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp)) },
                        label = { Text(if (horizon == 0) "${spanFrom.format(shortDayFmt)} – ${spanTo.format(shortDayFmt)}" else "Pick dates…") },
                    )
                }
            }
            item("scope") {
                ScopeChips(
                    settings = settings,
                    dests = dests,
                    nights = "${query.nightsMin}–${query.nightsMax} nights",
                    onSettings = vm::updateSettings,
                    showTripType = true,
                )
            }
            when (val s = state) {
                Load.Idle, Load.Loading -> item("loading") {
                    Column(Modifier.padding(16.dp)) {
                        ScanningDiagram(Query(origins = origins.take(4).map { OriginLine(it, TimeWindow(LocalDate.now().toString(), LocalDate.now().toString())) }))
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Checking every route from ${origins.size} airports, then comparing each fare with its route's usual price…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                is Load.Failed -> item("failed") {
                    EmptyLine("No deals came back", s.message) { Button(onClick = { vm.loadDeals(spanFrom, spanTo, force = true) }) { Text("Try again") } }
                }
                is Load.Ready -> {
                    val r = s.value
                    item("stamp") { SourceStamp(r.sources.ifEmpty { setOf(com.ebad.flightfilters.data.FareSource.RYANAIR) }, r.fetchedAt, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }

                    item("drops-title") { SectionTitle(if (following) "Cheapest to ${dests.joinToString(" · ") { Airports.cityOf(it) }}" else "Unusually cheap right now") }
                    if (r.drops.isEmpty()) item("drops-empty") {
                        Text(
                            if (following) "No fares found to ${dests.joinToString(", ") { Airports.cityOf(it) }} in this period. Try a longer period, allow nearby airports, or check Network for exact dates."
                            else "No fares found from your airports in this period.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    items(r.drops, key = { "drop-" + it.itinerary.fare.key }) { d ->
                        val f = d.itinerary.fare
                        if (openKey == f.key) {
                            Column {
                                ItineraryStrip(d.itinerary, inkOf(f.originAirport), true, eurRate, settings.showEur, live[f.key],
                                    onClick = { openKey = null }, onCheckLive = { vm.checkLive(d.itinerary) })
                                PlanThis(onClick = { vm.planAround(d.itinerary); onPlanned() })
                            }
                        } else {
                            DropRow(d, inkOf(f.originAirport), eurRate, settings.showEur, onClick = { openKey = f.key })
                        }
                        HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }

                    if (r.countries.isNotEmpty()) {
                        item("countries-title") { SectionTitle("Cheapest countries to reach") }
                        items(r.countries.take(15), key = { "c-" + it.country }) { c ->
                            CountryRow(c, inkOf(c.cheapest.fare.originAirport), onClick = { vm.planAround(c.cheapest); onPlanned() })
                        }
                    }

                    item("promos-title") {
                        Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            SectionTitle(if (following) "Promos mentioning ${dests.joinToString(" · ") { Airports.cityOf(it) }}" else "Promo & error fares", Modifier.weight(1f))
                            FilterChip(selected = nearOnly, onClick = { nearOnly = !nearOnly }, label = { Text("Near home") }, modifier = Modifier.padding(top = 18.dp))
                        }
                    }
                    r.promoError?.let { msg -> item("promo-err") { Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) } }
                    val promos = if (nearOnly) r.promos.filter { it.nearYou } else r.promos
                    if (promos.isEmpty() && r.promoError == null) item("promo-empty") {
                        Text(
                            when {
                                following && nearOnly -> "No current promos for ${dests.joinToString(", ") { Airports.cityOf(it) }} from near Basel. Switch off “Near home”, or turn off “Only my destinations” above."
                                following -> "No current promos mention ${dests.joinToString(", ") { Airports.cityOf(it) }}."
                                nearOnly -> "No promos from near Basel in the latest batch. Switch off “Near home” to see all of Europe."
                                else -> "No promos in the feed right now."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    items(promos, key = { "p-" + it.link }) { p ->
                        PromoRow(p, eurRate)
                        HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

/**
 * What Deals and Departures scan, in three switches: home airport only or nearby too, one way or
 * return, and whether a Plan's destinations narrow the results.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScopeChips(
    settings: AppSettings,
    dests: List<String>,
    nights: String,
    onSettings: ((AppSettings) -> AppSettings) -> Unit,
    showTripType: Boolean,
) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = settings.includeNearby,
            onClick = { onSettings { it.copy(includeNearby = !it.includeNearby) } },
            label = { Text(if (settings.includeNearby) "Basel + nearby airports" else "${Airports.cityOf(settings.homeIata)} only") },
        )
        if (showTripType) {
            FilterChip(
                selected = settings.dealsReturn,
                onClick = { onSettings { it.copy(dealsReturn = !it.dealsReturn) } },
                label = { Text(if (settings.dealsReturn) "Return · $nights" else "One way") },
            )
        }
        if (dests.isNotEmpty()) {
            FilterChip(
                selected = settings.dealsFollowPlan,
                onClick = { onSettings { it.copy(dealsFollowPlan = !it.dealsFollowPlan) } },
                label = {
                    Text(
                        if (settings.dealsFollowPlan) "Only to " + dests.joinToString(" · ") { Airports.cityOf(it) } else "All destinations",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

@Composable
private fun DropRow(d: PriceDrop, ink: Color, eurRate: Double, showEur: Boolean, onClick: () -> Unit) {
    val f = d.itinerary.fare
    val inks = LocalLineInks.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineRoundel(f.originAirport, ink)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(f.destinationCity ?: f.destinationAirport, f.country).joinToString(", "),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(f.departAt.format(dayFmt)); append(" "); append(f.departAt.format(timeFmt))
                    f.returnAt?.let { append(" · back ").append(it.format(dayFmt)) }
                    append(" · "); append(Airlines.name(f.airline))
                    d.typicalChf?.let { append(" · usually ${chf(it)}") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            PriceBlock(f.priceChf, eurRate, showEur, Modifier.widthIn(min = 64.dp))
            if (d.dropPct >= 15) {
                val (fill, text) = roundelColors(inks.green)
                Surface(color = fill, shape = MaterialTheme.shapes.extraSmall, modifier = Modifier.padding(top = 2.dp)) {
                    Text("−${d.dropPct}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = text, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
                }
            }
        }
    }
}

@Composable
private fun PlanThis(onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.padding(start = 16.dp, bottom = 12.dp).heightIn(min = 48.dp)) {
        Icon(Icons.Outlined.Route, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Compare all airlines around this date")
    }
}

@Composable
private fun CountryRow(c: CountryLow, ink: Color, onClick: () -> Unit) {
    val f = c.cheapest.fare
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(c.country, style = MaterialTheme.typography.titleMedium)
            Text(
                "${f.destinationCity ?: f.destinationAirport} on ${f.departAt.format(dayFmt)} · ${c.destinations} destination${if (c.destinations == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LineRoundel(f.originAirport, ink)
        Spacer(Modifier.width(10.dp))
        Text("from ${chf(f.priceChf)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PromoRow(p: PromoDeal, eurRate: Double) {
    val uri = LocalUriHandler.current
    val inks = LocalLineInks.current
    Column(Modifier.fillMaxWidth().clickable { uri.openUri(p.link) }.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(p.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            p.priceEur?.let {
                Column(horizontalAlignment = Alignment.End) {
                    Text("€$it", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("≈ ${chf(it / eurRate)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (p.errorFare) Tag("Error fare", inks.amber)
            if (p.nearYou) Tag("Near home", inks.cobalt)
            Text("${p.site} · ${ago(p.publishedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Open deal", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Tag(text: String, ink: Color) {
    val (fill, fg) = roundelColors(ink)
    Surface(color = fill, shape = MaterialTheme.shapes.extraSmall) {
        Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = fg, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
    }
}
