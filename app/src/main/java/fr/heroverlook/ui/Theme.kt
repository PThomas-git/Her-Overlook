package fr.heroverlook.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import fr.heroverlook.data.ItemKind
import fr.heroverlook.data.Verdict

/**
 * Charte « Hér'Overlook » : un poste de supervision de nuit.
 *  - Encre bleu-nuit (fond), teal « fibre » (action), ambre « signal » (attention),
 *  - rouge / vert réservés aux verdicts de conformité.
 */
object Brand {
    val Ink = Color(0xFF0B1620)
    val Panel = Color(0xFF122431)
    val PanelHigh = Color(0xFF1A3140)
    val Fibre = Color(0xFF3DD6C6)
    val FibreDeep = Color(0xFF00695F)
    val Signal = Color(0xFFFFB547)
    val Mist = Color(0xFFE6EEF2)
}

private val DarkScheme = darkColorScheme(
    primary = Brand.Fibre,
    onPrimary = Color(0xFF00201D),
    primaryContainer = Color(0xFF0F4A46),
    onPrimaryContainer = Color(0xFFB2F5EE),
    secondary = Brand.Signal,
    onSecondary = Color(0xFF2B1A00),
    background = Brand.Ink,
    onBackground = Brand.Mist,
    surface = Brand.Ink,
    onSurface = Brand.Mist,
    surfaceVariant = Brand.PanelHigh,
    onSurfaceVariant = Color(0xFFA9BCC8),
    surfaceContainer = Brand.Panel,
    surfaceContainerHigh = Brand.PanelHigh,
    surfaceContainerHighest = Color(0xFF22404F),
    outline = Color(0xFF4A6577),
    outlineVariant = Color(0xFF2A4254),
    error = Color(0xFFFF6B6B),
)

private val LightScheme = lightColorScheme(
    primary = Brand.FibreDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2F0E8),
    onPrimaryContainer = Color(0xFF00201D),
    secondary = Color(0xFF8A5A00),
    onSecondary = Color.White,
    background = Color(0xFFF2F6F8),
    onBackground = Color(0xFF0B1620),
    surface = Color(0xFFF2F6F8),
    onSurface = Color(0xFF0B1620),
    surfaceVariant = Color(0xFFDDE7ED),
    onSurfaceVariant = Color(0xFF41576A),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFE8F0F4),
    surfaceContainerHighest = Color(0xFFDDE7ED),
    outline = Color(0xFF6F8798),
    outlineVariant = Color(0xFFC3D2DB),
    error = Color(0xFFC62828),
)

private val HerTypography = Typography().let {
    it.copy(
        headlineSmall = it.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = it.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
        titleMedium = it.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = it.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

val MonoStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, letterSpacing = 0.4.sp)

@Composable
fun HerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        typography = HerTypography,
        content = content,
    )
}

@Composable
fun Verdict.color(): Color {
    val dark = isSystemInDarkTheme()
    return when (this) {
        Verdict.OK -> if (dark) Color(0xFF4CD97B) else Color(0xFF1E8E3E)
        Verdict.RESERVE -> if (dark) Color(0xFFFFB547) else Color(0xFFB26A00)
        Verdict.NC -> if (dark) Color(0xFFFF6B6B) else Color(0xFFC62828)
        Verdict.NA -> if (dark) Color(0xFF8CA0AE) else Color(0xFF607D8B)
        Verdict.UNSET -> MaterialTheme.colorScheme.outline
    }
}

/** Couleur du texte / icône posés sur un fond de verdict. */
@Composable
fun onVerdictColor(): Color = if (isSystemInDarkTheme()) Brand.Ink else Color.White

fun Verdict.icon(): ImageVector = when (this) {
    Verdict.OK -> Icons.Outlined.Check
    Verdict.RESERVE -> Icons.Outlined.Warning
    Verdict.NC -> Icons.Outlined.Close
    Verdict.NA -> Icons.Outlined.Remove
    Verdict.UNSET -> Icons.Outlined.HelpOutline
}

fun Verdict.shortLabel(kind: ItemKind): String = when (kind) {
    ItemKind.CONFORMITY -> when (this) {
        Verdict.OK -> "Conforme"
        Verdict.RESERVE -> "Réserve"
        Verdict.NC -> "Non conf."
        Verdict.NA -> "S.O."
        Verdict.UNSET -> "—"
    }
    ItemKind.PRESENCE -> when (this) {
        Verdict.OK -> "Présent"
        Verdict.RESERVE -> "Partiel"
        Verdict.NC -> "Absent"
        Verdict.NA -> "Non vu"
        Verdict.UNSET -> "—"
    }
}
