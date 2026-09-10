// Sovereign Atlas — first host shell (ADR-005 app track).
//
// Boundary: UI + flutter_map adapter here; engine semantics stay in
// packages/ (this file imports engine CONTRACTS only — no engine package
// imports Flutter, ever). Basemap template comes from the engine provider
// registry (single source of truth, blueprint 2.2 engine completion).
import 'package:atlas_providers/atlas_providers.dart';
import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';

void main() {
  runApp(const AtlasApp());
}

/// Engine-owned basemap template (OSM standard, blueprint 2.2 set).
String get _osmTemplate =>
    AtlasBuiltinProviders.osmStandard.urlTemplate ??
    'https://tile.openstreetmap.org/{z}/{x}/{y}.png';

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
  LatLng _center = const LatLng(0.0, 0.0);
  double _zoom = 2.0;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Sovereign Atlas')),
      body: FlutterMap(
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
            urlTemplate: _osmTemplate,
            userAgentPackageName: 'com.sovereignatlas.atlas',
          ),
          MarkerLayer(
            markers: [
              Marker(
                point: _center,
                child: const Icon(Icons.location_on, color: Colors.red),
              ),
            ],
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
