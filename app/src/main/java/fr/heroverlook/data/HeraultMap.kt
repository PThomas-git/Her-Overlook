package fr.heroverlook.data

import android.content.Context

/**
 * Fond de carte hors ligne de l'Hérault (assets/herault_map.txt).
 * Contours : IGN Admin Express, via data.gouv.fr (Licence Ouverte Etalab), simplifiés (~50 m).
 * Format : une ligne par contour, « D » (département) ou « C » (commune), puis des paires « lon lat ».
 */
object HeraultMap {
    class Shapes(
        val outline: List<DoubleArray>,
        val communes: List<DoubleArray>,
        val minLat: Double,
        val maxLat: Double,
        val minLon: Double,
        val maxLon: Double,
    )

    fun load(ctx: Context): Shapes {
        val outline = ArrayList<DoubleArray>()
        val communes = ArrayList<DoubleArray>()
        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var minLon = Double.MAX_VALUE
        var maxLon = -Double.MAX_VALUE
        ctx.assets.open("herault_map.txt").bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (l in lines) {
                if (l.length < 4) continue
                val parts = l.substring(2).split(' ')
                val ring = DoubleArray(parts.size) { parts[it].toDouble() }
                if (l[0] == 'D') {
                    outline.add(ring)
                    var i = 0
                    while (i + 1 < ring.size) {
                        val lon = ring[i]
                        val lat = ring[i + 1]
                        if (lon < minLon) minLon = lon
                        if (lon > maxLon) maxLon = lon
                        if (lat < minLat) minLat = lat
                        if (lat > maxLat) maxLat = lat
                        i += 2
                    }
                } else {
                    communes.add(ring)
                }
            }
        }
        return Shapes(outline, communes, minLat, maxLat, minLon, maxLon)
    }
}
