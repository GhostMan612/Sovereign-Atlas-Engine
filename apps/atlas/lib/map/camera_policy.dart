// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_location/atlas_location.dart';
import 'package:atlas_map/atlas_map.dart';

import '../location/location_service.dart';

const double kMyLocationZoom = 15.0;
const double kStartupLocalZoom = 13.0;

AtlasCameraState? startupIntent({
  required AtlasLocationStatus status,
  required AtlasLocationFix? fix,
}) {
  if (status != AtlasLocationStatus.valid || fix == null) return null;
  final state = AtlasCameraState(
    center: fix.position,
    zoom: kStartupLocalZoom,
    bearing: 0.0,
    pitch: 0.0,
  );
  return state.validate().isValid ? state : null;
}

AtlasCameraState? myLocationIntent({
  required AtlasLocationStatus status,
  required AtlasLocationFix? fix,
  required double bearing,
}) {
  if (status != AtlasLocationStatus.valid || fix == null) return null;
  final state = AtlasCameraState(
    center: fix.position,
    zoom: kMyLocationZoom,
    bearing: bearing,
    pitch: 0.0,
  );
  return state.validate().isValid ? state : null;
}
