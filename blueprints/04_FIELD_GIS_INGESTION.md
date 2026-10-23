# BP-04: Field GIS Ingestion Subsystem (GeoPackage, KML/KMZ, Shapefile)

> **Status:** APPROVED DESIGN SPECIFICATION (AMENDED 2026-10-23)  
> **Target:** Pure contracts in `gis/ingestion/`, Platform adapters in `gis/adapters/`, UI in `ui/gis/`  
> **Standards:** OGC GeoPackage 1.3, OGC KML 2.3, GeoJSON RFC 7946, ESRI Shapefile Technical Description

---

## 1. Executive Summary

Search-and-rescue teams, wildland firefighters, utility linemen, and tactical operators operate in environments where situational datasets (incident perimeters, search grids, critical infrastructure, hazard zones) are created in external tools like **CalTopo, ArcGIS, QGIS, or Google Earth**.

Currently, the Sovereign Atlas Engine consumes pre-compiled raster MBTiles and binary `.pack` archives. This blueprint designs a universal **Field GIS Ingestion Subsystem** that allows operators to import arbitrary GIS files directly into the live map without an internet connection or desktop conversion step.

---

## 2. Ingestion Matrix & Supported Formats

| Format | File Extension | Pure Domain Contract | Adapter Implementation | Geometric Types |
|---|---|---|---|---|
| **OGC GeoPackage** | `.gpkg` | `GeoPackageTableParser` + `WkbDecoder` | `AndroidSqliteGpkgAdapter` (`android.database.sqlite`) | Points, MultiPoints, LineStrings, Polygons |
| **Google Earth / CalTopo** | `.kml`, `.kmz` | `KmlXmlStreamingParser` | `AndroidXmlPullKmlAdapter` (`org.xmlpull.v1`) + `ZipStream` | Placemarks, Paths, Polygons, ExtendedData |
| **GeoJSON** | `.geojson`, `.json` | Pure `kotlinx.serialization.json` | Direct JVM Parser (no platform dependency) | RFC 7946 Features & Geometries |
| **Zipped Shapefile** | `.zip` (`.shp` + `.dbf` + `.shx`) | Pure ByteStream Shapefile & DBF Parser | `ZipStream` with Zip-Slip Guard | Points, PolyLines, Polygons + DBF attributes |

---

## 3. Architecture & Pure Boundary Discipline

Per `RULES.md` §2.1, pure logic modules must contain **zero Android framework imports**. All platform-specific classes (`android.database.sqlite.SQLiteDatabase`, `org.xmlpull.v1.XmlPullParser`, `android.net.Uri`) are isolated in platform adapter packages.

```text
               EXTERNAL STORAGE / USB
                          │
          [ .gpkg | .kml | .kmz | .geojson | .zip ]
                          │
            GisDatasetImporter (Platform Adapter)
                          │
         ┌────────────────┼────────────────┐
         ▼                ▼                ▼
   GeoPackageAdapter  KmlAdapter     ShapefileAdapter
   (Android SQLite)  (Android XML)   (ByteStream)
         │                │                │
         └────────────────┼────────────────┘
                          │
               GisFeatureSink (Pure Domain Contract)
         ├── id: String
         ├── name: String
         ├── crs: Srid (EPSG:4326 / EPSG:3857 / UTM)
         ├── features: List<RenderFeature>
         └── attributes: Map<String, Any?>
                          │
             UserLayerManager (Engine)
                          │
         FeatureSink / MapLibre GeoJsonSource
```

### 3.1 OGC GeoPackage Adapter
* **Header Parsing:** GeoPackage binary geometries begin with magic bytes `0x47 0x50` (`"GP"`), a 1-byte version, a 1-byte flags field (bit 0 = endianness, bits 1–3 = envelope indicator), followed by a 4-byte SRS ID and the envelope (0, 32, 48, or 64 bytes) preceding the standard ISO WKB geometry.
* **Adapter Placement:** SQLite querying occurs via `AndroidSqliteGpkgAdapter` in the app module; geometry byte arrays are handed to pure `WkbDecoder` in domain logic.

### 3.2 KML / KMZ Adapter
* **Streaming Unpacker:** For `.kmz`, extracts the root `.kml` stream via `java.util.zip.ZipInputStream` with canonical path verification (preventing zip-slip attacks).
* **Streaming XML:** Implemented via `AndroidXmlPullKmlAdapter` on Android, emitting pure `GisFeature` objects to `GisFeatureSink` without loading the full document DOM into memory.
* **Coordinate Ordering:** Standard KML coordinate strings are formatted as `lon,lat[,alt]`. The parser converts them directly to `AtlasCoordinate(lat, lon)`.

### 3.3 Shapefile & Projection Handling
* **ESRI Shapefile:** Ingests zipped `.shp` (geometry bytecode), `.shx` (index), `.dbf` (dBase III attribute table), and `.prj` (projection definition).
* **Supported Projections (unknown > invented):**
  - Native support for **EPSG:4326 (WGS84)**, **EPSG:3857 (Web Mercator)**, and standard **UTM zones** (using `MgrsEngine`).
  - If `.prj` specifies an unsupported local coordinate system without transform parameters, the importer explicitly refuses the dataset and reports the unsupported projection, preventing silent coordinate distortion.

---

## 4. UI/UX Workflow & Inspector

### 4.1 Ingestion Flow
1. Open **Sovereign Command Center** -> Tap **"Import Field GIS Dataset"**.
2. Android System File Picker (`ActivityResultContracts.OpenDocument`) filters for `.gpkg`, `.kml`, `.kmz`, `.geojson`, `.zip`.
3. Background parsing executes under `AtlasActivityBanner` micro-loader (`"Parsing CalTopo_Search_Grid.gpkg • 1,420 features..."`).
4. Dataset is assigned a default styling palette (Stroke `#39C5F3`, Fill `#2039C5F3`, Width 2dp) with an interactive color/opacity picker.

### 4.2 Interactive Feature Inspection
* Tapping any imported polygon, line, or point on the map queries MapLibre `queryRenderedFeatures`.
* Pops the **GIS Feature Inspector Sheet**:
  - Displays feature name, geometry type, perimeter, area (acres / hectares / sq km).
  - Clean table of all attribute metadata (e.g., `Agency: USFS`, `Status: Active Containment`, `Division: Alpha`).
  - One-tap "Navigate To", "Set As Geofence", or "Export to GPX".

---

## 5. Security & Resource Ceilings
1. **Pure Boundary:** Pure contracts in `gis/ingestion/` carry 0 Android framework imports, allowing fast host-side JVM unit testing.
2. **Zip Slip Guard:** All archive streams verify `entry.name` does not contain `..` or escape the temporary scratch directory.
3. **Vertex Ceilings:** Single layer imports are capped at 100,000 vertices to ensure smooth frame rates on entry-level hardware (Moto G / BLU View 5).
