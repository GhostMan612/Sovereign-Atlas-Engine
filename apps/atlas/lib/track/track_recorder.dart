// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_location/atlas_location.dart';
import 'package:flutter/foundation.dart';

import '../location/location_service.dart';

enum TrackRecorderState { idle, recording }

final class TrackRecorder extends ChangeNotifier {
  TrackRecorder({required LocationService locationService})
      : _location = locationService;

  final LocationService _location;
  TrackRecorderState _state = TrackRecorderState.idle;
  AtlasTrackLog _log = AtlasTrackLog();
  AtlasLocationFix? _lastAppended;
  bool _listening = false;
  bool _disposed = false;

  TrackRecorderState get state => _state;
  bool get isRecording => _state == TrackRecorderState.recording;
  int get pointCount => _log.fixCount;
  List<AtlasLocationFix> get points => _log.fixes;

  void start() {
    if (_disposed || _state == TrackRecorderState.recording) return;
    _log = AtlasTrackLog();
    _lastAppended = null;
    _state = TrackRecorderState.recording;
    if (!_listening) {
      _location.addListener(_onLocation);
      _listening = true;
    }
    _ingestCurrent();
    notifyListeners();
  }

  List<AtlasLocationFix> stop() {
    if (_state == TrackRecorderState.idle) return const [];
    _detach();
    final fixes = _log.fixes;
    _log = AtlasTrackLog();
    _lastAppended = null;
    _state = TrackRecorderState.idle;
    notifyListeners();
    return fixes;
  }

  @override
  void dispose() {
    _disposed = true;
    _detach();
    super.dispose();
  }

  void _detach() {
    if (_listening) {
      _location.removeListener(_onLocation);
      _listening = false;
    }
  }

  void _onLocation() {
    if (_disposed || _state != TrackRecorderState.recording) return;
    if (_location.status != AtlasLocationStatus.valid) return;
    final fix = _location.latestFix;
    if (fix == null || fix == _lastAppended) return;
    _log.append(fix);
    _lastAppended = fix;
    notifyListeners();
  }

  void _ingestCurrent() {
    if (_location.status != AtlasLocationStatus.valid) return;
    final fix = _location.latestFix;
    if (fix == null) return;
    _log.append(fix);
    _lastAppended = fix;
  }
}
