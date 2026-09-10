// Sovereign Atlas Engine — atlas_tiles
// Store-backed operation: the cache side of the executor, made real.
//
// Contract: phase-3 note §2. Serves the NAMED entry against the store (no
// freshness re-decision — 2.0-I); store miss ⇒ [AtlasStoreMissException]
// (executor maps to operationThrown: absence-at-serve is mechanical, and the
// 2.0-L 3-set is kept, documented, fixture-proven). Handoff puts (resident =
// acknowledged). runAcquisition throws the inverse seam (stores never
// acquire — mirrors the provider-ops seam, ADR-003).
// Phase 3 slice. Depends on atlas_core + atlas_provider_api (+ siblings).

import 'package:atlas_provider_api/atlas_provider_api.dart';
import '../entries/cache_entry.dart';
import '../execution/execution_context.dart';
import '../execution/operation_binding.dart';
import 'memory_store.dart';

/// Thrown when a named entry is absent from the store at serve time.
final class AtlasStoreMissException implements Exception {
  const AtlasStoreMissException(this.key);

  final String key;

  @override
  String toString() => 'AtlasStoreMissException(absent: $key)';
}

/// Executor operation backed by an [AtlasMemoryStore].
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
