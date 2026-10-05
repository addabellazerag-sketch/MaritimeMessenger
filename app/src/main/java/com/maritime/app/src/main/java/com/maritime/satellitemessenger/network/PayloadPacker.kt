package com.maritime.satellitemessenger.network

import java.nio.ByteBuffer
import java.nio.ByteOrder

object PayloadPacker {

    // الهيكل: 4 بايت خط العرض + 4 بايت خط الطول + 8 بايت التوقيت + بيانات الصوت المضغوطة
    fun pack(lat: Float, lon: Float, audio: ByteArray): ByteArray {
        val timestamp = System.currentTimeMillis()
        val buffer = ByteBuffer.allocate(4 + 4 + 8 + audio.size).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            putFloat(lat)
            putFloat(lon)
            putLong(timestamp)
            put(audio)
        }
        return buffer.array()
    }

    fun unpack(bytes: ByteArray): MaritimeMessage {
        val buffer = ByteBuffer.wrap(bytes).apply {
            order(ByteOrder.LITTLE_ENDIAN)
        }
        val lat = buffer.getFloat()
        val lon = buffer.getFloat()
        val timestamp = buffer.getLong()
        val audioSize = bytes.size - 16
        val audio = ByteArray(audioSize)
        buffer.get(audio)

        return MaritimeMessage(
            latitude = lat,
            longitude = lon,
            audioData = audio,
            timestamp = timestamp
        )
    }
}
