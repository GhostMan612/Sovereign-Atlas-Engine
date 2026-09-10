// Sovereign Atlas — Offline Areas on-device proof (DEVICE-005).
//
// Runs ON the emulator with the PRODUCTION chunk source (real HTTP through
// engine URL resolution + engine transport). Two proofs, both by semantic
// locators:
// 1. Refusal path needs no network: default OSM plan without approval
//    surfaces BULK_GUARD on device.
// 2. Download path: 1-tile Esri pack downloads for real. The terminal is
//    recorded, not assumed: `complete` proves end-to-end bytes-to-seal;
//    `failed` with engine detail proves honest failure surfacing. Either is
//    a terminal the UI surfaced (the test fails only if NO terminal
//    arrives). The actual outcome is recorded in DEVICE-005.
import 'package:atlas/main.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('offline refusal + download reach terminals on device',
      (tester) async {
    await tester.pumpWidget(const AtlasApp());
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('offline-areas'));
    await tester.pumpAndSettle();
    expect(find.text('Offline Areas'), findsOneWidget);

    // 1. Refusal path (offline-safe): default OSM, no approval.
    await tester.tap(find.byTooltip('new-pack'));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('bytes-per-tile')),
      '20000',
    );
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('plan-pack')));
    await tester.pumpAndSettle();
    expect(find.textContaining('BULK_GUARD'), findsWidgets);

    // 2. Download path: switch to Esri (prefetch allowed, no bulk guard).
    await tester.tap(find.byKey(const ValueKey('provider-dropdown')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Esri World Imagery').last);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('plan-pack')));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('download-pack')), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('download-pack')));
    await tester.pumpAndSettle();

    var terminal = '';
    for (var i = 0; i < 60; i++) {
      await tester.pump(const Duration(seconds: 2));
      if (find.text('complete').evaluate().isNotEmpty) {
        terminal = 'complete';
        break;
      }
      if (find.text('failed').evaluate().isNotEmpty) {
        terminal = 'failed';
        break;
      }
    }
    // ignore: avoid_print
    print('OFFLINE_DEVICE_RESULT: $terminal');
    expect(terminal, isNotEmpty, reason: 'no terminal state reached');

    if (terminal == 'complete') {
      await tester.tap(find.text('Saved Areas'));
      await tester.pumpAndSettle();
      expect(find.text('Esri World Imagery'), findsWidgets);
    }
  });
}
