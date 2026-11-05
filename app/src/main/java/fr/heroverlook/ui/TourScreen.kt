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
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
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
import fr.heroverlook.data.LocationResult
import fr.heroverlook.data.requestLocation
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal data class Pt(val lat: Double, val lon: Double)

private fun haversineKm(a: Pt, b: Pt): Double {
    val r = 6371.0
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val h = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * r * asin(sqrt(h))
}

internal fun pointOf(s: Site) = Pt(s.lat!!, s.lon!!)

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
    // Ordre : celui enregistré (modifiable à la main) ; les sites pas encore classés sont ajoutés dans l'ordre calculé.
    val auto = remember(withGps.map { it.id }, startText) { planRoute(withGps, start) }
    val route = run {
        val byId = withGps.associateBy { it.id }
        val ordered = vm.tourOrder.toList().mapNotNull { byId[it] }
        ordered + auto.filter { a -> ordered.none { it.id == a.id } }
    }

    fun optimize(from: Pt?) {
        vm.setTourOrder(planRoute(withGps, from).map { it.id })
    }

    fun move(i: Int, delta: Int) {
        val j = i + delta
        if (j < 0 || j >= route.size) return
        val ids = route.map { it.id }.toMutableList()
        val t = ids[i]
        ids[i] = ids[j]
        ids[j] = t
        vm.setTourOrder(ids)
    }

    fun fetch() {
        locating = true
        requestLocation(ctx) { res ->
            locating = false
            when (res) {
                is LocationResult.Ok -> {
                    startText = res.text
                    optimize(Pt(res.lat, res.lon))
                    vm.toast("Position : ${res.text}" + (res.note?.let { " — $it" } ?: ""))
                }
                is LocationResult.Fail -> vm.toast(res.reason)
            }
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
                OutlinedButton(
                    onClick = { startText = ""; optimize(null) },
                    modifier = Modifier.weight(1f),
                    enabled = start != null,
                ) {
                    Text("Départ à l'ouest")
                }
            }
            OutlinedButton(onClick = { optimize(start) }, modifier = Modifier.fillMaxWidth(), enabled = route.size > 1) {
                Text("Recalculer l'ordre optimal")
            }
            Text(
                "Ordre modifiable : utilise les flèches ▲ ▼ sur chaque étape. Il est mémorisé.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                        IconButton(onClick = { move(i, -1) }, enabled = i > 0, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Outlined.ArrowUpward, "Monter")
                        }
                        IconButton(onClick = { move(i, 1) }, enabled = i < route.size - 1, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Outlined.ArrowDownward, "Descendre")
                        }
                        IconButton(
                            onClick = {
                                try {
                                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${s.lat},${s.lon}")))
                                } catch (e: Exception) {
                                    vm.toast("Aucune application de navigation")
                                }
                            },
                            modifier = Modifier.size(40.dp),
                        ) { Icon(Icons.Outlined.Navigation, "Itinéraire") }
                    }
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
