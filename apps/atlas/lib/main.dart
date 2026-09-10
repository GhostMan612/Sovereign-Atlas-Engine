// Sovereign Atlas — first host shell (ADR-005 app track).
//
// Boundary: UI + flutter_map adapter here; engine semantics stay in
// packages/ (this file imports engine CONTRACTS only — no engine package
// imports Flutter, ever). Basemap set, templates, and attribution all come
// from the engine provider registry (single source of truth, blueprint
// 2.2 engine completion); the casual picker (blueprint 2.4) is a thin view
// over it.
import 'package:atlas_providers/atlas_providers.dart';
import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';

void main() {
  runApp(const AtlasApp());
}

/// Casual basemap choices (blueprint 2.4) bound to engine provider ids.
const Map<String, String> _casualBasemaps = {
  'Standard': 'osm-standard',
  'Satellite': 'esri-imagery',
  'Topographic': 'opentopomap',
  'Dark': 'esri-dark-gray',
};

class AtlasApp extends StatelessWidget {
  const AtlasApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Sovereign Atlas',
      theme: ThemeData(colorSchemeSeed: Colors.indigo, useMaterial3: true),
      home: const AtlasMapPage(),
    );
  }
}

class AtlasMapPage extends StatefulWidget {
  const AtlasMapPage({super.key});

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
