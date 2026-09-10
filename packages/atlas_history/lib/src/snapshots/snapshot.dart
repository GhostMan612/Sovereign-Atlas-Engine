// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';
import 'package:atlas_data/atlas_data.dart';

final class AtlasHistoricalSnapshot {
  const AtlasHistoricalSnapshot({
    required this.id,
    required this.at,
    required this.sourceId,
    this.sourceEpoch,
    this.retrievedAt,
    this.sourceNote = '',
    this.license,
    this.features = const [],
  });

  final AtlasId id;

  final int at;
  final String sourceId;
  final String? sourceEpoch;
  final int? retrievedAt;
  final String sourceNote;
  final String? license;
  final List<AtlasFeature> features;

  int get featureCount => features.length;

  AtlasValidation validate() {
    final idCheck = AtlasIds.check(id.value);
    if (!idCheck.isValid) return idCheck;
    if (at < 0) {
      return const AtlasValidation.invalid(
        AtlasRejection(
          'INVALID_SNAPSHOT',
          'Snapshot event time must be non-negative epoch seconds.',
        ),
      );
    }
    if (sourceId.isEmpty) {
      return const AtlasValidation.invalid(
        AtlasRejection(
          'INVALID_SNAPSHOT',
          'Snapshots must name their source (context is never flattened).',
        ),
      );
    }
    return const AtlasValidation.valid();
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasHistoricalSnapshot &&
          id == other.id &&
          at == other.at &&
          sourceId == other.sourceId &&
          sourceEpoch == other.sourceEpoch &&
          retrievedAt == other.retrievedAt &&
          sourceNote == other.sourceNote &&
          license == other.license &&
          featureCount == other.featureCount;

  @override
  int get hashCode => Object.hash(
        id,
        at,
        sourceId,
        sourceEpoch,
        retrievedAt,
        sourceNote,
        license,
        featureCount,
      );
}
