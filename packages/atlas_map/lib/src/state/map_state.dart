// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';
import 'package:atlas_layers/atlas_layers.dart';
import '../camera/camera_state.dart';

final class AtlasMapState {
  const AtlasMapState({required this.camera, required this.layers});

  final AtlasCameraState camera;
  final AtlasLayerStack layers;

  AtlasMapState copyWith({AtlasCameraState? camera, AtlasLayerStack? layers}) =>
      AtlasMapState(
        camera: camera ?? this.camera,
        layers: layers ?? this.layers,
      );

  AtlasValidation validate() {
    final cameraCheck = camera.validate();
    if (!cameraCheck.isValid) return cameraCheck;
    for (final state in layers.states) {
      final idCheck = AtlasIds.check(state.definition.id.value);
      if (!idCheck.isValid) return idCheck;
      if (state.opacity < 0.0 ||
          state.opacity > 1.0 ||
          !state.opacity.isFinite) {
        return const AtlasValidation.invalid(
          AtlasRejection(
            'INVALID_LAYER_STATE',
            'Layer opacity must be within [0, 1] (PROPOSED range).',
          ),
        );
      }
      for (final bound in [
        state.definition.minZoom,
        state.definition.maxZoom,
      ]) {
        if (bound != null && !bound.isFinite) {
          return const AtlasValidation.invalid(
            AtlasRejection(
              'INVALID_LAYER_STATE',
              'Layer zoom bounds must be finite when present (PROPOSED).',
            ),
          );
        }
      }
    }
    return const AtlasValidation.valid();
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasMapState &&
          camera == other.camera &&
          layers == other.layers;

  @override
  int get hashCode => Object.hash(camera, layers);
}
