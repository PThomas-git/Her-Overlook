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
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Navigation
import android.content.Context
import android.content.Intent
import android.net.Uri
import fr.heroverlook.data.latLon
import fr.heroverlook.data.LatLon
import fr.heroverlook.data.LocationResult
import fr.heroverlook.data.TourPlanner
import fr.heroverlook.data.requestLocation
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
import androidx.compose.runtime.LaunchedEffect
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

/** Lance la navigation vers le site (coordonnées, sinon adresse). */
private fun openDirections(ctx: Context, s: Site, onFail: () -> Unit) {
    val ll = s.latLon()
    val uri = if (ll != null) Uri.parse("google.navigation:q=${ll.lat},${ll.lon}")
    else Uri.parse("geo:0,0?q=" + Uri.encode("${s.address} ${s.postalCode} ${s.city}"))
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: Exception) {
        onFail()
    }
}

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

    // « Autour de moi » : les sites les plus proches de la position actuelle (évite de saisir sur le mauvais site)
    var nearby by remember { mutableStateOf<List<Pair<Site, Double>>?>(null) }
    var locating by remember { mutableStateOf(false) }
    val locError = remember { mutableStateOf<String?>(null) }
    fun findNearby() {
        locating = true
        requestLocation(ctx) { res ->
            locating = false
            when (res) {
                is LocationResult.Ok ->
                    nearby = TourPlanner.nearest(vm.sites.values.toList(), LatLon(res.lat, res.lon), 6)
                is LocationResult.Fail -> locError.value = res.reason
            }
        }
    }
    val locPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) findNearby()
        else locError.value = "Permission de localisation refusée : autorise « Position » pour Hér'Overlook dans les réglages de l'appli."
    }
    LocationErrorDialog(locError.value) { locError.value = null }
    nearby?.let { list ->
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { nearby = null },
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sites proches de toi", style = MaterialTheme.typography.titleLarge)
                if (list.isEmpty()) Text("Aucun site avec coordonnées.", style = MaterialTheme.typography.bodyMedium)
                list.forEach { (s, d) ->
                    Panel(onClick = { nearby = null; vm.go("site/${s.id}") }) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            KindBadge(s.kind)
                            Column(Modifier.weight(1f)) {
                                Text(s.name.ifBlank { s.id }, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                                Text(s.city, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            Text(TourPlanner.fmtDist(d), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                        }
                    }
                }
            }
        }
    }

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

    // La tournée existe dès qu'il y a des sites : l'accueil la reflète (ordre, prochain arrêt, direction).
    LaunchedEffect(vm.sites.size) { if (vm.sites.isNotEmpty() && vm.tourPlan.isEmpty()) vm.planTour() }
    val stopOf = HashMap<String, Triple<Int, Int, Int>>() // id -> (jour, rang dans le jour, nb d'arrêts du jour)
    vm.tourPlan.forEachIndexed { d, ids -> ids.forEachIndexed { i, id -> stopOf[id] = Triple(d, i, ids.size) } }
    // Ordre : tournée (jour puis ordre de passage) · hors tournée (POP puis ADR) · terminés en bas.
    fun group(s: Site): Int = when {
        s.status == VisitStatus.DONE -> 2
        stopOf.containsKey(s.id) -> 0
        else -> 1
    }
    val all = vm.sites.values.sortedWith(
        compareBy<Site>({ group(it) }, { stopOf[it.id]?.first ?: 0 }, { stopOf[it.id]?.second ?: 0 }, { it.kind.ordinal }, { it.id })
    )
    fun header(s: Site): String = when (group(s)) {
        0 -> "Jour ${(stopOf[s.id]?.first ?: 0) + 1}"
        1 -> "Hors tournée"
        else -> "Terminés"
    }
    // Prochain arrêt : premier site non terminé du premier jour qui en a encore.
    val nextStop: Site? = vm.tourPlan.firstNotNullOfOrNull { ids -> ids.firstNotNullOfOrNull { id -> vm.sites[id]?.takeIf { it.status != VisitStatus.DONE } } }
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
                    IconButton(
                        onClick = { locPermLauncher.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION)) },
                        enabled = !locating,
                    ) { Icon(Icons.Outlined.MyLocation, "Sites autour de moi") }
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
                state = vm.homeListState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { Overview(all) }
                if (nextStop != null) {
                    item {
                        val st = stopOf[nextStop.id]
                        val remaining = st?.let { (d, _, _) -> vm.tourPlan[d].count { id -> vm.sites[id]?.status?.let { it != VisitStatus.DONE } == true } } ?: 0
                        Panel(accent = MaterialTheme.colorScheme.secondary, onClick = { vm.go("site/${nextStop.id}") }) {
                            Text(
                                if (st != null) "Prochain arrêt · Jour ${st.first + 1} · $remaining restant(s)" else "Prochain arrêt",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                KindBadge(nextStop.kind)
                                Column(Modifier.weight(1f)) {
                                    Text(nextStop.name.ifBlank { nextStop.id }, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        listOf(nextStop.city, nextStop.address).filter { it.isNotBlank() }.joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { openDirections(ctx, nextStop) { vm.toast("Aucune application de navigation") } },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(Icons.Outlined.Navigation, null)
                                    Text("  Directions")
                                }
                                OutlinedButton(onClick = { vm.go("tour") }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Outlined.Map, null)
                                    Text("  Tournée")
                                }
                            }
                        }
                    }
                }
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
                    // Filtres repliés par défaut : le résumé dit ce qui est actif, sans prendre la place de la liste.
                    Collapsible(
                        title = "Filtres",
                        summary = buildList {
                            if (inSel(typeSel, "POP")) add("POP")
                            if (inSel(typeSel, "ADR")) add("ADR")
                            VisitStatus.values().forEach { if (inSel(statusSel, it.name)) add(it.label) }
                        }.joinToString(" · ").ifBlank { "Aucun filtre · ${all.size} sites" },
                    ) {
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
                shown.groupBy { header(it) }.forEach { (title, list) ->
                    item(key = "h_$title") {
                        Text(
                            "$title · ${list.size}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    items(list, key = { it.id }) { s ->
                        val st = stopOf[s.id]?.takeIf { s.status != VisitStatus.DONE }
                        SiteCard(
                            s, Modifier.animateItem(),
                            stop = st?.let { (_, i, n) -> "${i + 1}/$n" },
                            onDirections = { openDirections(ctx, s) { vm.toast("Aucune application de navigation") } },
                        ) { vm.go("site/${s.id}") }
                    }
                }
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
    val todo = all.size - done - doing
    val toValidate = all.sumOf { s -> s.known.count { it.state == KnownState.TODO } }
    Panel(accent = MaterialTheme.colorScheme.primary) {
        Text("${all.size} sites", style = MaterialTheme.typography.titleMedium)
        val target = if (all.isEmpty()) 0f else done.toFloat() / all.size
        val shown by animateFloatAsState(target, tween(800), label = "overview")
        LinearProgressIndicator(progress = { shown }, modifier = Modifier.fillMaxWidth())
        // Quatre compteurs de même largeur, une seule ligne chacun : aucun chevauchement possible.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Counter("$done", "Terminés", Verdict.OK.color(), Modifier.weight(1f))
            Counter("$doing", "En cours", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            Counter("$todo", "À faire", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            Counter("$toValidate", "À confirmer", MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Counter(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
private fun SiteCard(s: Site, modifier: Modifier = Modifier, stop: String? = null, onDirections: (() -> Unit)? = null, onClick: () -> Unit) {
    val st = s.stats()
    val progress by animateFloatAsState(st.progress, tween(700), label = "siteProgress")
    Panel(modifier = modifier, accent = statusColor(s.status), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KindBadge(s.kind)
            if (stop != null) Text("arrêt $stop", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary, maxLines = 1)
            Text(s.id, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (onDirections != null && s.status != VisitStatus.DONE) {
                IconButton(onClick = onDirections, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.Navigation, "Directions", tint = MaterialTheme.colorScheme.primary)
                }
            }
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
        // Une seule ligne d'aperçu : avancement à gauche, informations à confirmer à droite (pas de détail réserves / NC / photos ici).
        val toValidate = s.known.count { it.state == KnownState.TODO }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "${st.answered}/${st.total} points",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            if (toValidate > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(14.dp))
                    Text("$toValidate à confirmer", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary, maxLines = 1)
                }
            }
        }
    }
}
