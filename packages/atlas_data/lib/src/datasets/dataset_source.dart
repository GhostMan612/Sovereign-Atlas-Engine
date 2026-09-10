// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';

enum AtlasDatasetFamily { weather, elevation, signals, boundaries, custom }

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
