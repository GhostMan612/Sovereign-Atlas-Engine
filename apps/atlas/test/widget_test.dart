// Sovereign Atlas shell smoke test: app boots, map page renders.
import 'package:flutter_test/flutter_test.dart';

import 'package:atlas/main.dart';

void main() {
  testWidgets('Atlas app boots with map readout', (WidgetTester tester) async {
    await tester.pumpWidget(const AtlasApp());
    await tester.pumpAndSettle();
    expect(find.text('Sovereign Atlas'), findsOneWidget);
    expect(find.textContaining('lat '), findsOneWidget);
  });
}
