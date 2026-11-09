package com.othmane.vocaleai.whisper

fun interface ProgressListener {
    fun onProgress(percent: Int)
}

/** Funzioni native definite in whisper_jni.cpp. */
object WhisperLib {
    init {
        System.loadLibrary("whisperjni")
    }

    external fun initContext(modelPath: String): Long
    external fun freeContext(ctx: Long)
    external fun transcribe(
        ctx: Long,
        audio: FloatArray,
        threads: Int,
        language: String,
        listener: ProgressListener?,
    ): ByteArray?

    external fun detectedLanguage(ctx: Long): String
    external fun systemInfo(): String
}
