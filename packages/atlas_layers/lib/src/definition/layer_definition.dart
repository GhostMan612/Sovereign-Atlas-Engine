// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';

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

enum AtlasLayerCategory { base, overlay, live, historical, personal }

enum AtlasLayerCapability { offlineCapable, timeAware, queryable, selectable }

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

  final String providerId;

  final String? datasetId;

  final AtlasLayerCategory? category;

  final Set<AtlasLayerCapability> capabilities;

  final String? title;
  final double? minZoom;
  final double? maxZoom;

  final String? attribution;

  final bool isPrivate;

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
  ) =>
      a.length == b.length && a.containsAll(b);

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

final class AtlasLayerState {
  const AtlasLayerState({
    required this.definition,
    this.visible = true,
    this.opacity = 1.0,
  });

  final AtlasLayerDefinition definition;
  final bool visible;
  final double opacity;

  AtlasLayerState toggled() => AtlasLayerState(
        definition: definition,
        visible: !visible,
        opacity: opacity,
      );

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

final class AtlasLayerStack {
  const AtlasLayerStack(this.states);

  final List<AtlasLayerState> states;

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

  List<AtlasLayerState> orderedVisible() =>
      states.where((s) => s.visible).toList(growable: false);

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
