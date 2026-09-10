// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';

String fnv1a64(List<int> bytes) {
  var hash = 0xcbf29ce484222325;
  for (final byte in bytes) {
    hash ^= byte & 0xff;
    hash = hash * 0x100000001b3;
  }
  final high = (hash >>> 32).toRadixString(16).padLeft(8, '0');
  final low = (hash & 0xffffffff).toRadixString(16).padLeft(8, '0');
  return '$high$low';
}

final class AtlasPackEntry {
  const AtlasPackEntry({required this.address, required this.checksum});

  final String address;
  final String checksum;

  Map<String, dynamic> toJson() => {'address': address, 'checksum': checksum};

  factory AtlasPackEntry.fromJson(Map<String, dynamic> json) => AtlasPackEntry(
        address: json['address'] as String,
        checksum: json['checksum'] as String,
      );

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasPackEntry &&
          address == other.address &&
          checksum == other.checksum;

  @override
  int get hashCode => Object.hash(address, checksum);
}

final class AtlasPackManifest {
  const AtlasPackManifest({
    required this.packId,
    required this.provider,
    required this.zoomMin,
    required this.zoomMax,
    required this.createdAt,
    required this.entries,
    this.sourceVersion,
    this.attribution,
  });

  final AtlasId packId;
  final AtlasId provider;
  final int zoomMin;
  final int zoomMax;

  final int createdAt;
  final List<AtlasPackEntry> entries;
  final String? sourceVersion;
  final String? attribution;

  String get seal {
    final sorted = entries.toList()
      ..sort((a, b) => a.address.compareTo(b.address));
    final buffer = StringBuffer();
    for (final entry in sorted) {
      buffer.write('${entry.address}:${entry.checksum};');
    }
    return fnv1a64(buffer.toString().codeUnits);
  }

  int get entryCount => entries.length;

  AtlasValidation validate() {
    if (zoomMin < 0 || zoomMax < 0 || zoomMin > zoomMax) {
      return const AtlasValidation.invalid(
        AtlasRejection(
          'INVALID_MANIFEST',
          'Pack zoom range must be non-negative with min <= max.',
        ),
      );
    }
    final idCheck = AtlasIds.check(packId.value);
    if (!idCheck.isValid) return idCheck;
    return const AtlasValidation.valid();
  }

  Map<String, dynamic> toJson() => {
        'pack_id': packId.value,
        'provider': provider.value,
        'zoom_min': zoomMin,
        'zoom_max': zoomMax,
        'created_at': createdAt,
        'source_version': sourceVersion,
        'attribution': attribution,
        'entries': [for (final e in entries) e.toJson()],
      };

  factory AtlasPackManifest.fromJson(Map<String, dynamic> json) =>
      AtlasPackManifest(
        packId: AtlasId(json['pack_id'] as String),
        provider: AtlasId(json['provider'] as String),
        zoomMin: (json['zoom_min'] as num).toInt(),
        zoomMax: (json['zoom_max'] as num).toInt(),
        createdAt: (json['created_at'] as num).toInt(),
        sourceVersion: json['source_version'] as String?,
        attribution: json['attribution'] as String?,
        entries: [
          for (final e
              in (json['entries'] as List).cast<Map<String, dynamic>>())
            AtlasPackEntry.fromJson(e),
        ],
      );

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasPackManifest &&
          packId == other.packId &&
          provider == other.provider &&
          zoomMin == other.zoomMin &&
          zoomMax == other.zoomMax &&
          createdAt == other.createdAt &&
          sourceVersion == other.sourceVersion &&
          attribution == other.attribution &&
          _equalEntries(entries, other.entries);

  static bool _equalEntries(List<AtlasPackEntry> a, List<AtlasPackEntry> b) {
    if (a.length != b.length) return false;
    for (var i = 0; i < a.length; i++) {
      if (a[i] != b[i]) return false;
    }
    return true;
  }

  @override
  int get hashCode => Object.hash(
        packId,
        provider,
        zoomMin,
        zoomMax,
        createdAt,
        sourceVersion,
        attribution,
        Object.hashAll(entries),
      );
}
