// Sovereign Atlas — Offline Areas track widget tests.
//
// UI proofs over a real engine registry with fake chunk sources: tabs
// render, the form enforces the estimate rule, refusals surface verbatim,
// and an approved pack downloads end to end through the same buttons the
// operator presses.

import 'dart:io';

import 'package:atlas/diagnostics/diagnostics_page.dart';
import 'package:atlas/offline/offline_page.dart';
import 'package:atlas/offline/offline_repository.dart';
import 'package:atlas_providers/atlas_providers.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

OfflineRepository uiRepo() {
  return OfflineRepository(
    registry: AtlasBuiltinProviders.registry(),
    chunkSourceFactory: (_) => (tile) async => [tile.z, tile.x, tile.y],
    clock: () => 1000,
    // Isolated journal (never touches the host docs dir).
    directoryProvider: () async =>
        Directory.systemTemp.createTempSync('atlas_ui_'),
  );
}

Future<void> openSheet(WidgetTester tester) async {
  await tester.tap(find.byTooltip('new-pack'));
  await tester.pumpAndSettle();
  expect(find.text('New offline pack'), findsOneWidget);
}

Future<void> fillBytes(WidgetTester tester, String value) async {
  await tester.enterText(find.byKey(const ValueKey('bytes-per-tile')), value);
  await tester.pump();
}

Future<void> pressPlan(WidgetTester tester) async {
  await tester.tap(find.byKey(const ValueKey('plan-pack')));
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('offline page renders four tabs and empty state', (tester) async {
    await tester.pumpWidget(
      MaterialApp(home: OfflinePage(repository: uiRepo())),
    );
    expect(find.text('Downloads'), findsOneWidget);
    expect(find.text('Saved Areas'), findsOneWidget);
    expect(find.text('Storage'), findsOneWidget);
    expect(find.text('Providers'), findsOneWidget);
    expect(find.textContaining('No packs yet'), findsOneWidget);
  });

  testWidgets('plan without estimate is blocked (no default assumed)',
      (tester) async {
    await tester.pumpWidget(
      MaterialApp(home: OfflinePage(repository: uiRepo())),
    );
    await openSheet(tester);
    await pressPlan(tester);
    // Shown both on the sheet result and on the registered record card.
    expect(find.textContaining('ESTIMATE_REQUIRED'), findsWidgets);
  });

  testWidgets('default OSM plan without approval surfaces BULK_GUARD',
      (tester) async {
    await tester.pumpWidget(
      MaterialApp(home: OfflinePage(repository: uiRepo())),
    );
    await openSheet(tester);
    await fillBytes(tester, '20000');
    await pressPlan(tester);
    // Shown both on the sheet result and on the registered record card.
    expect(find.textContaining('BULK_GUARD'), findsWidgets);
    expect(find.textContaining('Tile Usage Policy'), findsWidgets);
  });

  testWidgets('approved plan offers download with tile count + estimate',
      (tester) async {
    await tester.pumpWidget(
      MaterialApp(home: OfflinePage(repository: uiRepo())),
    );
    await openSheet(tester);
    await fillBytes(tester, '20000');
    await tester.tap(find.byKey(const ValueKey('approved-bulk')));
    await tester.pump();
    await pressPlan(tester);
    expect(find.byKey(const ValueKey('download-pack')), findsOneWidget);
    // Count + estimate appear on the sheet button and the record card.
    expect(find.textContaining('1 tiles'), findsWidgets);
    // 1 tile x 20000 B = 20000 B = 19.5 KiB.
    expect(find.textContaining('19.5 KiB'), findsWidgets);
  });

  testWidgets('download completes and lands in Saved Areas + Storage',
      (tester) async {
    final repo = uiRepo();
    await tester.pumpWidget(MaterialApp(home: OfflinePage(repository: repo)));
    await openSheet(tester);
    // Default provider is osm-standard (first registered): approval needed.
    await fillBytes(tester, '20000');
    await tester.tap(find.byKey(const ValueKey('approved-bulk')));
    await tester.pump();
    await pressPlan(tester);
    await tester.tap(find.byKey(const ValueKey('download-pack')));
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('download-pack')), findsNothing);
    await tester.tap(find.text('Saved Areas'));
    await tester.pumpAndSettle();
    // Downloads card (offstage) + Saved Areas entry.
    expect(find.text('OpenStreetMap Standard'), findsWidgets);
    expect(find.textContaining('1 tiles'), findsWidgets);
    expect(find.textContaining('age '), findsWidgets);

    await tester.tap(find.text('Storage'));
    await tester.pumpAndSettle();
    expect(find.textContaining('Pack index: 1/64'), findsOneWidget);
    expect(find.textContaining('63 slots remaining'), findsOneWidget);
  });

  testWidgets('providers tab surfaces declared restrictions verbatim',
      (tester) async {
    await tester.pumpWidget(
      MaterialApp(home: OfflinePage(repository: uiRepo())),
    );
    await tester.tap(find.text('Providers'));
    await tester.pumpAndSettle();
    expect(find.textContaining('Bulk downloading is discouraged'),
        findsWidgets);
    expect(find.textContaining('max tiles: undeclared'), findsWidgets);
    expect(find.textContaining('© OpenStreetMap contributors'), findsWidgets);
  });

  testWidgets('diagnostics page shows live wired state', (tester) async {
    final repo = uiRepo();
    await tester.pumpWidget(
      MaterialApp(home: DiagnosticsPage(repository: repo)),
    );
    expect(find.textContaining('Sovereign Atlas host v0.1.0+1'),
        findsOneWidget);
    expect(find.textContaining('Providers registered: 7'), findsOneWidget);
    expect(find.textContaining('Endpoints self-valid: 7/7'), findsOneWidget);
    expect(find.textContaining('Pack index: 0/64'), findsOneWidget);
    // Label accuracy: inert planned records are "unfinished", never "active".
    expect(
      find.textContaining('unfinished: 0 (downloading: 0)'),
      findsOneWidget,
    );
    expect(find.textContaining('offline serves'), findsOneWidget);
    expect(find.textContaining('No events yet'), findsOneWidget);
  });
}
