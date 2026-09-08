// Sovereign Atlas Engine — atlas_provider_api
// AtlasTileRequest + pure URL-template substitution: what a request MEANS.
//
// Contract: ATLAS-TILE-RET-001 area (request semantics, not transport).
// Status: ATLAS-NORMATIVE mechanics with explicit limits:
// - The template is CALLER-SUPPLIED data (adapter config, test vector),
//   never stored on descriptors (endpoint-free rule) and never fetched here.
// - Substitution is pure string replacement of `{z}`, `{x}`, `{y}` where `{y}`
//   is the scheme-correct row (TMS flips). Esri-style `{z}/{y}/{x}` ordering
//   falls out of template text, needing no special case (forensic F-10).
// - Extra placeholders (e.g. `{s}` subdomains) resolve ONLY from explicit
//   caller params — no rotation/derivation policy is invented. Unknown
//   placeholders throw MALFORMED_TEMPLATE rather than passing through silently.
// - No HTTP, no client, no I/O anywhere in this file.
// Phase 1.4 slice. Depends on atlas_core only.

import '../../../../atlas_core/lib/atlas_core.dart';
import 'tile_identity.dart';

/// A tile request: an identity plus caller-supplied template parameters.
final class AtlasTileRequest {
  const AtlasTileRequest({required this.identity, this.params = const {}});

  final AtlasTileIdentity identity;

  /// Explicit substitution parameters (e.g. `{'s': 'a'}`). No derivation,
  /// no defaults, no rotation policy — the caller states every value.
  final Map<String, String> params;

  /// Resolves [template] by pure substitution. Throws
  /// [AtlasRejectionException] (`MALFORMED_TEMPLATE`) on unknown placeholders.
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
