// Sovereign Atlas Engine — atlas_provider_api
// AtlasResolver: pure eligibility + deterministic selection (no contact).
//
// Contract: 1.5-D/E/G/H (inventory: PROPOSED → PROVISIONAL rules).
// Pipeline (all synchronous, deterministic, no I/O):
//   validate request → invalidRequest on failure
//   tile kinds: address location (polar → unsupported) ; non-tile: no address
//   kind filter → empty → unsupported (nothing serves this kind)
//   capability gate: tile kinds (rasterTiles/vectorTiles) additionally require
//     tileServing in descriptor capabilities (that flag's declared meaning);
//     non-tile kinds require kind membership only (zoom is meaningless there
//     and MUST NOT gate them — 1.5-H non-tile test)
//   zoom gate (tile kinds only): declared native [min,max] containing the tile
//     zoom; undeclared range never excludes (no assumption)
//   → empty → noMatch (message names kind count + blocking fact)
//   → one → resolved ; several + usable preference → preferred wins ;
//     several + none → ambiguous (all listed, none chosen)
// Explicitly absent: HTTP probing, health, latency, auth, download success,
// renderer compat, relevance ranking, coverage polygons (zoom range is the only
// spatial gate; geographic filtering stays DEFERRED per 1.4 ruling).
// Catalog order is significant (eligible output order); no sets, no maps, no
// clocks, no randomness, no globals. Phase 1.5 slice.

import '../../../../atlas_core/lib/atlas_core.dart';
import '../capabilities/provider_capability.dart';
import '../provider/data_kind.dart';
import '../provider/provider_descriptor.dart';
import '../requests/tile_coordinate.dart';
import 'resolution_request.dart';
import 'resolution_result.dart';
import 'tile_addressing.dart';

/// Pure resolution against an explicit provider catalog.
abstract final class AtlasResolver {
  /// Data kinds addressed through the tile grid. All other kinds resolve
  /// without tile addressing (1.5-H: never force elevation/boundary/parcel/
  /// historical/structure/local/geojson into z/x/y).
  static bool isTileKind(AtlasDataKind kind) =>
      kind == AtlasDataKind.rasterTiles || kind == AtlasDataKind.vectorTiles;

  /// Resolves [request] against [catalog] (catalog order significant).
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
        // Addressing unsupported here (e.g. polar latitude): the KIND may be
        // served, but no tile address exists — unsupported, not noMatch.
        return AtlasResolutionResult(
          request: request,
          status: AtlasResolutionStatus.unsupported,
          eligible: const [],
          reason: e.rejection.category,
        );
      }
    }
    final kindProviders = catalog
        .where((p) => p.kinds.contains(request.kind))
        .toList();
    if (kindProviders.isEmpty) {
      return AtlasResolutionResult(
        request: request,
        status: AtlasResolutionStatus.unsupported,
        eligible: const [],
        reason: 'no catalog provider serves ${request.kind.name}',
      );
    }
    final tileZoom = tileKind
        ? AtlasTileAddressing.tileZoomFor(request.zoom)
        : 0;
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
        reason:
            '${kindProviders.length} serve(s) ${request.kind.name}; '
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
