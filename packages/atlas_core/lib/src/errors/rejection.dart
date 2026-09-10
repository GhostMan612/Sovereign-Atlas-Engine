// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

final class AtlasRejection {
  const AtlasRejection(this.category, this.message);

  final String category;

  final String message;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasRejection &&
          category == other.category &&
          message == other.message;

  @override
  int get hashCode => Object.hash(category, message);

  @override
  String toString() => 'AtlasRejection($category: $message)';
}

final class AtlasRejectionException implements Exception {
  const AtlasRejectionException(this.rejection);

  final AtlasRejection rejection;

  @override
  String toString() => 'AtlasRejectionException($rejection)';
}
