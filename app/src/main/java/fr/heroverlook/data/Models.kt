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

/** État de validation d'une information déjà connue (rapports de maintenance, fiches site…). */
@Serializable
enum class KnownState { TODO, OK, FIX }

/** Information préchargée à valider ou corriger sur place. */
@Serializable
data class Known(
    val id: String,
    val label: String,
    val value: String,
    val source: String = "",
    val state: KnownState = KnownState.TODO,
    val fix: String = "",
)

/** Archive d'une visite précédente (créée au lancement d'une contre-visite). */
@Serializable
data class VisitRound(
    val date: String = "",
    val entries: Map<String, Entry> = emptyMap(),
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

    // --- données préchargées / contre-visite ---
    val known: List<Known> = emptyList(),
    /** Observation connue par point de contrôle (id du point -> texte), affichée dans la check-list. */
    val prior: Map<String, String> = emptyMap(),
    val rounds: List<VisitRound> = emptyList(),
    /** Points à revoir lors de la contre-visite en cours. */
    val revisitIds: List<String> = emptyList(),
)

@Serializable
data class InvKnown(
    val id: String,
    val label: String,
    val value: String,
    val source: String = "",
)

@Serializable
data class InvEquip(
    val type: String = "",
    val brand: String = "",
    val model: String = "",
    val commissioningDate: String = "",
    val comment: String = "",
)

@Serializable
data class InvRack(
    val label: String,
    val kind: String = "Active",
    val description: String = "",
    val usager: String = "",
    val equipments: List<InvEquip> = emptyList(),
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
    val known: List<InvKnown> = emptyList(),
    val prior: Map<String, String> = emptyMap(),
    /** Valeurs pré-remplies par point de contrôle (id du point -> valeur), à valider sur place. */
    val prefill: Map<String, String> = emptyMap(),
    val racks: List<InvRack> = emptyList(),
)

fun Site.allPhotos(): List<String> {
    val out = LinkedHashSet<String>()
    out += accessPhotos
    out += generalPhotos
    entries.values.forEach { out += it.photos }
    rounds.forEach { r -> r.entries.values.forEach { out += it.photos } }
    out += layout.photos
    out += layout.sketches
    layout.deviations.forEach { out += it.photos }
    racks.forEach { r ->
        out += r.photos
        r.equipments.forEach { out += it.photos }
    }
    return out.toList()
}
