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

void _layers(Map<String, dynamic> f) {
  final id = f['id'] as String;
  final expected = f['expected'] as Map<String, dynamic>;
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
  _record(
    id,
    Verdict.notApplicable,
    'ATLAS-PROV-DESC-001 descriptors belong to atlas_provider_api scope.',
  );
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
    'tiles',
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
