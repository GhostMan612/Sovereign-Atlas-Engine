// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';
import '../coordinates/coordinate.dart';

abstract final class AtlasRings {

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

final class AtlasCollectionScreening<T> {
  const AtlasCollectionScreening({
    required this.kept,
    required this.skippedIndices,
  });

  final List<T> kept;
  final List<int> skippedIndices;

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
