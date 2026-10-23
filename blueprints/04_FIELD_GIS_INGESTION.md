# BP-04: Field GIS Ingestion Subsystem (GeoPackage, KML/KMZ, Shapefile)

> **Status:** APPROVED DESIGN SPECIFICATION  
> **Target:** `gis/ingestion/`, `layers/user/`, `ui/gis/`, `apps/atlas-android/`  
> **Standards:** OGC GeoPackage 1.3, OGC KML 2.3, GeoJSON RFC 7946, ESRI Shapefile Technical Description

---

## 1. Executive Summary

Search-and-rescue teams, wildland firefighters, utility linemen, and military planners operate in environments where situational datasets (incident perimeters, search grids, critical infrastructure, hazard zones) are created in external tools like **CalTopo, ArcGIS, QGIS, or Google Earth**.

Currently, the Sovereign Atlas Engine consumes pre-compiled raster MBTiles and binary `.pack` archives. This blueprint designs a universal **Field GIS Ingestion Subsystem** that allows operators to import arbitrary GIS files directly into the live map without an internet connection or desktop conversion step.

---

## 2. Ingestion Matrix & Supported Formats

| Format | File Extension | Engine Decoder | Geometric Types |
|---|---|---|---|
| **OGC GeoPackage** | `.gpkg` | Pure SQLite Reader + WKB Parser | Points, MultiPoints, LineStrings, Polygons, Multipolygons |
| **Google Earth / CalTopo** | `.kml`, `.kmz` | Streaming XML Pull Parser + Zip Unpacker | Placemarks, Paths, Polygons, GroundOverlays, ExtendedData |
| **GeoJSON** | `.geojson`, `.json` | Pure `kotlinx.serialization.json` | FeatureCollection, Features, Geometries |
| **Zipped Shapefile** | `.zip` (`.shp` + `.dbf` + `.shx`) | Pure ByteStream Shapefile Parser | Points, PolyLines, Polygons + DBF Attribute Table |

---

## 3. Pure Domain Architecture (`gis/ingestion/`)

```text
               EXTERNAL STORAGE / USB
                          │
          [ .gpkg | .kml | .kmz | .geojson | .zip ]
                          │
              GisDatasetImporter (Interface)
                          │
         ┌────────────────┼────────────────┐
         ▼                ▼                ▼
   GeoPackageReader   KmlParser     ShapefileReader
         │                │                │
         └────────────────┼────────────────┘
                          │
               GisFeatureCollection (Pure)
         ├── id: String
         ├── name: String
         ├── crs: CoordinateReferenceSystem (EPSG:4326)
         ├── features: List<GisFeature>
         └── attributes: Map<String, Any?>
                          │
             UserLayerManager (Engine)
                          │
         MapLibre Vector Source & Fill/Line Layers
```

### 3.1 OGC GeoPackage Reader
* A `.gpkg` file is a standard SQLite 3 database.
* The importer queries `gpkg_contents` to discover vector feature tables (`data_type = 'features'`).
* Queries `gpkg_geometry_columns` for geometry column names and SRID.
* Decodes the Standard GeoPackage Binary Geometry Header (flags, envelope, WKB payload) into pure `AtlasCoordinate` arrays.
* Extracts row columns into feature properties for inspector sheets.

### 3.2 KML / KMZ Unpacker
* For `.kmz`, extracts `doc.kml` and local image icons in-memory via `java.util.zip.ZipInputStream`.
* Uses `XmlPullParser` in streaming mode (low memory footprint, handles 50MB+ KMLs without OutOfMemoryError).
* Extracts `<coordinates>`, `<Style>` (stroke color, fill color, line width), and `<ExtendedData>` key-values.

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

## 5. Security & Purity Verification
1. **Purity Boundary:** The geometry conversion and WKB/KML parsers live strictly in pure logic (`gis/ingestion/`) with 0 Android framework imports.
2. **Zip Slip & Path Traversal Guard:** KMZ and Shapefile ZIP extraction enforces canonical path checks to prevent archive extraction vulnerabilities.
3. **Memory Ceilings:** Streaming parsing caps feature batches to 100,000 vertices per layer to preserve 60fps MapLibre rendering on budget hardware (BLU View 5).
