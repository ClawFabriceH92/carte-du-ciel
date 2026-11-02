package fr.cartedueciel.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

/**
 * Icônes dessinées à la main (formes simples), afin de ne dépendre d'aucune bibliothèque
 * d'icônes étendue et de garantir qu'elles suivent la couleur du thème (mode nuit).
 */
object AppIcons {

    @Composable
    fun Layers(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
        Canvas(modifier.size(24.dp)) {
            val w = size.width
            val stroke = w * 0.1f
            for (i in 0 until 3) {
                val y = w * (0.25f + 0.25f * i)
                drawLine(tint, Offset(w * 0.15f, y), Offset(w * 0.85f, y), strokeWidth = stroke, cap = StrokeCap.Round)
            }
        }
    }

    @Composable
    fun Camera(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
        Canvas(modifier.size(24.dp)) {
            val w = size.width
            val stroke = w * 0.08f
            drawRoundRect(tint, topLeft = Offset(w * 0.1f, w * 0.28f), size = Size(w * 0.8f, w * 0.55f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.1f), style = Stroke(stroke))
            drawRoundRect(tint, topLeft = Offset(w * 0.35f, w * 0.16f), size = Size(w * 0.3f, w * 0.14f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.04f))
            drawCircle(tint, radius = w * 0.16f, center = Offset(w * 0.5f, w * 0.56f), style = Stroke(stroke))
        }
    }

    @Composable
    fun Night(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
        Canvas(modifier.size(24.dp)) {
            val w = size.width
            val path = Path().apply {
                addArc(androidx.compose.ui.geometry.Rect(w * 0.15f, w * 0.15f, w * 0.85f, w * 0.85f), 40f, 280f)
                arcTo(androidx.compose.ui.geometry.Rect(w * 0.3f, w * 0.12f, w * 0.95f, w * 0.78f), 250f, -200f, false)
                close()
            }
            drawPath(path, tint)
        }
    }

    @Composable
    fun Manual(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
        Canvas(modifier.size(24.dp)) {
            val w = size.width
            val c = Offset(w / 2, w / 2)
            val stroke = w * 0.08f
            drawLine(tint, Offset(w * 0.12f, c.y), Offset(w * 0.88f, c.y), stroke, StrokeCap.Round)
            drawLine(tint, Offset(c.x, w * 0.12f), Offset(c.x, w * 0.88f), stroke, StrokeCap.Round)
            val a = w * 0.14f
            fun tri(tip: Offset, dx: Float, dy: Float) {
                val p = Path().apply {
                    moveTo(tip.x, tip.y)
                    lineTo(tip.x - dx * a + dy * a * 0.7f, tip.y - dy * a + dx * a * 0.7f)
                    lineTo(tip.x - dx * a - dy * a * 0.7f, tip.y - dy * a - dx * a * 0.7f)
                    close()
                }
                drawPath(p, tint)
            }
            tri(Offset(w * 0.08f, c.y), -1f, 0f); tri(Offset(w * 0.92f, c.y), 1f, 0f)
            tri(Offset(c.x, w * 0.08f), 0f, -1f); tri(Offset(c.x, w * 0.92f), 0f, 1f)
        }
    }

    @Composable
    fun Compass(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
        Canvas(modifier.size(24.dp)) {
            val w = size.width
            val c = Offset(w / 2, w / 2)
            drawCircle(tint, radius = w * 0.42f, center = c, style = Stroke(w * 0.07f))
            val needle = Path().apply {
                moveTo(c.x, w * 0.18f); lineTo(c.x + w * 0.1f, c.y); lineTo(c.x, w * 0.82f); lineTo(c.x - w * 0.1f, c.y); close()
            }
            drawPath(needle, tint)
        }
    }

    @Composable
    fun Arrow(modifier: Modifier = Modifier, tint: Color = LocalContentColor.current, angleDeg: Float = 0f) {
        Canvas(modifier.size(48.dp)) {
            val w = size.width
            val c = Offset(w / 2, w / 2)
            rotate(angleDeg, c) {
                val p = Path().apply {
                    moveTo(c.x, w * 0.08f)
                    lineTo(c.x + w * 0.3f, w * 0.62f)
                    lineTo(c.x, w * 0.5f)
                    lineTo(c.x - w * 0.3f, w * 0.62f)
                    close()
                }
                drawPath(p, tint)
            }
        }
    }
}
