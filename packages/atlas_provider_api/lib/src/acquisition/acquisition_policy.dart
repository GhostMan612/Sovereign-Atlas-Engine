// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';

final class AtlasAcquisitionPolicy {
  const AtlasAcquisitionPolicy({this.timeoutSeconds, this.allowRetry = true});

  final int? timeoutSeconds;

  final bool allowRetry;

  AtlasValidation validate() {
    if (timeoutSeconds != null && timeoutSeconds! < 0) {
      return const AtlasValidation.invalid(
        AtlasRejection(
          'INVALID_REQUEST',
          'Acquisition timeout must be non-negative when declared.',
        ),
      );
    }
    return const AtlasValidation.valid();
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasAcquisitionPolicy &&
          timeoutSeconds == other.timeoutSeconds &&
          allowRetry == other.allowRetry;

  @override
  int get hashCode => Object.hash(timeoutSeconds, allowRetry);
}
