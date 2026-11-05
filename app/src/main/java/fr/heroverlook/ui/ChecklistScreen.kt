@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package fr.heroverlook.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import fr.heroverlook.data.Site
import fr.heroverlook.data.Verdict

private enum class CheckFilter(val label: String) {
    ALL("Tous"), TODO("Non renseignés"), FLAGGED("Réserves / NC"), REVISIT("Contre-visite")
}

private fun matches(s: Site, item: Item, f: CheckFilter): Boolean {
    val v = s.entries[item.id]?.verdict ?: Verdict.UNSET
    return when (f) {
        CheckFilter.ALL -> true
        CheckFilter.TODO -> v == Verdict.UNSET
        CheckFilter.FLAGGED -> v == Verdict.RESERVE || v == Verdict.NC
        CheckFilter.REVISIT -> item.id in s.revisitIds
    }
}

@Composable
private fun FilterBar(s: Site, filter: CheckFilter, onChange: (CheckFilter) -> Unit) {
    val items = Catalog.allItems(s.kind)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CheckFilter.values().forEach { f ->
            if (f == CheckFilter.REVISIT && s.revisitIds.isEmpty()) return@forEach
            val n = items.count { matches(s, it, f) }
            FilterChip(
                selected = filter == f,
                onClick = { onChange(f) },
                label = { Text(if (f == CheckFilter.ALL) f.label else "${f.label} ($n)") },
            )
        }
    }
}

@Composable
fun ChecklistScreen(vm: AppVM, id: String) {
    val s = vm.sites[id] ?: return
    var filter by rememberSaveable { mutableStateOf(CheckFilter.ALL) }
    Screen("Check-list", "${s.kind.label} · ${s.name.ifBlank { s.id }}", vm::back) {
        FilterBar(s, filter) { filter = it }
        Catalog.checklist(s.kind).forEach { sec -> SectionBlock(vm, id, sec, filter) }
    }
}

@Composable
fun DocumentsScreen(vm: AppVM, id: String) {
    val s = vm.sites[id] ?: return
    var filter by rememberSaveable { mutableStateOf(CheckFilter.ALL) }
    Screen("Documents sur site", s.name.ifBlank { s.id }, vm::back) {
        Text(
            "Présence et état des documents affichés ou disponibles dans le local. " +
                "Le détail des pièces à récupérer (3.x) se traite sur PC à partir de ce relevé.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilterBar(s, filter) { filter = it }
        SectionBlock(vm, id, Catalog.documents(s.kind), filter, showTitle = false)
    }
}

@Composable
private fun SectionBlock(vm: AppVM, id: String, sec: Section, filter: CheckFilter, showTitle: Boolean = true) {
    val s = vm.sites[id] ?: return
    val visible = sec.items.filter { matches(s, it, filter) }
    if (visible.isEmpty()) return
    if (showTitle) {
        val answered = sec.items.count { (s.entries[it.id]?.verdict ?: Verdict.UNSET) != Verdict.UNSET }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(sec.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Text(sec.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("$answered/${sec.items.size}", style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (answered < sec.items.size) {
                TextButton(onClick = { vm.markUnansweredOk(id, sec.items.map { it.id }) }) {
                    Text("Tout OK")
                }
            }
        }
    }
    visible.forEach { item -> ItemCard(vm, id, item) }
}

@Composable
private fun ItemCard(vm: AppVM, siteId: String, item: Item) {
    val s = vm.sites[siteId] ?: return
    val e = s.entries[item.id] ?: Entry()
    var open by rememberSaveable(item.id, siteId) { mutableStateOf(false) }
    val hasDetail = e.comment.isNotBlank() || e.value.isNotBlank() || e.photos.isNotEmpty()
    val prior = s.prior[item.id]
    val prev = s.rounds.lastOrNull()?.takeIf { it.entries[item.id]?.verdict != null && it.entries[item.id]?.verdict != Verdict.UNSET }

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
        // Connu avant la visite (rapports de maintenance…) : à confirmer ou infirmer
        if (!prior.isNullOrBlank()) {
            Text("Connu : $prior", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
        if (e.value.isNotBlank() && e.verdict == Verdict.UNSET && item.valueLabel != null) {
            Text("${item.valueLabel} (pré-rempli, à valider) : ${e.value}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
        // Contre-visite : rappel du constat précédent
        if (prev != null) {
            val pe = prev.entries.getValue(item.id)
            Text(
                "Visite du ${prev.date} : ${pe.verdict.shortLabel(item.kind)}" + if (pe.comment.isNotBlank()) " — ${pe.comment}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = Verdict.RESERVE.color(),
            )
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
            TextButton(onClick = { open = !open }) {
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
