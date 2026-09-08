// Sovereign Atlas Engine — atlas_map
// AtlasCameraState: renderer-independent camera value object + serialization.
//
// Contract: ATLAS-CORE-CAM-001 (camera-contract.md).
// - Wire `lat|lng|zoom|bearing|tilt`: SOURCE-VERIFIED shape (F-03). Unversioned
//   as observed; schema versioning is PLANNED, not invented here.
// - Range guards lat ±90 / lng ±180 / zoom 0–24 / tilt 0–85: SOURCE-VERIFIED.
// - Bearing: NO range guard observed — any finite bearing is accepted exactly
//   as the source behavior shows (no invention, DEC-008 notes the open wrap
//   semantics). Non-finite bearing rejects.
// - Malformed input surfaces AtlasRejectionException(MALFORMED); the CORE
//   reports the error and the ADAPTER chooses the fallback (camera-contract
//   §4). This file defines no fallback coordinates of its own, except the
//   explicitly source-derived [AtlasCameraState.home] constructor, documented
//   as Mantle-derived DATA (not an Atlas-global default; DEC-008 open).
// - Camera maximum (24) is a property of the CAMERA model; provider-native
//   ceilings live in provider/layer metadata and MUST NOT be encoded here
//   (ATLAS-NORMATIVE distinction).
// Phase 0.5 slice. Depends on atlas_core + atlas_geo only.

import '../../../../atlas_core/lib/atlas_core.dart';
import '../../../../atlas_geo/lib/atlas_geo.dart';

/// Renderer-independent camera state.
final class AtlasCameraState {
  const AtlasCameraState({
    required this.center,
    required this.zoom,
    required this.bearing,
    required this.pitch,
  });

  /// Maximum camera zoom of the Atlas camera model (SOURCE-VERIFIED value 24
  /// as Mantle DATA; the Atlas-global value is DEC-008, carried here as the
  /// observed precedent with the decision still open).
  static const double maxZoom = 24.0;
  static const double minZoom = 0.0;

  /// Maximum tilt/pitch in degrees (SOURCE-VERIFIED guard bound).
  static const double maxPitch = 85.0;

  /// Mantle-derived home camera (39.83/-98.58/z3.0, SOURCE-VERIFIED F-03).
  /// Recorded as DATA. Not an Atlas-global default (DEC-008 open).
  factory AtlasCameraState.home() => const AtlasCameraState(
    center: AtlasCoordinate(latitude: 39.83, longitude: -98.58),
    zoom: 3.0,
    bearing: 0.0,
    pitch: 0.0,
  );

  final AtlasCoordinate center;
  final double zoom;
  final double bearing;
  final double pitch;

  /// Validates ranges. Bearing accepts any finite value (no observed guard).
  AtlasValidation validate() {
    final centerCheck = AtlasCoordinates.validate(
      center.latitude,
      center.longitude,
    );
    if (!centerCheck.isValid) return centerCheck;
    if (!zoom.isFinite || zoom < minZoom || zoom > maxZoom) {
      return const AtlasValidation.invalid(
        AtlasRejection(
          'INVALID_ZOOM',
          'Zoom must be within [0, 24] (SOURCE-VERIFIED guard).',
        ),
      );
    }
    if (!bearing.isFinite) {
      return const AtlasValidation.invalid(
        AtlasRejection('NON_FINITE', 'Bearing must be finite.'),
      );
    }
    if (!pitch.isFinite || pitch < 0 || pitch > maxPitch) {
      return const AtlasValidation.invalid(
        AtlasRejection(
          'OUT_OF_RANGE',
          'Pitch must be within [0, 85] (SOURCE-VERIFIED guard).',
        ),
      );
    }
    return const AtlasValidation.valid();
  }

  /// Serializes to the observed pipe wire format.
  String serialize() =>
      '${center.latitude}|${center.longitude}|$zoom|$bearing|$pitch';

  /// Parses the pipe wire format. Throws [AtlasRejectionException] with
  /// category `MALFORMED` for structural/numeric failures, or the range
  /// rejection from [validate] for out-of-range values.
  static AtlasCameraState parse(String serialized) {
    AtlasRejection malformed(String detail) =>
        AtlasRejection('MALFORMED', detail);
    final parts = serialized.split('|');
    if (parts.length != 5) {
      throw AtlasRejectionException(
        malformed('Camera state requires 5 pipe-separated fields.'),
      );
    }
    double parsePart(String part, String field) {
      final value = double.tryParse(part);
      if (value == null) {
        throw AtlasRejectionException(
          malformed('Camera field "$field" is not a number: "$part".'),
        );
      }
      return value;
    }

    final latitude = parsePart(parts[0], 'latitude');
    final longitude = parsePart(parts[1], 'longitude');
    final zoom = parsePart(parts[2], 'zoom');
    final bearing = parsePart(parts[3], 'bearing');
    final pitch = parsePart(parts[4], 'pitch');
    final candidate = AtlasCameraState(
      center: AtlasCoordinate(latitude: latitude, longitude: longitude),
      zoom: zoom,
      bearing: bearing,
      pitch: pitch,
    );
    final validation = candidate.validate();
    if (!validation.isValid) {
      throw AtlasRejectionException(validation.rejection!);
    }
    return candidate;
  }

  /// Pure functional recomposition (1.3-H transition doctrine): deterministic,
  /// synchronous, no events/callbacks/animation/gestures. Construction ≠
  /// validation by design — use [validate] to check the result.
  AtlasCameraState copyWith({
    AtlasCoordinate? center,
    double? zoom,
    double? bearing,
    double? pitch,
  }) => AtlasCameraState(
    center: center ?? this.center,
    zoom: zoom ?? this.zoom,
    bearing: bearing ?? this.bearing,
    pitch: pitch ?? this.pitch,
  );

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasCameraState &&
          center == other.center &&
          zoom == other.zoom &&
          bearing == other.bearing &&
          pitch == other.pitch;

  @override
  int get hashCode => Object.hash(center, zoom, bearing, pitch);

  @override
  String toString() => 'AtlasCameraState(${serialize()})';
}
