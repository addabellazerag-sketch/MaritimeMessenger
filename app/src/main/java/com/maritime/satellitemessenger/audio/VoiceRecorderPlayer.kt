package com.maritime.satellitemessenger.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class VoiceRecorderPlayer(private val codec: Codec2Engine) {

    private val sampleRate = 8000
    private val channelConfigIn = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfigIn, audioFormat)

    @Volatile
    private var isRecording = false
    private var codecHandle: Long = 0
    private val outputStream = ByteArrayOutputStream()

    @SuppressLint("MissingPermission")
    suspend fun recordAndCompress() = withContext(Dispatchers.IO) {
        outputStream.reset()
        codecHandle = codec.createCodec()
        isRecording = true

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfigIn,
            audioFormat,
            bufferSize
        )

        val audioBuffer = ShortArray(320)
        recorder.startRecording()

        while (isRecording) {
            val read = recorder.read(audioBuffer, 0, audioBuffer.size)
            if (read > 0) {
                val encodedBytes = codec.encode(codecHandle, audioBuffer)
                outputStream.write(encodedBytes)
            }
        }

        recorder.stop()
        recorder.release()
        codec.destroyCodec(codecHandle)
    }

    fun stopAndGetRecording(): ByteArray {
        isRecording = false
        return outputStream.toByteArray()
    }

    suspend fun playCompressedAudio(compressedData: ByteArray) = withContext(Dispatchers.IO) {
        val playHandle = codec.createCodec()
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .build()

        track.play()

        val chunkSize = 8
        var offset = 0
        while (offset + chunkSize <= compressedData.size) {
            val chunk = compressedData.copyOfRange(offset, offset + chunkSize)
            val pcmOut = codec.decode(playHandle, chunk)
            track.write(pcmOut, 0, pcmOut.size)
            offset += chunkSize
        }

        track.stop()
        track.release()
        codec.destroyCodec(playHandle)
    }
}
