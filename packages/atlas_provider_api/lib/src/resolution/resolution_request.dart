// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';
import '../provider/data_kind.dart';
import '../requests/tile_coordinate.dart';

final class AtlasResolutionRequest {
  const AtlasResolutionRequest({
    required this.kind,
    required this.latitude,
    required this.longitude,
    required this.zoom,
    this.scheme = AtlasTileScheme.xyz,
    this.preferredProviders = const [],
  });

  final AtlasDataKind kind;

  final double latitude;
  final double longitude;

  final double zoom;

  final AtlasTileScheme scheme;

  final List<AtlasId> preferredProviders;

  AtlasValidation validate() {
    if (!latitude.isFinite || !longitude.isFinite) {
      return const AtlasValidation.invalid(
        AtlasRejection('NON_FINITE', 'Resolution location must be finite.'),
      );
    }
    if (latitude < -90.0 ||
        latitude > 90.0 ||
        longitude < -180.0 ||
        longitude > 180.0) {
      return const AtlasValidation.invalid(
        AtlasRejection(
          'OUT_OF_RANGE',
          'Resolution location must satisfy ATLAS-COORD-001 bounds.',
        ),
      );
    }
    if (!zoom.isFinite || zoom < 0) {
      return const AtlasValidation.invalid(
        AtlasRejection(
          'INVALID_REQUEST',
          'Resolution zoom must be finite and non-negative.',
        ),
      );
    }
    return const AtlasValidation.valid();
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasResolutionRequest &&
          kind == other.kind &&
          latitude == other.latitude &&
          longitude == other.longitude &&
          zoom == other.zoom &&
          scheme == other.scheme &&
          _equalIds(preferredProviders, other.preferredProviders);

  static bool _equalIds(List<AtlasId> a, List<AtlasId> b) {
    if (a.length != b.length) return false;
    for (var i = 0; i < a.length; i++) {
      if (a[i] != b[i]) return false;
    }
    return true;
  }

  @override
  int get hashCode => Object.hash(
        kind,
        latitude,
        longitude,
        zoom,
        scheme,
        Object.hashAll(preferredProviders),
      );
}
