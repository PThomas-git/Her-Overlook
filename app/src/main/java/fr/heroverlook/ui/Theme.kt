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
 * Charte « Hér'Overlook » : noir chaud et crème, tons tamisés.
 *  - Nuit : fond presque noir, texte crème, accent sable ;
 *  - Jour : papier crème, texte brun-noir ;
 *  - verdicts en tons éteints (sauge, ocre, brique) pour ne pas casser l'ambiance.
 */
object Brand {
    val Ink = Color(0xFF110F0D)
    val Panel = Color(0xFF1B1916)
    val PanelHigh = Color(0xFF25221E)
    val Sand = Color(0xFFD8C7A0)
    val Copper = Color(0xFFC48E5E)
    val Cream = Color(0xFFEDE5D3)
    val Paper = Color(0xFFF3EBDC)
    val Bark = Color(0xFF2E261A)
}

private val DarkScheme = darkColorScheme(
    primary = Brand.Sand,
    onPrimary = Color(0xFF1C1608),
    primaryContainer = Color(0xFF3A3224),
    onPrimaryContainer = Color(0xFFEDE0C2),
    secondary = Brand.Copper,
    onSecondary = Color(0xFF241406),
    background = Brand.Ink,
    onBackground = Brand.Cream,
    surface = Brand.Ink,
    onSurface = Brand.Cream,
    surfaceVariant = Brand.PanelHigh,
    onSurfaceVariant = Color(0xFFA89F8C),
    surfaceContainer = Brand.Panel,
    surfaceContainerHigh = Brand.PanelHigh,
    surfaceContainerHighest = Color(0xFF302C26),
    outline = Color(0xFF6A6252),
    outlineVariant = Color(0xFF38332C),
    error = Color(0xFFD0766A),
)

private val LightScheme = lightColorScheme(
    primary = Brand.Bark,
    onPrimary = Brand.Paper,
    primaryContainer = Color(0xFFE4D8BD),
    onPrimaryContainer = Brand.Bark,
    secondary = Color(0xFF8A5A2B),
    onSecondary = Color.White,
    background = Brand.Paper,
    onBackground = Color(0xFF1B1914),
    surface = Brand.Paper,
    onSurface = Color(0xFF1B1914),
    surfaceVariant = Color(0xFFE7DDC6),
    onSurfaceVariant = Color(0xFF6B6354),
    surfaceContainer = Color(0xFFFAF5EA),
    surfaceContainerHigh = Color(0xFFEDE3CF),
    surfaceContainerHighest = Color(0xFFE4D9C1),
    outline = Color(0xFF8F8570),
    outlineVariant = Color(0xFFD8CCB2),
    error = Color(0xFFA8433A),
)

private val HerTypography = Typography().let {
    it.copy(
        headlineSmall = it.headlineSmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium),
        titleLarge = it.titleLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, fontSize = 22.sp),
        titleMedium = it.titleMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, fontSize = 17.sp),
        titleSmall = it.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = it.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = it.labelMedium.copy(letterSpacing = 0.8.sp),
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
        Verdict.OK -> if (dark) Color(0xFF9DBF94) else Color(0xFF4F7A4A)
        Verdict.RESERVE -> if (dark) Color(0xFFD9A95B) else Color(0xFFA8751F)
        Verdict.NC -> if (dark) Color(0xFFD0766A) else Color(0xFFA8433A)
        Verdict.NA -> if (dark) Color(0xFF9A9283) else Color(0xFF7C7466)
        Verdict.UNSET -> MaterialTheme.colorScheme.outline
    }
}

/** Couleur du texte / icône posés sur un fond de verdict. */
@Composable
fun onVerdictColor(): Color = if (isSystemInDarkTheme()) Brand.Ink else Color(0xFFFBF6EA)

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
