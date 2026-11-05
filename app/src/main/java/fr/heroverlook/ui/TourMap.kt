@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalTextApi::class)

package fr.heroverlook.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.heroverlook.data.AppLog
import fr.heroverlook.data.HeraultMap
import fr.heroverlook.data.Site
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

private const val MAX_ZOOM = 10f

private val TOWNS = listOf(
    Triple("Montpellier", 43.6108, 3.8767),
    Triple("Béziers", 43.3441, 3.2158),
    Triple("Sète", 43.4028, 3.6967),
    Triple("Agde", 43.3108, 3.4758),
    Triple("Lunel", 43.6747, 4.1358),
    Triple("Lodève", 43.7317, 3.3189),
    Triple("Pézenas", 43.4600, 3.4235),
    Triple("Ganges", 43.9333, 3.7067),
    Triple("Bédarieux", 43.6122, 3.1595),
    Triple("Clermont-l'H.", 43.6281, 3.4319),
    Triple("St-Pons", 43.4833, 2.7667),
    Triple("Frontignan", 43.4478, 3.7547),
    Triple("Mauguio", 43.6167, 4.0083),
    Triple("Gignac", 43.6515, 3.5516),
)

/** Projection plan : longitude corrigée par cos(latitude) pour ne pas déformer la carte. */
private class Proj(val minLat: Double, val maxLat: Double, val minLon: Double, val maxLon: Double) {
    private val k = cos((minLat + maxLat) / 2.0 * PI / 180.0)
    private val spanX = max((maxLon - minLon) * k, 1e-6)
    private val spanY = max(maxLat - minLat, 1e-6)

    fun at(lat: Double, lon: Double, w: Float, h: Float, pad: Float): Offset {
        val scale = min((w - 2 * pad) / spanX, (h - 2 * pad) / spanY).toFloat()
        val offX = (w - spanX.toFloat() * scale) / 2f
        val offY = (h - spanY.toFloat() * scale) / 2f
        return Offset(offX + ((lon - minLon) * k).toFloat() * scale, offY + (maxLat - lat).toFloat() * scale)
    }
}

/** Cadre de départ (zoom 1) : tout le département (si le fond est chargé) + les sites + le départ. */
private fun buildProj(shapes: HeraultMap.Shapes?, pts: List<Pt>, start: Pt?): Proj {
    var minLat = Double.MAX_VALUE
    var maxLat = -Double.MAX_VALUE
    var minLon = Double.MAX_VALUE
    var maxLon = -Double.MAX_VALUE
    fun add(lat: Double, lon: Double) {
        if (lat < minLat) minLat = lat
        if (lat > maxLat) maxLat = lat
        if (lon < minLon) minLon = lon
        if (lon > maxLon) maxLon = lon
    }
    pts.forEach { add(it.lat, it.lon) }
    if (start != null) add(start.lat, start.lon)
    if (shapes != null) {
        add(shapes.minLat, shapes.minLon)
        add(shapes.maxLat, shapes.maxLon)
    }
    if (minLat > maxLat) { add(43.6, 3.5); add(43.7, 3.6) }
    return Proj(minLat, maxLat, minLon, maxLon)
}

/** Géométrie du fond, déjà projetée aux dimensions de l'écran (zoom 1). */
private class MapBg(
    val land: Path,
    val communes: List<RingPath>,
    val sites: List<Offset>,
    val start: Offset?,
    val towns: List<Offset>,
    val hasShapes: Boolean,
)

/** Un contour de commune avec son rectangle englobant (zoom 1), pour ne dessiner que ce qui est visible. */
private class RingPath(val path: Path, val minX: Float, val minY: Float, val maxX: Float, val maxY: Float)

private fun buildRing(ring: DoubleArray, proj: Proj, w: Float, h: Float, pad: Float): RingPath {
    val path = Path()
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    var i = 0
    var first = true
    while (i + 1 < ring.size) {
        val o = proj.at(ring[i + 1], ring[i], w, h, pad)
        if (o.x < minX) minX = o.x
        if (o.x > maxX) maxX = o.x
        if (o.y < minY) minY = o.y
        if (o.y > maxY) maxY = o.y
        if (first) { path.moveTo(o.x, o.y); first = false } else path.lineTo(o.x, o.y)
        i += 2
    }
    path.close()
    return RingPath(path, minX, minY, maxX, maxY)
}

private fun addRing(path: Path, ring: DoubleArray, proj: Proj, w: Float, h: Float, pad: Float) {
    var i = 0
    var first = true
    while (i + 1 < ring.size) {
        val o = proj.at(ring[i + 1], ring[i], w, h, pad)
        if (first) { path.moveTo(o.x, o.y); first = false } else path.lineTo(o.x, o.y)
        i += 2
    }
    path.close()
}

/**
 * Carte hors ligne de l'Hérault : département, communes, villes repères, sites et circuit.
 * Pincer / glisser / double appui pour zoomer ; le dézoom s'arrête au cadrage initial. Touche un point pour ouvrir le site.
 */
@Composable
internal fun TourMap(route: List<Site>, start: Pt?, onOpen: (Site) -> Unit) {
    val ctx = LocalContext.current
    val shapes by produceState<HeraultMap.Shapes?>(null) {
        value = withContext(Dispatchers.IO) {
            runCatching { HeraultMap.load(ctx) }
                .onFailure { AppLog.e("map", "Fond de carte illisible", it) }
                .getOrNull()
        }
    }
    val density0 = LocalDensity.current
    val labelArgb = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val textPaint = remember(labelArgb, density0) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            color = labelArgb
            textSize = with(density0) { 10.sp.toPx() }
        }
    }
    val land = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.40f)
    val border = MaterialTheme.colorScheme.outline
    val communeLine = MaterialTheme.colorScheme.outlineVariant
    val line = MaterialTheme.colorScheme.primary
    val startColor = MaterialTheme.colorScheme.secondary
    val ring = MaterialTheme.colorScheme.onBackground
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val colors = route.map { statusColor(it.status) }
    val pts = route.map { pointOf(it) }

    // Zoom (1 = cadrage initial, minimum) et décalage. Transformation écran = pivot + (p - pivot) * zoom + pan.
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    /** Change le zoom en gardant fixe le point [focus] de l'écran, puis borne le décalage pour que la carte couvre toujours le cadre. */
    fun zoomTo(newZoom: Float, focus: Offset, extraPan: Offset = Offset.Zero) {
        val w = canvasSize.width.toFloat()
        val h = canvasSize.height.toFloat()
        if (w <= 0f || h <= 0f) return
        // Garde-fous : une valeur non numérique (NaN / infini) dans un calcul de geste corromprait la carte.
        if (!newZoom.isFinite() || !focus.isSpecified || !focus.x.isFinite() || !focus.y.isFinite()) return
        if (!extraPan.x.isFinite() || !extraPan.y.isFinite()) return
        val pivot = Offset(w / 2f, h / 2f)
        val z0 = zoom
        val z1 = newZoom.coerceIn(1f, MAX_ZOOM)
        val p = (focus - pivot) - ((focus - pivot - pan) / z0) * z1 + extraPan
        val maxX = (z1 - 1f) * w / 2f
        val maxY = (z1 - 1f) * h / 2f
        pan = Offset(p.x.coerceIn(-maxX, maxX), p.y.coerceIn(-maxY, maxY))
        zoom = z1
    }

    // Fond reconstruit seulement si la taille, les données ou le circuit changent (pas à chaque geste de zoom).
    val density = LocalDensity.current
    val bg = remember(shapes, canvasSize, pts, start) {
        val w = canvasSize.width.toFloat()
        val h = canvasSize.height.toFloat()
        if (w <= 0f || h <= 0f) {
            null
        } else {
            val pad = with(density) { 24.dp.toPx() }
            val proj = buildProj(shapes, pts, start)
            val landPath = Path()
            shapes?.outline?.forEach { addRing(landPath, it, proj, w, h, pad) }
            MapBg(
                land = landPath,
                communes = shapes?.communes?.map { buildRing(it, proj, w, h, pad) } ?: emptyList(),
                sites = pts.map { proj.at(it.lat, it.lon, w, h, pad) },
                start = start?.let { proj.at(it.lat, it.lon, w, h, pad) },
                towns = TOWNS.map { proj.at(it.second, it.third, w, h, pad) },
                hasShapes = shapes != null,
            )
        }
    }

    Panel {
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(340.dp)
                .clipToBounds()
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    // Pincer (2 doigts) ou glisser une fois zoomé : agit sur la carte.
                    // Un seul doigt au zoom minimal : on laisse la page défiler.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var pastSlop = false
                        var accPan = Offset.Zero
                        var accZoom = 1f
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.any { it.isConsumed }) break
                            val zc = event.calculateZoom()
                            val pc = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = false)
                            val multi = event.changes.count { it.pressed } > 1
                            accZoom *= zc
                            accPan += pc
                            if (!pastSlop) {
                                val moved = accPan.getDistance() > viewConfiguration.touchSlop || kotlin.math.abs(accZoom - 1f) > 0.03f
                                if (moved && (multi || zoom > 1.001f)) pastSlop = true
                            }
                            if (pastSlop) {
                                if (zc != 1f || pc != Offset.Zero) zoomTo(zoom * zc, centroid, pc)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .pointerInput(route, bg) {
                    detectTapGestures(
                        onDoubleTap = { tap ->
                            if (zoom > 1.5f) zoomTo(1f, tap) else zoomTo(3f, tap)
                        },
                        onTap = { tap ->
                            val sites = bg?.sites
                            if (sites != null) {
                                val pivot = Offset(size.width / 2f, size.height / 2f)
                                var best = -1
                                var bestD = 40.dp.toPx()
                                sites.forEachIndexed { i, base ->
                                    val o = (base - pivot) * zoom + pivot + pan
                                    val d = (o - tap).getDistance()
                                    if (d < bestD) { bestD = d; best = i }
                                }
                                if (best >= 0) onOpen(route[best])
                            }
                        },
                    )
                }
                .drawBehind {
                    val b = bg
                    if (b != null) {
                        val w = size.width
                        val h = size.height
                        val sh = b.hasShapes
                        val landPath = b.land
                        val offs = b.sites
                        val startOff = b.start
                        val townOffs = b.towns
                        val pivot = Offset(w / 2f, h / 2f)
                        val z = zoom
                        val pn = pan
                        fun tp(o: Offset): Offset = (o - pivot) * z + pivot + pn

                        if (sh && z.isFinite() && pn.x.isFinite() && pn.y.isFinite()) {
                            // Rectangle visible exprimé dans le repère du fond (zoom 1) : on ne dessine que les communes qui y touchent.
                            val vx0 = (0f - pn.x - pivot.x) / z + pivot.x
                            val vx1 = (w - pn.x - pivot.x) / z + pivot.x
                            val vy0 = (0f - pn.y - pivot.y) / z + pivot.y
                            val vy1 = (h - pn.y - pivot.y) / z + pivot.y
                            withTransform({
                                translate(pn.x, pn.y)
                                scale(z, z, pivot)
                            }) {
                                drawPath(landPath, land)
                                val thin = Stroke(width = 0.6.dp.toPx() / z)
                                val col = communeLine.copy(alpha = 0.55f)
                                for (r in b.communes) {
                                    if (r.maxX < vx0 || r.minX > vx1 || r.maxY < vy0 || r.minY > vy1) continue
                                    drawPath(r.path, col, style = thin)
                                }
                                drawPath(landPath, border, style = Stroke(width = 1.5.dp.toPx() / z))
                            }
                        }
                        // villes repères (taille constante, quel que soit le zoom) ; texte natif : pas de mesure Compose à chaque image
                        val nc = drawContext.canvas.nativeCanvas
                        TOWNS.forEachIndexed { i, t ->
                            val o = tp(townOffs[i])
                            if (o.x in -40f..(w + 40f) && o.y in -20f..(h + 20f)) {
                                drawCircle(labelColor, radius = 2.dp.toPx(), center = o)
                                nc.drawText(t.first, o.x + 5.dp.toPx(), o.y + 3.dp.toPx(), textPaint)
                            }
                        }
                        // circuit
                        val chain = (if (startOff != null) listOf(startOff) + offs else offs).map { tp(it) }
                        for (i in 0 until chain.size - 1) {
                            drawLine(line.copy(alpha = 0.7f), chain[i], chain[i + 1], strokeWidth = 2.dp.toPx())
                        }
                        // sites
                        offs.forEachIndexed { i, base ->
                            val o = tp(base)
                            drawCircle(colors[i], radius = 8.dp.toPx(), center = o)
                            drawCircle(ring, radius = 8.dp.toPx(), center = o, style = Stroke(width = 1.5.dp.toPx()))
                        }
                        // départ
                        if (startOff != null) {
                            val o = tp(startOff)
                            drawCircle(startColor, radius = 7.dp.toPx(), center = o)
                            drawCircle(startColor, radius = 13.dp.toPx(), center = o, style = Stroke(width = 2.dp.toPx()))
                        }
                    }
                },
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = { zoomTo(zoom / 1.6f, Offset(canvasSize.width / 2f, canvasSize.height / 2f)) },
                enabled = zoom > 1.001f,
                modifier = Modifier.weight(1f),
            ) { Icon(Icons.Outlined.Remove, "Dézoomer") }
            OutlinedButton(
                onClick = { zoomTo(zoom * 1.6f, Offset(canvasSize.width / 2f, canvasSize.height / 2f)) },
                enabled = zoom < MAX_ZOOM - 0.01f,
                modifier = Modifier.weight(1f),
            ) { Icon(Icons.Outlined.Add, "Zoomer") }
            OutlinedButton(
                onClick = { zoomTo(1f, Offset(canvasSize.width / 2f, canvasSize.height / 2f)) },
                enabled = zoom > 1.001f,
                modifier = Modifier.weight(1f),
            ) { Icon(Icons.Outlined.CenterFocusStrong, "Cadrage initial") }
        }
        Text(
            "Pincer ou double appui pour zoomer · le dézoom s'arrête au cadrage initial · fond embarqué (hors ligne), contours IGN / Etalab, Licence Ouverte",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
