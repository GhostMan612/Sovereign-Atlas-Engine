// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.core.knowledge

import com.sovereignatlas.atlas.core.AtlasBoundingBox

class HistoricalKnowledgeIndex {

    private val dossiersById = mutableMapOf<String, HistoricalDossier>()

    fun insert(dossier: HistoricalDossier) {
        dossiersById[dossier.id] = dossier
    }

    fun insertAll(dossiers: Collection<HistoricalDossier>) {
        for (dossier in dossiers) {
            dossiersById[dossier.id] = dossier
        }
    }

    fun get(id: String): HistoricalDossier? {
        return dossiersById[id]
    }

    fun count(): Int = dossiersById.size

    fun querySpatial(box: AtlasBoundingBox): List<HistoricalDossier> {
        return dossiersById.values.filter { it.isInBounds(box) }
    }

    fun queryTemporal(year: Int): List<HistoricalDossier> {
        return dossiersById.values.filter { it.temporal.containsYear(year) }
    }

    fun query(box: AtlasBoundingBox, year: Int? = null, entityType: EntityType? = null): List<HistoricalDossier> {
        return dossiersById.values.filter { dossier ->
            val matchSpatial = dossier.isInBounds(box)
            val matchTemporal = year == null || dossier.temporal.containsYear(year)
            val matchType = entityType == null || dossier.entityType == entityType
            matchSpatial && matchTemporal && matchType
        }
    }

    fun search(queryText: String): List<HistoricalDossier> {
        if (queryText.isBlank()) return emptyList()
        val queryLower = queryText.trim().lowercase()

        return dossiersById.values.filter { dossier ->
            dossier.name.lowercase().contains(queryLower) ||
                dossier.description.lowercase().contains(queryLower) ||
                dossier.tags.any { it.lowercase().contains(queryLower) } ||
                dossier.primaryCitation.title.lowercase().contains(queryLower) ||
                dossier.primaryCitation.authorOrArchive.lowercase().contains(queryLower)
        }
    }

    fun clear() {
        dossiersById.clear()
    }
}
