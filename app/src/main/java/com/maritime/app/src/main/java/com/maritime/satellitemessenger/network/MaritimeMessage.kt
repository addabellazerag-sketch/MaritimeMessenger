package com.maritime.satellitemessenger.network

data class MaritimeMessage(
    val latitude: Float,
    val longitude: Float,
    val audioData: ByteArray,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MaritimeMessage

        if (latitude != other.latitude) return false
        if (longitude != other.longitude) return false
        if (!audioData.contentEquals(other.audioData)) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = latitude.hashCode()
        result = 31 * result + longitude.hashCode()
        result = 31 * result + audioData.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}
