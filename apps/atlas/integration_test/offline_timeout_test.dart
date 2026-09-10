// Sovereign Atlas — forced-timeout device proof (DEC-020).
//
// Deterministic trigger, not a natural stall: the repository is injected
// with a never-completing chunk source and a 2 s per-tile bound, then the
// REAL app flow (navigate → plan → download) must reach the REAL failed
// terminal with TimeoutException identity — proving the mapping on
// hardware. Real socket timing is inherently nondeterministic and is NOT
// claimed; the mechanism is host-proven, the mapping is device-proven.
// Uses the existing AtlasApp(repository:) seam (also used by widget
// tests) — no test hooks in production code.
import 'dart:async';

import 'package:atlas/main.dart';
import 'package:atlas/offline/offline_repository.dart';
import 'package:atlas_providers/atlas_providers.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('forced stall reaches failed with TimeoutException on device',
      (tester) async {
    final repo = OfflineRepository(
      registry: AtlasBuiltinProviders.registry(),
      chunkSourceFactory: (_) => (_) => Completer<List<int>>().future,
      perTileTimeout: const Duration(seconds: 2),
    );
    await tester.pumpWidget(AtlasApp(repository: repo));
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('offline-areas'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('new-pack'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('provider-dropdown')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Esri World Imagery').last);
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('bytes-per-tile')),
      '20000',
    );
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('plan-pack')));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('download-pack')), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('download-pack')));
    await tester.pumpAndSettle();

    // Real seconds must pass for the real 2 s bound to fire (fake-clock
    // pumps alone cannot trip a real timer).
    var terminal = '';
    for (var i = 0; i < 20; i++) {
      await tester.pump(const Duration(seconds: 1));
      await Future<void>.delayed(const Duration(seconds: 1));
      if (find.text('failed').evaluate().isNotEmpty) {
        terminal = 'failed';
        break;
      }
      if (find.text('complete').evaluate().isNotEmpty) {
        terminal = 'complete';
        break;
      }
    }
    // ignore: avoid_print
    print('OFFLINE_TIMEOUT_RESULT: $terminal');
    // Success against a never-answering source would be fabrication.
    expect(terminal, 'failed');
    expect(find.textContaining('TimeoutException'), findsWidgets);

    // Nothing indexed, nothing served, app fully usable afterwards.
    await tester.tap(find.text('Saved Areas'));
    await tester.pumpAndSettle();
    expect(find.textContaining('Nothing available offline'), findsOneWidget);
    await tester.tap(find.text('Storage'));
    await tester.pumpAndSettle();
    expect(find.textContaining('Pack index: 0/64'), findsOneWidget);
  });
}
