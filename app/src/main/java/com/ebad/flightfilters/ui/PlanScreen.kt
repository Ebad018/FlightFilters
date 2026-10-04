package com.ebad.flightfilters.ui

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.ui.platform.LocalContext
import com.ebad.flightfilters.data.AirportDb
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.ebad.flightfilters.data.Airlines
import com.ebad.flightfilters.data.Airports
import com.ebad.flightfilters.data.AppSettings
import com.ebad.flightfilters.data.Cabin
import com.ebad.flightfilters.data.OriginLine
import com.ebad.flightfilters.data.Query
import com.ebad.flightfilters.data.Recommender
import com.ebad.flightfilters.data.Stops
import com.ebad.flightfilters.data.TimeWindow
import com.ebad.flightfilters.ui.theme.LocalLineInks
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlanScreen(vm: AppViewModel, settings: AppSettings, onScan: () -> Unit, onOpenSettings: () -> Unit, onOpenLines: () -> Unit) {
    val q by vm.query.collectAsState()
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var saving by rememberSaveable { mutableStateOf(false) }
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    fun update(t: (Query) -> Query) { vm.updateQuery(t) }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Plan", style = MaterialTheme.typography.titleLarge)
                        Text(routeTitle(q), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = onOpenLines) { Icon(Icons.Outlined.Bookmarks, "Saved lines") }
                    IconButton(onClick = { saving = true }) { Icon(Icons.Outlined.BookmarkAdd, "Save as a line") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, "Settings") }
                },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.topAppBarColors(scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.runScan(); onScan() },
                icon = { Icon(Icons.Outlined.Hub, null) },
                text = { Text("Scan network") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 96.dp)) {
            item { SectionTitle("From") }
            itemsIndexed(q.origins, key = { _, o -> o.iata }) { i, line ->
                OriginRow(line, LocalLineInks.current.forIndex(i), settings, onToggle = { on ->
                    update { qq -> qq.copy(origins = qq.origins.map { if (it.iata == line.iata) it.copy(enabled = on) else it }) }
                }, onClick = { editing = line.iata })
            }
            item {
                TextButton(
                    onClick = { adding = true },
                    modifier = Modifier.padding(horizontal = 8.dp).heightIn(min = 48.dp),
                    enabled = q.origins.size < 8,
                ) {
                    Icon(Icons.Outlined.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add a departure airport")
                }
            }

            item { SectionTitle("To") }
            item { DestinationPicker(q.dests) { list -> update { it.withDests(list) } } }

            item { SectionTitle("Trip") }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(true to "One way", false to "Return").forEachIndexed { i, (oneWay, label) ->
                            SegmentedButton(
                                selected = q.oneWay == oneWay,
                                onClick = { update { it.copy(oneWay = oneWay) } },
                                shape = SegmentedButtonDefaults.itemShape(i, 2),
                                label = { Text(label) },
                            )
                        }
                    }
                    if (!q.oneWay) {
                        Spacer(Modifier.height(16.dp))
                        LabeledValue("Nights away", "${q.nightsMin}–${q.nightsMax}")
                        RangeSlider(
                            value = q.nightsMin.toFloat()..q.nightsMax.toFloat(),
                            onValueChange = { r -> update { it.copy(nightsMin = r.start.roundToInt(), nightsMax = r.endInclusive.roundToInt()) } },
                            valueRange = 1f..21f,
                            steps = 19,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    LabeledValue("Look around your dates", if (q.flexDays == 0) "Exact dates" else "±${q.flexDays} days")
                    Slider(
                        value = q.flexDays.toFloat(),
                        onValueChange = { v -> update { it.copy(flexDays = v.roundToInt()) } },
                        valueRange = 0f..7f,
                        steps = 6,
                    )
                    Text(
                        "Nearby days are scanned too, and suggested when they save money or time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { SectionTitle("Filters") }
            item { FiltersBlock(q) { t -> update(t) } }
        }
    }

    editing?.let { code ->
        val line = q.origins.firstOrNull { it.iata == code }
        if (line == null) editing = null else {
            WindowSheet(
                line = line,
                ink = LocalLineInks.current.forIndex(q.origins.indexOf(line)),
                canRemove = q.origins.size > 1,
                onDismiss = { editing = null },
                onSave = { w, applyAll ->
                    update { qq -> qq.copy(origins = qq.origins.map { if (applyAll || it.iata == code) it.copy(window = w) else it }) }
                    editing = null
                },
                onRemove = {
                    update { qq -> qq.copy(origins = qq.origins.filterNot { it.iata == code }) }
                    editing = null
                },
            )
        }
    }

    if (adding) {
        AddOriginSheet(
            existing = q.origins.map { it.iata },
            settings = settings,
            onDismiss = { adding = false },
            onPick = { code ->
                val w = q.origins.firstOrNull()?.window ?: TimeWindow(LocalDate.now().plusDays(1).toString(), LocalDate.now().plusDays(3).toString())
                update { it.copy(origins = it.origins + OriginLine(code, w)) }
                adding = false
            },
        )
    }

    if (saving) {
        var name by remember { mutableStateOf(routeTitle(q)) }
        AlertDialog(
            onDismissRequest = { saving = false },
            title = { Text("Save as a line") },
            text = {
                Column {
                    Text("Keeps these airports, windows and filters so you can run them again with one tap.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { vm.savePreset(name.ifBlank { routeTitle(q) }); saving = false }) { Text("Save line") } },
            dismissButton = { TextButton(onClick = { saving = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun OriginRow(line: OriginLine, ink: androidx.compose.ui.graphics.Color, settings: AppSettings, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    val airport = Airports.byCode(line.iata)
    val w = line.window
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineRoundel(line.iata, if (line.enabled) ink else MaterialTheme.colorScheme.outline, large = true)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(airport?.name ?: AirportDb.get(LocalContext.current).get(line.iata)?.name ?: line.iata, style = MaterialTheme.typography.titleMedium)
            Text(
                "${windowDates(w)} · ${"%02d".format(w.hourFrom)}–${"%02d".format(w.hourTo)}h · ${Recommender.hm(settings.ground(line.iata))} away",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = line.enabled, onCheckedChange = onToggle)
    }
}

private fun windowDates(w: TimeWindow): String =
    if (w.from == w.to) w.from.format(dayFmt) else "${w.from.format(dayFmt)} – ${w.to.format(shortDayFmt)}"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DestinationPicker(current: List<String>, onChange: (List<String>) -> Unit) {
    val db = AirportDb.get(LocalContext.current)
    fun toggle(code: String) = onChange(if (code in current) current - code else current + code)
    Column(Modifier.padding(horizontal = 16.dp)) {
        // Where the scan is aimed right now: each destination is a removable chip, or Anywhere.
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (current.size) {
                    0 -> "Anywhere: scanning every destination"
                    1 -> "1 destination"
                    else -> "${current.size} destinations, each searched separately"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            FilterChip(selected = current.isEmpty(), onClick = { if (current.isNotEmpty()) onChange(emptyList()) }, label = { Text("Anywhere") })
        }
        if (current.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                current.forEach { c ->
                    androidx.compose.material3.InputChip(
                        selected = true,
                        onClick = { toggle(c) },
                        label = { Text(db.get(c)?.let { "${it.city.ifBlank { it.name }} · $c" } ?: "${Airports.cityOf(c)} · $c") },
                        trailingIcon = { Icon(Icons.Outlined.Close, "Remove ${Airports.cityOf(c)}", Modifier.size(18.dp)) },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        AirportSearchField(
            label = if (current.isEmpty()) "Search any city, airport, country or code" else "Add another destination",
            onPick = { if (it.code !in current) onChange(current + it.code) },
            exclude = current.toSet(),
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Airports.destinations.forEach { d ->
                FilterChip(selected = d.code in current, onClick = { toggle(d.code) }, label = { Text(d.city) })
            }
        }
        if (current.size > 6) {
            Text("Only the first 6 destinations are scanned live.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

/**
 * Type-ahead over every scheduled airport in the world. Results appear inline under the field
 * (no popup), so it works inside lists and bottom sheets alike.
 */
@Composable
fun AirportSearchField(label: String, onPick: (AirportDb.Entry) -> Unit, modifier: Modifier = Modifier, exclude: Set<String> = emptySet()) {
    val db = AirportDb.get(LocalContext.current)
    var text by remember { mutableStateOf("") }
    val results = remember(text) { db.search(text).filter { it.code !in exclude } }
    Column(modifier) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(label) },
            placeholder = { Text("e.g. Tokyo, Marrakesh, Iceland, CMB") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = androidx.compose.ui.text.input.ImeAction.Search),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { results.firstOrNull()?.let { onPick(it); text = "" } }),
            modifier = Modifier.fillMaxWidth(),
        )
        if (text.length >= 2 && results.isEmpty()) {
            Text("No airport matches “$text”.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
        results.forEach { e ->
            Row(
                Modifier.fillMaxWidth().clickable { onPick(e); text = "" }.heightIn(min = 52.dp).padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LineRoundel(e.code, MaterialTheme.colorScheme.surfaceContainerHighest)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.city.ifBlank { e.name }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text("${e.name} · ${e.country}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FiltersBlock(q: Query, update: ((Query) -> Query) -> Unit) {
    val f = q.filters
    Column(Modifier.padding(horizontal = 16.dp)) {
        LabeledValue("Price cap", f.maxPriceChf?.let { "CHF $it" } ?: "No cap")
        Slider(
            value = (f.maxPriceChf ?: 1000).toFloat(),
            onValueChange = { v -> val c = (v / 10).roundToInt() * 10; update { it.copy(filters = it.filters.copy(maxPriceChf = if (c >= 1000) null else c)) } },
            valueRange = 30f..1000f,
        )

        Spacer(Modifier.height(12.dp))
        Text("Changes", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Stops.entries.forEachIndexed { i, s ->
                SegmentedButton(
                    selected = f.stops == s,
                    onClick = { update { it.copy(filters = it.filters.copy(stops = s)) } },
                    shape = SegmentedButtonDefaults.itemShape(i, Stops.entries.size),
                    label = { Text(s.label) },
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        LabeledValue("Longest flight", f.maxDurationHours?.let { "$it h" } ?: "Any length")
        Slider(
            value = (f.maxDurationHours ?: 24).toFloat(),
            onValueChange = { v -> val h = v.roundToInt(); update { it.copy(filters = it.filters.copy(maxDurationHours = if (h >= 24) null else h)) } },
            valueRange = 1f..24f,
            steps = 22,
        )

        Spacer(Modifier.height(12.dp))
        Text("Airlines", style = MaterialTheme.typography.bodyLarge)
        Text(
            "Tap once to fly only these, twice to avoid one.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Airlines.common.forEach { code ->
                val inc = code in f.airlinesInclude
                val exc = code in f.airlinesExclude
                FilterChip(
                    selected = inc || exc,
                    onClick = {
                        update {
                            val ff = it.filters
                            it.copy(
                                filters = when {
                                    inc -> ff.copy(airlinesInclude = ff.airlinesInclude - code, airlinesExclude = ff.airlinesExclude + code)
                                    exc -> ff.copy(airlinesExclude = ff.airlinesExclude - code)
                                    else -> ff.copy(airlinesInclude = ff.airlinesInclude + code)
                                },
                            )
                        }
                    },
                    leadingIcon = when {
                        inc -> { { Icon(Icons.Outlined.Check, "Only", Modifier.size(16.dp)) } }
                        exc -> { { Icon(Icons.Outlined.Block, "Avoid", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error) } }
                        else -> null
                    },
                    label = { Text(Airlines.name(code)) },
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Cabin", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Cabin.entries.forEachIndexed { i, c ->
                SegmentedButton(
                    selected = f.cabin == c,
                    onClick = { update { it.copy(filters = it.filters.copy(cabin = c)) } },
                    shape = SegmentedButtonDefaults.itemShape(i, Cabin.entries.size),
                    label = { Text(c.label, maxLines = 1) },
                )
            }
        }
        Text(
            "Cached fares are economy. The cabin you choose here applies to live price checks.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
fun LabeledValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun LocalDate.utcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.utcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WindowSheet(
    line: OriginLine,
    ink: androidx.compose.ui.graphics.Color,
    canRemove: Boolean,
    onDismiss: () -> Unit,
    onSave: (TimeWindow, Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dates = rememberDateRangePickerState(
        initialSelectedStartDateMillis = line.window.from.utcMillis(),
        initialSelectedEndDateMillis = line.window.to.utcMillis(),
    )
    var hourFrom by remember { mutableFloatStateOf(line.window.hourFrom.toFloat()) }
    var hourTo by remember { mutableFloatStateOf(line.window.hourTo.toFloat()) }
    var applyAll by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.navigationBarsPadding()) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                LineRoundel(line.iata, ink, large = true)
                Spacer(Modifier.width(12.dp))
                Text("Departure window from ${Airports.cityOf(line.iata)}", style = MaterialTheme.typography.titleMedium)
            }
            DateRangePicker(
                state = dates,
                modifier = Modifier.height(440.dp),
                title = null,
                showModeToggle = false,
            )
            Column(Modifier.padding(horizontal = 16.dp)) {
                LabeledValue("Leave between", "${"%02d".format(hourFrom.roundToInt())}:00 – ${"%02d".format(hourTo.roundToInt())}:00")
                RangeSlider(
                    value = hourFrom..hourTo,
                    onValueChange = { hourFrom = it.start; hourTo = it.endInclusive },
                    valueRange = 0f..24f,
                    steps = 23,
                )
                Row(Modifier.fillMaxWidth().clickable { applyAll = !applyAll }.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = applyAll, onCheckedChange = { applyAll = it })
                    Text("Use this window for every line", style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.Button(
                    onClick = {
                        val start = dates.selectedStartDateMillis?.utcDate() ?: line.window.from
                        val end = dates.selectedEndDateMillis?.utcDate() ?: start
                        val hf = hourFrom.roundToInt()
                        val ht = hourTo.roundToInt().coerceAtLeast(hf + 1)
                        onSave(TimeWindow(start.toString(), end.toString(), hf, ht), applyAll)
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("Set window") }
                if (canRemove) {
                    // Destructive action kept apart from the primary one.
                    Spacer(Modifier.height(32.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = onRemove,
                        modifier = Modifier.heightIn(min = 48.dp),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Remove ${line.iata} line") }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddOriginSheet(existing: List<String>, settings: AppSettings, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val inks = LocalLineInks.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp).verticalScroll(rememberScrollState())) {
            Text("Add a departure airport", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            AirportSearchField(
                label = "Any airport in the world",
                onPick = { onPick(it.code) },
                exclude = existing.toSet(),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text("In reach of home", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
            Airports.nearby.filter { it.code !in existing }.forEach { a ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(a.code) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LineRoundel(a.code, inks.forIndex(existing.size))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.name, style = MaterialTheme.typography.titleMedium)
                        Text("${Recommender.hm(settings.ground(a.code))} from home", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
