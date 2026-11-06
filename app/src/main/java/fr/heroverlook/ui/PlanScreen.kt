@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package fr.heroverlook.ui

import android.content.Intent
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.RotateLeft
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import fr.heroverlook.AppVM
import fr.heroverlook.data.AppLog
import fr.heroverlook.data.Plan
import fr.heroverlook.data.PlanItem
import fr.heroverlook.data.PlanKind
import fr.heroverlook.data.PlanPoint
import fr.heroverlook.data.PlanRender
import fr.heroverlook.data.PlanTheme
import fr.heroverlook.data.SiteStore
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private fun isRackKind(k: PlanKind) =
    k == PlanKind.RACK || k == PlanKind.RACK_SQ || k == PlanKind.RACK_WIDE ||
        k == PlanKind.ENERGY || k == PlanKind.BATTERY || k == PlanKind.ODF

private fun kindForRack(kind: String, label: String): PlanKind = when (kind) {
    "Énergie" -> PlanKind.ENERGY
    "Batteries" -> PlanKind.BATTERY
    "Optique / ODF" -> PlanKind.ODF
    "TGBT" -> PlanKind.TGBT
    "Emplacement vide" -> PlanKind.RACK_SQ
    "Autre" -> if (label.contains("clim", ignoreCase = true)) PlanKind.CLIM_CAB else PlanKind.OTHER
    else -> PlanKind.RACK
}

private fun String.toMeters(): Double? = replace(',', '.').trim().toDoubleOrNull()

/**
 * Éditeur de plan d'agencement à l'échelle. Choix de conception : pas de dessin à main levée (pas de cotes, pas de
 * tailles réelles, impossible à redessiner proprement) ; un contour saisi en mètres + des éléments de taille réelle
 * déplacés avec accrochage sur grille. L'export (PNG + SVG + CSV) permet de refaire le plan sur PC.
 */
@Composable
fun PlanScreen(vm: AppVM, id: String) {
    val s = vm.sites[id] ?: return
    val ctx = LocalContext.current
    val d = LocalDensity.current.density

    var plan by remember(id) { mutableStateOf(s.layout.plan) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var scale by remember { mutableFloatStateOf(0f) } // pixels par cm ; 0 = pas encore cadré
    var offset by remember { mutableStateOf(Offset.Zero) } // position écran de l'origine (0, 0 cm)
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var showShape by remember { mutableStateOf(false) }
    val history = remember { mutableStateListOf<Plan>() }

    val cs = MaterialTheme.colorScheme
    val theme = remember(cs) {
        PlanTheme(
            bg = cs.background.toArgb(),
            floor = cs.surfaceContainerHigh.toArgb(),
            grid = (cs.onBackground.copy(alpha = 0.06f)).toArgb(),
            gridMajor = (cs.onBackground.copy(alpha = 0.16f)).toArgb(),
            wall = cs.onBackground.toArgb(),
            text = cs.onBackground.toArgb(),
            dim = cs.secondary.toArgb(),
            select = cs.primary.toArgb(),
        )
    }

    fun persist(p: Plan) {
        vm.update(id) { it.copy(layout = it.layout.copy(plan = p)) }
    }

    /** Applique une modification ; [record] enregistre l'état précédent pour « Annuler ». */
    fun commit(p: Plan, record: Boolean = true) {
        if (record && p != plan) {
            history.add(plan)
            if (history.size > 40) history.removeAt(0)
        }
        plan = p
        persist(p)
    }

    fun fit(p: Plan = plan) {
        val w = viewSize.width.toFloat()
        val h = viewSize.height.toFloat()
        if (w <= 0f || h <= 0f) return
        val b = PlanRender.bounds(p) ?: RectF(0f, 0f, 600f, 400f)
        val pad = 40f * d
        val sc = min((w - 2 * pad) / max(b.width(), 100f), (h - 2 * pad) / max(b.height(), 100f)).coerceIn(0.05f, 6f)
        scale = sc
        offset = Offset(w / 2f - (b.left + b.width() / 2f) * sc, h / 2f - (b.top + b.height() / 2f) * sc)
    }

    LaunchedEffect(viewSize) { if (scale == 0f) fit() }

    fun zoomView(zc: Float, centroid: Offset, pan: Offset) {
        val s0 = scale
        if (s0 <= 0f || !zc.isFinite() || !centroid.isSpecified) return
        val s1 = (s0 * zc).coerceIn(0.03f, 8f)
        offset = centroid - (centroid - offset) * (s1 / s0) + pan
        scale = s1
    }

    fun snapTo(v: Float): Int {
        val sn = plan.snapCm.coerceAtLeast(1)
        return (v / sn).roundToInt() * sn
    }

    fun addItem(kind: PlanKind) {
        val sc = scale
        if (sc <= 0f) return
        val cx = (viewSize.width / 2f - offset.x) / sc
        val cy = (viewSize.height / 2f - offset.y) / sc
        val label = if (isRackKind(kind)) (plan.items.count { isRackKind(it.kind) } + 1).toString() else ""
        val item = PlanItem(kind = kind, label = label, x = snapTo(cx), y = snapTo(cy), w = kind.w, h = kind.h)
        commit(plan.copy(items = plan.items + item))
        selectedId = item.id
    }

    fun importRacks() {
        val linked = plan.items.mapNotNull { it.rackId }.toSet()
        val todo = s.racks.filter { it.id !in linked }
        if (todo.isEmpty()) {
            vm.toast("Toutes les baies de la fiche sont déjà sur le plan.")
            return
        }
        val b = PlanRender.bounds(plan)
        val maxX = (b?.right?.toInt() ?: 600).coerceAtLeast(300)
        var x = 40
        var y = 40
        val added = todo.map { r ->
            val kind = kindForRack(r.kind, r.label)
            val item = PlanItem(
                kind = kind, label = r.label, x = x + kind.w / 2, y = y + kind.h / 2,
                w = kind.w, h = kind.h, rackId = r.id, note = r.description.take(80),
            )
            x += kind.w + 10
            if (x > maxX) { x = 40; y += 100 }
            item
        }
        commit(plan.copy(items = plan.items + added))
        vm.toast("${added.size} baie(s) de la fiche ajoutée(s) : place-les comme dans le local.")
    }

    fun share() {
        val snapshot = plan
        val name = SiteStore.safeName(id)
        Thread {
            try {
                val dir = File(ctx.filesDir, "plans").apply { mkdirs() }
                val f = File(dir, "plan_$name.png")
                FileOutputStream(f).use { PlanRender.bitmap(snapshot).compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                Handler(Looper.getMainLooper()).post {
                    try {
                        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        ctx.startActivity(Intent.createChooser(intent, "Partager le plan"))
                    } catch (e: Exception) {
                        AppLog.e("plan", "Partage du plan impossible", e)
                        vm.toast("Partage impossible : ${e.message}")
                    }
                }
            } catch (e: Exception) {
                AppLog.e("plan", "Export PNG du plan impossible", e)
                vm.toast("Export impossible : ${e.message}")
            }
        }.start()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Plan du local", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge)
                        Text(s.name.ifBlank { s.id }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = vm::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                actions = {
                    IconButton(
                        onClick = {
                            if (history.isNotEmpty()) {
                                val prev = history.removeAt(history.lastIndex)
                                plan = prev
                                persist(prev)
                                if (selectedId != null && prev.items.none { it.id == selectedId }) selectedId = null
                            }
                        },
                        enabled = history.isNotEmpty(),
                    ) { Icon(Icons.Outlined.Undo, "Annuler") }
                    IconButton(onClick = { share() }, enabled = !plan.isBlank) { Icon(Icons.Outlined.Share, "Partager l'image du plan") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // ---------------- zone de dessin ----------------
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
                    .background(MaterialTheme.colorScheme.background)
                    .onSizeChanged { viewSize = it }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val sc0 = scale
                            if (sc0 <= 0f) return@awaitEachGesture
                            val before = plan
                            val xCm = (down.position.x - offset.x) / sc0
                            val yCm = (down.position.y - offset.y) / sc0
                            val hit = PlanRender.hit(plan, xCm, yCm, (10f * d) / sc0)
                            if (hit != null) selectedId = hit.id
                            var dragId: String? = hit?.id
                            var raw = if (hit != null) Offset(hit.x.toFloat(), hit.y.toFloat()) else Offset.Zero
                            var pastSlop = false
                            var acc = Offset.Zero
                            var changed = false
                            do {
                                val ev = awaitPointerEvent()
                                val pressed = ev.changes.count { it.pressed }
                                if (pressed >= 2) {
                                    // deux doigts : zoom / déplacement de la vue
                                    val zc = ev.calculateZoom()
                                    val pc = ev.calculatePan()
                                    val ctr = ev.calculateCentroid(useCurrent = false)
                                    if (zc.isFinite() && ctr.isSpecified) zoomView(zc, ctr, pc)
                                    dragId = null
                                    pastSlop = true
                                    ev.changes.forEach { it.consume() }
                                } else if (pressed == 1) {
                                    val ch = ev.changes.firstOrNull { it.pressed }
                                    if (ch != null) {
                                        val delta = ch.position - ch.previousPosition
                                        acc += delta
                                        if (!pastSlop && acc.getDistance() > viewConfiguration.touchSlop) pastSlop = true
                                        if (pastSlop && delta != Offset.Zero) {
                                            val did = dragId
                                            if (did != null) {
                                                raw += delta / scale
                                                val nx = snapTo(raw.x)
                                                val ny = snapTo(raw.y)
                                                plan = plan.copy(items = plan.items.map { if (it.id == did) it.copy(x = nx, y = ny) else it })
                                                changed = true
                                            } else {
                                                offset += delta
                                            }
                                            ch.consume()
                                        }
                                    }
                                }
                            } while (ev.changes.any { it.pressed })
                            if (changed) {
                                history.add(before)
                                if (history.size > 40) history.removeAt(0)
                                persist(plan)
                            }
                            if (!pastSlop && hit == null) selectedId = null
                        }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val sc = scale
                    if (sc > 0f) {
                        drawIntoCanvas { cv ->
                            PlanRender.draw(cv.nativeCanvas, plan, sc, offset.x, offset.y, size.width, size.height, selectedId, theme, true, d)
                        }
                    }
                }
                if (plan.isBlank) {
                    Column(
                        Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Plan vide", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Commence par « Forme du local » (dimensions en mètres), puis ajoute les baies et équipements à leur taille réelle.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ---------------- panneau du bas ----------------
            val sel = plan.items.firstOrNull { it.id == selectedId }
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 330.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (sel != null) {
                    fun upd(record: Boolean, f: (PlanItem) -> PlanItem) =
                        commit(plan.copy(items = plan.items.map { if (it.id == sel.id) f(it) else it }), record)
                    val snap = plan.snapCm.coerceAtLeast(1)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(sel.kind.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text("x ${sel.x} · y ${sel.y} cm", style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        IconButton(onClick = { selectedId = null }) { Icon(Icons.Outlined.Close, "Désélectionner") }
                    }
                    Field("Libellé (n° de baie, nom…)", sel.label, { t -> upd(false) { it.copy(label = t) } })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field(
                            "Largeur (cm)", sel.w.toString(),
                            { t -> t.toIntOrNull()?.let { v -> upd(false) { it.copy(w = v.coerceIn(5, 2000)) } } },
                            Modifier.weight(1f), keyboard = KeyboardType.Number,
                        )
                        Field(
                            "Profondeur (cm)", sel.h.toString(),
                            { t -> t.toIntOrNull()?.let { v -> upd(false) { it.copy(h = v.coerceIn(2, 2000)) } } },
                            Modifier.weight(1f), keyboard = KeyboardType.Number,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(0.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { upd(true) { it.copy(rot = (it.rot + 270) % 360) } }) { Icon(Icons.Outlined.RotateLeft, "Tourner à gauche") }
                        IconButton(onClick = { upd(true) { it.copy(rot = (it.rot + 90) % 360) } }) { Icon(Icons.Outlined.RotateRight, "Tourner à droite") }
                        if (sel.kind == PlanKind.DOOR) {
                            IconButton(onClick = { upd(true) { it.copy(flip = !it.flip) } }) { Icon(Icons.Outlined.SwapVert, "Inverser le côté d'ouverture") }
                        }
                        IconButton(onClick = { upd(true) { it.copy(x = it.x - snap) } }) { Icon(Icons.Outlined.ArrowBack, "Décaler à gauche") }
                        IconButton(onClick = { upd(true) { it.copy(y = it.y - snap) } }) { Icon(Icons.Outlined.ArrowUpward, "Décaler vers le haut") }
                        IconButton(onClick = { upd(true) { it.copy(y = it.y + snap) } }) { Icon(Icons.Outlined.ArrowDownward, "Décaler vers le bas") }
                        IconButton(onClick = { upd(true) { it.copy(x = it.x + snap) } }) { Icon(Icons.Outlined.ArrowForward, "Décaler à droite") }
                        IconButton(onClick = {
                            val copy = sel.copy(id = fr.heroverlook.data.newId(), x = sel.x + 20, y = sel.y + 20, rackId = null)
                            commit(plan.copy(items = plan.items + copy))
                            selectedId = copy.id
                        }) { Icon(Icons.Outlined.ContentCopy, "Dupliquer") }
                        IconButton(onClick = {
                            commit(plan.copy(items = plan.items.filter { it.id != sel.id }))
                            selectedId = null
                        }) { Icon(Icons.Outlined.Delete, "Supprimer") }
                    }
                    if (s.racks.isNotEmpty() && isRackKind(sel.kind)) {
                        Text("Lier à une fiche baie", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            s.racks.forEach { r ->
                                FilterChip(
                                    selected = sel.rackId == r.id,
                                    onClick = {
                                        if (sel.rackId == r.id) upd(true) { it.copy(rackId = null) }
                                        else upd(true) { it.copy(rackId = r.id, label = r.label.ifBlank { it.label }) }
                                    },
                                    label = { Text("${r.label.ifBlank { "?" }} · ${r.kind}") },
                                )
                            }
                        }
                    }
                    Field("Note (matériel, état, particularité)", sel.note, { t -> upd(false) { it.copy(note = t) } }, singleLine = false, minLines = 2)
                } else {
                    Text("Ajouter un élément (taille réelle, modifiable)", style = MaterialTheme.typography.labelLarge)
                    PlanKind.values().toList().groupBy { it.group }.forEach { (group, kinds) ->
                        Text(group, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            kinds.forEach { k -> AssistChip(onClick = { addItem(k) }, label = { Text(k.label) }) }
                        }
                    }
                }

                // barre d'outils commune
                Text("Outils", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = { showShape = true }) { Text("Forme du local") }
                    if (s.racks.isNotEmpty()) OutlinedButton(onClick = { importRacks() }) { Text("Importer les baies de la fiche") }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Accroche", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    listOf(1, 5, 10, 25).forEach { v ->
                        FilterChip(
                            selected = plan.snapCm == v,
                            onClick = { commit(plan.copy(snapCm = v), record = false) },
                            label = { Text("$v cm") },
                        )
                    }
                    Box(Modifier.weight(1f))
                    IconButton(onClick = { zoomView(1f / 1.4f, Offset(viewSize.width / 2f, viewSize.height / 2f), Offset.Zero) }) { Icon(Icons.Outlined.ZoomOut, "Dézoomer") }
                    IconButton(onClick = { zoomView(1.4f, Offset(viewSize.width / 2f, viewSize.height / 2f), Offset.Zero) }) { Icon(Icons.Outlined.ZoomIn, "Zoomer") }
                    IconButton(onClick = { fit() }) { Icon(Icons.Outlined.CenterFocusStrong, "Cadrer le plan") }
                }
            }
        }
    }

    if (showShape) {
        ShapeDialog(
            current = plan.outline,
            onDismiss = { showShape = false },
            onApply = { pts ->
                showShape = false
                val next = plan.copy(outline = pts)
                commit(next)
                fit(next)
            },
            onError = { vm.toast(it) },
        )
    }
}

/** Forme du local : rectangle, forme en L ou contour libre (sommets en cm). */
@Composable
private fun ShapeDialog(
    current: List<PlanPoint>,
    onDismiss: () -> Unit,
    onApply: (List<PlanPoint>) -> Unit,
    onError: (String) -> Unit,
) {
    var mode by remember { mutableStateOf("Rectangle") }
    var len by remember { mutableStateOf("5.00") }
    var wid by remember { mutableStateOf("3.00") }
    var notchW by remember { mutableStateOf("1.50") }
    var notchH by remember { mutableStateOf("1.00") }
    var notchRight by remember { mutableStateOf(true) }
    var notchTop by remember { mutableStateOf(true) }
    var free by remember {
        mutableStateOf(
            if (current.isEmpty()) "0,0 ; 500,0 ; 500,300 ; 0,300" else current.joinToString(" ; ") { "${it.x},${it.y}" }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Forme du local") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChips(listOf("Rectangle", "Forme en L", "Contour libre"), mode) { mode = it.ifBlank { mode } }
                when (mode) {
                    "Rectangle", "Forme en L" -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Field("Longueur (m)", len, { len = it }, Modifier.weight(1f), keyboard = KeyboardType.Decimal)
                            Field("Largeur (m)", wid, { wid = it }, Modifier.weight(1f), keyboard = KeyboardType.Decimal)
                        }
                        if (mode == "Forme en L") {
                            Text("Entaille (partie manquante dans un angle)", style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Field("Entaille long. (m)", notchW, { notchW = it }, Modifier.weight(1f), keyboard = KeyboardType.Decimal)
                                Field("Entaille larg. (m)", notchH, { notchH = it }, Modifier.weight(1f), keyboard = KeyboardType.Decimal)
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = notchTop, onClick = { notchTop = true }, label = { Text("En haut") })
                                FilterChip(selected = !notchTop, onClick = { notchTop = false }, label = { Text("En bas") })
                                FilterChip(selected = notchRight, onClick = { notchRight = true }, label = { Text("À droite") })
                                FilterChip(selected = !notchRight, onClick = { notchRight = false }, label = { Text("À gauche") })
                            }
                        }
                    }
                    else -> {
                        Text(
                            "Sommets dans l'ordre autour du local, en cm : « x,y ; x,y ; … ». Le plan est orienté comme à l'écran (y vers le bas).",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Field("Sommets (cm)", free, { free = it }, singleLine = false, minLines = 3)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val pts: List<PlanPoint>? = when (mode) {
                    "Rectangle" -> {
                        val l = len.toMeters()
                        val w = wid.toMeters()
                        if (l == null || w == null || l <= 0.5 || w <= 0.5) null
                        else {
                            val L = (l * 100).roundToInt()
                            val W = (w * 100).roundToInt()
                            listOf(PlanPoint(0, 0), PlanPoint(L, 0), PlanPoint(L, W), PlanPoint(0, W))
                        }
                    }
                    "Forme en L" -> {
                        val l = len.toMeters()
                        val w = wid.toMeters()
                        val a = notchW.toMeters()
                        val b = notchH.toMeters()
                        if (l == null || w == null || a == null || b == null || a <= 0 || b <= 0 || a >= l || b >= w) null
                        else {
                            val L = (l * 100).roundToInt()
                            val W = (w * 100).roundToInt()
                            val A = (a * 100).roundToInt()
                            val B = (b * 100).roundToInt()
                            // entaille en haut à droite, puis symétries demandées
                            val base = listOf(PlanPoint(0, 0), PlanPoint(L - A, 0), PlanPoint(L - A, B), PlanPoint(L, B), PlanPoint(L, W), PlanPoint(0, W))
                            base.map { p ->
                                PlanPoint(if (notchRight) p.x else L - p.x, if (notchTop) p.y else W - p.y)
                            }
                        }
                    }
                    else -> {
                        val parsed = free.split(';', '\n').mapNotNull { part ->
                            val m = Regex("(-?\\d+)\\s*[,\\s]\\s*(-?\\d+)").find(part)
                            if (m != null) PlanPoint(m.groupValues[1].toInt(), m.groupValues[2].toInt()) else null
                        }
                        if (parsed.size >= 3) parsed else null
                    }
                }
                if (pts == null) onError("Dimensions invalides : vérifie les valeurs (3 sommets minimum pour un contour libre).")
                else onApply(pts)
            }) { Text("Appliquer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
