// Sovereign Atlas Engine — atlas_providers
// Attribution composition: owed text from the active provider stack.
//
// Contract: blueprint 2.3/2.4 (attribution generated from active layers) +
// 1.2 layer precedent (attribution follows provider identity). Display-side
// pure function: ordered provider ids in, deduped combined string out.
// Unknown ids are skipped (display robustness — composition never fails a
// render on bookkeeping); empty input yields the empty string.
// Phase 2 slice. Depends on atlas_core only (ids as plain strings keeps the
// layers package out of this edge).

/// Provider-order attribution composer (pure display data).
/// Contrast layers' `AtlasAttribution` (visibility SET from layer state):
/// this type renders an ORDERED display string from provider ids in stack
/// order (bottom-first). The two compose downstream: visible stack →
/// provider ids → this display string.
abstract final class AtlasProviderAttribution {
  /// Combines attribution for [providerIds] (stack order, bottom-first)
  /// using [lookup] for descriptor text. Unknown ids are skipped.
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
