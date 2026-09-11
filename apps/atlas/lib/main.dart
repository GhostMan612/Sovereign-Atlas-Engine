// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_providers/atlas_providers.dart';
import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';

import 'diagnostics/diagnostics_page.dart';
import 'location/location_service.dart';
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
  const AtlasApp({super.key, OfflineRepository? repository, LocationService? locationService})
      : _repositoryOverride = repository,
        _locationOverride = locationService;

  final OfflineRepository? _repositoryOverride;
  final LocationService? _locationOverride;

  @override
  State<AtlasApp> createState() => _AtlasAppState();
}

class _AtlasAppState extends State<AtlasApp> {
  late final OfflineRepository _repository;
  late final LocationService _location;

  @override
  void initState() {
    super.initState();
    _repository = widget._repositoryOverride ??
        OfflineRepository(registry: AtlasBuiltinProviders.registry());
    _location = widget._locationOverride ??
        LocationService(locationSource: ChannelLocationSource());

    if (widget._repositoryOverride == null) {
      _repository.restore();
    }
  }

  @override
  void dispose() {
    if (widget._repositoryOverride == null) _repository.dispose();
    if (widget._locationOverride == null) _location.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Sovereign Atlas',
      theme: ThemeData(colorSchemeSeed: Colors.indigo, useMaterial3: true),
      home: AtlasMapPage(repository: _repository, locationService: _location),
    );
  }
}

class AtlasMapPage extends StatefulWidget {
  const AtlasMapPage({super.key, required this.repository, required this.locationService});

  final OfflineRepository repository;
  final LocationService locationService;

  @override
  State<AtlasMapPage> createState() => _AtlasMapPageState();
}

class _AtlasMapPageState extends State<AtlasMapPage> {
  final MapController _controller = MapController();
  final AtlasProviderRegistry _registry = AtlasBuiltinProviders.registry();
  LatLng _center = const LatLng(0.0, 0.0);
  double _zoom = 2.0;
  String _providerId = 'osm-standard';
  bool _pendingRecenter = false;

  @override
  void initState() {
    super.initState();
    widget.locationService.addListener(_onLocationChanged);
  }

  @override
  void dispose() {
    widget.locationService.removeListener(_onLocationChanged);
    super.dispose();
  }

  void _onLocationChanged() {
    if (!mounted) return;
    if (_pendingRecenter) {
      final target = widget.locationService.recenterTarget;
      if (target != null) {
        _pendingRecenter = false;
        _controller.move(target, _zoom);
      }
    }
    setState(() {});
  }

  Future<void> _locate() async {
    await widget.locationService.ensureActive();
    if (!mounted) return;
    final target = widget.locationService.recenterTarget;
    if (target != null) {
      _pendingRecenter = false;
      _controller.move(target, _zoom);
    } else if (widget.locationService.status ==
        AtlasLocationStatus.acquiring) {
      _pendingRecenter = true;
    }
  }

  LatLng? get _fixPoint {
    final fix = widget.locationService.latestFix;
    if (fix == null) return null;
    return LatLng(fix.position.latitude, fix.position.longitude);
  }

  CircleMarker? get _accuracyCircle {
    final fix = widget.locationService.latestFix;
    final accuracy = fix?.accuracyMeters;
    final point = _fixPoint;
    if (point == null ||
        accuracy == null ||
        !accuracy.isFinite ||
        accuracy <= 0) {
      return null;
    }
    return CircleMarker(
      key: const ValueKey<String>('accuracy-circle'),
      point: point,
      radius: accuracy,
      useRadiusInMeter: true,
      color: const Color(0x332196F3),
      borderColor: Colors.blue,
      borderStrokeWidth: 2.0,
    );
  }

  String _deviceLine() {
    final location = widget.locationService;
    switch (location.status) {
      case AtlasLocationStatus.notRequested:
        return 'DEVICE not-requested';
      case AtlasLocationStatus.denied:
        return 'DEVICE denied';
      case AtlasLocationStatus.permanentlyDenied:
        return 'DEVICE permanently-denied';
      case AtlasLocationStatus.servicesDisabled:
        return 'DEVICE services-disabled';
      case AtlasLocationStatus.acquiring:
        return 'DEVICE acquiring';
      case AtlasLocationStatus.error:
        return 'DEVICE error';
      case AtlasLocationStatus.valid:
      case AtlasLocationStatus.stale:
        final fix = location.latestFix;
        if (fix == null) return 'DEVICE acquiring';
        final label = location.status == AtlasLocationStatus.stale
            ? 'stale'
            : 'valid';
        final accuracy = fix.accuracyMeters;
        final acc = accuracy == null
            ? 'UNKNOWN'
            : '${accuracy.toStringAsFixed(1)} m';
        return 'DEVICE $label lat ${fix.position.latitude.toStringAsFixed(4)} '
            'lon ${fix.position.longitude.toStringAsFixed(4)} acc $acc';
    }
  }

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
            icon: const Icon(Icons.my_location),
            tooltip: 'my-location',
            onPressed: _locate,
          ),
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
                initialRotation: 0.0,
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
                    if (_fixPoint != null)
                      Marker(
                        key: const ValueKey<String>('position-marker'),
                        point: _fixPoint!,
                        child: Container(
                          width: 20.0,
                          height: 20.0,
                          decoration: BoxDecoration(
                            color: Colors.blue,
                            shape: BoxShape.circle,
                            border: Border.all(
                              color: Colors.white,
                              width: 3.0,
                            ),
                          ),
                        ),
                      ),
                  ],
                ),
                if (_accuracyCircle != null)
                  CircleLayer(
                    circles: [_accuracyCircle!],
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
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'MAP lat ${_center.latitude.toStringAsFixed(4)} · '
                'lon ${_center.longitude.toStringAsFixed(4)} · '
                'zoom ${_zoom.toStringAsFixed(1)}',
              ),
              Text(_deviceLine()),
            ],
          ),
        ),
      ),
    );
  }
}
