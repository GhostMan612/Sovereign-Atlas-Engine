// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';
import 'package:atlas_provider_api/atlas_provider_api.dart';

final class AtlasProviderPolicy {
  const AtlasProviderPolicy({
    required this.onlineAllowed,
    required this.cacheAllowed,
    required this.prefetchAllowed,
    this.maxTiles,
    this.maxRequestsPerSecond,
    this.requiresKey = false,
    this.bulkGuard,
    this.userAgent,
  });

  final bool onlineAllowed;
  final bool cacheAllowed;
  final bool prefetchAllowed;

  final int? maxTiles;

  final double? maxRequestsPerSecond;

  final bool requiresKey;

  final String? bulkGuard;

  final String? userAgent;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasProviderPolicy &&
          onlineAllowed == other.onlineAllowed &&
          cacheAllowed == other.cacheAllowed &&
          prefetchAllowed == other.prefetchAllowed &&
          maxTiles == other.maxTiles &&
          maxRequestsPerSecond == other.maxRequestsPerSecond &&
          requiresKey == other.requiresKey &&
          bulkGuard == other.bulkGuard &&
          userAgent == other.userAgent;

  @override
  int get hashCode => Object.hash(
        onlineAllowed,
        cacheAllowed,
        prefetchAllowed,
        maxTiles,
        maxRequestsPerSecond,
        requiresKey,
        bulkGuard,
        userAgent,
      );
}

final class AtlasProviderEndpoint {
  const AtlasProviderEndpoint({
    required this.descriptor,
    required this.policy,
    this.urlTemplate,
    this.params = const {},
    this.headers = const {},
  });

  final AtlasProviderDescriptor descriptor;

  final String? urlTemplate;

  final Map<String, String> params;

  final Map<String, String> headers;

  final AtlasProviderPolicy policy;

  bool get isLocal => urlTemplate == null;

  AtlasValidation validate() {
    final descriptorCheck = descriptor.validate();
    if (!descriptorCheck.isValid) return descriptorCheck;
    if (urlTemplate != null && urlTemplate!.isEmpty) {
      return const AtlasValidation.invalid(
        AtlasRejection(
          'INVALID_ENDPOINT',
          'URL templates must be non-empty when declared.',
        ),
      );
    }
    if (policy.maxTiles != null && policy.maxTiles! < 0) {
      return const AtlasValidation.invalid(
        AtlasRejection(
          'INVALID_ENDPOINT',
          'Policy maxTiles must be non-negative when declared.',
        ),
      );
    }
    if (policy.maxRequestsPerSecond != null &&
        policy.maxRequestsPerSecond! <= 0) {
      return const AtlasValidation.invalid(
        AtlasRejection(
          'INVALID_ENDPOINT',
          'Policy maxRequestsPerSecond must be positive when declared.',
        ),
      );
    }
    return const AtlasValidation.valid();
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasProviderEndpoint &&
          descriptor == other.descriptor &&
          urlTemplate == other.urlTemplate &&
          policy == other.policy &&
          _equalStrings(params, other.params) &&
          _equalStrings(headers, other.headers);

  static bool _equalStrings(Map<String, String> a, Map<String, String> b) {
    if (a.length != b.length) return false;
    for (final key in a.keys) {
      if (b[key] != a[key]) return false;
    }
    return true;
  }

  @override
  int get hashCode => Object.hash(
        descriptor,
        urlTemplate,
        policy,
        Object.hashAllUnordered(
          params.entries.map((e) => Object.hash(e.key, e.value)),
        ),
        Object.hashAllUnordered(
          headers.entries.map((e) => Object.hash(e.key, e.value)),
        ),
      );
}
