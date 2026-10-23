// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.gis

import com.sovereignatlas.atlas.core.LngLat
import com.sovereignatlas.atlas.geo.render.RenderGeometry
import com.sovereignatlas.atlas.geo.render.RenderProperty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GisRepositoryTest {

    @Test
    fun addAndRetrieveDataset() {
        val repo = InMemoryGisRepository()
        assertEquals(0, repo.datasets.value.size)

        val feature = GisFeature(
            id = "f-1",
            geometry = RenderGeometry.Point(LngLat(-105.0, 40.0)),
            properties = mapOf("name" to RenderProperty.Text("Observation Tower")),
        )
        val dataset = GisDataset(
            id = "ds-1",
            name = "Lookout Points",
            format = GisFormat.GEOJSON,
            srid = GisSrid.WGS84,
            featureCount = 1,
            features = listOf(feature),
        )

        repo.addDataset(dataset)
        assertEquals(1, repo.datasets.value.size)

        val retrieved = repo.getDataset("ds-1")
        assertNotNull(retrieved)
        assertEquals("Lookout Points", retrieved!!.name)

        val retrievedFeature = repo.getFeature("ds-1", "f-1")
        assertNotNull(retrievedFeature)
        assertEquals(RenderProperty.Text("Observation Tower"), retrievedFeature!!.properties["name"])
    }

    @Test
    fun updateVisibilityAndStyle() {
        val repo = InMemoryGisRepository()
        val dataset = GisDataset(
            id = "ds-2",
            name = "Perimeter",
            format = GisFormat.KML,
            srid = GisSrid.WGS84,
            featureCount = 0,
            features = emptyList(),
            isVisible = true,
        )
        repo.addDataset(dataset)

        repo.setDatasetVisibility("ds-2", false)
        assertFalse(repo.getDataset("ds-2")!!.isVisible)

        val newStyle = GisStyle(
            strokeColorHex = "#F59E0B",
            strokeWidthDp = 4.0f,
            fillColorHex = "#33F59E0B",
        )
        repo.updateDatasetStyle("ds-2", newStyle)
        val updated = repo.getDataset("ds-2")!!
        assertEquals("#F59E0B", updated.style.strokeColorHex)
        assertEquals(4.0f, updated.style.strokeWidthDp, 1e-4f)
    }

    @Test
    fun removeAndClearDatasets() {
        val repo = InMemoryGisRepository()
        val ds1 = GisDataset("ds-1", "Layer 1", GisFormat.GEOPACKAGE, GisSrid.WGS84, 0, emptyList())
        val ds2 = GisDataset("ds-2", "Layer 2", GisFormat.SHAPEFILE, GisSrid.WGS84, 0, emptyList())

        repo.addDataset(ds1)
        repo.addDataset(ds2)
        assertEquals(2, repo.datasets.value.size)

        repo.removeDataset("ds-1")
        assertEquals(1, repo.datasets.value.size)
        assertNull(repo.getDataset("ds-1"))
        assertNotNull(repo.getDataset("ds-2"))

        repo.clearAll()
        assertEquals(0, repo.datasets.value.size)
    }
}
