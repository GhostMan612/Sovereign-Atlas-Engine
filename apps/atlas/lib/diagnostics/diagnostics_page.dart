// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:flutter/material.dart';

import '../offline/offline_pack.dart';
import '../offline/offline_repository.dart';

const String kAtlasAppVersion = '0.1.0+1';

const List<String> kWiredEnginePackages = [
  'atlas_core',
  'atlas_provider_api',
  'atlas_tiles',
  'atlas_offline',
  'atlas_providers',
];

class DiagnosticsPage extends StatelessWidget {
  const DiagnosticsPage({super.key, required this.repository});

  final OfflineRepository repository;

  @override
  Widget build(BuildContext context) {
    final registry = repository.registry;
    var validEndpoints = 0;
    for (final id in registry.ids) {
      if (registry.lookup(id)!.validate().isValid) validEndpoints += 1;
    }
    final stats = repository.store.stats;

    final unfinished = repository.packs.where((p) => !p.isTerminal).length;
    final downloading = repository.packs
        .where((p) => p.lifecycle == OfflinePackLifecycle.downloading)
        .length;
    return Scaffold(
      appBar: AppBar(title: const Text('Diagnostics')),
      body: ListView(
        padding: const EdgeInsets.all(16.0),
        children: [
          const Text(
            'Application',
            style: TextStyle(fontSize: 16.0, fontWeight: FontWeight.bold),
          ),
          Text('Sovereign Atlas host v$kAtlasAppVersion'),
          Text('Engine packages wired: ${kWiredEnginePackages.join(', ')}'),
          const SizedBox(height: 12.0),
          const Text(
            'Registry',
            style: TextStyle(fontSize: 16.0, fontWeight: FontWeight.bold),
          ),
          Text(
            'Providers registered: ${registry.ids.length} '
            '(${registry.ids.join(', ')})',
          ),
          Text('Endpoints self-valid: $validEndpoints/${registry.ids.length}'),
          const SizedBox(height: 12.0),
          const Text(
            'Offline state',
            style: TextStyle(fontSize: 16.0, fontWeight: FontWeight.bold),
          ),
          Text(
            'Pack index: ${stats.entryCount}/${stats.capacity} · '
            'records: ${repository.packs.length} · '
            'unfinished: $unfinished (downloading: $downloading)',
          ),
          Text(
            'Renderer: ${repository.offlineTileHits} offline serves · '
            '${repository.networkTileRequests} network requests',
          ),
          const SizedBox(height: 12.0),
          const Text(
            'Event log (newest first)',
            style: TextStyle(fontSize: 16.0, fontWeight: FontWeight.bold),
          ),
          if (repository.events.isEmpty)
            const Text('No events yet.')
          else
            for (final event in repository.events) Text(event),
        ],
      ),
    );
  }
}
