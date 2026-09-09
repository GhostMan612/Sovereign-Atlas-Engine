// Sovereign Atlas Engine — atlas_provider_api
// AtlasResolutionResult: deterministic resolution outcome (never acquisition).
//
// Contract: 1.5-F (inventory: implemented statuses only).
// Statuses and their exact meaning:
// - resolved: one provider selected (sole eligible, or explicit preference
//   applied); tile address present for tile kinds, null otherwise.
// - noMatch: the kind IS served by the catalog but no provider is eligible
//   (capability/zoom gates); message names the blocking fact.
// - unsupported: nothing to match — no catalog provider serves the kind, or
//   tile addressing itself is unsupported here (e.g. polar latitude).
// - invalidRequest: the request fails validation (follows its rejection).
// - ambiguous: several eligible, no usable preference — all listed, none
//   chosen (1.5-G: ambiguity represented, never silently resolved).
// `Deferred` is deliberately ABSENT (nothing defers inside resolution).
// The result carries: request echo, provider id, tile ADDRESS (coordinate +
// scheme — never a full TileIdentity, because layers (not requests) own the
// layer dimension; provider identity stays separate by construction), the
// eligible set in catalog order, and a deterministic reason string. No bytes,
// no HTTP, no cache entries, no paths, no renderer objects (1.5-F/J/K).
// Phase 1.5 slice. Depends on atlas_core (+ sibling request/coordinate) only.

import '../../../../atlas_core/lib/atlas_core.dart';
import '../requests/tile_coordinate.dart';
import 'resolution_request.dart';

/// Resolution outcome status. Only contract-justified values exist.
enum AtlasResolutionStatus {
  resolved,
  noMatch,
  unsupported,
  invalidRequest,
  ambiguous,
}

/// Deterministic outcome of resolving one request against one catalog.
final class AtlasResolutionResult {
  const AtlasResolutionResult({
    required this.request,
    required this.status,
    required this.eligible,
    this.provider,
    this.tile,
    this.reason = '',
  });

  final AtlasResolutionRequest request;
  final AtlasResolutionStatus status;

  /// Eligible providers in catalog order (deterministic; empty unless the
  /// request matched: resolved carries the winner, ambiguous carries all).
  final List<AtlasId> eligible;

  /// Selected provider (resolved only; null otherwise).
  final AtlasId? provider;

  /// Resolved tile address for tile kinds (null for non-tile kinds and for
  /// any non-resolved status). Address only — layer binding happens downstream.
  final AtlasTileCoordinate? tile;

  /// Deterministic human-readable reason (counts/ids, never timestamps).
  final String reason;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasResolutionResult &&
          request == other.request &&
          status == other.status &&
          provider == other.provider &&
          tile == other.tile &&
          reason == other.reason &&
          _equalIds(eligible, other.eligible);

  static bool _equalIds(List<AtlasId> a, List<AtlasId> b) {
    if (a.length != b.length) return false;
    for (var i = 0; i < a.length; i++) {
      if (a[i] != b[i]) return false;
    }
    return true;
  }

  @override
  int get hashCode => Object.hash(
    request,
    status,
    provider,
    tile,
    reason,
    Object.hashAll(eligible),
  );
}
