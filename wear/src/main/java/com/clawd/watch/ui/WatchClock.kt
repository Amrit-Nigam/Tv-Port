package com.clawd.watch.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos

/**
 * One frame clock for the whole face, in seconds since the screen appeared.
 *
 * Every moving part — the eyes, the perimeter ring — derives its position analytically from this
 * single value rather than owning an Animatable. That keeps the animations inherently in phase and
 * means exactly one frame callback is running no matter how many things are moving.
 *
 * Returned as State so only the composables that actually read it recompose, not their parents.
 */
@Composable
fun rememberSeconds(running: Boolean = true): State<Float> {
    val seconds = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> seconds.floatValue = (now - start) / 1_000_000_000f }
        }
    }
    return seconds
}
