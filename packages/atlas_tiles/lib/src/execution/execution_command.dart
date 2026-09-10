// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_provider_api/atlas_provider_api.dart';
import '../entries/cache_entry.dart';

final class ServeEntryCommand {
  const ServeEntryCommand({required this.entry, this.fallback = false});

  final AtlasCacheEntry entry;
  final bool fallback;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is ServeEntryCommand &&
          entry == other.entry &&
          fallback == other.fallback;

  @override
  int get hashCode => Object.hash(entry, fallback);

  @override
  String toString() => 'ServeEntryCommand($entry fallback=$fallback)';
}

final class RunAcquisitionCommand {
  const RunAcquisitionCommand({required this.request});

  final AtlasAcquisitionRequest request;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is RunAcquisitionCommand && request == other.request;

  @override
  int get hashCode => request.hashCode;

  @override
  String toString() => 'RunAcquisitionCommand($request)';
}

final class StoreHandoffCommand {
  const StoreHandoffCommand({required this.handoff});

  final AtlasCacheEntry handoff;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is StoreHandoffCommand && handoff == other.handoff;

  @override
  int get hashCode => handoff.hashCode;

  @override
  String toString() => 'StoreHandoffCommand($handoff)';
}
