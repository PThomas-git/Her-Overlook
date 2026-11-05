@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package fr.heroverlook.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fr.heroverlook.AppVM
import fr.heroverlook.data.AppLog
import fr.heroverlook.data.Site
import fr.heroverlook.data.VisitStatus
import fr.heroverlook.data.requestLocation
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private data class Pt(val lat: Double, val lon: Double)

private fun haversineKm(a: Pt, b: Pt): Double {
    val r = 6371.0
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val h = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * r * asin(sqrt(h))
}

private fun pointOf(s: Site) = Pt(s.lat!!, s.lon!!)

/**
 * Ordre de visite : plus proche voisin depuis le départ (position actuelle, sinon le site le plus à l'ouest),
 * puis amélioration 2-opt sur le chemin ouvert. Distances « à vol d'oiseau ».
 */
private fun planRoute(sites: List<Site>, start: Pt?): List<Site> {
    if (sites.isEmpty()) return emptyList()
    val pts = sites.map { pointOf(it) }
    val remaining = sites.indices.toMutableList()
    val order = ArrayList<Int>()
    var cur: Pt? = start
    if (cur == null) {
        val first = remaining.minByOrNull { pts[it].lon }!!
        order.add(first)
        remaining.remove(first)
        cur = pts[first]
    }
    while (remaining.isNotEmpty()) {
        val c = cur!!
        val next = remaining.minByOrNull { haversineKm(c, pts[it]) }!!
        order.add(next)
        remaining.remove(next)
        cur = pts[next]
    }

    val seq = ArrayList<Int>()
    if (start != null) seq.add(-1) // -1 = point de départ, fixe
    seq.addAll(order)
    fun p(i: Int): Pt = if (i == -1) start!! else pts[i]

    var improved = true
    var guard = 0
    while (improved && guard++ < 60) {
        improved = false
        for (i in 1 until seq.size - 1) {
            for (j in i + 1 until seq.size) {
                val a = p(seq[i - 1])
                val b = p(seq[i])
                val c = p(seq[j])
                val d = if (j + 1 < seq.size) p(seq[j + 1]) else null
                val before = haversineKm(a, b) + (if (d != null) haversineKm(c, d) else 0.0)
                val after = haversineKm(a, c) + (if (d != null) haversineKm(b, d) else 0.0)
                if (after + 1e-9 < before) {
                    seq.subList(i, j + 1).reverse()
                    improved = true
                }
            }
        }
    }
    return seq.filter { it >= 0 }.map { sites[it] }
}

private fun parseStart(text: String): Pt? {
    val m = Regex("(-?\\d+\\.\\d+)\\s*,\\s*(-?\\d+\\.\\d+)").find(text) ?: return null
    return Pt(m.groupValues[1].toDouble(), m.groupValues[2].toDouble())
}

@Composable
fun TourScreen(vm: AppVM) {
    val ctx = LocalContext.current
    var includeDone by rememberSaveable { mutableStateOf(false) }
    var startText by rememberSaveable { mutableStateOf("") } // « lat, lon » de la position actuelle
    var locating by remember { mutableStateOf(false) }

    val start = parseStart(startText)
    val all = vm.sites.values.toList()
    val withGps = all.filter { it.lat != null && it.lon != null && (includeDone || it.status != VisitStatus.DONE) }
    val withoutGps = all.filter { it.lat == null || it.lon == null }
    val route = remember(withGps.map { it.id }, startText) { planRoute(withGps, start) }

    fun fetch() {
        locating = true
        requestLocation(ctx) { res ->
            locating = false
            if (res == null) vm.toast("Position indisponible (GPS désactivé ?)") else startText = res
        }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) fetch() else vm.toast("Permission de localisation refusée")
    }

    // distances par tronçon
    val legs = route.mapIndexed { i, s ->
        val from = if (i == 0) start else pointOf(route[i - 1])
        if (from == null) null else haversineKm(from, pointOf(s))
    }
    val total = legs.filterNotNull().sum()

    Screen("Tournée", "${route.size} site(s) à visiter", vm::back) {
        Panel(accent = MaterialTheme.colorScheme.primary) {
            Text(
                if (route.isEmpty()) "Aucun site avec coordonnées à planifier."
                else "≈ ${"%.0f".format(total)} km à vol d'oiseau (compter environ ×1,3 par la route)",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                if (start != null) "Départ : ta position actuelle." else "Départ : le site le plus à l'ouest. Touche « Depuis ma position » pour recalculer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = includeDone, onClick = { includeDone = !includeDone }, label = { Text("Inclure les sites terminés") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.MyLocation, null)
                    Text(if (locating) "  Recherche…" else "  Depuis ma position")
                }
                OutlinedButton(onClick = { startText = "" }, modifier = Modifier.weight(1f), enabled = start != null) {
                    Text("Départ à l'ouest")
                }
            }
        }

        if (route.isNotEmpty()) {
            TourMap(route, start) { vm.go("site/${it.id}") }
            OutlinedButton(
                onClick = {
                    val stops = route.take(10)
                    val dest = stops.last()
                    val wp = stops.dropLast(1).joinToString("|") { "${it.lat},${it.lon}" }
                    val sb = StringBuilder("https://www.google.com/maps/dir/?api=1&travelmode=driving")
                    sb.append("&destination=${dest.lat},${dest.lon}")
                    if (wp.isNotEmpty()) sb.append("&waypoints=").append(Uri.encode(wp))
                    if (start != null) sb.append("&origin=${start.lat},${start.lon}")
                    AppLog.i("tour", "Ouverture du circuit dans Maps (${stops.size} étapes)")
                    try {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sb.toString())))
                    } catch (e: Exception) {
                        vm.toast("Impossible d'ouvrir Maps")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Directions, null)
                Text("  Ouvrir les 10 premières étapes dans Maps")
            }
        }

        route.forEachIndexed { i, s ->
            Panel(accent = statusColor(s.status), onClick = { vm.go("site/${s.id}") }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${i + 1}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                        Text(s.name.ifBlank { s.id }, style = MaterialTheme.typography.titleSmall)
                        Text(
                            listOf(s.city, s.status.label, legs[i]?.let { "+${"%.1f".format(it)} km" }).filterNotNull().filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = {
                        try {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${s.lat},${s.lon}")))
                        } catch (e: Exception) {
                            vm.toast("Aucune application de navigation")
                        }
                    }) { Icon(Icons.Outlined.Navigation, "Itinéraire") }
                }
            }
        }

        if (withoutGps.isNotEmpty()) {
            SectionTitle("Sans coordonnées")
            withoutGps.forEach { s ->
                Panel(onClick = { vm.go("site/${s.id}") }) {
                    Text(s.name.ifBlank { s.id }, style = MaterialTheme.typography.titleSmall)
                    Text("Relever la position dans « Accès » du site.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Carte schématique hors ligne : points reliés dans l'ordre de visite (touche un point pour ouvrir le site). */
@Composable
private fun TourMap(route: List<Site>, start: Pt?, onOpen: (Site) -> Unit) {
    val line = MaterialTheme.colorScheme.primary
    val startColor = MaterialTheme.colorScheme.secondary
    val ring = MaterialTheme.colorScheme.onBackground
    val colors = route.map { statusColor(it.status) }
    val pts = route.map { pointOf(it) }

    // Projection : longitude corrigée par cos(latitude) pour ne pas déformer la carte.
    val allPts = if (start != null) pts + start else pts
    val minLat = allPts.minOf { it.lat }
    val maxLat = allPts.maxOf { it.lat }
    val minLon = allPts.minOf { it.lon }
    val maxLon = allPts.maxOf { it.lon }
    val k = cos((minLat + maxLat) / 2.0 * PI / 180.0)
    val spanX = max((maxLon - minLon) * k, 1e-6)
    val spanY = max(maxLat - minLat, 1e-6)

    fun project(p: Pt, w: Float, h: Float, pad: Float): Offset {
        val scale = min((w - 2 * pad) / spanX, (h - 2 * pad) / spanY).toFloat()
        val offX = (w - spanX.toFloat() * scale) / 2f
        val offY = (h - spanY.toFloat() * scale) / 2f
        val x = offX + ((p.lon - minLon) * k).toFloat() * scale
        val y = offY + (maxLat - p.lat).toFloat() * scale
        return Offset(x, y)
    }

    Panel {
        Box(
            Modifier
                .fillMaxWidth()
                .height(280.dp)
                .pointerInput(route) {
                    detectTapGestures { tap ->
                        val pad = 28.dp.toPx()
                        var best = -1
                        var bestD = 40.dp.toPx()
                        pts.forEachIndexed { i, p ->
                            val o = project(p, size.width.toFloat(), size.height.toFloat(), pad)
                            val d = (o - tap).getDistance()
                            if (d < bestD) { bestD = d; best = i }
                        }
                        if (best >= 0) onOpen(route[best])
                    }
                },
        ) {
            Canvas(Modifier.fillMaxWidth().height(280.dp)) {
                val pad = 28.dp.toPx()
                val offs = pts.map { project(it, size.width, size.height, pad) }
                val startOff = start?.let { project(it, size.width, size.height, pad) }
                // tracé
                val chain = if (startOff != null) listOf(startOff) + offs else offs
                for (i in 0 until chain.size - 1) {
                    drawLine(line.copy(alpha = 0.55f), chain[i], chain[i + 1], strokeWidth = 2.dp.toPx())
                }
                // points
                offs.forEachIndexed { i, o ->
                    drawCircle(colors[i], radius = 8.dp.toPx(), center = o)
                    drawCircle(ring, radius = 8.dp.toPx(), center = o, style = Stroke(width = 1.5.dp.toPx()))
                }
                // départ
                if (startOff != null) {
                    drawCircle(startColor, radius = 7.dp.toPx(), center = startOff)
                    drawCircle(startColor, radius = 13.dp.toPx(), center = startOff, style = Stroke(width = 2.dp.toPx()))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Nord en haut · sans fond de carte (fonctionne hors ligne)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
