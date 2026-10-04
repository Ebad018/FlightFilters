package com.ebad.flightfilters.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.ebad.flightfilters.data.Itinerary
import com.ebad.flightfilters.data.Query
import com.ebad.flightfilters.ui.theme.Fira
import com.ebad.flightfilters.ui.theme.LocalLineInks
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The network: every departure line on the left, the destinations as termini on the right,
 * each itinerary drawn as a metro line that bends only at 45°. Changes are interchange circles.
 * The selected itinerary is traced at full ink; everything else recedes to a faint underlay.
 */
@Composable
fun RouteDiagram(
    query: Query,
    itineraries: List<Itinerary>,
    selectedKey: String?,
    onSelect: (Itinerary) -> Unit,
    eurRate: Double,
    showEur: Boolean,
    modifier: Modifier = Modifier,
) {
    val inks = LocalLineInks.current
    val scheme = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val origins = query.activeOrigins.map { it.iata }
    // Top itineraries by trip cost, always including the selected one so it can be traced.
    val shown = remember(itineraries, selectedKey) {
        val top = itineraries.take(12)
        val sel = itineraries.firstOrNull { it.fare.key == selectedKey }
        if (sel != null && sel !in top) top + sel else top
    }
    val termini = remember(shown) {
        shown.map { it.fare.destination }.distinct().take(4)
    }
    val drawn = shown.filter { (it.fare.origin in origins || it.fare.originAirport in origins) && it.fare.destination in termini }
    val height = (max(origins.size, termini.size).coerceAtLeast(2) * 64 + 24).dp

    val trace = remember { Animatable(1f) }
    LaunchedEffect(selectedKey) {
        trace.snapTo(0f)
        trace.animateTo(1f, tween(520, easing = TraceEasing))
    }

    val selected = drawn.firstOrNull { it.fare.key == selectedKey } ?: drawn.firstOrNull()
    val bestPerTerminus = termini.associateWith { t -> drawn.filter { it.fare.destination == t }.minByOrNull { it.fare.priceChf } }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics {
                contentDescription = "Route network: ${origins.joinToString()} to ${termini.joinToString()}. " +
                    (selected?.let { "Highlighted: ${it.fare.originAirport} to ${it.fare.destination}, ${chf(it.fare.priceChf)}." } ?: "")
            }
            .pointerInput(drawn) {
                detectTapGestures { tap ->
                    val geo = Geometry(size.width.toFloat(), size.height.toFloat(), density, origins, termini)
                    val hit = drawn.minByOrNull { geo.distanceTo(it, drawn, tap) }
                    if (hit != null && geo.distanceTo(hit, drawn, tap) < 28.dp.toPx()) onSelect(hit)
                }
            },
    ) {
        val geo = Geometry(size.width, size.height, density, origins, termini)

        // Underlay: every other itinerary, thin and faint.
        drawn.filter { it !== selected }.forEach { item ->
            val ink = inkFor(query, item.fare.origin.ifEmpty { item.fare.originAirport }, inks.inks, scheme.outline)
            val path = geo.path(item, drawn)
            drawPath(path, ink.copy(alpha = 0.32f), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            geo.interchanges(path, item.fare.transfers).forEach {
                drawCircle(scheme.surface, 4.dp.toPx(), it)
                drawCircle(ink.copy(alpha = 0.5f), 4.dp.toPx(), it, style = Stroke(1.5.dp.toPx()))
            }
        }

        // The chosen line, traced from its origin.
        selected?.let { item ->
            val ink = inkFor(query, item.fare.origin.ifEmpty { item.fare.originAirport }, inks.inks, scheme.outline)
            val full = geo.path(item, drawn)
            val pm = PathMeasure().apply { setPath(full, false) }
            val part = Path()
            pm.getSegment(0f, pm.length * trace.value, part, true)
            drawPath(part, ink, style = Stroke(7.dp.toPx(), cap = StrokeCap.Butt, join = StrokeJoin.Round))
            // Porcelain station tick where the traced line leaves its roundel.
            val start = pm.getPosition(0f)
            val tickX = start.x + 6.dp.toPx()
            drawLine(scheme.onSurface, Offset(tickX, start.y - 7.dp.toPx()), Offset(tickX, start.y + 7.dp.toPx()), 2.5.dp.toPx(), StrokeCap.Round)
            geo.interchanges(full, item.fare.transfers).forEachIndexed { i, p ->
                val reached = trace.value >= (i + 1f) / (item.fare.transfers + 1f) * 0.98f
                if (reached) {
                    drawCircle(scheme.surface, 7.dp.toPx(), p)
                    drawCircle(scheme.onSurface, 7.dp.toPx(), p, style = Stroke(2.5.dp.toPx()))
                }
            }
        }

        // Origin roundels.
        origins.forEachIndexed { i, code ->
            val ink = inks.forIndex(query.origins.indexOfFirst { it.iata == code })
            roundel(measurer, code, ink, Offset(0f, geo.originY(i)))
        }

        // Termini: interchange rings. The traced itinerary's terminus carries its own price at
        // answer scale; other termini show their cheapest fare, small and dim.
        termini.forEachIndexed { j, code ->
            val c = Offset(geo.xEnd, geo.destY(j))
            val isSel = selected?.fare?.destination == code
            drawCircle(scheme.surface, 11.dp.toPx(), c)
            drawCircle(if (isSel) scheme.onSurface else scheme.outline, 11.dp.toPx(), c, style = Stroke(3.5.dp.toPx()))
            val x = c.x + 17.dp.toPx()
            if (isSel && selected != null) {
                val code1 = measurer.measure(code, TextStyle(fontWeight = FontWeight.Bold, fontSize = SmallSize, color = scheme.onSurfaceVariant))
                val priceStyle = TextStyle(fontFamily = Fira, fontWeight = FontWeight.Bold, fontSize = PriceSize, color = scheme.onSurface, fontFeatureSettings = "tnum, lnum")
                val room = size.width - x
                var price = measurer.measure(chf(selected.fare.priceChf), priceStyle)
                // 4-digit fares or large system font: step the answer down rather than run off the edge.
                if (price.size.width > room) {
                    price = measurer.measure(chf(selected.fare.priceChf), priceStyle.copy(fontSize = priceStyle.fontSize * (room / price.size.width)))
                }
                val top = c.y - price.size.height / 2f
                drawText(code1, topLeft = Offset(x, top - code1.size.height))
                drawText(price, topLeft = Offset(x, top))
                if (showEur) {
                    val e = measurer.measure("≈ ${eur(selected.fare.priceChf, eurRate)}", TextStyle(fontSize = SmallSize, color = scheme.onSurfaceVariant, fontFeatureSettings = "tnum"))
                    drawText(e, topLeft = Offset(x, top + price.size.height))
                }
            } else {
                val label = measurer.measure(code, TextStyle(fontWeight = FontWeight.Bold, fontSize = LabelSize, color = scheme.onSurfaceVariant))
                drawText(label, topLeft = Offset(x, c.y - label.size.height - 1.dp.toPx()))
                bestPerTerminus[code]?.let { best ->
                    val price = measurer.measure(
                        "from ${best.fare.priceChf.roundToInt()}",
                        TextStyle(fontSize = SmallSize, color = scheme.onSurfaceVariant, fontFeatureSettings = "tnum"),
                    )
                    drawText(price, topLeft = Offset(x, c.y + 1.dp.toPx()))
                }
            }
        }
    }
}

/** Shown while a scan runs: the lines leave their roundels and pulse toward an unknown terminus. */
@Composable
fun ScanningDiagram(query: Query, modifier: Modifier = Modifier) {
    val inks = LocalLineInks.current
    val scheme = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val origins = query.activeOrigins.map { it.iata }
    val t = rememberInfiniteTransition(label = "scan")
    val progress = t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = TraceEasing), RepeatMode.Restart), label = "p")
    Canvas(modifier.fillMaxWidth().height((origins.size.coerceAtLeast(2) * 64 + 24).dp)) {
        val geo = Geometry(size.width, size.height, density, origins, listOf("?"))
        origins.forEachIndexed { i, code ->
            val ink = inks.forIndex(query.origins.indexOfFirst { it.iata == code })
            val path = geo.rawPath(geo.originY(i), geo.destY(0), 0f)
            val pm = PathMeasure().apply { setPath(path, false) }
            val part = Path()
            val stagger = (progress.value - i * 0.12f).coerceIn(0f, 1f)
            pm.getSegment(0f, pm.length * stagger, part, true)
            drawPath(path, ink.copy(alpha = 0.18f), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            drawPath(part, ink, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            roundel(measurer, code, ink, Offset(0f, geo.originY(i)))
        }
        val c = Offset(geo.xEnd, geo.destY(0))
        drawCircle(scheme.surface, 11.dp.toPx(), c)
        drawCircle(scheme.outline, 11.dp.toPx(), c, style = Stroke(3.5.dp.toPx()))
    }
}

private val LabelSize = 15.sp()
private val SmallSize = 12.sp()
private val PriceSize = 22.sp()
private fun Int.sp() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)

private const val ROUNDEL_W = 46f

private fun inkFor(query: Query, code: String, inks: List<Color>, fallback: Color): Color {
    val i = query.origins.indexOfFirst { it.iata == code }
    return if (i < 0) fallback else inks[i % inks.size]
}

private fun DrawScope.roundel(measurer: TextMeasurer, code: String, ink: Color, leftCenter: Offset) {
    val (fill, fg) = roundelColors(ink)
    val text = measurer.measure(code, TextStyle(fontFamily = Fira, fontWeight = FontWeight.Bold, fontSize = 13.sp(), color = fg))
    // Fixed-width plate so every line leaves from the same edge (Geometry.xStart).
    val w = ROUNDEL_W * density
    val h = text.size.height + 6.dp.toPx()
    val top = leftCenter.y - h / 2
    drawRoundRect(fill, topLeft = Offset(leftCenter.x, top), size = androidx.compose.ui.geometry.Size(w, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
    drawText(text, topLeft = Offset(leftCenter.x + (w - text.size.width) / 2f, top + 3.dp.toPx()))
}

/** Diagram layout: origins stacked on the left, termini on the right, 45° bends between. */
private class Geometry(
    val w: Float,
    val h: Float,
    val density: Float,
    val origins: List<String>,
    val termini: List<String>,
) {
    private fun dp(v: Float) = v * density
    /** Lines leave from the roundel's right edge, so the plate reads as their station sign. */
    val xStart = dp(ROUNDEL_W)
    /** Room on the right for the answer: "CHF 1234" at price scale beside the terminus ring. */
    val xEnd = w - dp(108f)
    private val top = dp(12f)
    private val bottom = h - dp(12f)

    fun originY(i: Int): Float = top + (bottom - top) * (i + 0.5f) / max(1, origins.size)
    fun destY(j: Int): Float = top + (bottom - top) * (j + 0.5f) / max(1, termini.size)

    /** Parallel lines sharing a corridor are offset like tracks on a metro map. */
    private fun offsetFor(item: Itinerary, all: List<Itinerary>): Float {
        val siblings = all.filter { it.fare.originAirport == item.fare.originAirport && it.fare.destination == item.fare.destination }
        val k = siblings.indexOf(item)
        val n = siblings.size
        return (k - (n - 1) / 2f) * dp(5f)
    }

    fun path(item: Itinerary, all: List<Itinerary>): Path {
        val oi = origins.indexOf(item.fare.origin).takeIf { it >= 0 } ?: origins.indexOf(item.fare.originAirport).coerceAtLeast(0)
        val di = termini.indexOf(item.fare.destination).coerceAtLeast(0)
        return rawPath(originY(oi), destY(di), offsetFor(item, all))
    }

    fun rawPath(oy: Float, dy: Float, off: Float): Path {
        val y0 = oy + off
        val y1 = dy + off
        val rise = abs(y1 - y0)
        val run = xEnd - xStart
        // Bend late enough to leave room for interchanges before it, early enough for a pure 45° diagonal.
        val bendX = min(xStart + run * 0.42f, xEnd - dp(28f) - rise).coerceAtLeast(xStart + dp(12f))
        val diagEnd = min(bendX + rise, xEnd - dp(4f))
        return Path().apply {
            moveTo(xStart, y0)
            lineTo(bendX, y0)
            lineTo(diagEnd, y1)
            lineTo(xEnd, y1)
        }
    }

    fun interchanges(path: Path, transfers: Int): List<Offset> {
        if (transfers <= 0) return emptyList()
        val pm = PathMeasure().apply { setPath(path, false) }
        return (1..transfers).map { i -> pm.getPosition(pm.length * i / (transfers + 1f)) }
    }

    fun distanceTo(item: Itinerary, all: List<Itinerary>, p: Offset): Float {
        val pm = PathMeasure().apply { setPath(path(item, all), false) }
        var best = Float.MAX_VALUE
        for (s in 0..32) {
            val q = pm.getPosition(pm.length * s / 32f)
            best = min(best, hypot(q.x - p.x, q.y - p.y))
        }
        return best
    }
}
