// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'dart:math' as math;

import 'package:atlas_geo/atlas_geo.dart';
import 'package:atlas_providers/atlas_providers.dart';
import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';

import 'diagnostics/diagnostics_page.dart';
import 'location/heading_service.dart';
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

enum _OrientationMode { northUp, headingUp }

const TextStyle _readoutStyle = TextStyle(fontSize: 12.0);

class AtlasApp extends StatefulWidget {
  const AtlasApp(
      {super.key,
      OfflineRepository? repository,
      LocationService? locationService,
      HeadingService? headingService})
      : _repositoryOverride = repository,
        _locationOverride = locationService,
        _headingOverride = headingService;

  final OfflineRepository? _repositoryOverride;
  final LocationService? _locationOverride;
  final HeadingService? _headingOverride;

  @override
  State<AtlasApp> createState() => _AtlasAppState();
}

class _AtlasAppState extends State<AtlasApp> {
  late final OfflineRepository _repository;
  late final LocationService _location;
  late final HeadingService _heading;

  @override
  void initState() {
    super.initState();
    _repository = widget._repositoryOverride ??
        OfflineRepository(registry: AtlasBuiltinProviders.registry());
    _location = widget._locationOverride ??
        LocationService(locationSource: ChannelLocationSource());
    _heading = widget._headingOverride ??
        HeadingService(source: ChannelHeadingSource());

    if (widget._repositoryOverride == null) {
      _repository.restore();
    }
  }

  @override
  void dispose() {
    if (widget._repositoryOverride == null) _repository.dispose();
    if (widget._locationOverride == null) _location.dispose();
    if (widget._headingOverride == null) _heading.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Sovereign Atlas',
      theme: ThemeData(colorSchemeSeed: Colors.indigo, useMaterial3: true),
      home: AtlasMapPage(
          repository: _repository,
          locationService: _location,
          headingService: _heading),
    );
  }
}

class AtlasMapPage extends StatefulWidget {
  const AtlasMapPage(
      {super.key,
      required this.repository,
      required this.locationService,
      required this.headingService});

  final OfflineRepository repository;
  final LocationService locationService;
  final HeadingService headingService;

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
  _OrientationMode _orientation = _OrientationMode.northUp;
  bool _pendingHeadingUp = false;

  @override
  void initState() {
    super.initState();
    widget.locationService.addListener(_onLocationChanged);
    widget.headingService.addListener(_onHeadingChanged);
  }

  @override
  void dispose() {
    widget.locationService.removeListener(_onLocationChanged);
    widget.headingService.removeListener(_onHeadingChanged);
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

  void _onHeadingChanged() {
    if (!mounted) return;
    final degrees = widget.headingService.displayDeg;
    if (_pendingHeadingUp && degrees != null) {
      _pendingHeadingUp = false;
      _orientation = _OrientationMode.headingUp;
      _controller.rotate(AtlasAngles.normalizeBearingDeg(-degrees));
    } else if (_orientation == _OrientationMode.headingUp &&
        degrees != null) {
      _controller.rotate(AtlasAngles.normalizeBearingDeg(-degrees));
    }
    setState(() {});
  }

  Future<void> _toggleOrientation() async {
    if (_orientation == _OrientationMode.headingUp) {
      _orientation = _OrientationMode.northUp;
      _pendingHeadingUp = false;
      _controller.rotate(0.0);
      setState(() {});
      return;
    }
    await widget.headingService.ensureStarted();
    if (!mounted) return;
    final degrees = widget.headingService.displayDeg;
    if (degrees != null) {
      _pendingHeadingUp = false;
      _orientation = _OrientationMode.headingUp;
      _controller.rotate(AtlasAngles.normalizeBearingDeg(-degrees));
    } else if (!widget.headingService.unsupported) {
      _pendingHeadingUp = true;
    }
    setState(() {});
  }

  void _faceNorth() {
    _orientation = _OrientationMode.northUp;
    _pendingHeadingUp = false;
    _controller.rotate(0.0);
    setState(() {});
  }

  String _orientToken() {
    final heading = widget.headingService;
    if (heading.unsupported) return 'orient unsupported';
    if (heading.latest == null) return 'orient off';
    return _orientation == _OrientationMode.headingUp
        ? 'orient heading-up'
        : 'orient north-up';
  }

  Widget _compassDial(double degrees, bool dimmed, String frame) {
    const labelStyle = TextStyle(color: Colors.white70, fontSize: 10.0);
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        GestureDetector(
          onTap: _faceNorth,
          child: Tooltip(
            message: 'compass',
            child: Opacity(
              opacity: dimmed ? 0.4 : 1.0,
              child: Container(
                width: 72.0,
                height: 72.0,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: Colors.black54,
                  border: Border.all(color: Colors.white70, width: 2.0),
                ),
                child: Stack(
                  children: [
                    Center(
                      child: Transform.rotate(
                        angle: -degrees * math.pi / 180.0,
                        child: const SizedBox(
                          width: 64.0,
                          height: 64.0,
                          child: Stack(
                            children: [
                              Positioned(
                                top: 2.0,
                                left: 0.0,
                                right: 0.0,
                                child: Center(
                                  child: Text(
                                    'N',
                                    style: TextStyle(
                                      color: Colors.redAccent,
                                      fontWeight: FontWeight.bold,
                                    ),
                                  ),
                                ),
                              ),
                              Positioned(
                                bottom: 2.0,
                                left: 0.0,
                                right: 0.0,
                                child: Center(
                                  child: Text('S', style: labelStyle),
                                ),
                              ),
                              Positioned(
                                left: 4.0,
                                top: 0.0,
                                bottom: 0.0,
                                child: Center(
                                  child: Text('W', style: labelStyle),
                                ),
                              ),
                              Positioned(
                                right: 4.0,
                                top: 0.0,
                                bottom: 0.0,
                                child: Center(
                                  child: Text('E', style: labelStyle),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                    Center(
                      child: Container(
                        width: 8.0,
                        height: 8.0,
                        decoration: BoxDecoration(
                          color: Colors.blue,
                          shape: BoxShape.circle,
                          border: Border.all(color: Colors.white, width: 1.5),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
        Text(
          frame,
          style: const TextStyle(
            color: Colors.white,
            fontSize: 10.0,
            fontWeight: FontWeight.bold,
          ),
        ),
      ],
    );
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
            icon: Icon(_orientation == _OrientationMode.headingUp
                ? Icons.explore
                : Icons.explore_outlined),
            tooltip: 'heading-up',
            onPressed: _toggleOrientation,
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
            child: Stack(
              children: [
                FlutterMap(
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
            if (widget.headingService.displayDeg != null)
              Positioned(
                top: 12.0,
                right: 12.0,
                child: _compassDial(
                  widget.headingService.displayDeg!,
                  widget.headingService.dimmed,
                  widget.headingService.frameLabel,
                ),
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
        height: 96.0,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 6.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
                Text(
                  'MAP lat ${_center.latitude.toStringAsFixed(4)} · '
                  'lon ${_center.longitude.toStringAsFixed(4)} · '
                  'zoom ${_zoom.toStringAsFixed(1)}',
                  style: _readoutStyle,
                  softWrap: false,
                  overflow: TextOverflow.ellipsis,
                ),
                Text(
                  _deviceLine(),
                  style: _readoutStyle,
                  softWrap: false,
                  overflow: TextOverflow.ellipsis,
                ),
                Text(_orientToken(), style: _readoutStyle),
              ],
            ),
          ),
      ),
    );
  }
}
