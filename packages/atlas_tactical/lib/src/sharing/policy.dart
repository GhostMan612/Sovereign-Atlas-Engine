// Sovereign Atlas Engine — atlas_tactical
// Sharing policy: who may receive what (model only, no transport).
//
// Contract: blueprint Phase 10 (sharing-policy models) + security baseline
// (classification travels as data). A policy binds sensitivity tiers to
// named recipient groups; evaluation is a pure predicate over the data's
// declared sensitivity. Enforcement lives in adapters; the model only
// answers the question. Mesh sending itself stays in integrations/.
// Phase 10 slice. Depends on atlas_core only.

import 'package:atlas_core/atlas_core.dart';

/// Sensitivity tiers (ordered; higher index = more restricted).
enum AtlasSensitivityTier { open, internal, restricted }

/// Named-group sharing policy value.
final class AtlasSharingPolicy {
  const AtlasSharingPolicy({
    required this.id,
    this.grants = const {},
  });

  final AtlasId id;

  /// Tier → recipient group names allowed at that tier (and below).
  final Map<AtlasSensitivityTier, Set<String>> grants;

  /// Whether [group] may receive data at [tier] (at-or-below inheritance:
  /// a grant at `restricted` covers all tiers; a grant at `open` covers
  /// open only).
  bool mayShare(AtlasSensitivityTier tier, String group) {
    for (final entry in grants.entries) {
      if (entry.key.index >= tier.index && entry.value.contains(group)) {
        return true;
      }
    }
    return false;
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) || other is AtlasSharingPolicy && id == other.id;

  @override
  int get hashCode => id.hashCode;
}
