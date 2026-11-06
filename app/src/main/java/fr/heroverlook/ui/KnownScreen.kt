@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package fr.heroverlook.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.heroverlook.AppVM
import fr.heroverlook.data.Known
import fr.heroverlook.data.KnownState
import fr.heroverlook.data.Verdict

/**
 * Informations déjà connues (rapports de maintenance 2026, fiches site…) à valider ou corriger sur place.
 * Seuls les éléments constatables à vue sont préchargés (pièces, modèles, dates de mise en service…).
 */
@Composable
fun KnownScreen(vm: AppVM, id: String) {
    val s = vm.sites[id] ?: return
    val todo = s.known.count { it.state == KnownState.TODO }
    Screen("Déjà connu", s.name.ifBlank { s.id }, vm::back) {
        Text(
            "Données issues des rapports de maintenance 2026 et des fiches site. " +
                "Pour chacune : « Confirmé » si c'est exact, « À corriger » puis note ce que tu constates.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (s.known.isEmpty()) {
            Panel {
                Text("Rien de préchargé pour ce site.", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Importe le fichier d'inventaire enrichi (menu ⋮ > Importer l'inventaire) pour charger les données connues.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            Text("$todo à valider sur ${s.known.size}", style = MaterialTheme.typography.labelLarge)
            s.known.forEach { k -> KnownCard(k) { f -> vm.setKnown(id, k.id, f) } }
        }
    }
}

/** Bouton à bascule : plein et coloré quand il est sélectionné, simple contour sinon (pas de coche en plus). */
@Composable
private fun StateButton(
    selected: Boolean,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    if (selected) {
        androidx.compose.material3.Button(
            onClick = onClick,
            modifier = modifier,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = color, contentColor = onVerdictColor()),
            content = content,
        )
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, content = content)
    }
}

@Composable
private fun KnownCard(k: Known, update: ((Known) -> Known) -> Unit) {
    val accent = when (k.state) {
        KnownState.OK -> Verdict.OK.color()
        KnownState.FIX -> Verdict.RESERVE.color()
        KnownState.TODO -> null
    }
    Panel(accent = accent) {
        Text(k.label, style = MaterialTheme.typography.titleSmall)
        Text(k.value, style = MaterialTheme.typography.bodyMedium)
        if (k.source.isNotBlank()) {
            Text(k.source, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StateButton(
                selected = k.state == KnownState.OK, color = Verdict.OK.color(),
                onClick = { update { it.copy(state = if (it.state == KnownState.OK) KnownState.TODO else KnownState.OK) } },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.Check, null)
                Text("  Confirmé")
            }
            StateButton(
                selected = k.state == KnownState.FIX, color = Verdict.RESERVE.color(),
                onClick = { update { it.copy(state = if (it.state == KnownState.FIX) KnownState.TODO else KnownState.FIX) } },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.Edit, null)
                Text("  À corriger")
            }
        }
        if (k.state == KnownState.FIX) {
            Field(
                "Ce que tu constates", k.fix, { t -> update { it.copy(fix = t) } },
                singleLine = false, minLines = 2,
            )
        }
    }
}
