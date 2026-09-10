// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

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
