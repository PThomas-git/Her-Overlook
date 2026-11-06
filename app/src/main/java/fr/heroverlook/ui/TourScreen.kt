@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package fr.heroverlook.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fr.heroverlook.AppVM
import fr.heroverlook.data.AppLog
import fr.heroverlook.data.KnownState
import fr.heroverlook.data.LocationResult
import fr.heroverlook.data.Site
import fr.heroverlook.data.TourPlanner
import fr.heroverlook.data.Verdict
import fr.heroverlook.data.VisitStatus
import fr.heroverlook.data.displayName
import fr.heroverlook.data.latLon
import fr.heroverlook.data.requestLocation

@Composable
fun TourScreen(vm: AppVM) {
    val ctx = LocalContext.current
    var selDay by rememberSaveable { mutableStateOf(0) }
    var locating by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { if (vm.tourPlan.isEmpty()) vm.planTour() }

    val base = vm.tourBase()

    // Jours effectifs : les sites terminés sont déclassés automatiquement.
    val days: List<List<Site>> = vm.tourPlan.map { ids ->
        ids.mapNotNull { vm.sites[it] }.filter { it.status != VisitStatus.DONE && it.latLon() != null }
    }
    val day = if (days.isEmpty()) 0 else selDay.coerceIn(0, days.size - 1)
    val route = days.getOrElse(day) { emptyList() }
    val planned = days.flatten().map { it.id }.toSet()
    val otherDays = days.filterIndexed { i, _ -> i != day }.flatten()
    val unplanned = vm.tourCandidates().filter { it.id !in planned }
    val done = vm.sites.values.filter { it.status == VisitStatus.DONE }.sortedBy { it.id }
    val withoutGps = vm.sites.values.filter { it.latLon() == null }

    // Mode « POP seulement » : ADR proches de la route du jour, avant / pendant / après la tournée.
    val suggestions = if (vm.tourPopOnly) {
        val candidates = vm.sites.values.filter { !it.kind.isPop && it.status != VisitStatus.DONE && it.latLon() != null && it.id !in planned }
        TourPlanner.suggestions(route, candidates, base, vm.tourDetourKm.toDouble())
    } else emptyList()

    fun fetch() {
        locating = true
        requestLocation(ctx) { res ->
            locating = false
            when (res) {
                is LocationResult.Ok -> {
                    vm.changeTourBase(res.text)
                    selDay = 0
                    vm.toast("Départ : ta position ${res.text}" + (res.note?.let { " — $it" } ?: ""))
                }
                is LocationResult.Fail -> vm.toast(res.reason)
            }
        }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) fetch() else vm.toast("Permission de localisation refusée")
    }

    Screen("Tournée", "${days.sumOf { it.size }} site(s) sur ${days.size.coerceAtLeast(1)} jour(s)", vm::back) {
        // ---------------- Réglages ----------------
        Panel(accent = MaterialTheme.colorScheme.primary) {
            Text("Réglages", style = MaterialTheme.typography.titleMedium)
            Text("Nombre de jours", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                (1..5).forEach { n ->
                    FilterChip(
                        selected = vm.tourDays == n,
                        onClick = { vm.updateTourSettings(days = n); selDay = 0 },
                        label = { Text(if (n == 1) "1 jour" else "$n jours") },
                    )
                }
            }
            Text("Départ et retour", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(
                    selected = vm.tourBaseHere.isBlank(),
                    onClick = { vm.changeTourBase(""); selDay = 0 },
                    label = { Text("Agence Saint-Aunès (clés)") },
                )
                FilterChip(
                    selected = vm.tourBaseHere.isNotBlank(),
                    onClick = { permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
                    label = { Text(if (locating) "Recherche…" else "Ma position") },
                )
            }
            Text(
                if (vm.tourBaseHere.isBlank()) "Position de l'agence approximative (centre de Saint-Aunès)." else "Départ : ${vm.tourBaseHere}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Organisation", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(
                    selected = vm.tourFarFirst,
                    onClick = { vm.updateTourSettings(farFirst = !vm.tourFarFirst) },
                    label = { Text("Commencer par les plus éloignés") },
                )
                FilterChip(
                    selected = vm.tourPopOnly,
                    onClick = { vm.updateTourSettings(popOnly = !vm.tourPopOnly); selDay = 0 },
                    label = { Text("Uniquement les POP") },
                )
            }
            if (vm.tourPopOnly) {
                Text("ADR proposés sur la route : détour maximum", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(3, 5, 10, 20).forEach { km ->
                        FilterChip(
                            selected = vm.tourDetourKm == km,
                            onClick = { vm.updateTourSettings(detourKm = km) },
                            label = { Text("$km km") },
                        )
                    }
                }
            }
            OutlinedButton(onClick = { vm.planTour(); selDay = 0 }, modifier = Modifier.fillMaxWidth()) {
                Text("Recalculer la tournée")
            }
            Text(
                "Les sites terminés sortent automatiquement de la tournée. Les flèches ▲ ▼ ajustent l'ordre à la main (conservé jusqu'au prochain recalcul).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ---------------- Choix du jour ----------------
        if (days.size > 1) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                days.forEachIndexed { i, d ->
                    val km = TourPlanner.routeKm(d, base)
                    FilterChip(
                        selected = i == day,
                        onClick = { selDay = i },
                        label = { Text("Jour ${i + 1} · ${d.size} · ${"%.0f".format(km)} km") },
                    )
                }
            }
        }

        // ---------------- Résumé du jour + carte ----------------
        if (route.isEmpty()) {
            Panel {
                Text("Rien à planifier ce jour-là.", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Aucun site avec coordonnées, non terminé, ne correspond aux réglages. Importe l'inventaire ou relève la position des sites.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            val km = TourPlanner.routeKm(route, base)
            val hours = TourPlanner.hours(km)
            Panel(accent = MaterialTheme.colorScheme.secondary) {
                Text(
                    "Jour ${day + 1} : ${route.size} site(s) · ≈ ${"%.0f".format(km)} km · ≈ ${"%d h %02d".format(hours.toInt(), ((hours - hours.toInt()) * 60).toInt())} de route",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Distances à vol d'oiseau, retour au départ compris ; temps estimé avec ×1,3 à 50 km/h de moyenne.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TourMap(route, otherDays, suggestions.map { it.site }, base) { vm.go("site/${it.id}") }
            OutlinedButton(
                onClick = {
                    val stops = route.take(10)
                    val dest = stops.last()
                    val wp = stops.dropLast(1).joinToString("|") { "${it.lat},${it.lon}" }
                    val sb = StringBuilder("https://www.google.com/maps/dir/?api=1&travelmode=driving")
                    sb.append("&origin=${base.lat},${base.lon}")
                    sb.append("&destination=${dest.lat},${dest.lon}")
                    if (wp.isNotEmpty()) sb.append("&waypoints=").append(Uri.encode(wp))
                    AppLog.i("tour", "Ouverture du jour ${day + 1} dans Maps (${stops.size} étapes)")
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

            // ---------------- Étapes ----------------
            SectionTitle("Étapes du jour ${day + 1}")
            route.forEachIndexed { i, s ->
                val prev = if (i == 0) base else route[i - 1].latLon()!!
                val leg = TourPlanner.km(prev, s.latLon()!!)
                val toValidate = s.known.count { it.state == KnownState.TODO }
                Panel(accent = statusColor(s.status), onClick = { vm.go("site/${s.id}") }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${i + 1}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                KindBadge(s.kind)
                                Text(s.displayName(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            }
                            Text(
                                listOf(s.city, s.status.label, "+${"%.1f".format(leg)} km").filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (toValidate > 0) {
                                Text("$toValidate info(s) à confirmer", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                        Row {
                            IconButton(onClick = { vm.moveInDay(day, i, -1) }, enabled = i > 0, modifier = Modifier.size(38.dp)) {
                                Icon(Icons.Outlined.ArrowUpward, "Monter")
                            }
                            IconButton(onClick = { vm.moveInDay(day, i, 1) }, enabled = i < route.size - 1, modifier = Modifier.size(38.dp)) {
                                Icon(Icons.Outlined.ArrowDownward, "Descendre")
                            }
                            if (days.size > 1) {
                                IconButton(onClick = { vm.moveToDay(day, s.id, (day + 1) % days.size) }, modifier = Modifier.size(38.dp)) {
                                    Icon(Icons.Outlined.SwapHoriz, "Passer au jour suivant")
                                }
                            }
                            IconButton(
                                onClick = {
                                    try {
                                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${s.lat},${s.lon}")))
                                    } catch (e: Exception) {
                                        vm.toast("Aucune application de navigation")
                                    }
                                },
                                modifier = Modifier.size(38.dp),
                            ) { Icon(Icons.Outlined.Navigation, "Itinéraire") }
                        }
                    }
                }
            }
        }

        // ---------------- ADR sur la route (mode POP seulement) ----------------
        if (vm.tourPopOnly && route.isNotEmpty()) {
            SectionTitle("ADR sur la route (détour ≤ ${vm.tourDetourKm} km)")
            if (suggestions.isEmpty()) {
                Text(
                    "Aucun ADR à moins de ${vm.tourDetourKm} km de détour de ce trajet. Augmente le détour maximum pour en voir plus.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TourPlanner.Phase.values().forEach { phase ->
                val group = suggestions.filter { it.phase == phase }
                if (group.isNotEmpty()) {
                    Text(phase.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    group.forEach { sg ->
                        Panel(accent = MaterialTheme.colorScheme.secondary, onClick = { vm.go("site/${sg.site.id}") }) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        KindBadge(sg.site.kind)
                                        Text(sg.site.displayName(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                    }
                                    Text(
                                        "+${"%.1f".format(sg.detourKm)} km de détour · ${sg.between}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Button(onClick = { vm.insertInDay(day, sg.index, sg.site.id) }) { Text("Ajouter") }
                            }
                        }
                    }
                }
            }
        }

        // ---------------- Non planifiés / terminés / sans coordonnées ----------------
        if (unplanned.isNotEmpty()) {
            SectionTitle("Non planifiés (${unplanned.size})")
            Panel {
                Text(
                    unplanned.joinToString(" · ") { it.displayName() },
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("Nouveaux sites ou sites rouverts : touche « Recalculer la tournée » pour les inclure.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (done.isNotEmpty()) {
            SectionTitle("Terminés, retirés de la tournée (${done.size})")
            done.forEach { s ->
                Panel(onClick = { vm.go("site/${s.id}") }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = Verdict.OK.color(), modifier = Modifier.size(20.dp))
                        Text(s.displayName(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(s.city, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (withoutGps.isNotEmpty()) {
            SectionTitle("Sans coordonnées (${withoutGps.size})")
            withoutGps.forEach { s ->
                Panel(onClick = { vm.go("site/${s.id}") }) {
                    Text(s.displayName(), style = MaterialTheme.typography.titleSmall)
                    Text("Relever la position dans « Accès » du site.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
