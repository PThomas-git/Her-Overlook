package fr.heroverlook.data

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Export ZIP pensé pour l'analyse sur PC :
 *  - export.json            : tout le relevé (structure complète)
 *  - sites.csv              : une ligne par site
 *  - checklist.csv          : une ligne par point de contrôle et par site (y compris non renseignés)
 *  - baies_equipements.csv  : une ligne par équipement (et par baie vide)
 *  - amenagement.csv        : écarts relevés par rapport au plan de référence
 *  - connu.csv              : informations préchargées et leur validation sur place (confirmé / corrigé)
 *  - sites/(id)/site.json + dossier sites/(id)/photos contenant les JPEG
 */
object Exporter {

    fun write(ctx: Context, sites: List<Site>, out: OutputStream) {
        val sorted = sites.sortedWith(compareBy({ it.kind.ordinal }, { it.id }))
        ZipOutputStream(BufferedOutputStream(out)).use { zip ->
            fun put(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("export.json", SiteStore.json.encodeToString(ListSerializer(Site.serializer()), sorted))
            put("sites.csv", sitesCsv(sorted))
            put("checklist.csv", checklistCsv(sorted))
            put("baies_equipements.csv", racksCsv(sorted))
            put("amenagement.csv", layoutCsv(sorted))
            put("connu.csv", knownCsv(sorted))
            put("plans_elements.csv", planCsv(sorted))
            put("journal.log", String(AppLog.bytes(), Charsets.UTF_8))

            for (s in sorted) {
                val base = "sites/${SiteStore.safeName(s.id)}"
                zip.setLevel(Deflater.DEFAULT_COMPRESSION)
                put("$base/site.json", SiteStore.json.encodeToString(Site.serializer(), s))
                val plan = s.layout.plan
                if (!plan.isBlank) {
                    // plan vectoriel (SVG, en cm) + image, pour reprise sur PC
                    put("$base/plan.svg", PlanRender.svg(plan, s.displayName()))
                    runCatching {
                        val bmp = PlanRender.bitmap(plan)
                        zip.putNextEntry(ZipEntry("$base/plan.png"))
                        bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, zip)
                        zip.closeEntry()
                        bmp.recycle()
                    }
                }
                // Les JPEG sont déjà compressés : on évite de perdre du temps à les recompresser.
                zip.setLevel(Deflater.NO_COMPRESSION)
                for (rel in s.allPhotos()) {
                    val f = File(ctx.filesDir, rel)
                    if (!f.exists()) continue
                    zip.putNextEntry(ZipEntry("$base/photos/${f.name}"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    private fun esc(v: String): String = "\"" + v.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ⏎ ") + "\""

    private fun csv(header: List<String>, rows: List<List<String>>): String {
        val sb = StringBuilder("﻿")
        sb.append(header.joinToString(";") { esc(it) }).append("\r\n")
        rows.forEach { r -> sb.append(r.joinToString(";") { esc(it) }).append("\r\n") }
        return sb.toString()
    }

    private fun names(photos: List<String>) = photos.joinToString(" | ") { it.substringAfterLast('/') }

    private fun sitesCsv(sites: List<Site>): String {
        val header = listOf(
            "id", "code_loc", "type", "nom", "adresse", "ville", "cp", "gps_inventaire", "gps_releve",
            "statut", "date_visite", "acces_moyens", "acces_resultat", "acces_note", "contact_sur_place",
            "points_total", "points_renseignes", "conformes", "reserves", "non_conformes",
            "nb_baies", "nb_equipements", "nb_usagers", "nb_photos", "analyse", "a_revoir", "note_inventaire"
        )
        val rows = sites.map { s ->
            val st = s.stats()
            listOf(
                s.id, s.locCode, s.kind.label, s.name, s.address, s.city, s.postalCode,
                if (s.lat != null && s.lon != null) "${s.lat}, ${s.lon}" else "", s.measuredGps,
                s.status.label, s.visitDate, s.accessMethods.joinToString(", "), s.accessResult.name, s.accessNote, s.onSiteContact,
                st.total.toString(), st.answered.toString(), st.ok.toString(), st.reserve.toString(), st.nc.toString(),
                s.racks.size.toString(), s.racks.sumOf { it.equipments.size }.toString(), s.usagers.size.toString(),
                s.allPhotos().size.toString(), s.analysis, s.revisit.joinToString(" ; "), s.inventoryNote
            )
        }
        return csv(header, rows)
    }

    private fun checklistCsv(sites: List<Site>): String {
        val header = listOf(
            "site_id", "site_nom", "type", "section", "point_id", "point", "ref", "etat", "valeur", "commentaire",
            "nb_photos", "photos", "connu_avant_visite", "visite_precedente_date", "visite_precedente_etat", "visite_precedente_commentaire"
        )
        val rows = ArrayList<List<String>>()
        for (s in sites) {
            val prev = s.rounds.lastOrNull()
            for (sec in Catalog.allSections(s.kind)) {
                for (item in sec.items) {
                    val e = s.entries[item.id] ?: Entry()
                    val pe = prev?.entries?.get(item.id)
                    rows += listOf(
                        s.id, s.name, s.kind.label, sec.title, item.id, item.label, item.ref,
                        e.verdict.name, e.value, e.comment, e.photos.size.toString(), names(e.photos),
                        s.prior[item.id] ?: "",
                        prev?.date ?: "", pe?.verdict?.name ?: "", pe?.comment ?: ""
                    )
                }
            }
        }
        return csv(header, rows)
    }

    private fun planCsv(sites: List<Site>): String {
        val header = listOf(
            "site_id", "site_nom", "element_id", "type", "libelle", "x_cm", "y_cm", "largeur_cm", "profondeur_cm",
            "rotation_deg", "baie_liee", "note"
        )
        val rows = ArrayList<List<String>>()
        for (s in sites) {
            for (it in s.layout.plan.items) {
                val rack = s.racks.firstOrNull { r -> r.id == it.rackId }?.label ?: ""
                rows += listOf(
                    s.id, s.name, it.id, it.kind.name, it.label, it.x.toString(), it.y.toString(),
                    it.w.toString(), it.h.toString(), it.rot.toString(), rack, it.note
                )
            }
        }
        return csv(header, rows)
    }

    private fun knownCsv(sites: List<Site>): String {
        val header = listOf("site_id", "site_nom", "info_id", "libelle", "valeur_connue", "source", "validation", "valeur_constatee")
        val rows = ArrayList<List<String>>()
        for (s in sites) {
            for (k in s.known) {
                rows += listOf(s.id, s.name, k.id, k.label, k.value, k.source, k.state.name, k.fix)
            }
        }
        return csv(header, rows)
    }

    private fun racksCsv(sites: List<Site>): String {
        val header = listOf(
            "site_id", "site_nom", "baie", "type_baie", "proprietaire", "usager_baie", "description_baie", "u_total", "u_libres",
            "jarretiérage", "commentaire_baie", "photos_baie",
            "equip_type", "marque", "modele", "numero_serie", "date_mise_en_service", "licence", "usager_equip", "commentaire_equip", "photos_equip"
        )
        val rows = ArrayList<List<String>>()
        for (s in sites) {
            for (r in s.racks) {
                val rackPart = listOf(
                    s.id, s.name, r.label, r.kind, r.owner, r.usager, r.description, r.totalU, r.freeU,
                    r.jumpers.name, r.comment, names(r.photos)
                )
                if (r.equipments.isEmpty()) {
                    rows += rackPart + List(9) { "" }
                } else {
                    for (e in r.equipments) {
                        rows += rackPart + listOf(
                            e.type, e.brand, e.model, e.serial, e.commissioningDate, e.license, e.usager, e.comment, names(e.photos)
                        )
                    }
                }
            }
        }
        return csv(header, rows)
    }

    private fun layoutCsv(sites: List<Site>): String {
        val header = listOf(
            "site_id", "site_nom", "plan_reference", "longueur_m", "largeur_m", "hauteur_m", "portes", "positions_baies",
            "commentaire", "ecart", "photos_ecart"
        )
        val rows = ArrayList<List<String>>()
        for (s in sites) {
            if (!s.kind.isPop) continue
            val l = s.layout
            val base = listOf(s.id, s.name, l.referencePlan, l.lengthM, l.widthM, l.heightM, l.doors, l.rackPositions, l.comment)
            if (l.deviations.isEmpty()) rows += base + listOf("", "")
            else l.deviations.forEach { rows += base + listOf(it.text, names(it.photos)) }
        }
        return csv(header, rows)
    }
}
