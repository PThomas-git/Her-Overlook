package fr.heroverlook.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.concurrent.thread

/**
 * Lecture de texte sur photo (ML Kit, modèle embarqué : fonctionne hors ligne) pour proposer des numéros de série.
 * Les propositions sont toujours à vérifier par l'utilisateur.
 */
object Ocr {
    private val labelled = Regex(
        "(?:S/?N|SERIAL(?: ?NO\\.?| NUMBER)?|SER\\.? ?NO\\.?|N° ?SERIE|NO ?SERIE)\\s*[:#.]?\\s*([A-Z0-9][A-Z0-9\\-_/.]{3,})"
    )
    private val token = Regex("[A-Z0-9][A-Z0-9\\-_/.]{5,}")

    /** Extrait des candidats de n° de série à partir des lignes lues : d'abord ceux précédés de « S/N », puis les autres. */
    fun candidates(lines: List<String>): List<String> {
        val first = LinkedHashSet<String>()
        val others = LinkedHashSet<String>()
        for (raw in lines) {
            val line = raw.uppercase().replace(' ', ' ')
            labelled.findAll(line).forEach { first.add(it.groupValues[1].trim('.', '-', '/')) }
            token.findAll(line).forEach { m ->
                val v = m.value.trim('.', '-', '/')
                if (v.length in 6..32 && v.any { it.isDigit() }) others.add(v)
            }
        }
        return (first + others).filter { it.length >= 4 }.distinct().take(10)
    }

    /** Lance la lecture en arrière-plan ; [onDone] est appelé sur le thread principal (liste vide en cas d'échec). */
    fun read(ctx: Context, rel: String, onDone: (List<String>) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        thread {
            val bmp = runCatching { Photos.decodeSampled(Photos.abs(ctx, rel), 2000) }.getOrNull()
            if (bmp == null) {
                AppLog.w("ocr", "Image illisible : $rel")
                main.post { onDone(emptyList()) }
                return@thread
            }
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(InputImage.fromBitmap(bmp, 0))
                .addOnSuccessListener { text ->
                    val lines = text.textBlocks.flatMap { b -> b.lines.map { it.text } }
                    val c = candidates(lines)
                    AppLog.i("ocr", "${lines.size} ligne(s) lue(s), ${c.size} proposition(s)")
                    recognizer.close()
                    main.post { onDone(c) }
                }
                .addOnFailureListener { e ->
                    AppLog.e("ocr", "Lecture du texte échouée", e)
                    recognizer.close()
                    main.post { onDone(emptyList()) }
                }
        }
    }
}
