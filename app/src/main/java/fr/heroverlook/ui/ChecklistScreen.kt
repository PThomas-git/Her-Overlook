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
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.heroverlook.AppVM
import fr.heroverlook.data.Catalog
import fr.heroverlook.data.Entry
import fr.heroverlook.data.Item
import fr.heroverlook.data.Section
import fr.heroverlook.data.Verdict

@Composable
fun ChecklistScreen(vm: AppVM, id: String) {
    val s = vm.sites[id] ?: return
    Screen("Check-list", "${s.kind.label} · ${s.name.ifBlank { s.id }}", vm::back) {
        Catalog.checklist(s.kind).forEach { sec -> SectionBlock(vm, id, sec) }
    }
}

@Composable
fun DocumentsScreen(vm: AppVM, id: String) {
    val s = vm.sites[id] ?: return
    Screen("Documents sur site", s.name.ifBlank { s.id }, vm::back) {
        Text(
            "Présence et état des documents affichés ou disponibles dans le local. " +
                "Le détail des pièces à récupérer (3.x) se traite sur PC à partir de ce relevé.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SectionBlock(vm, id, Catalog.documents(s.kind), showTitle = false)
    }
}

@Composable
private fun SectionBlock(vm: AppVM, id: String, sec: Section, showTitle: Boolean = true) {
    val s = vm.sites[id] ?: return
    if (showTitle) {
        val answered = sec.items.count { (s.entries[it.id]?.verdict ?: Verdict.UNSET) != Verdict.UNSET }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(sec.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Text(sec.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("$answered/${sec.items.size}", style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    sec.items.forEach { item -> ItemCard(vm, id, item) }
}

@Composable
private fun ItemCard(vm: AppVM, siteId: String, item: Item) {
    val s = vm.sites[siteId] ?: return
    val e = s.entries[item.id] ?: Entry()
    var open by rememberSaveable(item.id, siteId) { mutableStateOf(false) }
    val hasDetail = e.comment.isNotBlank() || e.value.isNotBlank() || e.photos.isNotEmpty()

    Panel(accent = if (e.verdict == Verdict.UNSET) null else e.verdict.color(), onClick = null) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.label, style = MaterialTheme.typography.titleSmall)
                if (item.hint.isNotBlank()) {
                    Text(item.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (item.ref.isNotBlank()) {
                    Text("réf. ${item.ref}", style = MonoStyle, color = MaterialTheme.colorScheme.secondary)
                }
            }
            if (e.photos.isNotEmpty()) {
                Icon(Icons.Outlined.PhotoCamera, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${e.photos.size}", style = MaterialTheme.typography.labelMedium)
            }
        }
        VerdictBar(e.verdict, item.kind, { v ->
            vm.setEntry(siteId, item.id) { it.copy(verdict = v) }
            if (v == Verdict.RESERVE || v == Verdict.NC) open = true
        })
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            androidx.compose.material3.TextButton(onClick = { open = !open }) {
                Text(if (open) "Masquer" else if (hasDetail) "Détails (renseignés)" else "Commentaire / photo")
                Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
            }
        }
        AnimatedVisibility(visible = open) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (item.valueLabel != null) {
                    Field(item.valueLabel, e.value, { t -> vm.setEntry(siteId, item.id) { it.copy(value = t) } })
                }
                Field("Commentaire", e.comment, { t -> vm.setEntry(siteId, item.id) { it.copy(comment = t) } }, singleLine = false, minLines = 2)
                PhotoStrip(
                    siteId = siteId, tag = item.id, photos = e.photos,
                    onAdd = { p -> vm.setEntry(siteId, item.id) { it.copy(photos = it.photos + p) } },
                    onRemove = { p -> vm.setEntry(siteId, item.id) { it.copy(photos = it.photos - p) } },
                )
            }
        }
    }
}
