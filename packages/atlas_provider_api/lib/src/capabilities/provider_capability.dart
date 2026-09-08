// Sovereign Atlas Engine — atlas_provider_api
// AtlasProviderCapability: advertised provider capabilities.
//
// Contract: ATLAS-PROV-DESC-001 (provider-contract.md).
// Status: PROPOSED → PROVISIONAL, advertised-only. A provider claiming
// `tileServing` does NOT implement fetching here; acquisition lives outside
// this package (deferred with atlas_tiles). Distinct from layer capability
// claims (AtlasLayerCapability): different claimant, different consumer —
// layers advertise compositional needs, providers advertise serving abilities.
// Empty set = no claim (safe default, never an implicit promise).
// Phase 1.4 slice. No dependencies beyond dart:core.

/// Serving abilities a provider may advertise.
enum AtlasProviderCapability {
  /// Serves addressable tiles (raster and/or vector).
  tileServing,

  /// Can back offline packs (policy + data shape TBD with atlas_offline).
  offlinePacks,

  /// Answers feature/data queries (spatial query engine TBD).
  queryable,

  /// Publishes refreshed/live data (freshness model TBD, Phase 6).
  liveRefresh,
}
