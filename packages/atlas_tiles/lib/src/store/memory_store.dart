// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';
import '../entries/cache_entry.dart';
import '../keys/cache_key.dart';

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

  AtlasCacheEntry? get(AtlasCacheKey key) {
    final stored = _entries.remove(_keyOf(key));
    if (stored == null) return null;
    _entries[_keyOf(key)] = stored;
    return stored;
  }

  bool remove(AtlasCacheKey key) => _entries.remove(_keyOf(key)) != null;

  void clear() => _entries.clear();

  bool invalidate(AtlasCacheKey key) {
    final stored = _entries[_keyOf(key)];
    if (stored == null) return false;
    _entries.remove(_keyOf(key));
    _entries[_keyOf(key)] = stored.invalidate();
    return true;
  }
}
