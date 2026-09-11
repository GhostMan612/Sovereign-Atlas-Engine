// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'dart:math' as math;

import 'package:flutter/widgets.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:atlas/location/heading_service.dart';
import 'package:atlas/main.dart';

import 'fake_heading_source.dart';

HeadingService scopedHeading(FakeHeadingSource fake) {
  final service = HeadingService(source: fake);
  addTearDown(service.dispose);
  return service;
}

Future<void> pumpCompass(
  WidgetTester tester,
  HeadingService service,
) async {
  await tester.pumpWidget(AtlasApp(headingService: service));
  await tester.pumpAndSettle();
}

Future<void> tapHeadingUp(WidgetTester tester) async {
  await tester.tap(find.byTooltip('heading-up'));
  await tester.pumpAndSettle();
}

Future<void> emitHeading(
  WidgetTester tester,
  FakeHeadingSource fake,
  HeadingSample sample,
) async {
  fake.emit(sample);
  await tester.idle();
  await tester.pump();
}

Matrix4 compassMatrix(WidgetTester tester) {
  return tester
      .widget<Transform>(
        find.descendant(
          of: find.byTooltip('compass'),
          matching: find.byType(Transform),
        ),
      )
      .transform;
}

void expectDialDegrees(WidgetTester tester, double degrees) {
  final expected = -degrees * math.pi / 180.0;
  final matrix = compassMatrix(tester);
  expect(matrix.entry(0, 0), moreOrLessEquals(math.cos(expected)));
  expect(matrix.entry(1, 0), moreOrLessEquals(math.sin(expected)));
}

double mapRotation(WidgetTester tester) {
  return tester
      .widget<FlutterMap>(find.byType(FlutterMap))
      .mapController!
      .camera
      .rotation;
}

void main() {
  testWidgets('no compass while heading is idle', (
    WidgetTester tester,
  ) async {
    final service = scopedHeading(FakeHeadingSource());
    await pumpCompass(tester, service);
    expect(find.byTooltip('heading-up'), findsOneWidget);
    expect(find.byTooltip('compass'), findsNothing);
    expect(find.textContaining('orient off'), findsOneWidget);
  });

  testWidgets('compass appears with true frame and tracking dial', (
    WidgetTester tester,
  ) async {
    final fake = FakeHeadingSource();
    final service = scopedHeading(fake);
    await pumpCompass(tester, service);
    await tapHeadingUp(tester);
    await emitHeading(
      tester,
      fake,
      testSample(magnetic: 90.0, trueNorth: 95.0),
    );
    expect(find.byTooltip('compass'), findsOneWidget);
    expect(find.text('TRUE'), findsOneWidget);
    expectDialDegrees(tester, 95.0);
    expect(find.textContaining('orient heading-up'), findsOneWidget);
  });

  testWidgets('missing true north labels the dial MAG', (
    WidgetTester tester,
  ) async {
    final fake = FakeHeadingSource();
    final service = scopedHeading(fake);
    await pumpCompass(tester, service);
    await tapHeadingUp(tester);
    await emitHeading(
      tester,
      fake,
      testSample(magnetic: 90.0, trueNorth: null),
    );
    expect(find.text('MAG'), findsOneWidget);
    expectDialDegrees(tester, 90.0);
  });

  testWidgets('unreliable accuracy dims the compass', (
    WidgetTester tester,
  ) async {
    final fake = FakeHeadingSource();
    final service = scopedHeading(fake);
    await pumpCompass(tester, service);
    await tapHeadingUp(tester);
    await emitHeading(
      tester,
      fake,
      testSample(accuracy: HeadingAccuracy.unreliable),
    );
    final dimmed = tester.widget<Opacity>(
      find.descendant(
        of: find.byTooltip('compass'),
        matching: find.byType(Opacity),
      ),
    );
    expect(dimmed.opacity, 0.4);
  });

  testWidgets('heading-up rotates the map without moving its center', (
    WidgetTester tester,
  ) async {
    final fake = FakeHeadingSource();
    final service = scopedHeading(fake);
    await pumpCompass(tester, service);
    await tapHeadingUp(tester);
    await emitHeading(
      tester,
      fake,
      testSample(magnetic: 90.0, trueNorth: 90.0),
    );
    expect(mapRotation(tester), 270.0);
    expect(find.textContaining('MAP lat 0.0000'), findsOneWidget);
    await emitHeading(
      tester,
      fake,
      testSample(magnetic: 180.0, trueNorth: 180.0),
    );
    expect(mapRotation(tester), 180.0);
    expect(find.textContaining('MAP lat 0.0000'), findsOneWidget);
  });

  testWidgets('tapping the compass faces north', (
    WidgetTester tester,
  ) async {
    final fake = FakeHeadingSource();
    final service = scopedHeading(fake);
    await pumpCompass(tester, service);
    await tapHeadingUp(tester);
    await emitHeading(
      tester,
      fake,
      testSample(magnetic: 90.0, trueNorth: 90.0),
    );
    expect(mapRotation(tester), 270.0);
    await tester.tap(find.byTooltip('compass'));
    await tester.pumpAndSettle();
    expect(mapRotation(tester), 0.0);
    expect(find.textContaining('orient north-up'), findsOneWidget);
  });

  testWidgets('leaving heading-up restores north-up', (
    WidgetTester tester,
  ) async {
    final fake = FakeHeadingSource();
    final service = scopedHeading(fake);
    await pumpCompass(tester, service);
    await tapHeadingUp(tester);
    await emitHeading(
      tester,
      fake,
      testSample(magnetic: 90.0, trueNorth: 90.0),
    );
    expect(mapRotation(tester), 270.0);
    await tapHeadingUp(tester);
    expect(mapRotation(tester), 0.0);
    expect(find.textContaining('orient north-up'), findsOneWidget);
  });

  testWidgets('pending heading-up engages on the first sample', (
    WidgetTester tester,
  ) async {
    final fake = FakeHeadingSource();
    final service = scopedHeading(fake);
    await pumpCompass(tester, service);
    await tapHeadingUp(tester);
    expect(find.textContaining('orient off'), findsOneWidget);
    await emitHeading(
      tester,
      fake,
      testSample(magnetic: 45.0, trueNorth: 45.0),
    );
    expect(mapRotation(tester), 315.0);
    expect(find.textContaining('orient heading-up'), findsOneWidget);
  });

  testWidgets('unsupported sensor stays honest with no compass', (
    WidgetTester tester,
  ) async {
    final fake = FakeHeadingSource()..supportedResult = false;
    final service = scopedHeading(fake);
    await pumpCompass(tester, service);
    await tapHeadingUp(tester);
    expect(find.textContaining('orient unsupported'), findsOneWidget);
    expect(find.byTooltip('compass'), findsNothing);
    expect(mapRotation(tester), 0.0);
    expect(fake.samplesCalls, 0);
  });

  testWidgets('rebuilds and repeat toggles never duplicate heading', (
    WidgetTester tester,
  ) async {
    final fake = FakeHeadingSource();
    final service = scopedHeading(fake);
    await pumpCompass(tester, service);
    await tapHeadingUp(tester);
    await emitHeading(tester, fake, testSample());
    await pumpCompass(tester, service);
    await tapHeadingUp(tester);
    await tapHeadingUp(tester);
    expect(fake.samplesCalls, 1);
  });
}
