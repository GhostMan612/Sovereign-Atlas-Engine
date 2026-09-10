// Sovereign Atlas Engine — atlas_geo
// Minimal ring/polygon/segment validity kernel + skip-member collection policy.
//
// Contracts: ATLAS-GEOM-001 (geometry-contract.md), ATLAS-VALID-001.
// Decided here (SOURCE-VERIFIED precedents F-05/F-06, generalized):
// - LineString-equivalent: >= 2 valid coordinates.
// - Ring-equivalent: >= 4 positions, first == last (closed), members valid.
// - Corrupt members are SKIPPED, collections survive (skip-and-continue).
// Explicitly NOT decided (DEC-007 OPEN, fixtures BLOCKED, not implemented):
// - auto-close vs reject for unclosed rings (ADV-011),
// - winding rules, empty-geometry legality beyond collections (ADV-010),
// - tolerance-based equality (exact == used).
// Empty feature collections ACCEPT as empty (PROPOSED, ADV-013/GEOM-003).
// Zero-length flow segments REJECT as INVALID_GEOMETRY.
// Status: PROVISIONAL — NOT ATLAS-NORMATIVE (0.5A Ruling 3a). Ownership:
// DEC-007 (geometry strictness), still open; re-verdict on its closure.
// (FLOW-003/ADV-022 execute against this provisional.)
// Phase 0.5 slice. Depends on atlas_core + coordinate.dart only.

import 'package:atlas_core/atlas_core.dart';
import '../coordinates/coordinate.dart';

/// Validity checks for position sequences.
abstract final class AtlasRings {
  /// A ring is valid when it has >= 4 positions, every member validates, and
  /// it is explicitly closed (first == last). Closure is NOT auto-repaired:
  /// unclosed input is invalid here and its handling stays DECISION REQUIRED.
  static AtlasValidation validateRing(List<AtlasCoordinate> ring) {
    if (ring.length < 4) {
      return const AtlasValidation.invalid(
        AtlasRejection(
          'INVALID_GEOMETRY',
          'Rings require at least 4 positions (SOURCE-VERIFIED skip precedent).',
        ),
      );
    }
    for (final point in ring) {
      final member = AtlasCoordinates.validate(point.latitude, point.longitude);
      if (!member.isValid) return member;
    }
    if (ring.first != ring.last) {
      return const AtlasValidation.invalid(
        AtlasRejection(
          'INVALID_GEOMETRY',
          'Unclosed ring (handling undecided, DEC-007; not auto-closed).',
        ),
      );
    }
    return const AtlasValidation.valid();
  }

  /// A segment path is valid with >= 2 valid coordinates.
  static AtlasValidation validatePath(List<AtlasCoordinate> path) {
    if (path.length < 2) {
      return const AtlasValidation.invalid(
        AtlasRejection(
          'INVALID_GEOMETRY',
          'Paths require at least 2 positions.',
        ),
      );
    }
    for (final point in path) {
      final member = AtlasCoordinates.validate(point.latitude, point.longitude);
      if (!member.isValid) return member;
    }
    return const AtlasValidation.valid();
  }
}

/// Directed two-point segment (migration-flow primitive, CAP-010/F-05).
///
/// Endpoints are non-nullable by type. Zero-length segments (from == to)
/// validate INVALID_GEOMETRY — PROPOSED rule (FLOW-003/ADV-022), disclosed
/// for audit; the contract assigns no DEC.
final class AtlasFlowSegment {
  const AtlasFlowSegment({required this.from, required this.to});

  final AtlasCoordinate from;
  final AtlasCoordinate to;

  AtlasValidation validate() {
    final fromCheck = AtlasCoordinates.validate(from.latitude, from.longitude);
    if (!fromCheck.isValid) return fromCheck;
    final toCheck = AtlasCoordinates.validate(to.latitude, to.longitude);
    if (!toCheck.isValid) return toCheck;
    if (from == to) {
      return const AtlasValidation.invalid(
        AtlasRejection(
          'INVALID_GEOMETRY',
          'Zero-length flow segments are rejected (PROPOSED, FLOW-003).',
        ),
      );
    }
    return const AtlasValidation.valid();
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasFlowSegment && from == other.from && to == other.to;

  @override
  int get hashCode => Object.hash(from, to);
}

/// Skip-member-keep-collection policy (SOURCE-VERIFIED precedent F-05/F-06).
///
/// Returns the surviving members; [skippedIndices] reports positions so the
/// DEC-006 reporting channel has data to carry once resolved.
///
/// Empty input yields empty output. Status: PROVISIONAL — NOT ATLAS-NORMATIVE
/// (0.5A Ruling 3b). Ownership: DEC-007 ("Empty geometry legal?"), still open
/// (ADV-013 executes against this provisional).
final class AtlasCollectionScreening<T> {
  const AtlasCollectionScreening({
    required this.kept,
    required this.skippedIndices,
  });

  final List<T> kept;
  final List<int> skippedIndices;

  /// Screens [members] with [isValid], keeping valid entries in order.
  static AtlasCollectionScreening<T> screen<T>(
    List<T> members,
    bool Function(T member) isValid,
  ) {
    final kept = <T>[];
    final skipped = <int>[];
    for (var i = 0; i < members.length; i++) {
      if (isValid(members[i])) {
        kept.add(members[i]);
      } else {
        skipped.add(i);
      }
    }
    return AtlasCollectionScreening(kept: kept, skippedIndices: skipped);
  }
}
