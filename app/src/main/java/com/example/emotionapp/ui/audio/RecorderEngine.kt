package com.example.emotionapp.ui.audio

import android.content.Context
import android.media.*
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.media.MediaCodec.*
import android.media.MediaFormat
import android.os.Build
import android.os.Process
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Graba con AudioRecord (VOICE_COMMUNICATION) + AGC/NS (si disponibles),
 * aplica un refuerzo en dB al PCM, y codifica a AAC (.m4a) con MediaCodec/Muxer.
 *
 * Resultado: un archivo .m4a que suena más alto en cualquier reproductor, sin FFmpeg.
 */
class AudioRecorderEngine(
    private val context: Context,
    private val sampleRate: Int = 44100,
    private val channelCount: Int = 1,
    private val aacBitrate: Int = 128_000
) {

    private var audioRecord: AudioRecord? = null
    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var muxerTrack: Int = -1
    private var recordingThread: Thread? = null
    private val running = AtomicBoolean(false)

    private var agc: AutomaticGainControl? = null
    private var ns: NoiseSuppressor? = null

    private var ptsUsPerSample: Long = 1_000_000L / sampleRate
    private var totalSamplesSubmitted: Long = 0

    /**
     * Inicia la grabación hacia [outputFile].
     * @param boostDb 0, 6 o 9 dB (recomendado 6 como default).
     */
    @Throws(Exception::class)
    fun start(outputFile: File, boostDb: Int = 6) {
        stop() // por si acaso

        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            .coerceAtLeast(4096)

        // AudioRecord con VOICE_COMMUNICATION para aprovechar AGC del sistema
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            sampleRate,
            channelConfig,
            audioFormat,
            minBuf
        )
        check(record.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord no inicializado" }
        audioRecord = record

        // Efectos (si disponibles)
        if (AutomaticGainControl.isAvailable()) {
            agc = AutomaticGainControl.create(record.audioSessionId)?.apply { enabled = true }
        }
        if (NoiseSuppressor.isAvailable()) {
            ns = NoiseSuppressor.create(record.audioSessionId)?.apply { enabled = true }
        }

        // Encoder AAC
        val mime = MediaFormat.MIMETYPE_AUDIO_AAC
        val format = MediaFormat.createAudioFormat(mime, sampleRate, channelCount).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, aacBitrate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, minBuf)
        }
        val enc = MediaCodec.createEncoderByType(mime)
        enc.configure(format, null, null, CONFIGURE_FLAG_ENCODE)
        enc.start()
        codec = enc

        // Muxer MP4/M4A
        val m = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        muxer = m
        muxerTrack = -1
        totalSamplesSubmitted = 0

        running.set(true)
        recordingThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            record.startRecording()
            val gain = dbToLinear(boostDb) // lineal
            val inBuf = ShortArray(minBuf / 2) // 16-bit -> 2 bytes
            val inputInfo = BufferInfo()

            try {
                loop@ while (running.get()) {
                    val read = record.read(inBuf, 0, inBuf.size)
                    if (read <= 0) continue

                    // Aplica refuerzo moderado + limitador por clamp
                    if (gain != 1f) applyGainInPlace(inBuf, read, gain)

                    // Enviar chunks al encoder
                    var offset = 0
                    while (offset < read) {
                        val inIndex = enc.dequeueInputBuffer(10_000)
                        if (inIndex >= 0) {
                            val buf: ByteBuffer? = enc.getInputBuffer(inIndex)
                            buf?.clear()
                            // copiar short -> bytebuffer
                            val toCopy = min(buf?.remaining() ?: 0, (read - offset) * 2)
                            buf?.let {
                                it.asShortBuffer().put(inBuf, offset, toCopy / 2)
                                val samplesThis = toCopy / 2
                                val ptsUs = totalSamplesSubmitted * ptsUsPerSample
                                inputInfo.set(0, toCopy, ptsUs, 0)
                                enc.queueInputBuffer(inIndex, 0, toCopy, ptsUs, 0)
                                totalSamplesSubmitted += samplesThis
                                offset += samplesThis
                            }
                        }
                        // sacar salida y escribir al muxer
                        drainEncoder(enc, m)
                    }
                }

                // Fin de stream
                val inIndex = enc.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    enc.queueInputBuffer(inIndex, 0, 0, totalSamplesSubmitted * ptsUsPerSample, BUFFER_FLAG_END_OF_STREAM)
                }
                drainEncoder(enc, m, endOfStream = true)

            } catch (_: Exception) {
                // Silenciar fallos en hilo; se limpian en finally
            } finally {
                try { record.stop() } catch (_: Exception) {}
                record.release()
                releaseEffects()
                try { enc.stop() } catch (_: Exception) {}
                try { enc.release() } catch (_: Exception) {}
                try { if (muxerTrack != -1) m.stop() } catch (_: Exception) {}
                try { m.release() } catch (_: Exception) {}
            }
        }, "AudioRecorderEngine").also { it.start() }
    }

    fun stop() {
        running.set(false)
        recordingThread?.join(1_000)
        recordingThread = null
        // el resto se libera en el finally del hilo
    }

    private fun drainEncoder(enc: MediaCodec, m: MediaMuxer, endOfStream: Boolean = false) {
        val outInfo = BufferInfo()
        while (true) {
            val outIndex = enc.dequeueOutputBuffer(outInfo, 10_000)
            when {
                outIndex == INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) break else continue
                }
                outIndex == INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (muxerTrack != -1) return
                    muxerTrack = m.addTrack(enc.outputFormat)
                    m.start()
                }
                outIndex >= 0 -> {
                    val outBuf = enc.getOutputBuffer(outIndex) ?: return
                    if (outInfo.size > 0 && muxerTrack != -1) {
                        outBuf.position(outInfo.offset)
                        outBuf.limit(outInfo.offset + outInfo.size)
                        m.writeSampleData(muxerTrack, outBuf, outInfo)
                    }
                    enc.releaseOutputBuffer(outIndex, false)
                    if ((outInfo.flags and BUFFER_FLAG_END_OF_STREAM) != 0) break
                }
            }
        }
    }

    private fun releaseEffects() {
        try { agc?.enabled = false } catch (_: Exception) {}
        try { agc?.release() } catch (_: Exception) {}
        try { ns?.enabled = false } catch (_: Exception) {}
        try { ns?.release() } catch (_: Exception) {}
        agc = null
        ns = null
    }

    private fun dbToLinear(db: Int): Float = 10f.pow(db / 20f)

    private fun applyGainInPlace(samples: ShortArray, count: Int, gain: Float) {
        // refuerzo moderado con clamp a [-32768, 32767]
        for (i in 0 until count) {
            val v = (samples[i] * gain).toInt()
            samples[i] = when {
                v > Short.MAX_VALUE.toInt() -> Short.MAX_VALUE
                v < Short.MIN_VALUE.toInt() -> Short.MIN_VALUE
                else -> v.toShort()
            }
        }
    }
}
