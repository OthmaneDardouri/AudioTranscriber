package com.othmane.vocaleai.detect

object VoiceDetect {
    val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")

    private const val MIC = "🎤" // 🎤
    private val PATTERNS = Regex(
        "messaggio vocale|messaggio audio|voice message|audio message|" +
            "رسالة صوتية|رسالة صوتيه|mensaje de voz|message vocal|sprachnachricht|spraakbericht",
        RegexOption.IGNORE_CASE,
    )

    /** Il testo di una notifica WhatsApp è tipo "🎤 Messaggio vocale (0:12)". */
    fun isVoice(text: CharSequence?): Boolean {
        if (text.isNullOrBlank()) return false
        val s = text.toString()
        return s.contains(MIC) || PATTERNS.containsMatchIn(s)
    }
}
