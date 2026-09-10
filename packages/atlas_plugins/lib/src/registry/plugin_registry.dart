// Sovereign Atlas Engine — atlas_plugins
// Plugin registry: explicit register/enable/disable + permission queries.
//
// Contract: blueprint Phase 12 (discovery/lifecycle/permissions as data).
// Registration is explicit (no scanning, no discovery protocol — that would
// be IO); duplicates refuse loudly; enable requires a valid manifest;
// permission queries answer from declared grants (enforcement downstream).
// Lifecycle here means registry state (registered/disabled), never process
// control (no isolate/thread/process concepts anywhere near plugins).
// Phase 12 slice. Depends on atlas_core + siblings only.

import 'package:atlas_core/atlas_core.dart';
import '../plugin/plugin.dart';

/// Registry-state lifecycle (admin data, not process control).
enum AtlasPluginState { registered, enabled, disabled }

/// Explicit plugin registry value holder.
final class AtlasPluginRegistry {
  AtlasPluginRegistry() : _entries = {};

  final Map<String, (_Entry, AtlasPluginState)> _entries;

  /// Registers [manifest] (duplicates throw StateError — closed honesty).
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

  /// Declared-grant query (does [id] hold [permission]?).
  bool grants(String id, AtlasPluginPermission permission) =>
      _entries[id]?.$1.manifest.permissions.contains(permission) ?? false;
}

final class _Entry {
  const _Entry({required this.manifest});

  final AtlasPluginManifest manifest;
}
