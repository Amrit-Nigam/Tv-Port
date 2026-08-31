package com.clawd.watch.claude

import kotlinx.serialization.Serializable

/**
 * Mirrors ~/.claude/statusbar/state.json served by serve.py — the same payload the TV dashboard
 * consumes. state ∈ thinking | tool | done | waiting | permission | idle.
 */
@Serializable
data class ClaudeStateDto(
    val state: String = "idle",
    val label: String = "",
    val tool: String = "",
    val project: String = "",
    val startedAt: Long = 0,
    val ts: Long = 0,
)

enum class ClaudeKind { THINKING, TOOL, WAITING, PERMISSION, DONE, IDLE }

/** What the watch face renders. [busy] drives the "actively doing something" eye behaviour. */
data class ClaudeUi(
    val kind: ClaudeKind,
    val label: String,
    val project: String,
    val startedAt: Long, // epoch seconds, 0 if not timing
    val busy: Boolean,
)

/** The whole screen's input: the last known state plus whether the stream is currently alive. */
data class WatchState(
    val ui: ClaudeUi? = null,
    val online: Boolean = false,
)

fun ClaudeStateDto.toUi(): ClaudeUi {
    val kind = when (state.lowercase()) {
        "thinking" -> ClaudeKind.THINKING
        "tool" -> ClaudeKind.TOOL
        "waiting" -> ClaudeKind.WAITING
        "permission" -> ClaudeKind.PERMISSION
        "done" -> ClaudeKind.DONE
        else -> ClaudeKind.IDLE
    }
    val text = label.ifBlank {
        when (kind) {
            ClaudeKind.THINKING -> "Thinking…"
            ClaudeKind.TOOL -> "Working…"
            ClaudeKind.WAITING -> "Waiting for you"
            ClaudeKind.PERMISSION -> "Awaiting permission"
            ClaudeKind.DONE -> "Done"
            ClaudeKind.IDLE -> "Idle"
        }
    }
    return ClaudeUi(
        kind = kind,
        label = text,
        project = project,
        startedAt = startedAt,
        busy = kind == ClaudeKind.THINKING || kind == ClaudeKind.TOOL,
    )
}

/** True for the states that deserve a buzz on the wrist — Claude is blocked on you. */
val ClaudeKind.needsYou: Boolean
    get() = this == ClaudeKind.WAITING || this == ClaudeKind.PERMISSION
