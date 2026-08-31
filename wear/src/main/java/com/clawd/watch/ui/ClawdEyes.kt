package com.clawd.watch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import com.clawd.watch.claude.ClaudeKind
import com.clawd.watch.claude.WatchState
import kotlin.math.sin

/**
 * The face. Two eyes on a near-black round screen whose behaviour is driven entirely by Claude's
 * live state:
 *
 *   IDLE       half-lidded, slow wandering gaze, long lazy blinks
 *   THINKING   gaze drifts up and away, the classic "looking into space" tell
 *   TOOL       eyes sweep left-to-right like reading a line, with a fast flyback and quick blinks
 *   WAITING    wide, pupils locked on you, amber, breathing
 *   PERMISSION same but narrowed and urgent red, pulsing fast
 *   DONE       a happy squint (arcs, no pupils)
 *   offline    closed — two flat grey lines
 *
 * All motion is computed analytically from one clock rather than from a pile of Animatables, so
 * adding a behaviour is a matter of adding a branch, and there is no animation state to keep in
 * sync with the incoming SSE events.
 */
@Composable
fun ClawdEyes(state: WatchState, t: Float, modifier: Modifier = Modifier) {
    val kind = state.ui?.kind ?: ClaudeKind.IDLE
    val offline = !state.online

    Canvas(modifier = modifier) {
        drawFace(t = t, kind = kind, offline = offline)
    }
}

// Palette is shared with the ring (see StatusRing.kt) — warm and low-luminance, matching the TV
// dashboard's "midnight ember" night theme. This sits an inch from the eye in the dark, so nothing
// here is allowed to be bright or blue.

private fun eyeColor(kind: ClaudeKind, offline: Boolean): Color = when {
    offline -> Ash
    kind == ClaudeKind.PERMISSION -> Ember
    kind == ClaudeKind.WAITING -> Amber
    kind == ClaudeKind.DONE -> Terracotta
    kind == ClaudeKind.IDLE -> Cream.copy(alpha = 0.55f)
    else -> Cream
}

// ── Drawing ───────────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawFace(t: Float, kind: ClaudeKind, offline: Boolean) {
    val color = eyeColor(kind, offline)

    // Eyes fill the box they are given on both axes rather than scaling off the smaller one — the
    // caller has already inset the box clear of the bezel, so anything left unused here is just
    // wasted glass. Stroke widths still key off minDimension so they stay proportional.
    val unit = size.minDimension
    val eyeW = size.width * 0.30f
    val eyeH = size.height * 0.74f
    val gap = size.width * 0.14f
    val cy = size.height * 0.5f
    val leftCx = size.width / 2f - gap / 2f - eyeW / 2f
    val rightCx = size.width / 2f + gap / 2f + eyeW / 2f

    // A subtle whole-face bob while busy: it reads as effort without being distracting.
    val bob = if (kind == ClaudeKind.THINKING || kind == ClaudeKind.TOOL) sin(t * 2.4f) * unit * 0.006f else 0f

    if (offline) {
        drawClosedEyes(leftCx, rightCx, cy + bob, eyeW, color)
        return
    }
    if (kind == ClaudeKind.DONE) {
        drawHappyArcs(leftCx, rightCx, cy + bob, eyeW, eyeH, color)
        return
    }

    val openness = openness(t, kind)
    val gaze = gaze(t, kind)

    // WAITING/PERMISSION breathe — a slow glow swell that catches the eye in peripheral vision.
    val pulse = when (kind) {
        ClaudeKind.WAITING -> 0.5f + 0.5f * sin(t * 2.2f)
        ClaudeKind.PERMISSION -> 0.5f + 0.5f * sin(t * 5.0f)
        else -> 0f
    }

    for (cx in listOf(leftCx, rightCx)) {
        drawEye(
            cx = cx,
            cy = cy + bob,
            w = eyeW,
            h = eyeH,
            openness = openness,
            gaze = gaze,
            color = color,
            glow = pulse,
        )
    }
}

/** One eye: a rounded-rect sclera clipped by the eyelid, with a dark pupil offset by the gaze. */
private fun DrawScope.drawEye(
    cx: Float,
    cy: Float,
    w: Float,
    h: Float,
    openness: Float,
    gaze: Offset,
    color: Color,
    glow: Float,
) {
    val openH = (h * openness).coerceAtLeast(w * 0.10f) // never fully vanish; a blink is a slit
    val rect = Rect(cx - w / 2f, cy - openH / 2f, cx + w / 2f, cy + openH / 2f)
    val radius = CornerRadius(w * 0.42f, w * 0.42f)

    // Soft halo behind the eye when it wants your attention.
    if (glow > 0f) {
        drawRoundRect(
            color = color.copy(alpha = 0.16f * glow),
            topLeft = Offset(rect.left - w * 0.22f, rect.top - w * 0.22f),
            size = Size(rect.width + w * 0.44f, rect.height + w * 0.44f),
            cornerRadius = CornerRadius(w * 0.6f, w * 0.6f),
        )
    }

    drawRoundRect(
        color = color,
        topLeft = Offset(rect.left, rect.top),
        size = Size(rect.width, rect.height),
        cornerRadius = radius,
    )

    // The pupil is CLIPPED to the eye rather than merely kept inside it. That is what makes a
    // blink look like an eyelid closing over the eye instead of a circle shrinking independently:
    // as the lid comes down it slices the pupil off, exactly as a real one does.
    val pupilR = w * 0.26f
    val travelX = (w / 2f - pupilR) * 0.85f
    val travelY = (h / 2f - pupilR).coerceAtLeast(0f)
    val px = cx + gaze.x * travelX
    // Track the gaze against the fully-open eye, so the pupil holds its position while the lid
    // passes over it rather than being dragged up and down by the blink.
    val py = cy + gaze.y * travelY
    val lid = Path().apply { addRoundRect(RoundRect(rect, radius)) }
    clipPath(lid) {
        drawCircle(color = Ink, radius = pupilR, center = Offset(px, py))
    }
}

/** DONE: no pupils, just two upward arcs — the universal shorthand for a pleased squint. */
private fun DrawScope.drawHappyArcs(leftCx: Float, rightCx: Float, cy: Float, w: Float, h: Float, color: Color) {
    val stroke = Stroke(width = w * 0.22f)
    for (cx in listOf(leftCx, rightCx)) {
        val path = Path().apply {
            moveTo(cx - w / 2f, cy + h * 0.10f)
            quadraticTo(cx, cy - h * 0.34f, cx + w / 2f, cy + h * 0.10f)
        }
        drawPath(path, color = color, style = stroke)
    }
}

/** Offline: eyes shut. Deliberately inert so a dead link is unmistakable at a glance. */
private fun DrawScope.drawClosedEyes(leftCx: Float, rightCx: Float, cy: Float, w: Float, color: Color) {
    val stroke = Stroke(width = w * 0.18f)
    for (cx in listOf(leftCx, rightCx)) {
        val path = Path().apply {
            moveTo(cx - w / 2f, cy)
            lineTo(cx + w / 2f, cy)
        }
        drawPath(path, color = color, style = stroke)
    }
}

// ── Motion ────────────────────────────────────────────────────────────────────────────────────

/** Eyelid position, 0 = shut, 1 = wide. Baseline per state, minus a blink dip. */
private fun openness(t: Float, kind: ClaudeKind): Float {
    val base = when (kind) {
        ClaudeKind.IDLE -> 0.55f
        ClaudeKind.THINKING -> 0.88f
        ClaudeKind.TOOL -> 0.92f
        ClaudeKind.WAITING -> 1.0f
        ClaudeKind.PERMISSION -> 0.78f // narrowed: urgent rather than surprised
        else -> 0.8f
    }
    // Blink cadence: relaxed when idle, snappier while working.
    val period = when (kind) {
        ClaudeKind.IDLE -> 5.4f
        ClaudeKind.TOOL -> 2.3f
        ClaudeKind.WAITING -> 4.2f
        ClaudeKind.PERMISSION -> 3.0f
        else -> 3.8f
    }
    val dur = 0.16f
    val phase = t % period
    if (phase < period - dur) return base
    // A blink is a fast close and a slightly slower open: 0 -> shut -> 0 across `dur`.
    val u = (phase - (period - dur)) / dur
    return base * (1f - sin(u * Math.PI.toFloat()))
}

/** Where the pupils are pointing, each axis in -1..1. */
private fun gaze(t: Float, kind: ClaudeKind): Offset = when (kind) {
    // Locked on you. Nothing to look at but the person who needs to answer.
    ClaudeKind.WAITING, ClaudeKind.PERMISSION -> Offset(0f, 0f)

    // Reading a line of output: sweep right, snap back, drop a little. This is the "writing" tell.
    ClaudeKind.TOOL -> {
        val lineDur = 1.15f
        val u = (t % lineDur) / lineDur
        val x = if (u < 0.82f) {
            -0.85f + (u / 0.82f) * 1.7f              // steady left-to-right scan
        } else {
            0.85f - ((u - 0.82f) / 0.18f) * 1.7f     // fast flyback to the start of the next line
        }
        // A slight vertical wander so successive sweeps aren't mechanically identical.
        Offset(x, 0.18f * sin(t * 0.9f))
    }

    // Looking up and away, drifting — deliberating rather than scanning.
    ClaudeKind.THINKING -> Offset(0.55f * sin(t * 0.55f), -0.6f + 0.15f * sin(t * 1.3f))

    // Idle/done: occasional lazy saccades to a new resting point.
    else -> saccade(t, interval = 2.7f, amp = 0.7f)
}

/**
 * Discrete glances: hold a point, then flick to the next one. The eye moves in ~15% of the
 * interval and rests for the remainder, which is roughly how a real saccade reads.
 */
private fun saccade(t: Float, interval: Float, amp: Float): Offset {
    val step = (t / interval).toInt()
    val u = ((t % interval) / interval) / 0.15f
    val k = if (u >= 1f) 1f else u * u * (3f - 2f * u) // smoothstep ease
    val from = Offset(rand(step * 2) * amp, rand(step * 2 + 1) * amp * 0.6f)
    val to = Offset(rand(step * 2 + 2) * amp, rand(step * 2 + 3) * amp * 0.6f)
    return Offset(from.x + (to.x - from.x) * k, from.y + (to.y - from.y) * k)
}

/** Deterministic pseudo-random in -1..1, so the gaze is stable across recompositions. */
private fun rand(i: Int): Float {
    var x = i * 374761393 + 668265263
    x = (x xor (x shr 13)) * 1274126177
    val v = ((x xor (x shr 16)) and 0x7fffffff).toFloat() / 0x7fffffff.toFloat()
    return v * 2f - 1f
}
