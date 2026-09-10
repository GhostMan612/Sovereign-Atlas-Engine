// Sovereign Atlas Engine — atlas_provider_api
// AtlasAcquisitionRequest: semantic "obtain this resource" (never HOW).
//
// Contract: 1.8-C (inventory: PROPOSED → PROVISIONAL).
// - Target is a resolved-resource SHAPE (identity/kind/address); the request
//   does not re-resolve and never contacts anything.
// - Optional explicit provider binding must equal the resource provider when
//   present (ACQ-004 structural integrity, not a fetch error).
// - Policy carries declared bounds only (see AtlasAcquisitionPolicy).
// - Deliberately absent: URLs, HTTP method/headers, credentials, cache
//   directives, renderer hints, attempt ids, timestamps (time arrives at
//   lifecycle transitions, never in the request).
// Phase 1.8 slice. Depends on atlas_core (+ sibling resource/policy) only.

import 'package:atlas_core/atlas_core.dart';
import '../resources/resource_identity.dart';
import 'acquisition_policy.dart';

/// Semantic acquisition target + declared bounds.
final class AtlasAcquisitionRequest {
  const AtlasAcquisitionRequest({
    required this.resource,
    this.provider,
    this.policy = const AtlasAcquisitionPolicy(),
  });

  /// Target resource identity (address may be a tile rendering or qualifier).
  final AtlasResourceIdentity resource;

  /// Explicit provider binding. Null = derive from resource. Present but
  /// unequal to resource.provider → INVALID_REQUEST (never fetched to find out).
  final AtlasId? provider;

  final AtlasAcquisitionPolicy policy;

  AtlasValidation validate() {
    final resourceCheck = resource.validate();
    if (!resourceCheck.isValid) return resourceCheck;
    if (provider != null && provider != resource.provider) {
      return const AtlasValidation.invalid(
        AtlasRejection(
          'INVALID_REQUEST',
          'Explicit provider binding must equal the resource provider.',
        ),
      );
    }
    return policy.validate();
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasAcquisitionRequest &&
          resource == other.resource &&
          provider == other.provider &&
          policy == other.policy;

  @override
  int get hashCode => Object.hash(resource, provider, policy);
}
