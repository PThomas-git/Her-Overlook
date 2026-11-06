@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package fr.heroverlook.ui

import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fr.heroverlook.data.AppLog
import fr.heroverlook.data.ItemKind
import fr.heroverlook.data.Photos
import fr.heroverlook.data.SiteKind
import fr.heroverlook.data.Verdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread

// ---------- Écran ----------

/** Déploiement d'un bloc (commentaire, photos…) : un seul tempo pour le contenu ET le cadre qui le contient. */
val ExpandEnter: EnterTransition =
    fadeIn(tween(220)) + expandVertically(tween(260, easing = FastOutSlowInEasing))
val ExpandExit: ExitTransition =
    fadeOut(tween(140)) + shrinkVertically(tween(220, easing = FastOutSlowInEasing))

/** Route de l'écran affiché et mémoire des positions de défilement par route (fournis par AppRoot). */
val LocalRoute = staticCompositionLocalOf<String?> { null }
val LocalScrolls = staticCompositionLocalOf<MutableMap<String, ScrollState>?> { null }

@Composable
fun Screen(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    // Au retour sur un écran, on retrouve sa position de défilement (l'état est gardé tant que la route est dans la pile).
    val route = LocalRoute.current
    val scrolls = LocalScrolls.current
    val fallback = rememberScrollState()
    val scroll = if (route != null && scrolls != null) scrolls.getOrPut(route) { ScrollState(0) } else fallback
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge)
                        if (subtitle != null) {
                            Text(
                                subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(scroll)
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/**
 * Carte avec liseré gauche (rappel des montants de baie 19").
 * Le liseré est dessiné avec drawBehind : surtout pas de IntrinsicSize ici, car les LazyRow
 * (bandes de photos) ne supportent pas les mesures intrinsèques et feraient planter l'écran.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    /**
     * Anime les changements de hauteur du contenu. À désactiver quand le contenu gère lui-même son déploiement
     * (AnimatedVisibility) : sinon le cadre et son contenu s'animent séparément et le cadre traîne.
     */
    animateSize: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        if (pressed) 0.97f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "panelPress",
    )
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val enter by animateFloatAsState(if (shown) 1f else 0f, tween(380, easing = FastOutSlowInEasing), label = "panelEnter")
    val bar by animateColorAsState(accent ?: Color.Transparent, tween(300), label = "panelAccent")

    val look = modifier
        .fillMaxWidth()
        .graphicsLayer {
            alpha = enter
            translationY = (1f - enter) * 14.dp.toPx()
            scaleX = pressScale
            scaleY = pressScale
        }
    val inner: @Composable () -> Unit = {
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (animateSize) Modifier.animateContentSize(tween(260, easing = FastOutSlowInEasing)) else Modifier)
                .drawBehind { if (bar.alpha > 0f) drawRect(bar, size = Size(4.dp.toPx(), size.height)) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = look,
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            interactionSource = interaction,
        ) { inner() }
    } else {
        Surface(
            modifier = look,
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) { inner() }
    }
}

/** Fenêtre d'erreur de position : dit précisément quoi régler et ouvre directement les bons réglages. */
@Composable
fun LocationErrorDialog(message: String?, onDismiss: () -> Unit) {
    if (message == null) return
    val ctx = LocalContext.current
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Position indisponible") },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = {
                runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                onDismiss()
            }) { Text("Réglages de localisation") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    runCatching {
                        ctx.startActivity(
                            android.content.Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.parse("package:${ctx.packageName}"),
                            )
                        )
                    }
                    onDismiss()
                }) { Text("Réglages de l'appli") }
                TextButton(onClick = onDismiss) { Text("Fermer") }
            }
        },
    )
}

/** États dépliés / repliés des blocs repliables, gardés tant que l'appli tourne (fournis par AppRoot). */
val LocalUiFlags = staticCompositionLocalOf<androidx.compose.runtime.snapshots.SnapshotStateMap<String, Boolean>?> { null }

/**
 * Bloc repliable : seul l'en-tête (titre + résumé d'une ligne) est visible tant qu'il est replié.
 * Sert à ranger les détails secondaires pour ne montrer que l'essentiel à chaque niveau.
 */
@Composable
fun Collapsible(
    title: String,
    summary: String? = null,
    startOpen: Boolean = false,
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val flags = LocalUiFlags.current
    val key = "${LocalRoute.current}|$title"
    var local by remember { mutableStateOf(startOpen) }
    val open = if (flags != null) (flags[key] ?: startOpen) else local
    Panel(accent = accent, animateSize = false) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { if (flags != null) flags[key] = !open else local = !open },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (summary != null) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                if (open) androidx.compose.material.icons.Icons.Outlined.ExpandLess else androidx.compose.material.icons.Icons.Outlined.ExpandMore,
                if (open) "Replier" else "Déplier",
            )
        }
        androidx.compose.animation.AnimatedVisibility(visible = open, enter = ExpandEnter, exit = ExpandExit) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(top = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
fun KindBadge(kind: SiteKind) {
    val c = if (kind.isPop) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    Text(
        kind.label,
        style = MaterialTheme.typography.labelSmall,
        color = c,
        modifier = Modifier
            .border(1.dp, c, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

// ---------- Saisie ----------

@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    singleLine: Boolean = true,
    minLines: Int = 1,
    mono: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    var text by remember { mutableStateOf(value) }
    LaunchedEffect(value) { if (value != text) text = value }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onChange(it) },
        label = { Text(label) },
        modifier = modifier,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else minLines,
        textStyle = if (mono) LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace) else LocalTextStyle.current,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboard,
            capitalization = if (mono) KeyboardCapitalization.Characters else KeyboardCapitalization.Sentences,
        ),
    )
}

@Composable
fun VerdictBar(value: Verdict, kind: ItemKind, onChange: (Verdict) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(Verdict.OK, Verdict.RESERVE, Verdict.NC, Verdict.NA).forEach { v ->
            VerdictButton(
                v, kind, selected = value == v,
                onClick = { onChange(if (value == v) Verdict.UNSET else v) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun VerdictButton(v: Verdict, kind: ItemKind, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val c = v.color()
    val colorSpec = tween<Color>(240)
    val bg by animateColorAsState(if (selected) c else c.copy(alpha = 0f), colorSpec, label = "verdictBg")
    val border by animateColorAsState(if (selected) c else MaterialTheme.colorScheme.outline, colorSpec, label = "verdictBorder")
    val fg by animateColorAsState(
        if (selected) onVerdictColor() else MaterialTheme.colorScheme.onSurfaceVariant, colorSpec, label = "verdictFg",
    )
    val scale by animateFloatAsState(
        if (selected) 1.05f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "verdictScale",
    )
    Surface(
        onClick = onClick,
        modifier = modifier.height(54.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(12.dp),
        color = bg,
        border = BorderStroke(1.5.dp, border),
        contentColor = fg,
    ) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(v.icon(), contentDescription = null, modifier = Modifier.size(20.dp))
            Text(v.shortLabel(kind), style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

/** Sélecteur à choix unique (puces). */
@Composable
fun ChoiceChips(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { o ->
            FilterChip(selected = selected == o, onClick = { onSelect(if (selected == o) "" else o) }, label = { Text(o) })
        }
    }
}

/** Sélecteur à choix multiples (puces). */
@Composable
fun MultiChips(options: List<String>, selected: List<String>, onChange: (List<String>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { o ->
            val on = o in selected
            FilterChip(
                selected = on,
                onClick = { onChange(if (on) selected - o else selected + o) },
                label = { Text(o) },
            )
        }
    }
}

@Composable
fun InfoLine(label: String, value: String) {
    if (value.isBlank()) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(96.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
fun Divider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

// ---------- Photos ----------

@Composable
fun rememberPhotoTaker(siteId: String, tag: String, onTaken: (String) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val currentOnTaken by rememberUpdatedState(onTaken)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val rel = pending
        pending = null
        if (rel != null) {
            val f = Photos.abs(ctx, rel)
            if (ok && f.exists() && f.length() > 0) {
                AppLog.i("photo", "Prise de vue OK : ${f.name}")
                thread {
                    Photos.compress(f)
                    Handler(Looper.getMainLooper()).post { currentOnTaken(rel) }
                }
            } else {
                AppLog.w("photo", "Prise de vue annulée ou vide : ${f.name} (ok=$ok)")
                f.delete()
            }
        }
    }
    return {
        try {
            val f = Photos.newFile(ctx, siteId, tag)
            pending = Photos.rel(ctx, f)
            AppLog.i("photo", "Ouverture de l'appareil photo ($siteId / $tag)")
            launcher.launch(Photos.uriFor(ctx, f))
        } catch (e: Exception) {
            pending = null
            AppLog.e("photo", "Impossible d'ouvrir l'appareil photo", e)
            android.widget.Toast.makeText(ctx, "Appareil photo indisponible : ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun Thumb(rel: String, modifier: Modifier = Modifier, maxSide: Int = 300, contentScale: ContentScale = ContentScale.Crop) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, rel) {
        value = withContext(Dispatchers.IO) {
            runCatching { Photos.decodeSampled(Photos.abs(ctx, rel), maxSide)?.asImageBitmap() }
                .onFailure { AppLog.e("photo", "Lecture de la miniature impossible : $rel", it) }
                .getOrNull()
        }
    }
    val fade by animateFloatAsState(if (bmp != null) 1f else 0f, tween(350), label = "thumbFade")
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        bmp?.let { Image(it, null, Modifier.fillMaxSize().graphicsLayer { alpha = fade }, contentScale = contentScale) }
    }
}

@Composable
fun PhotoStrip(
    siteId: String,
    tag: String,
    photos: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    label: String? = null,
) {
    val ctx = LocalContext.current
    val take = rememberPhotoTaker(siteId, tag, onAdd)
    var viewing by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
        Row(
            Modifier.fillMaxWidth().animateContentSize().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            run {
                Surface(
                    onClick = take,
                    modifier = Modifier.size(84.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Column(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Outlined.AddAPhoto, "Prendre une photo")
                        Text("Photo", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            photos.forEach { p ->
                key(p) { Thumb(p, Modifier.size(84.dp).clip(RoundedCornerShape(10.dp)).clickable { viewing = p }) }
            }
        }
    }
    viewing?.let { p ->
        Dialog(onDismissRequest = { viewing = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = Color.Black) {
                Column(Modifier.fillMaxSize()) {
                    Thumb(p, Modifier.weight(1f).fillMaxWidth(), maxSide = 1800, contentScale = ContentScale.Fit)
                    Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { viewing = null }) { Text("Fermer") }
                        OutlinedButton(onClick = {
                            Photos.abs(ctx, p).delete()
                            onRemove(p)
                            viewing = null
                        }) {
                            Icon(Icons.Outlined.Delete, null)
                            Text("  Supprimer")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SmallAction(text: String, onClick: () -> Unit) {
    AssistChip(onClick = onClick, label = { Text(text) })
}
