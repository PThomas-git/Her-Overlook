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
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.padding
import fr.heroverlook.data.KnownState
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

private fun currentFilter(vm: AppVM): CheckFilter =
    runCatching { CheckFilter.valueOf(vm.checkFilter) }.getOrDefault(CheckFilter.ALL)

/** Niveau 1 : la liste des sections avec leur avancement. On entre dans une section pour voir ses points. */
@Composable
fun ChecklistScreen(vm: AppVM, id: String) {
    val s = vm.sites[id] ?: return
    val filter = currentFilter(vm)
    Screen("Check-list", "${s.kind.label} · ${s.name.ifBlank { s.id }}", vm::back) {
        val toValidate = s.known.count { it.state == KnownState.TODO }
        if (toValidate > 0) {
            Panel(accent = MaterialTheme.colorScheme.secondary, onClick = { vm.go("site/$id/known") }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp))
                    Text("$toValidate information(s) à confirmer", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Icon(Icons.Outlined.ChevronRight, null)
                }
            }
        }
        FilterBar(s, filter) { vm.checkFilter = it.name }
        val sections = Catalog.checklist(s.kind).filter { sec -> sec.items.any { matches(s, it, filter) } }
        if (sections.isEmpty()) {
            Text("Aucun point ne correspond à ce filtre.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        sections.forEach { sec ->
            val answered = sec.items.count { (s.entries[it.id]?.verdict ?: Verdict.UNSET) != Verdict.UNSET }
            val nc = sec.items.count { s.entries[it.id]?.verdict == Verdict.NC }
            val res = sec.items.count { s.entries[it.id]?.verdict == Verdict.RESERVE }
            Panel(onClick = { vm.go("site/$id/checklist/${sec.id}") }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(sec.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                    Column(Modifier.weight(1f)) {
                        Text(sec.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Text(
                            if (filter == CheckFilter.ALL) "$answered/${sec.items.size} points"
                            else "${sec.items.count { matches(s, it, filter) }} point(s) concerné(s)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    Icon(Icons.Outlined.ChevronRight, null)
                }
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { if (sec.items.isEmpty()) 0f else answered.toFloat() / sec.items.size },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (nc > 0 || res > 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (nc > 0) Text("$nc non conforme(s)", style = MaterialTheme.typography.labelMedium, color = Verdict.NC.color(), maxLines = 1)
                        if (res > 0) Text("$res réserve(s)", style = MaterialTheme.typography.labelMedium, color = Verdict.RESERVE.color(), maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Niveau 2 : les points d'une seule section, avec passage direct à la section suivante. */
@Composable
fun ChecklistSectionScreen(vm: AppVM, id: String, secId: String) {
    val s = vm.sites[id] ?: return
    val sections = Catalog.checklist(s.kind)
    val idx = sections.indexOfFirst { it.id == secId }
    if (idx < 0) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    val sec = sections[idx]
    val filter = currentFilter(vm)
    val answered = sec.items.count { (s.entries[it.id]?.verdict ?: Verdict.UNSET) != Verdict.UNSET }
    val visible = sec.items.count { matches(s, it, filter) }
    Screen(sec.title, "$answered/${sec.items.size} points · ${s.name.ifBlank { s.id }}", vm::back) {
        if (answered < sec.items.size) {
            OutlinedButton(onClick = { vm.markUnansweredOk(id, sec.items.map { it.id }) }, modifier = Modifier.fillMaxWidth()) {
                Text("Tout OK sur les points non renseignés")
            }
        }
        if (visible == 0) {
            Text("Rien à afficher avec le filtre « ${filter.label} ».", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionBlock(vm, id, sec, filter, showTitle = false)
        val next = sections.getOrNull(idx + 1)
        if (next != null) {
            androidx.compose.material3.Button(
                onClick = { vm.replaceTop("site/$id/checklist/${next.id}") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Section suivante : ${next.title}") }
        } else {
            OutlinedButton(onClick = { vm.back() }, modifier = Modifier.fillMaxWidth()) { Text("Terminé : retour aux sections") }
        }
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
        val docs = Catalog.documents(s.kind)
        val left = docs.items.count { (s.entries[it.id]?.verdict ?: Verdict.UNSET) == Verdict.UNSET }
        if (left > 0) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { vm.markUnansweredOk(id, docs.items.map { it.id }, Verdict.NC) },
                    modifier = Modifier.weight(1f),
                ) { Text("Restants ($left) : absents") }
                OutlinedButton(
                    onClick = { vm.markUnansweredOk(id, docs.items.map { it.id }, Verdict.OK) },
                    modifier = Modifier.weight(1f),
                ) { Text("Restants ($left) : présents") }
            }
        }
        FilterBar(s, filter) { filter = it }
        SectionBlock(vm, id, docs, filter, showTitle = false)
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

/** Encadré coloré pour une information connue à confirmer ou infirmer sur place. */
@Composable
private fun ConfirmBox(title: String, text: String) {
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ItemCard(vm: AppVM, siteId: String, item: Item) {
    val s = vm.sites[siteId] ?: return
    val e = s.entries[item.id] ?: Entry()
    var open by rememberSaveable(item.id, siteId) { mutableStateOf(false) }
    val hasDetail = e.comment.isNotBlank() || e.value.isNotBlank() || e.photos.isNotEmpty()
    val prior = s.prior[item.id]
    val prev = s.rounds.lastOrNull()?.takeIf { it.entries[item.id]?.verdict != null && it.entries[item.id]?.verdict != Verdict.UNSET }

    Panel(accent = if (e.verdict == Verdict.UNSET) null else e.verdict.color(), onClick = null, animateSize = false) {
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
            ConfirmBox("Connu — à confirmer sur place", prior)
        }
        if (e.value.isNotBlank() && e.verdict == Verdict.UNSET && item.valueLabel != null) {
            ConfirmBox("${item.valueLabel} pré-rempli — à valider", e.value)
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
            if (e.verdict == Verdict.UNSET && (e.comment.isNotBlank() || e.photos.isNotEmpty() || (e.value.isNotBlank() && item.valueLabel == null))) {
                Text(
                    "Verdict à choisir",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f),
                )
            }
            TextButton(onClick = { open = !open }) {
                Text(if (open) "Masquer" else if (hasDetail) "Détails (renseignés)" else "Commentaire / photo")
                Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
            }
        }
        AnimatedVisibility(visible = open, enter = ExpandEnter, exit = ExpandExit) {
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
