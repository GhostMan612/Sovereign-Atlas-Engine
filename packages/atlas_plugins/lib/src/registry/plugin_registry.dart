// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';
import '../plugin/plugin.dart';

enum AtlasPluginState { registered, enabled, disabled }

final class AtlasPluginRegistry {
  AtlasPluginRegistry() : _entries = {};

  final Map<String, (_Entry, AtlasPluginState)> _entries;

  void register(AtlasPluginManifest manifest) {
    final check = manifest.validate();
    if (!check.isValid) {
      throw AtlasRejectionException(check.rejection!);
    }
    if (_entries.containsKey(manifest.id.value)) {
      throw StateError('duplicate plugin id: ${manifest.id.value}');
    }
    _entries[manifest.id.value] = (
      _Entry(manifest: manifest),
      AtlasPluginState.registered,
    );
  }

  void enable(String id) => _set(id, AtlasPluginState.enabled);

  void disable(String id) => _set(id, AtlasPluginState.disabled);

  void _set(String id, AtlasPluginState state) {
    final current = _entries[id];
    if (current == null) throw StateError('unknown plugin id: $id');
    _entries[id] = (current.$1, state);
  }

  AtlasPluginState? stateOf(String id) => _entries[id]?.$2;

  List<String> get ids => _entries.keys.toList();

  bool grants(String id, AtlasPluginPermission permission) =>
      _entries[id]?.$1.manifest.permissions.contains(permission) ?? false;
}

final class _Entry {
  const _Entry({required this.manifest});

  final AtlasPluginManifest manifest;
}
