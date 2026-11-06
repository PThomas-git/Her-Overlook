package fr.heroverlook.data

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double)

fun Site.latLon(): LatLon? {
    val a = lat
    val b = lon
    return if (a != null && b != null) LatLon(a, b) else null
}

fun Site.displayName(): String = name.ifBlank { id }

/**
 * Planification de tournée : répartition en jours, ordre de visite et ADR « sur la route ».
 * Toutes les distances sont « à vol d'oiseau » (compter environ ×1,3 par la route).
 */
object TourPlanner {
    /** Agence Hérault Télécom de Saint-Aunès (retrait des clés). Position approximative du centre de la commune. */
    val AGENCY = LatLon(43.652, 3.980)

    fun km(a: LatLon, b: LatLon): Double {
        val r = 6371.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(sqrt(h))
    }

    /** Longueur d'une journée : du départ jusqu'au dernier site, plus le retour au départ si [returnToBase]. */
    fun routeKm(route: List<Site>, base: LatLon?, returnToBase: Boolean = true): Double {
        val pts = route.mapNotNull { it.latLon() }
        if (pts.isEmpty()) return 0.0
        var total = 0.0
        var prev: LatLon? = base
        for (p in pts) {
            if (prev != null) total += km(prev, p)
            prev = p
        }
        if (returnToBase && base != null) total += km(pts.last(), base)
        return total
    }

    /** Durée de route estimée en heures : distance ×1,3 à 50 km/h de moyenne. */
    fun hours(km: Double): Double = km * 1.3 / 50.0

    /**
     * Ordre de visite d'une journée : plus proche voisin puis amélioration 2-opt.
     * Avec [farFirst] et un départ connu, on commence par le site le plus éloigné du départ puis on se rapproche.
     */
    fun orderDay(sites: List<Site>, base: LatLon?, farFirst: Boolean): List<Site> {
        if (sites.size <= 1) return sites
        val pts = sites.map { it.latLon()!! }
        val remaining = sites.indices.toMutableList()
        val order = ArrayList<Int>()
        var cur: LatLon
        if (base != null) {
            cur = base
            if (farFirst) {
                val f = remaining.maxByOrNull { km(base, pts[it]) }!!
                order.add(f)
                remaining.remove(f)
                cur = pts[f]
            }
        } else {
            val f = remaining.minByOrNull { pts[it].lon }!!
            order.add(f)
            remaining.remove(f)
            cur = pts[f]
        }
        while (remaining.isNotEmpty()) {
            val c = cur
            val n = remaining.minByOrNull { km(c, pts[it]) }!!
            order.add(n)
            remaining.remove(n)
            cur = pts[n]
        }

        val seq = ArrayList<Int>()
        if (base != null) seq.add(-1) // -1 = départ, fixe
        seq.addAll(order)
        val fixed = (if (base != null) 1 else 0) + (if (base == null || farFirst) 1 else 0)
        fun p(i: Int): LatLon = if (i == -1) base!! else pts[i]

        var improved = true
        var guard = 0
        while (improved && guard++ < 60) {
            improved = false
            for (i in fixed until seq.size - 1) {
                for (j in i + 1 until seq.size) {
                    val a = p(seq[i - 1])
                    val b = p(seq[i])
                    val c = p(seq[j])
                    val d = if (j + 1 < seq.size) p(seq[j + 1]) else null
                    val before = km(a, b) + (if (d != null) km(c, d) else 0.0)
                    val after = km(a, c) + (if (d != null) km(b, d) else 0.0)
                    if (after + 1e-9 < before) {
                        seq.subList(i, j + 1).reverse()
                        improved = true
                    }
                }
            }
        }
        return seq.filter { it >= 0 }.map { sites[it] }
    }

    /**
     * Répartit les sites en [days] journées géographiquement cohérentes (k-moyennes), la journée la plus éloignée
     * du départ en premier.
     */
    fun splitDays(sites: List<Site>, base: LatLon?, days: Int): List<List<Site>> {
        val k = days.coerceIn(1, max(1, sites.size))
        if (k <= 1 || sites.size <= 1) return listOf(sites)
        val pts = sites.map { it.latLon()!! }
        val ref = base ?: LatLon(pts.map { it.lat }.average(), pts.map { it.lon }.average())

        val centers = ArrayList<LatLon>()
        centers.add(pts.maxByOrNull { km(ref, it) }!!)
        while (centers.size < k) {
            centers.add(pts.maxByOrNull { p -> centers.minOf { km(it, p) } }!!)
        }
        val assign = IntArray(sites.size)
        repeat(15) {
            for (i in pts.indices) assign[i] = centers.indices.minByOrNull { km(centers[it], pts[i]) }!!
            for (c in centers.indices) {
                val m = pts.indices.filter { assign[it] == c }
                if (m.isNotEmpty()) centers[c] = LatLon(m.map { pts[it].lat }.average(), m.map { pts[it].lon }.average())
            }
        }
        val groups = (0 until k)
            .map { c -> sites.filterIndexed { i, _ -> assign[i] == c } }
            .filter { it.isNotEmpty() }
        return groups.sortedByDescending { g -> g.maxOf { km(ref, it.latLon()!!) } }
    }

    enum class Phase(val label: String) { BEFORE("Avant la tournée"), DURING("Pendant"), AFTER("Après la tournée") }

    class Suggestion(
        val site: Site,
        /** Position d'insertion dans la journée (0 = avant le premier site, taille = après le dernier). */
        val index: Int,
        val detourKm: Double,
        val phase: Phase,
        val between: String,
    )

    /**
     * ADR qui se trouvent « sur la route » d'une journée de POP : pour chaque candidat on cherche l'insertion qui coûte
     * le moins de détour (avant le premier site, entre deux sites, ou après le dernier avec retour au départ).
     */
    fun suggestions(route: List<Site>, candidates: List<Site>, base: LatLon?, maxKm: Double): List<Suggestion> {
        if (route.isEmpty()) return emptyList()
        val pts = route.map { it.latLon()!! }
        val out = ArrayList<Suggestion>()
        for (c in candidates) {
            val cp = c.latLon() ?: continue
            var bestCost = Double.MAX_VALUE
            var bestIdx = 0
            for (i in 0..route.size) {
                val prev: LatLon? = if (i == 0) base else pts[i - 1]
                val next: LatLon? = if (i == route.size) base else pts[i]
                val cost = when {
                    prev != null && next != null -> km(prev, cp) + km(cp, next) - km(prev, next)
                    prev != null -> km(prev, cp)
                    next != null -> km(cp, next)
                    else -> Double.MAX_VALUE
                }
                if (cost < bestCost) {
                    bestCost = cost
                    bestIdx = i
                }
            }
            if (bestCost <= maxKm) {
                val phase = when (bestIdx) {
                    0 -> Phase.BEFORE
                    route.size -> Phase.AFTER
                    else -> Phase.DURING
                }
                val between = when (phase) {
                    Phase.BEFORE -> "avant ${route.first().displayName()}"
                    Phase.AFTER -> "après ${route.last().displayName()}"
                    Phase.DURING -> "entre ${route[bestIdx - 1].displayName()} et ${route[bestIdx].displayName()}"
                }
                out.add(Suggestion(c, bestIdx, bestCost, phase, between))
            }
        }
        return out.sortedBy { it.detourKm }
    }

    fun encodePlan(days: List<List<String>>): String = days.joinToString("\n") { it.joinToString("\t") }

    fun decodePlan(text: String): List<List<String>> =
        if (text.isBlank()) emptyList() else text.split("\n").map { line -> line.split("\t").filter { it.isNotBlank() } }
}
