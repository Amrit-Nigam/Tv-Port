package com.clawd.watch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.AnchorType
import androidx.wear.compose.foundation.CurvedDirection
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.foundation.CurvedTextStyle
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.curvedText
import com.clawd.watch.AmbientUi
import com.clawd.watch.claude.ClaudeKind
import com.clawd.watch.claude.WatchState
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The whole watch UI, built around the circle rather than fighting it.
 *
 * Three concentric zones, each doing the job it is best shaped for:
 *   1. the bezel      — the status ring, plus curved text following the curve of the glass
 *   2. the mid band   — nothing, deliberately; the breathing room that makes the rest legible
 *   3. the centre     — the eyes and one line of state, where the eye lands first
 *
 * A rectangular layout on a round screen throws away the four corners and crowds the top and
 * bottom against the bezel. Putting the project name and the elapsed timer on curved baselines
 * reclaims exactly the band a Column cannot use, and buys the centre enough room for the eyes to
 * be big enough to read across a room.
 */
@Composable
fun ClawdScreen(state: WatchState, ambient: AmbientUi, modifier: Modifier = Modifier) {
    val ui = state.ui
    val dimmed = ambient.isAmbient

    // Interactive: a real frame clock. Ambient: motion is not permitted, so the clock advances only
    // on the system's ~per-minute tick — enough to keep the burn-in drift creeping, nothing more.
    val live by rememberSeconds(running = !dimmed)
    val seconds = if (dimmed) ambient.tick * 60f else live

    // ── Burn-in protection ───────────────────────────────────────────────────────────────────
    // This screen is meant to sit lit on a charger indefinitely, and the panel is OLED: any pixel
    // held at one colour for days will stain. The eyes move on their own, but the ring and the
    // curved text are otherwise perfectly static, so the entire composition creeps around a slow
    // Lissajous path. The two periods (~127s and ~89s) are close to coprime, so the figure does
    // not retrace itself for hours and no pixel sits under the same element for long. The
    // amplitude is a few px — invisible while you watch it, but enough to spread the wear.
    val driftX = sin(seconds * 0.0495f) * 5f
    val driftY = cos(seconds * 0.0706f) * 5f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Bg),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(driftX.roundToInt(), driftY.roundToInt()) }
                // One alpha for the whole face rather than a dimmed variant of every element:
                // ambient asks for fewer lit pixels, not a different design.
                .alpha(if (dimmed) 0.55f else 1f),
            contentAlignment = Alignment.Center,
        ) {
            StatusRing(state = state, t = seconds, calm = dimmed, modifier = Modifier.fillMaxSize())

            // Top arc: which project is running. Reads left-to-right over the top of the glass.
            CurvedLayout(
                modifier = Modifier.fillMaxSize().padding(11.dp),
                anchor = 270f,
                anchorType = AnchorType.Center,
                angularDirection = CurvedDirection.Angular.Clockwise,
            ) {
                curvedText(
                    text = topLine(state),
                    style = CurvedTextStyle(
                        fontSize = 13.sp,
                        color = if (state.online) TextHi else TextLow,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }

            // Bottom arc: how long it has been at it. Counter-clockwise so it stays upright.
            val bottom = bottomLine(state)
            if (bottom.isNotEmpty()) {
                CurvedLayout(
                    modifier = Modifier.fillMaxSize().padding(11.dp),
                    anchor = 90f,
                    anchorType = AnchorType.Center,
                    angularDirection = CurvedDirection.Angular.CounterClockwise,
                ) {
                    curvedText(
                        text = bottom,
                        style = CurvedTextStyle(fontSize = 12.sp, color = TextLow),
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                // The centre column only ever uses the inscribed square, which is what keeps it
                // clear of the ring and the curved text on a round display.
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 30.dp, vertical = 26.dp),
            ) {
                ClawdEyes(
                    state = state,
                    t = seconds,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.45f),
                )

                Spacer(Modifier.height(8.dp))
                Text(
                    text = ui?.label ?: "Connecting…",
                    color = when {
                        !state.online -> TextLow
                        ui?.kind == ClaudeKind.PERMISSION -> Ember
                        ui?.kind == ClaudeKind.WAITING -> Amber
                        else -> TextHi
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Top arc: the app's own name. The one fixed thing on the face — this is the identity. */
private fun topLine(state: WatchState): String =
    if (state.online) "CLAWD" else "OFFLINE"

/**
 * Bottom arc: which project, and how long this turn has been running.
 *
 * The project sits here rather than on the top arc because it is *status*, not identity — it
 * changes with whatever you happen to be working on, and it belongs next to the timer that
 * qualifies it.
 */
@Composable
private fun bottomLine(state: WatchState): String {
    val ui = state.ui ?: return ""
    if (!state.online) return "reconnecting…"

    val project = ui.project.ifBlank { "" }
    if (!ui.busy || ui.startedAt <= 0) return project

    // Re-tick once a second so the counter actually counts.
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(ui.busy) {
        while (ui.busy) {
            delay(1000)
            now = System.currentTimeMillis() / 1000
        }
    }

    val secs = (now - ui.startedAt).coerceAtLeast(0)
    val elapsed = if (secs >= 3600) {
        "${secs / 3600}h ${(secs % 3600) / 60}m"
    } else {
        "${secs / 60}:${(secs % 60).toString().padStart(2, '0')}"
    }
    return if (project.isBlank()) elapsed else "$project · $elapsed"
}
