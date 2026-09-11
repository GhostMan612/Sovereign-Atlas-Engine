// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_location/atlas_location.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:latlong2/latlong.dart';

import 'package:atlas/location/location_service.dart';
import 'package:atlas/main.dart';

import 'fake_location_source.dart';

Future<void> pumpAtlas(
  WidgetTester tester,
  LocationService service,
) async {
  await tester.pumpWidget(AtlasApp(locationService: service));
  await tester.pumpAndSettle();
}

LocationService scopedService(FakeLocationSource fake) {
  final service = LocationService(locationSource: fake);
  addTearDown(service.dispose);
  return service;
}

Future<void> tapLocate(WidgetTester tester) async {
  await tester.tap(find.byTooltip('my-location'));
  await tester.pumpAndSettle();
}

Future<void> emitFix(
  WidgetTester tester,
  FakeLocationSource fake,
  AtlasLocationFix fix,
) async {
  fake.emit(fix);
  await tester.idle();
  await tester.pump();
}

double mapRotation(WidgetTester tester) {
  return tester
      .widget<FlutterMap>(find.byType(FlutterMap))
      .mapController!
      .camera
      .rotation;
}

Marker? positionMarker(WidgetTester tester) {
  final markers =
      tester.widget<MarkerLayer>(find.byType(MarkerLayer)).markers;
  for (final marker in markers) {
    if (marker.key == const ValueKey<String>('position-marker')) {
      return marker;
    }
  }
  return null;
}

void main() {
  testWidgets('no position marker without a valid fix', (
    WidgetTester tester,
  ) async {
    final service = scopedService( FakeLocationSource());
    await pumpAtlas(tester, service);
    expect(find.byTooltip('my-location'), findsOneWidget);
    expect(positionMarker(tester), isNull);
    expect(find.byType(CircleLayer), findsNothing);
    expect(find.text('DEVICE not-requested'), findsOneWidget);
  });

  testWidgets('denied permission renders no marker', (
    WidgetTester tester,
  ) async {
    final fake = FakeLocationSource()
      ..requestResult = const AtlasLocationQuery(
        permission: AtlasLocationPermission.denied,
        servicesEnabled: true,
      );
    final service = scopedService(fake);
    await pumpAtlas(tester, service);
    await tapLocate(tester);
    expect(find.text('DEVICE denied'), findsOneWidget);
    expect(positionMarker(tester), isNull);
  });

  testWidgets('position marker follows fake fixes', (
    WidgetTester tester,
  ) async {
    final fake = FakeLocationSource()..queryResult = grantedQuery;
    final service = scopedService(fake);
    await pumpAtlas(tester, service);
    await tapLocate(tester);
    expect(service.status, AtlasLocationStatus.acquiring);
    expect(fake.fixesCalls, 1);
    await emitFix(tester, fake, testFix(latitude: 10.0, longitude: 20.0));
    expect(positionMarker(tester)?.point, const LatLng(10.0, 20.0));
    await emitFix(tester, fake, testFix(latitude: 11.0, longitude: 21.0));
    expect(positionMarker(tester)?.point, const LatLng(11.0, 21.0));
    service.stop();
  });

  testWidgets('known accuracy renders a meter circle', (
    WidgetTester tester,
  ) async {
    final fake = FakeLocationSource()
      ..queryResult = grantedQuery
      ..seedResult = testFix(accuracy: 4.5);
    final service = scopedService(fake);
    await pumpAtlas(tester, service);
    await tapLocate(tester);
    expect(find.byType(CircleLayer), findsOneWidget);
    final layer = tester.widget<CircleLayer>(find.byType(CircleLayer));
    expect(layer.circles.single.radius, 4.5);
    expect(layer.circles.single.useRadiusInMeter, isTrue);
    service.stop();
  });

  testWidgets('null accuracy keeps UNKNOWN and no circle', (
    WidgetTester tester,
  ) async {
    final fake = FakeLocationSource()
      ..queryResult = grantedQuery
      ..seedResult = testFix(accuracy: null);
    final service = scopedService(fake);
    await pumpAtlas(tester, service);
    await tapLocate(tester);
    expect(positionMarker(tester)?.point, const LatLng(10.0, 20.0));
    expect(find.byType(CircleLayer), findsNothing);
    expect(find.textContaining('UNKNOWN'), findsOneWidget);
    service.stop();
  });

  testWidgets('recenter moves to the latest valid fix', (
    WidgetTester tester,
  ) async {
    final fake = FakeLocationSource()
      ..queryResult = grantedQuery
      ..seedResult = testFix(latitude: 10.0, longitude: 20.0);
    final service = scopedService(fake);
    await pumpAtlas(tester, service);
    await tapLocate(tester);
    expect(find.textContaining('MAP lat 10.0000'), findsOneWidget);
    expect(find.textContaining('DEVICE valid'), findsOneWidget);
    service.stop();
  });

  testWidgets('recenter with no fix leaves the camera untouched', (
    WidgetTester tester,
  ) async {
    final fake = FakeLocationSource()..queryResult = grantedQuery;
    final service = scopedService(fake);
    await pumpAtlas(tester, service);
    await tapLocate(tester);
    expect(find.textContaining('MAP lat 0.0000'), findsOneWidget);
    expect(find.text('DEVICE acquiring'), findsOneWidget);
    expect(positionMarker(tester), isNull);
  });

  testWidgets('location updates never rotate the map', (
    WidgetTester tester,
  ) async {
    final fake = FakeLocationSource()
      ..queryResult = grantedQuery
      ..seedResult = testFix();
    final service = scopedService(fake);
    await pumpAtlas(tester, service);
    await tapLocate(tester);
    await emitFix(tester, fake, testFix(latitude: 12.0, longitude: 22.0));
    await tapLocate(tester);
    expect(mapRotation(tester), 0.0);
    service.stop();
  });

  testWidgets('rebuilds and repeat taps never duplicate acquisition', (
    WidgetTester tester,
  ) async {
    final fake = FakeLocationSource()
      ..queryResult = grantedQuery
      ..seedResult = testFix();
    final service = scopedService(fake);
    await pumpAtlas(tester, service);
    await tapLocate(tester);
    await pumpAtlas(tester, service);
    await tapLocate(tester);
    expect(fake.fixesCalls, 1);
    service.stop();
  });

  testWidgets('readout distinguishes device position from map center', (
    WidgetTester tester,
  ) async {
    final fake = FakeLocationSource()
      ..queryResult = grantedQuery
      ..seedResult = testFix(latitude: 10.0, longitude: 20.0);
    final service = scopedService(fake);
    await pumpAtlas(tester, service);
    await tapLocate(tester);
    expect(find.textContaining('MAP lat 10.0000'), findsOneWidget);
    expect(find.textContaining('DEVICE valid lat 10.0000'), findsOneWidget);
    service.stop();
  });
}
