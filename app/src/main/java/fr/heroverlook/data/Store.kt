package fr.heroverlook.data

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.Executors

class SiteStore(ctx: Context) {
    private val dir = File(ctx.filesDir, "sites").apply { mkdirs() }
    private val io = Executors.newSingleThreadExecutor()

    private fun fileFor(id: String) = File(dir, safeName(id) + ".json")

    fun loadAll(): List<Site> =
        dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { f ->
                runCatching { json.decodeFromString(Site.serializer(), f.readText()) }.getOrNull()
            }
            ?: emptyList()

    fun save(site: Site) {
        io.execute {
            runCatching {
                val tmp = File(dir, safeName(site.id) + ".json.tmp")
                tmp.writeText(json.encodeToString(Site.serializer(), site))
                tmp.renameTo(fileFor(site.id))
            }
        }
    }

    fun delete(id: String) {
        io.execute { runCatching { fileFor(id).delete() } }
    }

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
        }

        fun safeName(id: String): String = id.replace(Regex("[^A-Za-z0-9_-]"), "_")

        /** Accepte un tableau JSON de sites (InvSite). */
        fun parseInventory(text: String): List<InvSite> =
            json.decodeFromString(ListSerializer(InvSite.serializer()), text)

        /** Relie les éléments du plan aux fiches de baie de même libellé. */
        fun linkPlan(plan: Plan, racks: List<Rack>): Plan = plan.copy(
            items = plan.items.map { it0 ->
                if (it0.rackId == null && it0.label.isNotBlank()) {
                    val r = racks.firstOrNull { r -> r.label == it0.label }
                    if (r != null) it0.copy(rackId = r.id) else it0
                } else it0
            }
        )

        fun fromInventory(i: InvSite): Site {
            val racks = i.racks.map { r ->
                Rack(
                    label = r.label, kind = r.kind, description = r.description, usager = r.usager,
                    equipments = r.equipments.map { e ->
                        Equipment(type = e.type, brand = e.brand, model = e.model, commissioningDate = e.commissioningDate, comment = e.comment)
                    },
                )
            }
            return Site(
            id = i.id,
            locCode = i.loc,
            kind = when (i.kind.uppercase()) {
                "POP" -> SiteKind.POP
                "ADR_PLUS", "ADR+" -> SiteKind.ADR_PLUS
                else -> SiteKind.ADR
            },
            name = i.name,
            address = i.address,
            city = i.city,
            postalCode = i.postalCode,
            network = i.network,
            tier = i.tier,
            activeEquipment = i.active,
            cooling = i.cooling,
            power = i.power,
            lat = i.lat,
            lon = i.lon,
            accessHO = i.accessHO,
            accessHNO = i.accessHNO,
            inventoryNote = i.note,
            known = i.known.map { Known(id = it.id, label = it.label, value = it.value, source = it.source) },
            prior = i.prior,
            entries = i.prefill.mapValues { Entry(value = it.value) },
            racks = racks,
            layout = Layout(plan = i.plan?.let { linkPlan(it, racks) } ?: Plan()),
            )
        }

        fun demoSite(): Site = Site(
            id = "DEMO-0001-EXEMPLE",
            kind = SiteKind.POP,
            name = "SITE DE DÉMO",
            address = "Rue de l'Exemple",
            city = "MONTPELLIER",
            postalCode = "34000",
            tier = "NRO",
            activeEquipment = "OUI",
            cooling = "OUI",
            power = "OUI",
            lat = 43.6108,
            lon = 3.8767,
            accessHO = "OK Clé",
            accessHNO = "OK Clé",
            inventoryNote = "Site fictif pour tester l'application.",
        )
    }
}
