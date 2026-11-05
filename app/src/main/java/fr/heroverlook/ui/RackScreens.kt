@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package fr.heroverlook.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AssistChip
import androidx.compose.ui.platform.LocalContext
import fr.heroverlook.data.Ocr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.heroverlook.AppVM
import fr.heroverlook.data.Equipment
import fr.heroverlook.data.EquipmentTypes
import fr.heroverlook.data.ItemKind
import fr.heroverlook.data.Rack
import fr.heroverlook.data.RackKinds
import fr.heroverlook.data.Verdict

// ---------------------------------------------------------------- LISTE DES BAIES

@Composable
fun RacksScreen(vm: AppVM, id: String) {
    val s = vm.sites[id] ?: return
    val pop = s.kind.isPop
    Screen(if (pop) "Baies" else "Équipements de l'armoire", s.name.ifBlank { s.id }, vm::back) {
        Text(
            if (pop) "Ligne 3.6 — une fiche par baie (numéro repris du plan d'aménagement), photos de face et équipements avec n° de série."
            else "Une fiche « Armoire » regroupe les équipements, batteries et atelier d'énergie avec leurs n° de série.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        s.racks.forEach { r ->
            Panel(onClick = { vm.go("site/$id/rack/${r.id}") }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        r.label.ifBlank { "?" },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(r.kind, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    if (r.photos.isNotEmpty()) {
                        Icon(Icons.Outlined.PhotoCamera, null, modifier = Modifier.size(18.dp))
                        Text("${r.photos.size}", style = MaterialTheme.typography.labelMedium)
                    }
                }
                val line = listOf(r.usager, r.description).filter { it.isNotBlank() }.joinToString(" · ")
                if (line.isNotBlank()) Text(line, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${r.equipments.size} équipement(s)" + if (r.freeU.isNotBlank()) " · ${r.freeU} U libres" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Button(onClick = {
            val r = Rack(
                label = if (pop) (s.racks.size + 1).toString() else "Armoire",
                kind = if (pop) "Active" else "Armoire ADR",
            )
            vm.update(id) { it.copy(racks = it.racks + r) }
            vm.go("site/$id/rack/${r.id}")
        }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Add, null)
            Text(if (pop) "  Ajouter une baie" else "  Ajouter une armoire / fiche")
        }
    }
}

// ---------------------------------------------------------------- FICHE BAIE

@Composable
fun RackScreen(vm: AppVM, siteId: String, rackId: String) {
    val s = vm.sites[siteId] ?: return
    val r = s.racks.firstOrNull { it.id == rackId }
    if (r == null) {
        Screen("Baie introuvable", null, vm::back) { Text("Cette fiche a été supprimée.") }
        return
    }
    var confirmDelete by remember { mutableStateOf(false) }
    fun upd(f: (Rack) -> Rack) = vm.updateRack(siteId, rackId, f)
    val tag = "baie${r.label}"

    Screen(
        title = "Baie ${r.label}".trim(),
        subtitle = s.name.ifBlank { s.id },
        onBack = vm::back,
        actions = {
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Supprimer la baie") }
        },
    ) {
        Panel {
            SectionTitle("Identification")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("N° / position", r.label, { t -> upd { it.copy(label = t) } }, Modifier.weight(1f))
                Field("U total", r.totalU, { t -> upd { it.copy(totalU = t) } }, Modifier.weight(1f), keyboard = KeyboardType.Number)
                Field("U libres", r.freeU, { t -> upd { it.copy(freeU = t) } }, Modifier.weight(1f), keyboard = KeyboardType.Number)
            }
            ChoiceChips(RackKinds.all, r.kind) { k -> upd { it.copy(kind = k.ifBlank { r.kind }) } }
            Field("Usager / référence (opérateur, client)", r.usager, { t -> upd { it.copy(usager = t) } })
            Field("Description (contenu, étiquettes, particularités)", r.description, { t -> upd { it.copy(description = t) } }, singleLine = false, minLines = 3)
        }

        Panel {
            SectionTitle("Photos")
            PhotoStrip(
                siteId = siteId, tag = tag, photos = r.photos,
                onAdd = { p -> upd { it.copy(photos = it.photos + p) } },
                onRemove = { p -> upd { it.copy(photos = it.photos - p) } },
                label = "Face avant, face arrière, détails (cadrer l'ensemble puis chaque tiroir)",
            )
        }

        Panel {
            SectionTitle("Jarretiérage de la baie")
            VerdictBar(r.jumpers, ItemKind.CONFORMITY, { v -> upd { it.copy(jumpers = v) } })
            Field("Commentaire", r.comment, { t -> upd { it.copy(comment = t) } }, singleLine = false, minLines = 2)
        }

        SectionTitle("Équipements (${r.equipments.size})")
        r.equipments.forEach { e -> EquipmentCard(siteId, tag, e, ::upd) }
        OutlinedButton(onClick = { upd { it.copy(equipments = it.equipments + Equipment()) } }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Add, null)
            Text("  Ajouter un équipement")
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer cette baie ?") },
            text = { Text("La fiche et ses équipements seront supprimés. Les photos restent dans le téléphone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.back()
                    vm.update(siteId) { st -> st.copy(racks = st.racks.filter { it.id != rackId }) }
                }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun EquipmentCard(siteId: String, rackTag: String, e: Equipment, updRack: ((Rack) -> Rack) -> Unit) {
    var open by rememberSaveable(e.id) { mutableStateOf(e.type.isBlank() && e.serial.isBlank()) }
    fun upd(f: (Equipment) -> Equipment) =
        updRack { r -> r.copy(equipments = r.equipments.map { if (it.id == e.id) f(it) else it }) }

    // Lecture automatique du n° de série : photo de l'étiquette, puis propositions à valider.
    val ctx = LocalContext.current
    var candidates by remember(e.id) { mutableStateOf<List<String>>(emptyList()) }
    var scanning by remember(e.id) { mutableStateOf(false) }
    var scanned by remember(e.id) { mutableStateOf(false) }
    val scan = rememberPhotoTaker(siteId, "${rackTag}_sn") { rel ->
        upd { it.copy(photos = it.photos + rel) }
        scanning = true
        Ocr.read(ctx, rel) { list ->
            scanning = false
            scanned = true
            candidates = list
        }
    }

    Panel(accent = MaterialTheme.colorScheme.primary, animateSize = false) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    listOf(e.type, e.brand, e.model).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Équipement" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (e.serial.isNotBlank()) Text("S/N ${e.serial}", style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { open = !open }) {
                Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, "Déplier")
            }
        }
        AnimatedVisibility(visible = open, enter = ExpandEnter, exit = ExpandExit) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ChoiceChips(EquipmentTypes.all, e.type) { t -> upd { it.copy(type = t) } }
                Field("Type (libre)", e.type, { t -> upd { it.copy(type = t) } })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("Marque", e.brand, { t -> upd { it.copy(brand = t) } }, Modifier.weight(1f))
                    Field("Modèle", e.model, { t -> upd { it.copy(model = t) } }, Modifier.weight(1f))
                }
                Field("N° de série", e.serial, { t -> upd { it.copy(serial = t.trim()) } }, mono = true)
                OutlinedButton(onClick = scan, enabled = !scanning, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.QrCodeScanner, null)
                    Text(if (scanning) "  Lecture en cours…" else "  Scanner l'étiquette (lecture auto)")
                }
                if (candidates.isNotEmpty()) {
                    Text(
                        "Propositions lues sur la photo — touche la bonne valeur (à vérifier) :",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        candidates.forEach { c ->
                            AssistChip(
                                onClick = { upd { it.copy(serial = c) }; candidates = emptyList() },
                                label = { Text(c, style = MonoStyle) },
                            )
                        }
                    }
                } else if (scanned && !scanning) {
                    Text(
                        "Aucun numéro lisible : rapproche-toi, évite les reflets, ou saisis-le à la main (la photo est conservée).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("Mise en service", e.commissioningDate, { t -> upd { it.copy(commissioningDate = t) } }, Modifier.weight(1f))
                    Field("N° de licence", e.license, { t -> upd { it.copy(license = t) } }, Modifier.weight(1f), mono = true)
                }
                Field("Usager / service porté", e.usager, { t -> upd { it.copy(usager = t) } })
                Field("Commentaire", e.comment, { t -> upd { it.copy(comment = t) } }, singleLine = false, minLines = 2)
                PhotoStrip(
                    siteId = siteId, tag = "${rackTag}_equip", photos = e.photos,
                    onAdd = { p -> upd { it.copy(photos = it.photos + p) } },
                    onRemove = { p -> upd { it.copy(photos = it.photos - p) } },
                    label = "Photos (étiquette n° de série, face avant)",
                )
                TextButton(onClick = { updRack { r -> r.copy(equipments = r.equipments.filter { it.id != e.id }) } }) {
                    Icon(Icons.Outlined.Delete, null)
                    Text("  Supprimer cet équipement")
                }
            }
        }
    }
}
