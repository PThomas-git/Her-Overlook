package fr.heroverlook.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DoorFront
import androidx.compose.material.icons.outlined.Fence
import androidx.compose.ui.graphics.vector.ImageVector

enum class ItemKind { CONFORMITY, PRESENCE }

data class Item(
    val id: String,
    val label: String,
    val hint: String = "",
    val kind: ItemKind = ItemKind.CONFORMITY,
    /** Si non nul, un champ de saisie libre est proposé (ex. date de mise en service). */
    val valueLabel: String? = null,
    /** Référence à la grille de la mission (ex. "3.14") ou aux réserves majeures. */
    val ref: String = "",
)

data class Section(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val items: List<Item>,
)

data class Stats(val total: Int, val answered: Int, val ok: Int, val reserve: Int, val nc: Int) {
    val progress: Float get() = if (total == 0) 0f else answered.toFloat() / total
}

object Catalog {

    private val jarretierage = Section(
        "jarretierage", "Jarretiérage", Icons.Outlined.Cable,
        listOf(
            Item("j_volantes", "Jarretières volantes ou de mauvaise longueur", "Réserve majeure : brassages à remettre au propre"),
            Item("j_sfp", "SFP débranchés / hors service", "À vérifier avec la supervision (lien actif ou résilié)"),
            Item("j_sol", "Jarretières au sol", "En dehors des espaces d'hébergement"),
            Item("j_etiq", "Étiquetage des jarretières optiques", "Absent ou illisible alors que le service est actif"),
        )
    )

    private val popSections = listOf(
        Section(
            "p_ext", "Accès et extérieur", Icons.Outlined.Fence,
            listOf(
                Item("p_portail", "Portail d'accès extérieur", "Ouverture / fermeture à clé ou badge, état, motorisation"),
                Item("p_serrure", "Serrure / ouverture du POP", "Tester la clé ou le badge réellement utilisé pour la fermeture"),
                Item("p_lecteur", "Lecteur de badge / gâche électrique", "Si présent : test d'ouverture"),
                Item("p_cloture", "Clôture", "Ne doit pas laisser passer une personne"),
                Item("p_broussailles", "Broussailles autour du POP", "À corriger si > 0,5 m"),
                Item("p_eclairage_ext", "Éclairage extérieur"),
                Item("p_exterieur", "État extérieur du local et de la toiture"),
                Item("p_rongeurs", "Étanchéité aux rongeurs", "Déjections au sol, points d'accès des nuisibles"),
            )
        ),
        Section(
            "p_local", "Local technique", Icons.Outlined.DoorFront,
            listOf(
                Item("p_porte", "Porte du POP", "Dégradée ou défaillante ?"),
                Item("p_etiq_risque", "Étiquette risque électrique sur la porte", "Présente et conforme à la réglementation"),
                Item("p_extincteurs", "Extincteurs", "Présence, conformité, date du dernier contrôle", valueLabel = "Date de dernier contrôle"),
                Item("p_proprete", "Propreté du local"),
                Item("p_secours", "Éclairage de sécurité"),
            )
        ),
        Section(
            "p_energie", "Énergie", Icons.Outlined.Bolt,
            listOf(
                Item("p_tgbt_plan", "Plan / schéma TGBT", "Affiché ou présent dans le POP", ref = "Réserve majeure"),
                Item("p_tgbt_etat", "TGBT : état général et étiquetage"),
                Item("p_ae", "Atelier d'énergie", "Redresseurs, qualité de l'énergie", valueLabel = "Marque / modèle"),
                Item("p_batteries", "Batteries", "Fuite, défaut, date limite dépassée", valueLabel = "Date de mise en service"),
                Item("p_ge", "Groupe électrogène", "Prise de relais ; sans objet si absent"),
                Item("p_compteur", "Compteur électrique", "Relever PRM / PDL et puissance souscrite", valueLabel = "N° PRM / PDL", ref = "3.13"),
            )
        ),
        Section(
            "p_clim", "Climatisation et alarmes", Icons.Outlined.AcUnit,
            listOf(
                Item("p_clim", "Climatisation", "Produit du froid, capacité de dissipation suffisante", valueLabel = "Température relevée (°C)"),
                Item("p_clim_secours", "Climatisation de secours"),
                Item("p_ventil", "Ventilation et filtres"),
                Item("p_alarmes", "Alarmes (porte, clim, gazole, énergie)", "Collectées au boîtier GTC ; test si possible"),
                Item("p_gtc", "GTC / télétransmetteur"),
                Item("p_eau_fumee", "Détection eau / fumée"),
            )
        ),
        jarretierage,
    )

    private val adrSections = listOf(
        Section(
            "a_ext", "Accès et environnement", Icons.Outlined.Fence,
            listOf(
                Item("a_acces", "Accès à l'armoire", "Stationnement, dégagement, accessibilité"),
                Item("a_broussailles", "Broussailles autour de l'armoire", "À corriger si > 1 m"),
            )
        ),
        Section(
            "a_armoire", "Armoire extérieure", Icons.Outlined.DoorFront,
            listOf(
                Item("a_structure", "Structure de l'armoire", "Dégradée au point d'empêcher l'exploitation ?"),
                Item("a_socle", "Fixation armoire / socle", "Désolidarisation"),
                Item("a_serrure", "Fermeture / serrure", "Tester avec la clé"),
                Item("a_porte", "Porte", "Hors service, trouée ?"),
                Item("a_etiq_risque", "Étiquette risque électrique"),
                Item("a_proprete", "Intérieur : propreté, humidité, nuisibles"),
            )
        ),
        Section(
            "a_energie", "Énergie", Icons.Outlined.Bolt,
            listOf(
                Item("a_tgbt_plan", "Plan TGBT", "Affiché ou présent dans l'armoire", ref = "Réserve majeure"),
                Item("a_ae", "Atelier d'énergie / onduleur", valueLabel = "Marque / modèle"),
                Item("a_batteries", "Batteries", "Fuite, date limite dépassée, hors d'usage", valueLabel = "Date de mise en service"),
                Item("a_compteur", "Compteur / alimentation", "Relever PRM / PDL", valueLabel = "N° PRM / PDL", ref = "3.13"),
            )
        ),
        Section(
            "a_clim", "Ventilation et alarmes", Icons.Outlined.AcUnit,
            listOf(
                Item("a_ventil", "Ventilation et filtre", "Fonctionnement, propreté du filtre"),
                Item("a_clim", "Climatisation (si présente)", valueLabel = "Température relevée (°C)"),
                Item("a_alarmes", "Alarmes / remontées"),
            )
        ),
        jarretierage,
    )

    private val docsPop = Section(
        "docs", "Documents et observations", Icons.Outlined.Description,
        listOf(
            Item("d_diuo", "DIUO", kind = ItemKind.PRESENCE),
            Item("d_prevention", "Plan de prévention", kind = ItemKind.PRESENCE),
            Item("d_maintenance", "Rapports de maintenance", "Carnet ou dernier rapport présent sur site ?", ItemKind.PRESENCE, ref = "3.14"),
            Item("d_plan_interieur", "Plan d'aménagement intérieur affiché", kind = ItemKind.PRESENCE, ref = "3.4"),
            Item("d_synoptique", "Synoptique / schémas réseau", kind = ItemKind.PRESENCE),
            Item("d_consignes", "Consignes de sécurité, numéros d'urgence", kind = ItemKind.PRESENCE),
            Item("d_convention", "Convention / panneau du propriétaire du terrain", kind = ItemKind.PRESENCE, ref = "3.3"),
            Item("d_licences", "Licences / étiquettes de licence sur les équipements", kind = ItemKind.PRESENCE, ref = "3.9"),
            Item("d_usagers", "Étiquetage ou liste des usagers", kind = ItemKind.PRESENCE, ref = "3.12"),
        )
    )

    private val docsAdr = docsPop.copy(
        items = docsPop.items.filter { it.id != "d_plan_interieur" }
    )

    fun checklist(kind: SiteKind): List<Section> = if (kind.isPop) popSections else adrSections

    fun documents(kind: SiteKind): Section = if (kind.isPop) docsPop else docsAdr

    fun allSections(kind: SiteKind): List<Section> = checklist(kind) + documents(kind)

    fun allItems(kind: SiteKind): List<Item> = allSections(kind).flatMap { it.items }
}

fun Site.stats(): Stats {
    val items = Catalog.allItems(kind)
    var answered = 0
    var ok = 0
    var reserve = 0
    var nc = 0
    for (it in items) {
        val v = entries[it.id]?.verdict ?: Verdict.UNSET
        if (v != Verdict.UNSET) answered++
        when (v) {
            Verdict.OK -> ok++
            Verdict.RESERVE -> reserve++
            Verdict.NC -> nc++
            else -> {}
        }
    }
    return Stats(items.size, answered, ok, reserve, nc)
}

object RackOwners {
    val all = listOf("HTel", "Tiers")
}

object RackKinds {
    val all = listOf("Active", "Énergie", "Batteries", "Optique / ODF", "TGBT", "Armoire ADR", "Emplacement vide", "Autre")
}

object EquipmentTypes {
    val all = listOf(
        "Routeur", "Switch", "OLT", "Transpondeur", "Convertisseur", "Atelier d'énergie", "Onduleur",
        "Batterie", "Climatisation", "GTC", "Télétransmetteur", "ODF", "Autre"
    )
}

object AccessMethods {
    val all = listOf("Clé", "Badge", "Code", "Garde / accompagnement", "Autre")
}
