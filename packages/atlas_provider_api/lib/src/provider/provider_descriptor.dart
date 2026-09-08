// Sovereign Atlas Engine — atlas_provider_api
// AtlasProviderDescriptor: endpoint-free provider description.
//
// Contract: ATLAS-PROV-DESC-001 (provider-contract.md §1).
// Status: PROPOSED → PROVISIONAL shapes; the no-endpoint rule is
// ATLAS-NORMATIVE (extraction-matrix forbidden moves; 1.4 boundary).
// There are deliberately NO url/template/endpoint/key/credential/client fields:
// identity, taxonomy, native range, and attribution/license hooks describe the
// provider; adapters own every byte of acquisition. Geographic coverage is
// NOT modeled here (would need a geo dependency → dependency-map ADR;
// zoom range suffices for Phase 1.4 — inventory ruling).
// Phase 1.4 slice. Depends on atlas_core only.

import '../../../../atlas_core/lib/atlas_core.dart';
import 'data_kind.dart';
import '../capabilities/provider_capability.dart';

/// Endpoint-free description of a data provider.
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

  /// Opaque provider identity (e.g. `osm-standard`). Never a URL, never a
  /// display name, never shared with layer identity (directive §5 analog).
  final AtlasId id;

  /// Served data families. Must be non-empty (a provider serving nothing is
  /// not a provider — structural sanity, see [validate]).
  final Set<AtlasDataKind> kinds;

  /// Human-readable label. Presentation data, excluded from `==`/hashCode
  /// (same rule as layer titles).
  final String? title;

  /// Advertised serving abilities (claims only — see enum docs).
  final Set<AtlasProviderCapability> capabilities;

  /// Native resolution range (zoom levels the provider actually resolves).
  /// Null = undeclared (valid; never defaulted). Both present requires
  /// min ≤ max; non-negative. No upper bound is imposed (no invention).
  final int? nativeMinZoom;
  final int? nativeMaxZoom;

  /// Attribution text owed when this provider's data is visible.
  final String? attribution;

  /// License/terms hook (opaque string owned by the license catalog, Phase 2).
  final String? license;

  /// Sensitivity hook (opaque string owned by the security architecture).
  final String? sensitivity;

  /// Structural validation: identity non-empty, kinds non-empty, zoom sane.
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
