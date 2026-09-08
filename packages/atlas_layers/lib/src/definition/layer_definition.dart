// Sovereign Atlas Engine — atlas_layers
// Layer definition, state, ordered stack, and attribution rule.
//
// Contracts: ATLAS-LAYER-001, ATLAS-MAP-ORDER-001, ATLAS-ATTR-001
// (layer-contract.md). Phase 1.2 expansion notes:
// - Identity is three-level and opaque: providerId (required) / datasetId
//   (optional) / id (required). Display title, provider id, URLs, and cache
//   keys are NEVER identity (§5). No URL/endpoint field exists anywhere in
//   this package (provider ban, §35).
// - Semantic category (BASEMAP/OVERLAY/LIVE/HISTORICAL/PERSONAL): PROPOSED →
//   PROVISIONAL descriptor field, optional (null = unclassified; no invented
//   default). Never a renderer concept.
// - Capabilities: PROPOSED → PROVISIONAL advertised-only flags. A layer saying
//   `offlineCapable` does NOT implement offline storage; `queryable` does NOT
//   implement querying (§9). Empty set = no claim (safe default).
// - Equality levels (§32): identity (id ==), definition (structural == below,
//   title EXCLUDED as presentation data per §11), state (full == incl. visible
//   and opacity). Same display name never merges distinct layers.
// - Opacity lives in STATE, never definition (§13), as a PROVISIONAL display
//   hint (semantic ownership undecided). No `enabled` dimension exists: only
//   visibility is contract-required (§22).
// - Canonical baseline order: SOURCE-VERIFIED sequence (F-07), normative as
//   the BASELINE composition. [AtlasLayerStack.conformsToBaseline] FLAGS
//   deviation instead of rejecting it: CANONICAL BASELINE ≠ VALIDITY
//   CONSTRAINT (0.5A Ruling 2; ADV-016 BLOCKED). Ties keep stable insertion
//   order (List semantics — the explicit tie-break, §18).
// - Attribution: visible PUBLIC layers with non-empty attribution are listed;
//   private/local-only layers are EXCLUDED (SOURCE-VERIFIED rule F-07,
//   ATLAS-NORMATIVE requirement). Compared as a SET, so no join format is
//   enshrined. Attribution ≠ identity ≠ provider identity (§23).
// - Source/provenance: opaque `providerId`/`datasetId` reference hooks only.
//   No authority inference from category (§25), no provenance duplication
//   (§24), no access mechanisms (§27). Temporal need is a `timeAware` flag
//   only (§28); valid/observation/publication split is recorded-future.
// - No dependency graph: unrepresentable by construction, so cycles are
//   impossible rather than handled (§20/21 deferred to a requiring contract).
// Phase 1.2. Depends on atlas_core only (no geo import until geometry is
// genuinely needed; no unused imports).

import '../../../../atlas_core/lib/atlas_core.dart';

/// Layer data category (PROPOSED taxonomy, blueprint Phase 2 / F-catalog).
/// Unchanged by Phase 1.2 (inventory verdict: already minimal).
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

/// Semantic layer category (directive §6, Phase 1.2 inventory).
///
/// PROPOSED → PROVISIONAL. Semantic buckets for composition policy, never
/// renderer concepts. Optional on definitions: null means unclassified, which
/// is valid (taxonomy is descriptive, not mandatory).
enum AtlasLayerCategory { base, overlay, live, historical, personal }

/// Advertised layer capability (directive §8/9, Phase 1.2 inventory).
///
/// PROPOSED → PROVISIONAL. Each value is a CLAIM consumed by the owning
/// subsystem, never an implementation inside `atlas_layers`:
/// - `offlineCapable`: some offline subsystem can serve this layer.
/// - `timeAware`: the layer has temporal semantics (valid/observation/
///   publication split is recorded-future, §28).
/// - `queryable`: a query subsystem may interrogate it.
/// - `selectable`: a selection workflow may target it.
enum AtlasLayerCapability { offlineCapable, timeAware, queryable, selectable }

/// Renderer-independent layer definition.
///
/// Identity is the opaque triple ([providerId], [datasetId], [id]). Human
/// [title] is presentation data: excluded from equality and hashing (§11).
/// There is deliberately NO url/endpoint field.
final class AtlasLayerDefinition {
  const AtlasLayerDefinition({
    required this.id,
    required this.kind,
    required this.providerId,
    this.datasetId,
    this.category,
    this.capabilities = const {},
    this.title,
    this.minZoom,
    this.maxZoom,
    this.attribution,
    this.isPrivate = false,
  });

  final AtlasId id;
  final AtlasLayerKind kind;

  /// Opaque provider identity (e.g. a future `usgs`). Never a URL, never a
  /// display name, never the layer identity (§4/5).
  final String providerId;

  /// Opaque dataset identity within the provider (e.g. dataset `X` for layer
  /// `Y`). Null = the layer addresses the provider directly.
  final String? datasetId;

  /// Semantic category. Null = unclassified (valid; no invented default).
  final AtlasLayerCategory? category;

  /// Advertised capabilities (claims only — see enum docs).
  final Set<AtlasLayerCapability> capabilities;

  /// Human-readable label. Presentation data: excluded from `==`/hashCode.
  /// Two layers may share a title and remain distinct (§11).
  final String? title;
  final double? minZoom;
  final double? maxZoom;

  /// Attribution text for this layer's provider (descriptor data).
  final String? attribution;

  /// Private/local-only layers are never attributed to public providers and
  /// never leak endpoints (security-baseline; ATTR-001).
  final bool isPrivate;

  /// Definition equality: structural minus [title] (§11/32). Identity-only
  /// comparison is `a.id == b.id`; state comparison is [AtlasLayerState.==].
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasLayerDefinition &&
          id == other.id &&
          kind == other.kind &&
          providerId == other.providerId &&
          datasetId == other.datasetId &&
          category == other.category &&
          _equalCapabilities(capabilities, other.capabilities) &&
          minZoom == other.minZoom &&
          maxZoom == other.maxZoom &&
          attribution == other.attribution &&
          isPrivate == other.isPrivate;

  static bool _equalCapabilities(
    Set<AtlasLayerCapability> a,
    Set<AtlasLayerCapability> b,
  ) => a.length == b.length && a.containsAll(b);

  @override
  int get hashCode => Object.hash(
    id,
    kind,
    providerId,
    datasetId,
    category,
    Object.hashAllUnordered(capabilities),
    minZoom,
    maxZoom,
    attribution,
    isPrivate,
  );
}

/// Per-layer runtime state. UI toggles WRITE this state from the adapter side
/// (CAP-R03 correction); the core only consumes the ordered collection.
///
/// Opacity is a PROVISIONAL display hint kept in state (never definition);
/// its semantic ownership is undecided (§13).
final class AtlasLayerState {
  const AtlasLayerState({
    required this.definition,
    this.visible = true,
    this.opacity = 1.0,
  });

  final AtlasLayerDefinition definition;
  final bool visible;
  final double opacity;

  /// Visibility flip. Deterministic, validated by construction (bool domain).
  AtlasLayerState toggled() => AtlasLayerState(
    definition: definition,
    visible: !visible,
    opacity: opacity,
  );

  /// Opacity change. Throws [AtlasRejectionException] (`INVALID_LAYER_STATE`)
  /// outside [0, 1] instead of clamping (no silent coercion).
  AtlasLayerState withOpacity(double value) {
    if (!value.isFinite || value < 0.0 || value > 1.0) {
      throw const AtlasRejectionException(
        AtlasRejection(
          'INVALID_LAYER_STATE',
          'Layer opacity must be within [0, 1] (PROPOSED range).',
        ),
      );
    }
    return AtlasLayerState(
      definition: definition,
      visible: visible,
      opacity: value,
    );
  }

  /// Copy with validated opacity (same rejection behavior as [withOpacity]).
  AtlasLayerState copyWith({bool? visible, double? opacity}) {
    final nextOpacity = opacity ?? this.opacity;
    if (!nextOpacity.isFinite || nextOpacity < 0.0 || nextOpacity > 1.0) {
      throw const AtlasRejectionException(
        AtlasRejection(
          'INVALID_LAYER_STATE',
          'Layer opacity must be within [0, 1] (PROPOSED range).',
        ),
      );
    }
    return AtlasLayerState(
      definition: definition,
      visible: visible ?? this.visible,
      opacity: nextOpacity,
    );
  }

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
/// Equal ranks keep stable insertion order (the explicit §18 tie-break).
final class AtlasLayerStack {
  const AtlasLayerStack(this.states);

  final List<AtlasLayerState> states;

  /// Structural validation: duplicate layer ids reject (`DUPLICATE_IDENTITY`).
  ///
  /// PROVISIONAL — NOT ATLAS-NORMATIVE (identity-integrity contract text, no
  /// DEC assigned). Duplicate ids break referenceability (§4: identity must
  /// serve references from other Atlas systems), so the STACK (not the
  /// baseline) refuses them. Empty stacks are VALID (fail-secure boot).
  AtlasValidation validate() {
    final seen = <String>{};
    for (final state in states) {
      final idCheck = AtlasIds.check(state.definition.id.value);
      if (!idCheck.isValid) return idCheck;
      if (!seen.add(state.definition.id.value)) {
        return const AtlasValidation.invalid(
          AtlasRejection(
            'DUPLICATE_IDENTITY',
            'Layer ids must be unique within a stack (PROVISIONAL).',
          ),
        );
      }
    }
    return const AtlasValidation.valid();
  }

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
      other is AtlasLayerStack && _equalStates(states, other.states);

  static bool _equalStates(List<AtlasLayerState> a, List<AtlasLayerState> b) {
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
