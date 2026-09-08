// Sovereign Atlas Engine — atlas_map
// AtlasMapState: renderer-independent composition of camera + ordered layers.
//
// Contract: ATLAS-CORE-CAM-001 + ATLAS-MAP-ORDER-001. This is a DOMAIN model:
// it describes map state and behavior without knowing how that state renders.
// No MapLibre, no flutter_map, no widget, no platform types appear here or
// anywhere in this package (RULE #8).
// An empty layer stack is VALID (fail-secure boot: local map/grid with no
// provider layers must still start — offline-contract invariant).
// Phase 0.5 slice. Depends on atlas_core + atlas_geo + atlas_layers only.

import '../../../../atlas_core/lib/atlas_core.dart';
import '../../../../atlas_layers/lib/atlas_layers.dart';
import '../camera/camera_state.dart';

/// Renderer-independent map state: a validated camera plus an ordered,
// explicitly composed layer stack (adapter toggles are projected into the
/// stack before it arrives here — CAP-R03 correction).
final class AtlasMapState {
  const AtlasMapState({required this.camera, required this.layers});

  final AtlasCameraState camera;
  final AtlasLayerStack layers;

  /// Pure functional recomposition (1.3-H transition doctrine): "camera changed
  /// from A to B" as deterministic data, never a gesture/animation pipeline.
  /// Construction ≠ validation by design — use [validate] to check the result.
  AtlasMapState copyWith({AtlasCameraState? camera, AtlasLayerStack? layers}) =>
      AtlasMapState(
        camera: camera ?? this.camera,
        layers: layers ?? this.layers,
      );

  /// Validates camera ranges and layer-field sanity. Baseline conformance is
  /// deliberately NOT part of validity (baseline is a compositional contract,
  /// checked via [AtlasLayerStack.conformsToBaseline], not a veto).
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
