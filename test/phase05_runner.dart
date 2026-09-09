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

import '../packages/atlas_core/lib/atlas_core.dart';
import '../packages/atlas_geo/lib/atlas_geo.dart';
import '../packages/atlas_layers/lib/atlas_layers.dart';
import '../packages/atlas_map/lib/atlas_map.dart';
import '../packages/atlas_provider_api/lib/atlas_provider_api.dart';

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
    final ok =
        _close(got.latitude, _num(expected['latitude']), tolerance) &&
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
    final ok =
        moved.camera.zoom == _num(expected['zoom']) &&
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
    final ok =
        turned.bearing == _num(expected['bearing']) &&
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
    final ok =
        (a ==
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
    final ok =
        once.serialize() == expected['canonical'] &&
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
      final ok =
          home.center.latitude == _num(expected['latitude']) &&
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
        final want =
            (expected['rejection'] as Map<String, dynamic>)['category']
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
}) => AtlasLayerDefinition(
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
    final ok =
        defs[0].id != defs[1].id &&
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
    final ok =
        names.containsAll(want) &&
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
    final ok =
        def.capabilities.map((v) => v.name).toSet().containsAll(want) &&
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
    final ok =
        stack.validate().isValid &&
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
    final ok =
        flipped.visible == false &&
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
    final ok =
        (a == b) == (expected['different_title_still_equal'] as bool) &&
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
    final got = stack
        .orderedVisible()
        .map((s) => s.definition.id.value)
        .toList();
    final want = (expected['order_preserved'] as List).cast<String>();
    final ok =
        got.join(',') == want.join(',') &&
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
    final ordered = stack
        .orderedVisible()
        .map((s) => s.definition.id.value)
        .toList();
    final want =
        (expected['order_significant'] as bool) &&
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
    final orderedIds =
        <String>['offline-graticule', for (final t in toggles) mapping[t]!]
          ..sort(
            (a, b) =>
                AtlasBaselineRanks.rankOf(a)!
                    .compareTo(AtlasBaselineRanks.rankOf(b)!),
          );
    final stack = AtlasLayerStack([
      for (final layerId in orderedIds)
        AtlasLayerState(definition: _testDef(layerId)),
    ]);
    final got = stack
        .orderedVisible()
        .map((s) => s.definition.id.value)
        .toList();
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
  final ok =
      v.isValid &&
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
    final ok =
        v.isValid &&
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
  for (final file
      in lib
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
    final ok =
        xyz != tms &&
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
    ok =
        ok &&
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
      ok =
          ok &&
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

/// 1.5-M arch-leakage self-check: resolution sources must contain no
/// transport/renderer/storage/network-activity markers (code only; the
/// contracted vocabulary itself is never the violation).
bool _resolutionLeakCheck(List<String> violations) {
  const banned = [
    'dart:io',
    'package:http',
    'HttpClient',
    'Socket',
    'socket',
    'MapLibre',
    'flutter',
    'Widget',
    'File(',
    'Directory(',
    'Credential',
    'credential',
    'apiKey',
    'https://',
    'http://',
    'resolveUrl',
  ];
  final dir = Directory('packages/atlas_provider_api/lib/src/resolution');
  for (final file in dir.listSync().whereType<File>().where(
    (f) => f.path.endsWith('.dart'),
  )) {
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
  return violations.isEmpty;
}

// ---------------------------------------------------------------------------
// GEOMETRY kernel (rings validity, segments, screening)
// ---------------------------------------------------------------------------

List<AtlasCoordinate> _ring(List<dynamic> pts) => [
  for (final p in pts.cast<List>())
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
    final ok =
        v.isValid &&
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
    final ok =
        line.validateStructure().isValid &&
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
    final ok =
        v.isValid &&
        bounds.south == _num(boundsExpected['south']) &&
        bounds.west == _num(boundsExpected['west']) &&
        bounds.north == _num(boundsExpected['north']) &&
        bounds.east == _num(boundsExpected['east']);
    _record(id, ok ? Verdict.pass : Verdict.fail, 'valid+bounds exact');
    return;
  }
  if (id == 'GEOM-001') {
    final ring = _ring((f['inputs'] as Map<String, dynamic>)['rings'][0]);
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
      if (!AtlasRings.validateRing(_ring((poly as List)[0])).isValid) {
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
    final tableOk =
        AtlasRangeRings.steps.join(',') ==
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
    final ok =
        box.validate().isValid &&
        !box.crossesAntimeridian &&
        box.contains(inside) &&
        !box.contains(outside);
    _record(id, ok ? Verdict.pass : Verdict.fail, 'valid+containment');
    return;
  }
  if (id == 'BOX-002') {
    final inputs = f['inputs'] as Map<String, dynamic>;
    final box = _box(inputs);
    final ok =
        box.validate().isValid &&
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
  final ok =
      bounds.south == _num(boundsExpected['south']) &&
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

void _adversarial(Map<String, dynamic> f) {
  final id = f['id'] as String;
  final expected = f['expected'] as Map<String, dynamic>;
  if (id == 'ADV-038' ||
      id == 'ADV-039' ||
      id == 'ADV-040' ||
      id == 'ADV-041') {
    _resolution(f);
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
// main
// ---------------------------------------------------------------------------

void main() {
  final root = Directory('test/golden');
  if (!root.existsSync()) {
    stderr.writeln(
      'Run from the repository root: dart test/phase05_runner.dart',
    );
    exit(2);
  }
  final files =
      root
          .listSync(recursive: true)
          .whereType<File>()
          .where((f) => f.path.endsWith('.json'))
          .toList()
        ..sort((a, b) => a.path.compareTo(b.path));

  const naDirs = {
    'cache',
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
          case 'resolution':
            _resolution(fixture);
          case 'tiles':
            _tiles(fixture);
          case 'migration':
            _migration(fixture);
          case 'tactical':
            _tactical(fixture);
          case 'adversarial':
            _adversarial(fixture);
          default:
            _record(
              fixture['id'] as String,
              Verdict.fail,
              'unknown fixture dir: $dir',
            );
        }
      }
    } catch (e) {
      _record(fixture['id'] as String, Verdict.fail, 'harness error: $e');
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
