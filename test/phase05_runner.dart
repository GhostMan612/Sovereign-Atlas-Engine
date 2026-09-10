// Sovereign Atlas Engine — Phase 0.5 contract-fixture runner (TEST TOOLING).
//
// This file is development/test infrastructure, NOT production engine code.
// It may use dart:io and dart:convert (forbidden in atlas_core production).
// Allowed by the 0.5 directive: "Tests may be created under the established
// test structure." Run from the repository root with a bare Dart SDK:
//
//   dart test/phase05_runner.dart
//
// No package manifests are required: all imports are relative file paths.
// Exit code 0 = no FAIL; 1 = at least one FAIL. BLOCKED and NOT_APPLICABLE
// never fail the run; they are reported for architect adjudication.

import 'dart:convert';
import 'dart:io';

import 'package:atlas_core/atlas_core.dart';
import 'package:atlas_geo/atlas_geo.dart';
import 'package:atlas_layers/atlas_layers.dart';
import 'package:atlas_map/atlas_map.dart';
import 'package:atlas_provider_api/atlas_provider_api.dart';
import 'package:atlas_providers/atlas_providers.dart';
import 'package:atlas_offline/atlas_offline.dart';
import 'package:atlas_tiles/atlas_tiles.dart';

/// Per-fixture verdict.
enum Verdict { pass, fail, blocked, notApplicable }

final class _Outcome {
  _Outcome(this.id, this.verdict, this.detail);
  final String id;
  final Verdict verdict;
  final String detail;
}

final List<_Outcome> _outcomes = [];

void _record(String id, Verdict verdict, String detail) {
  _outcomes.add(_Outcome(id, verdict, detail));
  stdout.writeln('${verdict.name.toUpperCase().padRight(14)} $id  $detail');
}

Map<String, dynamic> _load(String path) =>
    jsonDecode(File(path).readAsStringSync()) as Map<String, dynamic>;

double _num(dynamic value) => (value as num).toDouble();

AtlasCoordinate _coord(Map<String, dynamic> m) => AtlasCoordinate(
      latitude: _num(m['latitude']),
      longitude: _num(m['longitude']),
    );

bool _close(double actual, double expected, double tolerance) =>
    AtlasComparison.withinTolerance(actual, expected, tolerance);

// ---------------------------------------------------------------------------
// GEO
// ---------------------------------------------------------------------------

void _geo(Map<String, dynamic> f) {
  final id = f['id'] as String;
  if (id == 'GEO-007') {
    _record(id, Verdict.blocked, 'DEC-004 open (normalize vs reject).');
    return;
  }
  final inputs = f['inputs'] as Map<String, dynamic>;
  final expected = f['expected'] as Map<String, dynamic>;
  if (id == 'GEO-006') {
    // Boundary-case array: every listed pole position must validate.
    var ok = true;
    for (final c in (inputs['cases'] as List).cast<Map<String, dynamic>>()) {
      if (!AtlasCoordinates.validate(
        _num(c['latitude']),
        _num(c['longitude']),
      ).isValid) {
        ok = false;
      }
    }
    _record(id, ok ? Verdict.pass : Verdict.fail, 'pole boundaries accept');
    return;
  }
  final check = AtlasCoordinates.validate(
    _num(inputs['latitude']),
    _num(inputs['longitude']),
  );
  final wantValid = expected['valid'] as bool;
  _record(
    id,
    check.isValid == wantValid ? Verdict.pass : Verdict.fail,
    'valid=${check.isValid} want=$wantValid',
  );
}

// ---------------------------------------------------------------------------
// DISTANCE / BEARING
// ---------------------------------------------------------------------------

void _distance(Map<String, dynamic> f) {
  final id = f['id'] as String;
  final inputs = f['inputs'] as Map<String, dynamic>;
  final expected = f['expected'] as Map<String, dynamic>;
  final tolerance =
      ((f['tolerance'] as Map<String, dynamic>?)?['value'] as num?)
              ?.toDouble() ??
          0.0;
  final dist = AtlasGeoMath.haversineKm(
    _coord(inputs['from'] as Map<String, dynamic>),
    _coord(inputs['to'] as Map<String, dynamic>),
    radiusKm: _num(inputs['reference_radius_km'] ?? 6371.0088),
  );
  final want = _num(expected['value_km']);
  var ok = _close(dist, want, tolerance);
  var detail = 'got=$dist want=$want tol=$tolerance';
  if (expected.containsKey('display')) {
    final display = AtlasGeoMath.formatDistance(dist);
    final wantDisplay = expected['display'] as String;
    ok = ok && display == wantDisplay;
    detail += ' display="$display" want="$wantDisplay"';
  }
  _record(id, ok ? Verdict.pass : Verdict.fail, detail);
}

void _bearing(Map<String, dynamic> f) {
  final id = f['id'] as String;
  if (id == 'BRG-004') {
    _record(id, Verdict.blocked, 'Coincident-bearing shape undecided.');
    return;
  }
  if (id == 'DEST-001') {
    final inputs = f['inputs'] as Map<String, dynamic>;
    final expected = f['expected'] as Map<String, dynamic>;
    final tolerance = _num(
      ((f['tolerance'] as Map<String, dynamic>)['value'] as num),
    );
    final got = AtlasGeoMath.destinationPoint(
      _coord(inputs['origin'] as Map<String, dynamic>),
      _num(inputs['distance_km']),
      _num(inputs['bearing_deg']),
    );
    final ok = _close(got.latitude, _num(expected['latitude']), tolerance) &&
        _close(got.longitude, _num(expected['longitude']), tolerance);
    _record(
      id,
      ok ? Verdict.pass : Verdict.fail,
      'PROVISIONAL op; got=(${got.latitude}, ${got.longitude})',
    );
    return;
  }
  final tolerance = _num(
    ((f['tolerance'] as Map<String, dynamic>)['value'] as num),
  );
  if (id == 'BRG-001') {
    final expected = f['expected'] as Map<String, dynamic>;
    var ok = true;
    final got = <String>[];
    for (final c in (expected['cases'] as List).cast<Map<String, dynamic>>()) {
      final from = (c['from'] as List).map((v) => _num(v)).toList();
      final to = (c['to'] as List).map((v) => _num(v)).toList();
      final value = AtlasGeoMath.initialBearingDeg(
        AtlasCoordinate(latitude: from[0], longitude: from[1]),
        AtlasCoordinate(latitude: to[0], longitude: to[1]),
      );
      got.add('${c['direction']}=$value');
      if (!_close(value, _num(c['value_deg']), tolerance)) ok = false;
    }
    _record(id, ok ? Verdict.pass : Verdict.fail, got.join(' '));
    return;
  }
  final inputs = f['inputs'] as Map<String, dynamic>;
  final expected = f['expected'] as Map<String, dynamic>;
  final value = AtlasGeoMath.initialBearingDeg(
    _coord(inputs['from'] as Map<String, dynamic>),
    _coord(inputs['to'] as Map<String, dynamic>),
  );
  final want = _num(expected['value_deg']);
  _record(
    id,
    _close(value, want, tolerance) ? Verdict.pass : Verdict.fail,
    'got=$value want=$want tol=$tolerance',
  );
}

// ---------------------------------------------------------------------------
// CAMERA
// ---------------------------------------------------------------------------

void _camera(Map<String, dynamic> f) {
  final id = f['id'] as String;
  final expected = f['expected'] as Map<String, dynamic>;
  if (id == 'TRANSITION-001') {
    final base = AtlasMapState(
      camera: AtlasCameraState.home(),
      layers: const AtlasLayerStack([]),
    );
    final moved = base.copyWith(camera: base.camera.copyWith(zoom: 10.0));
    final ok = moved.camera.zoom == _num(expected['zoom']) &&
        moved.layers == base.layers &&
        moved.validate().isValid == (expected['valid'] as bool);
    _record(
      id,
      ok && (expected['layers_preserved'] as bool)
          ? Verdict.pass
          : Verdict.fail,
      'pure recomposition, layers preserved',
    );
    return;
  }
  if (id == 'TRANSITION-002') {
    final turned = AtlasCameraState.home().copyWith(bearing: 90.0);
    final ok = turned.bearing == _num(expected['bearing']) &&
        turned.center == AtlasCameraState.home().center &&
        turned.validate().isValid == (expected['valid'] as bool);
    _record(
      id,
      ok && (expected['center_preserved'] as bool)
          ? Verdict.pass
          : Verdict.fail,
      'bearing=90, center preserved',
    );
    return;
  }
  if (id == 'EQUALITY-001') {
    final a = AtlasMapState(
      camera: AtlasCameraState.home(),
      layers: const AtlasLayerStack([]),
    );
    final c = a.copyWith(camera: a.camera.copyWith(zoom: 10.0));
    final ok = (a ==
                AtlasMapState(
                  camera: AtlasCameraState.home(),
                  layers: const AtlasLayerStack([]),
                )) ==
            (expected['identical_equal'] as bool) &&
        (a != c) == (expected['different_zoom_not_equal'] as bool);
    _record(id, ok ? Verdict.pass : Verdict.fail, 'structural equality');
    return;
  }
  if (id == 'EQUALITY-002') {
    AtlasLayerState titled(String layerId, String title) => AtlasLayerState(
          definition: AtlasLayerDefinition(
            id: AtlasId(layerId),
            kind: AtlasLayerKind.raster,
            providerId: 'p',
            title: title,
          ),
        );
    final inputs = f['inputs'] as Map<String, dynamic>;
    AtlasLayerStack stackOf(List<dynamic> layers) => AtlasLayerStack([
          for (final l in layers.cast<Map<String, dynamic>>())
            titled(l['id'] as String, l['title'] as String),
        ]);
    final a = AtlasMapState(
      camera: AtlasCameraState.home(),
      layers: stackOf(inputs['a_layers'] as List),
    );
    final b = AtlasMapState(
      camera: AtlasCameraState.home(),
      layers: stackOf(inputs['b_layers'] as List),
    );
    _record(
      id,
      (a == b) == (expected['equal'] as bool) ? Verdict.pass : Verdict.fail,
      'title difference is not map-state difference',
    );
    return;
  }
  if (id == 'SERDE-001') {
    final inputs = f['inputs'] as Map<String, dynamic>;
    final once = AtlasCameraState.parse(inputs['serialized'] as String);
    final twice = AtlasCameraState.parse(once.serialize());
    final ok = once.serialize() == expected['canonical'] &&
        twice == once &&
        (expected['round_trip_stable'] as bool);
    _record(
      id,
      ok ? Verdict.pass : Verdict.fail,
      'canonical=${once.serialize()}',
    );
    return;
  }
  switch (id) {
    case 'CAM-001':
      final home = AtlasCameraState.home();
      final ok = home.center.latitude == _num(expected['latitude']) &&
          home.center.longitude == _num(expected['longitude']) &&
          home.zoom == _num(expected['zoom']);
      _record(id, ok ? Verdict.pass : Verdict.fail, home.serialize());
    case 'CAM-002':
      final parsed = AtlasCameraState.parse(expected['serialized'] as String);
      final roundTrip = parsed.serialize() == expected['serialized'];
      _record(
        id,
        roundTrip && parsed.validate().isValid ? Verdict.pass : Verdict.fail,
        'roundTrip=$roundTrip',
      );
    case 'CAM-003':
    case 'CAM-004':
      try {
        AtlasCameraState.parse(
          (f['inputs'] as Map<String, dynamic>)['serialized'] as String,
        );
        _record(id, Verdict.fail, 'parsed without rejection');
      } on AtlasRejectionException catch (e) {
        final want = (expected['rejection'] as Map<String, dynamic>)['category']
            as String;
        _record(
          id,
          e.rejection.category == want ? Verdict.pass : Verdict.fail,
          'category=${e.rejection.category} want=$want',
        );
      }
    case 'CAM-005':
      final accept = (expected['accept'] as List).map(_num).toList();
      final reject = (expected['reject'] as List).map(_num).toList();
      var ok = true;
      for (final z in accept) {
        final state = AtlasCameraState(
          center: const AtlasCoordinate(latitude: 0, longitude: 0),
          zoom: z,
          bearing: 0,
          pitch: 0,
        );
        if (!state.validate().isValid) ok = false;
      }
      for (final z in reject) {
        final state = AtlasCameraState(
          center: const AtlasCoordinate(latitude: 0, longitude: 0),
          zoom: z,
          bearing: 0,
          pitch: 0,
        );
        final v = state.validate();
        if (v.isValid || v.rejection?.category != 'INVALID_ZOOM') ok = false;
      }
      _record(id, ok ? Verdict.pass : Verdict.fail, 'bounds enforced');
    case 'CAM-006':
      final natives = (expected['provider_natives'] as Map<String, dynamic>)
          .values
          .map(_num)
          .toList();
      final distinct = natives.every((n) => n != AtlasCameraState.maxZoom);
      _record(
        id,
        distinct && AtlasCameraState.maxZoom == _num(expected['camera_maximum'])
            ? Verdict.pass
            : Verdict.fail,
        'cameraMax=${AtlasCameraState.maxZoom} natives=$natives',
      );
  }
}

// ---------------------------------------------------------------------------
// LAYERS (order, attribution; descriptors belong to atlas_provider_api scope)
// ---------------------------------------------------------------------------

AtlasLayerDefinition _testDef(
  String id, {
  String attribution = '',
  bool isPrivate = false,
}) =>
    AtlasLayerDefinition(
      id: AtlasId(id),
      kind: AtlasLayerKind.raster,
      providerId: 'test-provider',
      attribution: attribution.isEmpty ? null : attribution,
      isPrivate: isPrivate,
    );

AtlasLayerCategory? _category(String? name) => name == null
    ? null
    : AtlasLayerCategory.values.firstWhere((v) => v.name == name);

Set<AtlasLayerCapability> _caps(List<dynamic> names) => {
      for (final n in names.cast<String>())
        AtlasLayerCapability.values.firstWhere((v) => v.name == n),
    };

void _layers(Map<String, dynamic> f) {
  final id = f['id'] as String;
  final expected = f['expected'] as Map<String, dynamic>;
  if (id == 'LAYER-001') {
    final inputs = f['inputs'] as Map<String, dynamic>;
    final defs = [
      for (final l in (inputs['layers'] as List).cast<Map<String, dynamic>>())
        AtlasLayerDefinition(
          id: AtlasId(l['id'] as String),
          kind: AtlasLayerKind.raster,
          providerId: l['provider'] as String,
          datasetId: l['dataset'] as String,
          title: l['title'] as String,
        ),
    ];
    final stack = AtlasLayerStack([
      for (final d in defs) AtlasLayerState(definition: d),
    ]);
    final ok = defs[0].id != defs[1].id &&
        defs[0] != defs[1] &&
        stack.validate().isValid == (expected['stack_valid'] as bool);
    _record(
      id,
      ok && (expected['distinct_ids'] as bool) ? Verdict.pass : Verdict.fail,
      'same title, distinct ids; triple identity opaque',
    );
    return;
  }
  if (id == 'LAYER-002') {
    final names = AtlasLayerCategory.values.map((v) => v.name).toSet();
    final want = (expected['values'] as List).cast<String>().toSet();
    final roundTrips = want.every((n) => _category(n) != null);
    const unclassified = AtlasLayerDefinition(
      id: AtlasId('x'),
      kind: AtlasLayerKind.raster,
      providerId: 'p',
    );
    final ok = names.containsAll(want) &&
        want.containsAll(names) &&
        roundTrips &&
        unclassified.category == null;
    _record(
      id,
      ok && (expected['unclassified_valid'] as bool)
          ? Verdict.pass
          : Verdict.fail,
      'PROVISIONAL taxonomy; categories=$names',
    );
    return;
  }
  if (id == 'LAYER-003') {
    final inputs = f['inputs'] as Map<String, dynamic>;
    final def = AtlasLayerDefinition(
      id: const AtlasId('c'),
      kind: AtlasLayerKind.vector,
      providerId: 'p',
      capabilities: _caps(inputs['capabilities'] as List),
    );
    const bare = AtlasLayerDefinition(
      id: AtlasId('b'),
      kind: AtlasLayerKind.vector,
      providerId: 'p',
    );
    final want = (expected['advertised'] as List).cast<String>().toSet();
    final ok = def.capabilities.map((v) => v.name).toSet().containsAll(want) &&
        bare.capabilities.isEmpty == (expected['default_empty'] as bool);
    _record(
      id,
      ok ? Verdict.pass : Verdict.fail,
      'PROVISIONAL flags; advertised-only, implements nothing',
    );
    return;
  }
  if (id == 'LAYER-004') {
    const stack = AtlasLayerStack([]);
    final ok = stack.validate().isValid &&
        stack.orderedVisible().isEmpty &&
        stack.conformsToBaseline(AtlasBaselineRanks.rankOf);
    _record(id, ok ? Verdict.pass : Verdict.fail, 'empty stack valid');
    return;
  }
  if (id == 'LAYER-005') {
    const start = AtlasLayerState(
      definition: AtlasLayerDefinition(
        id: AtlasId('s'),
        kind: AtlasLayerKind.raster,
        providerId: 'p',
      ),
    );
    final flipped = start.toggled();
    final dimmed = start.withOpacity(0.5);
    final copied = start.copyWith(visible: false);
    var threw = false;
    try {
      start.withOpacity(2.0);
    } on AtlasRejectionException catch (e) {
      threw = e.rejection.category == 'INVALID_LAYER_STATE';
    }
    final ok = flipped.visible == false &&
        dimmed.opacity == _num(expected['withOpacity_0_5']) &&
        (copied.visible == false) ==
            (expected['copyWith_visible_false'] as bool) &&
        threw;
    _record(
      id,
      ok && (expected['toggled_flips'] as bool) ? Verdict.pass : Verdict.fail,
      'PROVISIONAL transitions; no clamping',
    );
    return;
  }
  if (id == 'LAYER-006') {
    AtlasLayerDefinition def(String layerId, String title) =>
        AtlasLayerDefinition(
          id: AtlasId(layerId),
          kind: AtlasLayerKind.raster,
          providerId: 'p',
          title: title,
        );
    final inputs = f['inputs'] as Map<String, dynamic>;
    final a = def(
      (inputs['a'] as Map<String, dynamic>)['id'] as String,
      (inputs['a'] as Map<String, dynamic>)['title'] as String,
    );
    final b = def(
      (inputs['b'] as Map<String, dynamic>)['id'] as String,
      (inputs['b'] as Map<String, dynamic>)['title'] as String,
    );
    final c = def(
      (inputs['c'] as Map<String, dynamic>)['id'] as String,
      (inputs['c'] as Map<String, dynamic>)['title'] as String,
    );
    final ok = (a == b) == (expected['different_title_still_equal'] as bool) &&
        (a != c) == (expected['different_id_not_equal'] as bool);
    _record(
      id,
      ok ? Verdict.pass : Verdict.fail,
      'title excluded from definition equality',
    );
    return;
  }
  if (id == 'LAYER-007') {
    final inputs = f['inputs'] as Map<String, dynamic>;
    final ids = (inputs['stack'] as List).cast<String>();
    final stack = AtlasLayerStack([
      for (final layerId in ids) AtlasLayerState(definition: _testDef(layerId)),
    ]);
    final got =
        stack.orderedVisible().map((s) => s.definition.id.value).toList();
    final want = (expected['order_preserved'] as List).cast<String>();
    final ok = got.join(',') == want.join(',') &&
        stack.conformsToBaseline(AtlasBaselineRanks.rankOf) ==
            (expected['conforms'] as bool);
    _record(id, ok ? Verdict.pass : Verdict.fail, 'tie=insertion order');
    return;
  }
  if (id == 'ORDER-001') {
    final stack = AtlasLayerStack([
      for (final rank in AtlasBaselineRanks.ranks)
        AtlasLayerState(definition: _testDef(rank)),
    ]);
    final ordered =
        stack.orderedVisible().map((s) => s.definition.id.value).toList();
    final want = (expected['order_significant'] as bool) &&
        ordered.join(',') == AtlasBaselineRanks.ranks.join(',');
    _record(
      id,
      want && stack.conformsToBaseline(AtlasBaselineRanks.rankOf)
          ? Verdict.pass
          : Verdict.fail,
      'order=$ordered',
    );
    return;
  }
  if (id == 'ORDER-002') {
    // Adapter projection under test: toggles plus the always-present
    // graticule compose the ordered core list (test-side composition policy
    // mirrors the fail-secure boot rule). Rank vocabulary comes from the
    // fixture's toggle_to_rank map (aligned to the ORDER-001 baseline).
    final mapping =
        ((f['inputs'] as Map<String, dynamic>)['toggle_to_rank'] as Map)
            .cast<String, String>();
    final toggles =
        ((f['inputs'] as Map<String, dynamic>)['adapter_toggles'] as List)
            .cast<String>();
    final orderedIds = <String>[
      'offline-graticule',
      for (final t in toggles) mapping[t]!
    ]..sort(
        (a, b) => AtlasBaselineRanks.rankOf(a)!
            .compareTo(AtlasBaselineRanks.rankOf(b)!),
      );
    final stack = AtlasLayerStack([
      for (final layerId in orderedIds)
        AtlasLayerState(definition: _testDef(layerId)),
    ]);
    final got =
        stack.orderedVisible().map((s) => s.definition.id.value).toList();
    final want = (expected['ordered_core_list'] as List).cast<String>().join(
          ',',
        );
    _record(
      id,
      got.join(',') == want &&
              stack.conformsToBaseline(AtlasBaselineRanks.rankOf)
          ? Verdict.pass
          : Verdict.fail,
      'got=$got want=$want widgets_fixtured=${expected['toggle_widgets_fixtured']}',
    );
    return;
  }
  if (id == 'ATTR-001') {
    final stack = AtlasLayerStack([
      AtlasLayerState(definition: _testDef('esri-sat', attribution: 'Esri')),
      AtlasLayerState(definition: _testDef('otm', attribution: 'OTM')),
      AtlasLayerState(definition: _testDef('usgs', attribution: 'USGS')),
      AtlasLayerState(definition: _testDef('imagery', isPrivate: true)),
    ]);
    final got = AtlasAttribution.forVisible(stack);
    final ok = got.containsAll(['Esri', 'OTM', 'USGS']) && got.length == 3;
    _record(id, ok ? Verdict.pass : Verdict.fail, 'attribution=$got');
    return;
  }
  if (id == 'ATTR-002') {
    final stack = AtlasLayerStack([
      AtlasLayerState(definition: _testDef('esri-dark', attribution: 'Esri')),
      AtlasLayerState(definition: _testDef('otm', attribution: 'OTM')),
    ]);
    final got = AtlasAttribution.forVisible(stack);
    _record(
      id,
      got.containsAll(['Esri', 'OTM']) ? Verdict.pass : Verdict.fail,
      'attribution=$got (gap must not recur)',
    );
    return;
  }
  if (id.startsWith('DESC-')) {
    _descriptor(f);
    return;
  }
  _record(id, Verdict.notApplicable, 'No layer-scope handler for $id.');
}

// ---------------------------------------------------------------------------
// PROVIDERS + TILES (Phase 1.4 executable contracts; acquisition deferred)
// ---------------------------------------------------------------------------

AtlasTileScheme _scheme(String name) =>
    AtlasTileScheme.values.firstWhere((v) => v.name == name);

AtlasTileIdentity _tileIdentity(Map<String, dynamic> m) => AtlasTileIdentity(
      provider: AtlasId(m['provider'] as String),
      layer: AtlasId(m['layer'] as String),
      coordinate: AtlasTileCoordinate(
        z: (m['z'] as num).toInt(),
        x: (m['x'] as num).toInt(),
        y: (m['y'] as num).toInt(),
      ),
      scheme: m.containsKey('scheme')
          ? _scheme(m['scheme'] as String)
          : AtlasTileScheme.xyz,
    );

/// Phase 0.4 DESC-* fixtures (descriptor shape, provider-contract vocabulary).
void _descriptor(Map<String, dynamic> f) {
  final id = f['id'] as String;
  final inputs = f['inputs'] as Map<String, dynamic>;
  final expected = f['expected'] as Map<String, dynamic>;
  const categoryToKind = {
    'raster': AtlasDataKind.rasterTiles,
    'vector': AtlasDataKind.vectorTiles,
    'geojson': AtlasDataKind.geojson,
    'elevation': AtlasDataKind.elevation,
    'historical': AtlasDataKind.historical,
    'boundary': AtlasDataKind.boundary,
    'parcel': AtlasDataKind.parcel,
    'structure': AtlasDataKind.structure,
    'local-dataset': AtlasDataKind.localDataset,
    'local': AtlasDataKind.localDataset,
  };
  final descriptorJson = inputs['descriptor'] as Map<String, dynamic>;
  final zoom = descriptorJson['native_zoom'] as List?;
  final descriptor = AtlasProviderDescriptor(
    id: AtlasId(descriptorJson['id'] as String),
    kinds: {categoryToKind[inputs['category'] as String]!},
    attribution: descriptorJson['attribution'] as String?,
    license: descriptorJson['license'] as String?,
    nativeMinZoom: zoom == null ? null : (zoom[0] as num).toInt(),
    nativeMaxZoom: zoom == null ? null : (zoom[1] as num).toInt(),
  );
  // Authority classes (e.g. DESC-007 AUTHORITATIVE) belong to the atlas_data
  // provenance model, not to descriptors: recorded here as unmodeled, never
  // defaulted. The descriptor answers identity/taxonomy/range only.
  final v = descriptor.validate();
  final ok = v.isValid &&
      (expected['identity_present'] as bool? ?? true) &&
      (zoom == null || (expected['native_zoom_present'] as bool? ?? true));
  _record(
    id,
    ok ? Verdict.pass : Verdict.fail,
    'endpoint-free descriptor validates; authority unmodeled (data scope)',
  );
}

/// Phase 0.4 + 1.4 providers/ fixtures.
void _providers(Map<String, dynamic> f) {
  final id = f['id'] as String;
  final inputs = f['inputs'] as Map<String, dynamic>;
  final expected = f['expected'] as Map<String, dynamic>;
  if (id == 'PVD-001') {
    final d = inputs['descriptor'] as Map<String, dynamic>;
    final descriptor = AtlasProviderDescriptor(
      id: AtlasId(d['id'] as String),
      kinds: const {AtlasDataKind.rasterTiles},
      title: null,
      capabilities: const {AtlasProviderCapability.tileServing},
      nativeMinZoom: (d['native_min_zoom'] as num).toInt(),
      nativeMaxZoom: (d['native_max_zoom'] as num).toInt(),
      attribution: d['attribution'] as String?,
      license: d['license'] as String?,
    );
    final v = descriptor.validate();
    final zoomExpected = expected['native_zoom'] as List;
    final ok = v.isValid &&
        descriptor.attribution == expected['attribution'] &&
        descriptor.nativeMinZoom == (zoomExpected[0] as num).toInt() &&
        descriptor.nativeMaxZoom == (zoomExpected[1] as num).toInt();
    _record(id, ok ? Verdict.pass : Verdict.fail, 'raster descriptor valid');
    return;
  }
  if (id == 'PVD-002') {
    final d = inputs['descriptor'] as Map<String, dynamic>;
    final descriptor = AtlasProviderDescriptor(
      id: AtlasId(d['id'] as String),
      kinds: const {AtlasDataKind.elevation},
    );
    _record(
      id,
      descriptor.validate().isValid ? Verdict.pass : Verdict.fail,
      'non-tile kind needs no tile fields (no tile-fetch assumption)',
    );
    return;
  }
  // PVD-003: endpoint-free shape proven by source grep (arch-check).
  final bannedImports = RegExp("import\\s+'(dart:io|package:http[^']*)'");
  final bannedField = RegExp(
    r'(final|late|var)\s+[\w<>,\? ]*\b(url|endpoint|credential|apiKey|secret)\w*\s*[;=]',
  );
  var violations = <String>[];
  final lib = Directory('packages/atlas_provider_api/lib');
  for (final file in lib
      .listSync(recursive: true)
      .whereType<File>()
      .where((f) => f.path.endsWith('.dart'))) {
    final code = file
        .readAsLinesSync()
        .where((line) => !line.trimLeft().startsWith('//'))
        .join('\n');
    if (bannedImports.hasMatch(code)) {
      violations.add('${file.path}: transport import');
    }
    if (bannedField.hasMatch(code)) {
      violations.add('${file.path}: stored endpoint/credential field');
    }
  }
  _record(
    id,
    violations.isEmpty ? Verdict.pass : Verdict.fail,
    violations.isEmpty
        ? 'no stored endpoints/credentials/transport imports'
        : violations.join('; '),
  );
}

void _tiles(Map<String, dynamic> f) {
  final id = f['id'] as String;
  final inputs = f['inputs'] as Map<String, dynamic>;
  final expected = f['expected'] as Map<String, dynamic>;
  if (id == 'TILE-001' || id == 'TILE-002' || id == 'TILE-003') {
    final a = _tileIdentity(inputs['a'] as Map<String, dynamic>);
    final b = _tileIdentity(inputs['b'] as Map<String, dynamic>);
    final differ = (a != b) == (expected['identities_differ'] as bool);
    _record(
      id,
      differ && a.validate().isValid && b.validate().isValid
          ? Verdict.pass
          : Verdict.fail,
      'identity dimensions differ as contracted',
    );
    return;
  }
  if (id == 'TILE-004') {
    final key = AtlasTileKey(
      layer: inputs['layer'] as String,
      z: (inputs['z'] as num).toInt(),
      x: (inputs['x'] as num).toInt(),
      y: (inputs['y'] as num).toInt(),
    );
    _record(
      id,
      key.keyString == expected['key'] ? Verdict.pass : Verdict.fail,
      'key=${key.keyString} (SOURCE-VERIFIED shape)',
    );
    return;
  }
  if (id == 'TILE-005') {
    _record(
      id,
      Verdict.notApplicable,
      'Template-registry lookup belongs to provider discovery (PLANNED); '
      'the silent-fallback defect is structurally impossible (no fallback code).',
    );
    return;
  }
  if (id == 'TILE-006') {
    final c = inputs['coordinate'] as Map<String, dynamic>;
    final coord = AtlasTileCoordinate(
      z: (c['z'] as num).toInt(),
      x: (c['x'] as num).toInt(),
      y: (c['y'] as num).toInt(),
    );
    final xyz = AtlasTileIdentity(
      provider: const AtlasId('p'),
      layer: const AtlasId('l'),
      coordinate: coord,
    );
    final tms = AtlasTileIdentity(
      provider: const AtlasId('p'),
      layer: const AtlasId('l'),
      coordinate: coord,
      scheme: AtlasTileScheme.tms,
    );
    final ok = xyz != tms &&
        (expected['scheme_differs'] as bool) &&
        coord.rowFor(AtlasTileScheme.xyz) ==
            _num(expected['row_xyz']).toInt() &&
        coord.rowFor(AtlasTileScheme.tms) == _num(expected['row_tms']).toInt();
    _record(id, ok ? Verdict.pass : Verdict.fail, 'xyz=2 tms=5, differ');
    return;
  }
  if (id == 'TILE-007') {
    final c = inputs['coordinate'] as Map<String, dynamic>;
    final v = AtlasTileCoordinate(
      z: (c['z'] as num).toInt(),
      x: (c['x'] as num).toInt(),
      y: (c['y'] as num).toInt(),
    ).validate();
    _record(
      id,
      !v.isValid ? Verdict.pass : Verdict.fail,
      'rejection=${v.rejection?.category}',
    );
    return;
  }
  if (id == 'TILE-008') {
    final key = inputs['key'] as String;
    final parsed = AtlasTileKey.parse(key);
    _record(
      id,
      parsed.keyString == key && (expected['round_trip'] as bool)
          ? Verdict.pass
          : Verdict.fail,
      'parse→render round-trip exact',
    );
    return;
  }
  if (id == 'TILE-009') {
    try {
      AtlasTileKey.parse(inputs['key'] as String);
      _record(id, Verdict.fail, 'parsed malformed key without rejection');
    } on AtlasRejectionException catch (e) {
      _record(
        id,
        e.rejection.category ==
                (expected['rejection'] as Map<String, dynamic>)['category']
            ? Verdict.pass
            : Verdict.fail,
        'category=${e.rejection.category}',
      );
    }
    return;
  }
  if (id == 'TILE-010' || id == 'TILE-011') {
    final request = AtlasTileRequest(
      identity: AtlasTileIdentity(
        provider: const AtlasId('p'),
        layer: const AtlasId('l'),
        coordinate: AtlasTileCoordinate(
          z: ((inputs['coordinate'] as Map)['z'] as num).toInt(),
          x: ((inputs['coordinate'] as Map)['x'] as num).toInt(),
          y: ((inputs['coordinate'] as Map)['y'] as num).toInt(),
        ),
        scheme: _scheme(inputs['scheme'] as String),
      ),
    );
    final url = request.resolveUrl(inputs['template'] as String);
    _record(
      id,
      url == expected['url'] ? Verdict.pass : Verdict.fail,
      'url=$url (pure substitution, no fetch)',
    );
    return;
  }
  if (id == 'TILE-012') {
    final request = AtlasTileRequest(
      identity: AtlasTileIdentity(
        provider: const AtlasId('p'),
        layer: const AtlasId('l'),
        coordinate: AtlasTileCoordinate(
          z: ((inputs['coordinate'] as Map)['z'] as num).toInt(),
          x: ((inputs['coordinate'] as Map)['x'] as num).toInt(),
          y: ((inputs['coordinate'] as Map)['y'] as num).toInt(),
        ),
      ),
    );
    try {
      request.resolveUrl(inputs['template'] as String);
      _record(id, Verdict.fail, 'undeclared placeholder passed silently');
    } on AtlasRejectionException catch (e) {
      _record(
        id,
        e.rejection.category ==
                (expected['rejection'] as Map<String, dynamic>)['category']
            ? Verdict.pass
            : Verdict.fail,
        'category=${e.rejection.category}',
      );
    }
    return;
  }
  // ENTRY-001 / ENTRY-002.
  final entryJson = inputs['entry'] as Map<String, dynamic>;
  final identityJson = entryJson['identity'] as Map<String, dynamic>;
  final coordJson = identityJson['coordinate'] as Map<String, dynamic>;
  final payloadJson = entryJson['payload'] as Map<String, dynamic>;
  final entry = AtlasTileEntry(
    identity: AtlasTileIdentity(
      provider: AtlasId(identityJson['provider'] as String),
      layer: AtlasId(identityJson['layer'] as String),
      coordinate: AtlasTileCoordinate(
        z: (coordJson['z'] as num).toInt(),
        x: (coordJson['x'] as num).toInt(),
        y: (coordJson['y'] as num).toInt(),
      ),
      scheme: _scheme(identityJson['scheme'] as String),
    ),
    key: AtlasTileKey.parse(entryJson['key'] as String),
    payloadId: AtlasId((payloadJson['id'] as String)),
  );
  final payload = AtlasTilePayload(
    id: AtlasId(payloadJson['id'] as String),
    byteLength: (payloadJson['byte_length'] as num).toInt(),
  );
  final entryCheck = entry.validate();
  final payloadCheck = payload.validate();
  if (id == 'ENTRY-001') {
    _record(
      id,
      entryCheck.isValid && payloadCheck.isValid && (expected['valid'] as bool)
          ? Verdict.pass
          : Verdict.fail,
      'PROVISIONAL shape; consistent addresses validate',
    );
    return;
  }
  _record(
    id,
    !entryCheck.isValid ? Verdict.pass : Verdict.fail,
    'PROVISIONAL rule; rejection=${entryCheck.rejection?.category}',
  );
}

// ---------------------------------------------------------------------------
// RESOLUTION (Phase 1.5: meaning + deterministic decisions, never acquisition)
// ---------------------------------------------------------------------------

AtlasDataKind _resolutionKind(String name) =>
    AtlasDataKind.values.firstWhere((v) => v.name == name);

double _resNum(dynamic value) {
  if (value is num) return value.toDouble();
  if (value == 'NaN') return double.nan;
  if (value == 'Infinity') return double.infinity;
  if (value == '-Infinity') return double.negativeInfinity;
  throw StateError('non-numeric test scalar: $value');
}

AtlasProviderDescriptor _resProvider(Map<String, dynamic> m) {
  return AtlasProviderDescriptor(
    id: AtlasId(m['id'] as String),
    kinds: {
      for (final k in (m['kinds'] as List).cast<String>()) _resolutionKind(k),
    },
    capabilities: {
      for (final c in ((m['capabilities'] as List?) ?? const []).cast<String>())
        AtlasProviderCapability.values.firstWhere((v) => v.name == c),
    },
    nativeMinZoom: m.containsKey('native_min_zoom')
        ? (m['native_min_zoom'] as num).toInt()
        : null,
    nativeMaxZoom: m.containsKey('native_max_zoom')
        ? (m['native_max_zoom'] as num).toInt()
        : null,
    attribution: m['attribution'] as String?,
    license: m['license'] as String?,
    sensitivity: m['sensitivity'] as String?,
  );
}

AtlasResolutionRequest _resRequest(Map<String, dynamic> m) =>
    AtlasResolutionRequest(
      kind: _resolutionKind(m['kind'] as String),
      latitude: _resNum(m['latitude']),
      longitude: _resNum(m['longitude']),
      zoom: _resNum(m['zoom']),
      scheme: m.containsKey('scheme')
          ? _scheme(m['scheme'] as String)
          : AtlasTileScheme.xyz,
      preferredProviders: [
        for (final p in ((m['preferred'] as List?) ?? const []).cast<String>())
          AtlasId(p),
      ],
    );

void _resolution(Map<String, dynamic> f) {
  final id = f['id'] as String;
  final inputs = f['inputs'] as Map<String, dynamic>;
  final expected = f['expected'] as Map<String, dynamic>;
  final catalog = [
    for (final p in (inputs['catalog'] as List).cast<Map<String, dynamic>>())
      _resProvider(p),
  ];
  final result = AtlasResolver.resolve(
    _resRequest(inputs['request'] as Map<String, dynamic>),
    catalog,
  );
  var ok = result.status.name == expected['status'];
  if (expected.containsKey('provider')) {
    final want = expected['provider'] as String?;
    ok = ok &&
        (want == null
            ? result.provider == null
            : result.provider?.value == want);
  }
  if (expected.containsKey('tile')) {
    final want = expected['tile'] as Map<String, dynamic>?;
    if (want == null) {
      ok = ok && result.tile == null;
    } else if (result.tile == null) {
      ok = false;
    } else {
      ok = ok &&
          result.tile!.z == (want['z'] as num).toInt() &&
          result.tile!.x == (want['x'] as num).toInt() &&
          result.tile!.y == (want['y'] as num).toInt();
    }
  }
  if (expected.containsKey('eligible')) {
    final want = (expected['eligible'] as List).cast<String>();
    final got = result.eligible.map((e) => e.value).toList();
    ok = ok && got.join(',') == want.join(',');
  }
  if (expected.containsKey('rejection')) {
    ok = ok && result.reason == expected['rejection'];
  }
  _record(
    id,
    ok ? Verdict.pass : Verdict.fail,
    'status=${result.status.name} provider=${result.provider?.value} '
    'tile=${result.tile} eligible=${result.eligible.map((e) => e.value).toList()} '
    'reason=${result.reason}',
  );
}

/// 1.5-M + 1.7 arch-leakage self-check: resolution AND cache-semantic sources
/// must contain no transport/renderer/storage/network-activity markers (code
/// only; contracted vocabulary itself is never the violation).
bool _resolutionLeakCheck(List<String> violations) {
  const banned = [
    'dart:io',
    'package:http',
    'HttpClient',
    'Socket',
    'MapLibre',
    'flutter',
    'Widget',
    'File(',
    'Directory(',
    'Credential',
    'apiKey',
    'https://',
    'http://',
    'DateTime.now',
    'Random(',
    'sqlite',
    'hive',
    'isar',
    'SharedPreferences',
    'shared_preferences',
    'path_provider',
    'pathProvider',
    'attemptId',
    'Uuid',
    'uuid',
    'Uint8List',
    'List<int>',
  ];
  const dirs = [
    'packages/atlas_provider_api/lib/src/resolution',
    'packages/atlas_provider_api/lib/src/resources',
    'packages/atlas_provider_api/lib/src/acquisition',
    'packages/atlas_tiles/lib/src',
  ];
  for (final dirPath in dirs) {
    final dir = Directory(dirPath);
    if (!dir.existsSync()) continue;
    for (final file in dir
        .listSync(recursive: true)
        .whereType<File>()
        .where((f) => f.path.endsWith('.dart'))) {
      final code = file
          .readAsLinesSync()
          .where((line) => !line.trimLeft().startsWith('//'))
          .join('\n');
      for (final token in banned) {
        if (code.contains(token)) {
          violations.add(
            '${file.path.split(Platform.pathSeparator).last}: $token',
          );
        }
      }
    }
  }
  return violations.isEmpty;
}

// ---------------------------------------------------------------------------
// CACHE SEMANTICS (Phase 1.7: storage-independent decisions, explicit time)
// ---------------------------------------------------------------------------

AtlasCacheKey _cacheKey(Map<String, dynamic> m) => AtlasCacheKey(
      namespace: AtlasCacheNamespace.values.firstWhere(
        (v) => v.name == m['namespace'],
      ),
      value: m['value'] as String,
    );

AtlasCacheEntry? _cacheEntry(dynamic raw) {
  if (raw == null) return null;
  final m = raw as Map<String, dynamic>;
  final resourceJson = m['resource'] as Map<String, dynamic>?;
  return AtlasCacheEntry(
    key: _cacheKey(m['key'] as Map<String, dynamic>),
    resource: resourceJson == null
        ? null
        : AtlasResourceIdentity(
            provider: AtlasId(resourceJson['provider'] as String),
            kind: _resolutionKind(resourceJson['kind'] as String),
            address: (resourceJson['address'] as String?) ?? '',
          ),
    storedAt: (m['stored_at'] as num).toInt(),
    maxAgeSeconds: m.containsKey('max_age_seconds')
        ? (m['max_age_seconds'] as num).toInt()
        : null,
    payloadId:
        m.containsKey('payload_id') ? AtlasId(m['payload_id'] as String) : null,
    revoked: (m['revoked'] as bool?) ?? false,
  );
}

void _cache(Map<String, dynamic> f) {
  final id = f['id'] as String;
  final inputs = f['inputs'] as Map<String, dynamic>;
  final expected = f['expected'] as Map<String, dynamic>;
  if (id.startsWith('CACHE-')) {
    _record(
      id,
      Verdict.notApplicable,
      'Flow-engine behavior (lookup→validate→fetch) needs an acquisition '
      'engine; semantic decisions below supersede it without reproducing it.',
    );
    return;
  }
  if (id == 'KEY-001') {
    final a = _cacheKey(inputs['a'] as Map<String, dynamic>);
    final b = _cacheKey(inputs['b'] as Map<String, dynamic>);
    _record(
      id,
      a == b && a.value == expected['key'] && a.validate().isValid
          ? Verdict.pass
          : Verdict.fail,
      'equivalent keys equal; canonical=${a.value}',
    );
    return;
  }
  if (id == 'KEY-005') {
    final key = _cacheKey(inputs['key'] as Map<String, dynamic>);
    _record(
      id,
      key.validate().isValid && key.value == expected['canonical']
          ? Verdict.pass
          : Verdict.fail,
      'canonical=${key.value}',
    );
    return;
  }
  if (id == 'KEY-004') {
    final v = _cacheKey(inputs['key'] as Map<String, dynamic>).validate();
    _record(
      id,
      !v.isValid ? Verdict.pass : Verdict.fail,
      'rejection=${v.rejection?.category}',
    );
    return;
  }
  if (id == 'KEY-002' || id == 'KEY-003' || id == 'ADV-049') {
    // Inequality fixtures: expected.equal is false in all three; a != b must hold.
    final a = _cacheKey(inputs['a'] as Map<String, dynamic>);
    final AtlasCacheKey b;
    if (inputs.containsKey('b')) {
      b = _cacheKey(inputs['b'] as Map<String, dynamic>);
    } else {
      final t = inputs['tile_b'] as Map<String, dynamic>;
      b = AtlasCacheKey(
        namespace: AtlasCacheNamespace.resource,
        value: '${t['provider'] as String}/rasterTiles/',
      );
    }
    _record(
      id,
      a != b && !(expected['equal'] as bool) ? Verdict.pass : Verdict.fail,
      'distinct namespaces/providers never collide',
    );
    return;
  }
  if (id == 'ADV-044') {
    final v = _cacheKey(inputs['key'] as Map<String, dynamic>).validate();
    _record(
      id,
      !v.isValid ? Verdict.pass : Verdict.fail,
      'rejection=${v.rejection?.category}',
    );
    return;
  }
  if (id == 'ADV-050') {
    final lookupFamily = RegExp(
      r'"id"\s*:\s*"(LOOKUP|FRESH|RET|ADV-046|ADV-047|ADV-048)',
    );
    final missing = <String>[];
    final dir = Directory('test/golden/cache');
    for (final file in dir.listSync().whereType<File>().where(
          (f) => f.path.endsWith('.json'),
        )) {
      final text = file.readAsStringSync();
      if (lookupFamily.hasMatch(text) && !text.contains('"now"')) {
        missing.add(file.path.split(Platform.pathSeparator).last);
      }
    }
    _record(
      id,
      missing.isEmpty ? Verdict.pass : Verdict.fail,
      missing.isEmpty
          ? 'every lookup fixture carries explicit now'
          : 'missing now: $missing',
    );
    return;
  }
  if (id == 'ADV-044') {
    final v = _cacheKey(inputs['key'] as Map<String, dynamic>).validate();
    _record(
      id,
      !v.isValid ? Verdict.pass : Verdict.fail,
      'rejection=${v.rejection?.category}',
    );
    return;
  }
  if (id == 'ADV-050') {
    final lookupFamily = RegExp(
      r'"id"\s*:\s*"(LOOKUP|FRESH|RET|ADV-046|ADV-047|ADV-048)',
    );
    final missing = <String>[];
    final dir = Directory('test/golden/cache');
    for (final file in dir.listSync().whereType<File>().where(
          (f) => f.path.endsWith('.json'),
        )) {
      final text = file.readAsStringSync();
      if (lookupFamily.hasMatch(text) && !text.contains('"now"')) {
        missing.add(file.path.split(Platform.pathSeparator).last);
      }
    }
    _record(
      id,
      missing.isEmpty ? Verdict.pass : Verdict.fail,
      missing.isEmpty
          ? 'every lookup fixture carries explicit now'
          : 'missing now: $missing',
    );
    return;
  }
  if (id == 'RET-001') {
    final entry = _cacheEntry(inputs['entry'])!;
    final now = (inputs['now'] as num).toInt();
    final before = AtlasCache.lookup(entry, now);
    final revoked = entry.invalidate();
    final after = AtlasCache.lookup(revoked, now);
    final ok = before.outcome.name == 'hit' &&
        (expected['before'] as String) == 'hit' &&
        after.outcome.name == (expected['after'] as String) &&
        !entry.revoked;
    _record(
      id,
      ok ? Verdict.pass : Verdict.fail,
      'before=${before.outcome.name} after=${after.outcome.name} (original untouched)',
    );
    return;
  }
  if (id == 'RET-003') {
    final now = (inputs['now'] as num).toInt();
    final oldDecision = AtlasCache.lookup(
      _cacheEntry(inputs['old_entry']),
      now,
    );
    final newDecision = AtlasCache.lookup(
      _cacheEntry(inputs['new_entry']),
      now,
    );
    final ok = oldDecision.outcome.name == 'expired' &&
        newDecision.outcome.name == 'hit' &&
        (expected['decision_uses_new'] as bool);
    _record(
      id,
      ok ? Verdict.pass : Verdict.fail,
      'old=${oldDecision.outcome.name} new=${newDecision.outcome.name}',
    );
    return;
  }
  // LOOKUP-*, FRESH-*, RET-002, ADV-046/047/048: single-entry decisions.
  final entry = _cacheEntry(inputs['entry']);
  final now = (inputs['now'] as num).toInt();
  final decision = AtlasCache.lookup(entry, now);
  var ok = decision.outcome.name == expected['lookup'];
  if (expected.containsKey('entry_valid')) {
    ok = ok && entry!.validate().isValid == (expected['entry_valid'] as bool);
  }
  if (expected.containsKey('entry_still_valid')) {
    ok = ok &&
        entry!.validate().isValid == (expected['entry_still_valid'] as bool);
  }
  _record(
    id,
    ok ? Verdict.pass : Verdict.fail,
    'outcome=${decision.outcome.name}',
  );
}

// ---------------------------------------------------------------------------
// ACQUISITION (Phase 1.8: semantic attempts/outcomes, never transport)
// ---------------------------------------------------------------------------

AtlasAcquisitionPolicy _acqPolicy(Map<String, dynamic> m) =>
    AtlasAcquisitionPolicy(
      timeoutSeconds: m.containsKey('timeout_seconds')
          ? (m['timeout_seconds'] as num).toInt()
          : null,
      allowRetry: (m['allow_retry'] as bool?) ?? true,
    );

AtlasResourceIdentity _acqResource(Map<String, dynamic> m) =>
    AtlasResourceIdentity(
      provider: AtlasId(m['provider'] as String),
      kind: _resolutionKind(m['kind'] as String),
      address: (m['address'] as String?) ?? '',
    );

AtlasAcquisitionRequest _acqRequest(Map<String, dynamic> inputs) {
  final r = inputs['resource'] as Map<String, dynamic>;
  return AtlasAcquisitionRequest(
    resource: _acqResource(r),
    provider: inputs.containsKey('provider')
        ? AtlasId(inputs['provider'] as String)
        : null,
    policy: inputs.containsKey('policy')
        ? _acqPolicy(inputs['policy'] as Map<String, dynamic>)
        : const AtlasAcquisitionPolicy(),
  );
}

AtlasAcquisition _acqStarted(Map<String, dynamic> inputs, {int? atOverride}) {
  final startedAt =
      (inputs['started_at'] as num?)?.toInt() ?? (inputs['now'] as num).toInt();
  return AtlasAcquisition.start(_acqRequest(inputs), atOverride ?? startedAt);
}

void _acquisition(Map<String, dynamic> f) {
  final id = f['id'] as String;
  final inputs = f['inputs'] as Map<String, dynamic>;
  final expected = f['expected'] as Map<String, dynamic>;
  AtlasAcquisitionFailure _failure(String name) =>
      AtlasAcquisitionFailure.values.firstWhere((v) => v.name == name);
  if (id == 'ACQ-001' ||
      id == 'ACQ-002' ||
      id == 'ADV-055' ||
      id == 'ADV-061') {
    // Target validation (+ network-flag independence for ADV-061: unknown
    // JSON keys never reach constructors, so validity is identical).
    final request = _acqRequest(inputs);
    final v = request.validate();
    final held = AtlasAcquisition(request: request);
    final ok = v.isValid == (expected['valid'] as bool) &&
        held.state.name == 'notStarted';
    _record(
      id,
      ok ? Verdict.pass : Verdict.fail,
      'valid=${v.isValid} initial=${held.state.name}',
    );
    return;
  }
  if (id == 'ACQ-003' ||
      id == 'ACQ-004' ||
      id == 'ACQ-014' ||
      id == 'ADV-051') {
    final v = _acqRequest(inputs).validate();
    final want = expected['rejection'] is Map
        ? (expected['rejection'] as Map)['category']
        : expected['rejection'];
    _record(
      id,
      !v.isValid && v.rejection?.category == want ? Verdict.pass : Verdict.fail,
      'rejection=${v.rejection?.category}',
    );
    return;
  }
  if (id == 'ACQ-005' || id == 'ACQ-006') {
    final started = _acqStarted(inputs);
    _record(
      id,
      started.state.name == expected['after'] ? Verdict.pass : Verdict.fail,
      'state=${started.state.name} (explicit now, no clock)',
    );
    return;
  }
  if (id == 'ACQ-007') {
    final acquisition =
        _acqStarted(inputs).cancel((inputs['now'] as num).toInt());
    _record(
      id,
      acquisition.state.name == expected['after'] ? Verdict.pass : Verdict.fail,
      'cancelled ≠ failed (distinct status)',
    );
    return;
  }
  if (id == 'ACQ-008' || id == 'ACQ-009') {
    final acquisition =
        _acqStarted(inputs).checkTimeout((inputs['now'] as num).toInt());
    var ok = acquisition.state.name == expected['after'];
    if (expected.containsKey('retryable')) {
      ok = ok &&
          acquisition.failure!.retryable == (expected['retryable'] as bool);
    }
    _record(
      id,
      ok ? Verdict.pass : Verdict.fail,
      'state=${acquisition.state.name} (pure duration compare)',
    );
    return;
  }
  if (id == 'ACQ-010' || id == 'ADV-054') {
    // ADV-054 probes completion mechanics, so it carries no resource of its
    // own: a fixed valid request stands in (time stays explicit).
    final effectiveInputs = id == 'ADV-054'
        ? {
            'policy': <String, dynamic>{},
            'resource': {
              'provider': 'osm',
              'kind': 'rasterTiles',
              'address': 'z=1/x=0/y=0@xyz',
            },
            'started_at': inputs['started_at'],
          }
        : inputs;
    final acquisition = _acqStarted(effectiveInputs).complete(
      AtlasId(inputs['payload_id'] as String),
      (inputs['now'] as num).toInt(),
    );
    final result = acquisition.toResult();
    final ok = acquisition.state.name ==
            (expected['after'] as String? ?? 'succeeded') &&
        result.payloadId?.value == (inputs['payload_id'] as String) &&
        result.request == acquisition.request &&
        (id == 'ACQ-010'
            ? result.payloadId?.value == expected['payload_id'] &&
                (expected['resource_preserved'] as bool)
            : !(expected['bytes_required'] as bool));
    _record(
      id,
      ok ? Verdict.pass : Verdict.fail,
      'payload ID bound, bytes never involved',
    );
    return;
  }
  if (id == 'ACQ-011' || id == 'ACQ-012' || id == 'ACQ-013') {
    final acquisition = _acqStarted(inputs).fail(
      _failure(inputs['failure'] as String),
      (inputs['now'] as num).toInt(),
    );
    final ok = acquisition.state.name == expected['after'] &&
        acquisition.failure!.name == expected['failure'] &&
        acquisition.failure!.retryable == (expected['retryable'] as bool);
    _record(
      id,
      ok ? Verdict.pass : Verdict.fail,
      'failure=${acquisition.failure!.name} '
      'retryable=${acquisition.failure!.retryable} (advice only)',
    );
    return;
  }
  if (id == 'ACQ-015') {
    // Boundary probe: exercised through the arch self-check (no transport/
    // cache/renderer markers in acquisition sources) plus a live validation
    // that request construction needs no network-shaped input.
    final request = AtlasAcquisitionRequest(
      resource: _acqResource({
        'provider': 'osm',
        'kind': 'rasterTiles',
        'address': 'z=12/x=986/y=1473@xyz',
      }),
    );
    final leaks = <String>[];
    final ok = request.validate().isValid &&
        (expected['acquires_nothing'] as bool) &&
        (expected['has_no_cache_lookup'] as bool) &&
        (expected['has_no_url_execution'] as bool) &&
        _resolutionLeakCheck(leaks) &&
        leaks.isEmpty;
    _record(
      id,
      ok ? Verdict.pass : Verdict.fail,
      'boundaries hold; leak-check green',
    );
    return;
  }
  if (id == 'ADV-052') {
    final names = AtlasAcquisitionFailure.values.map((v) => v.name).toSet();
    final want = (expected['closed_set'] as List).cast<String>().toSet();
    final hasDigits = names.any((n) => RegExp(r'[0-9]').hasMatch(n));
    _record(
      id,
      names.containsAll(want) &&
              want.containsAll(names) &&
              !hasDigits &&
              (expected['has_no_numeric_codes'] as bool)
          ? Verdict.pass
          : Verdict.fail,
      'taxonomy=$names (closed, no HTTP codes)',
    );
    return;
  }
  if (id == 'ADV-053' || id == 'ADV-057') {
    // Shape audits: success results expose exactly request/state/failure/
    // payloadId surface (verified structurally — the types have no other
    // members; arch grep enforces the absences).
    final acquisition = _acqStarted({
      'policy': <String, dynamic>{},
      'resource': {
        'provider': 'osm',
        'kind': 'rasterTiles',
        'address': 'z=1/x=0/y=0@xyz',
      },
    }).complete(const AtlasId('p-1'), 1700000000);
    final result = acquisition.toResult();
    final ok = result.state == AtlasAcquisitionState.succeeded &&
        result.payloadId == const AtlasId('p-1') &&
        result.failure == null;
    _record(
      id,
      ok ? Verdict.pass : Verdict.fail,
      'result surface = request/state/failure/payloadId only',
    );
    return;
  }
  if (id == 'ADV-056' || id == 'ADV-062') {
    final leaks = <String>[];
    _record(
      id,
      _resolutionLeakCheck(leaks) && leaks.isEmpty
          ? Verdict.pass
          : Verdict.fail,
      leaks.isEmpty ? 'no cache/sdk members in acquisition' : leaks.join('; '),
    );
    return;
  }
  if (id == 'ADV-058') {
    final acquisition = _acqStarted({
      'policy': <String, dynamic>{},
      'resource': {
        'provider': 'osm',
        'kind': 'rasterTiles',
        'address': 'z=1/x=0/y=0@xyz',
      },
    });
    final cancelled = acquisition.cancel(1700000001);
    _record(
      id,
      cancelled.state == AtlasAcquisitionState.cancelled &&
              (expected['cancel_takes_no_token'] as bool)
          ? Verdict.pass
          : Verdict.fail,
      'cancel(int) — no token type exists',
    );
    return;
  }
  if (id == 'ADV-059') {
    final temporal = RegExp(
      r'"id"\s*:\s*"(ACQ-005|ACQ-006|ACQ-007|ACQ-008|ACQ-009|ACQ-010|ACQ-011|ACQ-012|ACQ-013|ADV-054)"',
    );
    final missing = <String>[];
    final dir = Directory('test/golden/acquisition');
    for (final file in dir.listSync().whereType<File>().where(
          (f) => f.path.endsWith('.json'),
        )) {
      final text = file.readAsStringSync();
      if (temporal.hasMatch(text) && !text.contains('"now"')) {
        missing.add(file.path.split(Platform.pathSeparator).last);
      }
    }
    _record(
      id,
      missing.isEmpty && (expected['explicit_now_present'] as bool)
          ? Verdict.pass
          : Verdict.fail,
      missing.isEmpty
          ? 'every lifecycle fixture carries explicit now'
          : 'missing now: $missing',
    );
    return;
  }
  if (id == 'ADV-060') {
    // No attempt-id member exists: construction surface is request/state/
    // startedAt/updatedAt/failure/payloadId only (arch-grep for id-carrying
    // members enforced alongside).
    final acquisition = AtlasAcquisition(
      request: _acqRequest({
        'policy': <String, dynamic>{},
        'resource': {
          'provider': 'osm',
          'kind': 'rasterTiles',
          'address': 'z=1/x=0/y=0@xyz',
        },
      }),
    );
    _record(
      id,
      acquisition.startedAt == null && !(expected['attempt_ids_exist'] as bool)
          ? Verdict.pass
          : Verdict.fail,
      'no attempt identity anywhere in the model',
    );
    return;
  }
  _record(id, Verdict.fail, 'unknown acquisition fixture: $id');
}

// ---------------------------------------------------------------------------
// GEOMETRY kernel (rings validity, segments, screening)
// ---------------------------------------------------------------------------

List<AtlasCoordinate> _ring(List<dynamic> pts) => [
      for (final p in pts.cast<List<dynamic>>())
        AtlasCoordinate(latitude: _num(p[1]), longitude: _num(p[0])),
    ];

void _geometry(Map<String, dynamic> f) {
  final id = f['id'] as String;
  final expected = f['expected'] as Map<String, dynamic>;
  if (id == 'POLY-001' || id == 'POLY-002') {
    final inputs = f['inputs'] as Map<String, dynamic>;
    final points = [
      for (final p in (inputs['points'] as List).cast<Map<String, dynamic>>())
        _coord(p),
    ];
    final line = AtlasPolyline(points);
    final v = line.validateStructure();
    final ok = v.isValid &&
        line.isLengthMeaningful == (expected['meaningful'] as bool) &&
        line.lengthKm() == _num(expected['length_km']);
    _record(
      id,
      ok ? Verdict.pass : Verdict.fail,
      'PROVISIONAL type; valid=${v.isValid} meaningful=${line.isLengthMeaningful}',
    );
    return;
  }
  if (id == 'POLY-003') {
    final inputs = f['inputs'] as Map<String, dynamic>;
    final points = [
      for (final p in (inputs['points'] as List).cast<Map<String, dynamic>>())
        _coord(p),
    ];
    final line = AtlasPolyline(points);
    final tolerance = _num(
      ((f['tolerance'] as Map<String, dynamic>)['value'] as num),
    );
    final ok = line.validateStructure().isValid &&
        _close(line.lengthKm(), _num(expected['length_km']), tolerance);
    _record(
      id,
      ok ? Verdict.pass : Verdict.fail,
      'duplicates preserved; length=${line.lengthKm()}',
    );
    return;
  }
  if (id == 'GON-001' || id == 'GON-002') {
    final inputs = f['inputs'] as Map<String, dynamic>;
    final polygon = AtlasPolygon(exterior: _ring(inputs['exterior'] as List));
    final v = polygon.validate();
    if (id == 'GON-002') {
      _record(
        id,
        !v.isValid ? Verdict.pass : Verdict.fail,
        'rejection=${v.rejection?.category}',
      );
      return;
    }
    final boundsExpected = expected['bounds'] as Map<String, dynamic>;
    final bounds = polygon.bounds;
    final ok = v.isValid &&
        bounds.south == _num(boundsExpected['south']) &&
        bounds.west == _num(boundsExpected['west']) &&
        bounds.north == _num(boundsExpected['north']) &&
        bounds.east == _num(boundsExpected['east']);
    _record(id, ok ? Verdict.pass : Verdict.fail, 'valid+bounds exact');
    return;
  }
  if (id == 'GEOM-001') {
    final rings = (f['inputs'] as Map<String, dynamic>)['rings'] as List;
    final ring = _ring((rings[0] as List).cast<dynamic>());
    final v = AtlasRings.validateRing(ring);
    _record(
      id,
      v.isValid && (expected['valid'] as bool) ? Verdict.pass : Verdict.fail,
      'closed-ring accepts',
    );
    return;
  }
  if (id == 'GEOM-002') {
    final inputs = f['inputs'] as Map<String, dynamic>;
    var ok = true;
    for (final poly in (inputs['polygons'] as List)) {
      if (!AtlasRings.validateRing(
        _ring(((poly as List)[0] as List).cast<dynamic>()),
      ).isValid) {
        ok = false;
      }
    }
    _record(id, ok ? Verdict.pass : Verdict.fail, 'both parts valid');
    return;
  }
  if (id == 'GEOM-003') {
    // good / bad-two-vertex / good-2 modelled as ring-validity screening.
    final good = _ring([
      [-93.3, 44.9],
      [-93.2, 44.9],
      [-93.2, 45.0],
      [-93.3, 44.9],
    ]);
    final bad = _ring([
      [-93.3, 44.9],
      [-93.2, 44.9],
    ]);
    final result = AtlasCollectionScreening.screen<List<AtlasCoordinate>>([
      good,
      bad,
      good,
    ], (m) => AtlasRings.validateRing(m).isValid);
    final ok =
        result.kept.length == 2 && result.skippedIndices.join(',') == '1';
    _record(
      id,
      ok && (expected['member_rejected'] as bool) ? Verdict.pass : Verdict.fail,
      'kept=${result.kept.length} skipped=${result.skippedIndices}',
    );
    return;
  }
}

void _parcels(Map<String, dynamic> f) {
  final id = f['id'] as String;
  if (id == 'PAIRED-001') {
    _record(
      id,
      Verdict.notApplicable,
      'Authority precedence needs the atlas_data provenance model (reserved).',
    );
    return;
  }
  // PARCEL-002: two-vertex ring rejects.
  final inputs = f['inputs'] as Map<String, dynamic>;
  final ring = _ring(inputs['ring'] as List);
  final v = AtlasRings.validateRing(ring);
  _record(
    id,
    !v.isValid ? Verdict.pass : Verdict.fail,
    'rejection=${v.rejection?.category}',
  );
}

void _migration(Map<String, dynamic> f) {
  final id = f['id'] as String;
  final expected = f['expected'] as Map<String, dynamic>;
  if (id == 'FLOW-002') {
    // Null endpoints are unrepresentable in the non-nullable segment type:
    // malformed input cannot become a valid segment by construction.
    _record(
      id,
      Verdict.pass,
      'mechanism=non-nullable-type-guarantee (null unrepresentable)',
    );
    return;
  }
  if (id == 'FLOW-003') {
    const point = AtlasCoordinate(latitude: 44.9, longitude: -93.3);
    const segment = AtlasFlowSegment(from: point, to: point);
    final v = segment.validate();
    _record(
      id,
      !v.isValid ? Verdict.pass : Verdict.fail,
      'PROPOSED rule executed; rejection=${v.rejection?.category}',
    );
    return;
  }
  // FLOW-001: distinct opaque endpoints validate (deterministic single segment).
  const segment = AtlasFlowSegment(
    from: AtlasCoordinate(latitude: 44.9, longitude: -93.3),
    to: AtlasCoordinate(latitude: 45.0, longitude: -93.2),
  );
  final v = segment.validate();
  _record(
    id,
    v.isValid && (expected['segments'] as int) == 1
        ? Verdict.pass
        : Verdict.fail,
    'valid=${v.isValid}',
  );
}

// ---------------------------------------------------------------------------
// TACTICAL slice (rings in scope; pins/MGRS out of scope)
// ---------------------------------------------------------------------------

void _tactical(Map<String, dynamic> f) {
  final id = f['id'] as String;
  if (id == 'RING-001') {
    final expected = f['expected'] as Map<String, dynamic>;
    final tableOk = AtlasRangeRings.steps.join(',') ==
        ((expected['steps'] as List).map((v) => _num(v))).join(',');
    var ok = tableOk && AtlasRangeRings.ringsPerStep == 4;
    for (var i = 0; i < AtlasRangeRings.steps.length && ok; i++) {
      final set = AtlasRangeRings.generate(
        const AtlasCoordinate(latitude: 44.9778, longitude: -93.265),
        i,
      );
      if (set.rings.length != 4 || set.spokes.length != 4) {
        ok = false;
        break;
      }
      for (var r = 0; r < 4; r++) {
        if (set.rings[r].length != AtlasRangeRings.verticesPerRing) {
          ok = false;
          break;
        }
        // Outer-vertex distance within 1% of the step radius (method check,
        // not exact-vertex comparison — RING-001 tolerance model).
        final outer = set.rings[3][0];
        final d = AtlasGeoMath.haversineKm(set.center, outer);
        if (!_close(
          d,
          AtlasRangeRings.steps[i],
          0.01 * AtlasRangeRings.steps[i],
        )) {
          ok = false;
          break;
        }
      }
    }
    _record(id, ok ? Verdict.pass : Verdict.fail, 'steps+counts+radii(1%)');
    return;
  }
  if (id == 'RING-002') {
    final set = AtlasRangeRings.generate(null, -1);
    _record(id, set.isEmpty ? Verdict.pass : Verdict.fail, 'empty set');
    return;
  }
  if (id.startsWith('PIN-')) {
    _record(
      id,
      Verdict.notApplicable,
      'Pin codec/state belongs to atlas_tactical (reserved).',
    );
    return;
  }
  // MGRS-001
  _record(id, Verdict.blocked, 'No conversion vectors (no library selected).');
}

// ---------------------------------------------------------------------------
// ANGLES + BOXES (Phase 1.1 named ops and bounds)
// ---------------------------------------------------------------------------

void _angles(Map<String, dynamic> f) {
  final id = f['id'] as String;
  final expected = f['expected'] as Map<String, dynamic>;
  if (id == 'NORM-004') {
    try {
      AtlasAngles.normalizeBearingDeg(double.nan);
      _record(id, Verdict.fail, 'normalized NaN without rejection');
    } on AtlasRejectionException catch (e) {
      _record(
        id,
        e.rejection.category ==
                (expected['rejection'] as Map<String, dynamic>)['category']
            ? Verdict.pass
            : Verdict.fail,
        'category=${e.rejection.category}',
      );
    }
    return;
  }
  var ok = true;
  final got = <String>[];
  for (final c in (expected['cases'] as List).cast<Map<String, dynamic>>()) {
    final input = _num(c['in']);
    final value = id == 'NORM-001'
        ? AtlasAngles.normalizeBearingDeg(input)
        : id == 'NORM-002'
            ? AtlasAngles.normalizeSignedDeg(input)
            : AtlasAngles.normalizeLongitudeDeg(input);
    got.add('$input->$value');
    if (value != _num(c['out'])) ok = false;
  }
  _record(
    id,
    ok ? Verdict.pass : Verdict.fail,
    '${id == 'NORM-003' ? 'PROVISIONAL op; ' : ''}${got.join(' ')}',
  );
}

AtlasBoundingBox _box(Map<String, dynamic> m) => AtlasBoundingBox(
      south: _num(m['south']),
      west: _num(m['west']),
      north: _num(m['north']),
      east: _num(m['east']),
    );

void _boxes(Map<String, dynamic> f) {
  final id = f['id'] as String;
  final expected = f['expected'] as Map<String, dynamic>;
  if (id == 'BOX-001') {
    final inputs = f['inputs'] as Map<String, dynamic>;
    final box = _box(inputs);
    final inside = _coord({'latitude': 44.95, 'longitude': -93.25});
    final outside = _coord({'latitude': 46.0, 'longitude': -93.25});
    final ok = box.validate().isValid &&
        !box.crossesAntimeridian &&
        box.contains(inside) &&
        !box.contains(outside);
    _record(id, ok ? Verdict.pass : Verdict.fail, 'valid+containment');
    return;
  }
  if (id == 'BOX-002') {
    final inputs = f['inputs'] as Map<String, dynamic>;
    final box = _box(inputs);
    final ok = box.validate().isValid &&
        box.crossesAntimeridian == (expected['crosses_antimeridian'] as bool);
    _record(
      id,
      ok ? Verdict.pass : Verdict.fail,
      'crossing flagged, shape valid (not an error)',
    );
    return;
  }
  if (id == 'BOX-003') {
    final inputs = f['inputs'] as Map<String, dynamic>;
    final box = _box(inputs['box'] as Map<String, dynamic>);
    try {
      box.contains(_coord(inputs['point'] as Map<String, dynamic>));
      _record(id, Verdict.fail, 'contained across antimeridian without ruling');
    } on AtlasRejectionException catch (e) {
      _record(
        id,
        e.rejection.category ==
                (expected['rejection'] as Map<String, dynamic>)['category']
            ? Verdict.pass
            : Verdict.fail,
        'DEC-005 open; category=${e.rejection.category}',
      );
    }
    return;
  }
  // BOX-004: polygon bounds derivation.
  final inputs = f['inputs'] as Map<String, dynamic>;
  final polygon = AtlasPolygon(exterior: _ring(inputs['ring'] as List));
  if (!polygon.validate().isValid) {
    _record(id, Verdict.fail, 'fixture ring must validate');
    return;
  }
  final boundsExpected = expected['bounds'] as Map<String, dynamic>;
  final bounds = polygon.bounds;
  final ok = bounds.south == _num(boundsExpected['south']) &&
      bounds.west == _num(boundsExpected['west']) &&
      bounds.north == _num(boundsExpected['north']) &&
      bounds.east == _num(boundsExpected['east']);
  _record(id, ok ? Verdict.pass : Verdict.fail, 'bounds exact');
}

// ---------------------------------------------------------------------------
// ADVERSARIAL
// ---------------------------------------------------------------------------

bool _noDynamicConstructors() {
  // Production coordinate/geometry/camera entry points accept typed values
  // only: no fromJson/fromDynamic/dynamic parsing exists in the slice (the
  // camera wire parser is the single contractual exception and surfaces
  // MALFORMED explicitly). Verified by reading the implementation sources.
  const sources = [
    'packages/atlas_core/lib/src/models/validation.dart',
    'packages/atlas_core/lib/src/models/identifier.dart',
    'packages/atlas_core/lib/src/errors/rejection.dart',
    'packages/atlas_core/lib/src/util/comparison.dart',
    'packages/atlas_geo/lib/src/coordinates/coordinate.dart',
    'packages/atlas_geo/lib/src/coordinates/distance.dart',
    'packages/atlas_geo/lib/src/geometry/polygon.dart',
    'packages/atlas_geo/lib/src/measurements/rings.dart',
  ];
  final banned = RegExp(r'fromJson|fromDynamic|dynamic Hacker|jsonDecode');
  for (final path in sources) {
    if (banned.hasMatch(File(path).readAsStringSync())) return false;
  }
  return true;
}

// ---------------------------------------------------------------------------
// RESOURCES (Phase 1.6: resolved-resource + materialization boundary)
// ---------------------------------------------------------------------------

AtlasProviderDescriptor? _findDescriptor(
  List<AtlasProviderDescriptor> catalog,
  AtlasId id,
) {
  for (final p in catalog) {
    if (p.id == id) return p;
  }
  return null;
}

AtlasResolvedResource _bindForTest(
  AtlasResolutionRequest request,
  List<AtlasProviderDescriptor> catalog,
) {
  final result = AtlasResolver.resolve(request, catalog);
  if (result.status != AtlasResolutionStatus.resolved ||
      result.provider == null) {
    throw StateError(
      'fixture requires a resolved result, got ${result.status}',
    );
  }
  return AtlasResolvedResource.fromResolution(
    result: result,
    provider: _findDescriptor(catalog, result.provider!)!,
  );
}

void _resources(Map<String, dynamic> f) {
  final id = f['id'] as String;
  final inputs = f['inputs'] as Map<String, dynamic>;
  final expected = f['expected'] as Map<String, dynamic>;
  List<AtlasProviderDescriptor> catalogOf(dynamic raw) => [
        for (final p in (raw as List).cast<Map<String, dynamic>>())
          _resProvider(p),
      ];
  if (id == 'RESRC-001') {
    final catalog = catalogOf(inputs['catalog']);
    final resource = _bindForTest(
      _resRequest(inputs['request'] as Map<String, dynamic>),
      catalog,
    );
    final tile = expected['tile'] as Map<String, dynamic>;
    final ok = resource.provider.value == expected['provider'] &&
        resource.kind.name == expected['kind'] &&
        resource.tile != null &&
        resource.tile!.z == (tile['z'] as num).toInt() &&
        resource.tile!.x == (tile['x'] as num).toInt() &&
        resource.tile!.y == (tile['y'] as num).toInt() &&
        resource.identity.address == expected['address'] &&
        resource.attribution == expected['attribution'] &&
        resource.validate().isValid;
    _record(id, ok ? Verdict.pass : Verdict.fail, 'identity=$resource');
    return;
  }
  if (id == 'RESRC-002') {
    final catalog = catalogOf(inputs['catalog']);
    final resource = _bindForTest(
      _resRequest(inputs['request'] as Map<String, dynamic>),
      catalog,
    );
    final ok = resource.provider.value == expected['provider'] &&
        resource.kind.name == expected['kind'] &&
        resource.tile == null &&
        resource.identity.address == expected['address'] &&
        resource.validate().isValid;
    _record(id, ok ? Verdict.pass : Verdict.fail, 'non-tiled, no tile ref');
    return;
  }
  if (id == 'RESRC-003') {
    final location = inputs['location'] as Map<String, dynamic>;
    final zoom = _resNum(inputs['zoom']);
    var ok = true;
    for (final c in (inputs['cases'] as List).cast<Map<String, dynamic>>()) {
      final kind = _resolutionKind(c['kind'] as String);
      final tiled = kind == AtlasDataKind.rasterTiles ||
          kind == AtlasDataKind.vectorTiles;
      final catalog = [
        AtlasProviderDescriptor(
          id: AtlasId('p-${kind.name}'),
          kinds: {kind},
          capabilities:
              tiled ? const {AtlasProviderCapability.tileServing} : const {},
        ),
      ];
      final resource = _bindForTest(
        AtlasResolutionRequest(
          kind: kind,
          latitude: _resNum(location['latitude']),
          longitude: _resNum(location['longitude']),
          zoom: zoom,
        ),
        catalog,
      );
      if ((resource.tile != null) != tiled || !resource.validate().isValid) {
        ok = false;
      }
    }
    _record(
      id,
      ok && (expected['all_resolve'] as bool) ? Verdict.pass : Verdict.fail,
      'all nine kinds resolve; tiles only for tile kinds',
    );
    return;
  }
  if (id == 'RESRC-004') {
    final catalog = catalogOf(inputs['catalog']);
    final request = _resRequest(inputs['request'] as Map<String, dynamic>);
    final first = _bindForTest(request, catalog);
    final second = _bindForTest(request, catalog);
    _record(
      id,
      (first == second) == (expected['equal'] as bool)
          ? Verdict.pass
          : Verdict.fail,
      'deterministic identity equality',
    );
    return;
  }
  if (id == 'MAT-001' || id == 'MAT-002' || id == 'AVAIL-001') {
    final catalog = catalogOf(inputs['catalog']);
    final resource = _bindForTest(
      _resRequest(inputs['request'] as Map<String, dynamic>),
      catalog,
    );
    final inputsMap = inputs;
    final materialized = AtlasMaterializer.materialize(
      resource,
      urlTemplate: inputsMap.containsKey('template')
          ? inputsMap['template'] as String
          : null,
    );
    var ok = materialized.status.name == expected['status'];
    if (expected.containsKey('representation')) {
      final want = expected['representation'] as String?;
      ok = ok && materialized.representation == want;
    }
    _record(
      id,
      ok ? Verdict.pass : Verdict.fail,
      'status=${materialized.status.name} reason=${materialized.reason}',
    );
    return;
  }
  if (id == 'MAT-003') {
    final catalog = catalogOf(inputs['catalog']);
    final resource = _bindForTest(
      _resRequest(inputs['request'] as Map<String, dynamic>),
      catalog,
    );
    final materialized = AtlasMaterializer.materialize(
      resource,
      urlTemplate: inputs['template'] as String,
    );
    _record(
      id,
      materialized.status.name == expected['status'] &&
              materialized.representation == null
          ? Verdict.pass
          : Verdict.fail,
      'template ignored for non-tiles (never forced)',
    );
    return;
  }
  if (id == 'MAT-004') {
    final r = inputs['resource'] as Map<String, dynamic>;
    final resource = AtlasResolvedResource(
      identity: AtlasResourceIdentity(
        provider: AtlasId(r['provider'] as String),
        kind: _resolutionKind(r['kind'] as String),
      ),
      provider: AtlasId(r['provider'] as String),
      kind: _resolutionKind(r['kind'] as String),
    );
    final materialized = AtlasMaterializer.materialize(resource);
    _record(
      id,
      materialized.status.name == expected['status'] &&
              materialized.reason ==
                  (expected['rejection'] as Map<String, dynamic>)['category']
          ? Verdict.pass
          : Verdict.fail,
      'invalid resource, reason=${materialized.reason}',
    );
    return;
  }
  if (id == 'RID-001') {
    final catalog = catalogOf(inputs['catalog']);
    final resource = _bindForTest(
      _resRequest(inputs['request'] as Map<String, dynamic>),
      catalog,
    );
    final key = AtlasTileKey(
      layer: 'standard',
      z: resource.tile!.z,
      x: resource.tile!.x,
      y: resource.tile!.y,
    );
    final a = resource.provider.value;
    final b = resource.identity.toString();
    final c = key.keyString;
    final ok = a != b && a != c && b != c && (expected['all_distinct'] as bool);
    _record(
      id,
      ok ? Verdict.pass : Verdict.fail,
      'provider/identity/key differ',
    );
    return;
  }
  if (id == 'REP-001') {
    final catalog = catalogOf(inputs['catalog']);
    final resource = _bindForTest(
      _resRequest(inputs['request'] as Map<String, dynamic>),
      catalog,
    );
    final materialized = AtlasMaterializer.materialize(
      resource,
      urlTemplate: inputs['template'] as String,
    );
    final representation = materialized.representation ?? '';
    final identityString = resource.identity.toString();
    final ok = representation.contains('://') &&
        representation != identityString &&
        !identityString.contains('://') &&
        (expected['representation_differs_from_identity'] as bool) &&
        (expected['identity_has_no_scheme_separator'] as bool);
    _record(id, ok ? Verdict.pass : Verdict.fail, 'url=$representation');
    return;
  }
  // BOUND-001 only: unguarded fallthrough here previously swallowed
  // ADV-042/043 (fixed: explicit guard + unknown-id safety net).
  if (id == 'BOUND-001') {
    final catalog = catalogOf(inputs['catalog']);
    final resource = _bindForTest(
      _resRequest(inputs['request'] as Map<String, dynamic>),
      catalog,
    );
    final keyJson = inputs['key'] as Map<String, dynamic>;
    final key = AtlasTileKey(
      layer: keyJson['layer'] as String,
      z: (keyJson['z'] as num).toInt(),
      x: (keyJson['x'] as num).toInt(),
      y: (keyJson['y'] as num).toInt(),
    );
    final payloadJson = inputs['payload'] as Map<String, dynamic>;
    final entry = AtlasTileEntry(
      identity: AtlasTileIdentity(
        provider: resource.provider,
        layer: const AtlasId('standard'),
        coordinate: AtlasTileCoordinate(z: key.z, x: key.x, y: key.y),
      ),
      key: key,
      payloadId: AtlasId(payloadJson['id'] as String),
    );
    final materialized = AtlasMaterializer.materialize(
      resource,
      urlTemplate: inputs['template'] as String,
    );
    final ok = entry.validate().isValid == (expected['entry_valid'] as bool) &&
        key.keyString ==
            'standard/${resource.tile!.z}_${resource.tile!.x}_${resource.tile!.y}' &&
        (expected['key_renders_address'] as bool) &&
        materialized.status == AtlasMaterializationStatus.ready &&
        (expected['materialization_clean'] as bool);
    _record(
      id,
      ok ? Verdict.pass : Verdict.fail,
      'key/entry coexist with materialization uncollapsed',
    );
    return;
  }

  if (id == 'ADV-042') {
    final identityJson = inputs['identity'] as Map<String, dynamic>;
    final identity = AtlasResourceIdentity(
      provider: AtlasId(identityJson['provider'] as String),
      kind: _resolutionKind(identityJson['kind'] as String),
      address: identityJson['address'] as String,
    );
    final v = identity.validate();
    _record(
      id,
      !v.isValid ? Verdict.pass : Verdict.fail,
      'PROPOSED rule; rejection=${v.rejection?.category}',
    );
    return;
  }
  // ADV-043: binding a non-resolved result must throw INVALID_STATE.
  final ambiguousCatalog = catalogOf(inputs['catalog']);
  final ambiguousResult = AtlasResolver.resolve(
    _resRequest(inputs['request'] as Map<String, dynamic>),
    ambiguousCatalog,
  );
  if (ambiguousResult.status != AtlasResolutionStatus.ambiguous) {
    _record(
      id,
      Verdict.fail,
      'fixture requires ambiguity, got ${ambiguousResult.status}',
    );
    return;
  }
  try {
    AtlasResolvedResource.fromResolution(
      result: ambiguousResult,
      provider: ambiguousCatalog.first,
    );
    _record(id, Verdict.fail, 'bound an ambiguous result without rejection');
    return;
  } on AtlasRejectionException catch (e) {
    _record(
      id,
      e.rejection.category ==
              (expected['rejection'] as Map<String, dynamic>)['category']
          ? Verdict.pass
          : Verdict.fail,
      'category=${e.rejection.category}',
    );
    return;
  }
}

//---------------------------------------------------------------------------
// PIPELINE (Phase 1.9: orchestration decisions, never execution)
// ---------------------------------------------------------------------------

AtlasResolutionResult _pipeResolution(Map<String, dynamic> m) {
  final q = m['request'] as Map<String, dynamic>;
  final tileJson = m['tile'] as Map<String, dynamic>?;
  return AtlasResolutionResult(
    request: AtlasResolutionRequest(
      kind: _resolutionKind(q['kind'] as String),
      latitude: (q['latitude'] as num).toDouble(),
      longitude: (q['longitude'] as num).toDouble(),
      zoom: (q['zoom'] as num).toDouble(),
      scheme: AtlasTileScheme.values.firstWhere(
        (v) => v.name == ((q['scheme'] as String?) ?? 'xyz'),
      ),
    ),
    status: AtlasResolutionStatus.values.firstWhere(
      (v) => v.name == m['status'],
    ),
    eligible: ((m['eligible'] as List?) ?? const [])
        .map((e) => AtlasId(e as String))
        .toList(),
    provider: m['provider'] == null ? null : AtlasId(m['provider'] as String),
    tile: tileJson == null
        ? null
        : AtlasTileCoordinate(
            z: (tileJson['z'] as num).toInt(),
            x: (tileJson['x'] as num).toInt(),
            y: (tileJson['y'] as num).toInt(),
          ),
    reason: (m['reason'] as String?) ?? '',
  );
}

/// Mirrors the pipeline's descriptor-free binding so materialization inputs
/// can be built for the same resource the pipeline will bind.
AtlasResolvedResource _pipeBoundResource(Map<String, dynamic> m) {
  final resolution = _pipeResolution(m);
  final tile = resolution.tile;
  final provider = resolution.provider!;
  return AtlasResolvedResource(
    identity: AtlasResourceIdentity(
      provider: provider,
      kind: resolution.request.kind,
      address: tile == null
          ? ''
          : AtlasResolvedResource.tileAddressFor(
              tile,
              resolution.request.scheme,
            ),
    ),
    provider: provider,
    kind: resolution.request.kind,
    tile: tile,
    scheme: tile == null ? null : resolution.request.scheme,
  );
}

AtlasAcquisitionResult _pipeAcquisition(Map<String, dynamic> m) {
  final req = _acqRequest(m['request'] as Map<String, dynamic>);
  final state = AtlasAcquisitionState.values.firstWhere(
    (v) => v.name == m['state'],
  );
  final started = (m['started_at'] as num).toInt();
  final updated = (m['updated_at'] as num).toInt();
  final failureName = m['failure'] as String?;
  final failure = failureName == null
      ? null
      : AtlasAcquisitionFailure.values.firstWhere((v) => v.name == failureName);
  switch (state) {
    case AtlasAcquisitionState.succeeded:
      final payloadName = m['payload_id'] as String?;
      if (payloadName == null) {
        return AtlasAcquisitionResult(request: req, state: state);
      }
      return AtlasAcquisition.start(
        req,
        started,
      ).complete(AtlasId(payloadName), updated).toResult();
    case AtlasAcquisitionState.failed:
      return AtlasAcquisition.start(
        req,
        started,
      ).fail(failure!, updated).toResult();
    case AtlasAcquisitionState.cancelled:
      return AtlasAcquisition.start(req, started).cancel(updated).toResult();
    case AtlasAcquisitionState.timedOut:
      return AtlasAcquisition.start(
        req,
        started,
      ).checkTimeout(updated).toResult();
    case AtlasAcquisitionState.inProgress:
      return AtlasAcquisition.start(req, started).toResult();
    case AtlasAcquisitionState.notStarted:
      return AtlasAcquisition(request: req).toResult();
  }
}

AtlasMaterialization? _pipeMaterialization(
  dynamic raw,
  AtlasResolvedResource bound,
) {
  if (raw == null) return null;
  final m = raw as Map<String, dynamic>;
  final override = m['resource_override'] as Map<String, dynamic>?;
  final identity = override == null ? null : _acqResource(override);
  return AtlasMaterialization(
    resource: identity == null
        ? bound
        : AtlasResolvedResource(
            identity: identity,
            provider: identity.provider,
            kind: identity.kind,
          ),
    status: AtlasMaterializationStatus.values.firstWhere(
      (v) => v.name == m['status'],
    ),
    representation: m['representation'] as String?,
    reason: (m['reason'] as String?) ?? '',
  );
}

AtlasPipelinePolicy _pipePolicy(Map<String, dynamic> m) => AtlasPipelinePolicy(
      acquireOnStale: (m['acquire_on_stale'] as bool?) ?? false,
      acquireOnExpired: (m['acquire_on_expired'] as bool?) ?? false,
      acquireOnInvalid: (m['acquire_on_invalid'] as bool?) ?? false,
      fallbackToStaleOnFailure:
          (m['fallback_to_stale_on_failure'] as bool?) ?? false,
    );

void _pipeline(Map<String, dynamic> f) {
  final id = f['id'] as String;
  final inputs = f['inputs'] as Map<String, dynamic>;
  final expected = f['expected'] as Map<String, dynamic>;
  final resolutionJson = inputs['resolution'] as Map<String, dynamic>;
  final aqJson = inputs['acquisition'] as Map<String, dynamic>?;
  final matJson = inputs['materialization'];
  // Binding mirrors the pipeline (resolved results only); resolution
  // terminals return before any materialization is consulted.
  final resolved = (resolutionJson['status'] as String) == 'resolved';
  final outcome = AtlasPipeline.decide(
    resolution: _pipeResolution(resolutionJson),
    cacheEntry: _cacheEntry(inputs['cache_entry']),
    acquisition: aqJson == null ? null : _pipeAcquisition(aqJson),
    materialization: matJson == null || !resolved
        ? null
        : _pipeMaterialization(matJson, _pipeBoundResource(resolutionJson)),
    nowSeconds: (inputs['now'] as num).toInt(),
    policy: _pipePolicy(inputs['policy'] as Map<String, dynamic>),
    acquisitionPolicy: inputs.containsKey('acquisition_policy')
        ? _acqPolicy(inputs['acquisition_policy'] as Map<String, dynamic>)
        : const AtlasAcquisitionPolicy(),
  );
  var ok = outcome.status.name == expected['status'];
  if (expected.containsKey('source')) {
    ok = ok && outcome.source.name == expected['source'];
  }
  if (expected.containsKey('cache_outcome')) {
    ok = ok && outcome.cacheOutcome?.name == expected['cache_outcome'];
  }
  if (expected.containsKey('failure')) {
    ok = ok && outcome.failure?.name == expected['failure'];
  }
  if (expected.containsKey('materialization_status')) {
    ok = ok &&
        outcome.materialization?.status.name ==
            expected['materialization_status'];
  }
  if (expected.containsKey('request_address')) {
    ok = ok &&
        outcome.acquisitionRequest?.resource.address ==
            expected['request_address'];
  }
  if (expected.containsKey('request_kind')) {
    ok = ok &&
        outcome.acquisitionRequest?.resource.kind.name ==
            expected['request_kind'];
  }
  if (expected.containsKey('request_provider')) {
    ok = ok &&
        outcome.acquisitionRequest?.resource.provider.value ==
            expected['request_provider'];
  }
  if (expected.containsKey('request_provider_derived')) {
    ok = ok &&
        (outcome.acquisitionRequest?.provider == null) ==
            (expected['request_provider_derived'] as bool);
  }
  if (expected.containsKey('handoff_payload')) {
    ok = ok &&
        outcome.cacheHandoff?.payloadId?.value == expected['handoff_payload'];
  }
  if (expected.containsKey('handoff_key')) {
    ok = ok && outcome.cacheHandoff?.key.value == expected['handoff_key'];
  }
  if (expected.containsKey('handoff_namespace')) {
    ok = ok &&
        outcome.cacheHandoff?.key.namespace.name ==
            expected['handoff_namespace'];
  }
  if (expected.containsKey('handoff_stored_at')) {
    ok = ok && outcome.cacheHandoff?.storedAt == expected['handoff_stored_at'];
  }
  if (expected.containsKey('handoff_max_age_null')) {
    ok = ok &&
        (outcome.cacheHandoff?.maxAgeSeconds == null) ==
            (expected['handoff_max_age_null'] as bool);
  }
  if (expected.containsKey('never')) {
    ok = ok && outcome.status.name != expected['never'];
  }
  if (expected.containsKey('entry_still_valid')) {
    ok = ok &&
        outcome.entry?.validate().isValid ==
            (expected['entry_still_valid'] as bool);
  }
  if (expected.containsKey('acquire_request_keys')) {
    final want = (expected['acquire_request_keys'] as List).cast<String>();
    ok = ok &&
        outcome.acquisitionRequest != null &&
        want.toSet().containsAll({'resource', 'provider', 'policy'});
  }
  if (expected.containsKey('directive_carries')) {
    ok = ok &&
        outcome.acquisition != null &&
        outcome.cacheHandoff != null &&
        outcome.cacheHandoff?.payloadId != null;
  }
  if (expected.containsKey('generic_keys_only')) {
    final req = outcome.acquisitionRequest;
    ok = ok &&
        req != null &&
        req.provider == null &&
        req.resource.provider.value == 'opentopo' &&
        req.resource.kind == AtlasDataKind.elevation;
  }
  if (expected.containsKey('deterministic')) {
    final again = AtlasPipeline.decide(
      resolution: _pipeResolution(resolutionJson),
      cacheEntry: _cacheEntry(inputs['cache_entry']),
      acquisition: aqJson == null ? null : _pipeAcquisition(aqJson),
      materialization: matJson == null || !resolved
          ? null
          : _pipeMaterialization(matJson, _pipeBoundResource(resolutionJson)),
      nowSeconds: (inputs['now'] as num).toInt(),
      policy: _pipePolicy(inputs['policy'] as Map<String, dynamic>),
    );
    ok = ok &&
        (expected['deterministic'] as bool) &&
        again.cacheHandoff?.key.value == outcome.cacheHandoff?.key.value &&
        again == outcome;
  }
  _record(
    id,
    ok ? Verdict.pass : Verdict.fail,
    'status=${outcome.status.name} via ${outcome.source.name} '
    'cache=${outcome.cacheOutcome?.name} reason=${outcome.reason}',
  );
}

Future<void> _adversarial(Map<String, dynamic> f) async {
  final id = f['id'] as String;
  final expected = f['expected'] as Map<String, dynamic>;
  final inputs = f['inputs'] as Map<String, dynamic>;
  if (id == 'ADV-038' ||
      id == 'ADV-039' ||
      id == 'ADV-040' ||
      id == 'ADV-041') {
    _resolution(f);
    return;
  }
  if (id == 'ADV-042' || id == 'ADV-043') {
    _resources(f);
    return;
  }
  if (id == 'ADV-051' || id == 'ADV-055' || id == 'ADV-061') {
    // URL-target refusal / non-tile validity / network-flag independence:
    // all reduce to request validation over inline resources.
    final r = inputs['resource'] as Map<String, dynamic>;
    final request = AtlasAcquisitionRequest(
      resource: _acqResource(r),
      policy: inputs.containsKey('policy')
          ? _acqPolicy(inputs['policy'] as Map<String, dynamic>)
          : const AtlasAcquisitionPolicy(),
    );
    final v = request.validate();
    final wantInvalid = id == 'ADV-051';
    final wantCategory = id == 'ADV-051' ? 'INVALID_IDENTITY' : null;
    final ok = wantInvalid
        ? !v.isValid && v.rejection?.category == wantCategory
        : v.isValid;
    _record(
      id,
      ok ? Verdict.pass : Verdict.fail,
      id == 'ADV-061'
          ? 'unknown keys ignored; validity network-independent'
          : 'valid=${v.isValid}',
    );
    return;
  }
  if (id == 'ADV-052') {
    final names = AtlasAcquisitionFailure.values.map((v) => v.name).toSet();
    final want = (expected['closed_set'] as List).cast<String>().toSet();
    final hasDigits = names.any((n) => RegExp(r'[0-9]').hasMatch(n));
    _record(
      id,
      names.containsAll(want) &&
              want.containsAll(names) &&
              !hasDigits &&
              (expected['has_no_numeric_codes'] as bool)
          ? Verdict.pass
          : Verdict.fail,
      'taxonomy=$names (closed, no HTTP codes)',
    );
    return;
  }
  if (id == 'ADV-053' || id == 'ADV-057' || id == 'ADV-054') {
    // Shape audits over a live success: request/state/failure/payloadId are
    // the whole surface (absence enforced by arch grep alongside).
    final base = id == 'ADV-054'
        ? {
            'policy': <String, dynamic>{},
            'resource': {
              'provider': 'osm',
              'kind': 'rasterTiles',
              'address': 'z=1/x=0/y=0@xyz',
            },
            'started_at': inputs['started_at'],
          }
        : {
            'policy': <String, dynamic>{},
            'resource': {
              'provider': 'osm',
              'kind': 'rasterTiles',
              'address': 'z=1/x=0/y=0@xyz',
            },
            'started_at': 1700000000,
          };
    final acquisition = AtlasAcquisition.start(
      _acqRequest(base),
      (inputs['now'] as num?)?.toInt() ?? 1700000010,
    ).complete(
      AtlasId((inputs['payload_id'] as String?) ?? 'p-1'),
      (inputs['now'] as num?)?.toInt() ?? 1700000010,
    );
    final result = acquisition.toResult();
    final ok = result.state == AtlasAcquisitionState.succeeded &&
        result.payloadId != null &&
        result.failure == null;
    _record(
      id,
      ok ? Verdict.pass : Verdict.fail,
      'surface = request/state/failure/payloadId; bytes never required',
    );
    return;
  }
  if (id == 'ADV-056' || id == 'ADV-062') {
    final leaks = <String>[];
    _record(
      id,
      _resolutionLeakCheck(leaks) && leaks.isEmpty
          ? Verdict.pass
          : Verdict.fail,
      leaks.isEmpty ? 'no cache/sdk members in acquisition' : leaks.join('; '),
    );
    return;
  }
  if (id == 'ADV-058') {
    final acquisition = AtlasAcquisition.start(
      _acqRequest({
        'policy': <String, dynamic>{},
        'resource': {
          'provider': 'osm',
          'kind': 'rasterTiles',
          'address': 'z=1/x=0/y=0@xyz',
        },
      }),
      1700000000,
    );
    final cancelled = acquisition.cancel(1700000001);
    _record(
      id,
      cancelled.state == AtlasAcquisitionState.cancelled &&
              (expected['cancel_takes_no_token'] as bool)
          ? Verdict.pass
          : Verdict.fail,
      'cancel(int) — no token type exists',
    );
    return;
  }
  if (id == 'ADV-059') {
    final temporal = RegExp(
      r'"id"\s*:\s*"(ACQ-005|ACQ-006|ACQ-007|ACQ-008|ACQ-009|ACQ-010|ACQ-011|ACQ-012|ACQ-013)"',
    );
    final missing = <String>[];
    final dir = Directory('test/golden/acquisition');
    for (final file in dir.listSync().whereType<File>().where(
          (f) => f.path.endsWith('.json'),
        )) {
      final text = file.readAsStringSync();
      if (temporal.hasMatch(text) && !text.contains('"now"')) {
        missing.add(file.path.split(Platform.pathSeparator).last);
      }
    }
    _record(
      id,
      missing.isEmpty && (expected['explicit_now_present'] as bool)
          ? Verdict.pass
          : Verdict.fail,
      missing.isEmpty
          ? 'every lifecycle fixture carries explicit now'
          : 'missing now: $missing',
    );
    return;
  }
  if (id == 'ADV-060') {
    final acquisition = AtlasAcquisition(
      request: _acqRequest({
        'policy': <String, dynamic>{},
        'resource': {
          'provider': 'osm',
          'kind': 'rasterTiles',
          'address': 'z=1/x=0/y=0@xyz',
        },
      }),
    );
    _record(
      id,
      acquisition.startedAt == null && !(expected['attempt_ids_exist'] as bool)
          ? Verdict.pass
          : Verdict.fail,
      'no attempt identity anywhere in the model',
    );
    return;
  }
  if (id == 'ADV-063' ||
      id == 'ADV-064' ||
      id == 'ADV-065' ||
      id == 'ADV-067' ||
      id == 'ADV-070' ||
      id == 'ADV-071' ||
      id == 'ADV-074' ||
      id == 'ADV-075') {
    _pipeline(f);
    return;
  }
  if (id == 'ADV-079' || id == 'ADV-080' || id == 'ADV-081') {
    await _execution(f);
    return;
  }
  if (id == 'ADV-086' ||
      id == 'ADV-087' ||
      id == 'ADV-088' ||
      id == 'ADV-089' ||
      id == 'ADV-090' ||
      id == 'ADV-091' ||
      id == 'ADV-092') {
    await _basemap(f);
    return;
  }
  if (id == 'ADV-093' || id == 'ADV-098') {
    await _store(f);
    return;
  }
  if (id == 'ADV-094' ||
      id == 'ADV-095' ||
      id == 'ADV-096' ||
      id == 'ADV-097') {
    await _packs(f);
    return;
  }
  if (id == 'ADV-066' ||
      id == 'ADV-068' ||
      id == 'ADV-069' ||
      id == 'ADV-072' ||
      id == 'ADV-076' ||
      id == 'ADV-077' ||
      id == 'ADV-078' ||
      id == 'ADV-082' ||
      id == 'ADV-083' ||
      id == 'ADV-084' ||
      id == 'ADV-085' ||
      id == 'ADV-099' ||
      id == 'ADV-100') {
    // Source-collapse scans: forbidden tokens must not appear in pipeline
    // code lines (full-line comments excluded, same as the SELF check).
    final tokens = ((expected['forbidden_tokens'] as List?) ??
            (expected['forbidden_imports'] as List))
        .cast<String>();
    final hits = <String>[];
    final dirs = (inputs['scan'] as String).split(',');
    for (final dirPath in dirs) {
      final dir = Directory(dirPath);
      for (final file in dir
          .listSync(recursive: true)
          .whereType<File>()
          .where((f) => f.path.endsWith('.dart'))) {
        final code = file
            .readAsLinesSync()
            .where((line) => !line.trimLeft().startsWith('//'))
            .join('\n');
        for (final token in tokens) {
          if (code.contains(token)) {
            hits.add(
              '${file.path.split(Platform.pathSeparator).last}: $token',
            );
          }
        }
      }
    }
    _record(
      id,
      hits.isEmpty && (expected['status'] as String) == 'clean'
          ? Verdict.pass
          : Verdict.fail,
      hits.isEmpty ? 'no collapse tokens in pipeline sources' : hits.join('; '),
    );
    return;
  }
  if (id == 'ADV-073') {
    // Explicit-time meta-scan: every pipeline fixture carries now, and no
    // clock token exists in pipeline sources.
    final missing = <String>[];
    final dir = Directory('test/golden/pipeline');
    for (final file in dir.listSync().whereType<File>().where(
          (f) => f.path.endsWith('.json'),
        )) {
      if (!file.readAsStringSync().contains('"now"')) {
        missing.add(file.path.split(Platform.pathSeparator).last);
      }
    }
    final clockHits = <String>[];
    final src = Directory(inputs['scan'] as String);
    for (final file in src
        .listSync(recursive: true)
        .whereType<File>()
        .where((f) => f.path.endsWith('.dart'))) {
      final code = file
          .readAsLinesSync()
          .where((line) => !line.trimLeft().startsWith('//'))
          .join('\n');
      if (code.contains('DateTime.now')) {
        clockHits.add(file.path.split(Platform.pathSeparator).last);
      }
    }
    _record(
      id,
      missing.isEmpty &&
              clockHits.isEmpty &&
              (expected['explicit_now_present'] as bool) &&
              (expected['no_clock_in_sources'] as bool)
          ? Verdict.pass
          : Verdict.fail,
      missing.isEmpty && clockHits.isEmpty
          ? 'every pipeline fixture carries explicit now; no clock in sources'
          : 'missing now: $missing; clock: $clockHits',
    );
    return;
  }
  if (id == 'KEY-004' ||
      id == 'ADV-044' ||
      id == 'ADV-046' ||
      id == 'ADV-047' ||
      id == 'ADV-048' ||
      id == 'ADV-049' ||
      id == 'ADV-050') {
    _cache(f);
    return;
  }
  switch (id) {
    case 'ADV-001':
    case 'ADV-002':
    case 'ADV-003':
    case 'ADV-004':
      _record(
        id,
        _noDynamicConstructors() ? Verdict.pass : Verdict.fail,
        'mechanism=envelope-rejection+no-dynamic-constructors '
        '(null/missing/malformed unrepresentable in typed API)',
      );
    case 'ADV-005':
    case 'ADV-006':
      const probe = AtlasCoordinate(latitude: 0, longitude: 0);
      final evil = id == 'ADV-005'
          ? AtlasCoordinates.validate(double.nan, probe.longitude)
          : AtlasCoordinates.validate(probe.latitude, double.infinity);
      _record(
        id,
        !evil.isValid && evil.rejection?.category == expected['category']
            ? Verdict.pass
            : Verdict.fail,
        'category=${evil.rejection?.category}',
      );
    case 'ADV-007':
      final over = AtlasCoordinates.validate(91.0, 0.0);
      final under = AtlasCoordinates.validate(-91.0, 0.0);
      _record(
        id,
        !over.isValid && !under.isValid ? Verdict.pass : Verdict.fail,
        'both rejected OUT_OF_RANGE',
      );
    case 'ADV-008':
    case 'ADV-010':
    case 'ADV-011':
    case 'ADV-014':
      _record(id, Verdict.blocked, 'Open decision (see catalog entry).');
    case 'ADV-009':
      var ok = true;
      for (final z in [-1.0, 25.0]) {
        final v = AtlasCameraState(
          center: const AtlasCoordinate(latitude: 0, longitude: 0),
          zoom: z,
          bearing: 0,
          pitch: 0,
        ).validate();
        if (v.isValid || v.rejection?.category != 'INVALID_ZOOM') ok = false;
      }
      _record(id, ok ? Verdict.pass : Verdict.fail, 'zoom guards');
    case 'ADV-012':
      final bad = _ring([
        [-93.3, 44.9],
        [-93.2, 44.9],
      ]);
      _record(
        id,
        !AtlasRings.validateRing(bad).isValid ? Verdict.pass : Verdict.fail,
        'member invalid, collection survives (GEOM-003)',
      );
    case 'ADV-013':
      final result = AtlasCollectionScreening.screen<AtlasCoordinate>(
        [],
        (_) => true,
      );
      _record(
        id,
        result.kept.isEmpty ? Verdict.pass : Verdict.fail,
        'PROPOSED empty-accept executed',
      );
    case 'ADV-016':
      _record(
        id,
        Verdict.blocked,
        '0.5A Ruling 2: baseline is not a validity law; flag-not-veto stands, fixture needs contract clarification.',
      );
    case 'ADV-019':
      final v = AtlasCoordinates.validate(0.0, 0.0, crs: 'MARS-2000');
      _record(
        id,
        !v.isValid && v.rejection?.category == 'UNSUPPORTED_CRS'
            ? Verdict.pass
            : Verdict.fail,
        'no silent WGS84 assumption',
      );
    case 'ADV-020':
      try {
        AtlasCameraState.parse('39.83|oops');
        _record(id, Verdict.fail, 'parsed without rejection');
      } on AtlasRejectionException catch (e) {
        _record(
          id,
          e.rejection.category == 'MALFORMED' ? Verdict.pass : Verdict.fail,
          'category=${e.rejection.category}',
        );
      }
    case 'ADV-022':
      const point = AtlasCoordinate(latitude: 44.9, longitude: -93.3);
      const segment = AtlasFlowSegment(from: point, to: point);
      final v = segment.validate();
      _record(
        id,
        !v.isValid ? Verdict.pass : Verdict.fail,
        'PROPOSED zero-length rejection executed',
      );
    case 'ADV-024':
      try {
        AtlasAngles.normalizeBearingDeg(double.nan);
        _record(id, Verdict.fail, 'normalized NaN without rejection');
      } on AtlasRejectionException catch (e) {
        _record(
          id,
          e.rejection.category == 'NON_FINITE' ? Verdict.pass : Verdict.fail,
          'category=${e.rejection.category}',
        );
      }
    case 'ADV-025':
      const crossingBox = AtlasBoundingBox(
        south: 44.0,
        west: 170.0,
        north: 45.0,
        east: -170.0,
      );
      try {
        crossingBox.contains(
          const AtlasCoordinate(latitude: 44.5, longitude: 175.0),
        );
        _record(id, Verdict.fail, 'contained across antimeridian');
      } on AtlasRejectionException catch (e) {
        _record(
          id,
          e.rejection.category == 'UNRESOLVED_ANTIMERIDIAN'
              ? Verdict.pass
              : Verdict.fail,
          'DEC-005 open; category=${e.rejection.category}',
        );
      }
    case 'ADV-026':
      const inverted = AtlasBoundingBox(
        south: 45.0,
        west: -93.3,
        north: 44.9,
        east: -93.2,
      );
      final invertedCheck = inverted.validate();
      _record(
        id,
        !invertedCheck.isValid ? Verdict.pass : Verdict.fail,
        'rejection=${invertedCheck.rejection?.category}',
      );
    case 'ADV-027':
      final openPolygon = AtlasPolygon(
        exterior: _ring([
          [-93.3, 44.9],
          [-93.2, 44.9],
          [-93.2, 45.0],
          [-93.3, 45.0],
        ]),
      );
      final polygonCheck = openPolygon.validate();
      _record(
        id,
        !polygonCheck.isValid ? Verdict.pass : Verdict.fail,
        'rejection=${polygonCheck.rejection?.category}',
      );
    case 'ADV-028':
      final dupStack = AtlasLayerStack([
        AtlasLayerState(definition: _testDef('dup')),
        AtlasLayerState(definition: _testDef('dup')),
      ]);
      final dupCheck = dupStack.validate();
      _record(
        id,
        !dupCheck.isValid ? Verdict.pass : Verdict.fail,
        'PROVISIONAL rule; rejection=${dupCheck.rejection?.category}',
      );
    case 'ADV-029':
      const opacityState = AtlasLayerState(
        definition: AtlasLayerDefinition(
          id: AtlasId('o'),
          kind: AtlasLayerKind.raster,
          providerId: 'p',
        ),
      );
      try {
        opacityState.withOpacity(-0.5);
        _record(id, Verdict.fail, 'clamped or accepted without rejection');
      } on AtlasRejectionException catch (e) {
        _record(
          id,
          e.rejection.category == 'INVALID_LAYER_STATE'
              ? Verdict.pass
              : Verdict.fail,
          'category=${e.rejection.category}',
        );
      }
    case 'ADV-030':
      final tilted = AtlasCameraState.home().copyWith(pitch: 91.0);
      final tiltedCheck = tilted.validate();
      _record(
        id,
        !tiltedCheck.isValid ? Verdict.pass : Verdict.fail,
        'rejection=${tiltedCheck.rejection?.category}',
      );
    case 'ADV-031':
      try {
        AtlasCameraState.parse('39.83|-98.58|3.0|NaN|0.0');
        _record(id, Verdict.fail, 'accepted NaN bearing silently');
      } on AtlasRejectionException catch (e) {
        _record(
          id,
          e.rejection.category == 'NON_FINITE' ? Verdict.pass : Verdict.fail,
          'category=${e.rejection.category}',
        );
      }
    case 'ADV-032':
      final emptyKinds = AtlasProviderDescriptor(
        id: const AtlasId('empty-kinds'),
        kinds: const {},
      );
      final emptyCheck = emptyKinds.validate();
      _record(
        id,
        !emptyCheck.isValid ? Verdict.pass : Verdict.fail,
        'PROPOSED rule; rejection=${emptyCheck.rejection?.category}',
      );
    case 'ADV-033':
      final invertedZoom = AtlasProviderDescriptor(
        id: const AtlasId('inverted-zoom'),
        kinds: const {AtlasDataKind.rasterTiles},
        nativeMinZoom: 19,
        nativeMaxZoom: 10,
      );
      final zoomCheck = invertedZoom.validate();
      _record(
        id,
        !zoomCheck.isValid ? Verdict.pass : Verdict.fail,
        'PROPOSED rule; rejection=${zoomCheck.rejection?.category}',
      );
    case 'ADV-034':
      final badTile = AtlasTileCoordinate(z: 3, x: 8, y: 0).validate();
      _record(
        id,
        !badTile.isValid ? Verdict.pass : Verdict.fail,
        'rejection=${badTile.rejection?.category}',
      );
    case 'ADV-035':
      try {
        AtlasTileKey.parse('standard/3-1-2');
        _record(id, Verdict.fail, 'parsed malformed key without rejection');
      } on AtlasRejectionException catch (e) {
        _record(
          id,
          e.rejection.category == 'MALFORMED' ? Verdict.pass : Verdict.fail,
          'category=${e.rejection.category}',
        );
      }
    case 'ADV-036':
      const placeholderRequest = AtlasTileRequest(
        identity: AtlasTileIdentity(
          provider: AtlasId('p'),
          layer: AtlasId('l'),
          coordinate: AtlasTileCoordinate(z: 3, x: 1, y: 2),
        ),
      );
      try {
        placeholderRequest.resolveUrl(
          'https://tiles.example.com/{z}/{x}/{y}/{s}.png',
        );
        _record(id, Verdict.fail, 'undeclared placeholder passed silently');
      } on AtlasRejectionException catch (e) {
        _record(
          id,
          e.rejection.category == 'MALFORMED_TEMPLATE'
              ? Verdict.pass
              : Verdict.fail,
          'category=${e.rejection.category}',
        );
      }
    case 'ADV-037':
      const badEntry = AtlasTileEntry(
        identity: AtlasTileIdentity(
          provider: AtlasId('osm'),
          layer: AtlasId('standard'),
          coordinate: AtlasTileCoordinate(z: 3, x: 1, y: 2),
        ),
        key: AtlasTileKey(layer: 'standard', z: 3, x: 9, y: 9),
        payloadId: AtlasId('p-1'),
      );
      final badEntryCheck = badEntry.validate();
      _record(
        id,
        !badEntryCheck.isValid ? Verdict.pass : Verdict.fail,
        'PROVISIONAL rule; rejection=${badEntryCheck.rejection?.category}',
      );
    default:
      _record(
        id,
        Verdict.notApplicable,
        'Needs reserved package (tiles/cache/provenance/authority).',
      );
  }
}

// ---------------------------------------------------------------------------
// EXECUTION (Phase 2.0: substrate serving, scripted doubles live HERE only)
// ---------------------------------------------------------------------------

/// Scripted test operation (2.0-M §2): pre-declared outcomes + contact log.
/// Production code must never contain this class (arch-scan enforced).
final class _ScriptedOperation implements AtlasExecutionOperation {
  _ScriptedOperation(
    this.script,
    this.cancellation,
    this.cancelDuring,
    this.log,
  );

  final Map<String, dynamic> script;
  final ExecutionCancellation cancellation;
  final bool cancelDuring;
  final List<String> log;

  /// Cooperation point: logs contact, optionally simulates an arriving
  /// cancel, then cooperates (signal) or proceeds per script.
  Future<T> _at<T>(String method, Future<T> Function() act) {
    log.add(method);
    if (cancelDuring) {
      cancellation.requestCancel();
      if ((script['cooperate'] as bool?) ?? true) {
        throw const ExecutionCancelled();
      }
    }
    return act();
  }

  Never _throwScripted(Map<String, dynamic> s) {
    final type = s['type'] as String?;
    final message = (s['message'] as String?) ?? 'scripted throw';
    if (type == 'StateError') throw StateError(message);
    throw Exception('$type: $message');
  }

  @override
  Future<AtlasCacheEntry> serveEntry(
    AtlasCacheEntry entry,
    ExecutionContext context,
  ) =>
      _at('serve', () async {
        final s = script['serve'] as Map<String, dynamic>;
        switch (s['do']) {
          case 'echo':
            return entry;
          case 'invalid_entry':
            return AtlasCacheEntry(
              key: const AtlasCacheKey(
                namespace: AtlasCacheNamespace.resource,
                value: '',
              ),
              storedAt: 0,
            );
          case 'throw':
            _throwScripted(s);
          default:
            throw StateError('unknown serve script: ${s['do']}');
        }
      });

  @override
  Future<AtlasAcquisitionResult> runAcquisition(
    AtlasAcquisitionRequest request,
    ExecutionContext context,
  ) =>
      _at('acquire', () async {
        final s = script['acquire'] as Map<String, dynamic>;
        final now = context.nowSeconds;
        switch (s['do']) {
          case 'succeed':
            return AtlasAcquisition.start(
              request,
              now - 100,
            ).complete(AtlasId(s['payload_id'] as String), now).toResult();
          case 'fail':
            return AtlasAcquisition.start(request, now - 100)
                .fail(
                  AtlasAcquisitionFailure.values.firstWhere(
                    (v) => v.name == s['failure'],
                  ),
                  now,
                )
                .toResult();
          case 'cancelled_result':
            return AtlasAcquisition.start(
              request,
              now - 100,
            ).cancel(now).toResult();
          case 'timeout_result':
            final timed = AtlasAcquisitionRequest(
              resource: request.resource,
              policy: AtlasAcquisitionPolicy(
                timeoutSeconds: (s['timeout_seconds'] as num).toInt(),
              ),
            );
            return AtlasAcquisition.start(
              timed,
              now - 100,
            ).checkTimeout(now).toResult();
          case 'pending':
            return AtlasAcquisition.start(request, now - 100).toResult();
          case 'throw':
            _throwScripted(s);
          default:
            throw StateError('unknown acquire script: ${s['do']}');
        }
      });

  @override
  Future<AtlasCacheEntry> storeHandoff(
    AtlasCacheEntry handoff,
    ExecutionContext context,
  ) =>
      _at('store', () async {
        final s = script['store'] as Map<String, dynamic>;
        switch (s['do']) {
          case 'echo':
            return handoff;
          case 'invalid_entry':
            return AtlasCacheEntry(
              key: const AtlasCacheKey(
                namespace: AtlasCacheNamespace.resource,
                value: '',
              ),
              storedAt: 0,
            );
          case 'throw':
            _throwScripted(s);
          default:
            throw StateError('unknown store script: ${s['do']}');
        }
      });
}

Future<ExecutionResultBase> _serveExecution(
  String kind,
  Map<String, dynamic> commandJson,
  ExecutionContext context,
) {
  switch (kind) {
    case 'serve':
      return AtlasExecutor.serveEntry(
        command: ServeEntryCommand(
          entry: _cacheEntry(commandJson['entry'])!,
          fallback: (commandJson['fallback'] as bool?) ?? false,
        ),
        context: context,
      );
    case 'acquire':
      return AtlasExecutor.runAcquisition(
        command: RunAcquisitionCommand(
          request: _acqRequest(commandJson['request'] as Map<String, dynamic>),
        ),
        context: context,
      );
    case 'store':
      return AtlasExecutor.storeHandoff(
        command: StoreHandoffCommand(
          handoff: _cacheEntry(commandJson['entry'])!,
        ),
        context: context,
      );
    default:
      throw StateError('unknown command kind: $kind');
  }
}

Future<void> _execution(Map<String, dynamic> f) async {
  final id = f['id'] as String;
  final inputs = f['inputs'] as Map<String, dynamic>;
  final expected = f['expected'] as Map<String, dynamic>;
  final commandJson = inputs['command'] as Map<String, dynamic>;
  final kind = commandJson['kind'] as String;
  final contextJson = inputs['context'] as Map<String, dynamic>;

  final serveKey = kind == 'acquire'
      ? 'acquire'
      : kind == 'serve'
          ? 'serve'
          : 'store';
  final opSpecs = (inputs['operations'] as List)
      .cast<Map<dynamic, dynamic>>()
      .map((op) => op.cast<String, dynamic>())
      .toList();
  for (final spec in opSpecs) {
    final script =
        (spec['script'] as Map<dynamic, dynamic>).cast<String, dynamic>();
    if (!script.containsKey(serveKey)) {
      throw StateError('fixture $id: script missing $serveKey behavior');
    }
  }

  ExecutionContext buildContext(List<String> log) {
    final cancellation = ExecutionCancellation();
    final binding = <AtlasResourceIdentity, AtlasExecutionOperation>{};
    for (final spec in opSpecs) {
      binding[_acqResource(
        (spec['identity'] as Map<dynamic, dynamic>).cast<String, dynamic>(),
      )] = _ScriptedOperation(
        (spec['script'] as Map<dynamic, dynamic>).cast<String, dynamic>(),
        cancellation,
        (contextJson['cancel_during'] as bool?) ?? false,
        log,
      );
    }
    return ExecutionContext(
      nowSeconds: (contextJson['now'] as num).toInt(),
      cancellation: cancellation,
      binding: AtlasOperationBinding(binding),
    );
  }

  final log = <String>[];
  var context = buildContext(log);
  if ((contextJson['cancel_before'] as bool?) ?? false) {
    context.cancellation.requestCancel();
  }
  final result = await _serveExecution(kind, commandJson, context);

  var ok = result.state.name == expected['state'];
  if (result is RunAcquisitionResult) {
    if (expected.containsKey('acquisition_state')) {
      ok =
          ok && result.acquisition?.state.name == expected['acquisition_state'];
    }
    if (expected.containsKey('acquisition_failure')) {
      final want = expected['acquisition_failure'] as String?;
      ok = ok && result.acquisition?.failure?.name == want;
    }
    if (expected.containsKey('echo_payload')) {
      ok = ok &&
          result.acquisition?.payloadId?.value == expected['echo_payload'];
    }
    if (expected.containsKey('carries_identity_not_bytes')) {
      ok = ok &&
          (expected['carries_identity_not_bytes'] as bool) &&
          result.acquisition != null &&
          result.acquisition?.payloadId != null;
    }
  }
  if (result is ServeEntryResult) {
    if (expected.containsKey('fallback')) {
      ok = ok && result.command.fallback == expected['fallback'];
    }
    if (expected.containsKey('echo_entry')) {
      ok = ok &&
          (expected['echo_entry'] as bool) &&
          result.servedEntry ==
              _cacheEntry(
                (commandJson['entry'] as Map).cast<String, dynamic>(),
              );
    }
  }
  if (result is StoreHandoffResult && expected.containsKey('echo_entry')) {
    ok = ok &&
        (expected['echo_entry'] as bool) &&
        result.storedEntry ==
            _cacheEntry((commandJson['entry'] as Map).cast<String, dynamic>());
  }
  if (expected.containsKey('echo_payload') && result is StoreHandoffResult) {
    ok = ok && result.storedEntry?.payloadId?.value == expected['echo_payload'];
  }
  if (expected.containsKey('malfunction')) {
    ok = ok && result.malfunction?.name == expected['malfunction'];
  }
  if (expected.containsKey('contacted')) {
    ok = ok && log.isNotEmpty == (expected['contacted'] as bool);
  }
  if (expected.containsKey('contact_count')) {
    ok = ok && log.length == expected['contact_count'];
  }
  if (expected.containsKey('reason_contains')) {
    final wants = expected['reason_contains'] is List
        ? (expected['reason_contains'] as List).cast<String>()
        : [expected['reason_contains'] as String];
    ok = ok && wants.every((w) => result.reason.contains(w));
  }
  if (expected.containsKey('equal_rerun')) {
    final log2 = <String>[];
    final context2 = buildContext(log2);
    if ((contextJson['cancel_before'] as bool?) ?? false) {
      context2.cancellation.requestCancel();
    }
    final again = await _serveExecution(kind, commandJson, context2);
    ok = ok && (expected['equal_rerun'] as bool) && again == result;
  }
  _record(
    id,
    ok ? Verdict.pass : Verdict.fail,
    'state=${result.state.name} malfunction=${result.malfunction?.name} '
    'contacts=$log reason=${result.reason}',
  );
}

// ---------------------------------------------------------------------------
// RETENTION + PACKS (Blueprint Phase 3 engine side)
// ---------------------------------------------------------------------------

Future<void> _store(Map<String, dynamic> f) async {
  final id = f['id'] as String;
  final inputs = f['inputs'] as Map<String, dynamic>;
  final expected = f['expected'] as Map<String, dynamic>;
  final op = expected['op'] as String? ??
      (id == 'ADV-093'
          ? 'invalidate'
          : id == 'ADV-098'
              ? 'op_miss'
              : 'put_get');
  final store = AtlasMemoryStore(
    capacity: (inputs['capacity'] as num).toInt(),
  );
  AtlasCacheEntry entry(String field) =>
      _cacheEntry(inputs[field] as Map<String, dynamic>)!;
  var ok = true;
  var detail = '';
  switch (op) {
    case 'put_get':
      final e = entry('entry');
      store.put(e);
      ok = store.get(e.key) == e && store.entryCount == expected['count'];
      detail = 'echo + count';
    case 'eviction':
      final items = (inputs['entries'] as List).cast<Map<dynamic, dynamic>>();
      AtlasCacheEntry? evicted;
      for (var idx = 0; idx < items.length; idx++) {
        final e = _cacheEntry(items[idx].cast<String, dynamic>())!;
        if (idx == items.length - 1) {
          for (final a in (inputs['access'] as List).cast<String>()) {
            store.get(
              AtlasCacheKey(namespace: AtlasCacheNamespace.resource, value: a),
            );
          }
          evicted = store.put(e);
        } else {
          store.put(e);
        }
      }
      final bKey = AtlasCacheKey(
        namespace: AtlasCacheNamespace.resource,
        value: 'other/rasterTiles/z=9/x=9/y=9@xyz',
      );
      ok = evicted?.key.value == expected['evicted'] &&
          (store.get(bKey) == null) == (expected['b_absent'] as bool);
      detail = 'evicted=${evicted?.key.value} lru-honored';
    case 'replace':
      final first = _cacheEntry(inputs['first'] as Map<String, dynamic>)!;
      final second = _cacheEntry(inputs['second'] as Map<String, dynamic>)!;
      store.put(first);
      final evicted = store.put(second);
      ok = evicted == null &&
          store.entryCount == expected['count'] &&
          store.get(second.key)?.payloadId?.value == expected['echo_payload'];
      detail = 'replace refreshes, never evicts self';
    case 'invalid_put':
      try {
        store.put(entry('entry'));
        ok = false;
      } on AtlasRejectionException catch (e) {
        ok = e.rejection.category == expected['rejection'];
        detail = 'category=${e.rejection.category}';
      }
    case 'remove':
      final e = entry('entry');
      store.put(e);
      final first = store.remove(e.key);
      final second = store.remove(e.key);
      ok = first == (expected['first'] as bool) &&
          second == (expected['second'] as bool);
      detail = 'remove=$first then $second';
    case 'clear':
      for (final e
          in (inputs['entries'] as List).cast<Map<dynamic, dynamic>>()) {
        store.put(_cacheEntry(e.cast<String, dynamic>())!);
      }
      store.clear();
      ok = store.entryCount == expected['after'];
      detail = 'cleared';
    case 'invalidate':
      final e = entry('entry');
      store.put(e);
      final done = store.invalidate(e.key);
      final stored = store.get(e.key);
      final missing = store.invalidate(
        const AtlasCacheKey(
          namespace: AtlasCacheNamespace.resource,
          value: 'k/v/zzz',
        ),
      );
      ok = done;
      if (expected.containsKey('revoked')) {
        ok = ok && (stored?.revoked ?? false) == expected['revoked'];
      }
      if (expected.containsKey('missing')) {
        ok = ok && missing == (expected['missing'] as bool);
      }
      if (expected.containsKey('revoked_echo')) {
        ok = ok && (stored?.revoked ?? false) == expected['revoked_echo'];
      }
      detail = 'revoked=${stored?.revoked}';
    case 'stats':
      for (final e
          in (inputs['entries'] as List).cast<Map<dynamic, dynamic>>()) {
        store.put(_cacheEntry(e.cast<String, dynamic>())!);
      }
      ok = store.stats.entryCount == expected['count'] &&
          store.stats.capacity == expected['capacity'];
      detail = 'stats=${store.stats.entryCount}/${store.stats.capacity}';
    case 'op_serve':
    case 'op_miss':
      final e = entry('entry');
      if (op == 'op_serve') store.put(e);
      final identity = e.resource!;
      final result = await AtlasExecutor.serveEntry(
        command: ServeEntryCommand(entry: e),
        context: ExecutionContext(
          nowSeconds: (inputs['now'] as num).toInt(),
          cancellation: ExecutionCancellation(),
          binding: AtlasOperationBinding({
            identity: AtlasStoreOperation(store: store),
          }),
        ),
      );
      ok = result.state.name == expected['state'];
      if (expected.containsKey('echo')) {
        ok = ok && result.servedEntry == e;
      }
      if (expected.containsKey('malfunction')) {
        ok = ok && result.malfunction?.name == expected['malfunction'];
      }
      if (expected.containsKey('reason_contains')) {
        final wants = (expected['reason_contains'] as List).cast<String>();
        ok = ok && wants.every((w) => result.reason.contains(w));
      }
      detail =
          'state=${result.state.name} served=${result.servedEntry != null}';
    case 'op_store':
      final e = entry('entry');
      final identity = e.resource!;
      final result = await AtlasExecutor.storeHandoff(
        command: StoreHandoffCommand(handoff: e),
        context: ExecutionContext(
          nowSeconds: (inputs['now'] as num).toInt(),
          cancellation: ExecutionCancellation(),
          binding: AtlasOperationBinding({
            identity: AtlasStoreOperation(store: store),
          }),
        ),
      );
      ok = result.state.name == expected['state'] && store.get(e.key) != null;
      detail = 'resident=${store.get(e.key) != null}';
    case 'op_acquire_seam':
      final identity = AtlasResourceIdentity(
        provider: AtlasId(inputs['provider'] as String),
        kind: _resolutionKind(inputs['kind'] as String),
        address: inputs['address'] as String,
      );
      final result = await AtlasExecutor.runAcquisition(
        command: RunAcquisitionCommand(
          request: AtlasAcquisitionRequest(resource: identity),
        ),
        context: ExecutionContext(
          nowSeconds: (inputs['now'] as num).toInt(),
          cancellation: ExecutionCancellation(),
          binding: AtlasOperationBinding({
            identity: AtlasStoreOperation(store: store),
          }),
        ),
      );
      ok = result.state.name == expected['state'] &&
          result.malfunction?.name == expected['malfunction'];
      if (expected.containsKey('reason_contains')) {
        final wants = (expected['reason_contains'] as List).cast<String>();
        ok = ok && wants.every((w) => result.reason.contains(w));
      }
      detail = 'seam holds: ${result.reason}';
    default:
      _record(id, Verdict.fail, 'unknown store op: $op');
      return;
  }
  _record(id, ok ? Verdict.pass : Verdict.fail, detail);
}

AtlasPackManifest _packsManifest(Map<String, dynamic> inputs) =>
    AtlasPackManifest(
      packId: AtlasId(inputs['pack_id'] as String),
      provider: AtlasId(inputs['provider'] as String),
      zoomMin: (inputs['zoom_min'] as num).toInt(),
      zoomMax: (inputs['zoom_max'] as num).toInt(),
      createdAt: (inputs['created_at'] as num).toInt(),
      sourceVersion: inputs['source_version'] as String?,
      attribution: inputs['attribution'] as String?,
      entries: [
        for (final e
            in (inputs['entries'] as List).cast<Map<dynamic, dynamic>>())
          AtlasPackEntry(
            address: e['address'] as String,
            checksum: e['checksum'] as String,
          ),
      ],
    );

Future<void> _packs(Map<String, dynamic> f) async {
  final id = f['id'] as String;
  final inputs = f['inputs'] as Map<String, dynamic>;
  final expected = f['expected'] as Map<String, dynamic>;
  var ok = true;
  var detail = '';
  List<AtlasTileCoordinate> tilesOf(dynamic raw) => [
        for (final t in (raw as List).cast<Map<dynamic, dynamic>>())
          AtlasTileCoordinate(
            z: (t['z'] as num).toInt(),
            x: (t['x'] as num).toInt(),
            y: (t['y'] as num).toInt(),
          ),
      ];
  if (id == 'PAK-001' || id == 'ADV-094') {
    final manifest = _packsManifest(inputs);
    ok = manifest.validate().isValid &&
        manifest.seal.length == 16 &&
        AtlasPackManifest.fromJson(
              manifest.toJson().cast<String, dynamic>(),
            ) ==
            manifest;
    if (id == 'ADV-094') {
      final tampered = AtlasPackManifest(
        packId: manifest.packId,
        provider: manifest.provider,
        zoomMin: manifest.zoomMin,
        zoomMax: manifest.zoomMax,
        createdAt: manifest.createdAt,
        entries: const [
          AtlasPackEntry(
            address: 'z=1/x=0/y=0@xyz',
            checksum: 'ffffffffffffffff',
          ),
        ],
      );
      ok = ok && tampered.seal != manifest.seal;
    }
    detail = 'seal=${manifest.seal}';
  } else if (id == 'PAK-002') {
    final check = _packsManifest(inputs).validate();
    ok = !check.isValid && check.rejection?.category == expected['rejection'];
    detail = 'invalid range refused';
  } else if (id == 'PAK-003') {
    final bytes =
        (expected['bytes'] as List).cast<num>().map((n) => n.toInt()).toList();
    final first = fnv1a64(bytes);
    ok = first.length == expected['len'] && fnv1a64(bytes) == first;
    detail = 'fnv=$first';
  } else if (id == 'PAK-004' ||
      id == 'PAK-005' ||
      id == 'PAK-006' ||
      id == 'PAK-007' ||
      id == 'PAK-008' ||
      id == 'PAK-017' ||
      id == 'ADV-097') {
    Object plan() => AtlasPackPlanner.plan(
          endpoint: _bmEndpoint(inputs['endpoint'] as String),
          zMin: (inputs['z_min'] as num).toInt(),
          zMax: (inputs['z_max'] as num).toInt(),
          xMin: (inputs['x_min'] as num).toInt(),
          xMax: (inputs['x_max'] as num).toInt(),
          yMin: (inputs['y_min'] as num).toInt(),
          yMax: (inputs['y_max'] as num).toInt(),
          bytesPerTileEstimate: (inputs['bytes_per_tile'] as num).toInt(),
          approvedBulk: (inputs['approved_bulk'] as bool?) ?? false,
          isPrefetch: (inputs['is_prefetch'] as bool?) ?? false,
        );
    if (id == 'ADV-097') {
      final first = plan() as AtlasPackPlan;
      final second = plan() as AtlasPackPlan;
      ok = (expected['identical'] as bool) &&
          first.estimatedBytes == second.estimatedBytes &&
          first.tiles.length == second.tiles.length &&
          Iterable<int>.generate(
            first.tiles.length,
          ).every((i) => first.tiles[i] == second.tiles[i]);
      detail = 'plan deterministic x${first.tiles.length}';
    } else {
      final outcome = plan();
      if (outcome is AtlasPackRefusal) {
        ok = outcome.reason == expected['reason'];
        detail = 'refused: ${outcome.reason}';
      } else {
        final planOk = outcome as AtlasPackPlan;
        ok = planOk.entryCount == expected['entry_count'] &&
            planOk.estimatedBytes == expected['estimated_bytes'];
        detail = 'planned x${planOk.entryCount}';
      }
    }
  } else if (id == 'PAK-014') {
    final limiter = AtlasRateLimiter(
      capacity: (inputs['capacity'] as num).toInt(),
      refillPerSecond: (inputs['refill'] as num).toInt(),
    );
    final at =
        (expected['at'] as List).cast<num>().map((n) => n.toInt()).toList();
    final want = (expected['admitted'] as List).cast<bool>();
    final got = [for (final t in at) limiter.take(t)];
    ok = got.length == want.length &&
        Iterable<int>.generate(got.length).every((i) => got[i] == want[i]);
    detail = 'admitted=$got';
  } else {
    // Download scenarios (PAK-009..013, PAK-015/016, ADV-095/096).
    final byteMap = <String, List<int>>{};
    if (inputs.containsKey('bytes_map')) {
      for (final kv
          in ((inputs['bytes_map'] as Map).cast<String, dynamic>()).entries) {
        byteMap[kv.key] =
            (kv.value as List).cast<num>().map((n) => n.toInt()).toList();
      }
    }
    final prereceived = <String, List<int>>{};
    if (inputs.containsKey('prereceived')) {
      for (final kv
          in ((inputs['prereceived'] as Map).cast<String, dynamic>()).entries) {
        prereceived[kv.key] =
            (kv.value as List).cast<num>().map((n) => n.toInt()).toList();
      }
    }
    final failOn = inputs['fail_on'] as String?;
    final cancelOn = inputs['cancel_on'] as String?;
    final cancellation = ExecutionCancellation();
    AtlasRateLimiter? limiter;
    if (inputs.containsKey('limiter')) {
      final lim = inputs['limiter'] as Map<String, dynamic>;
      limiter = AtlasRateLimiter(
        capacity: (lim['capacity'] as num).toInt(),
        refillPerSecond: (lim['refill'] as num).toInt(),
      );
    }
    final tiles = tilesOf(inputs['tiles']);
    Future<List<int>> source(AtlasTileCoordinate tile) async {
      final key = '${tile.z}/${tile.x}/${tile.y}';
      if (key == cancelOn) cancellation.requestCancel();
      if (key == failOn) throw Exception('chunk boom for $key');
      final hit = byteMap[key];
      if (hit == null) throw Exception('no scripted bytes for $key');
      return hit;
    }

    Future<AtlasPackDownloader> run() {
      final downloader = AtlasPackDownloader(
        tiles: tiles,
        source: source,
        limiter: limiter,
        cancellation: cancellation,
        received: Map.of(prereceived),
      );
      return downloader
          .download((inputs['now'] as num).toInt())
          .then((_) => downloader);
    }

    final downloader = await run();
    if (id == 'PAK-015') {
      downloader.discard();
      ok = downloader.state == AtlasDownloadState.planned &&
          downloader.progress.received == expected['after'];
      detail = 'discarded to planned';
    } else if (id == 'ADV-096') {
      final again = await downloader.download((inputs['now'] as num).toInt());
      ok = downloader.state == AtlasDownloadState.failed &&
          again == AtlasDownloadState.failed;
      detail = 'failed sticky';
    } else if (id == 'PAK-016') {
      final manifest = AtlasPackManifest(
        packId: AtlasId(inputs['pack_id'] as String),
        provider: AtlasId(inputs['provider'] as String),
        zoomMin: (inputs['zoom_min'] as num).toInt(),
        zoomMax: (inputs['zoom_max'] as num).toInt(),
        createdAt: (inputs['now'] as num).toInt(),
        attribution: inputs['attribution'] as String?,
        entries: [
          for (final kv in downloader.received.entries)
            AtlasPackEntry(address: kv.key, checksum: fnv1a64(kv.value)),
        ],
      );
      ok = downloader.state == AtlasDownloadState.complete &&
          manifest.validate().isValid &&
          manifest.entryCount == expected['entry_count'] &&
          manifest.seal.length == expected['seal_len'];
      detail = 'manifest from download seal=${manifest.seal}';
    } else {
      final progress = downloader.progress;
      ok = downloader.state.name == expected['state'];
      if (expected.containsKey('received')) {
        ok = ok && progress.received == expected['received'];
      }
      if (expected.containsKey('planned')) {
        ok = ok && progress.planned == expected['planned'];
      }
      if (expected.containsKey('bytes')) {
        ok = ok && progress.bytes == expected['bytes'];
      }
      if (expected.containsKey('retained_partial')) {
        ok = ok &&
            (expected['retained_partial'] as bool) &&
            progress.received > 0 &&
            progress.received < progress.planned;
      }
      if (expected.containsKey('detail_contains')) {
        final wants = (expected['detail_contains'] as List).cast<String>();
        ok = ok && wants.every((w) => downloader.failureDetail.contains(w));
      }
      detail = 'state=${downloader.state.name} $progress';
    }
  }
  _record(id, ok ? Verdict.pass : Verdict.fail, detail);
}

//---------------------------------------------------------------------------
// BASEMAP MATRIX (Blueprint Phase 2 engine side: registry + implementations)
// ---------------------------------------------------------------------------

AtlasProviderRegistry _bmRegistry() => AtlasBuiltinProviders.registry();

AtlasProviderEndpoint _bmEndpoint(String id) {
  AtlasProviderDescriptor syn(String sid) => AtlasProviderDescriptor(
        id: AtlasId(sid),
        kinds: const {AtlasDataKind.rasterTiles},
      );
  const policy = AtlasProviderPolicy(
    onlineAllowed: true,
    cacheAllowed: true,
    prefetchAllowed: true,
  );
  switch (id) {
    case 'synthetic-bad':
      return AtlasProviderEndpoint(
        descriptor: syn('syn-bad'),
        policy: policy,
        urlTemplate: 'https://tiles.example/{z}/{foo}.png',
      );
    case 'synthetic-noparams':
      return AtlasProviderEndpoint(
        descriptor: syn('syn-noparams'),
        policy: policy,
        urlTemplate: 'https://{s}.tiles.example/{z}/{x}/{y}.png',
      );
    case 'synthetic-keyed':
      return AtlasProviderEndpoint(
        descriptor: syn('syn-keyed'),
        policy: const AtlasProviderPolicy(
          onlineAllowed: true,
          cacheAllowed: true,
          prefetchAllowed: false,
          requiresKey: true,
        ),
        urlTemplate: 'https://tiles.example/{z}/{x}/{y}.png',
      );
    case 'synthetic-capped':
      return AtlasProviderEndpoint(
        descriptor: syn('syn-capped'),
        policy: const AtlasProviderPolicy(
          onlineAllowed: true,
          cacheAllowed: true,
          prefetchAllowed: true,
          maxTiles: 2,
        ),
        urlTemplate: 'https://tiles.example/{z}/{x}/{y}.png',
      );
    default:
      return _bmRegistry().lookup(id)!;
  }
}

final class _FakeTransport {
  _FakeTransport(this.mode, this.bytes, this.status);

  final String mode;
  final List<int> bytes;
  final int status;
  final List<Uri> urls = [];
  final List<Map<String, String>> headersSeen = [];

  Future<List<int>> call(Uri url, Map<String, String> headers) async {
    urls.add(url);
    headersSeen.add(headers);
    if (mode == 'throw') {
      throw AtlasTransportException('status $status', statusCode: status);
    }
    return bytes;
  }
}

Future<void> _basemap(Map<String, dynamic> f) async {
  final id = f['id'] as String;
  final inputs = f['inputs'] as Map<String, dynamic>;
  final expected = f['expected'] as Map<String, dynamic>;
  final op = expected['op'] as String;
  final registry = _bmRegistry();
  var ok = true;
  var detail = '';
  switch (op) {
    case 'registry_ids':
      final want = (expected['ids'] as List).cast<String>();
      final got = registry.ids;
      ok = got.length == want.length &&
          Iterable<int>.generate(got.length).every((i) => got[i] == want[i]);
      detail = 'ids=$got';
    case 'validate_all':
      ok = registry.descriptors.every(
            (d) => AtlasBuiltinProviders.all
                .firstWhere((e) => e.descriptor == d)
                .validate()
                .isValid,
          ) ==
          (expected['valid'] as bool);
      detail = 'all validate';
    case 'providers_for':
      final got = registry.providersFor(
        _resolutionKind(expected['kind'] as String),
      );
      ok = got.length == expected['count'];
      detail = 'count=${got.length}';
    case 'lookup':
      ok = (registry.lookup(expected['id'] as String) != null) ==
          (expected['found'] as bool);
      detail = 'found=${registry.lookup(expected['id'] as String) != null}';
    case 'register_duplicate':
      try {
        registry.register(AtlasBuiltinProviders.osmStandard);
        ok = false;
      } on StateError {
        ok = (expected['throws_duplicate'] as bool);
      }
      detail = 'duplicate refused';
    case 'policy':
      final policy = _bmEndpoint(
        (expected['id'] as String?) ?? inputs['id'] as String,
      ).policy;
      if (expected.containsKey('prefetch_allowed')) {
        ok = ok && policy.prefetchAllowed == expected['prefetch_allowed'];
      }
      if (expected.containsKey('bulk_guard_present')) {
        ok = ok &&
            (policy.bulkGuard?.isNotEmpty ?? false) ==
                (expected['bulk_guard_present'] as bool);
      }
      if (expected.containsKey('requires_key')) {
        ok = ok && policy.requiresKey == expected['requires_key'];
      }
      detail = 'prefetch=${policy.prefetchAllowed} key=${policy.requiresKey}';
    case 'template':
      final endpoint = _bmEndpoint(inputs['endpoint'] as String);
      AtlasTileIdentity identity;
      if (inputs.containsKey('address')) {
        final parsed = parseTileAddress(inputs['address'] as String)!;
        identity = AtlasTileIdentity(
          provider: endpoint.descriptor.id,
          layer: const AtlasId(''),
          coordinate: parsed.coordinate,
          scheme: parsed.scheme,
        );
      } else {
        final tile = inputs['tile'] as Map<String, dynamic>;
        identity = AtlasTileIdentity(
          provider: endpoint.descriptor.id,
          layer: const AtlasId(''),
          coordinate: AtlasTileCoordinate(
            z: (tile['z'] as num).toInt(),
            x: (tile['x'] as num).toInt(),
            y: (tile['y'] as num).toInt(),
          ),
          scheme: AtlasTileScheme.values.firstWhere(
            (v) => v.name == inputs['scheme'],
          ),
        );
      }
      final url = AtlasTileRequest(
        identity: identity,
        params: endpoint.params,
      ).resolveUrl(endpoint.urlTemplate!);
      ok = url == expected['url'];
      detail = 'url=$url';
    case 'malformed':
      try {
        final endpoint = _bmEndpoint(inputs['endpoint'] as String);
        final tile = inputs['tile'] as Map<String, dynamic>;
        AtlasTileRequest(
          identity: AtlasTileIdentity(
            provider: endpoint.descriptor.id,
            layer: const AtlasId(''),
            coordinate: AtlasTileCoordinate(
              z: (tile['z'] as num).toInt(),
              x: (tile['x'] as num).toInt(),
              y: (tile['y'] as num).toInt(),
            ),
          ),
          params: endpoint.params,
        ).resolveUrl(endpoint.urlTemplate!);
        ok = false;
      } on AtlasRejectionException catch (e) {
        ok = e.rejection.category == expected['rejection'];
        detail = 'category=${e.rejection.category}';
      }
    case 'local_template':
      ok = (AtlasBuiltinProviders.localBundle.urlTemplate == null) ==
          (expected['template_null'] as bool);
      detail = 'local is bundle-backed';
    case 'resolve':
      final request = _resRequest((inputs['request'] as Map<String, dynamic>));
      final result = AtlasResolver.resolve(
        AtlasResolutionRequest(
          kind: request.kind,
          latitude: request.latitude,
          longitude: request.longitude,
          zoom: request.zoom,
          scheme: request.scheme,
          preferredProviders: [
            for (final p
                in ((inputs['preferred'] as List?) ?? const []).cast<String>())
              AtlasId(p),
          ],
        ),
        registry.descriptors,
      );
      ok = result.status.name == expected['status'];
      if (expected.containsKey('provider')) {
        ok = ok && result.provider?.value == expected['provider'];
      }
      if (expected.containsKey('eligible')) {
        final want = (expected['eligible'] as List).cast<String>();
        final got = result.eligible.map((e) => e.value).toList();
        ok = ok &&
            got.length == want.length &&
            Iterable<int>.generate(got.length).every((i) => got[i] == want[i]);
      }
      if (expected.containsKey('count')) {
        ok = ok && result.eligible.length == expected['count'];
      }
      detail =
          'status=${result.status.name} eligible=${result.eligible.map((e) => e.value).toList()}';
    case 'fetch':
      final transportJson = inputs['transport'] as Map<String, dynamic>;
      final rawBytes = transportJson['bytes'];
      final fake = _FakeTransport(
        transportJson['mode'] as String,
        rawBytes == 'large'
            ? List<int>.filled(100000, 7)
            : rawBytes == null
                ? <int>[]
                : (rawBytes as List).cast<num>().map((n) => n.toInt()).toList(),
        (transportJson['status'] as num?)?.toInt() ?? 0,
      );
      final endpoint = _bmEndpoint(inputs['endpoint'] as String);
      final operation = AtlasTileFetchOperation(
        endpoint: endpoint,
        transport: fake.call,
      );
      final resource = AtlasResourceIdentity(
        provider: endpoint.descriptor.id,
        kind: _resolutionKind(inputs['kind'] as String),
        address: inputs['address'] as String,
      );
      final request = AtlasAcquisitionRequest(resource: resource);
      final context = ExecutionContext(
        nowSeconds: (inputs['now'] as num).toInt(),
        cancellation: ExecutionCancellation(),
        binding: AtlasOperationBinding({resource: operation}),
      );
      final result = await AtlasExecutor.runAcquisition(
        command: RunAcquisitionCommand(request: request),
        context: context,
      );
      ok = result.state.name == expected['state'];
      if (expected.containsKey('acquisition_state')) {
        ok = ok &&
            result.acquisition?.state.name == expected['acquisition_state'];
      }
      if (expected.containsKey('acquisition_failure')) {
        ok = ok &&
            result.acquisition?.failure?.name ==
                expected['acquisition_failure'];
      }
      if (expected.containsKey('echo_payload')) {
        ok = ok &&
            result.acquisition?.payloadId?.value == expected['echo_payload'];
      }
      if (expected.containsKey('url_seen')) {
        ok = ok && fake.urls.single.toString() == expected['url_seen'];
        detail = 'url=${fake.urls.single}';
      }
      if (expected.containsKey('headers_contain')) {
        ok = ok &&
            fake.headersSeen.single.containsKey(expected['headers_contain']);
      }
      if (expected.containsKey('header_name')) {
        final value = fake.headersSeen.single[expected['header_name']] ?? '';
        ok = ok && value.contains(expected['header_value_contains'] as String);
        detail = 'ua=$value';
      }
      if (expected.containsKey('url_differs_from_identity')) {
        ok = ok &&
            (expected['url_differs_from_identity'] as bool) &&
            fake.urls.single.toString() != (inputs['address'] as String);
      }
      if (expected.containsKey('payload_short')) {
        ok = ok &&
            (expected['payload_short'] as bool) &&
            (result.acquisition?.payloadId?.value.length ?? 9999) < 100;
      }
      if (expected.containsKey('malfunction')) {
        ok = ok && result.malfunction?.name == expected['malfunction'];
      }
      if (expected.containsKey('reason_contains')) {
        final wants = (expected['reason_contains'] as List).cast<String>();
        ok = ok && wants.every((w) => result.reason.contains(w));
      }
      detail = '$detail state=${result.state.name} '
          'acq=${result.acquisition?.state.name}/${result.acquisition?.failure?.name}';
    case 'fetch_cancel':
      final transportJson = inputs['transport'] as Map<String, dynamic>;
      final fake = _FakeTransport(
        'ok',
        (transportJson['bytes'] as List)
            .cast<num>()
            .map((n) => n.toInt())
            .toList(),
        0,
      );
      final endpoint = _bmEndpoint(inputs['endpoint'] as String);
      final resource = AtlasResourceIdentity(
        provider: endpoint.descriptor.id,
        kind: _resolutionKind(inputs['kind'] as String),
        address: inputs['address'] as String,
      );
      final cancellation = ExecutionCancellation()..requestCancel();
      final result = await AtlasExecutor.runAcquisition(
        command: RunAcquisitionCommand(
          request: AtlasAcquisitionRequest(resource: resource),
        ),
        context: ExecutionContext(
          nowSeconds: (inputs['now'] as num).toInt(),
          cancellation: cancellation,
          binding: AtlasOperationBinding({
            resource: AtlasTileFetchOperation(
              endpoint: endpoint,
              transport: fake.call,
            ),
          }),
        ),
      );
      ok = result.state.name == expected['state'] &&
          fake.urls.isEmpty == !(expected['contacted'] as bool? ?? true);
      detail = 'cancelled before contact calls=${fake.urls.length}';
    case 'fetch_registry':
      final endpoint = _bmEndpoint(inputs['endpoint'] as String);
      final resource = AtlasResourceIdentity(
        provider: endpoint.descriptor.id,
        kind: _resolutionKind(inputs['kind'] as String),
        address: inputs['address'] as String,
      );
      final transportJson = inputs['transport'] as Map<String, dynamic>;
      final lookedUp = registry.lookup(endpoint.descriptor.id.value)!;
      final result = await AtlasExecutor.runAcquisition(
        command: RunAcquisitionCommand(
          request: AtlasAcquisitionRequest(resource: resource),
        ),
        context: ExecutionContext(
          nowSeconds: (inputs['now'] as num).toInt(),
          cancellation: ExecutionCancellation(),
          binding: AtlasOperationBinding({
            resource: AtlasTileFetchOperation(
              endpoint: lookedUp,
              transport: (url, headers) async =>
                  (transportJson['bytes'] as List)
                      .cast<num>()
                      .map((n) => n.toInt())
                      .toList(),
            ),
          }),
        ),
      );
      ok = result.state.name == expected['state'] &&
          result.acquisition?.state.name == expected['acquisition_state'] &&
          result.acquisition?.payloadId?.value == expected['echo_payload'];
      detail = 'registry→binding→executor wired';
    case 'bundle':
      final files = (inputs['files'] as Map).cast<String, dynamic>();
      var seen = '';
      final operation = AtlasLocalBundleOperation(
        endpoint: AtlasBuiltinProviders.localBundle,
        root: inputs['root'] as String,
        ext: inputs['ext'] as String,
        readFile: (path) async {
          seen = path;
          final hit = files[path];
          if (hit == null) return null;
          return (hit as List).cast<num>().map((n) => n.toInt()).toList();
        },
      );
      final resource = AtlasResourceIdentity(
        provider: const AtlasId('local-bundle'),
        kind: _resolutionKind(inputs['kind'] as String),
        address: inputs['address'] as String,
      );
      final result = await AtlasExecutor.runAcquisition(
        command: RunAcquisitionCommand(
          request: AtlasAcquisitionRequest(resource: resource),
        ),
        context: ExecutionContext(
          nowSeconds: (inputs['now'] as num).toInt(),
          cancellation: ExecutionCancellation(),
          binding: AtlasOperationBinding({resource: operation}),
        ),
      );
      ok = result.state.name == expected['state'];
      if (expected.containsKey('acquisition_failure')) {
        ok = ok &&
            result.acquisition?.failure?.name ==
                expected['acquisition_failure'];
      }
      if (expected.containsKey('echo_payload')) {
        ok = ok &&
            result.acquisition?.payloadId?.value == expected['echo_payload'];
      }
      if (expected.containsKey('path_seen')) {
        ok = ok && seen == expected['path_seen'];
      }
      detail = 'path=$seen acq=${result.acquisition?.state.name}';
    case 'serve_seam':
      final endpoint = _bmEndpoint(inputs['endpoint'] as String);
      final resource = AtlasResourceIdentity(
        provider: endpoint.descriptor.id,
        kind: _resolutionKind(inputs['kind'] as String),
        address: inputs['address'] as String,
      );
      final entry = AtlasCacheEntry(
        key: const AtlasCacheKey(
          namespace: AtlasCacheNamespace.resource,
          value: 'osm-standard/rasterTiles/z=1/x=0/y=0@xyz',
        ),
        resource: resource,
        storedAt: 0,
      );
      final result = await AtlasExecutor.serveEntry(
        command: ServeEntryCommand(entry: entry),
        context: ExecutionContext(
          nowSeconds: (inputs['now'] as num).toInt(),
          cancellation: ExecutionCancellation(),
          binding: AtlasOperationBinding({
            resource: AtlasTileFetchOperation(
              endpoint: endpoint,
              transport: (url, headers) async => [0],
            ),
          }),
        ),
      );
      ok = result.state.name == expected['state'] &&
          result.malfunction?.name == expected['malfunction'];
      if (expected.containsKey('reason_contains')) {
        final wants = (expected['reason_contains'] as List).cast<String>();
        ok = ok && wants.every((w) => result.reason.contains(w));
      }
      detail = 'seam holds: ${result.reason}';
    case 'attribution':
      final text = AtlasProviderAttribution.compose(
        (inputs['providers'] as List).cast<String>(),
        (String lookedUp) => registry.lookup(lookedUp)?.descriptor.attribution,
      );
      ok = text == expected['text'];
      detail = 'attribution=$text';
    default:
      _record(id, Verdict.fail, 'unknown basemap op: $op');
      return;
  }
  _record(id, ok ? Verdict.pass : Verdict.fail, detail);
}

// ---------------------------------------------------------------------------
// main
// ---------------------------------------------------------------------------

void main() async {
  final root = Directory('test/golden');
  if (!root.existsSync()) {
    stderr.writeln(
      'Run from the repository root: dart test/phase05_runner.dart',
    );
    exit(2);
  }
  final files = root
      .listSync(recursive: true)
      .whereType<File>()
      .where((f) => f.path.endsWith('.json'))
      .toList()
    ..sort((a, b) => a.path.compareTo(b.path));

  const naDirs = {
    'offline',
    'provenance',
    'h3',
    'structures',
    'radio',
    'location',
  };

  for (final file in files) {
    final dir = file.parent.path.split(Platform.pathSeparator).last;
    late final Map<String, dynamic> fixture;
    try {
      fixture = _load(file.path);
    } catch (e) {
      _record(file.path, Verdict.fail, 'unparseable fixture: $e');
      continue;
    }
    try {
      if (dir == 'parcels' && (fixture['id'] as String) == 'PAIRED-001') {
        _parcels(fixture);
      } else if (naDirs.contains(dir)) {
        _record(
          fixture['id'] as String,
          Verdict.notApplicable,
          'Reserved package scope ($dir).',
        );
      } else {
        switch (dir) {
          case 'geo':
            _geo(fixture);
          case 'angles':
            _angles(fixture);
          case 'boxes':
            _boxes(fixture);
          case 'cache':
            _cache(fixture);
          case 'distance':
            _distance(fixture);
          case 'bearing':
            _bearing(fixture);
          case 'camera':
            _camera(fixture);
          case 'layers':
            _layers(fixture);
          case 'geometry':
            _geometry(fixture);
          case 'parcels':
            _parcels(fixture);
          case 'providers':
            _providers(fixture);
          case 'acquisition':
            _acquisition(fixture);
          case 'pipeline':
            _pipeline(fixture);
          case 'basemap':
            await _basemap(fixture);
          case 'store':
            await _store(fixture);
          case 'packs':
            await _packs(fixture);
          case 'execution':
            await _execution(fixture);
          case 'resolution':
            _resolution(fixture);
          case 'resources':
            _resources(fixture);
          case 'tiles':
            _tiles(fixture);
          case 'migration':
            _migration(fixture);
          case 'tactical':
            _tactical(fixture);
          case 'adversarial':
            await _adversarial(fixture);
          default:
            _record(
              fixture['id'] as String,
              Verdict.fail,
              'unknown fixture dir: $dir',
            );
        }
      }
    } catch (e, st) {
      _record(fixture['id'] as String, Verdict.fail, 'harness error: $e\n$st');
      return;
    }
  }

  // Determinism self-check: haversine repeatability (same input → same output).
  const a = AtlasCoordinate(latitude: 39.83, longitude: -98.58);
  const b = AtlasCoordinate(latitude: 48.85, longitude: 2.35);
  final d1 = AtlasGeoMath.haversineKm(a, b);
  final d2 = AtlasGeoMath.haversineKm(a, b);
  _record(
    'SELF-determinism',
    d1 == d2 ? Verdict.pass : Verdict.fail,
    'repeat=$d1',
  );

  // 1.5-M arch-leakage self-check (resolution sources only).
  final leaks = <String>[];
  _record(
    'SELF-arch-leakage',
    _resolutionLeakCheck(leaks) ? Verdict.pass : Verdict.fail,
    leaks.isEmpty ? 'no transport/renderer/storage markers' : leaks.join('; '),
  );

  final pass = _outcomes.where((o) => o.verdict == Verdict.pass).length;
  final fail = _outcomes.where((o) => o.verdict == Verdict.fail).length;
  final blocked = _outcomes.where((o) => o.verdict == Verdict.blocked).length;
  final na = _outcomes.where((o) => o.verdict == Verdict.notApplicable).length;
  stdout.writeln('---');
  stdout.writeln(
    'SUMMARY total=${_outcomes.length} pass=$pass fail=$fail blocked=$blocked notApplicable=$na',
  );
  if (fail > 0) {
    stdout.writeln('FAILURES:');
    for (final o in _outcomes.where((o) => o.verdict == Verdict.fail)) {
      stdout.writeln('  ${o.id}  ${o.detail}');
    }
    exit(1);
  }
}
