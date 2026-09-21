// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.layers

import com.sovereignatlas.atlas.core.AtlasContractException
import com.sovereignatlas.atlas.core.AtlasId
import com.sovereignatlas.atlas.core.AtlasIds
import com.sovereignatlas.atlas.core.AtlasRejection
import com.sovereignatlas.atlas.core.AtlasValidation

enum class AtlasLayerKind {
    raster,
    vector,
    geojson,
    elevation,
    historical,
    boundary,
    parcel,
    structure,
    localDataset,
}

enum class AtlasLayerCategory { base, overlay, live, historical, personal }

enum class AtlasLayerCapability { offlineCapable, timeAware, queryable, selectable }

data class AtlasLayerDefinition(
    val id: AtlasId,
    val kind: AtlasLayerKind,
    val providerId: String,
    val datasetId: String? = null,
    val category: AtlasLayerCategory? = null,
    val capabilities: Set<AtlasLayerCapability> = emptySet(),
    val title: String? = null,
    val minZoom: Double? = null,
    val maxZoom: Double? = null,
    val attribution: String? = null,
    val isPrivate: Boolean = false,
)

data class AtlasLayerState(
    val definition: AtlasLayerDefinition,
    val visible: Boolean = true,
    val opacity: Double = 1.0,
) {
    fun toggled(): AtlasLayerState = copy(visible = !visible)

    fun withOpacity(value: Double): AtlasLayerState {
        checkOpacity(value)
        return copy(opacity = value)
    }

    fun copyWith(visible: Boolean? = null, opacity: Double? = null): AtlasLayerState {
        val nextOpacity = opacity ?: this.opacity
        checkOpacity(nextOpacity)
        return copy(
            visible = visible ?: this.visible,
            opacity = nextOpacity,
        )
    }

    private fun checkOpacity(value: Double) {
        if (!value.isFinite() || value < 0.0 || value > 1.0) {
            throw AtlasContractException(
                AtlasRejection(
                    "INVALID_LAYER_STATE",
                    "Layer opacity must be within [0, 1] (PROPOSED range).",
                ),
            )
        }
    }
}

data class AtlasLayerStack(val states: List<AtlasLayerState>) {
    fun validate(): AtlasValidation {
        val seen = HashSet<String>()
        for (state in states) {
            val idCheck = AtlasIds.check(state.definition.id.value)
            if (!idCheck.isValid) return idCheck
            if (!seen.add(state.definition.id.value)) {
                return AtlasValidation.invalid(
                    AtlasRejection(
                        "DUPLICATE_IDENTITY",
                        "Layer ids must be unique within a stack (PROVISIONAL).",
                    ),
                )
            }
        }
        return AtlasValidation.valid()
    }

    fun orderedVisible(): List<AtlasLayerState> =
        states.filter { it.visible }

    fun conformsToBaseline(rankOf: (String) -> Int?): Boolean {
        var lastRank = -1
        for (state in orderedVisible()) {
            val rank = rankOf(state.definition.id.value) ?: continue
            if (rank < lastRank) return false
            lastRank = rank
        }
        return true
    }
}

object AtlasBaselineRanks {
    val ranks: List<String> = listOf(
        "raster-sources",
        "offline-graticule",
        "h3-heritage",
        "parcel-boundaries",
        "blueprint-structures",
        "migration-flows",
        "range-rings",
        "markers",
        "measurement-overlay-topmost",
    )

    fun rankOf(layerId: String): Int? {
        val rank = ranks.indexOf(layerId)
        return if (rank < 0) null else rank
    }
}

object AtlasAttribution {
    fun forVisible(stack: AtlasLayerStack): Set<String> {
        val result = linkedSetOf<String>()
        for (state in stack.orderedVisible()) {
            val definition = state.definition
            if (definition.isPrivate) continue
            val text = definition.attribution
            if (text == null || text.isEmpty()) continue
            result.add(text)
        }
        return result
    }
}
