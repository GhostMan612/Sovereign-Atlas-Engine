// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_providers/atlas_providers.dart';
import 'package:flutter/material.dart';

import '../diagnostics/diagnostics_page.dart';
import 'offline_pack.dart';
import 'offline_repository.dart';

String formatAge(int seconds) {
  if (seconds < 0) return 'clock-skew';
  if (seconds < 60) return '${seconds}s';
  if (seconds < 3600) return '${seconds ~/ 60}m';
  if (seconds < 86400) return '${seconds ~/ 3600}h';
  return '${seconds ~/ 86400}d';
}

String formatBytes(int bytes) {
  if (bytes < 1024) return '$bytes B';
  if (bytes < 1048576) return '${(bytes / 1024).toStringAsFixed(1)} KiB';
  return '${(bytes / 1048576).toStringAsFixed(2)} MiB';
}

class OfflinePage extends StatelessWidget {
  const OfflinePage({super.key, required this.repository});

  final OfflineRepository repository;

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: repository,
      builder: (context, _) => DefaultTabController(
        length: 4,
        child: Scaffold(
          appBar: AppBar(
            title: const Text('Offline Areas'),
            actions: [
              IconButton(
                icon: const Icon(Icons.assessment),
                tooltip: 'diagnostics',
                onPressed: () => Navigator.of(context).push(
                  MaterialPageRoute<void>(
                    builder: (_) =>
                        DiagnosticsPage(repository: repository),
                  ),
                ),
              ),
            ],
            bottom: const TabBar(
              tabs: [
                Tab(text: 'Downloads'),
                Tab(text: 'Saved Areas'),
                Tab(text: 'Storage'),
                Tab(text: 'Providers'),
              ],
            ),
          ),
          body: TabBarView(
            children: [
              _DownloadsTab(repository: repository),
              _SavedAreasTab(repository: repository),
              _StorageTab(repository: repository),
              _ProvidersTab(repository: repository),
            ],
          ),
          floatingActionButton: FloatingActionButton.extended(
            tooltip: 'new-pack',
            icon: const Icon(Icons.add),
            label: const Text('New pack'),
            onPressed: () => showModalBottomSheet<void>(
              context: context,
              isScrollControlled: true,
              builder: (_) => _NewPackSheet(repository: repository),
            ),
          ),
        ),
      ),
    );
  }
}

class _DownloadsTab extends StatelessWidget {
  const _DownloadsTab({required this.repository});

  final OfflineRepository repository;

  @override
  Widget build(BuildContext context) {
    final packs = repository.packs;
    if (packs.isEmpty) {
      return const Center(child: Text('No packs yet. Plan one with New pack.'));
    }
    return ListView(
      children: [for (final pack in packs) _PackCard(pack: pack, repository: repository)],
    );
  }
}

class _PackCard extends StatelessWidget {
  const _PackCard({required this.pack, required this.repository});

  final OfflinePackRecord pack;
  final OfflineRepository repository;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      margin: const EdgeInsets.all(8.0),
      child: Padding(
        padding: const EdgeInsets.all(12.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    pack.providerTitle,
                    style: theme.textTheme.titleMedium,
                  ),
                ),
                Chip(label: Text(pack.lifecycle.name),
                    key: ValueKey('state-${pack.packId}')),
              ],
            ),
            Text(
              '${pack.providerId} · z${pack.zMin}–${pack.zMax} '
              'x${pack.xMin}–${pack.xMax} y${pack.yMin}–${pack.yMax}',
              style: theme.textTheme.bodySmall,
            ),
            if (pack.plan != null)
              Text(
                '${pack.tileCount} tiles · est. ${formatBytes(pack.estimatedBytes)} '
                '(${pack.bytesPerTile} B/tile basis)',
              ),
            if (pack.lifecycle == OfflinePackLifecycle.downloading ||
                pack.lifecycle == OfflinePackLifecycle.paused ||
                pack.lifecycle == OfflinePackLifecycle.quotaPaused)
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  LinearProgressIndicator(
                    value: pack.tileCount == 0
                        ? 0
                        : pack.receivedTiles / pack.tileCount,
                  ),
                  Text(
                    'progress ${pack.receivedTiles}/${pack.tileCount} '
                    '(${formatBytes(pack.receivedBytes)})',
                  ),
                ],
              ),
            if (pack.refusal != null)
              Text(
                'Refused ${pack.refusal!.reason}: ${pack.refusal!.detail}',
                style: TextStyle(color: theme.colorScheme.error),
              ),
            if (pack.appBlock != null)
              Text(
                'Blocked: ${pack.appBlock}',
                style: TextStyle(color: theme.colorScheme.error),
              ),
            if (pack.failureDetail.isNotEmpty)
              Text(
                pack.failureDetail,
                style: TextStyle(color: theme.colorScheme.error),
              ),
            if (pack.seal != null) Text('seal ${pack.seal}'),
            Wrap(
              spacing: 8.0,
              children: [
                if (pack.plan != null &&
                    (pack.lifecycle == OfflinePackLifecycle.planned ||
                        pack.lifecycle == OfflinePackLifecycle.paused ||
                        pack.lifecycle == OfflinePackLifecycle.quotaPaused ||
                        pack.lifecycle == OfflinePackLifecycle.failed ||
                        pack.lifecycle == OfflinePackLifecycle.cancelled))
                  ElevatedButton(
                    onPressed: () =>
                        repository.startDownload(pack.packId),
                    child: Text(
                      pack.receivedTiles > 0 ? 'Resume' : 'Download',
                    ),
                  ),
                if (pack.lifecycle == OfflinePackLifecycle.downloading)
                  ElevatedButton(
                    onPressed: () =>
                        repository.pauseDownload(pack.packId),
                    child: const Text('Pause'),
                  ),
                if (pack.lifecycle == OfflinePackLifecycle.downloading)
                  TextButton(
                    onPressed: () =>
                        repository.cancelDownload(pack.packId),
                    child: const Text('Cancel'),
                  ),
                TextButton(
                  onPressed: () => repository.deletePack(pack.packId),
                  child: const Text('Delete'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _SavedAreasTab extends StatelessWidget {
  const _SavedAreasTab({required this.repository});

  final OfflineRepository repository;

  @override
  Widget build(BuildContext context) {
    final now = DateTime.now().millisecondsSinceEpoch ~/ 1000;
    final saved = repository.packs
        .where((p) => p.lifecycle == OfflinePackLifecycle.complete)
        .toList();
    if (saved.isEmpty) {
      return const Center(
        child: Text('Nothing available offline yet. Completed packs appear here.'),
      );
    }
    return ListView(
      children: [
        for (final pack in saved)
          Card(
            margin: const EdgeInsets.all(8.0),
            child: ListTile(
              title: Text(pack.providerTitle),
              subtitle: Text(
                '${pack.tileCount} tiles · ${formatBytes(pack.receivedBytes)} · '
                'age ${formatAge(pack.ageSeconds(now))} · '
                '${pack.cacheEntryPresent ? 'indexed' : 'bytes held, index cleared'}',
              ),
              trailing: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  if (pack.manifestJson != null)
                    IconButton(
                      icon: const Icon(Icons.receipt_long),
                      tooltip: 'view-manifest',
                      onPressed: () => showDialog<void>(
                        context: context,
                        builder: (_) => AlertDialog(
                          title: const Text('Pack manifest'),
                          content: SingleChildScrollView(
                            child: Text(pack.manifestJson!),
                          ),
                          actions: [
                            TextButton(
                              onPressed: () => Navigator.of(context).pop(),
                              child: const Text('Close'),
                            ),
                          ],
                        ),
                      ),
                    ),
                  IconButton(
                    icon: const Icon(Icons.delete),
                    tooltip: 'delete-pack',
                    onPressed: () => repository.deletePack(pack.packId),
                  ),
                ],
              ),
            ),
          ),
      ],
    );
  }
}

class _StorageTab extends StatelessWidget {
  const _StorageTab({required this.repository});

  final OfflineRepository repository;

  @override
  Widget build(BuildContext context) {
    final stats = repository.store.stats;
    final remaining = stats.capacity - stats.entryCount;
    final packBytes = repository.packs.fold<int>(
      0,
      (sum, pack) => sum + pack.receivedBytes,
    );
    return ListView(
      padding: const EdgeInsets.all(16.0),
      children: [
        Text(
          'Pack index: ${stats.entryCount}/${stats.capacity} used '
          '($remaining slots remaining)',
          style: Theme.of(context).textTheme.titleMedium,
        ),
        const SizedBox(height: 8.0),
        Text(
          'Session bytes held: ${formatBytes(packBytes)} '
          '(session-resident; no file persistence yet)',
        ),
        const SizedBox(height: 8.0),
        const Text(
          'One index entry per pack (pack-level accounting, not per tile). '
          'Clearing evicts everything — index entries, RAM bytes, and disk '
          'journal — while records stay as history flagged unindexed and '
          'byteless.',
        ),
        const SizedBox(height: 16.0),
        ElevatedButton(
          key: const ValueKey('clear-store'),
          onPressed: () => repository.clearStore(),
          child: const Text('Clear pack index'),
        ),
      ],
    );
  }
}

class _ProvidersTab extends StatelessWidget {
  const _ProvidersTab({required this.repository});

  final OfflineRepository repository;

  @override
  Widget build(BuildContext context) {
    final registry = repository.registry;
    return ListView(
      children: [
        for (final id in registry.ids)
          _ProviderCard(endpoint: registry.lookup(id)!),
      ],
    );
  }
}

class _ProviderCard extends StatelessWidget {
  const _ProviderCard({required this.endpoint});

  final AtlasProviderEndpoint endpoint;

  @override
  Widget build(BuildContext context) {
    final policy = endpoint.policy;
    final theme = Theme.of(context);
    String flag(bool value) => value ? 'allowed' : 'not allowed';
    return Card(
      margin: const EdgeInsets.all(8.0),
      child: Padding(
        padding: const EdgeInsets.all(12.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              endpoint.descriptor.title ??
                  endpoint.descriptor.id.value,
              style: theme.textTheme.titleMedium,
            ),
            Text('${endpoint.descriptor.id.value} · ${endpoint.isLocal ? 'local bundle' : 'network'}'),
            const SizedBox(height: 4.0),
            Text('online: ${flag(policy.onlineAllowed)} · '
                'cache: ${flag(policy.cacheAllowed)} · '
                'prefetch: ${flag(policy.prefetchAllowed)}'),
            Text('max tiles: ${policy.maxTiles?.toString() ?? 'undeclared'} · '
                'rate: ${policy.maxRequestsPerSecond?.toString() ?? 'undeclared'}/s · '
                'key: ${policy.requiresKey ? 'required' : 'not required'}'),
            if (policy.bulkGuard != null)
              Text(
                'Bulk restriction: ${policy.bulkGuard}',
                style: TextStyle(color: theme.colorScheme.error),
              ),
            Text('License: ${endpoint.descriptor.license ?? 'undeclared'}'),
            Text('Attribution: ${endpoint.descriptor.attribution ?? 'undeclared'}'),
          ],
        ),
      ),
    );
  }
}

class _NewPackSheet extends StatefulWidget {
  const _NewPackSheet({required this.repository});

  final OfflineRepository repository;

  @override
  State<_NewPackSheet> createState() => _NewPackSheetState();
}

class _NewPackSheetState extends State<_NewPackSheet> {
  late String _providerId;
  final _zMin = TextEditingController(text: '0');
  final _zMax = TextEditingController(text: '0');
  final _xMin = TextEditingController(text: '0');
  final _xMax = TextEditingController(text: '0');
  final _yMin = TextEditingController(text: '0');
  final _yMax = TextEditingController(text: '0');
  final _bytesPerTile = TextEditingController();
  bool _approvedBulk = false;
  bool _isPrefetch = false;
  OfflinePackRecord? _result;

  @override
  void initState() {
    super.initState();
    _providerId = widget.repository.registry.ids.first;
  }

  @override
  void dispose() {
    _zMin.dispose();
    _zMax.dispose();
    _xMin.dispose();
    _xMax.dispose();
    _yMin.dispose();
    _yMax.dispose();
    _bytesPerTile.dispose();
    super.dispose();
  }

  int _parse(TextEditingController controller, String name) {
    final value = int.tryParse(controller.text.trim());
    if (value == null || value < 0) {
      throw FormatException('$name must be a non-negative integer.');
    }
    return value;
  }

  void _plan() {
    setState(() {
      try {
        final bytes = _bytesPerTile.text.trim().isEmpty
            ? -1
            : _parse(_bytesPerTile, 'bytes-per-tile');
        _result = widget.repository.planPack(
          providerId: _providerId,
          zMin: _parse(_zMin, 'zMin'),
          zMax: _parse(_zMax, 'zMax'),
          xMin: _parse(_xMin, 'xMin'),
          xMax: _parse(_xMax, 'xMax'),
          yMin: _parse(_yMin, 'yMin'),
          yMax: _parse(_yMax, 'yMax'),
          bytesPerTile: bytes,
          approvedBulk: _approvedBulk,
          isPrefetch: _isPrefetch,
        );
      } on FormatException catch (error) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(error.message)),
        );
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final registry = widget.repository.registry;
    final endpoint = registry.lookup(_providerId)!;
    return Padding(
      padding: EdgeInsets.only(
        left: 16.0,
        right: 16.0,
        top: 16.0,
        bottom: MediaQuery.of(context).viewInsets.bottom + 16.0,
      ),
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'New offline pack',
              style: TextStyle(fontSize: 18.0, fontWeight: FontWeight.bold),
            ),
            DropdownButtonFormField<String>(
              key: const ValueKey('provider-dropdown'),
              initialValue: _providerId,
              decoration: const InputDecoration(labelText: 'Provider'),
              items: [
                for (final id in registry.ids)
                  DropdownMenuItem(
                    value: id,
                    child: Text(
                      registry.lookup(id)!.descriptor.title ?? id,
                    ),
                  ),
              ],
              onChanged: (value) {
                if (value != null) setState(() => _providerId = value);
              },
            ),
            if (endpoint.policy.bulkGuard != null)
              Text(
                'Restriction: ${endpoint.policy.bulkGuard}',
                style: TextStyle(color: Theme.of(context).colorScheme.error),
              ),
            Text(
              'Prefetch ${endpoint.policy.prefetchAllowed ? 'allowed' : 'NOT allowed'} · '
              'max tiles ${endpoint.policy.maxTiles?.toString() ?? 'undeclared'}',
            ),
            Row(
              children: [
                Expanded(
                  child: TextField(
                    key: const ValueKey('zmin'),
                    controller: _zMin,
                    decoration: const InputDecoration(labelText: 'zMin'),
                    keyboardType: TextInputType.number,
                  ),
                ),
                Expanded(
                  child: TextField(
                    key: const ValueKey('zmax'),
                    controller: _zMax,
                    decoration: const InputDecoration(labelText: 'zMax'),
                    keyboardType: TextInputType.number,
                  ),
                ),
                Expanded(
                  child: TextField(
                    key: const ValueKey('xmin'),
                    controller: _xMin,
                    decoration: const InputDecoration(labelText: 'xMin'),
                    keyboardType: TextInputType.number,
                  ),
                ),
                Expanded(
                  child: TextField(
                    key: const ValueKey('xmax'),
                    controller: _xMax,
                    decoration: const InputDecoration(labelText: 'xMax'),
                    keyboardType: TextInputType.number,
                  ),
                ),
                Expanded(
                  child: TextField(
                    key: const ValueKey('ymin'),
                    controller: _yMin,
                    decoration: const InputDecoration(labelText: 'yMin'),
                    keyboardType: TextInputType.number,
                  ),
                ),
                Expanded(
                  child: TextField(
                    key: const ValueKey('ymax'),
                    controller: _yMax,
                    decoration: const InputDecoration(labelText: 'yMax'),
                    keyboardType: TextInputType.number,
                  ),
                ),
              ],
            ),
            TextField(
              key: const ValueKey('bytes-per-tile'),
              controller: _bytesPerTile,
              decoration: const InputDecoration(
                labelText: 'Bytes per tile estimate (required, no default)',
              ),
              keyboardType: TextInputType.number,
            ),
            CheckboxListTile(
              key: const ValueKey('approved-bulk'),
              title: const Text('Operator approves bulk use'),
              value: _approvedBulk,
              onChanged: (value) =>
                  setState(() => _approvedBulk = value ?? false),
              controlAffinity: ListTileControlAffinity.leading,
            ),
            CheckboxListTile(
              key: const ValueKey('prefetch'),
              title: const Text('This is a background prefetch'),
              value: _isPrefetch,
              onChanged: (value) =>
                  setState(() => _isPrefetch = value ?? false),
              controlAffinity: ListTileControlAffinity.leading,
            ),
            Wrap(
              spacing: 8.0,
              runSpacing: 8.0,
              children: [
                ElevatedButton(
                  key: const ValueKey('plan-pack'),
                  onPressed: _plan,
                  child: const Text('Plan'),
                ),
                if (_result?.plan != null)
                  ElevatedButton(
                    key: const ValueKey('download-pack'),
                    onPressed: () {
                      final id = _result!.packId;
                      Navigator.of(context).pop();
                      widget.repository.startDownload(id);
                    },
                    child: Text(
                      'Download ${_result!.tileCount} tiles '
                      '(est. ${formatBytes(_result!.estimatedBytes)})',
                    ),
                  ),
              ],
            ),
            if (_result?.refusal != null)
              Text(
                'Refused ${_result!.refusal!.reason}: '
                '${_result!.refusal!.detail}',
                style: TextStyle(color: Theme.of(context).colorScheme.error),
              ),
            if (_result?.appBlock != null)
              Text(
                'Blocked: ${_result!.appBlock}',
                style: TextStyle(color: Theme.of(context).colorScheme.error),
              ),
          ],
        ),
      ),
    );
  }
}
