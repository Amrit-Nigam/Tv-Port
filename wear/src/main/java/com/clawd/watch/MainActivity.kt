package com.clawd.watch

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.ambient.AmbientLifecycleObserver
import com.clawd.watch.claude.ClaudeKind
import com.clawd.watch.claude.ClaudeRepository
import com.clawd.watch.claude.WatchState
import com.clawd.watch.claude.needsYou
import com.clawd.watch.ui.ClawdScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Single-screen always-on Claude Code monitor for Wear OS 3.
 *
 * Built for a watch that lives on its charger as a desk display, which on Wear OS means leaning on
 * ambient mode rather than trying to defeat it. Measured on the actual Gen 6:
 *
 *   with AmbientLifecycleObserver     the activity stays on screen indefinitely, dimmed
 *   without it                        the system dozes and REPLACES us with the watch face
 *
 * FLAG_KEEP_SCREEN_ON alone does not hold a docked Gen 6 awake, so ambient is the only thing that
 * keeps this face up around the clock. We therefore register for it and render a complete, legible
 * face in ambient — dimmed and frozen, but not stripped — instead of treating ambient as an
 * afterthought. The flag is still set because it keeps the screen bright (and the Wi-Fi radio, and
 * therefore the SSE connection, awake) whenever the watch is genuinely interactive.
 *
 * Off the charger this will flatten the battery in a few hours. That is the trade it exists to make.
 */
class MainActivity : ComponentActivity() {

    private val repo = ClaudeRepository()
    private val state = MutableStateFlow(WatchState())
    private lateinit var notifier: Notifier

    // isAmbient plus a tick the system bumps roughly once a minute. The tick is what lets the
    // burn-in drift keep creeping while ambient — without it the composition would be frozen at
    // one offset for as long as the watch sits docked, which is the exact thing drift prevents.
    private val ambient = mutableStateOf(AmbientUi())

    // Last kind we saw, so we buzz on the *transition* into waiting/permission rather than on
    // every heartbeat that repeats the same state.
    private var lastKind: ClaudeKind? = null

    private val ambientCallback = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
            ambient.value = AmbientUi(isAmbient = true, tick = ambient.value.tick + 1)
        }

        override fun onExitAmbient() {
            ambient.value = AmbientUi(isAmbient = false, tick = ambient.value.tick + 1)
        }

        override fun onUpdateAmbient() {
            ambient.value = ambient.value.copy(tick = ambient.value.tick + 1)
        }
    }

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* buzz works regardless */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycle.addObserver(AmbientLifecycleObserver(this, ambientCallback))
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        notifier = Notifier(this)
        notifier.ensureChannel()
        requestNotificationsIfNeeded()

        setContent {
            val watchState by state.collectAsState()
            ClawdScreen(state = watchState, ambient = ambient.value)
        }

        // The stream is bound to STARTED: it drops when you swipe away and reconnects when you
        // come back, so a backgrounded app isn't holding a socket open for nothing.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repo.stream().collect { next ->
                    state.value = next
                    onStateChanged(next)
                }
            }
        }
    }

    private fun onStateChanged(next: WatchState) {
        val ui = next.ui
        val kind = ui?.kind
        if (next.online && ui != null && kind!!.needsYou && lastKind != kind) {
            notifier.notifyNeedsYou(ui)
        }
        // Only track kinds we actually received; a disconnect shouldn't re-arm the buzzer.
        if (next.online && kind != null) lastKind = kind
    }

    private fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

/** Ambient state for the UI: whether we are dimmed, and a counter the system bumps ~once a minute. */
data class AmbientUi(val isAmbient: Boolean = false, val tick: Int = 0)
