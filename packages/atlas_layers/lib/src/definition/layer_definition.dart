// Sovereign Atlas Engine — atlas_layers
// Layer definition, state, ordered stack, and attribution rule.
//
// Contracts: ATLAS-LAYER-001, ATLAS-MAP-ORDER-001, ATLAS-ATTR-001
// (layer-contract.md).
// - Identity/type/metadata/ordering/visibility/provider-identity are DISTINCT
//   fields; no URL field exists anywhere in this package (provider ban).
// - Kind taxonomy (9 categories): PROPOSED (blueprint Phase 2).
// - Canonical baseline order: SOURCE-VERIFIED sequence (F-07), normative as
//   the BASELINE composition. Explicit orders are always accepted;
///  [AtlasLayerStack.conformsToBaseline] FLAGS deviation instead of rejecting
//   it, because the architect review holds the baseline must not become a
//   universal law for every renderer (see ADV-016 tension note).
// - Attribution: visible PUBLIC layers with non-empty attribution are listed;
//   private/local-only layers are EXCLUDED (SOURCE-VERIFIED rule F-07,
//   ATLAS-NORMATIVE requirement). Exact join format is runner-compared as a
//   SET, not a string, so no format is enshrined here.
// Phase 0.5 slice. Depends on atlas_core + atlas_geo (identifier types only
// via core; geo unused at runtime — dependency declared for future spatial
// layer extents, currently unused to keep the slice honest... see note below).
//
// NOTE: this file imports atlas_core only. The atlas_geo dependency allowed by
// dependency-map.md is NOT exercised yet; no import is declared until a layer
// concept genuinely needs geometry. Governor: no unused imports.

import '../../../../atlas_core/lib/atlas_core.dart';

/// Layer data category (PROPOSED taxonomy, blueprint Phase 2 / F-catalog).
enum AtlasLayerKind {
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

/// Renderer-independent layer definition.
///
/// [providerId] names the data provider. There is deliberately NO url/endpoint
/// field: concrete tile URLs, keys, and credentials MUST NOT enter Atlas
/// packages (extraction-matrix forbidden moves; provider-contract §1).
final class AtlasLayerDefinition {
  const AtlasLayerDefinition({
    required this.id,
    required this.kind,
    required this.providerId,
    this.title,
    this.minZoom,
    this.maxZoom,
    this.attribution,
    this.isPrivate = false,
  });

  final AtlasId id;
  final AtlasLayerKind kind;
  final String providerId;
  final String? title;
  final double? minZoom;
  final double? maxZoom;

  /// Attribution text for this layer's provider (descriptor data).
  final String? attribution;

  /// Private/local-only layers are never attributed to public providers and
  /// never leak endpoints (security-baseline; ATTR-001).
  final bool isPrivate;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasLayerDefinition &&
          id == other.id &&
          kind == other.kind &&
          providerId == other.providerId &&
          title == other.title &&
          minZoom == other.minZoom &&
          maxZoom == other.maxZoom &&
          attribution == other.attribution &&
          isPrivate == other.isPrivate;

  @override
  int get hashCode => Object.hash(
        id,
        kind,
        providerId,
        title,
        minZoom,
        maxZoom,
        attribution,
        isPrivate,
      );
}

/// Per-layer runtime state. UI toggles WRITE this state from the adapter side
/// (CAP-R03 correction); the core only consumes the ordered collection.
final class AtlasLayerState {
  const AtlasLayerState({
    required this.definition,
    this.visible = true,
    this.opacity = 1.0,
  });

  final AtlasLayerDefinition definition;
  final bool visible;
  final double opacity;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasLayerState &&
          definition == other.definition &&
          visible == other.visible &&
          opacity == other.opacity;

  @override
  int get hashCode => Object.hash(definition, visible, opacity);
}

/// Ordered layer composition. Order is significant (determinism-policy).
///
/// Any explicit order is accepted — ordering intent belongs to the composer
/// (adapter or spec). [conformsToBaseline] checks the canonical F-07 rank
/// sequence without enforcing it: CANONICAL BASELINE ≠ VALIDITY CONSTRAINT
/// (0.5A Ruling 2; ADV-016 stays BLOCKED pending contract clarification).
final class AtlasLayerStack {
  const AtlasLayerStack(this.states);

  final List<AtlasLayerState> states;

  /// Visible states in stack order (bottom → top).
  List<AtlasLayerState> orderedVisible() =>
      states.where((s) => s.visible).toList(growable: false);

  /// True when the visible layer-id sequence, projected through [rankOf],
  /// is non-decreasing in canonical rank. Unknown ids are ignored (future
  /// layer groups MUST NOT break the baseline check).
  bool conformsToBaseline(int? Function(String layerId) rankOf) {
    var lastRank = -1;
    for (final state in orderedVisible()) {
      final rank = rankOf(state.definition.id.value);
      if (rank == null) continue;
      if (rank < lastRank) return false;
      lastRank = rank;
    }
    return true;
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasLayerStack &&
          _equalStates(states, other.states);

  static bool _equalStates(
    List<AtlasLayerState> a,
    List<AtlasLayerState> b,
  ) {
    if (a.length != b.length) return false;
    for (var i = 0; i < a.length; i++) {
      if (a[i] != b[i]) return false;
    }
    return true;
  }

  @override
  int get hashCode => Object.hashAll(states);
}

/// Canonical baseline ranks (SOURCE-VERIFIED F-07 sequence, ATLAS-NORMATIVE
/// as baseline). Maps layer-id to rank; unknown ids yield null (ignored).
abstract final class AtlasBaselineRanks {
  static const List<String> ranks = [
    'raster-sources',
    'offline-graticule',
    'h3-heritage',
    'parcel-boundaries',
    'blueprint-structures',
    'migration-flows',
    'range-rings',
    'markers',
    'measurement-overlay-topmost',
  ];

  static int? rankOf(String layerId) {
    final rank = ranks.indexOf(layerId);
    return rank < 0 ? null : rank;
  }
}

/// Attribution derived from the visible set (ATLAS-ATTR-001).
///
/// Returns the SET of attribution strings from visible, non-private layers
/// with non-empty attribution. Private layers are excluded (SOURCE-VERIFIED
/// IMAGERY exclusion, ATLAS-NORMATIVE requirement).
abstract final class AtlasAttribution {
  static Set<String> forVisible(AtlasLayerStack stack) {
    final result = <String>{};
    for (final state in stack.orderedVisible()) {
      final definition = state.definition;
      if (definition.isPrivate) continue;
      final text = definition.attribution;
      if (text == null || text.isEmpty) continue;
      result.add(text);
    }
    return result;
  }
}
