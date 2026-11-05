@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package fr.heroverlook.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.heroverlook.AppVM
import fr.heroverlook.data.Catalog
import fr.heroverlook.data.Usager
import fr.heroverlook.data.Verdict
import fr.heroverlook.data.stats

// ---------------------------------------------------------------- USAGERS (3.12)

@Composable
fun UsagersScreen(vm: AppVM, id: String) {
    val s = vm.sites[id] ?: return
    fun upd(uid: String, f: (Usager) -> Usager) =
        vm.update(id) { st -> st.copy(usagers = st.usagers.map { if (it.id == uid) f(it) else it }) }

    Screen("Usagers présents", s.name.ifBlank { s.id }, vm::back) {
        Text(
            "Ligne 3.12 — opérateurs et clients hébergés dans le local ou l'armoire (étiquettes, baies, contrats visibles).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        s.usagers.forEach { u ->
            Panel(accent = MaterialTheme.colorScheme.secondary) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Field("Opérateur / usager", u.name, { t -> upd(u.id) { it.copy(name = t) } }, Modifier.weight(1f))
                    IconButton(onClick = { vm.update(id) { st -> st.copy(usagers = st.usagers.filter { it.id != u.id }) } }) {
                        Icon(Icons.Outlined.Delete, "Supprimer")
                    }
                }
                Field("Référence / étiquette", u.reference, { t -> upd(u.id) { it.copy(reference = t) } })
                Field("Où (baie, emplacement)", u.location, { t -> upd(u.id) { it.copy(location = t) } })
                Field("Commentaire", u.comment, { t -> upd(u.id) { it.copy(comment = t) } }, singleLine = false, minLines = 2)
            }
        }
        OutlinedButton(onClick = { vm.update(id) { it.copy(usagers = it.usagers + Usager()) } }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Add, null)
            Text("  Ajouter un usager")
        }
    }
}

// ---------------------------------------------------------------- PHOTOS GÉNÉRALES

@Composable
fun PhotosScreen(vm: AppVM, id: String) {
    val s = vm.sites[id] ?: return
    Screen("Photos du site", s.name.ifBlank { s.id }, vm::back) {
        Panel {
            PhotoStrip(
                siteId = id, tag = "general", photos = s.generalPhotos,
                onAdd = { p -> vm.update(id) { it.copy(generalPhotos = it.generalPhotos + p) } },
                onRemove = { p -> vm.update(id) { it.copy(generalPhotos = it.generalPhotos - p) } },
                label = "Vue d'ensemble, extérieur, clôture, environnement, alentours",
            )
        }
    }
}

// ---------------------------------------------------------------- SYNTHÈSE

@Composable
fun SummaryScreen(vm: AppVM, id: String) {
    val s = vm.sites[id] ?: return
    val st = s.stats()
    var newTodo by remember { mutableStateOf("") }
    var confirmRevisit by remember { mutableStateOf(false) }

    if (confirmRevisit) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmRevisit = false },
            title = { Text("Lancer une contre-visite ?") },
            text = {
                Text(
                    "Le relevé actuel est archivé. Les ${st.reserve + st.nc} point(s) en réserve ou non conformes " +
                        "sont remis à « non renseigné » pour être revus ; tout le reste est conservé."
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmRevisit = false
                    vm.startRevisit(id)
                }) { Text("Lancer") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmRevisit = false }) { Text("Annuler") } },
        )
    }

    Screen("Synthèse", s.name.ifBlank { s.id }, vm::back) {
        Panel(accent = if (st.nc > 0) Verdict.NC.color() else if (st.reserve > 0) Verdict.RESERVE.color() else Verdict.OK.color()) {
            Text("${st.answered} point(s) renseignés sur ${st.total}", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("${st.ok} conformes", color = Verdict.OK.color(), style = MaterialTheme.typography.labelLarge)
                Text("${st.reserve} réserves", color = Verdict.RESERVE.color(), style = MaterialTheme.typography.labelLarge)
                Text("${st.nc} NC", color = Verdict.NC.color(), style = MaterialTheme.typography.labelLarge)
            }
        }

        val flagged = Catalog.allSections(s.kind).flatMap { sec -> sec.items.map { sec to it } }
            .filter { (_, item) ->
                val v = s.entries[item.id]?.verdict ?: Verdict.UNSET
                v == Verdict.RESERVE || v == Verdict.NC
            }
        if (flagged.isNotEmpty()) {
            SectionTitle("Réserves et non-conformités")
            flagged.forEach { (_, item) ->
                val e = s.entries[item.id]!!
                Panel(accent = e.verdict.color(), onClick = null) {
                    Text(item.label, style = MaterialTheme.typography.titleSmall)
                    if (e.comment.isNotBlank()) Text(e.comment, style = MaterialTheme.typography.bodyMedium)
                    if (e.photos.isNotEmpty()) Text("${e.photos.size} photo(s)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        SectionTitle("Contre-visite")
        Panel {
            if (s.rounds.isNotEmpty()) {
                Text(
                    "${s.rounds.size} visite(s) archivée(s) — dernière : ${s.rounds.last().date}",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Text(
                "Archive le relevé actuel et remet à zéro uniquement les points en réserve ou non conformes. " +
                    "Les points conformes restent acquis ; la check-list affiche alors le filtre « Contre-visite » " +
                    "avec le constat précédent sous chaque point.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { confirmRevisit = true },
                enabled = st.reserve + st.nc > 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Lancer une contre-visite (${st.reserve + st.nc} point(s))")
            }
        }

        SectionTitle("Analyse du site")
        Field(
            "Analyse libre (état général, risques, priorités, remarques pour la restitution)",
            s.analysis, { t -> vm.update(id) { it.copy(analysis = t) } }, singleLine = false, minLines = 6,
        )

        SectionTitle("À revoir / contre-visite")
        s.revisit.forEachIndexed { idx, todo ->
            Panel {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(todo, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = { vm.update(id) { st2 -> st2.copy(revisit = st2.revisit.filterIndexed { i, _ -> i != idx }) } }) {
                        Icon(Icons.Outlined.Delete, "Supprimer")
                    }
                }
            }
        }
        Field("Ajouter (document à demander, accès à prévoir, mesure à refaire…)", newTodo, { newTodo = it }, singleLine = false, minLines = 2)
        OutlinedButton(
            onClick = {
                val t = newTodo.trim()
                if (t.isNotEmpty()) {
                    vm.update(id) { it.copy(revisit = it.revisit + t) }
                    newTodo = ""
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Add, null)
            Text("  Ajouter à la liste")
        }
    }
}
