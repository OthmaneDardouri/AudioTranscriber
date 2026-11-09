package com.othmane.vocaleai.text

import kotlin.math.ceil
import kotlin.math.sqrt

data class Summary(
    /** Frasi chiave nell'ordine originale; vuota se il messaggio è già breve. */
    val keySentences: List<String>,
    val keywords: List<String>,
)

/**
 * Riassunto estrattivo, 100% offline: sceglie le frasi più rappresentative del vocale
 * in base alla frequenza delle parole significative (italiano, inglese, arabo/darija).
 * Non riscrive il testo: seleziona le frasi che contano.
 */
object Summarizer {
    private val WORD = Regex("[\\p{L}\\p{N}]+(?:'[\\p{L}]+)?")
    private val SENTENCE_SPLIT = Regex("(?<=[.!?؟…])\\s+|\\n+")

    private val STOP: Set<String> = (
        // italiano
        "a ad al allo ai agli all alla alle anche ancora avere aveva avevo abbiamo hanno ha ho hai " +
            "che chi ci con col come cosa cose così cui da dal dallo dai dagli dalla dalle dei degli del della delle dello di " +
            "dove dopo e ed è era ero essere fa fare fatto fino gli già ha il in io la le lei li lo loro lui ma me mi mia mie " +
            "mio miei molto ne nei negli nel nella nelle nello no noi non nostro nostra o oppure per perché però più poi " +
            "quale quando quanto quella quelle quelli quello questa queste questi questo qui se sei sia siamo sono sta stai " +
            "stato stavo su sua sue sul sulla suo suoi te ti tra tu tua tuo tutti tutto un una uno vi voi vostro va vado " +
            "allora cioè dai boh tipo comunque niente proprio solo poi ok okay sì si vabbè beh eh ah oh insomma praticamente " +
            "adesso ora magari sempre mai anche quindi ciao comunque ecco" +
            // inglese
            " the and for are but not you all any can had her was one our out his has have how its who did yes get " +
            "this that with from they will would there their what about which when your just like then them some " +
            "into than been were also more very okay yeah gonna wanna " +
            // arabo standard + darija (traslitterazione araba)
            " في من على إلى الى عن مع هذا هذه ذلك تلك التي الذي الذين أن ان إن كان كانت ما لا لم لن قد ثم أو او هو هي " +
            "أنا انا انت أنت نحن هم كل بعد قبل عند حتى إذا اذا يا و لي له لها لك فيه فيها هناك هنا ديال واش غادي كاين " +
            "بزاف شي هاد هادي راه راني راك واحد فين علاش كيفاش دابا مزيان ولا باش حيت بلا"
        ).split(" ").filter { it.isNotBlank() }.toSet()

    private fun tokens(text: String): List<String> =
        WORD.findAll(text.lowercase())
            .map { it.value }
            .filter { it.length > 2 && it !in STOP && it.any(Char::isLetter) }
            .toList()

    fun splitSentences(text: String): List<String> =
        text.split(SENTENCE_SPLIT).map { it.trim() }.filter { it.isNotEmpty() }

    fun summarize(text: String): Summary {
        val sentences = splitSentences(text)
        val words = tokens(text)
        if (words.isEmpty()) return Summary(emptyList(), emptyList())

        val freq = words.groupingBy { it }.eachCount()
        val keywords = freq.entries
            .filter { words.size < 40 || it.value >= 2 }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenByDescending { it.key.length })
            .take(5)
            .map { it.key }

        // Messaggio breve: il testo stesso è già il riassunto.
        if (words.size < 45 || sentences.size <= 2) return Summary(emptyList(), keywords)

        val maxF = freq.values.max().toDouble()
        val scored = sentences.mapIndexed { i, s ->
            val t = tokens(s)
            var score = if (t.isEmpty()) 0.0 else t.sumOf { (freq[it] ?: 0) / maxF } / sqrt(t.size.toDouble())
            if (i == 0) score *= 1.2               // di solito si apre col motivo del vocale
            if (s.contains('?') || s.contains('؟')) score *= 1.15  // le domande vanno spesso risposte
            if (t.size < 3) score *= 0.5           // frasi troppo corte ("ok", "va bene")
            i to score
        }
        val k = ceil(sentences.size * 0.3).toInt().coerceIn(1, 4)
        val picked = scored.sortedByDescending { it.second }.take(k).map { it.first }.sorted()
        return Summary(picked.map { sentences[it] }, keywords)
    }
}
