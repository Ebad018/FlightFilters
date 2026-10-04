package com.ebad.flightfilters.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TripOrigin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ebad.flightfilters.data.Airlines
import com.ebad.flightfilters.data.Airports
import com.ebad.flightfilters.data.FareSource
import com.ebad.flightfilters.data.Itinerary
import com.ebad.flightfilters.data.LiveCheck
import com.ebad.flightfilters.data.Notice
import com.ebad.flightfilters.data.NoticeKind
import com.ebad.flightfilters.data.Query
import com.ebad.flightfilters.data.Recommender
import com.ebad.flightfilters.ui.theme.LocalLineInks
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Exponential ease-out: lines are drawn quickly and settle at the station. */
val TraceEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
val shortDayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/** Ink for an origin: its position in the query's line list, so a line keeps its colour everywhere. */
@Composable
fun lineColor(query: Query, iata: String): Color {
    val inks = LocalLineInks.current
    val idx = query.origins.indexOfFirst { it.iata == iata }
    return if (idx < 0) MaterialTheme.colorScheme.outline else inks.forIndex(idx)
}

private val Midnight = Color(0xFF0E1A3A)
private val Porcelain = Color(0xFFF4F1E8)

private fun contrast(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return if (la > lb) la / lb else lb / la
}

/**
 * Fill and text for a roundel that carries a code. Line inks are tuned for drawing lines, so when
 * neither porcelain nor midnight text reaches 4.5:1 on an ink, the plate is deepened (or lifted)
 * in small steps until one does, whichever stays closer to the line's colour.
 */
fun roundelColors(ink: Color): Pair<Color, Color> {
    if (contrast(ink, Porcelain) >= 4.5f) return ink to Porcelain
    if (contrast(ink, Midnight) >= 4.5f) return ink to Midnight
    for (step in 1..20) {
        val t = step * 0.05f
        val darker = lerp(ink, Color.Black, t)
        if (contrast(darker, Porcelain) >= 4.5f) return darker to Porcelain
        val lighter = lerp(ink, Color.White, t)
        if (contrast(lighter, Midnight) >= 4.5f) return lighter to Midnight
    }
    return Midnight to Porcelain
}

fun Color.onInk(): Color = roundelColors(this).second

/** The line's roundel: its IATA code on its own ink, like a line bullet on a station sign. */
@Composable
fun LineRoundel(code: String, color: Color, modifier: Modifier = Modifier, large: Boolean = false) {
    val (fill, text) = roundelColors(color)
    Box(
        modifier
            .clip(MaterialTheme.shapes.small)
            .background(fill)
            .padding(horizontal = if (large) 10.dp else 6.dp, vertical = if (large) 5.dp else 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            code,
            style = if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = text,
        )
    }
}

fun chf(v: Double) = "CHF ${v.roundToInt()}"
fun eur(v: Double, rate: Double) = "€${(v * rate).roundToInt()}"

@Composable
fun PriceBlock(chfValue: Double, eurRate: Double, showEur: Boolean, modifier: Modifier = Modifier, emphasis: Boolean = false) {
    Column(modifier, horizontalAlignment = Alignment.End) {
        Text(
            chf(chfValue),
            style = if (emphasis) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (showEur) {
            Text("≈ ${eur(chfValue, eurRate)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

fun ago(millis: Long, now: Long = System.currentTimeMillis()): String {
    val m = ((now - millis) / 60000).toInt()
    return when {
        m < 1 -> "just now"
        m < 60 -> "$m min ago"
        m < 48 * 60 -> "${m / 60} h ago"
        else -> "${m / 1440} days ago"
    }
}

/** Where a price came from and how old it is. Every fare shown in the app is attributed this way. */
@Composable
fun SourceStamp(source: FareSource, seenAt: Long, modifier: Modifier = Modifier) = SourceStamp(setOf(source), seenAt, modifier)

@Composable
fun SourceStamp(sources: Collection<FareSource>, seenAt: Long, modifier: Modifier = Modifier) {
    val sample = FareSource.SAMPLE in sources
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (sample) {
            Surface(color = MaterialTheme.colorScheme.secondary, shape = MaterialTheme.shapes.extraSmall) {
                Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Science, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSecondary)
                    Spacer(Modifier.width(4.dp))
                    Text("SAMPLE DATA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondary, fontWeight = FontWeight.Bold)
                }
            }
            Text("Synthetic fares for an offline demo. Turn sample data off in Settings for real prices.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val allLive = sources.isNotEmpty() && sources.all { it.live }
            Icon(Icons.Outlined.Schedule, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                sources.sortedBy { it.ordinal }.joinToString(" + ") { it.label } +
                    " · fetched ${ago(seenAt)} · " +
                    (if (allLive) "live fares; the final price is on the booking page" else "cached fares are indicative, so check live before booking"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A service notice, like the change boards at a station: an interchange ring in the notice's ink,
 * a plain statement of the saving, and a tap that traces the alternative on the network.
 */
@Composable
fun ServiceNotice(notice: Notice, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val inks = LocalLineInks.current
    val (ink, icon) = when (notice.kind) {
        NoticeKind.CHEAPER_DATE -> inks.amber to Icons.Outlined.CalendarMonth
        NoticeKind.OTHER_ORIGIN -> inks.cobalt to Icons.Outlined.TripOrigin
        NoticeKind.FASTER -> inks.green to Icons.Outlined.Bolt
    }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            InterchangeRing(ink, icon)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(notice.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(2.dp))
                Text(notice.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Text(chf(notice.itinerary.fare.priceChf), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun InterchangeRing(ink: Color, icon: ImageVector, size: Dp = 36.dp) {
    val ground = MaterialTheme.colorScheme.surfaceContainer
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            drawCircle(ink, radius = this.size.minDimension / 2)
            drawCircle(ground, radius = this.size.minDimension / 2 - 4.dp.toPx())
        }
        Icon(icon, null, Modifier.size(size * 0.5f), tint = ink)
    }
}

/**
 * One itinerary as a strip of the line diagram: departure tick, interchange circles for each
 * change, arrival terminus, all drawn in the origin's ink.
 */
@Composable
fun ItineraryStrip(
    item: Itinerary,
    ink: Color,
    selected: Boolean,
    eurRate: Double,
    showEur: Boolean,
    live: Load<LiveCheck>?,
    onClick: () -> Unit,
    onCheckLive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val f = item.fare
    val arrive = f.departAt.plusMinutes(f.durationMin.toLong())
    val dim = !item.inWindow
    val surface = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface
    Surface(color = surface, modifier = modifier.fillMaxWidth()) {
        Column(
            Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics(mergeDescendants = false) {
                    contentDescription = "${f.originAirport} to ${f.destinationAirport}, ${f.departAt.format(dayFmt)} ${f.departAt.format(timeFmt)}, ${Recommender.stopsLabel(f.transfers)}, ${chf(f.priceChf)}"
                },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LineRoundel(f.originAirport, ink)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(f.departAt.format(timeFmt), style = MaterialTheme.typography.titleMedium)
                        LineTrack(ink, f.transfers, Modifier.weight(1f).padding(horizontal = 8.dp))
                        Text(arrive.format(timeFmt), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(6.dp))
                        Text(f.destinationAirport, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        buildString {
                            append(f.departAt.format(dayFmt)); append(" · ")
                            append(Airlines.name(f.airline)); append(" · ")
                            append(Recommender.hm(f.durationMin)); append(" · ")
                            append(Recommender.stopsLabel(f.transfers))
                            if (dim) append(" · outside your window")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (dim) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(12.dp))
                PriceBlock(f.priceChf, eurRate, showEur, Modifier.widthIn(min = 72.dp))
            }
            AnimatedVisibility(
                visible = selected,
                enter = expandVertically(tween(220, easing = TraceEasing)) + fadeIn(tween(160)),
                exit = shrinkVertically(tween(160)) + fadeOut(tween(120)),
            ) {
                FareDetail(item, eurRate, showEur, live, onCheckLive)
            }
        }
    }
}

/** A horizontal stretch of line with an interchange circle per change. */
@Composable
fun LineTrack(ink: Color, transfers: Int, modifier: Modifier = Modifier) {
    val ground = MaterialTheme.colorScheme.surface
    val porcelain = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.height(12.dp)) {
        val y = size.height / 2
        drawLine(ink, Offset(0f, y), Offset(size.width, y), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        // Porcelain station ticks at departure and arrival.
        listOf(1.5.dp.toPx(), size.width - 1.5.dp.toPx()).forEach { x ->
            drawLine(porcelain, Offset(x, y - 5.dp.toPx()), Offset(x, y + 5.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
        }
        for (i in 1..transfers) {
            val x = size.width * i / (transfers + 1)
            drawCircle(ground, 4.5.dp.toPx(), Offset(x, y))
            drawCircle(porcelain, 4.5.dp.toPx(), Offset(x, y), style = Stroke(2.dp.toPx()))
        }
    }
}

@Composable
private fun FareDetail(item: Itinerary, eurRate: Double, showEur: Boolean, live: Load<LiveCheck>?, onCheckLive: () -> Unit) {
    val f = item.fare
    val uri = LocalUriHandler.current
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DetailRow("Ticket", chf(f.priceChf) + if (showEur) "  ·  ≈ ${eur(f.priceChf, eurRate)}" else "")
        DetailRow("Getting to ${f.originAirport}", "${Recommender.hm(item.groundMin)} on the ground")
        DetailRow("Door to door", Recommender.hm(item.totalMin))
        DetailRow("Trip cost with your time", chf(item.effectiveCostChf))
        if (f.returnAt != null) DetailRow("Return", f.returnAt.format(dayFmt) + " · " + Recommender.stopsLabel(f.returnTransfers))
        SourceStamp(f.source, f.seenAt)

        when (live) {
            null -> Unit
            is Load.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Checking Google Flights…", style = MaterialTheme.typography.bodyMedium)
            }
            is Load.Ready -> LiveResult(live.value, f.priceChf)
            else -> Unit
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onCheckLive, enabled = live !is Load.Loading, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Outlined.Bolt, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (live is Load.Ready) "Check again" else "Check live price")
            }
            val link = (live as? Load.Ready)?.value?.url ?: f.bookingUrl
            if (link != null) {
                OutlinedButton(onClick = { uri.openUri(link) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Book")
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun LiveResult(check: LiveCheck, cachedPrice: Double) {
    val inks = LocalLineInks.current
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.small) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            if (check.error != null || check.priceChf == null) {
                Text(check.error ?: "No live price found", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                return@Column
            }
            val delta = check.priceChf - cachedPrice
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Live ${chf(check.priceChf)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Text(
                    when {
                        kotlin.math.abs(delta) < 1 -> "same as cached"
                        delta > 0 -> "+${chf(delta)} since cached"
                        else -> "−${chf(-delta)} since cached"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (delta > 0) inks.scarlet else inks.green,
                )
            }
            Text(
                buildString {
                    append("Google Flights · ${ago(check.checkedAt)}")
                    if (!check.matched) append(" · cheapest that day, not the same flight")
                    if (check.layovers.isNotEmpty()) append(" · via ${check.layovers.joinToString()}")
                    check.priceLevel?.let { append(" · price is $it") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 10.dp),
    )
}

@Composable
fun EmptyLine(title: String, body: String, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    val ink = MaterialTheme.colorScheme.outline
    val ground = MaterialTheme.colorScheme.surface
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth(0.7f).height(40.dp)) {
            val y = size.height / 2
            drawLine(ink, Offset(0f, y), Offset(size.width - 14.dp.toPx(), y), 4.dp.toPx(), StrokeCap.Round)
            drawCircle(ground, 12.dp.toPx(), Offset(size.width - 12.dp.toPx(), y))
            drawCircle(ink, 12.dp.toPx(), Offset(size.width - 12.dp.toPx(), y), style = Stroke(3.dp.toPx()))
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        action()
    }
}

fun routeTitle(q: Query): String {
    val from = q.activeOrigins.joinToString(" · ") { it.iata }.ifEmpty { "No lines" }
    val to = q.dests.takeIf { it.isNotEmpty() }?.joinToString(" · ") { Airports.cityOf(it) } ?: "Anywhere"
    return "$from → $to"
}

fun routeSubtitle(q: Query): String {
    val o = q.activeOrigins.firstOrNull() ?: return "Plan a journey"
    val w = o.window
    val dates = if (w.from == w.to) w.from.format(dayFmt) else "${w.from.format(dayFmt)} – ${w.to.format(shortDayFmt)}"
    val trip = if (q.oneWay) "one way" else "return ${q.nightsMin}–${q.nightsMax} nights"
    val flex = if (q.flexDays > 0) " · ±${q.flexDays} days" else ""
    return "$dates · $trip$flex"
}
