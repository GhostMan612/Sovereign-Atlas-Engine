// Sovereign Atlas Engine — atlas_location
// Heading value + append-only fix log (pure models).
//
// Contract: ADR-001 location charter (heading/compass abstractions live
// here; tactical meaning stays in atlas_tactical).
// Location slice. No engine imports (pure value).

/// Compass heading value (degrees clockwise from north, explicit source).
final class AtlasHeading {
  const AtlasHeading({required this.degrees, this.source = ''});

  final double degrees;
  final String source;

  /// Normalized to [0, 360).
  double get normalized {
    var value = degrees % 360.0;
    if (value < 0) value += 360.0;
    return value;
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasHeading &&
          degrees == other.degrees &&
          source == other.source;

  @override
  int get hashCode => Object.hash(degrees, source);
}
