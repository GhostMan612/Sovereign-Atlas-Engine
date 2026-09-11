// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'dart:async';

import 'package:atlas_geo/atlas_geo.dart';
import 'package:atlas_location/atlas_location.dart';

import 'package:atlas/location/location_service.dart';

AtlasLocationFix testFix({
  double latitude = 10.0,
  double longitude = 20.0,
  double? accuracy = 4.5,
}) {
  return AtlasLocationFix(
    position: AtlasCoordinate(latitude: latitude, longitude: longitude),
    at: 1,
    accuracyMeters: accuracy,
    source: 'gps',
  );
}

const AtlasLocationQuery grantedQuery = AtlasLocationQuery(
  permission: AtlasLocationPermission.granted,
  servicesEnabled: true,
);

final class FakeLocationSource implements AtlasLocationSource {
  AtlasLocationQuery queryResult = const AtlasLocationQuery(
    permission: AtlasLocationPermission.notRequested,
    servicesEnabled: false,
  );
  AtlasLocationQuery requestResult = grantedQuery;
  bool settingsResult = true;
  AtlasLocationFix? seedResult;
  Object? queryThrow;
  Object? requestThrow;
  Object? seedThrow;
  int queryCalls = 0;
  int requestCalls = 0;
  int seedCalls = 0;
  int fixesCalls = 0;
  int settingsCalls = 0;

  final StreamController<AtlasLocationFix> fixesController =
      StreamController<AtlasLocationFix>.broadcast();

  @override
  Future<AtlasLocationQuery> queryStatus() async {
    queryCalls++;
    final error = queryThrow;
    if (error != null) throw error;
    return queryResult;
  }

  @override
  Future<AtlasLocationQuery> requestPermission() async {
    requestCalls++;
    final error = requestThrow;
    if (error != null) throw error;
    return requestResult;
  }

  @override
  Future<bool> openAppSettings() async {
    settingsCalls++;
    return settingsResult;
  }

  @override
  Future<AtlasLocationFix?> lastKnownFix() async {
    seedCalls++;
    final error = seedThrow;
    if (error != null) throw error;
    return seedResult;
  }

  @override
  Stream<AtlasLocationFix> fixes() {
    fixesCalls++;
    return fixesController.stream;
  }

  void emit(AtlasLocationFix fix) => fixesController.add(fix);

  void emitError(Object error) => fixesController.addError(error);
}
