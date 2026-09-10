// Sovereign Atlas Engine — atlas_provider_api
// AtlasResolutionRequest: semantic "what resource is needed" (never how).
//
// Contract: 1.5-B (inventory: PROPOSED → PROVISIONAL).
// Dimensions are exactly the contracted minimum: data kind, location,
// semantic zoom, tile scheme, explicit provider preference. No transport,
// renderer, URL, credential, or acquisition field exists or may be added
// without a contract.
// - Location is raw doubles (provider_api → core only; no geo dependency —
//   the ±90/±180 bounds below cite ATLAS-COORD-001 as authority and mirror it
//   without duplicating its policy role).
// - Zoom is a semantic float (camera zoom may be fractional); tile-zoom
//   derivation (floor) lives in tile_addressing.dart, not here.
// - Preference is an ORDERED explicit caller choice consumed only by selection
//   (never a relevance score; no "best provider" invention).
// Phase 1.5 slice. Depends on atlas_core only.

import 'package:atlas_core/atlas_core.dart';
import '../provider/data_kind.dart';
import '../requests/tile_coordinate.dart';

/// Renderer-independent semantic resource request.
final class AtlasResolutionRequest {
  const AtlasResolutionRequest({
    required this.kind,
    required this.latitude,
    required this.longitude,
    required this.zoom,
    this.scheme = AtlasTileScheme.xyz,
    this.preferredProviders = const [],
  });

  /// Requested data family (e.g. rasterTiles, elevation). Never tile-forced:
  /// non-tile kinds resolve without any tile address (1.5-H).
  final AtlasDataKind kind;

  /// Requested position in decimal degrees (WGS84 understanding).
  final double latitude;
  final double longitude;

  /// Requested semantic zoom. Finite, ≥ 0. Fractional values are meaningful
  /// (camera capability); integer tile-zoom derivation is separate.
  final double zoom;

  /// Tile addressing scheme (meaningful for tile kinds only; carried
  /// opaquely otherwise, never validated against kind here).
  final AtlasTileScheme scheme;

  /// Explicit caller preference order (provider ids). Selection applies this
  /// order against the eligible set; unknown ids are ignored deterministically.
  final List<AtlasId> preferredProviders;

  /// Request validity: finite in-range position, finite non-negative zoom.
  /// Coordinate bounds cite ATLAS-COORD-001 (±90/±180).
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
