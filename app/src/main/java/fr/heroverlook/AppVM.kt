package fr.heroverlook

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import fr.heroverlook.data.AppLog
import fr.heroverlook.data.Entry
import fr.heroverlook.data.Exporter
import fr.heroverlook.data.Known
import fr.heroverlook.data.KnownState
import fr.heroverlook.data.LatLon
import fr.heroverlook.data.Photos
import fr.heroverlook.data.TourPlanner
import fr.heroverlook.data.latLon
import fr.heroverlook.data.VisitRound
import fr.heroverlook.data.Rack
import fr.heroverlook.data.Site
import fr.heroverlook.data.SiteStore
import fr.heroverlook.data.Verdict
import fr.heroverlook.data.VisitStatus
import fr.heroverlook.data.allPhotos
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppVM(app: Application, private val state: SavedStateHandle) : AndroidViewModel(app) {
    private val store = SiteStore(app)
    private val prefs = app.getSharedPreferences("tour", Context.MODE_PRIVATE)

    // ---- Tournée (réglages et plan conservés entre deux lancements) ----
    var tourDays by mutableIntStateOf(prefs.getInt("days", 1))
    var tourPopOnly by mutableStateOf(prefs.getBoolean("popOnly2", true))
    var tourFarFirst by mutableStateOf(prefs.getBoolean("farFirst", true))
    var tourDetourKm by mutableIntStateOf(prefs.getInt("detourKm", 5))

    /** Point de départ et de retour : « lat, lon » (ma position ou point choisi sur la carte) ; vide = aucun. */
    var tourStart by mutableStateOf(prefs.getString("start", "") ?: "")

    fun tourBase(): LatLon? {
        val m = Regex("(-?\\d+\\.\\d+)\\s*,\\s*(-?\\d+\\.\\d+)").find(tourStart) ?: return null
        return LatLon(m.groupValues[1].toDouble(), m.groupValues[2].toDouble())
    }

    fun changeTourStart(text: String) {
        tourStart = text
        planTour()
    }

    /** Plan : une liste d'identifiants de sites par jour. */
    val tourPlan = mutableStateListOf<List<String>>().apply {
        addAll(TourPlanner.decodePlan(prefs.getString("plan", "") ?: ""))
    }

    /** Sites retirés à la main de la tournée : ils n'y reviennent pas au recalcul tant qu'ils ne sont pas rajoutés. */
    val tourExcluded = mutableStateListOf<String>().apply {
        addAll((prefs.getString("excluded", "") ?: "").split("\n").filter { it.isNotBlank() })
    }

    private fun saveTour() {
        prefs.edit()
            .putInt("days", tourDays).putBoolean("popOnly2", tourPopOnly).putBoolean("farFirst", tourFarFirst)
            .putInt("detourKm", tourDetourKm).putString("start", tourStart)
            .putString("plan", TourPlanner.encodePlan(tourPlan.toList()))
            .putString("excluded", tourExcluded.joinToString("\n"))
            .apply()
    }

    fun setTourPlan(days: List<List<String>>) {
        tourPlan.clear()
        tourPlan.addAll(days)
        saveTour()
    }

    /** Sites candidats : avec coordonnées, pas terminés (les sites finis sont déclassés), POP seulement si demandé. */
    fun tourCandidates(): List<Site> = sites.values.filter {
        it.latLon() != null && it.status != VisitStatus.DONE && (!tourPopOnly || it.kind.isPop) && it.id !in tourExcluded
    }

    /** Retire un site de la tournée (et le garde à l'écart des prochains recalculs). */
    fun removeFromTour(id: String) {
        if (id !in tourExcluded) tourExcluded.add(id)
        setTourPlan(tourPlan.map { d -> d.filter { it != id } })
    }

    /** Ajoute (ou déplace) un site dans le jour [day], à la position qui coûte le moins de détour. */
    fun addToDay(day: Int, site: Site) {
        tourExcluded.remove(site.id)
        val plan = tourPlan.map { d -> d.filter { it != site.id } }.toMutableList()
        if (plan.isEmpty()) plan.add(emptyList())
        val di = day.coerceIn(0, plan.size - 1)
        val valid = plan[di].filter { sites[it]?.let { s -> s.status != VisitStatus.DONE } == true }.toMutableList()
        val route = valid.mapNotNull { sites[it] }
        val idx = TourPlanner.bestInsertIndex(route, site, tourBase())
        valid.add(idx.coerceIn(0, valid.size), site.id)
        plan[di] = valid
        setTourPlan(plan)
    }

    /** Recalcule tout le plan avec les réglages actuels (écrase l'ordre modifié à la main). */
    fun planTour() {
        // Avec un départ : boucle départ → sites → départ. Sans départ : boucle fermée entre les sites.
        val base = tourBase()
        val groups = TourPlanner.splitDays(tourCandidates(), base, tourDays)
            .map { g -> TourPlanner.orderDay(g, base, tourFarFirst).map { it.id } }
        setTourPlan(groups)
        AppLog.i("tour", "Tournée recalculée : ${groups.map { it.size }} site(s) par jour")
    }

    fun updateTourSettings(days: Int = tourDays, popOnly: Boolean = tourPopOnly, farFirst: Boolean = tourFarFirst, detourKm: Int = tourDetourKm) {
        val replan = days != tourDays || popOnly != tourPopOnly || farFirst != tourFarFirst
        tourDays = days
        tourPopOnly = popOnly
        tourFarFirst = farFirst
        tourDetourKm = detourKm
        if (replan) planTour() else saveTour()
    }

    fun moveInDay(day: Int, from: Int, delta: Int) {
        val d = tourPlan.getOrNull(day)?.toMutableList() ?: return
        val to = from + delta
        if (from !in d.indices || to !in d.indices) return
        val t = d[from]
        d[from] = d[to]
        d[to] = t
        tourPlan[day] = d
        saveTour()
    }

    /** Déplace une étape vers un autre jour (en fin de journée). */
    fun moveToDay(fromDay: Int, id: String, toDay: Int) {
        if (fromDay == toDay || toDay !in tourPlan.indices || fromDay !in tourPlan.indices) return
        tourPlan[fromDay] = tourPlan[fromDay].filter { it != id }
        tourPlan[toDay] = tourPlan[toDay] + id
        saveTour()
    }

    fun insertInDay(day: Int, index: Int, id: String) {
        val d = tourPlan.getOrNull(day)?.toMutableList() ?: return
        if (id in d) return
        d.add(index.coerceIn(0, d.size), id)
        tourPlan[day] = d
        saveTour()
    }

    val sites = mutableStateMapOf<String, Site>()

    /** État de défilement de la liste d'accueil, conservé quand on ouvre un site puis qu'on revient. */
    val homeListState = androidx.compose.foundation.lazy.LazyListState()

    /** Pile de navigation, sauvegardée : Android peut tuer l'appli pendant que l'appareil photo est ouvert. */
    val nav = mutableStateListOf<String>().apply {
        addAll(state.get<ArrayList<String>>("nav") ?: arrayListOf("home"))
    }
    var busy by mutableStateOf<String?>(null)

    init {
        store.loadAll().forEach { sites[it.id] = it }
        AppLog.i("app", "${sites.size} site(s) chargé(s)")
        // Routes pointant vers un site disparu : on les retire pour ne pas afficher d'écran vide.
        nav.removeAll { route ->
            val siteId = route.split("/").getOrNull(1)
            route.startsWith("site/") && (siteId == null || !sites.containsKey(siteId))
        }
        if (nav.isEmpty() || nav.first() != "home") nav.add(0, "home")
        saveNav()
        if (AppLog.consumeCrashFlag()) {
            toast("L'appli s'est fermée brutalement la dernière fois. Menu ⋮ > Partager le log pour m'envoyer le détail.")
        }
        recoverOrphanPhotos()
    }

    private fun saveNav() {
        state["nav"] = ArrayList(nav)
    }

    // --- navigation (routes : "home", "site/ID", "site/ID/access", "site/ID/rack/RACKID", ...) ---
    fun go(route: String) {
        if (nav.lastOrNull() == route) return // double appui : ne pas empiler deux fois le même écran
        AppLog.i("nav", "→ $route")
        nav.add(route)
        saveNav()
    }

    /** Remplace l'écran du dessus (passer à la section suivante sans empiler). */
    fun replaceTop(route: String) {
        if (nav.isEmpty() || nav.last() == route) return
        nav[nav.lastIndex] = route
        saveNav()
    }

    /** Filtre de la check-list (nom de CheckFilter), partagé entre la liste des sections et chaque section. */
    var checkFilter by mutableStateOf("ALL")

    fun back() {
        if (nav.size > 1) {
            AppLog.i("nav", "← retour (depuis ${nav.last()})")
            nav.removeAt(nav.lastIndex)
            saveNav()
        }
    }

    // --- données ---
    fun update(id: String, f: (Site) -> Site) {
        val cur = sites[id] ?: return
        val next = f(cur).copy(updatedAt = System.currentTimeMillis())
        sites[id] = next
        store.save(next)
    }

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /** Dès qu'un point est renseigné, le site passe automatiquement « En cours » (et la date de visite est posée). */
    private fun startedIfNeeded(s: Site): Site =
        if (s.status == VisitStatus.TODO) s.copy(status = VisitStatus.IN_PROGRESS, visitDate = s.visitDate.ifBlank { today() }) else s

    fun setEntry(siteId: String, itemId: String, f: (Entry) -> Entry) = update(siteId) { s ->
        val e = f(s.entries[itemId] ?: Entry())
        val next = s.copy(entries = s.entries + (itemId to e))
        if (e.verdict != Verdict.UNSET) startedIfNeeded(next) else next
    }

    /** Passe en « conforme » tous les points encore non renseignés d'une section (gain de temps sur site). */
    fun markUnansweredOk(siteId: String, itemIds: List<String>, verdict: Verdict = Verdict.OK) = update(siteId) { s ->
        val add = itemIds
            .filter { (s.entries[it]?.verdict ?: Verdict.UNSET) == Verdict.UNSET }
            .associateWith { id -> (s.entries[id] ?: Entry()).copy(verdict = verdict) }
        if (add.isEmpty()) s else startedIfNeeded(s.copy(entries = s.entries + add))
    }

    fun setKnown(siteId: String, knownId: String, f: (Known) -> Known) = update(siteId) { s ->
        s.copy(known = s.known.map { if (it.id == knownId) f(it) else it })
    }

    /**
     * Contre-visite : archive la visite en cours, remet à zéro uniquement les points en réserve ou non conformes
     * (les points conformes restent acquis) et mémorise la liste des points à revoir.
     */
    fun startRevisit(siteId: String) = update(siteId) { s ->
        val flagged = s.entries.filter { it.value.verdict == Verdict.RESERVE || it.value.verdict == Verdict.NC }.keys.toList()
        if (flagged.isEmpty()) s
        else s.copy(
            rounds = s.rounds + VisitRound(date = today(), entries = s.entries),
            revisitIds = flagged,
            entries = s.entries + flagged.associateWith { Entry() },
            status = VisitStatus.IN_PROGRESS,
        )
    }

    /**
     * Si Android a tué l'appli pendant la prise de vue, le fichier photo existe mais n'est rattaché à rien.
     * Au démarrage on les retrouve et on les range dans « Photos du site » (le nom du fichier garde le contexte).
     */
    private fun recoverOrphanPhotos() {
        val ctx = getApplication<Application>()
        val snapshot = sites.values.toList()
        Thread {
            try {
                val found = HashMap<String, List<String>>()
                for (s in snapshot) {
                    val dir = File(ctx.filesDir, "photos/${SiteStore.safeName(s.id)}")
                    val files = dir.listFiles { f -> f.isFile && f.extension.equals("jpg", true) } ?: continue
                    val known = s.allPhotos().toSet()
                    val orphans = ArrayList<String>()
                    for (f in files) {
                        // Fichier récent : peut encore être rattaché par le retour de l'appareil photo, on n'y touche pas.
                        if (System.currentTimeMillis() - f.lastModified() < 120_000L) continue
                        if (f.length() == 0L) { f.delete(); continue }
                        val rel = Photos.rel(ctx, f)
                        if (rel in known) continue
                        if (f.length() > 1_500_000L) Photos.compress(f)
                        orphans += rel
                    }
                    if (orphans.isNotEmpty()) found[s.id] = orphans
                }
                if (found.isNotEmpty()) {
                    Handler(Looper.getMainLooper()).post {
                        var n = 0
                        found.forEach { (id, list) ->
                            update(id) { st -> st.copy(generalPhotos = st.generalPhotos + list.filter { it !in st.generalPhotos }) }
                            n += list.size
                        }
                        AppLog.w("photo", "$n photo(s) orpheline(s) récupérée(s)")
                        toast("$n photo(s) récupérée(s) après une fermeture de l'appli : voir « Photos » du site.")
                    }
                }
            } catch (e: Exception) {
                AppLog.e("photo", "Récupération des photos orphelines impossible", e)
            }
        }.start()
    }

    fun updateRack(siteId: String, rackId: String, f: (Rack) -> Rack) = update(siteId) { s ->
        s.copy(racks = s.racks.map { if (it.id == rackId) f(it) else it })
    }

    fun importInventory(text: String): Int {
        return try {
            val list = SiteStore.parseInventory(text)
            var added = 0
            var refreshed = 0
            list.forEach { inv ->
                if (!sites.containsKey(inv.id)) {
                    val s = SiteStore.fromInventory(inv)
                    sites[s.id] = s
                    store.save(s)
                    added++
                } else {
                    // Site déjà présent : on met à jour les seules données d'inventaire, jamais le relevé.
                    val n = SiteStore.fromInventory(inv)
                    update(inv.id) { cur ->
                        cur.copy(
                            locCode = n.locCode, kind = n.kind, name = n.name, address = n.address, city = n.city,
                            postalCode = n.postalCode, network = n.network, tier = n.tier,
                            activeEquipment = n.activeEquipment, cooling = n.cooling, power = n.power,
                            lat = n.lat, lon = n.lon, accessHO = n.accessHO, accessHNO = n.accessHNO,
                            inventoryNote = n.inventoryNote,
                            // Données connues : on garde l'état de validation déjà saisi sur place.
                            known = n.known.map { nk ->
                                val old = cur.known.firstOrNull { it.id == nk.id }
                                if (old != null) nk.copy(state = old.state, fix = old.fix) else nk
                            },
                            prior = n.prior,
                            // Valeurs pré-remplies : jamais d'écrasement d'une valeur déjà saisie.
                            entries = cur.entries.toMutableMap().also { m ->
                                n.entries.forEach { (k, v) ->
                                    val e = m[k]
                                    if (e == null) m[k] = v else if (e.value.isBlank()) m[k] = e.copy(value = v.value)
                                }
                            },
                            racks = cur.racks + n.racks.filter { nr -> cur.racks.none { it.label == nr.label } },
                            // Plan de départ : seulement si aucun plan n'a encore été dessiné sur ce site.
                            layout = if (cur.layout.plan.isBlank && !n.layout.plan.isBlank) {
                                val allRacks = cur.racks + n.racks.filter { nr -> cur.racks.none { it.label == nr.label } }
                                val unlinked = n.layout.plan.copy(items = n.layout.plan.items.map { it.copy(rackId = null) })
                                cur.layout.copy(plan = SiteStore.linkPlan(unlinked, allRacks))
                            } else cur.layout,
                        )
                    }
                    refreshed++
                }
            }
            AppLog.i("import", "Inventaire : $added ajouté(s), $refreshed mis à jour")
            toast("$added site(s) ajouté(s), $refreshed mis à jour (${list.size} dans le fichier)")
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

    fun deleteDemo() = deleteSite(SiteStore.demoSite().id)

    /**
     * Copie le relevé de [src] dans [target] (l'identité du site cible — adresse, code, données connues — est conservée).
     * Les photos sont renvoyées par [remap]. Les validations « déjà connu » sont remises à zéro (elles portaient sur les
     * informations de l'autre site) ; les corrections saisies sont conservées dans l'analyse. Les valeurs pré-remplies
     * jamais touchées sont retirées pour ne pas laisser les données de l'autre site.
     */
    private fun transferReleve(target: Site, src: Site, remap: (String) -> String): Site {
        fun Entry.moved() = copy(photos = photos.map(remap))
        val entries = src.entries
            .mapValues { (_, e) -> if (e.verdict == Verdict.UNSET && e.comment.isBlank() && e.photos.isEmpty()) Entry() else e.moved() }
            .filterValues { it != Entry() }
        // Le plan DOE pré-dessiné appartient au site d'origine (local différent) : on ne le transfère pas.
        val doeOutline = src.layout.plan.items.any { it.note.startsWith("Plan DOE") }
        val plan = src.layout.plan.copy(
            outline = if (doeOutline) emptyList() else src.layout.plan.outline,
            items = src.layout.plan.items.filterNot { it.note.startsWith("Plan DOE") },
        )
        val layout = src.layout.copy(
            plan = plan,
            photos = src.layout.photos.map(remap),
            sketches = src.layout.sketches.map(remap),
            deviations = src.layout.deviations.map { d -> d.copy(photos = d.photos.map(remap)) },
        )
        val racks = src.racks.map { r ->
            r.copy(photos = r.photos.map(remap), equipments = r.equipments.map { e -> e.copy(photos = e.photos.map(remap)) })
        }
        val rounds = src.rounds.map { rd -> rd.copy(entries = rd.entries.mapValues { (_, e) -> e.moved() }) }
        val fixes = src.known.filter { it.fix.isNotBlank() }.map { "• ${it.label} : ${it.fix}" }
        val analysis = listOf(
            src.analysis,
            if (fixes.isEmpty()) "" else "Corrections saisies sur les infos « déjà connues » de ${src.name.ifBlank { src.id }} :\n" + fixes.joinToString("\n"),
        ).filter { it.isNotBlank() }.joinToString("\n\n")
        return target.copy(
            status = src.status, visitDate = src.visitDate, measuredGps = src.measuredGps,
            accessMethods = src.accessMethods, accessResult = src.accessResult, accessNote = src.accessNote,
            onSiteContact = src.onSiteContact, accessPhotos = src.accessPhotos.map(remap),
            entries = entries, layout = layout, racks = racks, usagers = src.usagers,
            generalPhotos = src.generalPhotos.map(remap), analysis = analysis, revisit = src.revisit,
            rounds = rounds, revisitIds = src.revisitIds,
            known = target.known.map { it.copy(state = KnownState.TODO, fix = "") },
        )
    }

    /**
     * Échange les relevés de deux sites (erreur de saisie : on a rempli le mauvais site). Les photos changent de dossier
     * et de nom ; l'identité des sites ne bouge pas. Réimporter ensuite l'inventaire recharge les données prévues.
     */
    fun swapReleve(aId: String, bId: String) {
        val a = sites[aId] ?: return
        val b = sites[bId] ?: return
        if (aId == bId) return
        val ctx = getApplication<Application>()
        busy = "Échange du relevé en cours…"
        Thread {
            try {
                val sa = SiteStore.safeName(aId)
                val sb = SiteStore.safeName(bId)
                fun movePhotos(from: Site, fromSafe: String, toSafe: String): Map<String, String> {
                    val map = HashMap<String, String>()
                    for (rel in from.allPhotos()) {
                        val name = rel.substringAfterLast('/')
                        val newName = if (name.startsWith(fromSafe + "_")) toSafe + name.removePrefix(fromSafe) else name
                        var dest = File(ctx.filesDir, "photos/$toSafe/$newName")
                        dest.parentFile?.mkdirs()
                        if (dest.exists()) dest = File(dest.parentFile, "x_" + newName)
                        val src = File(ctx.filesDir, rel)
                        if (src.exists()) {
                            if (!src.renameTo(dest)) {
                                src.copyTo(dest, overwrite = true)
                                src.delete()
                            }
                        }
                        map[rel] = Photos.rel(ctx, dest)
                    }
                    return map
                }
                val mapAtoB = movePhotos(a, sa, sb) // photos du relevé de A, qui passent chez B
                val mapBtoA = movePhotos(b, sb, sa)
                val newA = transferReleve(a, b) { mapBtoA[it] ?: it } // A reçoit le relevé de B
                val newB = transferReleve(b, a) { mapAtoB[it] ?: it }
                Handler(Looper.getMainLooper()).post {
                    update(aId) { newA }
                    update(bId) { newB }
                    busy = null
                    AppLog.w("data", "Relevés échangés : $aId <-> $bId")
                    toast("Relevés échangés. Vérifie les valeurs pré-remplies (PRM, équipements attendus) : elles viennent de l'autre site si tu ne les avais pas corrigées.")
                }
            } catch (e: Exception) {
                AppLog.e("data", "Échange de relevés impossible", e)
                Handler(Looper.getMainLooper()).post {
                    busy = null
                    toast("Échange impossible : ${e.message}")
                }
            }
        }.start()
    }

    /** Supprime un site, son relevé et ses photos de ce téléphone (irréversible : penser à exporter avant). */
    fun deleteSite(id: String, withPhotos: Boolean = true) {
        sites.remove(id)
        store.delete(id)
        if (withPhotos) {
            val dir = File(getApplication<Application>().filesDir, "photos/${SiteStore.safeName(id)}")
            runCatching { dir.deleteRecursively() }
        }
        nav.removeAll { it.startsWith("site/") && it.split("/").getOrNull(1) == id }
        if (nav.isEmpty()) nav.add("home")
        saveNav()
        if (tourPlan.any { id in it }) setTourPlan(tourPlan.map { d -> d.filter { it != id } })
        AppLog.w("data", "Site supprimé : $id (photos=$withPhotos)")
    }

    fun deleteAllSites() {
        val ids = sites.keys.toList()
        ids.forEach { deleteSite(it) }
        toast("${ids.size} site(s) supprimé(s). Tu peux réimporter l'inventaire (menu ⋮).")
    }

    /** Export ZIP de tous les sites, ou d'un seul si [onlyId] est donné. */
    fun export(uri: Uri, onlyId: String? = null) {
        val ctx = getApplication<Application>()
        val snapshot = sites.values.filter { onlyId == null || it.id == onlyId }
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
