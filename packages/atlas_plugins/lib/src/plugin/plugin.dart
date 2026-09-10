// Sovereign Atlas Engine — atlas_plugins
// Plugin manifest: identity, version, capabilities, permissions (data).
//
// Contract: blueprint Phase 12 + ADR-004 (admin data; grants name packages
// as strings — never imports, never a backdoor hub). Versions are explicit
// strings compared segment-wise (no semver library, no network registry).
// Permissions are closed-enum grants (unknown names refuse at parse).
// Phase 12 slice. Depends on atlas_core only.

import 'package:atlas_core/atlas_core.dart';

/// Closed permission set a plugin may request.
enum AtlasPluginPermission {
  readTiles,
  readFeatures,
  readLocation,
  writePack,
  useNetwork,
}

/// Plugin manifest value.
final class AtlasPluginManifest {
  const AtlasPluginManifest({
    required this.id,
    required this.version,
    this.title = '',
    this.capabilities = const {},
    this.permissions = const {},
    this.minEngineVersion,
  });

  final AtlasId id;
  final String version;
  final String title;
  final Set<String> capabilities;
  final Set<AtlasPluginPermission> permissions;
  final String? minEngineVersion;

  AtlasValidation validate() {
    final idCheck = AtlasIds.check(id.value);
    if (!idCheck.isValid) return idCheck;
    if (version.isEmpty) {
      return const AtlasValidation.invalid(
        AtlasRejection(
          'INVALID_PLUGIN',
          'Plugin versions must be non-empty.',
        ),
      );
    }
    if (minEngineVersion != null &&
        _compareVersions(minEngineVersion!, _engineVersion) > 0) {
      return const AtlasValidation.invalid(
        AtlasRejection(
          'INVALID_PLUGIN',
          'Plugin requires a newer engine version.',
        ),
      );
    }
    return const AtlasValidation.valid();
  }

  /// Current engine version (single source for compatibility checks).
  static const String _engineVersion = '0.1.0';

  /// Segment-wise numeric version compare (non-numeric segments compare
  /// lexically; missing segments count as zero).
  static int _compareVersions(String a, String b) {
    final as = a.split('.');
    final bs = b.split('.');
    final length = as.length > bs.length ? as.length : bs.length;
    for (var i = 0; i < length; i++) {
      final x = i < as.length ? as[i] : '0';
      final y = i < bs.length ? bs[i] : '0';
      final xn = int.tryParse(x);
      final yn = int.tryParse(y);
      final cmp = xn != null && yn != null ? xn.compareTo(yn) : x.compareTo(y);
      if (cmp != 0) return cmp;
    }
    return 0;
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasPluginManifest &&
          id == other.id &&
          version == other.version &&
          title == other.title &&
          capabilities.length == other.capabilities.length &&
          capabilities.containsAll(other.capabilities) &&
          permissions.length == other.permissions.length &&
          permissions.containsAll(other.permissions) &&
          minEngineVersion == other.minEngineVersion;

  @override
  int get hashCode => Object.hash(
        id,
        version,
        title,
        Object.hashAllUnordered(capabilities),
        Object.hashAllUnordered(permissions),
        minEngineVersion,
      );
}
