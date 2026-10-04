package com.ebad.flightfilters.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.ebad.flightfilters.data.AppSettings
import com.ebad.flightfilters.ui.theme.LocalLineInks

private val suggestions = listOf(
    "Sunny weekend from Basel under CHF 120 in November",
    "Cheapest way to Tokyo in April, I'm flexible ±3 days",
    "Any error fares or big price drops this month?",
    "Direct flights to Lisbon next Friday morning",
)

/** Ask in plain words; the assistant turns it into a real scan and explains what it found. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AskScreen(vm: AppViewModel, settings: AppSettings, onOpenNetwork: () -> Unit, onOpenDeals: () -> Unit, onOpenSettings: () -> Unit) {
    val chat by vm.chat.collectAsState()
    var input by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()
    val busy = chat.any { it.pending }
    val provider = settings.aiProvider
    fun send(text: String) { if (text.isNotBlank() && !busy) { vm.ask(text); input = "" } }

    LaunchedEffect(chat.size, chat.lastOrNull()?.text, chat.lastOrNull()?.status) {
        if (chat.isNotEmpty()) list.animateScrollToItem(chat.lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Ask", style = MaterialTheme.typography.titleLarge)
                        Text(
                            (if (provider.free) "Free · " else "Your key · ") + provider.label.substringBefore(" (") + " · " + settings.aiModel(provider).ifBlank { "default" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (chat.isNotEmpty()) IconButton(onClick = vm::clearChat, enabled = !busy) { Icon(Icons.Outlined.DeleteSweep, "Clear conversation") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, "AI settings") }
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).imePadding()) {
            Box(Modifier.weight(1f)) {
                if (chat.isEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(24.dp)) {
                        InterchangeRing(LocalLineInks.current.cobalt, Icons.Outlined.AutoAwesome, 44.dp)
                        Spacer(Modifier.height(16.dp))
                        Text("Tell me where and when, roughly", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "I turn it into a live scan of Google Flights, Ryanair and deal feeds from the airports around Basel, then tell you what's worth booking. Prices always come from the scan, never from the AI.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(20.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            suggestions.forEach { s -> SuggestionChip(onClick = { send(s) }, label = { Text(s) }) }
                        }
                    }
                } else {
                    LazyColumn(state = list, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(chat, key = { it.id }) { m ->
                            if (m.fromUser) UserBubble(m.text) else AssistantBubble(m, onOpenNetwork, onOpenDeals, onRetry = {
                                chat.lastOrNull { it.fromUser }?.let { u -> vm.clearLastError(); send(u.text) }
                            })
                        }
                    }
                }
            }
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("e.g. Cheap long weekend in the sun, late October") },
                        modifier = Modifier.weight(1f),
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send(input) }),
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(onClick = { send(input) }, enabled = input.isNotBlank() && !busy, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.Send, "Send")
                    }
                }
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.large, modifier = Modifier.widthIn(max = 300.dp)) {
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun AssistantBubble(m: ChatItem, onOpenNetwork: () -> Unit, onOpenDeals: () -> Unit, onRetry: () -> Unit) {
    val inks = LocalLineInks.current
    Row(Modifier.fillMaxWidth()) {
        LineRoundel("AI", inks.cobalt)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            if (m.text.isNotBlank()) {
                Text(
                    m.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (m.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }
            m.status?.let { s ->
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(s, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when (m.action) {
                ChatAction.OPEN_NETWORK -> FilledTonalButton(onClick = onOpenNetwork, modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.Hub, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("See all fares on the network")
                }
                ChatAction.OPEN_DEALS -> FilledTonalButton(onClick = onOpenDeals, modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.LocalOffer, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Open the deals")
                }
                ChatAction.NONE -> Unit
            }
            if (m.error) {
                androidx.compose.material3.TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text("Try again") }
            }
        }
    }
}
