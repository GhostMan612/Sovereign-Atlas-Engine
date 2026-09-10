// Sovereign Atlas Engine — atlas_provider_api
// AtlasResolvedResource: what resolution identified (never bytes obtained).
//
// Contract: 1.6-B/D/H (inventory: PROPOSED → PROVISIONAL).
// - Built ONLY from a resolved `AtlasResolutionResult` plus its descriptor via
//   `fromResolution` (throws INVALID_STATE otherwise — caller-contract
//   enforcement, matching parse-of-malformed precedent).
// - Tiled kinds carry their tile address (+ scheme); every other kind carries
//   no tile reference at all (1.6-D acceptance test: elevation/boundary/parcel/
//   historical/structure/local/geojson/vector-non-tiled resolve cleanly).
//   `AtlasDataKind` reuse means no second taxonomy (no duplication).
// - Attribution/license/sensitivity travel as OPAQUE descriptor hooks (no
//   provenance engine here — 1.6-B "where already contracted", nothing more).
// - Equality is identity-struct equality (1.6-O: resource identity is the
//   established equivalence; URLs/cache/bytes/timestamps never participate).
// Phase 1.6 slice. Depends on atlas_core (+ sibling descriptor/result) only.

import 'package:atlas_core/atlas_core.dart';
import '../provider/data_kind.dart';
import '../provider/provider_descriptor.dart';
import '../requests/tile_coordinate.dart';
import '../resolution/resolution_result.dart';
import 'resource_identity.dart';

/// A resolved (identified, not acquired) geographic resource.
final class AtlasResolvedResource {
  const AtlasResolvedResource({
    required this.identity,
    required this.provider,
    required this.kind,
    this.tile,
    this.scheme,
    this.attribution,
    this.license,
    this.sensitivity,
  });

  final AtlasResourceIdentity identity;
  final AtlasId provider;
  final AtlasDataKind kind;

  /// Tile address for tile kinds; null for every other kind (never forced).
  final AtlasTileCoordinate? tile;
  final AtlasTileScheme? scheme;

  /// Opaque descriptor hooks (attribution/license/sensitivity strings).
  final String? attribution;
  final String? license;
  final String? sensitivity;

  /// Canonical tile-address rendering for identity addresses (documented form,
  /// never a URL): `z=<z>/x=<x>/y=<y>@<scheme>`.
  static String tileAddressFor(
    AtlasTileCoordinate tile,
    AtlasTileScheme scheme,
  ) =>
      'z=${tile.z}/x=${tile.x}/y=${tile.y}@${scheme.name}';

  /// Binds a resolved result to its descriptor. Throws
  /// [AtlasRejectionException] (`INVALID_STATE`) unless the result resolved.
  factory AtlasResolvedResource.fromResolution({
    required AtlasResolutionResult result,
    required AtlasProviderDescriptor provider,
  }) {
    if (result.status != AtlasResolutionStatus.resolved ||
        result.provider == null) {
      throw const AtlasRejectionException(
        AtlasRejection(
          'INVALID_STATE',
          'Resolved resources bind resolved results only.',
        ),
      );
    }
    final tileKind = result.tile != null;
    return AtlasResolvedResource(
      identity: AtlasResourceIdentity(
        provider: result.provider!,
        kind: result.request.kind,
        address:
            tileKind ? tileAddressFor(result.tile!, result.request.scheme) : '',
      ),
      provider: result.provider!,
      kind: result.request.kind,
      tile: result.tile,
      scheme: tileKind ? result.request.scheme : null,
      attribution: provider.attribution,
      license: provider.license,
      sensitivity: provider.sensitivity,
    );
  }

  AtlasValidation validate() => identity.validate();

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasResolvedResource && identity == other.identity;

  @override
  int get hashCode => identity.hashCode;

  @override
  String toString() => 'AtlasResolvedResource($identity)';
}
