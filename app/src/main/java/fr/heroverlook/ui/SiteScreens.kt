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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Architecture
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import fr.heroverlook.data.displayName
import fr.heroverlook.data.latLon
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.heroverlook.AppVM
import fr.heroverlook.data.AccessMethods
import fr.heroverlook.data.ItemKind
import fr.heroverlook.data.Site
import fr.heroverlook.data.Verdict
import fr.heroverlook.data.VisitStatus
import fr.heroverlook.data.LocationResult
import fr.heroverlook.data.requestLocation
import fr.heroverlook.data.allPhotos
import fr.heroverlook.data.stats

@Composable
fun SiteScreen(vm: AppVM, id: String) {
    val s = vm.sites[id] ?: return
    val ctx = LocalContext.current
    val st = s.stats()
    val layoutFilled = s.layout.deviations.size + s.layout.photos.size
    val confirmDelete = remember { mutableStateOf(false) }
    val menuOpen = remember { mutableStateOf(false) }
    val swapOpen = remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.export(uri, id)
    }

    // Garde-fou « bon site ? » : si la permission est déjà accordée, on compare ta position à celle du site (sans rien demander).
    val here = remember(id) { mutableStateOf<fr.heroverlook.data.LatLon?>(null) }
    val hereAcc = remember(id) { mutableStateOf(0f) }
    val hereNote = remember(id) { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(id) {
        val granted = ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted && s.latLon() != null) {
            requestLocation(ctx) { r ->
                if (r is LocationResult.Ok) {
                    here.value = fr.heroverlook.data.LatLon(r.lat, r.lon)
                    hereAcc.value = r.accuracy
                    hereNote.value = r.note
                }
            }
        }
    }
    if (swapOpen.value) SwapDialog(vm, s) { swapOpen.value = false }
    if (confirmDelete.value) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete.value = false },
            title = { Text("Supprimer ce site ?") },
            text = {
                Text(
                    "${s.name.ifBlank { s.id }} : le relevé et les ${s.allPhotos().size} photo(s) seront effacés " +
                        "de ce téléphone, sans retour possible. Pense à exporter avant. " +
                        "Réimporter l'inventaire recréera le site, vide."
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmDelete.value = false
                    vm.back()
                    vm.deleteSite(id)
                }) { Text("Supprimer") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmDelete.value = false }) { Text("Annuler") } },
        )
    }
    Screen(
        title = s.name.ifBlank { s.id },
        subtitle = s.id,
        onBack = vm::back,
        actions = {
            androidx.compose.foundation.layout.Box {
                androidx.compose.material3.IconButton(onClick = { menuOpen.value = true }) {
                    Icon(Icons.Outlined.MoreVert, "Menu du site")
                }
                androidx.compose.material3.DropdownMenu(expanded = menuOpen.value, onDismissRequest = { menuOpen.value = false }) {
                    androidx.compose.material3.DropdownMenuItem(text = { Text("Exporter ce site (ZIP)") }, onClick = {
                        menuOpen.value = false
                        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd_HHmm", java.util.Locale.US).format(java.util.Date())
                        exportLauncher.launch("HerOverlook_${fr.heroverlook.data.SiteStore.safeName(id)}_$stamp.zip")
                    })
                    androidx.compose.material3.DropdownMenuItem(text = { Text("Échanger le relevé avec un autre site…") }, onClick = {
                        menuOpen.value = false
                        swapOpen.value = true
                    })
                    androidx.compose.material3.DropdownMenuItem(text = { Text("Supprimer le site…") }, onClick = {
                        menuOpen.value = false
                        confirmDelete.value = true
                    })
                }
            }
        },
    ) {
        // Position : bon site ? (affiché seulement si la position est connue et assez précise)
        val h = here.value
        val me = s.latLon()
        if (h != null && me != null && hereAcc.value <= 1500f) {
            val d = fr.heroverlook.data.TourPlanner.km(h, me)
            val nearest = vm.sites.values.filter { it.id != id }
                .mapNotNull { o -> o.latLon()?.let { p -> o to fr.heroverlook.data.TourPlanner.km(h, p) } }
                .minByOrNull { it.second }
            val stale = hereNote.value?.let { " (${it})" } ?: ""
            if (d <= 1.0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.MyLocation, null, tint = Verdict.OK.color(), modifier = Modifier.size(18.dp))
                    Text("Sur place : à ${fr.heroverlook.data.TourPlanner.fmtDist(d)} de ce site$stale", style = MaterialTheme.typography.labelLarge, color = Verdict.OK.color())
                }
            } else {
                Panel(accent = Verdict.RESERVE.color()) {
                    Text("Tu es à ${fr.heroverlook.data.TourPlanner.fmtDist(d)} de ce site$stale", style = MaterialTheme.typography.titleSmall)
                    Text("Vérifie que c'est bien le bon site avant de saisir.", style = MaterialTheme.typography.bodySmall)
                    if (nearest != null && nearest.second < d && nearest.second < 1.5) {
                        OutlinedButton(onClick = { vm.replaceTop("site/${nearest.first.id}") }, modifier = Modifier.fillMaxWidth()) {
                            Text("Ouvrir ${nearest.first.displayName()} (${fr.heroverlook.data.TourPlanner.fmtDist(nearest.second)})")
                        }
                    }
                }
            }
        }

        // En-tête : l'essentiel sur deux lignes ; le détail de l'inventaire est replié.
        Panel(accent = statusColor(s.status)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KindBadge(s.kind)
                Text(
                    listOf(s.postalCode, s.city).filter { it.isNotBlank() }.joinToString(" "),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
            }
            if (s.address.isNotBlank()) {
                Text(s.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            if (s.inventoryNote.isNotBlank()) {
                Text("À vérifier : ${s.inventoryNote}", style = MaterialTheme.typography.bodySmall, color = Verdict.RESERVE.color())
            }
        }
        Collapsible(
            title = "Détails d'inventaire",
            summary = listOf(s.tier, s.locCode).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null },
        ) {
            InfoLine("Code LOC", s.locCode)
            InfoLine("Gamme", s.tier)
            InfoLine("Actifs", s.activeEquipment)
            InfoLine("Refroid.", s.cooling)
            InfoLine("AE / onduleur", s.power)
        }

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val all = VisitStatus.values()
            all.forEachIndexed { i, v ->
                SegmentedButton(
                    selected = s.status == v,
                    onClick = {
                        vm.update(id) {
                            it.copy(
                                status = v,
                                visitDate = if (it.visitDate.isBlank() && v != VisitStatus.TODO)
                                    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                                else it.visitDate
                            )
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(i, all.size),
                ) { Text(v.label) }
            }
        }

        if (s.known.isNotEmpty()) {
            val todo = s.known.count { it.state == fr.heroverlook.data.KnownState.TODO }
            Panel(accent = if (todo > 0) MaterialTheme.colorScheme.secondary else Verdict.OK.color(), onClick = { vm.go("site/$id/known") }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(
                        Icons.Outlined.Info, null,
                        tint = if (todo > 0) MaterialTheme.colorScheme.secondary else Verdict.OK.color(),
                        modifier = Modifier.size(26.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (todo > 0) "$todo information(s) à confirmer" else "Informations connues : toutes validées",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "${s.known.size} donnée(s) issue(s) des rapports et fiches · touche pour valider ou corriger",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // Aperçu direct des premières informations à confirmer
                s.known.filter { it.state == fr.heroverlook.data.KnownState.TODO }.take(3).forEach { k ->
                    Column {
                        Text(k.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(k.value, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
            }
        }

        // Deux groupes de tuiles, deux colonnes : ce qu'on fait sur place, puis les compléments.
        SectionTitle("Sur place")
        TileGrid(
            buildList {
                add(TileSpec(Icons.Outlined.Lock, "Accès", accessSummary(s), "site/$id/access"))
                // Baies avant l'aménagement : le plan reprend alors les baies déjà saisies (liaison naturelle).
                add(
                    TileSpec(
                        Icons.Outlined.Inventory2,
                        if (s.kind.isPop) "Baies" else "Équipements",
                        "${s.racks.size} baie(s) · ${s.racks.sumOf { it.equipments.size }} équip.",
                        "site/$id/racks",
                    )
                )
                if (s.kind.isPop) {
                    add(TileSpec(Icons.Outlined.Architecture, "Aménagement", "$layoutFilled élément(s)", "site/$id/layout"))
                }
                add(TileSpec(Icons.Outlined.Checklist, "Check-list", "${st.answered}/${st.total} points", "site/$id/checklist"))
                add(TileSpec(Icons.Outlined.Description, "Documents", "présence sur site", "site/$id/docs"))
            },
            vm,
        )
        SectionTitle("Compléments")
        TileGrid(
            listOf(
                TileSpec(Icons.Outlined.Groups, "Usagers", "${s.usagers.size} présent(s)", "site/$id/usagers"),
                TileSpec(Icons.Outlined.PhotoLibrary, "Photos", "${s.generalPhotos.size} générales", "site/$id/photos"),
                TileSpec(Icons.Outlined.Summarize, "Synthèse", "${st.reserve} rés. · ${st.nc} NC", "site/$id/summary"),
            ),
            vm,
        )
    }
}

/** Choix du site avec lequel échanger le relevé (erreur de saisie : le mauvais site a été rempli). */
@Composable
private fun SwapDialog(vm: AppVM, site: Site, onDismiss: () -> Unit) {
    val target = remember { mutableStateOf<String?>(null) }
    val others = vm.sites.values.filter { it.id != site.id }.sortedWith(compareBy({ it.kind != site.kind }, { it.id }))
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Échanger le relevé") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Le relevé saisi sur « ${site.displayName()} » (photos, check-list, baies, plan…) passe sur le site choisi, et inversement. " +
                        "L'identité des sites (adresse, données connues) ne change pas ; les validations « déjà connu » sont remises à zéro. " +
                        "Pense à exporter avant.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Column(Modifier.heightIn(max = 260.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    others.forEach { o ->
                        Row(
                            Modifier.fillMaxWidth().clickable { target.value = o.id },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            androidx.compose.material3.RadioButton(selected = target.value == o.id, onClick = { target.value = o.id })
                            KindBadge(o.kind)
                            Text(o.displayName(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 2)
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                enabled = target.value != null,
                onClick = {
                    val t = target.value
                    if (t != null) {
                        onDismiss()
                        vm.swapReleve(site.id, t)
                    }
                },
            ) { Text("Échanger") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

private fun accessSummary(s: Site): String =
    if (s.accessResult == Verdict.UNSET) "à tester" else when (s.accessResult) {
        Verdict.OK -> "OK"
        Verdict.RESERVE -> "réserve"
        Verdict.NC -> "non conforme"
        else -> "S.O."
    }

private class TileSpec(val icon: ImageVector, val title: String, val sub: String, val route: String)

/** Tuiles en deux colonnes ; une tuile seule garde la largeur d'une colonne. */
@Composable
private fun TileGrid(tiles: List<TileSpec>, vm: AppVM) {
    tiles.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            pair.forEach { t -> Tile(t.icon, t.title, t.sub, Modifier.weight(1f)) { vm.go(t.route) } }
            if (pair.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun Tile(icon: ImageVector, title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    Panel(modifier = modifier, onClick = onClick) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(sub, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------- ACCÈS

@Composable
fun AccessScreen(vm: AppVM, id: String) {
    val s = vm.sites[id] ?: return
    val ctx = LocalContext.current
    val locating = remember { mutableStateOf(false) }
    val locError = remember { mutableStateOf<String?>(null) }

    fun fetch() {
        locating.value = true
        requestLocation(ctx) { res ->
            locating.value = false
            when (res) {
                is LocationResult.Ok -> {
                    vm.update(id) { it.copy(measuredGps = res.text) }
                    if (res.note != null) vm.toast("Position relevée (${res.note})")
                }
                is LocationResult.Fail -> locError.value = res.reason
            }
        }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) fetch()
        else locError.value = "Permission de localisation refusée : autorise « Position » pour Hér'Overlook dans les réglages de l'appli."
    }
    LocationErrorDialog(locError.value) { locError.value = null }

    Screen("Accès", s.name.ifBlank { s.id }, vm::back) {
        Panel {
            SectionTitle("Prévu à l'inventaire")
            InfoLine("Heures ouvrées", s.accessHO)
            InfoLine("Hors heures", s.accessHNO)
            InfoLine("Adresse", s.address)
        }

        Panel {
            SectionTitle("Constat sur place")
            Text("Accès au site testé", style = MaterialTheme.typography.labelLarge)
            VerdictBar(s.accessResult, ItemKind.CONFORMITY, { v -> vm.update(id) { it.copy(accessResult = v) } })
            Text("Moyens utilisés", style = MaterialTheme.typography.labelLarge)
            MultiChips(AccessMethods.all, s.accessMethods) { l -> vm.update(id) { it.copy(accessMethods = l) } }
            Field("Observations d'accès (clé, badge, portail, difficultés…)", s.accessNote, { t -> vm.update(id) { it.copy(accessNote = t) } }, singleLine = false, minLines = 3)
            Field("Contact / gardien sur place", s.onSiteContact, { t -> vm.update(id) { it.copy(onSiteContact = t) } })
            PhotoStrip(
                siteId = id, tag = "acces", photos = s.accessPhotos,
                onAdd = { p -> vm.update(id) { it.copy(accessPhotos = it.accessPhotos + p) } },
                onRemove = { p -> vm.update(id) { it.copy(accessPhotos = it.accessPhotos - p) } },
                label = "Photos (portail, porte, serrure, plaque, environnement)",
            )
        }

        Panel {
            SectionTitle("Position")
            val inv = if (s.lat != null && s.lon != null) "${s.lat}, ${s.lon}" else "—"
            InfoLine("Inventaire", inv)
            Field("Position relevée sur place", s.measuredGps, { t -> vm.update(id) { it.copy(measuredGps = t) } }, mono = false, keyboard = KeyboardType.Text)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.MyLocation, null)
                    Text(if (locating.value) "  Recherche…" else "  Relever")
                }
                OutlinedButton(onClick = {
                    val lat = s.lat
                    val lon = s.lon
                    val uri = if (lat != null && lon != null) Uri.parse("google.navigation:q=$lat,$lon")
                    else Uri.parse("geo:0,0?q=" + Uri.encode("${s.address} ${s.postalCode} ${s.city}"))
                    try {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    } catch (e: Exception) {
                        vm.toast("Aucune application de navigation")
                    }
                }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Navigation, null)
                    Text("  Itinéraire")
                }
            }
        }
    }
}
