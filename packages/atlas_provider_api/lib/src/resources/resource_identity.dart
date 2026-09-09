// Sovereign Atlas Engine — atlas_provider_api
// AtlasResourceIdentity: opaque, URL-free resource naming.
//
// Contract: 1.6-C (inventory: PROPOSED → PROVISIONAL).
// Identity chain (each ≠ the others): provider ≠ request (requests carry no
// id by design) ≠ resource (this type) ≠ tile identity (address + layer
// binding downstream) ≠ cache key ≠ cache entry ≠ payload.
// - `address` is opaque: for tiles the canonical documented form
//   `z=<z>/x=<x>/y=<y>@<scheme>` (a rendering of the tile address, NOT a
//   locator protocol and never a URL); for non-tiled resources a dataset
//   qualifier or "" (uniqueness scope is provider+kind+address; empty is valid
//   where the catalog yields one resource per provider+kind).
// - CRITICAL (1.6-C/1.6-R): an address/identity MUST NEVER be or contain a URL.
//   `AtlasResourceIdentity.validated()` rejects any address containing "://"
//   (INVALID_IDENTITY) so the URL==identity collapse is structurally refused.
// Phase 1.6 slice. Depends on atlas_core only.

import '../../../../atlas_core/lib/atlas_core.dart';
import '../provider/data_kind.dart';

/// Opaque identity of a resolved resource.
final class AtlasResourceIdentity {
  const AtlasResourceIdentity({
    required this.provider,
    required this.kind,
    this.address = '',
  });

  final AtlasId provider;
  final AtlasDataKind kind;

  /// Opaque address qualifier (canonical tile form, dataset qualifier, or "").
  final String address;

  /// Structural validation: provider non-empty; address URL-free.
  AtlasValidation validate() {
    final providerCheck = AtlasIds.check(provider.value);
    if (!providerCheck.isValid) return providerCheck;
    if (address.contains('://')) {
      return const AtlasValidation.invalid(
        AtlasRejection(
          'INVALID_IDENTITY',
          'Resource identity must never be or contain a URL.',
        ),
      );
    }
    return const AtlasValidation.valid();
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasResourceIdentity &&
          provider == other.provider &&
          kind == other.kind &&
          address == other.address;

  @override
  int get hashCode => Object.hash(provider, kind, address);

  @override
  String toString() =>
      'AtlasResourceIdentity(${provider.value}/${kind.name}/$address)';
}
