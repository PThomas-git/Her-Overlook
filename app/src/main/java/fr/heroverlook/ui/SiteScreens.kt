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
    val st = s.stats()
    val layoutFilled = s.layout.deviations.size + s.layout.photos.size
    val confirmDelete = remember { mutableStateOf(false) }
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
            androidx.compose.material3.IconButton(onClick = { confirmDelete.value = true }) {
                Icon(Icons.Outlined.Delete, "Supprimer le site")
            }
        },
    ) {
        Panel(accent = statusColor(s.status)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KindBadge(s.kind)
                Text(listOf(s.postalCode, s.city).filter { it.isNotBlank() }.joinToString(" "), style = MaterialTheme.typography.bodyMedium)
            }
            InfoLine("Adresse", s.address)
            InfoLine("Code LOC", s.locCode)
            InfoLine("Gamme", s.tier)
            InfoLine("Actifs", s.activeEquipment)
            InfoLine("Refroid.", s.cooling)
            InfoLine("AE / onduleur", s.power)
            if (s.inventoryNote.isNotBlank()) {
                Text("À vérifier : ${s.inventoryNote}", style = MaterialTheme.typography.bodySmall, color = Verdict.RESERVE.color())
            }
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

        SectionTitle("Relevé")
        TileRow {
            Tile(Icons.Outlined.Lock, "Accès", accessSummary(s), Modifier.weight(1f)) { vm.go("site/$id/access") }
            Tile(Icons.Outlined.Checklist, "Check-list", "${st.answered}/${st.total} points", Modifier.weight(1f)) { vm.go("site/$id/checklist") }
        }
        TileRow {
            if (s.kind.isPop) {
                Tile(Icons.Outlined.Architecture, "Aménagement", "$layoutFilled élément(s) · 3.4", Modifier.weight(1f)) { vm.go("site/$id/layout") }
            }
            Tile(
                Icons.Outlined.Inventory2,
                if (s.kind.isPop) "Baies" else "Équipements",
                "${s.racks.size} baie(s) · ${s.racks.sumOf { it.equipments.size }} équip. · 3.6",
                Modifier.weight(1f),
            ) { vm.go("site/$id/racks") }
            if (!s.kind.isPop) Tile(Icons.Outlined.Description, "Documents", "présence sur site", Modifier.weight(1f)) { vm.go("site/$id/docs") }
        }
        TileRow {
            if (s.kind.isPop) Tile(Icons.Outlined.Description, "Documents", "présence sur site", Modifier.weight(1f)) { vm.go("site/$id/docs") }
            Tile(Icons.Outlined.Groups, "Usagers", "${s.usagers.size} · 3.12", Modifier.weight(1f)) { vm.go("site/$id/usagers") }
            if (!s.kind.isPop) Tile(Icons.Outlined.PhotoLibrary, "Photos", "${s.generalPhotos.size} générales", Modifier.weight(1f)) { vm.go("site/$id/photos") }
        }
        TileRow {
            if (s.kind.isPop) Tile(Icons.Outlined.PhotoLibrary, "Photos", "${s.generalPhotos.size} générales", Modifier.weight(1f)) { vm.go("site/$id/photos") }
            Tile(Icons.Outlined.Summarize, "Synthèse", "${st.reserve} rés. · ${st.nc} NC", Modifier.weight(1f)) { vm.go("site/$id/summary") }
        }
    }
}

private fun accessSummary(s: Site): String =
    if (s.accessResult == Verdict.UNSET) "à tester" else when (s.accessResult) {
        Verdict.OK -> "OK"
        Verdict.RESERVE -> "réserve"
        Verdict.NC -> "non conforme"
        else -> "S.O."
    }

@Composable
private fun TileRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
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

    fun fetch() {
        locating.value = true
        requestLocation(ctx) { res ->
            locating.value = false
            when (res) {
                is LocationResult.Ok -> {
                    vm.update(id) { it.copy(measuredGps = res.text) }
                    if (res.note != null) vm.toast("Position relevée (${res.note})")
                }
                is LocationResult.Fail -> vm.toast(res.reason)
            }
        }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) fetch() else vm.toast("Permission de localisation refusée")
    }

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
