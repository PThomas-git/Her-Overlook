package fr.heroverlook.data

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Journal local de l'application (fichier texte dans filesDir/logs).
 *  - événements : démarrage, navigation, photos, export, localisation, erreurs ;
 *  - crashs : la pile d'appels est écrite avant la fermeture de l'appli ;
 *  - export : partage (mail, Drive, messagerie…) ou enregistrement dans un fichier, depuis le menu ⋮ de l'accueil.
 */
object AppLog {
    private const val MAX_BYTES = 512 * 1024L
    private const val KEEP_BYTES = 256 * 1024L

    private var appCtx: Context? = null
    private var file: File? = null
    private var crashFlag: File? = null
    private val lock = Any()

    fun init(ctx: Context) {
        val app = ctx.applicationContext
        appCtx = app
        val dir = File(app.filesDir, "logs").apply { mkdirs() }
        file = File(dir, "heroverlook.log")
        crashFlag = File(dir, "crash.flag")
        trimIfNeeded()

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                write("CRASH", "thread:${t.name}", stack(e))
                crashFlag?.writeText(stamp())
            }
            previous?.uncaughtException(t, e)
        }
        write("INFO", "app", "Démarrage — ${deviceLine()}")
    }

    fun i(tag: String, msg: String) = write("INFO", tag, msg)
    fun w(tag: String, msg: String) = write("WARN", tag, msg)
    fun e(tag: String, msg: String, t: Throwable? = null) =
        write("ERROR", tag, if (t == null) msg else "$msg\n${stack(t)}")

    /** Vrai (une seule fois) si le lancement précédent s'est terminé par un crash. */
    fun consumeCrashFlag(): Boolean {
        val f = crashFlag ?: return false
        if (!f.exists()) return false
        f.delete()
        return true
    }

    /** Journal complet précédé d'un en-tête (version, appareil, volumétrie). */
    fun fullText(sites: Int = -1, photos: Int = -1): String {
        val header = buildString {
            appendLine("=== Hér'Overlook — journal ===")
            appendLine("Exporté le : ${stamp()}")
            appendLine(deviceLine())
            if (sites >= 0) appendLine("Sites : $sites · Photos référencées : $photos")
            appendLine()
        }
        val body = synchronized(lock) { file?.takeIf { it.exists() }?.readText(Charsets.UTF_8) ?: "(journal vide)\n" }
        return header + body
    }

    /** Écrit le journal dans un fichier partageable et renvoie l'intent de partage. */
    fun shareIntent(ctx: Context, sites: Int, photos: Int): Intent {
        val dir = File(ctx.filesDir, "logs").apply { mkdirs() }
        val out = File(dir, "HerOverlook_log_${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.txt")
        out.writeText(fullText(sites, photos), Charsets.UTF_8)
        i("log", "Partage du journal : ${out.name}")
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", out)
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Hér'Overlook — journal")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Contenu du journal sous forme d'octets pour l'inclure dans l'export ZIP. */
    fun bytes(): ByteArray = fullText().toByteArray(Charsets.UTF_8)

    private fun write(level: String, tag: String, msg: String) {
        val f = file ?: return
        runCatching {
            synchronized(lock) { f.appendText("${stamp()} $level/$tag  $msg\n", Charsets.UTF_8) }
        }
    }

    private fun trimIfNeeded() {
        val f = file ?: return
        runCatching {
            if (f.length() > MAX_BYTES) {
                val tail = f.readBytes().let { it.copyOfRange((it.size - KEEP_BYTES).toInt(), it.size) }
                f.writeBytes(tail)
            }
        }
    }

    private fun stamp(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())

    private fun stack(t: Throwable): String = StringWriter().also { t.printStackTrace(PrintWriter(it)) }.toString()

    private fun deviceLine(): String {
        val ctx = appCtx
        val version = runCatching {
            ctx?.packageManager?.getPackageInfo(ctx.packageName, 0)?.versionName
        }.getOrNull() ?: "?"
        return "v$version · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    }
}
