package com.zaitxcode.opencut.core.export

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import com.zaitxcode.opencut.core.model.Project
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

/**
 * Mixes the audio layers of a project and encodes the result with AAC.
 *
 * Every audio segment is decoded to 16 bit PCM, resampled to a common sample rate,
 * mixed into a stereo buffer and finally encoded into the same MP4 container as the
 * video track.
 */
class AudioMixer(private val context: Context) {

    private val sampleRate = 44_100
    private val channels = 2

    /**
     * Encodes the project's audio.
     *
     * The audio is encoded before the container is started, because a muxer only
     * accepts a new track while it has not been started yet. The result carries the
     * output format of the encoder and the encoded samples, so that the exporter
     * can add the track and write the samples in the right order.
     */
    fun encode(
        project: Project,
        durationUs: Long,
    ): EncodedAudio? {
        val plans = audioPlans(project)
        if (plans.isEmpty()) return null

        val totalSamples = maxOf(1, (durationUs * sampleRate / 1_000_000L).toInt())
        val mixed = ShortArray(totalSamples * channels)

        for (plan in plans) {
            val pcm = decodeToPcm(plan.uri) ?: continue
            val gain = (plan.volume * 0.8f).coerceIn(0f, 4f)
            val offsetSamples = (plan.startUs * sampleRate / 1_000_000L).toInt()
            var index = 0
            while (index < pcm.size / 2 && offsetSamples + index / 2 < totalSamples) {
                val target = (offsetSamples + index / 2) * 2 + (index % 2)
                val sample = (pcm[index] * gain).toInt().coerceIn(-32768, 32767)
                mixed[target] = (mixed[target] + sample).coerceIn(-32768, 32767).toShort()
                index++
            }
        }

        return encodeAac(mixed, totalSamples)
    }

    /** One audio layer of the project. */
    private data class AudioPlan(
        val uri: Uri,
        val startUs: Long,
        val volume: Float,
        val muted: Boolean,
    )

    private fun audioPlans(project: Project): List<AudioPlan> {
        val plans = mutableListOf<AudioPlan>()
        for (track in project.tracks) {
            if (track.type != com.zaitxcode.opencut.core.model.TrackType.AUDIO) continue
            for (segment in track.segments) {
                if (segment.muted) continue
                val material = project.materials.find(segment.materialId) ?: continue
                if (material.path.isBlank()) continue
                plans += AudioPlan(
                    uri = Uri.parse(material.path),
                    startUs = segment.target.start,
                    volume = segment.volume,
                    muted = segment.muted,
                )
            }
        }
        return plans
    }

    /**
     * Decodes the audio track of a file into 16 bit PCM.
     *
     * The decoder output is converted to the target sample rate with linear
     * interpolation, which keeps the mixer simple while avoiding obvious pitch
     * artefacts.
     */
    private fun decodeToPcm(uri: Uri): ShortArray? = runCatching {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, emptyMap())
        var trackIndex = -1
        var format: MediaFormat? = null
        for (index in 0 until extractor.trackCount) {
            val candidate = extractor.getTrackFormat(index)
            val mime = candidate.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("audio/")) {
                trackIndex = index
                format = candidate
                break
            }
        }
        if (trackIndex < 0 || format == null) {
            extractor.release()
            return null
        }
        extractor.selectTrack(trackIndex)

        val sourceRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()

        val output = ArrayList<Short>()
        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false

        while (true) {
            if (!inputDone) {
                val inputIndex = codec.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)
                    val size = if (inputBuffer != null) extractor.readSampleData(inputBuffer, 0) else -1
                    if (size < 0) {
                        codec.queueInputBuffer(
                            inputIndex,
                            0,
                            0,
                            0L,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                        )
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
            if (outputIndex >= 0) {
                val buffer = codec.getOutputBuffer(outputIndex)
                if (buffer != null && bufferInfo.size > 0) {
                    buffer.position(bufferInfo.offset)
                    buffer.limit(bufferInfo.offset + bufferInfo.size)
                    val shorts = buffer.order(ByteOrder.nativeOrder()).asShortBuffer()
                    while (shorts.hasRemaining()) output.add(shorts.get())
                }
                codec.releaseOutputBuffer(outputIndex, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            } else if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER && inputDone) {
                break
            }
        }
        codec.stop()
        codec.release()
        extractor.release()

        val raw = ShortArray(output.size) { output[it] }
        if (sourceRate == sampleRate) raw else resample(raw, sourceRate)
    }.getOrNull()

    /** Linear resampling of interleaved 16 bit PCM. */
    private fun resample(input: ShortArray, sourceRate: Int): ShortArray {
        if (input.isEmpty() || sourceRate <= 0) return input
        val sourceFrames = input.size / channels
        val targetFrames = maxOf(1L, sourceFrames.toLong() * sampleRate / sourceRate).toInt()
        val output = ShortArray(targetFrames * channels)
        for (frame in 0 until targetFrames) {
            val sourcePosition = frame.toDouble() * sourceRate / sampleRate
            val index = sourcePosition.toInt().coerceIn(0, maxOf(0, sourceFrames - 1))
            val next = minOf(index + 1, maxOf(0, sourceFrames - 1))
            val fraction = (sourcePosition - index).toFloat()
            for (channel in 0 until channels) {
                val a = input[index * channels + channel].toFloat()
                val b = input[next * channels + channel].toFloat()
                output[frame * channels + channel] = (a + (b - a) * fraction).toInt().toShort()
            }
        }
        return output
    }

    /** Encodes the mixed PCM with AAC. */
    private fun encodeAac(pcm: ShortArray, sampleCount: Int): EncodedAudio? {
        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            sampleRate,
            channels,
        ).apply {
            setInteger(
                MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC,
            )
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val bufferInfo = MediaCodec.BufferInfo()
        var outputFormat: MediaFormat? = null
        val samples = mutableListOf<EncodedSample>()
        var sampleOffset = 0

        var inputDone = false
        while (true) {
            if (!inputDone) {
                val inputIndex = encoder.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    val inputBuffer = encoder.getInputBuffer(inputIndex)
                    if (inputBuffer != null && sampleOffset < sampleCount) {
                        val chunkSamples = minOf(inputBuffer.capacity() / 2 / channels, sampleCount - sampleOffset)
                        val bytes = ByteBuffer.allocate(chunkSamples * channels * 2)
                            .order(ByteOrder.nativeOrder())
                        for (i in 0 until chunkSamples * channels) {
                            bytes.putShort(pcm[sampleOffset * channels + i])
                        }
                        val data = bytes.array()
                        inputBuffer.clear()
                        inputBuffer.put(data)
                        encoder.queueInputBuffer(
                            inputIndex,
                            0,
                            data.size,
                            sampleOffset * 1_000_000L / sampleRate,
                            0,
                        )
                        sampleOffset += chunkSamples
                    } else {
                        // The end of the stream lets the encoder flush the packets
                        // it still holds, so the track is not cut short.
                        encoder.queueInputBuffer(
                            inputIndex,
                            0,
                            0,
                            0L,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                        )
                        inputDone = true
                    }
                }
            }
            val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                outputFormat = encoder.outputFormat
                continue
            }
            if (outputIndex >= 0) {
                val encoded = encoder.getOutputBuffer(outputIndex)
                if (encoded != null && bufferInfo.size > 0) {
                    encoded.position(bufferInfo.offset)
                    encoded.limit(bufferInfo.offset + bufferInfo.size)
                    val copy = ByteArray(bufferInfo.size)
                    encoded.get(copy)
                    samples += EncodedSample(
                        data = copy,
                        presentationTimeUs = bufferInfo.presentationTimeUs,
                        flags = bufferInfo.flags,
                    )
                }
                encoder.releaseOutputBuffer(outputIndex, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
        }
        encoder.stop()
        encoder.release()
        val encodedFormat = outputFormat ?: return null
        return EncodedAudio(format = encodedFormat, samples = samples)
    }
}

/** The encoded audio of a project, ready to be written into a container. */
data class EncodedAudio(
    val format: MediaFormat,
    val samples: List<EncodedSample>,
)

/** One encoded audio packet. */
data class EncodedSample(
    val data: ByteArray,
    val presentationTimeUs: Long,
    val flags: Int,
)
