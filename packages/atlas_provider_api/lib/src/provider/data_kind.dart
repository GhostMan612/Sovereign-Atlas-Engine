// Sovereign Atlas Engine — atlas_provider_api
// AtlasDataKind: provider data taxonomy.
//
// Contract: ATLAS-PROV-DESC-001 (provider-contract.md §2).
// Status: PROPOSED → PROVISIONAL (mirrors the DESC fixture families; slots
// such as elevation/historical carry no implementation — see DESC-004/005).
// This taxonomy is about WHAT a provider serves, never HOW it is fetched:
// no HTTP/raster-tile assumption is embedded (Phase 1.4 boundary).
// Phase 1.4 slice. No dependencies beyond dart:core.

/// Data families a provider may serve. Providers declare any non-empty subset.
enum AtlasDataKind {
  rasterTiles,
  vectorTiles,
  geojson,
  elevation,
  historical,
  boundary,
  parcel,
  structure,
  localDataset,
}
