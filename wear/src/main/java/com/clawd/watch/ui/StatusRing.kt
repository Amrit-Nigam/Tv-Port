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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
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
fun StatusRing(state: WatchState, t: Float, since: Float, calm: Boolean, modifier: Modifier = Modifier) {
    val kind = state.ui?.kind ?: ClaudeKind.IDLE
    Canvas(modifier = modifier) { drawRing(t, since, kind, state.online, calm) }
}

private fun DrawScope.drawRing(t: Float, since: Float, kind: ClaudeKind, online: Boolean, calm: Boolean) {
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
        drawBrokenRing(topLeft, arcSize, stroke, t, calm)
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
            // Twelve discrete positions, one every 0.4s: work landing, stepped against the
            // smooth comets. It rides the ring because the band inside it belongs to the
            // curved text — there is no free radius between them.
            if (kind == ClaudeKind.TOOL) {
                val a = (tickIndex(t) * 30f - 90f) * (PI / 180f).toFloat()
                val r = (unit - inset * 2f) / 2f
                drawCircle(
                    color = base.copy(alpha = 0.9f),
                    radius = stroke * 0.42f,
                    center = Offset(size.width / 2f + cos(a) * r, size.height / 2f + sin(a) * r),
                )
            }
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
        ClaudeKind.WAITING -> {
            val pulse = 0.45f + 0.55f * (0.5f + 0.5f * sin(t * 2.2f))
            drawArc(base.copy(alpha = 0.20f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            drawArc(base.copy(alpha = pulse), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
        }

        // A double-beat rather than a sine: pulse, pulse, rest. A steady pulse becomes wallpaper
        // within a minute; this stays legible as urgency because nothing else on a wrist moves
        // like a heartbeat.
        ClaudeKind.PERMISSION -> {
            drawArc(base.copy(alpha = 0.20f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            drawArc(base.copy(alpha = heartbeat(t)), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
        }

        // Draws itself closed over 0.7s on entry, then holds. Arriving is part of the message.
        ClaudeKind.DONE -> {
            val sweep = 360f * smoothstepF((since / 0.7f).coerceIn(0f, 1f))
            drawArc(base.copy(alpha = 0.85f), -90f, sweep, false, topLeft, arcSize, style = Stroke(stroke))
        }

        // Idle: present but recessive — you should have to look for it. It breathes with the
        // face rather than sitting at a fixed alpha, so a resting ring never reads as a dead one.
        ClaudeKind.IDLE -> {
            val tide = 0.10f + 0.06f * (0.5f + 0.5f * sin(t * 1.047f))
            drawArc(base.copy(alpha = tide), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke * 0.4f))
        }
    }
}

/** Offline: a ring with gaps in it. An incomplete circle reads as "broken" pre-verbally. */
private fun DrawScope.drawBrokenRing(topLeft: Offset, arcSize: Size, stroke: Float, t: Float, calm: Boolean) {
    val dashes = 16
    // One dash brightens and steps around the ring every 3s. An inert offline state looks
    // identical to a crashed app; this says the link is dead but something is still trying.
    val probe = if (calm) -1 else floor(t / 3f * dashes).toInt().mod(dashes)
    for (i in 0 until dashes) {
        drawArc(
            color = if (i == probe) Cream.copy(alpha = 0.34f) else Ash,
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

/** Smoothstep, for the one-shot ring draw-on. */
internal fun smoothstepF(x: Float): Float = x * x * (3f - 2f * x)
