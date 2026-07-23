package com.larry.arrowgame.game

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Lightweight synthesized SFX (no asset files), similar to the Linux pygame port.
 * Safe to call from the main thread — playback is offloaded.
 */
class GameAudio(context: Context) {
    @Volatile
    var muted: Boolean = false

    private val sampleRate = 22050
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private var popI = 0

    fun playError() = playTones(listOf(180.0 to 90, 140.0 to 110), 0.32f)

    fun playSuccess() = playTones(listOf(440.0 to 60, 660.0 to 90), 0.22f)

    fun playCelebrate() = playTones(
        listOf(
            523.0 to 70, 659.0 to 70, 784.0 to 70,
            1046.0 to 100, 784.0 to 80, 1046.0 to 160,
        ),
        0.28f,
    )

    fun playTick(index: Int = 0) {
        val freqs = doubleArrayOf(
            392.0, 440.0, 493.9, 523.3, 587.3, 659.3,
            698.5, 784.0, 880.0, 987.8, 1046.5, 784.0,
        )
        val f = freqs[index.mod(freqs.size)]
        playTones(listOf(f to 55), 0.16f)
    }

    fun playPop() {
        val pitch = 180.0 + (popI % 8) * 40.0
        popI++
        if (muted) return
        thread(name = "arrow-sfx-pop", isDaemon = true) {
            try {
                playSamples(noisePop(55, pitch, 0.30f))
            } catch (_: Exception) {
                // Ignore audio device failures
            }
        }
    }

    private fun playTones(notes: List<Pair<Double, Int>>, volume: Float) {
        if (muted) return
        thread(name = "arrow-sfx", isDaemon = true) {
            try {
                playSamples(tone(notes, volume))
            } catch (_: Exception) {
                // Ignore audio device failures
            }
        }
    }

    private fun tone(notes: List<Pair<Double, Int>>, volume: Float): ShortArray {
        val samples = ArrayList<Short>()
        for ((freq, ms) in notes) {
            val n = maxOf(1, sampleRate * ms / 1000)
            val attack = min(40, maxOf(1, n / 5))
            val release = min(80, maxOf(1, n / 3))
            for (i in 0 until n) {
                var env = 1.0
                if (i < attack) env = i.toDouble() / attack
                else if (i > n - release) env = maxOf(0.0, (n - i).toDouble() / release)
                val v = (32767 * volume * env * sin(2 * PI * freq * i / sampleRate)).toInt()
                samples.add(v.coerceIn(-32767, 32767).toShort())
            }
        }
        return samples.toShortArray()
    }

    private fun noisePop(durationMs: Int, pitch: Double, volume: Float): ShortArray {
        val n = maxOf(1, sampleRate * durationMs / 1000)
        val samples = ShortArray(n)
        val rng = Random((pitch * 17).toInt() + 3)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / maxOf(1, n - 1)
            val env = exp(-6.5 * t) * if (i > 2) 1.0 else i / 2.0
            val noise = rng.nextDouble(-1.0, 1.0)
            phase += 2 * PI * pitch / sampleRate
            val thump = sin(phase)
            val mix = 0.72 * noise + 0.28 * thump
            samples[i] = (32767 * volume * env * mix).toInt().coerceIn(-32767, 32767).toShort()
        }
        return samples
    }

    private fun playSamples(samples: ShortArray) {
        if (samples.isEmpty()) return
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufSize = maxOf(minBuf, samples.size * 2)
        val track = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(bufSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        } else {
            @Suppress("DEPRECATION")
            AudioTrack(
                android.media.AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufSize,
                AudioTrack.MODE_STATIC,
            )
        }
        try {
            track.write(samples, 0, samples.size)
            track.play()
            // Hold until done (approx)
            val ms = samples.size * 1000L / sampleRate + 40L
            Thread.sleep(ms)
        } finally {
            try {
                track.stop()
            } catch (_: Exception) {
            }
            track.release()
        }
    }

    fun release() {
        // Tracks are released per-play; nothing global to hold.
    }
}
