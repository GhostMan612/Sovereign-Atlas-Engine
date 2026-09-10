// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';
import '../resources/resource_identity.dart';
import 'acquisition_policy.dart';

final class AtlasAcquisitionRequest {
  const AtlasAcquisitionRequest({
    required this.resource,
    this.provider,
    this.policy = const AtlasAcquisitionPolicy(),
  });

  final AtlasResourceIdentity resource;

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
