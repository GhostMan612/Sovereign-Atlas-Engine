// Sovereign Atlas Engine — atlas_provider_api
// Materialization: the minimal honest step past "resolved", before acquisition.
//
// Contract: 1.6-E/F (inventory: PROPOSED → PROVISIONAL; the justified smaller
// equivalent of a lifecycle — no state machine, no async, no transport).
// Statuses implemented (and why the rest are not):
// - ready: a tiled resource + caller-supplied template yields an acquisition
//   REPRESENTATION string via existing pure substitution mechanics (reused
//   AtlasTileRequest.resolveUrl — no duplication). Representation, not fetch.
// - deferred: tiled resource without a template, or any non-tiled resource
//   (URL representations are never forced onto non-tiles — 1.6-D test).
//   Deferred means "acquisition not attempted", never failure.
// - invalid: the resource itself fails validation (programmer-side rejection).
// NOT implemented: Available/Unavailable (nothing is checked — that WOULD be
// acquisition), acquisition-Failed (no attempt exists to fail), retry/progress
// (no mechanism), ranking (no policy). Each omission is a refusal, not a gap.
// The template is caller-supplied (adapter config/test vector), exactly like
// 1.4 request mechanics: unknown placeholders throw MALFORMED_TEMPLATE.
// Phase 1.6 slice. Depends on atlas_core (+ siblings) only.

import 'package:atlas_core/atlas_core.dart';
import '../requests/tile_identity.dart';
import '../requests/tile_request.dart';
import 'resolved_resource.dart';

/// Materialization outcome. Only contract-justified values exist.
enum AtlasMaterializationStatus {
  /// An acquisition representation is available (still unexecuted).
  ready,

  /// No representation derivable (no template, or non-tiled by design).
  /// Acquisition not attempted — never an error.
  deferred,

  /// The resource reference itself is invalid.
  invalid,
}

/// Materialization outcome binding a resource to its status (+ representation
/// when ready). No bytes, no paths, no entries, no timestamps.
final class AtlasMaterialization {
  const AtlasMaterialization({
    required this.resource,
    required this.status,
    this.representation,
    this.reason = '',
  });

  final AtlasResolvedResource resource;
  final AtlasMaterializationStatus status;

  /// Acquisition representation string when ready (e.g. a tile URL template
  /// rendering). DATA, never an executed request. Null unless ready.
  final String? representation;

  /// Deterministic reason (category or explanation, never timestamps).
  final String reason;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasMaterialization &&
          resource == other.resource &&
          status == other.status &&
          representation == other.representation &&
          reason == other.reason;

  @override
  int get hashCode => Object.hash(resource, status, representation, reason);
}

/// Pure materialization: representation derivation without acquisition.
abstract final class AtlasMaterializer {
  /// Materializes [resource]: tiled + template → ready; tiled without
  /// template or any non-tiled resource → deferred; invalid resource →
  /// invalid (with its validation category as reason).
  static AtlasMaterialization materialize(
    AtlasResolvedResource resource, {
    String? urlTemplate,
    Map<String, String> params = const {},
  }) {
    final resourceCheck = resource.validate();
    if (!resourceCheck.isValid) {
      return AtlasMaterialization(
        resource: resource,
        status: AtlasMaterializationStatus.invalid,
        reason: resourceCheck.rejection!.category,
      );
    }
    if (resource.tile == null || urlTemplate == null) {
      return AtlasMaterialization(
        resource: resource,
        status: AtlasMaterializationStatus.deferred,
        reason: resource.tile == null
            ? 'non-tiled resource has no URL representation by design'
            : 'no template supplied (acquisition not attempted)',
      );
    }
    final request = AtlasTileRequest(
      identity: AtlasTileIdentity(
        provider: resource.provider,
        // Layer binding is downstream of resolution: the materialization
        // layer is unnamed here (empty id marks "unbound", never a real layer).
        layer: const AtlasId(''),
        coordinate: resource.tile!,
      ),
      params: params,
    );
    return AtlasMaterialization(
      resource: resource,
      status: AtlasMaterializationStatus.ready,
      representation: request.resolveUrl(urlTemplate),
      reason: 'representation derived; acquisition not attempted',
    );
  }
}
