package com.ebad.flightfilters.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ebad.flightfilters.data.AiProvider
import com.ebad.flightfilters.data.Airports
import com.ebad.flightfilters.data.AppSettings
import com.ebad.flightfilters.data.ThemeMode
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, settings: AppSettings, onBack: () -> Unit) {
    val eurRate by vm.eurRate.collectAsState()
    val eurRateLive by vm.eurRateLive.collectAsState()
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    fun set(t: (AppSettings) -> AppSettings) { vm.updateSettings(t) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 32.dp)) {
            item { SectionTitle("Home") }
            items(Airports.nearby.take(4)) { a ->
                Row(
                    Modifier.fillMaxWidth().clickable { set { it.copy(homeIata = a.code) } }.heightIn(min = 56.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = settings.homeIata == a.code, onClick = { set { it.copy(homeIata = a.code) } })
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(a.city, style = MaterialTheme.typography.titleMedium)
                        Text(a.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item { SectionTitle("Getting to each airport") }
            item {
                Text(
                    "Door-to-terminal time from home, one way. It's added to every fare from that airport.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            items(Airports.nearby) { a ->
                val m = settings.ground(a.code)
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    LineRoundel(a.code, MaterialTheme.colorScheme.surfaceContainerHighest)
                    Spacer(Modifier.width(12.dp))
                    Text(a.city, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { set { it.copy(groundMinutes = it.groundMinutes + (a.code to (m - 5).coerceAtLeast(0))) } }) {
                        Icon(Icons.Outlined.Remove, "5 minutes less to ${a.city}")
                    }
                    Text("$m min", style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(64.dp))
                    IconButton(onClick = { set { it.copy(groundMinutes = it.groundMinutes + (a.code to m + 5)) } }) {
                        Icon(Icons.Outlined.Add, "5 minutes more to ${a.city}")
                    }
                }
            }

            item { SectionTitle("What your time is worth") }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    LabeledValue("Per hour of travel", "CHF ${settings.valueOfTimeChfPerHour}")
                    Slider(
                        value = settings.valueOfTimeChfPerHour.toFloat(),
                        onValueChange = { v -> set { it.copy(valueOfTimeChfPerHour = (v / 5).roundToInt() * 5) } },
                        valueRange = 0f..100f,
                    )
                    Text(
                        "Used to compare a cheap fare with a long trip against a dearer, faster one. At 0, only the ticket price counts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { SectionTitle("Prices and departures") }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Show euros alongside francs", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (eurRateLive) "ECB rate: 1 CHF ≈ €${"%.3f".format(eurRate)}"
                                else "Approximate offline rate: 1 CHF ≈ €${"%.2f".format(eurRate)} (ECB rate unavailable)",
style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = settings.showEur, onCheckedChange = { on -> set { it.copy(showEur = on) } })
                    }
                    Spacer(Modifier.height(8.dp))
                    LabeledValue("Last-minute horizon", "${settings.lastMinuteDays} days")
                    Slider(
                        value = settings.lastMinuteDays.toFloat(),
                        onValueChange = { v -> set { it.copy(lastMinuteDays = v.roundToInt()) } },
                        valueRange = 1f..14f,
                        steps = 12,
                    )
                }
            }

            item { SectionTitle("Appearance") }
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    listOf(ThemeMode.MIDNIGHT to "Midnight", ThemeMode.PORCELAIN to "Porcelain", ThemeMode.SYSTEM to "System").forEachIndexed { i, (m, label) ->
                        SegmentedButton(
                            selected = settings.themeMode == m,
                            onClick = { set { it.copy(themeMode = m) } },
                            shape = SegmentedButtonDefaults.itemShape(i, 3),
                            label = { Text(label) },
                        )
                    }
                }
            }

            item { SectionTitle("AI assistant") }
            item {
                Text(
                    "Ask works for free with no key (LLM7, with Pollinations as backup), and it still understands plain requests if the AI is busy. Keyless services are best-effort; a free Gemini or Groq key is more reliable. If you have a paid OpenAI or Claude key, pick it here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            items(AiProvider.entries) { p ->
                Row(
                    Modifier.fillMaxWidth().clickable { set { it.copy(aiProvider = p) } }.heightIn(min = 52.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = settings.aiProvider == p, onClick = { set { it.copy(aiProvider = p) } })
                    Spacer(Modifier.width(8.dp))
                    Text(p.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    if (settings.aiKey(p).isNotBlank()) Text("key set", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                val p = settings.aiProvider
                var testResult by remember { mutableStateOf<String?>(null) }
                var testing by remember { mutableStateOf(false) }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    if (p == AiProvider.CUSTOM) {
                        androidx.compose.material3.OutlinedTextField(
                            value = settings.aiCustomUrl,
                            onValueChange = { v -> set { it.copy(aiCustomUrl = v.trim()) } },
                            label = { Text("Endpoint base URL") },
                            placeholder = { Text("https://host/v1") },
                            singleLine = true,
                            supportingText = { Text("Any OpenAI-compatible server: LM Studio, Ollama, Together, Mistral, DeepSeek…") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    if (p.needsKey || p == AiProvider.CUSTOM) {
                        SecretField(
                            label = "${p.label.substringBefore(" (")} API key",
                            value = settings.aiKey(p),
                            help = if (p.free) "Free to create; stays on this phone." else "Billed to your own account; stays on this phone.",
                            onChange = { v -> set { it.copy(aiKeys = it.aiKeys + (p.name to v.trim())) } },
                        )
                        if (p.keyUrl.isNotBlank()) TextLink("Get a ${p.label.substringBefore(" (")} key") { uri.openUri(p.keyUrl) }
                    }
                    androidx.compose.material3.OutlinedTextField(
                        value = settings.aiModels[p.name].orEmpty(),
                        onValueChange = { v -> set { it.copy(aiModels = it.aiModels + (p.name to v.trim())) } },
                        label = { Text("Model") },
                        placeholder = { Text(p.defaultModel.ifBlank { "model id" }) },
                        singleLine = true,
                        supportingText = { Text(if (p.defaultModel.isNotBlank()) "Leave blank for ${p.defaultModel}" else "Required for a custom endpoint") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { testing = true; testResult = null; scope.launch { testResult = vm.testAi(); testing = false } },
                            enabled = !testing,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text(if (testing) "Testing…" else "Test connection") }
                        Spacer(Modifier.width(12.dp))
                        testResult?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (it.startsWith("Connected")) com.ebad.flightfilters.ui.theme.LocalLineInks.current.green else MaterialTheme.colorScheme.error,
                                maxLines = 3,
                            )
                        }
                    }
                }
            }

            item { SectionTitle("Fare sources") }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        "Live prices come from Google Flights (every airline) and Ryanair's fare finder, with no keys needed. " +
                            "Promo and error fares come from Fly4free. The keys below are optional extras.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    SecretField(
                        label = "Travelpayouts token (optional)",
                        value = settings.travelpayoutsToken,
                        help = "Adds cached Aviasales fares for more airlines on 'Anywhere' scans. Free at travelpayouts.com → Profile → API token.",
                        onChange = { v -> set { it.copy(travelpayoutsToken = v.trim()) } },
                    )
                    TextLink("Open Travelpayouts") { uri.openUri("https://www.travelpayouts.com/") }
                    Spacer(Modifier.height(16.dp))
                    SecretField(
                        label = "SerpApi key (optional)",
                        value = settings.serpApiKey,
                        help = "Backup for “Check live price” if Google Flights can't be read. Free monthly quota.",
                        onChange = { v -> set { it.copy(serpApiKey = v.trim()) } },
                    )
                    TextLink("Open SerpApi") { uri.openUri("https://serpapi.com/users/sign_up") }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Use sample data", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Offline demo with synthetic fares, clearly labelled. Leave off for real prices.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = settings.usesSampleData,
                            onCheckedChange = { on -> set { it.copy(forceSampleData = on) } },
                        )
                    }
                }
            }

            item {
                // Destructive and alone, well away from everything above.
                Column(Modifier.padding(horizontal = 16.dp).padding(top = 48.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(24.dp))
                    OutlinedButton(
                        onClick = { vm.clearCache(); scope.launch { snack.showSnackbar("Fare cache cleared. The next scan fetches fresh prices.") } },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Clear fare cache") }
                    Text(
                        "Fresh scans use more of your free API quota.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SecretField(label: String, value: String, help: String, onChange: (String) -> Unit) {
    var shown by remember { mutableStateOf(false) }
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onChange(it) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { shown = !shown }) {
                Icon(if (shown) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (shown) "Hide $label" else "Show $label")
            }
        },
        supportingText = { Text(help) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TextLink(text: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick, contentPadding = ButtonDefaults.TextButtonContentPadding) { Text(text) }
}
