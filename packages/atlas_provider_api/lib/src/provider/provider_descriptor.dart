// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';
import 'data_kind.dart';
import '../capabilities/provider_capability.dart';

final class AtlasProviderDescriptor {
  const AtlasProviderDescriptor({
    required this.id,
    required this.kinds,
    this.title,
    this.capabilities = const {},
    this.nativeMinZoom,
    this.nativeMaxZoom,
    this.attribution,
    this.license,
    this.sensitivity,
  });

  final AtlasId id;

  final Set<AtlasDataKind> kinds;

  final String? title;

  final Set<AtlasProviderCapability> capabilities;

  final int? nativeMinZoom;
  final int? nativeMaxZoom;

  final String? attribution;

  final String? license;

  final String? sensitivity;

  AtlasValidation validate() {
    final idCheck = AtlasIds.check(id.value);
    if (!idCheck.isValid) return idCheck;
    if (kinds.isEmpty) {
      return const AtlasValidation.invalid(
        AtlasRejection(
          'INVALID_DESCRIPTOR',
          'A provider descriptor must declare at least one data kind.',
        ),
      );
    }
    if (nativeMinZoom != null && nativeMinZoom! < 0) {
      return const AtlasValidation.invalid(
        AtlasRejection(
          'INVALID_DESCRIPTOR',
          'Native zoom bounds must be non-negative.',
        ),
      );
    }
    if (nativeMaxZoom != null && nativeMaxZoom! < 0) {
      return const AtlasValidation.invalid(
        AtlasRejection(
          'INVALID_DESCRIPTOR',
          'Native zoom bounds must be non-negative.',
        ),
      );
    }
    if (nativeMinZoom != null &&
        nativeMaxZoom != null &&
        nativeMinZoom! > nativeMaxZoom!) {
      return const AtlasValidation.invalid(
        AtlasRejection(
          'INVALID_DESCRIPTOR',
          'Native min zoom must not exceed native max zoom.',
        ),
      );
    }
    return const AtlasValidation.valid();
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasProviderDescriptor &&
          id == other.id &&
          _equalKinds(kinds, other.kinds) &&
          capabilities.length == other.capabilities.length &&
          capabilities.containsAll(other.capabilities) &&
          nativeMinZoom == other.nativeMinZoom &&
          nativeMaxZoom == other.nativeMaxZoom &&
          attribution == other.attribution &&
          license == other.license &&
          sensitivity == other.sensitivity;

  static bool _equalKinds(Set<AtlasDataKind> a, Set<AtlasDataKind> b) =>
      a.length == b.length && a.containsAll(b);

  @override
  int get hashCode => Object.hash(
        id,
        Object.hashAllUnordered(kinds),
        Object.hashAllUnordered(capabilities),
        nativeMinZoom,
        nativeMaxZoom,
        attribution,
        license,
        sensitivity,
      );
}
