// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.gis

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface GisRepository {
    val datasets: StateFlow<List<GisDataset>>

    fun addDataset(dataset: GisDataset)
    fun removeDataset(datasetId: String)
    fun setDatasetVisibility(datasetId: String, isVisible: Boolean)
    fun updateDatasetStyle(datasetId: String, style: GisStyle)
    fun getDataset(datasetId: String): GisDataset?
    fun getFeature(datasetId: String, featureId: String): GisFeature?
    fun clearAll()
}

class InMemoryGisRepository : GisRepository {

    private val _datasets = MutableStateFlow<List<GisDataset>>(emptyList())
    override val datasets: StateFlow<List<GisDataset>> = _datasets.asStateFlow()

    private val lock = Any()

    override fun addDataset(dataset: GisDataset) {
        synchronized(lock) {
            val current = _datasets.value.toMutableList()
            current.removeAll { it.id == dataset.id }
            current.add(dataset)
            _datasets.value = current
        }
    }

    override fun removeDataset(datasetId: String) {
        synchronized(lock) {
            val current = _datasets.value.toMutableList()
            current.removeAll { it.id == datasetId }
            _datasets.value = current
        }
    }

    override fun setDatasetVisibility(datasetId: String, isVisible: Boolean) {
        synchronized(lock) {
            val current = _datasets.value.map { dataset ->
                if (dataset.id == datasetId) {
                    dataset.copy(isVisible = isVisible)
                } else {
                    dataset
                }
            }
            _datasets.value = current
        }
    }

    override fun updateDatasetStyle(datasetId: String, style: GisStyle) {
        synchronized(lock) {
            val current = _datasets.value.map { dataset ->
                if (dataset.id == datasetId) {
                    dataset.copy(style = style)
                } else {
                    dataset
                }
            }
            _datasets.value = current
        }
    }

    override fun getDataset(datasetId: String): GisDataset? {
        synchronized(lock) {
            return _datasets.value.firstOrNull { it.id == datasetId }
        }
    }

    override fun getFeature(datasetId: String, featureId: String): GisFeature? {
        synchronized(lock) {
            val dataset = _datasets.value.firstOrNull { it.id == datasetId } ?: return null
            return dataset.features.firstOrNull { it.id == featureId }
        }
    }

    override fun clearAll() {
        synchronized(lock) {
            _datasets.value = emptyList()
        }
    }
}
