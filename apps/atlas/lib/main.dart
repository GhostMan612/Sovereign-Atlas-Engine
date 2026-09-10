// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_providers/atlas_providers.dart';
import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';

import 'diagnostics/diagnostics_page.dart';
import 'offline/offline_page.dart';
import 'offline/offline_repository.dart';
import 'offline/offline_tile_provider.dart';

void main() {
  runApp(const AtlasApp());
}

const Map<String, String> _casualBasemaps = {
  'Standard': 'osm-standard',
  'Satellite': 'esri-imagery',
  'Topographic': 'opentopomap',
  'Dark': 'esri-dark-gray',
};

class AtlasApp extends StatefulWidget {
  const AtlasApp({super.key, OfflineRepository? repository})
      : _repositoryOverride = repository;

  final OfflineRepository? _repositoryOverride;

  @override
  State<AtlasApp> createState() => _AtlasAppState();
}

class _AtlasAppState extends State<AtlasApp> {
  late final OfflineRepository _repository;

  @override
  void initState() {
    super.initState();
    _repository = widget._repositoryOverride ??
        OfflineRepository(registry: AtlasBuiltinProviders.registry());

    if (widget._repositoryOverride == null) {
      _repository.restore();
    }
  }

  @override
  void dispose() {
    if (widget._repositoryOverride == null) _repository.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Sovereign Atlas',
      theme: ThemeData(colorSchemeSeed: Colors.indigo, useMaterial3: true),
      home: AtlasMapPage(repository: _repository),
    );
  }
}

class AtlasMapPage extends StatefulWidget {
  const AtlasMapPage({super.key, required this.repository});

  final OfflineRepository repository;

  @override
  State<AtlasMapPage> createState() => _AtlasMapPageState();
}

class _AtlasMapPageState extends State<AtlasMapPage> {
  final MapController _controller = MapController();
  final AtlasProviderRegistry _registry = AtlasBuiltinProviders.registry();
  LatLng _center = const LatLng(0.0, 0.0);
  double _zoom = 2.0;
  String _providerId = 'osm-standard';

  AtlasProviderEndpoint get _endpoint =>
      _registry.lookup(_providerId) ?? AtlasBuiltinProviders.osmStandard;

  String get _template =>
      _endpoint.urlTemplate ??
      'https://tile.openstreetmap.org/{z}/{x}/{y}.png';

  Future<void> _openPicker() async {
    final selected = await showModalBottomSheet<String>(
      context: context,
      builder: (context) => RadioGroup<String>(
        groupValue: _providerId,
        onChanged: (value) => Navigator.of(context).pop(value),
        child: ListView(
          shrinkWrap: true,
          children: [
            for (final entry in _casualBasemaps.entries)
              RadioListTile<String>(
                title: Text(entry.key),
                subtitle: Text(
                  _registry.lookup(entry.value)?.descriptor.title ??
                      entry.value,
                ),
                value: entry.value,
              ),
          ],
        ),
      ),
    );
    if (selected != null && selected != _providerId) {
      setState(() => _providerId = selected);
    }
  }

  @override
  Widget build(BuildContext context) {
    final attribution = _endpoint.descriptor.attribution ?? '';
    return Scaffold(
      appBar: AppBar(
        title: const Text('Sovereign Atlas'),
        actions: [
          IconButton(
            icon: const Icon(Icons.layers),
            tooltip: 'Basemap',
            onPressed: _openPicker,
          ),
          IconButton(
            icon: const Icon(Icons.download),
            tooltip: 'offline-areas',
            onPressed: () => Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) => OfflinePage(repository: widget.repository),
              ),
            ),
          ),
          IconButton(
            icon: const Icon(Icons.assessment),
            tooltip: 'diagnostics',
            onPressed: () => Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) => DiagnosticsPage(repository: widget.repository),
              ),
            ),
          ),
        ],
      ),
      body: Column(
        children: [
          Expanded(
            child: FlutterMap(
              mapController: _controller,
              options: MapOptions(
                initialCenter: _center,
                initialZoom: _zoom,
                onPositionChanged: (position, _) {
                  setState(() {
                    _center = position.center;
                    _zoom = position.zoom;
                  });
                },
              ),
              children: [
                TileLayer(
                  key: ValueKey<String>(_providerId),
                  urlTemplate: _template,
                  userAgentPackageName: 'com.sovereignatlas.atlas',

                  tileProvider: AtlasOfflineTileProvider(
                    repository: widget.repository,
                    providerId: _providerId,
                  ),
                ),
                MarkerLayer(
                  markers: [
                    Marker(
                      point: _center,
                      child: const Icon(
                        Icons.location_on,
                        color: Colors.red,
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
          if (attribution.isNotEmpty)
            Container(
              width: double.infinity,
              color: Colors.black54,
              padding: const EdgeInsets.symmetric(
                horizontal: 8.0,
                vertical: 2.0,
              ),
              child: Text(
                attribution,
                style: const TextStyle(color: Colors.white, fontSize: 10.0),
              ),
            ),
        ],
      ),
      bottomNavigationBar: BottomAppBar(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
          child: Text(
            'lat ${_center.latitude.toStringAsFixed(4)} · '
            'lon ${_center.longitude.toStringAsFixed(4)} · '
            'zoom ${_zoom.toStringAsFixed(1)}',
          ),
        ),
      ),
    );
  }
}
