// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:atlas/location/heading_service.dart';
import 'package:atlas/location/location_service.dart';
import 'package:atlas/main.dart';
import 'package:atlas/measure/measure_state.dart';

import 'fake_heading_source.dart';
import 'fake_location_source.dart';

Future<void> pumpAtlas(
  WidgetTester tester, {
  LocationService? location,
  HeadingService? heading,
}) async {
  await tester.pumpWidget(
    AtlasApp(locationService: location, headingService: heading),
  );
  await tester.pumpAndSettle();
}

Future<void> enterMeasure(WidgetTester tester) async {
  await tester.tap(find.byTooltip('measure'));
  await tester.pumpAndSettle();
}

Future<void> tapMap(
  WidgetTester tester, [
  Offset delta = const Offset(120.0, 16.0),
]) async {
  final origin = tester.getTopLeft(find.byType(FlutterMap));
  await tester.tapAt(origin + delta);
  await tester.pump(const Duration(milliseconds: 500));
}

Future<void> closeSheet(WidgetTester tester) async {
  await tester.tap(find.byKey(const ValueKey<String>('measure-close')));
  await tester.pumpAndSettle();
}

double mapRotation(WidgetTester tester) {
  return tester
      .widget<FlutterMap>(find.byType(FlutterMap))
      .mapController!
      .camera
      .rotation;
}

int polylineCount(WidgetTester tester) {
  final layers = tester.widgetList<PolylineLayer>(find.byType(PolylineLayer));
  return layers.fold<int>(0, (sum, layer) => sum + layer.polylines.length);
}

void main() {
  testWidgets('measure mode opens the result sheet', (
    WidgetTester tester,
  ) async {
    await pumpAtlas(tester);
    await enterMeasure(tester);
    expect(find.text('Measure'), findsOneWidget);
    expect(find.textContaining('tap the map to set point B'), findsOneWidget);
  });

  testWidgets('point A defaults to labeled map center without a fix', (
    WidgetTester tester,
  ) async {
    await pumpAtlas(tester);
    await enterMeasure(tester);
    expect(find.textContaining('map center'), findsOneWidget);
    expect(find.textContaining('GPS'), findsNothing);
  });

  testWidgets('point A uses the valid fix labeled GPS', (
    WidgetTester tester,
  ) async {
    final fake = FakeLocationSource()
      ..queryResult = grantedQuery
      ..seedResult = testFix();
    final service = LocationService(locationSource: fake);
    await pumpAtlas(tester, location: service);
    await tester.tap(find.byTooltip('my-location'));
    await tester.pumpAndSettle();
    await enterMeasure(tester);
    expect(find.textContaining('A (GPS): 10.0000, 20.0000'), findsOneWidget);
    service.stop();
  });

  testWidgets('map tap captures point B and draws the polyline', (
    WidgetTester tester,
  ) async {
    await pumpAtlas(tester);
    await enterMeasure(tester);
    expect(polylineCount(tester), 0);
    await tapMap(tester);
    expect(polylineCount(tester), 1);
    expect(find.textContaining('B: '), findsOneWidget);
  });

  testWidgets('distance and bearing render after B is set', (
    WidgetTester tester,
  ) async {
    await pumpAtlas(tester);
    await enterMeasure(tester);
    await tapMap(tester);
    expect(find.textContaining('Distance: '), findsOneWidget);
    expect(find.textContaining('BRG '), findsOneWidget);
    expect(find.textContaining('°'), findsWidgets);
  });

  testWidgets('dismissal clears state and polyline', (
    WidgetTester tester,
  ) async {
    await pumpAtlas(tester);
    await enterMeasure(tester);
    await tapMap(tester);
    expect(polylineCount(tester), 1);
    await closeSheet(tester);
    expect(find.text('Measure'), findsNothing);
    expect(polylineCount(tester), 0);
    await enterMeasure(tester);
    expect(find.textContaining('tap the map to set point B'), findsOneWidget);
    expect(polylineCount(tester), 0);
  });

  testWidgets('clear button clears and closes', (
    WidgetTester tester,
  ) async {
    await pumpAtlas(tester);
    await enterMeasure(tester);
    await tapMap(tester);
    expect(polylineCount(tester), 1);
    await tester.tap(find.byKey(const ValueKey<String>('measure-clear')));
    await tester.pumpAndSettle();
    expect(find.text('Measure'), findsNothing);
    expect(polylineCount(tester), 0);
  });

  testWidgets('measurement never rotates the camera', (
    WidgetTester tester,
  ) async {
    await pumpAtlas(tester);
    await enterMeasure(tester);
    await tapMap(tester);
    await tapMap(tester, const Offset(200.0, 24.0));
    expect(mapRotation(tester), 0.0);
    expect(find.textContaining('MAP lat 0.0000'), findsOneWidget);
  });

  testWidgets('measurement leaves heading state alone', (
    WidgetTester tester,
  ) async {
    await pumpAtlas(tester);
    await enterMeasure(tester);
    await tapMap(tester);
    expect(find.textContaining('orient off'), findsOneWidget);
  });

  testWidgets('re-tapping the map moves point B', (
    WidgetTester tester,
  ) async {
    await pumpAtlas(tester);
    await enterMeasure(tester);
    await tapMap(tester);
    final first = tester.widget<Text>(find.textContaining('B: ')).data;
    await tapMap(tester, const Offset(220.0, 32.0));
    final second = tester.widget<Text>(find.textContaining('B: ')).data;
    expect(first, isNotNull);
    expect(second, isNotNull);
    expect(second, isNot(equals(first)));
    expect(polylineCount(tester), 1);
  });

  testWidgets('unit selection changes the displayed unit', (
    WidgetTester tester,
  ) async {
    await pumpAtlas(tester);
    await enterMeasure(tester);
    await tapMap(tester);
    expect(find.textContaining(' m'), findsWidgets);
    await tester.tap(find.byType(DropdownButton<MeasureUnit>));
    await tester.pumpAndSettle();
    await tester.tap(find.text('mi').last);
    await tester.pumpAndSettle();
    expect(find.textContaining(' mi'), findsWidgets);
  });

  testWidgets('repeated entry and dismissal stays singular', (
    WidgetTester tester,
  ) async {
    await pumpAtlas(tester);
    for (var i = 0; i < 3; i++) {
      await enterMeasure(tester);
      await tapMap(tester);
      expect(polylineCount(tester), 1);
      await closeSheet(tester);
    }
    expect(polylineCount(tester), 0);
    expect(find.text('Measure'), findsNothing);
  });

  testWidgets('no measurement survives an app restart', (
    WidgetTester tester,
  ) async {
    await pumpAtlas(tester);
    await enterMeasure(tester);
    await tapMap(tester);
    expect(polylineCount(tester), 1);
    await tester.pumpWidget(Container());
    await tester.pumpAndSettle();
    await pumpAtlas(tester);
    expect(polylineCount(tester), 0);
    expect(find.text('Measure'), findsNothing);
  });

  testWidgets('slice 1 location flow still works beside measure', (
    WidgetTester tester,
  ) async {
    final fake = FakeLocationSource()
      ..queryResult = grantedQuery
      ..seedResult = testFix();
    final service = LocationService(locationSource: fake);
    await pumpAtlas(tester, location: service);
    expect(find.byTooltip('measure'), findsOneWidget);
    await tester.tap(find.byTooltip('my-location'));
    await tester.pumpAndSettle();
    expect(find.textContaining('DEVICE valid'), findsOneWidget);
    service.stop();
  });

  testWidgets('measurement composes with heading-up without interference', (
    WidgetTester tester,
  ) async {
    final fakeHeading = FakeHeadingSource();
    final heading = HeadingService(source: fakeHeading);
    await pumpAtlas(tester, heading: heading);
    await tester.tap(find.byTooltip('heading-up'));
    await tester.pumpAndSettle();
    fakeHeading.emit(testSample(magnetic: 90.0, trueNorth: 90.0));
    await tester.idle();
    await tester.pump();
    expect(mapRotation(tester), 270.0);
    await enterMeasure(tester);
    await tapMap(tester);
    expect(polylineCount(tester), 1);
    expect(mapRotation(tester), 270.0);
    expect(find.textContaining('orient heading-up'), findsOneWidget);
  });
}
