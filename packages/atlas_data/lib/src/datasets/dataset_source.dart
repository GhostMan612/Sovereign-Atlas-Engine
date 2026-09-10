// Sovereign Atlas Engine — atlas_data
// Dataset source descriptor: live/remote dataset origins (Phase 6 support).
//
// Contract: blueprint Phase 6 (live data families need declared origins) +
// ADR-001 (atlas_data owns dataset descriptors). A source names the API
// root + family + policy hooks; FETCHING lives downstream (provider
// operations / dataset adapters). No URLs are followed here — this is
// declaration data, and key material has no field (secrets ADR open).
// Phase 5/6 slice. Depends on atlas_core only.

import 'package:atlas_core/atlas_core.dart';

/// Live dataset families with declared origins.
enum AtlasDatasetFamily { weather, elevation, signals, boundaries, custom }

/// Declared origin of a live dataset family.
final class AtlasDatasetSource {
  const AtlasDatasetSource({
    required this.id,
    required this.family,
    required this.apiRoot,
    this.updateCadence,
    this.license,
    this.attribution,
    this.requiresKey = false,
  });

  final AtlasId id;

  final AtlasDatasetFamily family;

  /// API root (declaration only — never followed by this package).
  final String apiRoot;
  final String? updateCadence;
  final String? license;
  final String? attribution;
  final bool requiresKey;

  AtlasValidation validate() {
    final idCheck = AtlasIds.check(id.value);
    if (!idCheck.isValid) return idCheck;
    if (apiRoot.isEmpty) {
      return const AtlasValidation.invalid(
        AtlasRejection(
          'INVALID_DATASET_SOURCE',
          'Dataset API roots must be non-empty.',
        ),
      );
    }
    return const AtlasValidation.valid();
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasDatasetSource &&
          id == other.id &&
          family == other.family &&
          apiRoot == other.apiRoot &&
          updateCadence == other.updateCadence &&
          license == other.license &&
          attribution == other.attribution &&
          requiresKey == other.requiresKey;

  @override
  int get hashCode => Object.hash(
        id,
        family,
        apiRoot,
        updateCadence,
        license,
        attribution,
        requiresKey,
      );
}
