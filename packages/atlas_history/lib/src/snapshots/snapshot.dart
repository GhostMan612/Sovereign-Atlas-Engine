// Sovereign Atlas Engine — atlas_history
// Historical snapshots: source context preserved, never flattened.
//
// Contract: blueprint Phase 7 (historical datasets, timeline, provenance-
// through-time) + AGENTS.md sensitivity rule (historical/Indigenous data
// keeps source context). A snapshot binds features to their origin story:
// source id, source version/epoch label, retrieval instant, license, and a
// free-text source note. Snapshots are IMMUTABLE values; change over time is
// modeled by the timeline holding several, never by editing one.
// Phase 7 slice. Depends on atlas_core + atlas_data only.

import 'package:atlas_core/atlas_core.dart';
import 'package:atlas_data/atlas_data.dart';

/// One immutable historical capture with its full source context.
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

  /// Explicit epoch the snapshot describes (event time, not ingest time).
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
