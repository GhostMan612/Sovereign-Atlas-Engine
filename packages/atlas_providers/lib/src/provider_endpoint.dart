// Sovereign Atlas Engine — atlas_providers
// Endpoint + policy: implementation-side provider data (descriptors stay
// endpoint-free, 1.4 normative rule).
//
// Contract: blueprint 2.1 checklist (scheme/zoom/attribution/license/caching/
// prefetch/auth/version declarations) + 3.4 policy declarations as DATA.
// Enforcement (rate limits, bulk guards, pack size) is Phase 3; this file
// declares, never enforces.
// Phase 2 slice. Depends on atlas_core + atlas_provider_api only.

import 'package:atlas_core/atlas_core.dart';
import 'package:atlas_provider_api/atlas_provider_api.dart';

/// Declared provider policy (data for future enforcement, Phase 3).
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

  /// Prefetch ceiling (null = undeclared, never defaulted).
  final int? maxTiles;

  /// Declared request rate ceiling (null = undeclared).
  final double? maxRequestsPerSecond;

  /// Whether acquisition needs key material (no key plumbing exists yet —
  /// attempts against requiresKey endpoints report policyRejected, ADR-003).
  final bool requiresKey;

  /// Bulk-use restriction text (e.g. OSM Tile Usage Policy note).
  final String? bulkGuard;

  /// Identifying user-agent owed to the provider (e.g. OSM policy).
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

/// Implementation-side provider: descriptor + acquisition template + policy.
/// Templates are caller-side config DATA (1.4 mechanics); fetching lives in
/// the operation, never here.
final class AtlasProviderEndpoint {
  const AtlasProviderEndpoint({
    required this.descriptor,
    required this.policy,
    this.urlTemplate,
    this.params = const {},
    this.headers = const {},
  });

  final AtlasProviderDescriptor descriptor;

  /// Tile URL template (`{z}/{x}/{y}` + explicit params). Null = bundle-
  /// backed (local provider; no locator).
  final String? urlTemplate;

  /// Explicit template params (e.g. `{'s': 'a'}`). No derivation, no
  /// rotation — the definition states every value (1.4 rule).
  final Map<String, String> params;

  /// Request headers (e.g. policy-owed User-Agent). Data, never secrets:
  /// no key material may appear here (no plumbing exists).
  final Map<String, String> headers;

  final AtlasProviderPolicy policy;

  /// Bundle-backed (local) rather than locator-backed.
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
