# BP-02: True 3D DEM Terrain & Dynamic 2D Viewshed Mask

> **Status:** APPROVED DESIGN SPECIFICATION  
> **Target:** `geo/elevation/`, `map/terrain/`, `ui/hud/`, `apps/atlas-android/`  
> **Prerequisites:** BP-01 Modular Map Architecture

---

## 1. Executive Summary

While the Sovereign Atlas currently includes a USGS 3DEP Hillshade raster layer and a 1D Line-of-Sight (LoS) raycast profile chart, it renders in an orthographic 2D top-down plane.

This blueprint establishes:
1. **MapLibre Native 3D DEM Mesh:** Ingesting offline `raster-dem` tiles (Terrarium encoding) into MapLibre's native terrain engine, enabling real 3D terrain elevation, camera pitch/tilt (`0°..65°`), and dynamic hillshading based on sun azimuth and altitude.
2. **2D Radial Viewshed Polygon:** Upgrading the 1D LoS profile raycaster into a 360-degree, terrain-aware radial viewshed engine that calculates and paints visible vs. obscured observer zones directly on the map as a dynamic vector polygon.

---

## 2. Terrain DEM Architecture

```text
       OFFLINE STORAGE (.pack / .mbtiles)
                      │
           Terrarium RGB Tiles (z10-z14)
                      │
               LocalTileServer (127.0.0.1)
                      │
             MapLibre Style JSON
        "sources": {
          "terrain-source": {
            "type": "raster-dem",
            "tiles": ["http://127.0.0.1:{port}/terrain/{z}/{x}/{y}.png"],
            "encoding": "terrarium",
            "tileSize": 256
          }
        }
        "terrain": {
          "source": "terrain-source",
          "exaggeration": 1.2
        }
```

### 2.1 Elevation Decoding Math (Pure Domain `geo/elevation/`)
Terrarium format encodes elevation in meters into 24-bit RGB values:
$$\text{Elevation (meters)} = (R \times 256 + G + \frac{B}{256}) - 32768$$

```kotlin
object TerrariumDecoder {
    fun decodeRgbToElevation(r: Int, g: Int, b: Int): Double {
        return (r * 256.0 + g.toDouble() + b / 256.0) - 32768.0
    }
}
```

### 2.2 Offline Terrain Pack Packaging
* Terrain elevation packs use standard `.mbtiles` or `.pack` format containing 256x256 PNGs encoded in Terrarium format.
* Ingested through `PackTileServer` over localhost loopback (`127.0.0.1`) with the existing SHA-256 sidecar validation guard.

---

## 3. Dynamic 2D Viewshed Mask Engine

### 3.1 Pure Viewshed Algorithm (`geo/viewshed/ViewshedEngine.kt`)
The current `LosCalculator` shoots a single 1D ray from Observer $(lat_1, lon_1, alt_1)$ to Target $(lat_2, lon_2, alt_2)$.

The **Viewshed Engine** generalizes this into $N$ radial bearings (e.g., 360 rays at 1° intervals, or 720 rays at 0.5° intervals) out to a configurable maximum radius (e.g., 5 km – 25 km):

```kotlin
data class ViewshedConfig(
    val observer: AtlasCoordinate,
    val observerHeightAglMeters: Double = 2.0, // Above Ground Level (standing height)
    val targetHeightAglMeters: Double = 0.0,   // Ground level visibility
    val maxRadiusMeters: Double = 15000.0,     // Range ceiling
    val rayStepMeters: Double = 25.0,          // Sample resolution
    val angularStepDeg: Double = 1.0           // Radial density (360 rays)
)

data class ViewshedPolygon(
    val visibleSectors: List<List<AtlasCoordinate>>,
    val obscuredSectors: List<List<AtlasCoordinate>>,
    val maxRangeCircle: List<AtlasCoordinate>
)
```

### 3.2 Curvature and Atmospheric Refraction
For ranges beyond 5 km, earth curvature and standard atmospheric refraction ($k \approx 0.13$) are applied to each sample height $h(d)$:
$$\Delta h(d) = \frac{d^2}{2 R_e} \times (1 - k)$$
where $R_e \approx 6,371,000 \text{ m}$.

### 3.3 MapLibre Viewshed Layer Integration
The computed `ViewshedPolygon` is pushed to `MapFeatureSync.kt` as a `GeoJsonSource` (`VIEWSHED_SOURCE`) and rendered via:
* `FillLayer` (`VIEWSHED_OBSCURED_LAYER`): Semi-transparent dark charcoal (`#8010151C`) overlay over terrain that cannot be seen from the observer point.
* `LineLayer` (`VIEWSHED_PERIMETER_LAYER`): Razor navigation cyan (`#39C5F3`) line at the line-of-sight boundary.

---

## 4. UI/UX 3D Camera Controls

1. **Two-Finger Pitch Gesture:**
   * Two-finger drag up/down tilts the camera between $0^\circ$ (nadir 2D top-down) and $60^\circ$ (3D perspective).
2. **Pitch Indicator Badge:**
   * When camera pitch $> 5^\circ$, display a `3D • PITCH: 45°` HUD indicator next to the scale bar.
   * Tapping the badge smoothly animates pitch back to $0^\circ$ (instant 2D reset).
3. **Terrain Wireframe Toggle:**
   * In Studio workspace, an optional wireframe mesh mode renders topographic contours in vector space.

---

## 5. Implementation Roadmap
* **Step 1:** Pure `TerrariumDecoder.kt` and `ViewshedEngine.kt` in pure `geo/` with 100% deterministic unit tests.
* **Step 2:** Add `terrain` and `raster-dem` support in `MapStyleSync.kt`.
* **Step 3:** Hook two-finger pitch tilt into `AtlasMap.kt` camera controller.
* **Step 4:** Integrate Viewshed tool into the Sovereign Command Center and Tactical HUD.
