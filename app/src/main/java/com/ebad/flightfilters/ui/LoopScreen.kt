package com.ebad.flightfilters.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Loop
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ebad.flightfilters.data.Airlines
import com.ebad.flightfilters.data.Airports
import com.ebad.flightfilters.data.AppSettings
import com.ebad.flightfilters.data.LoopRequest
import com.ebad.flightfilters.data.LoopTrip
import com.ebad.flightfilters.ui.theme.Fira
import com.ebad.flightfilters.ui.theme.LocalLineInks
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Loop: a circle line. Leave home, stop in two to four cities for a few nights each, come back
 * home, with every leg the cheapest direct flight in its window.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LoopScreen(vm: AppViewModel, settings: AppSettings, onOpenSettings: () -> Unit) {
    val state by vm.loops.collectAsState()
    val progress by vm.loopProgress.collectAsState()
    val eurRate by vm.eurRate.collectAsState()
    var mine by rememberSaveable { mutableStateOf(false) }
    var stops by rememberSaveable { mutableIntStateOf(3) }
    var nMin by rememberSaveable { mutableFloatStateOf(2f) }
    var nMax by rememberSaveable { mutableFloatStateOf(4f) }
    var from by rememberSaveable { mutableStateOf(LocalDate.now().plusDays(14).toString()) }
    var to by rememberSaveable { mutableStateOf(LocalDate.now().plusDays(17).toString()) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var cities by rememberSaveable { mutableStateOf(listOf<String>()) }
    var budget by rememberSaveable { mutableFloatStateOf(1000f) }
    val homes = vm.dealOrigins(settings)

    if (picking) {
        DateRangeDialog(LocalDate.parse(from), LocalDate.parse(to), "Start the loop between", onDismiss = { picking = false }) { a, b ->
            from = a.toString(); to = b.toString(); picking = false
        }
    }

    fun search() = vm.findLoops(
        LoopRequest(
            homes = homes,
            startFrom = LocalDate.parse(from),
            startTo = LocalDate.parse(to),
            stops = if (mine) cities.size else stops,
            nightsMin = nMin.roundToInt(),
            nightsMax = nMax.roundToInt(),
            cities = if (mine) cities else emptyList(),
            maxTotalChf = budget.roundToInt().takeIf { it < 1000 },
        ),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Loop", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${Airports.cityOf(settings.homeIata)} → ${if (mine) "${cities.size} chosen" else "$stops"} cities → home",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = { IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, "Settings") } },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 24.dp)) {
            item("form") {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        listOf(false to "Cheapest cities", true to "My cities").forEachIndexed { i, (m, label) ->
                            SegmentedButton(selected = mine == m, onClick = { mine = m }, shape = SegmentedButtonDefaults.itemShape(i, 2), label = { Text(label) })
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    if (!mine) {
                        LabeledValue("Cities to visit", "$stops")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (2..4).forEach { n -> FilterChip(selected = stops == n, onClick = { stops = n }, label = { Text("$n cities") }) }
                        }
                        Text(
                            "The app picks the cheapest chain of direct flights. Each city is visited once.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text("Your cities, in visiting order (the reverse order is tried too)", style = MaterialTheme.typography.bodyLarge)
                        if (cities.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                cities.forEachIndexed { i, c ->
                                    InputChip(
                                        selected = true,
                                        onClick = { cities = cities - c },
                                        label = { Text("${i + 1}. ${Airports.cityOf(c)}") },
                                        trailingIcon = { Icon(Icons.Outlined.Close, "Remove ${Airports.cityOf(c)}", Modifier.size(18.dp)) },
                                    )
                                }
                            }
                        }
                        if (cities.size < 5) {
                            AirportSearchField(
                                label = if (cities.isEmpty()) "Add the first city" else "Add another city",
                                onPick = { if (it.code !in cities) cities = cities + it.code },
                                exclude = cities.toSet() + homes,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    LabeledValue("Start between", "${LocalDate.parse(from).format(dayFmt)} – ${LocalDate.parse(to).format(shortDayFmt)}")
                    OutlinedButton(onClick = { picking = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Change start dates")
                    }
                    Spacer(Modifier.height(12.dp))
                    LabeledValue("Nights in each city", "${nMin.roundToInt()}–${nMax.roundToInt()}")
                    RangeSlider(value = nMin..nMax, onValueChange = { nMin = it.start; nMax = it.endInclusive }, valueRange = 1f..7f, steps = 5)
                    LabeledValue("Total budget", if (budget >= 1000f) "No limit" else "CHF ${budget.roundToInt()}")
                    Slider(value = budget, onValueChange = { budget = (it / 10).roundToInt() * 10f }, valueRange = 50f..1000f)
                    ScopeChips(settings, emptyList(), "", vm::updateSettings, showTripType = false)
                    Text(
                        if (homes.size > 1) "Loops may start and end at any of these airports." else "Loops start and end at ${Airports.cityOf(settings.homeIata)}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = ::search,
                        enabled = state !is Load.Loading && (!mine || cities.size >= 2),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) {
                        Icon(Icons.Outlined.Loop, null); Spacer(Modifier.width(8.dp))
                        Text(if (mine && cities.size < 2) "Add at least 2 cities" else "Find loops")
                    }
                }
            }
            when (val s = state) {
                Load.Idle -> Unit
                Load.Loading -> item("loading") {
                    Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(progress ?: "Searching…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                is Load.Failed -> item("failed") { EmptyLine("No loop found", s.message) }
                is Load.Ready -> {
                    item("count") { SectionTitle("${s.value.size} loops, cheapest first") }
                    itemsIndexed(s.value, key = { _, l -> l.legs.joinToString { it.key } }) { _, loop ->
                        LoopCard(loop, eurRate, settings.showEur)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun LoopCard(loop: LoopTrip, eurRate: Double, showEur: Boolean) {
    val uri = LocalUriHandler.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    loop.cities.joinToString(" → ") { Airports.cityOf(it) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${loop.start.format(dayFmt)} – ${loop.end.format(shortDayFmt)} · ${loop.days} days · ${loop.legs.size} flights",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PriceBlock(loop.totalChf, eurRate, showEur, emphasis = true)
        }
        Spacer(Modifier.height(10.dp))
        LoopLine(loop)
        Spacer(Modifier.height(8.dp))
        loop.legs.forEachIndexed { i, f ->
            Row(
                Modifier.fillMaxWidth().clickable(enabled = f.bookingUrl != null) { f.bookingUrl?.let(uri::openUri) }.heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(f.departAt.format(dayFmt), style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(88.dp))
                Text(f.departAt.format(timeFmt), style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(48.dp))
                Text(
                    "${f.originAirport} → ${f.destinationAirport} · ${Airlines.name(f.airline)}" + if (f.transfers > 0) " · ${f.transfers} change" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(chf(f.priceChf), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                if (f.bookingUrl != null) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Book ${f.originAirport} to ${f.destinationAirport}", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            loop.nights.getOrNull(i)?.let { n ->
                Text(
                    "$n night${if (n == 1) "" else "s"} in ${Airports.cityOf(f.destinationAirport)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 136.dp, bottom = 2.dp),
                )
            }
        }
        Text(
            "Prices are per leg from Ryanair and Google, booked separately. Tap a leg to book it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** The trip as a circle line: stations along the top, the way home curving back underneath. */
@Composable
private fun LoopLine(loop: LoopTrip) {
    val inks = LocalLineInks.current
    val scheme = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val stations = listOf(loop.legs.first().originAirport) + loop.cities
    val home = loop.legs.last().destinationAirport
    Canvas(Modifier.fillMaxWidth().height(64.dp)) {
        val ink = inks.scarlet
        val left = 24.dp.toPx()
        val right = size.width - 24.dp.toPx()
        val top = 18.dp.toPx()
        val bottom = 50.dp.toPx()
        val step = (right - left) / (stations.size - 1).coerceAtLeast(1)
        val r = (bottom - top) / 2
        val path = Path().apply {
            moveTo(left, top)
            lineTo(right, top)
            // Turn back at the far end, then run home along the bottom.
            arcTo(androidx.compose.ui.geometry.Rect(right - r, top, right + r, bottom), -90f, 180f, false)
            lineTo(left, bottom)
            arcTo(androidx.compose.ui.geometry.Rect(left - r, top, left + r, bottom), 90f, 180f, false)
        }
        drawPath(path, ink, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        stations.forEachIndexed { i, code ->
            val c = Offset(left + step * i, top)
            val isHome = i == 0
            drawCircle(scheme.surface, if (isHome) 9.dp.toPx() else 7.dp.toPx(), c)
            drawCircle(scheme.onSurface, if (isHome) 9.dp.toPx() else 7.dp.toPx(), c, style = Stroke(2.5.dp.toPx()))
            val label = measurer.measure(code, TextStyle(fontFamily = Fira, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = scheme.onSurface))
            drawText(label, topLeft = Offset(c.x - label.size.width / 2f, c.y - 9.dp.toPx() - label.size.height))
        }
        if (home != stations.first()) {
            val label = measurer.measure("back to $home", TextStyle(fontSize = 10.sp, color = scheme.onSurfaceVariant))
            drawText(label, topLeft = Offset(left + 8.dp.toPx(), bottom + 2.dp.toPx()))
        }
    }
}

