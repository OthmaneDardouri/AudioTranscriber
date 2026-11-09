package com.othmane.vocaleai.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder

/**
 * Decodifica un vocale (Ogg/Opus di WhatsApp, ma anche m4a/mp3/…) in PCM float mono a 16 kHz,
 * il formato che si aspetta Whisper.
 */
object AudioDecoder {
    const val TARGET_RATE = 16_000

    fun decodeToMono16k(context: Context, uri: Uri): FloatArray {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IllegalArgumentException("Il file non contiene audio.")
            extractor.selectTrack(track)
            val inFormat = extractor.getTrackFormat(track)
            val mime = inFormat.getString(MediaFormat.KEY_MIME)!!

            var sampleRate = inFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = inFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var floatPcm = false

            val codec = MediaCodec.createDecoderByType(mime)
            val mono = FloatBuffer()
            try {
                codec.configure(inFormat, null, null, 0)
                codec.start()
                val info = MediaCodec.BufferInfo()
                var inputDone = false
                var outputDone = false
                while (!outputDone) {
                    if (!inputDone) {
                        val inIdx = codec.dequeueInputBuffer(10_000)
                        if (inIdx >= 0) {
                            val buf = codec.getInputBuffer(inIdx)!!
                            val size = extractor.readSampleData(buf, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                    when {
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val f = codec.outputFormat
                            sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                                f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                        }
                        outIdx >= 0 -> {
                            if (info.size > 0) {
                                val out = codec.getOutputBuffer(outIdx)!!
                                out.position(info.offset)
                                out.limit(info.offset + info.size)
                                out.order(ByteOrder.nativeOrder())
                                val ch = channels.coerceAtLeast(1)
                                if (floatPcm) {
                                    val fb = out.asFloatBuffer()
                                    val frames = fb.remaining() / ch
                                    for (i in 0 until frames) {
                                        var sum = 0f
                                        for (c in 0 until ch) sum += fb.get()
                                        mono.add(sum / ch)
                                    }
                                } else {
                                    val sb = out.asShortBuffer()
                                    val frames = sb.remaining() / ch
                                    for (i in 0 until frames) {
                                        var sum = 0f
                                        for (c in 0 until ch) sum += sb.get() / 32768f
                                        mono.add(sum / ch)
                                    }
                                }
                            }
                            codec.releaseOutputBuffer(outIdx, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        }
                    }
                }
            } finally {
                try { codec.stop() } catch (_: Exception) {}
                codec.release()
            }
            val pcm = mono.toArray()
            if (pcm.isEmpty()) throw IllegalStateException("Audio vuoto o non decodificabile.")
            return Resampler.resample(pcm, sampleRate, TARGET_RATE)
        } finally {
            extractor.release()
        }
    }

    /** Array di float che cresce senza boxing. */
    private class FloatBuffer {
        private var data = FloatArray(16_000 * 30)
        private var size = 0
        fun add(v: Float) {
            if (size == data.size) data = data.copyOf(data.size * 2)
            data[size++] = v
        }
        fun toArray(): FloatArray = data.copyOf(size)
    }
}
