// Sovereign Atlas Engine — atlas_tiles
// Memory cache store: retention machinery (the engine 1.7 never had).
//
// Contract: phase-3 note §1. The 1.7 no-remove/no-expire rule bound cache
// SEMANTICS (decisions stay pure); THIS is the retention engine that serves
// those decisions: explicit capacity, deterministic LRU (insertion order +
// remove/reinsert on touch), oldest-first eviction with the evicted entry
// reported, explicit remove/clear/invalidate, stats value.
// Refuses structurally invalid entries at put (stores knowledge, not
// garbage). No IO, no timers, no clocks — all time arrives in entries.
// Phase 3 slice. Depends on atlas_core + atlas_provider_api (+ siblings).

import 'package:atlas_core/atlas_core.dart';
import '../entries/cache_entry.dart';
import '../keys/cache_key.dart';

/// Store statistics value (point-in-time, deterministic).
final class AtlasStoreStats {
  const AtlasStoreStats({required this.entryCount, required this.capacity});

  final int entryCount;
  final int capacity;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasStoreStats &&
          entryCount == other.entryCount &&
          capacity == other.capacity;

  @override
  int get hashCode => Object.hash(entryCount, capacity);
}

/// Explicit-capacity in-memory cache store (insertion-ordered LRU).
final class AtlasMemoryStore {
  AtlasMemoryStore({required this.capacity})
      : assert(capacity > 0, 'Store capacity must be positive.'),
        _entries = <String, AtlasCacheEntry>{};

  final int capacity;
  final Map<String, AtlasCacheEntry> _entries;

  static String _keyOf(AtlasCacheKey key) =>
      '${key.namespace.name}/${key.value}';

  int get entryCount => _entries.length;

  AtlasStoreStats get stats =>
      AtlasStoreStats(entryCount: _entries.length, capacity: capacity);

  /// Stores [entry] (must validate). Returns the evicted entry, if any.
  /// Re-put of an existing key replaces and refreshes recency.
  AtlasCacheEntry? put(AtlasCacheEntry entry) {
    final check = entry.validate();
    if (!check.isValid) {
      throw AtlasRejectionException(check.rejection!);
    }
    final key = _keyOf(entry.key);
    _entries.remove(key);
    AtlasCacheEntry? evicted;
    if (_entries.length >= capacity && !_entries.containsKey(key)) {
      final oldest = _entries.keys.first;
      evicted = _entries.remove(oldest);
    }
    _entries[key] = entry;
    return evicted;
  }

  /// Reads by key (refreshes recency). Null = absent (no meaning attached).
  AtlasCacheEntry? get(AtlasCacheKey key) {
    final stored = _entries.remove(_keyOf(key));
    if (stored == null) return null;
    _entries[_keyOf(key)] = stored;
    return stored;
  }

  /// Explicit deletion. True = an entry was present.
  bool remove(AtlasCacheKey key) => _entries.remove(_keyOf(key)) != null;

  /// Explicit full clear (Manage-Storage semantics live downstream).
  void clear() => _entries.clear();

  /// Pure invalidation in place (revoked copy replaces; original untouched).
  bool invalidate(AtlasCacheKey key) {
    final stored = _entries[_keyOf(key)];
    if (stored == null) return false;
    _entries.remove(_keyOf(key));
    _entries[_keyOf(key)] = stored.invalidate();
    return true;
  }
}
