package com.clawd.watch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.clawd.watch.claude.ClaudeKind
import com.clawd.watch.claude.WatchState
import kotlin.math.sin

/**
 * The bezel ring — the reason this app is worth putting on a round screen rather than a square one.
 *
 * It turns the outermost band of the display, which a rectangular layout can only ever waste, into
 * the primary status indicator: readable from the far side of a room, at a glance, without reading
 * a single word.
 *
 *   THINKING   one slow comet orbiting  — deliberating
 *   TOOL       two fast comets orbiting — actively working
 *   WAITING    the full ring, breathing amber
 *   PERMISSION the full ring, urgent ember, fast
 *   DONE       the full ring, settled and solid
 *   IDLE       a faint hairline
 *   offline    a broken ring of dashes — visibly not a whole circle
 */
@Composable
fun StatusRing(state: WatchState, t: Float, calm: Boolean, modifier: Modifier = Modifier) {
    val kind = state.ui?.kind ?: ClaudeKind.IDLE
    Canvas(modifier = modifier) { drawRing(t, kind, state.online, calm) }
}

private fun DrawScope.drawRing(t: Float, kind: ClaudeKind, online: Boolean, calm: Boolean) {
    val unit = size.minDimension
    val stroke = unit * 0.024f
    // Hug the physical bezel, but leave enough margin that the burn-in drift (see ClawdScreen)
    // can never push the ring off the edge of the glass.
    val inset = stroke / 2f + unit * 0.028f
    val d = unit - inset * 2f
    val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)
    val arcSize = Size(d, d)
    val cap = Stroke(width = stroke, cap = StrokeCap.Round)

    if (!online) {
        drawBrokenRing(topLeft, arcSize, stroke)
        return
    }

    val base = when (kind) {
        ClaudeKind.PERMISSION -> Ember
        ClaudeKind.WAITING -> Amber
        ClaudeKind.DONE -> Terracotta
        ClaudeKind.THINKING, ClaudeKind.TOOL -> Cream
        ClaudeKind.IDLE -> Cream
    }

    // Ambient forbids motion, but not information: keep the state colour and draw the ring whole,
    // just still. You can still tell working from blocked from idle across a desk.
    if (calm) {
        drawArc(base.copy(alpha = 0.38f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke * 0.55f))
        return
    }

    when (kind) {
        // Orbiting comets: the ring reads as motion, so "still working" is obvious peripherally.
        ClaudeKind.THINKING, ClaudeKind.TOOL -> {
            val comets = if (kind == ClaudeKind.TOOL) 2 else 1
            val speed = if (kind == ClaudeKind.TOOL) 150f else 62f   // degrees / second
            val sweep = if (kind == ClaudeKind.TOOL) 46f else 74f
            drawArc(base.copy(alpha = 0.10f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke * 0.42f))
            repeat(comets) { i ->
                val head = (t * speed + i * 360f / comets) % 360f
                // Draw the tail as a few segments of falling alpha — a cheap gradient that costs
                // nothing and survives on a low-power GPU better than a real shader sweep.
                val segments = 5
                for (s in 0 until segments) {
                    val a = 1f - s / segments.toFloat()
                    drawArc(
                        color = base.copy(alpha = 0.85f * a * a),
                        startAngle = head - sweep * (s + 1) / segments,
                        sweepAngle = sweep / segments + 0.6f, // overlap hides seams between segments
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = cap,
                    )
                }
            }
        }

        // Blocked on you: the whole ring breathes so it catches the eye without a notification.
        ClaudeKind.WAITING, ClaudeKind.PERMISSION -> {
            val rate = if (kind == ClaudeKind.PERMISSION) 5.0f else 2.2f
            val pulse = 0.45f + 0.55f * (0.5f + 0.5f * sin(t * rate))
            drawArc(base.copy(alpha = 0.20f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            drawArc(base.copy(alpha = pulse), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
        }

        ClaudeKind.DONE ->
            drawArc(base.copy(alpha = 0.85f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))

        // Idle: present but recessive — you should have to look for it.
        ClaudeKind.IDLE ->
            drawArc(base.copy(alpha = 0.13f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke * 0.4f))
    }
}

/** Offline: a ring with gaps in it. An incomplete circle reads as "broken" pre-verbally. */
private fun DrawScope.drawBrokenRing(topLeft: Offset, arcSize: Size, stroke: Float) {
    val dashes = 16
    for (i in 0 until dashes) {
        drawArc(
            color = Ash,
            startAngle = i * (360f / dashes),
            sweepAngle = 360f / dashes * 0.42f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke * 0.5f, cap = StrokeCap.Round),
        )
    }
}

// Kept in one place so the ring, the eyes and the text can never drift out of palette.
internal val Cream = Color(0xFFE8DCC8)
internal val Amber = Color(0xFFE0A86A)
internal val Terracotta = Color(0xFFD98158)
internal val Ember = Color(0xFFC85A3C)
internal val Ash = Color(0xFF4A423C)
internal val Ink = Color(0xFF12100E)
internal val Bg = Color(0xFF070506)
internal val TextHi = Color(0xFFD8CFC4)
internal val TextLow = Color(0xFF5C534C)
