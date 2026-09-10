// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';
import '../requests/tile_identity.dart';
import '../requests/tile_request.dart';
import 'resolved_resource.dart';

enum AtlasMaterializationStatus {

  ready,

  deferred,

  invalid,
}

final class AtlasMaterialization {
  const AtlasMaterialization({
    required this.resource,
    required this.status,
    this.representation,
    this.reason = '',
  });

  final AtlasResolvedResource resource;
  final AtlasMaterializationStatus status;

  final String? representation;

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

abstract final class AtlasMaterializer {

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
