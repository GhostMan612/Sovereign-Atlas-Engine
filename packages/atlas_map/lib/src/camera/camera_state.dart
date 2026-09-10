// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';
import 'package:atlas_geo/atlas_geo.dart';

final class AtlasCameraState {
  const AtlasCameraState({
    required this.center,
    required this.zoom,
    required this.bearing,
    required this.pitch,
  });

  static const double maxZoom = 24.0;
  static const double minZoom = 0.0;

  static const double maxPitch = 85.0;

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

  String serialize() =>
      '${center.latitude}|${center.longitude}|$zoom|$bearing|$pitch';

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

  AtlasCameraState copyWith({
    AtlasCoordinate? center,
    double? zoom,
    double? bearing,
    double? pitch,
  }) =>
      AtlasCameraState(
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
