// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';
import '../capabilities/provider_capability.dart';
import '../provider/data_kind.dart';
import '../provider/provider_descriptor.dart';
import '../requests/tile_coordinate.dart';
import 'resolution_request.dart';
import 'resolution_result.dart';
import 'tile_addressing.dart';

abstract final class AtlasResolver {

  static bool isTileKind(AtlasDataKind kind) =>
      kind == AtlasDataKind.rasterTiles || kind == AtlasDataKind.vectorTiles;

  static AtlasResolutionResult resolve(
    AtlasResolutionRequest request,
    List<AtlasProviderDescriptor> catalog,
  ) {
    final requestCheck = request.validate();
    if (!requestCheck.isValid) {
      return AtlasResolutionResult(
        request: request,
        status: AtlasResolutionStatus.invalidRequest,
        eligible: const [],
        reason: requestCheck.rejection!.category,
      );
    }
    final tileKind = isTileKind(request.kind);
    AtlasTileCoordinate? tile;
    if (tileKind) {
      try {
        tile = AtlasTileAddressing.address(
          latitude: request.latitude,
          longitude: request.longitude,
          z: AtlasTileAddressing.tileZoomFor(request.zoom),
        );
      } on AtlasRejectionException catch (e) {

        return AtlasResolutionResult(
          request: request,
          status: AtlasResolutionStatus.unsupported,
          eligible: const [],
          reason: e.rejection.category,
        );
      }
    }
    final kindProviders =
        catalog.where((p) => p.kinds.contains(request.kind)).toList();
    if (kindProviders.isEmpty) {
      return AtlasResolutionResult(
        request: request,
        status: AtlasResolutionStatus.unsupported,
        eligible: const [],
        reason: 'no catalog provider serves ${request.kind.name}',
      );
    }
    final tileZoom =
        tileKind ? AtlasTileAddressing.tileZoomFor(request.zoom) : 0;
    final eligible = <AtlasProviderDescriptor>[];
    var blockedCapability = 0;
    var blockedZoom = 0;
    for (final provider in kindProviders) {
      if (tileKind &&
          !provider.capabilities.contains(
            AtlasProviderCapability.tileServing,
          )) {
        blockedCapability++;
        continue;
      }
      if (tileKind &&
          provider.nativeMinZoom != null &&
          tileZoom < provider.nativeMinZoom!) {
        blockedZoom++;
        continue;
      }
      if (tileKind &&
          provider.nativeMaxZoom != null &&
          tileZoom > provider.nativeMaxZoom!) {
        blockedZoom++;
        continue;
      }
      eligible.add(provider);
    }
    if (eligible.isEmpty) {
      final blockers = <String>[
        if (blockedCapability > 0) '$blockedCapability without tileServing',
        if (blockedZoom > 0) '$blockedZoom outside native zoom range',
      ].join('; ');
      return AtlasResolutionResult(
        request: request,
        status: AtlasResolutionStatus.noMatch,
        eligible: const [],
        reason: '${kindProviders.length} serve(s) ${request.kind.name}; '
            '0 eligible ($blockers)',
      );
    }
    if (eligible.length == 1) {
      return AtlasResolutionResult(
        request: request,
        status: AtlasResolutionStatus.resolved,
        eligible: [eligible.first.id],
        provider: eligible.first.id,
        tile: tile,
        reason: 'sole eligible provider',
      );
    }
    for (final preferred in request.preferredProviders) {
      if (eligible.any((p) => p.id == preferred)) {
        return AtlasResolutionResult(
          request: request,
          status: AtlasResolutionStatus.resolved,
          eligible: eligible.map((p) => p.id).toList(),
          provider: preferred,
          tile: tile,
          reason: 'explicit caller preference applied',
        );
      }
    }
    return AtlasResolutionResult(
      request: request,
      status: AtlasResolutionStatus.ambiguous,
      eligible: eligible.map((p) => p.id).toList(),
      reason:
          '${eligible.length} eligible; no usable preference (not resolved)',
    );
  }
}
