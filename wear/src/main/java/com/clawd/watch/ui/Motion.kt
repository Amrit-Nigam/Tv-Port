package com.clawd.watch.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.clawd.watch.claude.ClaudeKind
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * The per-state motion layer — the effects that sit between the bezel ring and the eyes.
 *
 * ── The geometry that constrains everything here ──────────────────────────────────────────────
 * Measured on the real layout, not on a mockup:
 *
 *   curved text glyph band   r ≈ 160 … 186 px   (CurvedLayout, 11dp inset, 13sp)
 *   bezel ring band          r ≈ 186 … 196 px
 *   eye centre               y  = 0.445 · h
 *   centre label top         y  = 0.726 · h
 *
 * There is **no free radial band** between the curved text and the ring — they meet at 186 px. So
 * no concentric ring, arc or orbiting marker can be added at an intermediate radius; anything that
 * orbits must ride the ring itself. And a centre effect may not exceed 117 px of radius or it
 * reaches the label. [SAFE_R] is that limit with margin.
 *
 * These two numbers are why the design mockups could not be ported literally: a browser mockup
 * drew its arc text wherever it liked, so it had room the device does not.
 */

/** Largest radius a centre-anchored effect may reach without touching the label. 0.281 h is the
 *  measured limit; 0.27 leaves a little for the burn-in drift. */
private const val SAFE_R = 0.27f

/** Where the eyes actually sit, as a fraction of height — everything centres on them, not on the
 *  geometric centre of the glass, so effects read as coming *from* the creature. */
private const val EYE_Y = 0.445f

/**
 * A double-beat: pulse, pulse, rest, on a 1.6 s cycle.
 *
 * A steady sine becomes wallpaper within a minute. A heartbeat cadence stays legible as urgency
 * because nothing else on a wrist moves like that — which is the entire reason permission uses it
 * and waiting does not.
 */
fun heartbeat(t: Float): Float {
    val p = t % 1.6f
    return when {
        p < 0.12f -> 1f - (p / 0.12f) * 0.72f
        p < 0.24f -> 0.28f
        p < 0.36f -> 1f - ((p - 0.24f) / 0.12f) * 0.72f
        else -> 0.28f
    }
}

/**
 * Draw the centre effects for [kind]. [since] is seconds since this state was entered, which is
 * what lets `done` fire once rather than looping.
 */
fun DrawScope.drawStateEffects(kind: ClaudeKind, online: Boolean, t: Float, since: Float, calm: Boolean) {
    if (calm) return // ambient permits no motion at all
    val unit = size.minDimension
    val centre = Offset(size.width / 2f, size.height * EYE_Y)
    val maxR = unit * SAFE_R

    if (!online) return

    when (kind) {
        // Thought waves: three rings expanding from behind the eyes. The one piece of motion that
        // says something is happening in there without resorting to a progress bar.
        ClaudeKind.THINKING -> {
            for (i in 0 until 3) {
                val p = (((t - i * 0.933f) / 2.8f) % 1f + 1f) % 1f
                // Gone by 62% of the cycle, well before the ring could reach the label.
                if (p >= 0.62f) continue
                val r = lerpF(unit * 0.06f, maxR, p)
                val a = 0.34f * (1f - p / 0.62f)
                drawCircle(
                    color = Amber.copy(alpha = a),
                    radius = r,
                    center = centre,
                    style = Stroke(width = unit * 0.005f),
                )
            }
        }

        // A halo that collapses INWARD toward the face. Inward motion pulls the gaze to the eyes;
        // an outward pulse pushes it off the screen, which is what the old breathing ring did.
        ClaudeKind.WAITING -> {
            val p = (t / 2.85f) % 1f
            val r = lerpF(maxR, unit * 0.13f, p)
            // sin() zeroes the alpha at both ends, so the ring is never visible at its widest —
            // that is what keeps it clear of the label on the way in.
            val a = sin(p * PI.toFloat()) * 0.42f
            drawCircle(
                color = Amber.copy(alpha = a),
                radius = r,
                center = centre,
                style = Stroke(width = unit * 0.008f),
            )
        }

        // The whole face acknowledges the beat, not just the ring. Kept to the outer edge, outside
        // the curved text, and low enough in alpha to be a wash rather than a flash.
        ClaudeKind.PERMISSION -> {
            val beat = heartbeat(t)
            drawRect(
                brush = Brush.radialGradient(
                    0.80f to Color.Transparent,
                    1f to Ember.copy(alpha = 0.20f * beat),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = unit / 2f,
                ),
                topLeft = Offset.Zero,
                size = Size(size.width, size.height),
            )
        }

        // One burst, once. Eight spokes fire and are gone inside 900 ms: a completion is
        // punctuation, not a state you sit in.
        ClaudeKind.DONE -> {
            if (since > 0.9f) return
            val p = (since / 0.9f).coerceIn(0f, 1f)
            val inner = lerpF(unit * 0.10f, unit * 0.17f, p)
            val outer = lerpF(unit * 0.13f, maxR, p)
            val a = (1f - p) * 0.85f
            for (i in 0 until 8) {
                val ang = (i * 45f) * (PI / 180f).toFloat()
                val dx = cos(ang)
                val dy = sin(ang)
                drawLine(
                    color = Terracotta.copy(alpha = a),
                    start = Offset(centre.x + dx * inner, centre.y + dy * inner),
                    end = Offset(centre.x + dx * outer, centre.y + dy * outer),
                    strokeWidth = unit * 0.006f,
                    cap = StrokeCap.Round,
                )
            }
        }

        else -> Unit // idle carries its motion in the breathing scale and the ring's tide
    }
}

/**
 * Idle's breathing scale. A resting creature is never perfectly still, and half a percent over six
 * seconds reads as alive without ever drawing the eye.
 *
 * Applied to the whole composition, which is safe: the ring is inset 16 px from the glass, so a
 * ±0.3% scale on a 416 px face moves it by about a pixel and can never clip.
 */
fun idleBreath(kind: ClaudeKind, online: Boolean, t: Float, calm: Boolean): Float =
    if (calm || !online || kind != ClaudeKind.IDLE) 1f
    else 1f + 0.003f * sin(t * 1.047f)

/** Twelve discrete positions a second and a bit apart — work landing, against the smooth comets. */
fun tickIndex(t: Float): Int = floor(t * 2.5f).toInt().mod(12)

internal fun lerpF(a: Float, b: Float, t: Float): Float = a + (b - a) * t
