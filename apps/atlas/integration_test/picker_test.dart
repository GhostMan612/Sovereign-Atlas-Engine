// Sovereign Atlas on-device interaction proof (DEVICE-004).
//
// Runs ON the attached emulator (flutter test integration_test), NOT on the
// host: taps use semantic locators (tooltip/text), never blind coordinates.
// Proves the picker flow end to end where DEVICE-003's coordinate taps
// could not reach.
import 'package:atlas/main.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('picker switches basemap on device', (tester) async {
    await tester.pumpWidget(const AtlasApp());
    await tester.pumpAndSettle();

    expect(find.text('Sovereign Atlas'), findsOneWidget);
    expect(find.textContaining('OpenStreetMap'), findsOneWidget);

    await tester.tap(find.byTooltip('Basemap'));
    await tester.pumpAndSettle();
    expect(find.text('Satellite'), findsOneWidget);

    await tester.tap(find.text('Satellite'));
    await tester.pumpAndSettle();

    final layers = tester.widgetList<TileLayer>(find.byType(TileLayer));
    expect(
      layers.any((l) => (l.urlTemplate ?? '').contains('arcgis')),
      isTrue,
    );
    expect(find.textContaining('Esri, Maxar'), findsOneWidget);
  });
}
