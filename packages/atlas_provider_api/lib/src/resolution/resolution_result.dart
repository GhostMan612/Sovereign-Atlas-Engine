// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';
import '../requests/tile_coordinate.dart';
import 'resolution_request.dart';

enum AtlasResolutionStatus {
  resolved,
  noMatch,
  unsupported,
  invalidRequest,
  ambiguous,
}

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

  final List<AtlasId> eligible;

  final AtlasId? provider;

  final AtlasTileCoordinate? tile;

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
