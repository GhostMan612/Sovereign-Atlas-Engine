// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';

enum AtlasSensitivityTier { open, internal, restricted }

final class AtlasSharingPolicy {
  const AtlasSharingPolicy({
    required this.id,
    this.grants = const {},
  });

  final AtlasId id;

  final Map<AtlasSensitivityTier, Set<String>> grants;

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
