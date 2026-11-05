package fr.heroverlook.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.Locale

sealed class LocationResult {
    data class Ok(val lat: Double, val lon: Double, val accuracy: Float, val note: String? = null) : LocationResult() {
        val text: String get() = String.format(Locale.US, "%.6f, %.6f (±%.0f m)", lat, lon, accuracy)
    }

    /** [reason] est un message prêt à afficher, qui dit précisément ce qui manque. */
    data class Fail(val reason: String) : LocationResult()
}

/**
 * Position actuelle, en trois temps :
 *  1) localisation « fusionnée » de Google (GPS + Wi-Fi + réseau mobile), la plus fiable en intérieur ;
 *  2) à défaut, dernière position connue de n'importe quel fournisseur (l'âge est indiqué) ;
 *  3) sinon un message d'erreur précis (permission, localisation coupée, pas de signal).
 */
@SuppressLint("MissingPermission")
fun requestLocation(ctx: Context, onResult: (LocationResult) -> Unit) {
    val main = Handler(Looper.getMainLooper())
    var done = false
    fun finish(r: LocationResult) {
        main.post {
            if (!done) {
                done = true
                AppLog.i("gps", "Résultat : $r")
                onResult(r)
            }
        }
    }

    val fine = ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val coarse = ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!fine && !coarse) {
        finish(LocationResult.Fail("Permission de localisation refusée : Réglages > Applications > Hér'Overlook > Autorisations > Position."))
        return
    }
    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    if (!lm.isLocationEnabled) {
        finish(LocationResult.Fail("La localisation est désactivée sur le téléphone : active « Position » dans le volet rapide d'Android, puis réessaie."))
        return
    }
    AppLog.i("gps", "Demande de position (précise=$fine)")

    fun fallback(reason: String) {
        var best: Location? = null
        val providers = try { lm.getProviders(true) } catch (e: Exception) { emptyList<String>() }
        for (p in providers) {
            val l = try { lm.getLastKnownLocation(p) } catch (e: Exception) { null }
            if (l != null && (best == null || l.time > best.time)) best = l
        }
        val b = best
        if (b != null) {
            val ageMin = (System.currentTimeMillis() - b.time) / 60000
            finish(LocationResult.Ok(b.latitude, b.longitude, b.accuracy, "dernière position connue, il y a $ageMin min"))
        } else {
            finish(
                LocationResult.Fail(
                    "Aucune position obtenue ($reason). Sors du local ou approche-toi d'une fenêtre, " +
                        "patiente quelques secondes, puis réessaie."
                )
            )
        }
    }

    try {
        val client = LocationServices.getFusedLocationProviderClient(ctx)
        val cts = CancellationTokenSource()
        main.postDelayed({ cts.cancel() }, 20_000)
        val priority = if (fine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        client.getCurrentLocation(priority, cts.token).addOnCompleteListener { t ->
            val loc = if (t.isSuccessful) t.result else null
            if (loc != null) {
                finish(LocationResult.Ok(loc.latitude, loc.longitude, loc.accuracy))
            } else {
                AppLog.w("gps", "Localisation fusionnée sans résultat : ${t.exception?.message ?: "délai dépassé / aucun signal"}")
                fallback("pas de signal en 20 s")
            }
        }
    } catch (e: Exception) {
        AppLog.e("gps", "Service de localisation indisponible", e)
        fallback("service de localisation indisponible")
    }
}
