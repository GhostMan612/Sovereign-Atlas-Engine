// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_provider_api/atlas_provider_api.dart';
import '../entries/cache_entry.dart';
import '../execution/execution_context.dart';
import '../execution/operation_binding.dart';
import 'memory_store.dart';

final class AtlasStoreMissException implements Exception {
  const AtlasStoreMissException(this.key);

  final String key;

  @override
  String toString() => 'AtlasStoreMissException(absent: $key)';
}

final class AtlasStoreOperation implements AtlasExecutionOperation {
  AtlasStoreOperation({required this.store});

  final AtlasMemoryStore store;

  @override
  Future<AtlasCacheEntry> serveEntry(
    AtlasCacheEntry entry,
    ExecutionContext context,
  ) async {
    if (context.cancellation.isCancelled) {
      throw const ExecutionCancelled();
    }
    final stored = store.get(entry.key);
    if (stored == null) {
      throw AtlasStoreMissException(
        '${entry.key.namespace.name}/${entry.key.value}',
      );
    }
    return stored;
  }

  @override
  Future<AtlasCacheEntry> storeHandoff(
    AtlasCacheEntry handoff,
    ExecutionContext context,
  ) async {
    if (context.cancellation.isCancelled) {
      throw const ExecutionCancelled();
    }
    store.put(handoff);
    return handoff;
  }

  @override
  Future<AtlasAcquisitionResult> runAcquisition(
    AtlasAcquisitionRequest request,
    ExecutionContext context,
  ) =>
      throw UnsupportedError(
        'store operations do not run acquisitions (provider scope)',
      );
}
