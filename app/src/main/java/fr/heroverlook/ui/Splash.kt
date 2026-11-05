package fr.heroverlook.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.min

private fun phase(t: Float, from: Float, to: Float): Float = ((t - from) / (to - from)).coerceIn(0f, 1f)

/** Animation de lancement : l'œil se dessine, l'iris se referme, la pupille (1 U de baie) apparaît, puis le nom. */
@Composable
fun SplashOverlay(onFinished: () -> Unit) {
    val t = remember { Animatable(0f) }
    val out = remember { Animatable(1f) }
    val bg = MaterialTheme.colorScheme.background
    val stroke = MaterialTheme.colorScheme.primary
    val accent = MaterialTheme.colorScheme.secondary

    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(1500, easing = LinearEasing))
        delay(350)
        out.animateTo(0f, tween(380))
        onFinished()
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = out.value }
            .background(bg)
            .pointerInput(Unit) { detectTapGestures { onFinished() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Canvas(Modifier.size(190.dp)) {
                val s = size.width / 108f
                val v = t.value
                scale(scale = s, pivot = Offset.Zero) {
                    // 1) contour de l'œil, tracé progressivement
                    val eye = Path().apply {
                        moveTo(22f, 54f)
                        quadraticBezierTo(54f, 28f, 86f, 54f)
                        quadraticBezierTo(54f, 80f, 22f, 54f)
                        close()
                    }
                    val pm = PathMeasure().apply { setPath(eye, false) }
                    val seg = Path()
                    pm.getSegment(0f, pm.length * phase(v, 0f, 0.42f), seg, true)
                    drawPath(seg, stroke, style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))

                    // 2) iris : arc qui se referme
                    val iris = phase(v, 0.34f, 0.66f)
                    if (iris > 0f) {
                        drawArc(
                            color = stroke, startAngle = -90f, sweepAngle = 360f * iris, useCenter = false,
                            topLeft = Offset(40f, 40f), size = Size(28f, 28f),
                            style = Stroke(width = 3f, cap = StrokeCap.Round),
                        )
                    }

                    // 3) pupille : une unité de baie qui s'imprime
                    val pop = phase(v, 0.60f, 0.78f)
                    if (pop > 0f) {
                        val half = 6f * pop
                        drawRect(accent, topLeft = Offset(54f - half, 54f - half), size = Size(half * 2, half * 2))
                    }

                    // 4) graduations de rack
                    val tick = phase(v, 0.72f, 0.92f)
                    if (tick > 0f) {
                        val a = min(1f, tick)
                        val c = accent.copy(alpha = a)
                        for (x in listOf(30f, 78f)) {
                            drawLine(c, Offset(x, 40f), Offset(x, 44f), strokeWidth = 2.5f, cap = StrokeCap.Round)
                            drawLine(c, Offset(x, 64f), Offset(x, 68f), strokeWidth = 2.5f, cap = StrokeCap.Round)
                        }
                    }
                }
            }
            val textAlpha = phase(t.value, 0.70f, 1f)
            Text(
                "Hér'Overlook",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.graphicsLayer { alpha = textAlpha; translationY = (1f - textAlpha) * 12.dp.toPx() },
            )
            Text(
                "Relevé POP / ADR",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { alpha = textAlpha },
            )
        }
    }
}
