package fr.heroverlook

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import fr.heroverlook.data.AppLog
import fr.heroverlook.data.Entry
import fr.heroverlook.data.Exporter
import fr.heroverlook.data.Rack
import fr.heroverlook.data.Site
import fr.heroverlook.data.SiteStore
import fr.heroverlook.data.allPhotos

class AppVM(app: Application) : AndroidViewModel(app) {
    private val store = SiteStore(app)

    val sites = mutableStateMapOf<String, Site>()
    val nav = mutableStateListOf("home")
    var busy by mutableStateOf<String?>(null)

    init {
        store.loadAll().forEach { sites[it.id] = it }
        AppLog.i("app", "${sites.size} site(s) chargé(s)")
        if (AppLog.consumeCrashFlag()) {
            toast("L'appli s'est fermée brutalement la dernière fois. Menu ⋮ > Partager le log pour m'envoyer le détail.")
        }
    }

    // --- navigation (routes : "home", "site/ID", "site/ID/access", "site/ID/rack/RACKID", ...) ---
    fun go(route: String) {
        AppLog.i("nav", "→ $route")
        nav.add(route)
    }

    fun back() {
        if (nav.size > 1) {
            AppLog.i("nav", "← retour (depuis ${nav.last()})")
            nav.removeAt(nav.lastIndex)
        }
    }

    // --- données ---
    fun update(id: String, f: (Site) -> Site) {
        val cur = sites[id] ?: return
        val next = f(cur).copy(updatedAt = System.currentTimeMillis())
        sites[id] = next
        store.save(next)
    }

    fun setEntry(siteId: String, itemId: String, f: (Entry) -> Entry) = update(siteId) { s ->
        s.copy(entries = s.entries + (itemId to f(s.entries[itemId] ?: Entry())))
    }

    fun updateRack(siteId: String, rackId: String, f: (Rack) -> Rack) = update(siteId) { s ->
        s.copy(racks = s.racks.map { if (it.id == rackId) f(it) else it })
    }

    fun importInventory(text: String): Int {
        return try {
            val list = SiteStore.parseInventory(text)
            var added = 0
            list.forEach { inv ->
                if (!sites.containsKey(inv.id)) {
                    val s = SiteStore.fromInventory(inv)
                    sites[s.id] = s
                    store.save(s)
                    added++
                }
            }
            toast("$added site(s) ajouté(s) (${list.size} dans le fichier)")
            added
        } catch (e: Exception) {
            AppLog.e("import", "Inventaire illisible", e)
            toast("Fichier d'inventaire illisible : ${e.message}")
            0
        }
    }

    fun loadDemo() {
        val s = SiteStore.demoSite()
        if (!sites.containsKey(s.id)) {
            sites[s.id] = s
            store.save(s)
        }
    }

    fun deleteDemo() {
        val id = SiteStore.demoSite().id
        sites.remove(id)
    }

    fun export(uri: Uri) {
        val ctx = getApplication<Application>()
        val snapshot = sites.values.toList()
        busy = "Export en cours…"
        Thread {
            try {
                ctx.contentResolver.openOutputStream(uri)!!.use { out -> Exporter.write(ctx, snapshot, out) }
                AppLog.i("export", "Export ZIP terminé (${snapshot.size} sites)")
                toast("Export terminé (${snapshot.size} sites)")
            } catch (e: Exception) {
                AppLog.e("export", "Export ZIP échoué", e)
                toast("Échec de l'export : ${e.message}")
            } finally {
                Handler(Looper.getMainLooper()).post { busy = null }
            }
        }.start()
    }

    /** Partage le journal (mail, Drive, messagerie…). */
    fun shareLog(ctx: Context) {
        try {
            val photos = sites.values.sumOf { it.allPhotos().size }
            ctx.startActivity(Intent.createChooser(AppLog.shareIntent(ctx, sites.size, photos), "Partager le log"))
        } catch (e: Exception) {
            AppLog.e("log", "Partage du journal impossible", e)
            toast("Partage impossible : ${e.message}")
        }
    }

    /** Enregistre le journal dans un fichier choisi par l'utilisateur. */
    fun saveLog(uri: Uri) {
        val ctx = getApplication<Application>()
        try {
            val photos = sites.values.sumOf { it.allPhotos().size }
            ctx.contentResolver.openOutputStream(uri)!!.use {
                it.write(AppLog.fullText(sites.size, photos).toByteArray(Charsets.UTF_8))
            }
            toast("Log enregistré")
        } catch (e: Exception) {
            AppLog.e("log", "Enregistrement du journal impossible", e)
            toast("Échec : ${e.message}")
        }
    }

    fun toast(msg: String) {
        AppLog.i("toast", msg)
        val ctx = getApplication<Application>()
        Handler(Looper.getMainLooper()).post { Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show() }
    }
}
