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
        if (returnToBase) {
            if (base != null) total += km(pts.last(), base)
            else if (pts.size > 1) total += km(pts.last(), pts.first()) // boucle fermée entre les sites
        }
        return total
    }

    /** Durée de route estimée en heures : distance ×1,3 à 50 km/h de moyenne. */
    fun hours(km: Double): Double = km * 1.3 / 50.0

    /**
     * Ordre de visite d'une journée avec retour au départ : on optimise d'abord la **boucle complète** (départ → sites → départ)
     * par plusieurs constructions au plus proche voisin suivies d'une amélioration 2-opt, et on garde la plus courte.
     * Le sens de parcours est choisi ensuite : la longueur totale est identique dans les deux sens, donc « commencer par
     * le plus éloigné » ([farFirst]) ne coûte aucun kilomètre et fait finir près du départ.
     * Sans départ connu, on retombe sur un chemin ouvert simple.
     */
    fun orderDay(sites: List<Site>, base: LatLon?, farFirst: Boolean): List<Site> {
        if (sites.size <= 1) return sites
        if (base == null) return orderLoop(sites, farFirst)
        val pts = sites.map { it.latLon()!! }
        val n = pts.size
        fun d(a: Int, b: Int): Double {
            val pa = if (a < 0) base else pts[a]
            val pb = if (b < 0) base else pts[b]
            return km(pa, pb)
        }

        var best: List<Int>? = null
        var bestLen = Double.MAX_VALUE
        for (first in 0 until n) {
            // construction au plus proche voisin en partant de [first]
            val remaining = (0 until n).filter { it != first }.toMutableList()
            val seq = ArrayList<Int>()
            seq.add(-1) // -1 = départ (fixe, aussi point d'arrivée)
            seq.add(first)
            var cur = first
            while (remaining.isNotEmpty()) {
                val c = cur
                val nx = remaining.minByOrNull { d(c, it) }!!
                seq.add(nx)
                remaining.remove(nx)
                cur = nx
            }
            // 2-opt sur la boucle fermée (l'arête de retour vers le départ compte)
            var improved = true
            var guard = 0
            while (improved && guard++ < 80) {
                improved = false
                for (i in 1 until seq.size - 1) {
                    for (j in i + 1 until seq.size) {
                        val a = seq[i - 1]
                        val b = seq[i]
                        val c = seq[j]
                        val e = seq[(j + 1) % seq.size]
                        val before = d(a, b) + d(c, e)
                        val after = d(a, c) + d(b, e)
                        if (after + 1e-9 < before) {
                            seq.subList(i, j + 1).reverse()
                            improved = true
                        }
                    }
                }
            }
            var len = 0.0
            for (k in seq.indices) len += d(seq[k], seq[(k + 1) % seq.size])
            if (len < bestLen - 1e-9) {
                bestLen = len
                best = seq.drop(1)
            }
        }
        val order = best!!.toMutableList()
        val firstFar = d(-1, order.first())
        val lastFar = d(-1, order.last())
        if (farFirst && firstFar < lastFar) order.reverse()
        if (!farFirst && firstFar > lastFar) order.reverse()
        return order.map { sites[it] }
    }

    /** Position d'insertion la moins coûteuse d'un site dans une journée (0 = avant le premier, taille = après le dernier). */
    fun bestInsertIndex(route: List<Site>, c: Site, base: LatLon?): Int {
        val cp = c.latLon() ?: return route.size
        val pts = route.mapNotNull { it.latLon() }
        var bestIdx = pts.size
        var bestCost = Double.MAX_VALUE
        for (i in 0..pts.size) {
            val prev: LatLon? = if (i == 0) base else pts[i - 1]
            val next: LatLon? = if (i == pts.size) base else pts[i]
            val cost = when {
                prev != null && next != null -> km(prev, cp) + km(cp, next) - km(prev, next)
                prev != null -> km(prev, cp)
                next != null -> km(cp, next)
                else -> 0.0
            }
            if (cost < bestCost) {
                bestCost = cost
                bestIdx = i
            }
        }
        return bestIdx
    }

    /**
     * Boucle fermée entre les seuls sites (aucun point de départ imposé) : plusieurs constructions au plus proche voisin
     * suivies d'un 2-opt, on garde la plus courte. Avec [farFirst] la journée commence par le site le plus éloigné du centre
     * des sites, sinon par le plus central ; le sens retenu est celui dont le premier trajet est le plus court.
     */
    private fun orderLoop(sites: List<Site>, farFirst: Boolean): List<Site> {
        val n = sites.size
        if (n <= 2) return sites
        val pts = sites.map { it.latLon()!! }
        fun d(a: Int, b: Int): Double = km(pts[a], pts[b])

        var best: List<Int>? = null
        var bestLen = Double.MAX_VALUE
        for (first in 0 until n) {
            val remaining = (0 until n).filter { it != first }.toMutableList()
            val seq = ArrayList<Int>()
            seq.add(first)
            var cur = first
            while (remaining.isNotEmpty()) {
                val c = cur
                val nx = remaining.minByOrNull { d(c, it) }!!
                seq.add(nx)
                remaining.remove(nx)
                cur = nx
            }
            var improved = true
            var guard = 0
            while (improved && guard++ < 80) {
                improved = false
                for (i in 1 until n - 1) {
                    for (j in i + 1 until n) {
                        val a = seq[i - 1]
                        val b = seq[i]
                        val c = seq[j]
                        val e = seq[(j + 1) % n]
                        if (d(a, c) + d(b, e) + 1e-9 < d(a, b) + d(c, e)) {
                            seq.subList(i, j + 1).reverse()
                            improved = true
                        }
                    }
                }
            }
            var len = 0.0
            for (k in 0 until n) len += d(seq[k], seq[(k + 1) % n])
            if (len < bestLen - 1e-9) {
                bestLen = len
                best = ArrayList(seq)
            }
        }
        val loop = best!!
        val centre = LatLon(pts.map { it.lat }.average(), pts.map { it.lon }.average())
        val start = if (farFirst) loop.maxByOrNull { km(pts[it], centre) }!! else loop.minByOrNull { km(pts[it], centre) }!!
        val k = loop.indexOf(start)
        var order = loop.drop(k) + loop.take(k)
        if (d(order[0], order[1]) > d(order[0], order.last())) {
            order = listOf(order[0]) + order.drop(1).reversed()
        }
        return order.map { sites[it] }
    }

    /** Chemin ouvert (sans départ connu) : plus proche voisin depuis le site le plus à l'ouest, puis 2-opt. */
    private fun orderOpen(sites: List<Site>, base: LatLon?, farFirst: Boolean): List<Site> {
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
