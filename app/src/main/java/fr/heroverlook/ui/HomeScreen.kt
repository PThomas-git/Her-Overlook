@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package fr.heroverlook.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Timelapse
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.heroverlook.AppVM
import fr.heroverlook.data.KnownState
import fr.heroverlook.data.Site
import fr.heroverlook.data.SiteKind
import fr.heroverlook.data.Verdict
import fr.heroverlook.data.VisitStatus
import fr.heroverlook.data.allPhotos
import fr.heroverlook.data.stats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Bascule une valeur dans une sélection stockée sous forme de texte « a,b,c » (sauvegardable). */
private fun toggleSel(cur: String, key: String): String {
    val s = cur.split(",").filter { it.isNotBlank() }.toMutableSet()
    if (!s.add(key)) s.remove(key)
    return s.joinToString(",")
}

private fun inSel(cur: String, key: String) = cur.split(",").contains(key)

@Composable
fun statusColor(s: VisitStatus): Color = when (s) {
    VisitStatus.TODO -> MaterialTheme.colorScheme.outline
    VisitStatus.IN_PROGRESS -> MaterialTheme.colorScheme.secondary
    VisitStatus.DONE -> Verdict.OK.color()
}

@Composable
fun HomeScreen(vm: AppVM) {
    val ctx = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    // Filtres combinables, sans exclusivité : plusieurs puces actives par groupe (OU) ; les deux groupes se cumulent (ET).
    // Aucune puce active dans un groupe = pas de restriction sur ce groupe.
    var typeSel by rememberSaveable { mutableStateOf("") }
    var statusSel by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.export(uri)
    }
    val logLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) vm.saveLog(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching {
                ctx.contentResolver.openInputStream(uri)!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
            }.getOrNull()
            if (text != null) vm.importInventory(text.removePrefix("﻿")) else vm.toast("Impossible de lire le fichier")
        }
    }

    val all = vm.sites.values.sortedWith(compareBy({ it.kind.ordinal }, { it.id }))
    val shown = all.filter { s ->
        val typeOk = typeSel.isBlank() ||
            (s.kind == SiteKind.POP && inSel(typeSel, "POP")) ||
            (s.kind != SiteKind.POP && inSel(typeSel, "ADR"))
        val statusOk = statusSel.isBlank() || inSel(statusSel, s.status.name)
        val q = query.trim()
        typeOk && statusOk && (q.isEmpty() || listOf(s.id, s.name, s.city, s.address).any { it.contains(q, ignoreCase = true) })
    }

    if (confirmDeleteAll) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Supprimer tous les sites ?") },
            text = {
                Text(
                    "Les ${all.size} site(s), leurs relevés et leurs photos seront effacés de ce téléphone, sans retour possible. " +
                        "Pense à exporter (icône ↑) avant. Tu pourras ensuite réimporter l'inventaire depuis le menu ⋮."
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDeleteAll = false; vm.deleteAllSites() }) { Text("Tout supprimer") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmDeleteAll = false }) { Text("Annuler") } },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Hér'Overlook", style = MaterialTheme.typography.titleLarge)
                        Text("Relevé POP / ADR · Hérault Télécom", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.go("tour") }) { Icon(Icons.Outlined.Map, "Tournée") }
                    IconButton(onClick = {
                        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
                        exportLauncher.launch("HerOverlook_$stamp.zip")
                    }) { Icon(Icons.Outlined.FileUpload, "Exporter") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Menu") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Importer l'inventaire (JSON)") }, onClick = {
                                menu = false
                                importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"))
                            })
                            DropdownMenuItem(text = { Text("Partager le log") }, onClick = { menu = false; vm.shareLog(ctx) })
                            DropdownMenuItem(text = { Text("Enregistrer le log (fichier)") }, onClick = {
                                menu = false
                                logLauncher.launch("HerOverlook_log_${SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())}.txt")
                            })
                            DropdownMenuItem(text = { Text("Ajouter un site de démo") }, onClick = { menu = false; vm.loadDemo() })
                            DropdownMenuItem(text = { Text("Retirer le site de démo") }, onClick = { menu = false; vm.deleteDemo() })
                            DropdownMenuItem(
                                text = { Text("Supprimer tous les sites…") },
                                enabled = all.isNotEmpty(),
                                onClick = { menu = false; confirmDeleteAll = true },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { Overview(all) }
                item {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Rechercher un site, une ville…") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        keyboardOptions = KeyboardOptions.Default,
                    )
                }
                item {
                    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Type", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp, end = 4.dp))
                            FilterChip(
                                selected = inSel(typeSel, "POP"), onClick = { typeSel = toggleSel(typeSel, "POP") },
                                label = { Text("POP (${all.count { it.kind == SiteKind.POP }})") },
                            )
                            FilterChip(
                                selected = inSel(typeSel, "ADR"), onClick = { typeSel = toggleSel(typeSel, "ADR") },
                                label = { Text("ADR (${all.count { it.kind != SiteKind.POP }})") },
                            )
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Statut", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp, end = 4.dp))
                            VisitStatus.values().forEach { st ->
                                FilterChip(
                                    selected = inSel(statusSel, st.name), onClick = { statusSel = toggleSel(statusSel, st.name) },
                                    label = { Text("${st.label} (${all.count { it.status == st }})") },
                                )
                            }
                        }
                        if (typeSel.isNotBlank() || statusSel.isNotBlank()) {
                            androidx.compose.material3.TextButton(onClick = { typeSel = ""; statusSel = "" }) {
                                Text("Réinitialiser les filtres (${shown.size}/${all.size} affichés)")
                            }
                        }
                    }
                }
                if (all.isEmpty()) {
                    item { EmptyState(vm, onImport = { importLauncher.launch(arrayOf("*/*")) }) }
                }
                items(shown, key = { it.id }) { s -> SiteCard(s, Modifier.animateItem()) { vm.go("site/${s.id}") } }
            }
            vm.busy?.let { msg ->
                Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text(msg, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun Overview(all: List<Site>) {
    val done = all.count { it.status == VisitStatus.DONE }
    val doing = all.count { it.status == VisitStatus.IN_PROGRESS }
    val nc = all.sumOf { it.stats().nc }
    val res = all.sumOf { it.stats().reserve }
    val photos = all.sumOf { it.allPhotos().size }
    val toValidate = all.sumOf { s -> s.known.count { it.state == KnownState.TODO } }
    Panel(accent = MaterialTheme.colorScheme.primary) {
        Text("Tournée", style = MaterialTheme.typography.titleMedium)
        val target = if (all.isEmpty()) 0f else done.toFloat() / all.size
        val shown by animateFloatAsState(target, tween(800), label = "overview")
        LinearProgressIndicator(progress = { shown }, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Counter("${done}/${all.size}", "terminés")
            Counter("$doing", "en cours")
            if (toValidate > 0) Counter("$toValidate", "à confirmer", MaterialTheme.colorScheme.secondary)
            Counter("$res", "réserves", Verdict.RESERVE.color())
            Counter("$nc", "non conf.", Verdict.NC.color())
            Counter("$photos", "photos")
        }
    }
}

@Composable
private fun Counter(value: String, label: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyState(vm: AppVM, onImport: () -> Unit) {
    Panel(accent = MaterialTheme.colorScheme.secondary) {
        Text("Aucun site pour l'instant", style = MaterialTheme.typography.titleMedium)
        Text(
            "Importe le fichier d'inventaire (sites_inventaire.json, copié dans le téléphone) pour créer les 7 POP et 17 ADR. " +
                "Tu peux aussi charger un site de démonstration pour essayer l'application.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text("Importer l'inventaire") }
        OutlinedButton(onClick = { vm.loadDemo() }, modifier = Modifier.fillMaxWidth()) { Text("Site de démo") }
    }
}

@Composable
private fun SiteCard(s: Site, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val st = s.stats()
    val progress by animateFloatAsState(st.progress, tween(700), label = "siteProgress")
    Panel(modifier = modifier, accent = statusColor(s.status), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KindBadge(s.kind)
            Text(s.id, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(
                when (s.status) {
                    VisitStatus.TODO -> Icons.Outlined.RadioButtonUnchecked
                    VisitStatus.IN_PROGRESS -> Icons.Outlined.Timelapse
                    VisitStatus.DONE -> Icons.Outlined.CheckCircle
                },
                contentDescription = s.status.label,
                tint = statusColor(s.status),
                modifier = Modifier.size(22.dp),
            )
        }
        Text(s.name.ifBlank { s.id }, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            listOf(s.city, s.address).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        val toValidate = s.known.count { it.state == KnownState.TODO }
        if (toValidate > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                Text(
                    "$toValidate info(s) à confirmer",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("${st.answered}/${st.total} points", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (st.reserve > 0) Text("${st.reserve} réserve(s)", style = MaterialTheme.typography.labelMedium, color = Verdict.RESERVE.color())
            if (st.nc > 0) Text("${st.nc} NC", style = MaterialTheme.typography.labelMedium, color = Verdict.NC.color())
        }
    }
}
