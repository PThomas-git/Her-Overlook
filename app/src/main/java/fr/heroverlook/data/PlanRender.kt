package fr.heroverlook.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class PlanTheme(
    val bg: Int,
    val floor: Int,
    val grid: Int,
    val gridMajor: Int,
    val wall: Int,
    val text: Int,
    val dim: Int,
    val select: Int,
)

/**
 * Rendu du plan d'un local (unités : cm dans le plan, pixels à l'écran).
 * Un seul code de dessin sert à l'écran, à l'export PNG et sert de référence à l'export SVG.
 * Transformation : écran = (ox + x·scale, oy + y·scale).
 */
object PlanRender {
    val LIGHT = PlanTheme(
        bg = Color.WHITE,
        floor = 0xFFF4F0E6.toInt(),
        grid = 0x14000000,
        gridMajor = 0x33000000,
        wall = 0xFF2E261A.toInt(),
        text = 0xFF1B1914.toInt(),
        dim = 0xFF8A5A2B.toInt(),
        select = 0xFFD0006F.toInt(),
    )

    fun kindColor(k: PlanKind): Int = when (k) {
        PlanKind.RACK, PlanKind.RACK_SQ, PlanKind.RACK_WIDE -> 0xFF5B8DB8.toInt()
        PlanKind.ENERGY -> 0xFFE0A040.toInt()
        PlanKind.BATTERY -> 0xFFC9A227.toInt()
        PlanKind.ODF -> 0xFF8E6BB8.toInt()
        PlanKind.TGBT -> 0xFFC0504D.toInt()
        PlanKind.GTC -> 0xFFC9774D.toInt()
        PlanKind.CLIM_SPLIT, PlanKind.CLIM_CAB -> 0xFF4FA3A5.toInt()
        PlanKind.EXTINGUISHER -> 0xFFD0392E.toInt()
        PlanKind.DOOR, PlanKind.WINDOW -> 0xFF7A6F5C.toInt()
        PlanKind.TRAY -> 0xFF9A9283.toInt()
        PlanKind.PILLAR -> 0xFF6A6252.toInt()
        PlanKind.OTHER -> 0xFF8F8570.toInt()
    }

    private fun isRack(k: PlanKind) =
        k == PlanKind.RACK || k == PlanKind.RACK_SQ || k == PlanKind.RACK_WIDE ||
            k == PlanKind.ENERGY || k == PlanKind.BATTERY || k == PlanKind.ODF

    /** Demi-étendues d'un élément après rotation (cm). */
    private fun halfExtents(it: PlanItem): Pair<Float, Float> {
        val a = Math.toRadians(it.rot.toDouble())
        val c = abs(cos(a)).toFloat()
        val s = abs(sin(a)).toFloat()
        return Pair(it.w / 2f * c + it.h / 2f * s, it.w / 2f * s + it.h / 2f * c)
    }

    /** Rectangle englobant (cm) du contour et des éléments, ou null si le plan est vide. */
    fun bounds(plan: Plan): RectF? {
        var l = Float.MAX_VALUE
        var t = Float.MAX_VALUE
        var r = -Float.MAX_VALUE
        var b = -Float.MAX_VALUE
        var any = false
        fun add(x: Float, y: Float) {
            any = true
            if (x < l) l = x
            if (x > r) r = x
            if (y < t) t = y
            if (y > b) b = y
        }
        plan.outline.forEach { add(it.x.toFloat(), it.y.toFloat()) }
        plan.items.forEach {
            val (hx, hy) = halfExtents(it)
            add(it.x - hx, it.y - hy)
            add(it.x + hx, it.y + hy)
        }
        return if (any) RectF(l, t, r, b) else null
    }

    /** Élément touché au point (xCm, yCm), le plus haut dans l'empilement en premier. */
    fun hit(plan: Plan, xCm: Float, yCm: Float, tolCm: Float): PlanItem? {
        for (it in plan.items.asReversed()) {
            val dx = xCm - it.x
            val dy = yCm - it.y
            val a = Math.toRadians(-it.rot.toDouble())
            val rx = dx * cos(a) - dy * sin(a)
            val ry = dx * sin(a) + dy * cos(a)
            if (abs(rx) <= it.w / 2f + tolCm && abs(ry) <= it.h / 2f + tolCm) return it
        }
        return null
    }

    private fun fmtM(cm: Double): String = String.format(java.util.Locale.FRANCE, "%.2f m", cm / 100.0)

    // ------------------------------------------------------------------ dessin

    fun draw(
        c: Canvas,
        plan: Plan,
        scale: Float,
        ox: Float,
        oy: Float,
        viewW: Float,
        viewH: Float,
        selectedId: String?,
        t: PlanTheme,
        grid: Boolean,
        d: Float,
    ) {
        if (grid) drawGrid(c, scale, ox, oy, viewW, viewH, t)

        val o = plan.outline
        if (o.size >= 3) {
            val path = Path()
            o.forEachIndexed { i, p ->
                val x = ox + p.x * scale
                val y = oy + p.y * scale
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.style = Paint.Style.FILL
            p.color = t.floor
            c.drawPath(path, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = max(3f * d, 12f * scale)
            p.strokeJoin = Paint.Join.MITER
            p.color = t.wall
            c.drawPath(path, p)
            drawDims(c, o, scale, ox, oy, t, d)
        }

        plan.items.forEach { drawItem(c, it, scale, ox, oy, t, d) }

        if (selectedId != null) {
            plan.items.firstOrNull { it.id == selectedId }?.let { it0 ->
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                p.style = Paint.Style.STROKE
                p.strokeWidth = 2f * d
                p.color = t.select
                p.pathEffect = DashPathEffect(floatArrayOf(8f * d, 5f * d), 0f)
                c.save()
                c.translate(ox + it0.x * scale, oy + it0.y * scale)
                c.rotate(it0.rot.toFloat())
                val hw = it0.w * scale / 2f + 4f * d
                val hh = it0.h * scale / 2f + 4f * d
                c.drawRect(-hw, -hh, hw, hh, p)
                c.restore()
            }
        }
    }

    private fun drawGrid(c: Canvas, scale: Float, ox: Float, oy: Float, w: Float, h: Float, t: PlanTheme) {
        val p = Paint()
        p.strokeWidth = 1f
        fun lines(stepCm: Float, color: Int) {
            val step = stepCm * scale
            if (step < 7f) return
            p.color = color
            var x = ox % step
            if (x < 0) x += step
            while (x <= w) { c.drawLine(x, 0f, x, h, p); x += step }
            var y = oy % step
            if (y < 0) y += step
            while (y <= h) { c.drawLine(0f, y, w, y, p); y += step }
        }
        lines(10f, t.grid)
        lines(50f, t.gridMajor)
    }

    private fun drawDims(c: Canvas, o: List<PlanPoint>, scale: Float, ox: Float, oy: Float, t: PlanTheme, d: Float) {
        val tp = Paint(Paint.ANTI_ALIAS_FLAG)
        tp.color = t.dim
        tp.textSize = 12f * d
        tp.textAlign = Paint.Align.CENTER
        val n = o.size
        val cx = o.sumOf { it.x.toDouble() } / n
        val cy = o.sumOf { it.y.toDouble() } / n
        for (i in 0 until n) {
            val a = o[i]
            val b = o[(i + 1) % n]
            val dx = (b.x - a.x).toDouble()
            val dy = (b.y - a.y).toDouble()
            val len = hypot(dx, dy)
            if (len < 1.0) continue
            val mx = (a.x + b.x) / 2.0
            val my = (a.y + b.y) / 2.0
            var nx = dy / len
            var ny = -dx / len
            if ((mx - cx) * nx + (my - cy) * ny < 0) { nx = -nx; ny = -ny }
            val px = (ox + mx * scale + nx * 16f * d).toFloat()
            val py = (oy + my * scale + ny * 16f * d + 4f * d).toFloat()
            c.drawText(fmtM(len), px, py, tp)
        }
    }

    private fun drawItem(c: Canvas, it: PlanItem, scale: Float, ox: Float, oy: Float, t: PlanTheme, d: Float) {
        val cx = ox + it.x * scale
        val cy = oy + it.y * scale
        val w = it.w * scale
        val h = it.h * scale
        val col = kindColor(it.kind)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        c.save()
        c.translate(cx, cy)
        c.rotate(it.rot.toFloat())
        when (it.kind) {
            PlanKind.DOOR -> drawDoor(c, w, h, it.flip, t, col, d)
            PlanKind.WINDOW -> {
                p.style = Paint.Style.FILL
                p.color = (col and 0x00FFFFFF) or 0x40000000
                c.drawRect(-w / 2, -h / 2, w / 2, h / 2, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = 1.5f * d
                p.color = col
                c.drawRect(-w / 2, -h / 2, w / 2, h / 2, p)
                c.drawLine(-w / 2, 0f, w / 2, 0f, p)
            }
            PlanKind.EXTINGUISHER -> {
                p.style = Paint.Style.FILL
                p.color = col
                c.drawCircle(0f, 0f, max(w, h) / 2f, p)
            }
            PlanKind.TRAY -> {
                p.style = Paint.Style.STROKE
                p.strokeWidth = 1.5f * d
                p.color = col
                p.pathEffect = DashPathEffect(floatArrayOf(6f * d, 4f * d), 0f)
                c.drawRect(-w / 2, -h / 2, w / 2, h / 2, p)
            }
            else -> {
                p.style = Paint.Style.FILL
                p.color = (col and 0x00FFFFFF) or 0x66000000
                c.drawRect(-w / 2, -h / 2, w / 2, h / 2, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = 2f * d
                p.color = col
                c.drawRect(-w / 2, -h / 2, w / 2, h / 2, p)
                if (isRack(it.kind)) {
                    // face avant (accès) : trait épais sur le bord « bas » de l'élément avant rotation
                    p.strokeWidth = 4f * d
                    c.drawLine(-w / 2, h / 2, w / 2, h / 2, p)
                }
            }
        }
        c.restore()

        if (it.label.isNotBlank() && it.kind != PlanKind.DOOR) {
            val tp = Paint(Paint.ANTI_ALIAS_FLAG)
            tp.color = t.text
            tp.textAlign = Paint.Align.CENTER
            val (hx, hy) = halfExtents(it)
            val boxW = hx * 2 * scale
            val boxH = hy * 2 * scale
            tp.textSize = (min(boxW, boxH) * 0.34f).coerceIn(9f * d, 16f * d)
            val tw = tp.measureText(it.label)
            val maxW = max(boxW, 26f * d)
            if (tw > maxW) tp.textSize = max(8f * d, tp.textSize * maxW / tw)
            c.drawText(it.label, cx, cy + tp.textSize * 0.35f, tp)
        }
    }

    private fun drawDoor(c: Canvas, w: Float, h: Float, flip: Boolean, t: PlanTheme, col: Int, d: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // ouverture dans le mur
        p.style = Paint.Style.FILL
        p.color = t.floor
        c.drawRect(-w / 2, -h / 2 - 2f * d, w / 2, h / 2 + 2f * d, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f * d
        p.color = col
        val sgn = if (flip) 1f else -1f
        // battant ouvert à 90° (charnière à gauche) et arc de débattement
        c.drawLine(-w / 2, 0f, -w / 2, sgn * w, p)
        val arc = RectF(-w / 2 - w, -w, -w / 2 + w, w)
        if (!flip) c.drawArc(arc, -90f, 90f, false, p) else c.drawArc(arc, 0f, 90f, false, p)
        p.strokeWidth = 1f * d
        c.drawLine(-w / 2, 0f, w / 2, 0f, p)
    }

    // ------------------------------------------------------------------ exports

    /** Image PNG du plan (fond blanc, grille principale, barre d'échelle 1 m). */
    fun bitmap(plan: Plan, widthPx: Int = 1600): Bitmap {
        val b = bounds(plan) ?: RectF(0f, 0f, 600f, 400f)
        val margin = 70f
        val bw = max(b.width(), 100f) + 2 * margin
        val bh = max(b.height(), 100f) + 2 * margin
        val scale = widthPx / bw
        val heightPx = ceil(bh * scale).toInt().coerceAtLeast(200)
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(LIGHT.bg)
        val d = 2.5f
        draw(c, plan, scale, (-b.left + margin) * scale, (-b.top + margin) * scale, widthPx.toFloat(), heightPx.toFloat(), null, LIGHT, true, d)
        // barre d'échelle : 1 m
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = LIGHT.text
        p.strokeWidth = 3f * d
        val x0 = 20f * d
        val y0 = heightPx - 16f * d
        c.drawLine(x0, y0, x0 + 100f * scale, y0, p)
        p.textSize = 12f * d
        c.drawText("1 m", x0, y0 - 8f * d, p)
        return bmp
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /** Plan vectoriel (SVG, unités = cm) pour reprise sur PC. */
    fun svg(plan: Plan, title: String): String {
        val b = bounds(plan) ?: RectF(0f, 0f, 600f, 400f)
        val m = 70f
        val x0 = b.left - m
        val y0 = b.top - m
        val w = max(b.width(), 100f) + 2 * m
        val h = max(b.height(), 100f) + 2 * m
        fun hex(c: Int) = String.format("#%06X", c and 0xFFFFFF)
        val sb = StringBuilder()
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"$x0 $y0 $w $h\" width=\"${(w * 2).toInt()}\" height=\"${(h * 2).toInt()}\" font-family=\"sans-serif\">\n")
        sb.append("<title>${esc(title)}</title>\n<rect x=\"$x0\" y=\"$y0\" width=\"$w\" height=\"$h\" fill=\"#FFFFFF\"/>\n")
        val o = plan.outline
        if (o.size >= 3) {
            sb.append("<polygon points=\"${o.joinToString(" ") { "${it.x},${it.y}" }}\" fill=\"${hex(LIGHT.floor)}\" stroke=\"${hex(LIGHT.wall)}\" stroke-width=\"12\" stroke-linejoin=\"miter\"/>\n")
            val n = o.size
            val cx = o.sumOf { it.x.toDouble() } / n
            val cy = o.sumOf { it.y.toDouble() } / n
            for (i in 0 until n) {
                val a = o[i]
                val bb = o[(i + 1) % n]
                val dx = (bb.x - a.x).toDouble()
                val dy = (bb.y - a.y).toDouble()
                val len = hypot(dx, dy)
                if (len < 1.0) continue
                val mx = (a.x + bb.x) / 2.0
                val my = (a.y + bb.y) / 2.0
                var nx = dy / len
                var ny = -dx / len
                if ((mx - cx) * nx + (my - cy) * ny < 0) { nx = -nx; ny = -ny }
                sb.append("<text x=\"${mx + nx * 28}\" y=\"${my + ny * 28 + 5}\" font-size=\"16\" text-anchor=\"middle\" fill=\"${hex(LIGHT.dim)}\">${fmtM(len)}</text>\n")
            }
        }
        for (it in plan.items) {
            val col = hex(kindColor(it.kind))
            sb.append("<g id=\"${esc(it.id)}\" data-kind=\"${it.kind.name}\" transform=\"translate(${it.x} ${it.y}) rotate(${it.rot})\">\n")
            val hw = it.w / 2f
            val hh = it.h / 2f
            when (it.kind) {
                PlanKind.DOOR -> {
                    val wd = it.w.toFloat()
                    sb.append("<rect x=\"${-hw}\" y=\"${-hh - 2}\" width=\"${it.w}\" height=\"${it.h + 4}\" fill=\"${hex(LIGHT.floor)}\"/>\n")
                    if (!it.flip) {
                        sb.append("<path d=\"M ${-hw} 0 L ${-hw} ${-wd} A $wd $wd 0 0 1 $hw 0\" fill=\"none\" stroke=\"$col\" stroke-width=\"2\"/>\n")
                    } else {
                        sb.append("<path d=\"M ${-hw} 0 L ${-hw} $wd A $wd $wd 0 0 0 $hw 0\" fill=\"none\" stroke=\"$col\" stroke-width=\"2\"/>\n")
                    }
                    sb.append("<line x1=\"${-hw}\" y1=\"0\" x2=\"$hw\" y2=\"0\" stroke=\"$col\" stroke-width=\"1\"/>\n")
                }
                PlanKind.EXTINGUISHER -> sb.append("<circle r=\"${max(hw, hh)}\" fill=\"$col\"/>\n")
                PlanKind.TRAY -> sb.append("<rect x=\"${-hw}\" y=\"${-hh}\" width=\"${it.w}\" height=\"${it.h}\" fill=\"none\" stroke=\"$col\" stroke-width=\"2\" stroke-dasharray=\"8 5\"/>\n")
                else -> {
                    sb.append("<rect x=\"${-hw}\" y=\"${-hh}\" width=\"${it.w}\" height=\"${it.h}\" fill=\"$col\" fill-opacity=\"0.4\" stroke=\"$col\" stroke-width=\"2\"/>\n")
                    if (isRack(it.kind)) sb.append("<line x1=\"${-hw}\" y1=\"$hh\" x2=\"$hw\" y2=\"$hh\" stroke=\"$col\" stroke-width=\"5\"/>\n")
                }
            }
            sb.append("</g>\n")
            if (it.label.isNotBlank() && it.kind != PlanKind.DOOR) {
                sb.append("<text x=\"${it.x}\" y=\"${it.y + 5}\" font-size=\"14\" text-anchor=\"middle\" fill=\"${hex(LIGHT.text)}\">${esc(it.label)}</text>\n")
            }
        }
        sb.append("</svg>\n")
        return sb.toString()
    }
}
