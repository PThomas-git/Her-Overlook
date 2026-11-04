package fr.heroverlook.data

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.location.Location
import android.location.LocationManager
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

object Photos {
    private fun dir(ctx: Context, siteId: String): File =
        File(ctx.filesDir, "photos/${SiteStore.safeName(siteId)}").apply { mkdirs() }

    /** Nom explicite : <site>_<contexte>_<horodatage>.jpg — utile à l'analyse sur PC. */
    fun newFile(ctx: Context, siteId: String, tag: String): File {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
        val t = tag.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(dir(ctx, siteId), "${SiteStore.safeName(siteId)}_${t}_$stamp.jpg")
    }

    fun rel(ctx: Context, f: File): String = f.relativeTo(ctx.filesDir).path.replace('\\', '/')

    fun abs(ctx: Context, rel: String): File = File(ctx.filesDir, rel)

    fun uriFor(ctx: Context, f: File) =
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)

    /** Réduit à 2560 px max, corrige l'orientation EXIF, JPEG qualité 85 (lisible pour les numéros de série). */
    fun compress(file: File, maxSide: Int = 2560, quality: Int = 85) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = BitmapFactory.decodeFile(file.path, opts) ?: return
            val deg = when (ExifInterface(file.path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            val longSide = max(bmp.width, bmp.height)
            val scale = if (longSide > maxSide) maxSide.toFloat() / longSide else 1f
            if (deg != 0f || scale < 1f) {
                val m = Matrix()
                m.postRotate(deg)
                m.postScale(scale, scale)
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }
            AppLog.i("photo", "Compressée : ${file.name} (${file.length() / 1024} Ko)")
        }.onFailure { AppLog.e("photo", "Compression échouée : ${file.name}", it) }
    }

    fun decodeSampled(file: File, maxSide: Int): Bitmap? {
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}

@SuppressLint("MissingPermission")
fun requestLocation(ctx: Context, onResult: (String?) -> Unit) {
    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val provider = when {
        lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else -> null
    }
    if (provider == null) {
        AppLog.w("gps", "Aucun fournisseur de localisation actif")
        onResult(null)
        return
    }
    AppLog.i("gps", "Demande de position ($provider)")
    lm.getCurrentLocation(provider, null, ctx.mainExecutor) { loc: Location? ->
        AppLog.i("gps", if (loc == null) "Position indisponible" else "Position obtenue (±${loc.accuracy.toInt()} m)")
        onResult(
            loc?.let {
                String.format(Locale.US, "%.6f, %.6f (±%.0f m)", it.latitude, it.longitude, it.accuracy)
            }
        )
    }
}
