// Sovereign Atlas — on-device failure-terminal proof (hardening pass).
//
// Drives a real download attempt with the transport blocked and proves the
// honest failed terminal on hardware: failure detail surfaces, nothing is
// indexed, nothing is served, and the app remains usable afterwards.
// (Host tests prove the same shape with throwing sources; this proves it
// through the real engine httpTransport against a dead stack.)
import 'dart:io';

import 'package:atlas/main.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

/// Denies every HTTP client construction (transport-level offline).
final class BlockedTransport extends HttpOverrides {
  @override
  HttpClient createHttpClient(SecurityContext? context) {
    throw const SocketException('transport blocked (offline simulation)');
  }
}

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('blocked transport reaches failed on device', (tester) async {
    HttpOverrides.global = BlockedTransport();
    await tester.pumpWidget(const AtlasApp());
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

    var terminal = '';
    for (var i = 0; i < 30; i++) {
      await tester.pump(const Duration(seconds: 2));
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
    print('OFFLINE_FAILURE_RESULT: $terminal');
    // The transport is dead: success here would be a FABRICATION bug.
    expect(terminal, 'failed');
    expect(find.textContaining('SocketException'), findsWidgets);

    // Nothing indexed, nothing served, Saved Areas honestly empty.
    await tester.tap(find.text('Saved Areas'));
    await tester.pumpAndSettle();
    expect(find.textContaining('Nothing available offline'), findsOneWidget);

    // App remains usable: Diagnostics opens over the failed state.
    await tester.tap(find.text('Storage'));
    await tester.pumpAndSettle();
    expect(find.textContaining('Pack index: 0/64'), findsOneWidget);
  });
}
