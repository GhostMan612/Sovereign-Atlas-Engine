// Sovereign Atlas shell tests: boot, readout, provider picker.
import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:atlas/main.dart';

void main() {
  testWidgets('Atlas app boots with map readout', (WidgetTester tester) async {
    await tester.pumpWidget(const AtlasApp());
    await tester.pumpAndSettle();
    expect(find.text('Sovereign Atlas'), findsOneWidget);
    expect(find.textContaining('lat '), findsOneWidget);
    expect(find.textContaining('OpenStreetMap'), findsOneWidget);
  });

  testWidgets('Provider picker switches basemap and attribution', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(const AtlasApp());
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('Basemap'));
    await tester.pumpAndSettle();
    expect(find.text('Satellite'), findsOneWidget);

    await tester.tap(find.text('Satellite'));
    await tester.pumpAndSettle();

    // Esri imagery template active (TileLayer keyed by provider id).
    final layers = tester.widgetList<TileLayer>(find.byType(TileLayer));
    expect(
      layers.any((l) => (l.urlTemplate ?? '').contains('arcgis')),
      isTrue,
    );
    expect(find.textContaining('Esri, Maxar'), findsOneWidget);
  });
}
