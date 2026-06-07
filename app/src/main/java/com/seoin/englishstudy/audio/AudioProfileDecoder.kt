package com.seoin.englishstudy.audio

import android.content.res.AssetManager
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.seoin.englishstudy.model.AudioProfile
import com.seoin.englishstudy.model.Lesson
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.sqrt

internal class AudioProfileDecoder(private val assets: AssetManager) {
    fun decode(lesson: Lesson): AudioProfile {
        val audio = lesson.audioAssets[lesson.defaultAudioId] ?: lesson.audioAssets.values.first()
        val afd = assets.openFd("${lesson.basePath}/${audio.file}")
        val extractor = MediaExtractor()
        val codec: MediaCodec
        var sampleRate = 44_100
        try {
            extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("audio track not found")
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: error("audio mime not found")
            sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            extractor.selectTrack(trackIndex)
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()
        } finally {
            afd.close()
        }

        val windowMs = 20
        val samplesPerWindow = max(1, sampleRate * windowMs / 1000)
        val bufferInfo = MediaCodec.BufferInfo()
        val profile = mutableListOf<Float>()
        var sawInputEnd = false
        var sawOutputEnd = false
        var squareSum = 0.0
        var sampleCount = 0

        fun pushSample(value: Short) {
            val normalized = value.toDouble() / Short.MAX_VALUE.toDouble()
            squareSum += normalized * normalized
            sampleCount += 1
            if (sampleCount >= samplesPerWindow) {
                profile.add(sqrt(squareSum / sampleCount).toFloat())
                squareSum = 0.0
                sampleCount = 0
            }
        }

        try {
            while (!sawOutputEnd) {
                if (!sawInputEnd) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        val sampleSize = if (inputBuffer != null) extractor.readSampleData(inputBuffer, 0) else -1
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEnd = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                    else -> if (outputIndex >= 0) {
                        val outputBuffer = codec.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            val pcm = outputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
                            pcm.position(bufferInfo.offset)
                            pcm.limit(bufferInfo.offset + bufferInfo.size)
                            while (pcm.remaining() >= 2) pushSample(pcm.getShort())
                        }
                        sawOutputEnd = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
            if (sampleCount > 0) profile.add(sqrt(squareSum / sampleCount).toFloat())
        } finally {
            codec.stop()
            codec.release()
            extractor.release()
        }

        val durationMs = profile.size * windowMs
        return AudioProfile(profile.toFloatArray(), windowMs, durationMs)
    }
}
