@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package fr.heroverlook.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.heroverlook.AppVM
import fr.heroverlook.data.Deviation
import fr.heroverlook.data.Layout
import fr.heroverlook.data.Verdict

/**
 * Relevé de l'aménagement intérieur (3.4).
 * Pas de dessin sur le téléphone : on collecte tout ce qu'il faut pour refaire le plan sur PC
 * (dimensions, vues de chaque mur, écarts par rapport au plan de référence, croquis photographié).
 */
@Composable
fun LayoutScreen(vm: AppVM, id: String) {
    val s = vm.sites[id] ?: return
    val l = s.layout
    fun upd(f: (Layout) -> Layout) = vm.update(id) { it.copy(layout = f(it.layout)) }

    Screen("Aménagement intérieur", s.name.ifBlank { s.id }, vm::back) {
        Text(
            "Ligne 3.4 — partir du plan le plus pertinent, noter les écarts constatés ; le plan à jour est refait sur PC.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Panel {
            SectionTitle("Plan de référence")
            Field("Plan utilisé (fichier, version, date)", l.referencePlan, { t -> upd { it.copy(referencePlan = t) } }, singleLine = false, minLines = 2)
        }

        Panel {
            SectionTitle("Local")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Longueur (m)", l.lengthM, { t -> upd { it.copy(lengthM = t) } }, Modifier.weight(1f), keyboard = KeyboardType.Decimal)
                Field("Largeur (m)", l.widthM, { t -> upd { it.copy(widthM = t) } }, Modifier.weight(1f), keyboard = KeyboardType.Decimal)
                Field("Haut. (m)", l.heightM, { t -> upd { it.copy(heightM = t) } }, Modifier.weight(1f), keyboard = KeyboardType.Decimal)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Portes", l.doors, { t -> upd { it.copy(doors = t) } }, Modifier.weight(1f), keyboard = KeyboardType.Number)
                Field("Positions de baies", l.rackPositions, { t -> upd { it.copy(rackPositions = t) } }, Modifier.weight(1f), keyboard = KeyboardType.Number)
            }
            Field("Commentaire général (sens d'ouverture, chemins de câbles, faux plancher…)", l.comment, { t -> upd { it.copy(comment = t) } }, singleLine = false, minLines = 3)
        }

        Panel {
            SectionTitle("Photos")
            PhotoStrip(
                siteId = id, tag = "amenagement", photos = l.photos,
                onAdd = { p -> upd { it.copy(photos = it.photos + p) } },
                onRemove = { p -> upd { it.copy(photos = it.photos - p) } },
                label = "Vues du local (4 murs, angles, plafond, sol)",
            )
            PhotoStrip(
                siteId = id, tag = "croquis", photos = l.sketches,
                onAdd = { p -> upd { it.copy(sketches = it.sketches + p) } },
                onRemove = { p -> upd { it.copy(sketches = it.sketches - p) } },
                label = "Croquis papier / plan annoté à la main",
            )
        }

        SectionTitle("Écarts avec le plan de référence")
        l.deviations.forEach { d ->
            Panel(accent = Verdict.RESERVE.color()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(
                        "Écart constaté (ex. baie 4 déplacée, porte inversée…)", d.text,
                        { t -> upd { lay -> lay.copy(deviations = lay.deviations.map { if (it.id == d.id) it.copy(text = t) else it }) } },
                        Modifier.weight(1f), singleLine = false, minLines = 2,
                    )
                    IconButton(onClick = { upd { lay -> lay.copy(deviations = lay.deviations.filter { it.id != d.id }) } }) {
                        Icon(Icons.Outlined.Delete, "Supprimer")
                    }
                }
                PhotoStrip(
                    siteId = id, tag = "ecart", photos = d.photos,
                    onAdd = { p -> upd { lay -> lay.copy(deviations = lay.deviations.map { if (it.id == d.id) it.copy(photos = it.photos + p) else it }) } },
                    onRemove = { p -> upd { lay -> lay.copy(deviations = lay.deviations.map { if (it.id == d.id) it.copy(photos = it.photos - p) else it }) } },
                )
            }
        }
        OutlinedButton(onClick = { upd { it.copy(deviations = it.deviations + Deviation()) } }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Add, null)
            Text("  Ajouter un écart")
        }
    }
}
