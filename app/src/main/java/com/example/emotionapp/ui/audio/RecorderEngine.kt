package com.example.emotionapp.ui.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodec.BufferInfo
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Process
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
import kotlin.math.pow

/**
 * Motor de grabación:
 * - Captura con AudioRecord (VOICE_COMMUNICATION si es posible; si no, MIC).
 * - Activa AGC/NS si están disponibles para el audioSessionId.
 * - Aplica refuerzo en dB al PCM (0 / 6 / 9 / 12 / 18…).
 * - Codifica a AAC LC (.m4a) con MediaCodec + MediaMuxer.
 *
 * Nota: refuerzos altos (+12/+18) pueden saturar; se aplica clamp a 16-bit
 * para evitar desbordes, pero puede oírse “apretado”. Úsalo solo si la fuente es baja.
 */
class AudioRecorderEngine(
    private val context: Context,
    private val sampleRate: Int = 44_100,
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
     * @param boostDb Ganancia en dB (p.ej. 0, 6, 9, 12, 18). Default 9 dB.
     */
    @Throws(Exception::class)
    fun start(outputFile: File, boostDb: Int = 9) {
        // Por si llaman dos veces seguidas
        stop()

        // ---- Config de captura ----
        val channelConfig = if (channelCount == 1)
            AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT

        var minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBuf <= 0) minBuf = 4096
        // Un poco más de margen para evitar underruns
        val bufferSize = (minBuf * 2).coerceAtLeast(8192)

        // Intenta VOICE_COMMUNICATION; si falla, usa MIC
        val trySources = intArrayOf(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC
        )

        var record: AudioRecord? = null
        var initOk = false
        for (src in trySources) {
            runCatching {
                val r = AudioRecord(
                    src,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
                if (r.state == AudioRecord.STATE_INITIALIZED) {
                    record = r
                    initOk = true
                } else {
                    r.release()
                }
            }
            if (initOk) break
        }
        check(initOk && record != null) { "AudioRecord no inicializado (verifica permisos/SDK)." }
        audioRecord = record

        // Efectos (si disponibles)
        releaseEffects() // por si acaso
        if (AutomaticGainControl.isAvailable()) {
            agc = AutomaticGainControl.create(record!!.audioSessionId)?.apply { enabled = true }
        }
        if (NoiseSuppressor.isAvailable()) {
            ns = NoiseSuppressor.create(record!!.audioSessionId)?.apply { enabled = true }
        }

        // ---- Encoder AAC LC ----
        val mime = MediaFormat.MIMETYPE_AUDIO_AAC
        val format = MediaFormat.createAudioFormat(mime, sampleRate, channelCount).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, aacBitrate)
            // Tamaño de input razonable; el codec internamente gestiona colas
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferSize)
        }
        val enc = MediaCodec.createEncoderByType(mime)
        enc.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        enc.start()
        codec = enc

        // ---- Muxer MP4/M4A ----
        val m = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        muxer = m
        muxerTrack = -1
        totalSamplesSubmitted = 0

        // ---- Bucle de captura/codificación en hilo dedicado ----
        running.set(true)
        recordingThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)

            val ar = audioRecord!!
            ar.startRecording()

            val gain = dbToLinear(boostDb) // factor lineal
            val inShorts = ShortArray(bufferSize / 2) // 16-bit PCM
            val encRef = codec!!

            try {
                while (running.get()) {
                    val read = ar.read(inShorts, 0, inShorts.size)
                    if (read <= 0) continue

                    // Aplica ganancia + clamp para evitar overflow
                    if (gain != 1f) applyGainInPlace(inShorts, read, gain)

                    // Empuja al encoder en trozos según quepa el input buffer
                    var offset = 0
                    while (offset < read) {
                        val idxIn = encRef.dequeueInputBuffer(10_000)
                        if (idxIn >= 0) {
                            val inBuf: ByteBuffer? = encRef.getInputBuffer(idxIn)
                            if (inBuf != null) {
                                inBuf.clear()
                                val shortsCap = (inBuf.remaining() / 2)
                                val toCopyShorts = min(shortsCap, read - offset)

                                // Copia short[] -> ByteBuffer (vista de short)
                                inBuf.asShortBuffer().put(inShorts, offset, toCopyShorts)

                                val bytesThis = toCopyShorts * 2
                                val samplesThis = toCopyShorts
                                val ptsUs = totalSamplesSubmitted * ptsUsPerSample

                                // IMPORTANTE: al usar asShortBuffer(), la posición del ByteBuffer no avanza.
                                // Por eso pasamos bytesThis al queue y leave offset 0.
                                encRef.queueInputBuffer(idxIn, 0, bytesThis, ptsUs, 0)

                                totalSamplesSubmitted += samplesThis
                                offset += toCopyShorts
                            }
                        }
                        // Saca lo que vaya produciendo el encoder
                        drainEncoder(encRef, m)
                    }
                }

                // Señal de fin de stream
                val idxIn = encRef.dequeueInputBuffer(10_000)
                if (idxIn >= 0) {
                    encRef.queueInputBuffer(
                        idxIn,
                        0,
                        0,
                        totalSamplesSubmitted * ptsUsPerSample,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                }
                drainEncoder(encRef, m, endOfStream = true)

            } catch (_: Exception) {
                // Silenciamos; se limpia en finally
            } finally {
                runCatching { ar.stop() }
                runCatching { ar.release() }
                audioRecord = null
                releaseEffects()
                runCatching { encRef.stop() }
                runCatching { encRef.release() }
                codec = null
                runCatching { if (muxerTrack != -1) m.stop() }
                runCatching { m.release() }
                muxer = null
            }
        }, "AudioRecorderEngine").also { it.start() }
    }

    /** Detiene la grabación y libera recursos (bloquea hasta 1s para cierre ordenado). */
    fun stop() {
        running.set(false)
        recordingThread?.join(1_000)
        recordingThread = null
        // El resto se libera en el finally del hilo.
    }

    /* ----------------- Internos ----------------- */

    private fun drainEncoder(enc: MediaCodec, m: MediaMuxer, endOfStream: Boolean = false) {
        val outInfo = BufferInfo()
        while (true) {
            val outIndex = enc.dequeueOutputBuffer(outInfo, 10_000)
            when {
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) break else continue
                }
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
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
                    if ((outInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                }
            }
        }
    }

    private fun releaseEffects() {
        runCatching { agc?.enabled = false }
        runCatching { agc?.release() }
        runCatching { ns?.enabled = false }
        runCatching { ns?.release() }
        agc = null
        ns = null
    }

    private fun dbToLinear(db: Int): Float = 10f.pow(db / 20f)

    private fun applyGainInPlace(samples: ShortArray, count: Int, gain: Float) {
        // Refuerzo sencillo con clamp a [-32768, 32767]
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
