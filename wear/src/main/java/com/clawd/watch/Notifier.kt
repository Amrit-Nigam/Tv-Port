package com.clawd.watch

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.clawd.watch.claude.ClaudeUi

/**
 * Buzzes the wrist when Claude becomes blocked on you. This is the point of wearing it: you can
 * walk away from the desk and still know the moment a run needs an answer.
 *
 * Only *transitions* into a needs-you state fire — MainActivity holds the previous kind — so a run
 * that sits waiting for ten minutes buzzes once, not continuously.
 */
class Notifier(private val context: Context) {

    fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Claude needs you",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Fires when a Claude Code run is waiting on your input or a permission."
            enableVibration(true)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun notifyNeedsYou(ui: ClaudeUi) {
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(ui.label)
            .setContentText(ui.project.ifBlank { "Claude Code" })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted — the vibration below still lands, which is the part
            // that actually gets your attention on a watch.
        }
        buzz()
    }

    /** A double-tap pattern: distinct from the single pulse the system uses for most things. */
    private fun buzz() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        } ?: return
        val pattern = longArrayOf(0, 90, 110, 90)
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    private companion object {
        const val CHANNEL_ID = "claude_needs_you"
        const val NOTIFICATION_ID = 1
    }
}
