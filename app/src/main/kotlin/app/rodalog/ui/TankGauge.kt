package app.rodalog.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Tanque de combustible animado: el líquido sube/baja suavemente al cambiar el nivel
 * y ondula todo el tiempo. Verde-menta normal, ámbar bajo 35 %, rojo bajo 15 %.
 */
@Composable
fun TankGauge(fraction: Float, modifier: Modifier = Modifier, outline: Color = Color.White) {
    val target = fraction.coerceIn(0f, 1f)
    val level by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
        label = "level",
    )
    val transition = rememberInfiniteTransition(label = "waves")
    val phase1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart),
        label = "phase1",
    )
    val phase2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(4300, easing = LinearEasing), RepeatMode.Restart),
        label = "phase2",
    )
    val liquid = when {
        level < 0.15f -> Color(0xFFFF6B6B)
        level < 0.35f -> Color(0xFFFFC857)
        else -> Color(0xFF7CF5E0)
    }

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val capH = h * 0.11f
        val bodyTop = capH * 0.8f
        val bodyH = h - bodyTop
        val r = w * 0.24f

        val body = Path().apply {
            addRoundRect(RoundRect(0f, bodyTop, w, h, CornerRadius(r, r)))
        }

        // tapón
        drawRoundRect(
            color = outline.copy(alpha = 0.9f),
            topLeft = Offset(w * 0.36f, 0f),
            size = Size(w * 0.28f, capH * 1.2f),
            cornerRadius = CornerRadius(8f, 8f),
        )
        // "vidrio"
        drawPath(body, outline.copy(alpha = 0.14f))

        clipPath(body) {
            val top = bodyTop + bodyH * (1f - level)
            val amp = (min(level, 1f - level) * 60f).coerceIn(0f, 9f)

            fun wave(phase: Float, shift: Float): Path {
                val p = Path()
                p.moveTo(0f, h)
                var x = 0f
                while (x <= w) {
                    val y = top + amp * sin(x / w * 2f * PI.toFloat() * 1.4f + phase + shift)
                    p.lineTo(x, y)
                    x += 4f
                }
                p.lineTo(w, h)
                p.close()
                return p
            }

            if (level > 0.01f) {
                drawPath(wave(phase2, 2f), liquid.copy(alpha = 0.45f))
                drawPath(wave(phase1, 0f), liquid.copy(alpha = 0.92f))
            }

            // marcas de 25 / 50 / 75 %
            for (g in 1..3) {
                val y = bodyTop + bodyH * (1f - g / 4f)
                drawLine(
                    color = outline.copy(alpha = 0.55f),
                    start = Offset(w * 0.78f, y),
                    end = Offset(w * 0.94f, y),
                    strokeWidth = 3f,
                )
            }
        }

        drawPath(body, outline, style = Stroke(width = 5f))
    }
}
