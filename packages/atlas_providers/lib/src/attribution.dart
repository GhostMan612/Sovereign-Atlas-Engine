// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

abstract final class AtlasProviderAttribution {

  static String compose(
    Iterable<String> providerIds,
    String? Function(String id) lookup,
  ) {
    final seen = <String>{};
    final parts = <String>[];
    for (final id in providerIds) {
      if (!seen.add(id)) continue;
      final text = lookup(id);
      if (text == null || text.isEmpty) continue;
      parts.add(text);
    }
    return parts.join('; ');
  }
}
