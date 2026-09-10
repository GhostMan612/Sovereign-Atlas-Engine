// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';
import 'tile_identity.dart';

final class AtlasTileRequest {
  const AtlasTileRequest({required this.identity, this.params = const {}});

  final AtlasTileIdentity identity;

  final Map<String, String> params;

  String resolveUrl(String template) {
    final coordinate = identity.coordinate;
    final values = <String, String>{
      'z': coordinate.z.toString(),
      'x': coordinate.x.toString(),
      'y': coordinate.rowFor(identity.scheme).toString(),
      ...params,
    };
    final placeholder = RegExp(r'\{([A-Za-z0-9_]+)\}');
    return template.replaceAllMapped(placeholder, (match) {
      final name = match.group(1)!;
      final value = values[name];
      if (value == null) {
        throw AtlasRejectionException(
          AtlasRejection(
            'MALFORMED_TEMPLATE',
            'Unknown URL placeholder "{$name}": pass it explicitly via params.',
          ),
        );
      }
      return value;
    });
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasTileRequest &&
          identity == other.identity &&
          _equalParams(params, other.params);

  static bool _equalParams(Map<String, String> a, Map<String, String> b) {
    if (a.length != b.length) return false;
    for (final key in a.keys) {
      if (b[key] != a[key]) return false;
    }
    return true;
  }

  @override
  int get hashCode => Object.hash(
        identity,
        Object.hashAllUnordered(
          params.entries.map((e) => Object.hash(e.key, e.value)),
        ),
      );
}
