// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.core

import org.junit.Assert.assertEquals
import org.junit.Test

final class VectorLayersTest {
    @Test
    fun extractsTopLevelLayerIds() {
        val json = """
            {"vector_layers":[{"id":"parcels","fields":{"name":"String"}}]}
        """.trimIndent()
        assertEquals(listOf("parcels"), parseVectorLayerIds(json))
    }

    @Test
    fun nestedFieldIdsAreIgnored() {
        // fields[] entries carry their own "id" keys; those are attribute names,
        // not source layers, and must not leak into the layer list.
        val json = """
            {
              "vector_layers": [
                {
                  "id": "parcels",
                  "fields": {"id": "parcel_id", "name": "Name", "year": "Year"},
                  "minzoom": 0,
                  "maxzoom": 14
                },
                {
                  "id": "patents",
                  "fields": {"id": "patent_id", "title": "Title"},
                  "description": "id is a field here, not a layer"
                }
              ]
            }
        """.trimIndent()
        assertEquals(listOf("parcels", "patents"), parseVectorLayerIds(json))
    }

    @Test
    fun multipleLayersPreserveMetadataOrder() {
        val json = """
            {"vector_layers":[{"id":"a"},{"id":"b"},{"id":"c"}]}
        """.trimIndent()
        assertEquals(listOf("a", "b", "c"), parseVectorLayerIds(json))
    }

    @Test
    fun nullAndBlankMetadataYieldEmpty() {
        assertEquals(emptyList<String>(), parseVectorLayerIds(null))
        assertEquals(emptyList<String>(), parseVectorLayerIds(""))
        assertEquals(emptyList<String>(), parseVectorLayerIds("   "))
    }

    @Test
    fun missingOrWrongShapedVectorLayersYieldEmpty() {
        assertEquals(emptyList<String>(), parseVectorLayerIds("""{"name":"pack"}"""))
        assertEquals(emptyList<String>(), parseVectorLayerIds("""{"vector_layers":{}}"""))
        assertEquals(emptyList<String>(), parseVectorLayerIds("""{"vector_layers":[]}"""))
    }

    @Test
    fun malformedJsonYieldsEmptyInsteadOfThrowing() {
        assertEquals(emptyList<String>(), parseVectorLayerIds("{not json"))
        assertEquals(emptyList<String>(), parseVectorLayerIds("[]"))
        assertEquals(emptyList<String>(), parseVectorLayerIds("null"))
    }

    @Test
    fun entriesWithoutIdAreSkipped() {
        val json = """{"vector_layers":[{"fields":{}},{"id":"ok"}]}"""
        assertEquals(listOf("ok"), parseVectorLayerIds(json))
    }
}
