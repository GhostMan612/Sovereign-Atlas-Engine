// Sovereign Atlas — offline rendering proof (DEVICE-006).
//
// Closes the chain: download → seal → store → resolve locally → transport
// blocked → render from local data. Two in-process phases (one file = one
// device install, so the disk journal survives between them):
//   A. Acquire: real 4-tile Esri pack (z2 x1-2 y1-2 = viewport center at
//      the map's initial 0,0/z2) via the production chunk source.
//   B. Relaunch simulation: FRESH repository + restore() (no download),
//      transport blocked at the HttpClient boundary, image cache evicted,
//      map pumped. Local tiles MUST render (offlineTileHits > 0) while
//      edge misses provably cannot reach the network (networkTileRequests
//      > 0, every one of them hitting the blocked stack and degrading to
//      transparent — never crashing, never faking a tile).
//
// Scope honesty: the block is transport-level (HttpOverrides denies ALL
// dart:io HttpClient construction, which package:http IOClient uses), not
// radio-off. For the map renderer the two are indistinguishable: zero
// network bytes can flow. Radio state is out of the renderer's observable
// universe.
import 'dart:io';

import 'package:atlas/main.dart';
import 'package:atlas/offline/offline_repository.dart';
import 'package:atlas_providers/atlas_providers.dart';
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

Future<void> switchToSatellite(WidgetTester tester) async {
  await tester.tap(find.byTooltip('Basemap'));
  await tester.pumpAndSettle();
  await tester.tap(find.text('Satellite'));
  await tester.pumpAndSettle();
}

Future<void> waitForText(
  WidgetTester tester,
  String text, {
  String? orText,
}) async {
  var seen = '';
  for (var i = 0; i < 60; i++) {
    await tester.pump(const Duration(seconds: 2));
    if (find.text(text).evaluate().isNotEmpty) {
      seen = text;
      break;
    }
    if (orText != null && find.text(orText).evaluate().isNotEmpty) {
      seen = orText;
      break;
    }
  }
  expect(seen, isNotEmpty, reason: 'no terminal for $text/$orText');
}

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('A: acquire a real center-viewport pack', (tester) async {
    final repo = OfflineRepository(
      registry: AtlasBuiltinProviders.registry(),
    );
    await tester.pumpWidget(AtlasApp(repository: repo));
    await tester.pumpAndSettle();

    await switchToSatellite(tester);

    await tester.tap(find.byTooltip('offline-areas'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('new-pack'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('provider-dropdown')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Esri World Imagery').last);
    await tester.pumpAndSettle();
    // Exact center 2x2 at z2 (all six bounds explicit: the planner
    // enumerates the full prism, so maxes alone would over-plan).
    await tester.enterText(find.byKey(const ValueKey('zmin')), '2');
    await tester.enterText(find.byKey(const ValueKey('zmax')), '2');
    await tester.enterText(find.byKey(const ValueKey('xmin')), '1');
    await tester.enterText(find.byKey(const ValueKey('xmax')), '2');
    await tester.enterText(find.byKey(const ValueKey('ymin')), '1');
    await tester.enterText(find.byKey(const ValueKey('ymax')), '2');
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

    await waitForText(tester, 'complete', orText: 'failed');
    final completed = repo.packs
        .where((p) => p.lifecycle.name == 'complete')
        .toList();
    // ignore: avoid_print
    print('OFFLINE_RENDER_PACK: ${completed.length} complete, '
        '${completed.isEmpty ? 0 : completed.first.tileCount} tiles');
    expect(completed.length, 1);
    expect(completed.first.tileCount, 4);
    expect(completed.first.seal, isNotNull);
  });

  testWidgets('B: relaunch from disk, render with transport blocked',
      (tester) async {
    // Fresh process-state: new repository, no download, journal only.
    final repo = OfflineRepository(
      registry: AtlasBuiltinProviders.registry(),
    );
    await repo.restore();
    expect(repo.packs.length, 1,
        reason: 'journal did not survive (uninstall between tests?)');
    // Pack coverage, renderer-independent and deterministic.
    for (final key in ['2/1/1', '2/2/1', '2/1/2', '2/2/2']) {
      expect(repo.resolveTileBytes('esri-imagery', key), isNotNull);
    }
    final baseHits = repo.offlineTileHits;

    await tester.pumpWidget(AtlasApp(repository: repo));
    await tester.pumpAndSettle();
    // Fresh pump lands on the default OSM layer: switch to the packed
    // provider (Satellite) so layer and pack agree.
    await switchToSatellite(tester);

    // NOW kill the transport, evict decoded images, and force a full
    // re-resolution by cycling the layer (Satellite→Dark→Satellite):
    // every tile resolves fresh with zero network bytes possible.
    HttpOverrides.global = BlockedTransport();
    PaintingBinding.instance.imageCache.clear();
    await tester.tap(find.byTooltip('Basemap'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Dark'));
    await tester.pumpAndSettle();
    PaintingBinding.instance.imageCache.clear();
    await switchToSatellite(tester);
    await tester.pumpAndSettle();

    final renderHits = repo.offlineTileHits - baseHits;
    // ignore: avoid_print
    print('OFFLINE_RENDER_RESULT: renderHits=$renderHits '
        'network=${repo.networkTileRequests}');
    expect(renderHits, greaterThanOrEqualTo(4),
        reason: 'packed center tiles did not render from local packs');
    expect(repo.networkTileRequests, greaterThan(0),
        reason: 'no miss attempted (viewport unexpectedly fully packed?)');

    // Restored records drive the UI without any download this launch.
    await tester.tap(find.byTooltip('offline-areas'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Saved Areas'));
    await tester.pumpAndSettle();
    expect(find.text('Esri World Imagery'), findsWidgets);
  });
}
