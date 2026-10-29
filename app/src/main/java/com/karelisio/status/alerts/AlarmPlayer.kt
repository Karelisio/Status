package com.karelisio.status.alerts

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Plays alert sound / vibration on the ALARM usage so it is heard even in silent or vibrate
 * ringer mode (alarm volume is independent from the ringer).
 */
object AlarmPlayer {
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    fun play(context: Context, sound: Boolean, vibrate: Boolean, durationMs: Long = 4_000) {
        if (vibrate) vibrate(context)
        if (!sound) return
        stop()
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return
        runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(context.applicationContext, uri)
                isLooping = true
                setOnPreparedListener { it.start() }
                prepareAsync()
            }
            handler.postDelayed({ stop() }, durationMs)
        }
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        player?.runCatching { stop(); release() }
        player = null
    }

    private fun vibrate(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        if (!vibrator.hasVibrator()) return
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 350, 180, 350, 180, 650), -1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
    }
}
