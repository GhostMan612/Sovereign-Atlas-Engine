// Sovereign Atlas Engine — atlas_geo
// DMS parsing/formatting (strict, never guessing).
//
// Contract: blueprint 4.1 (coordinate systems, DMS row).
// Phase 4 slice. Depends on atlas_core only.

import 'package:atlas_core/atlas_core.dart';

/// Degrees/minutes/seconds value (hemisphere carried by sign convention:
/// latitude S and longitude W are negative in decimal; DMS components here
/// are non-negative magnitudes plus an explicit hemisphere letter).
final class AtlasDms {
  const AtlasDms({
    required this.degrees,
    required this.minutes,
    required this.seconds,
    required this.hemisphere,
  });

  final int degrees;
  final int minutes;
  final double seconds;
  final String hemisphere;

  /// Parses `51°30'26"N`-style text (also accepts `51 30 26 N`, `51d30m26sN`).
  /// Throws [AtlasRejectionException] (`MALFORMED_DMS`) on malformed input.
  factory AtlasDms.parse(String text) {
    final match = RegExp(
      r"^\s*(\d+)\s*(?:°|d|\s)\s*(\d+)\s*(?:['|m\s])\s*(\d+(?:\.\d+)?)\s*(?:\x22|s)?\s*([NSEW])\s*$",
    ).firstMatch(text);
    if (match == null) {
      throw AtlasRejectionException(
        const AtlasRejection(
          'MALFORMED_DMS',
          'DMS text must read like 51°30\'26"N.',
        ),
      );
    }
    final minutes = int.parse(match.group(2)!);
    final seconds = double.parse(match.group(3)!);
    if (minutes >= 60 || seconds >= 60.0) {
      throw AtlasRejectionException(
        const AtlasRejection(
          'MALFORMED_DMS',
          'DMS minutes and seconds must be below 60.',
        ),
      );
    }
    return AtlasDms(
      degrees: int.parse(match.group(1)!),
      minutes: minutes,
      seconds: seconds,
      hemisphere: match.group(4)!,
    );
  }

  /// Decimal degrees (S/W negative).
  double toDecimal() {
    final magnitude = degrees + minutes / 60.0 + seconds / 3600.0;
    return (hemisphere == 'S' || hemisphere == 'W') ? -magnitude : magnitude;
  }

  /// Formats decimal degrees for [isLatitude] (N/S/E/W applied by sign).
  static AtlasDms fromDecimal(double decimal, {required bool isLatitude}) {
    final hemisphere =
        isLatitude ? (decimal < 0 ? 'S' : 'N') : (decimal < 0 ? 'W' : 'E');
    var remaining = decimal.abs();
    final degrees = remaining.floor();
    remaining = (remaining - degrees) * 60.0;
    final minutes = remaining.floor();
    final seconds = (remaining - minutes) * 60.0;
    return AtlasDms(
      degrees: degrees,
      minutes: minutes,
      seconds: seconds,
      hemisphere: hemisphere,
    );
  }

  @override
  String toString() =>
      '$degrees°$minutes\'${seconds.toStringAsFixed(2)}"$hemisphere';

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasDms &&
          degrees == other.degrees &&
          minutes == other.minutes &&
          (seconds - other.seconds).abs() < 1e-9 &&
          hemisphere == other.hemisphere;

  @override
  int get hashCode => Object.hash(degrees, minutes, seconds, hemisphere);
}
