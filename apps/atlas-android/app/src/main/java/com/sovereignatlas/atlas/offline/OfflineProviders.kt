// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.offline

data class OfflineProviderDescriptor(
    val id: String,
    val title: String,
    val urlTemplate: String?,
    val headers: Map<String, String> = emptyMap(),
    val params: Map<String, String> = emptyMap(),
    val minZoom: Int = 0,
    val maxZoom: Int = 19,
    val attribution: String,
    val license: String,
    val prefetchAllowed: Boolean,
    val bulkGuard: String? = null,
)

fun resolveTileUrl(
    descriptor: OfflineProviderDescriptor,
    z: Int,
    x: Int,
    y: Int,
): String? {
    val template = descriptor.urlTemplate ?: return null
    var url = template
        .replace("{z}", z.toString())
        .replace("{x}", x.toString())
        .replace("{y}", y.toString())
    for ((key, value) in descriptor.params) {
        url = url.replace("{$key}", value)
    }
    return url
}

object OfflineBuiltinProviders {
    val osmStandard = OfflineProviderDescriptor(
        id = "osm-standard",
        title = "OpenStreetMap Standard",
        urlTemplate = "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
        headers = mapOf(
            "User-Agent" to "SovereignAtlasEngine/0.1.0 (OSM Tile Usage Policy)",
        ),
        minZoom = 0,
        maxZoom = 19,
        attribution = "© OpenStreetMap contributors",
        license = "ODbL (openstreetmap.org/copyright)",
        prefetchAllowed = false,
        bulkGuard = "Bulk downloading is discouraged by the OSM Tile Usage Policy; " +
            "heavy prefetch requires explicit operator approval (Phase 3).",
    )
    val esriImagery = OfflineProviderDescriptor(
        id = "esri-imagery",
        title = "Esri World Imagery",
        urlTemplate = "https://server.arcgisonline.com/ArcGIS/rest/services/" +
            "World_Imagery/MapServer/tile/{z}/{y}/{x}",
        minZoom = 0,
        maxZoom = 19,
        attribution = "Esri, Maxar, Earthstar Geographics, and the GIS User Community",
        license = "Esri Terms of Use (arcgis.com)",
        prefetchAllowed = true,
    )
    val esriLightGray = OfflineProviderDescriptor(
        id = "esri-light-gray",
        title = "Esri Light Gray Canvas",
        urlTemplate = "https://server.arcgisonline.com/ArcGIS/rest/services/" +
            "Canvas/World_Light_Gray_Base/MapServer/tile/{z}/{y}/{x}",
        minZoom = 0,
        maxZoom = 16,
        attribution = "Esri, HERE, Garmin, OpenStreetMap contributors",
        license = "Esri Terms of Use (arcgis.com)",
        prefetchAllowed = true,
    )
    val esriDarkGray = OfflineProviderDescriptor(
        id = "esri-dark-gray",
        title = "Esri Dark Gray Canvas",
        urlTemplate = "https://server.arcgisonline.com/ArcGIS/rest/services/" +
            "Canvas/World_Dark_Gray_Base/MapServer/tile/{z}/{y}/{x}",
        minZoom = 0,
        maxZoom = 16,
        attribution = "Esri, HERE, Garmin, OpenStreetMap contributors",
        license = "Esri Terms of Use (arcgis.com)",
        prefetchAllowed = true,
    )
    val openTopoMap = OfflineProviderDescriptor(
        id = "opentopomap",
        title = "OpenTopoMap",
        urlTemplate = "https://{s}.tile.opentopomap.org/{z}/{x}/{y}.png",
        params = mapOf("s" to "a"),
        minZoom = 0,
        maxZoom = 17,
        attribution = "© OpenStreetMap contributors, SRTM | style: © OpenTopoMap (CC-BY-SA)",
        license = "CC-BY-SA (opentopomap.org)",
        prefetchAllowed = false,
    )
    val usgsTopo = OfflineProviderDescriptor(
        id = "usgs-topo",
        title = "USGS Topo (The National Map)",
        urlTemplate = "https://basemap.nationalmap.gov/arcgis/rest/services/" +
            "USGSTopo/MapServer/tile/{z}/{y}/{x}",
        minZoom = 0,
        maxZoom = 16,
        attribution = "USGS The National Map",
        license = "Public domain (USGS)",
        prefetchAllowed = true,
    )
    val localBundle = OfflineProviderDescriptor(
        id = "local-bundle",
        title = "Local Bundle (file tree)",
        urlTemplate = null,
        attribution = "Local bundle (provider attribution travels with data)",
        license = "Bundle license (travels with data)",
        prefetchAllowed = false,
    )
    val cartoPositron = OfflineProviderDescriptor(
        id = "carto-positron",
        title = "CARTO Positron",
        urlTemplate = "https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}.png",
        params = mapOf("s" to "a"),
        minZoom = 0,
        maxZoom = 20,
        attribution = "© OpenStreetMap contributors © CARTO",
        license = "CC-BY-SA (carto.com/attribution)",
        prefetchAllowed = false,
    )
    val cartoDarkMatter = OfflineProviderDescriptor(
        id = "carto-dark-matter",
        title = "CARTO Dark Matter",
        urlTemplate = "https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png",
        params = mapOf("s" to "a"),
        minZoom = 0,
        maxZoom = 20,
        attribution = "© OpenStreetMap contributors © CARTO",
        license = "CC-BY-SA (carto.com/attribution)",
        prefetchAllowed = false,
    )

    val all: List<OfflineProviderDescriptor> = listOf(
        osmStandard,
        esriImagery,
        esriLightGray,
        esriDarkGray,
        openTopoMap,
        usgsTopo,
        cartoPositron,
        cartoDarkMatter,
        localBundle,
    )

    fun lookup(id: String): OfflineProviderDescriptor? = all.firstOrNull { it.id == id }
}
