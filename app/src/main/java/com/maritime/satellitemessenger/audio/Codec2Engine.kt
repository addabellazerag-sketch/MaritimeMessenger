package com.maritime.satellitemessenger.audio

class Codec2Engine {

    companion object {
        init {
            System.loadLibrary("maritime_audio")
        }
    }

    external fun createCodec(): Long
    external fun destroyCodec(handle: Long)
    external fun encode(handle: Long, pcmData: ShortArray): ByteArray
    external fun decode(handle: Long, compressedData: ByteArray): ShortArray
}
