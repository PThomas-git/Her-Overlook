package fr.heroverlook.data

import kotlinx.serialization.Serializable
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString().take(8)

@Serializable
enum class SiteKind(val label: String) {
    POP("POP"),
    ADR("ADR"),
    ADR_PLUS("ADR+");

    val isPop: Boolean get() = this == POP
}

/** UNSET = pas encore renseigné. OK/RESERVE/NC/NA = conforme / réserve / non conforme / sans objet. */
@Serializable
enum class Verdict { UNSET, OK, RESERVE, NC, NA }

@Serializable
enum class VisitStatus(val label: String) {
    TODO("À faire"),
    IN_PROGRESS("En cours"),
    DONE("Terminé")
}

@Serializable
data class Entry(
    val verdict: Verdict = Verdict.UNSET,
    val comment: String = "",
    val value: String = "",
    val photos: List<String> = emptyList(),
)

@Serializable
data class Equipment(
    val id: String = newId(),
    val type: String = "",
    val brand: String = "",
    val model: String = "",
    val serial: String = "",
    val commissioningDate: String = "",
    val license: String = "",
    val usager: String = "",
    val comment: String = "",
    val photos: List<String> = emptyList(),
)

@Serializable
data class Rack(
    val id: String = newId(),
    /** Numéro / position de la baie sur le plan d'aménagement (ex. "1", "8", "TGBT"). */
    val label: String = "",
    val kind: String = "Active",
    val usager: String = "",
    val description: String = "",
    val totalU: String = "",
    val freeU: String = "",
    val jumpers: Verdict = Verdict.UNSET,
    val comment: String = "",
    val photos: List<String> = emptyList(),
    val equipments: List<Equipment> = emptyList(),
)

@Serializable
data class Usager(
    val id: String = newId(),
    val name: String = "",
    val reference: String = "",
    val location: String = "",
    val comment: String = "",
)

@Serializable
data class Deviation(
    val id: String = newId(),
    val text: String = "",
    val photos: List<String> = emptyList(),
)

/** Relevé de l'aménagement intérieur (ligne 3.4). */
@Serializable
data class Layout(
    val referencePlan: String = "",
    val lengthM: String = "",
    val widthM: String = "",
    val heightM: String = "",
    val doors: String = "",
    val rackPositions: String = "",
    val comment: String = "",
    val photos: List<String> = emptyList(),
    val sketches: List<String> = emptyList(),
    val deviations: List<Deviation> = emptyList(),
)

@Serializable
data class Site(
    val id: String,
    val locCode: String = "",
    val kind: SiteKind = SiteKind.ADR,
    val name: String = "",
    val address: String = "",
    val city: String = "",
    val postalCode: String = "",
    val network: String = "",
    val tier: String = "",
    val activeEquipment: String = "",
    val cooling: String = "",
    val power: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
    val accessHO: String = "",
    val accessHNO: String = "",
    val inventoryNote: String = "",

    // --- relevé terrain ---
    val status: VisitStatus = VisitStatus.TODO,
    val visitDate: String = "",
    val measuredGps: String = "",
    val accessMethods: List<String> = emptyList(),
    val accessResult: Verdict = Verdict.UNSET,
    val accessNote: String = "",
    val onSiteContact: String = "",
    val accessPhotos: List<String> = emptyList(),
    val entries: Map<String, Entry> = emptyMap(),
    val layout: Layout = Layout(),
    val racks: List<Rack> = emptyList(),
    val usagers: List<Usager> = emptyList(),
    val generalPhotos: List<String> = emptyList(),
    val analysis: String = "",
    val revisit: List<String> = emptyList(),
    val updatedAt: Long = 0L,
)

/** Format du fichier d'inventaire importé (sites_inventaire.json). */
@Serializable
data class InvSite(
    val id: String,
    val loc: String = "",
    val kind: String = "ADR",
    val name: String = "",
    val address: String = "",
    val city: String = "",
    val postalCode: String = "",
    val network: String = "",
    val tier: String = "",
    val active: String = "",
    val cooling: String = "",
    val power: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
    val accessHO: String = "",
    val accessHNO: String = "",
    val note: String = "",
)

fun Site.allPhotos(): List<String> {
    val out = LinkedHashSet<String>()
    out += accessPhotos
    out += generalPhotos
    entries.values.forEach { out += it.photos }
    out += layout.photos
    out += layout.sketches
    layout.deviations.forEach { out += it.photos }
    racks.forEach { r ->
        out += r.photos
        r.equipments.forEach { out += it.photos }
    }
    return out.toList()
}
