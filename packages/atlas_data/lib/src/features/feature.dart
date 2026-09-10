// Sovereign Atlas Engine — atlas_data
// Normalized feature model (ADR-001 contract: id, geometry, properties,
// source, sourceVersion, retrievedAt, license, confidence, accuracy,
// sensitivity) + dataset descriptors.
//
// Contract: blueprint Phase 5 (Atlas Data Layers, model side). Geometry is
// carried as explicit coordinate nests (Point/LineString/Polygon rings —
//validated on normalize, never assumed); properties stay untyped maps
// (schemas describe, never coerce — a future schema package can tighten).
// Time is explicit epoch seconds throughout.
// Phase 5 slice. Depends on atlas_core + atlas_geo only.

import 'package:atlas_core/atlas_core.dart';
import 'package:atlas_geo/atlas_geo.dart';

/// Normalized geometry families (GeoJSON-compatible names, engine-owned).
enum AtlasGeometryKind { point, lineString, polygon }

/// Normalized feature: geometry + properties + full provenance/currency.
final class AtlasFeature {
  const AtlasFeature({
    this.id,
    required this.geometryKind,
    required this.coordinates,
    this.properties = const {},
    required this.source,
    this.sourceVersion,
    this.retrievedAt,
    this.license,
    this.confidence,
    this.accuracyMeters,
    this.sensitivity,
  });

  final String? id;
  final AtlasGeometryKind geometryKind;

  /// Coordinate nest: point [lon,lat] | line [[lon,lat]…] | polygon
  /// [[[lon,lat]…]…] (exterior + holes). Numbers only; validated on use.
  final List<dynamic> coordinates;
  final Map<String, dynamic> properties;
  final String source;
  final String? sourceVersion;
  final int? retrievedAt;
  final String? license;
  final double? confidence;
  final double? accuracyMeters;
  final String? sensitivity;

  /// Structural validation: nest shape + finite numbers + valid positions.
  AtlasValidation validate() {
    final coords = coordinates;
    bool position(dynamic p) =>
        p is List &&
        p.length >= 2 &&
        p[0] is num &&
        p[1] is num &&
        AtlasCoordinates.validate(
          (p[1] as num).toDouble(),
          (p[0] as num).toDouble(),
        ).isValid;
    switch (geometryKind) {
      case AtlasGeometryKind.point:
        if (!position(coords)) {
          return const AtlasValidation.invalid(
            AtlasRejection('INVALID_FEATURE', 'Point needs [lon, lat].'),
          );
        }
      case AtlasGeometryKind.lineString:
        if (coords.length < 2 || !coords.every(position)) {
          return const AtlasValidation.invalid(
            AtlasRejection(
              'INVALID_FEATURE',
              'LineString needs ≥2 valid positions.',
            ),
          );
        }
      case AtlasGeometryKind.polygon:
        if (coords.isEmpty) {
          return const AtlasValidation.invalid(
            AtlasRejection('INVALID_FEATURE', 'Polygon needs ≥1 ring.'),
          );
        }
        for (final ring in coords) {
          final positions = ring is List ? ring : const [];
          if (positions.length < 4 || !positions.every(position)) {
            return const AtlasValidation.invalid(
              AtlasRejection(
                'INVALID_FEATURE',
                'Polygon rings need ≥4 valid positions.',
              ),
            );
          }
        }
    }
    if (confidence != null && (confidence! < 0.0 || confidence! > 1.0)) {
      return const AtlasValidation.invalid(
        AtlasRejection(
          'INVALID_FEATURE',
          'Confidence must sit within [0, 1].',
        ),
      );
    }
    return const AtlasValidation.valid();
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasFeature &&
          id == other.id &&
          geometryKind == other.geometryKind &&
          source == other.source &&
          sourceVersion == other.sourceVersion &&
          retrievedAt == other.retrievedAt &&
          license == other.license &&
          confidence == other.confidence &&
          accuracyMeters == other.accuracyMeters &&
          sensitivity == other.sensitivity;

  @override
  int get hashCode => Object.hash(
        id,
        geometryKind,
        source,
        sourceVersion,
        retrievedAt,
        license,
        confidence,
        accuracyMeters,
        sensitivity,
      );
}

/// Dataset descriptor: named feature collection contract (ADR-001: schemas,
// GeoJSON/MBTiles/raster/vector normalization contracts live here).
final class AtlasDatasetDescriptor {
  const AtlasDatasetDescriptor({
    required this.id,
    required this.title,
    required this.kinds,
    this.source,
    this.sourceVersion,
    this.license,
    this.attribution,
    this.sensitivity,
  });

  final AtlasId id;
  final String title;
  final Set<AtlasGeometryKind> kinds;
  final String? source;
  final String? sourceVersion;
  final String? license;
  final String? attribution;
  final String? sensitivity;

  AtlasValidation validate() {
    final idCheck = AtlasIds.check(id.value);
    if (!idCheck.isValid) return idCheck;
    if (kinds.isEmpty) {
      return const AtlasValidation.invalid(
        AtlasRejection(
          'INVALID_DATASET',
          'A dataset descriptor must declare at least one geometry kind.',
        ),
      );
    }
    return const AtlasValidation.valid();
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasDatasetDescriptor &&
          id == other.id &&
          title == other.title &&
          kinds.length == other.kinds.length &&
          kinds.containsAll(other.kinds) &&
          source == other.source &&
          sourceVersion == other.sourceVersion &&
          license == other.license &&
          attribution == other.attribution &&
          sensitivity == other.sensitivity;

  @override
  int get hashCode => Object.hash(
        id,
        title,
        Object.hashAllUnordered(kinds),
        source,
        sourceVersion,
        license,
        attribution,
        sensitivity,
      );
}
