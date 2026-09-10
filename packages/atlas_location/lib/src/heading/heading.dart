// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

final class AtlasHeading {
  const AtlasHeading({required this.degrees, this.source = ''});

  final double degrees;
  final String source;

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
