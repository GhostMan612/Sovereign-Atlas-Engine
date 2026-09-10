// Sovereign Atlas Engine — atlas_data
// GeoJSON normalization: plain maps in, validated features out.
//
// Contract: blueprint Phase 5 (GeoJSON normalization contract). Accepts
// decoded JSON maps (decoding itself is caller-side — no dart:convert here,
// no IO): Feature / FeatureCollection / bare-geometry documents.
// Unknown/unsupported geometry types throw MALFORMED_GEOJSON (never skipped
// silently — silent drops corrupt datasets). CRS members other than WGS84
// names refuse (coordinate contract consistency). Properties pass through
// untouched; source defaults to the caller-supplied origin (explicit).
// Phase 5 slice. Depends on atlas_core + siblings only.

import 'package:atlas_core/atlas_core.dart';
import '../features/feature.dart';

/// Pure GeoJSON normalizer (maps only — never strings, never files).
abstract final class AtlasGeoJson {
  /// Normalizes a decoded GeoJSON document with [origin] as the source tag.
  static List<AtlasFeature> normalize(
    Map<String, dynamic> document, {
    required String origin,
  }) {
    final type = document['type'] as String?;
    switch (type) {
      case 'FeatureCollection':
        final features =
            (document['features'] as List?)?.cast<Map<String, dynamic>>() ??
                const [];
        return [
          for (final f in features) _feature(f.cast<String, dynamic>(), origin),
        ];
      case 'Feature':
        return [_feature(document, origin)];
      case 'Point':
      case 'LineString':
      case 'Polygon':
        return [_geometry(document, const {}, origin)];
      default:
        throw AtlasRejectionException(
          AtlasRejection(
            'MALFORMED_GEOJSON',
            'Unsupported GeoJSON type: $type.',
          ),
        );
    }
  }

  static AtlasFeature _feature(Map<String, dynamic> json, String origin) {
    if (json['type'] != 'Feature') {
      throw AtlasRejectionException(
        const AtlasRejection(
          'MALFORMED_GEOJSON',
          'FeatureCollection members must be Features.',
        ),
      );
    }
    final geometry = json['geometry'] as Map<String, dynamic>?;
    if (geometry == null) {
      throw AtlasRejectionException(
        const AtlasRejection(
          'MALFORMED_GEOJSON',
          'Null geometries are not normalized (no silent drops).',
        ),
      );
    }
    return _geometry(
      geometry,
      (json['properties'] as Map?)?.cast<String, dynamic>() ?? const {},
      origin,
      id: json['id']?.toString(),
    );
  }

  static AtlasFeature _geometry(
    Map<String, dynamic> geometry,
    Map<String, dynamic> properties,
    String origin, {
    String? id,
  }) {
    final kind = switch (geometry['type']) {
      'Point' => AtlasGeometryKind.point,
      'LineString' => AtlasGeometryKind.lineString,
      'Polygon' => AtlasGeometryKind.polygon,
      final other => throw AtlasRejectionException(
          AtlasRejection(
            'MALFORMED_GEOJSON',
            'Unsupported geometry type: $other.',
          ),
        ),
    };
    return AtlasFeature(
      id: id,
      geometryKind: kind,
      coordinates: (geometry['coordinates'] as List).cast<dynamic>(),
      properties: properties,
      source: origin,
    );
  }
}
