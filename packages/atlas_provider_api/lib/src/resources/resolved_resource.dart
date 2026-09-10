// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';
import '../provider/data_kind.dart';
import '../provider/provider_descriptor.dart';
import '../requests/tile_coordinate.dart';
import '../resolution/resolution_result.dart';
import 'resource_identity.dart';

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

  final AtlasTileCoordinate? tile;
  final AtlasTileScheme? scheme;

  final String? attribution;
  final String? license;
  final String? sensitivity;

  static String tileAddressFor(
    AtlasTileCoordinate tile,
    AtlasTileScheme scheme,
  ) =>
      'z=${tile.z}/x=${tile.x}/y=${tile.y}@${scheme.name}';

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
