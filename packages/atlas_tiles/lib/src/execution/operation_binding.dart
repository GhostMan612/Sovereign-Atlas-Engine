// Sovereign Atlas Engine — atlas_tiles
// Operation binding: explicit identity→operation declaration + the abstract
// operation protocol.
//
// Contract: 2.0-H (explicit declaration, exact triple match, miss means
// unsupported without probing; operation is behavior, not transport).
// - The CALLER owns the declaration; the SUBSTRATE owns lookup mechanics;
//   the OPERATION owns its cooperation behavior and nothing about routing.
// - Operations receive (payload, explicit context) and yield future outcome
//   values from the closed semantic vocabulary. They reach outward for
//   nothing (arch-scan enforced).
// - Cooperation protocol: an operation observing a requested cancellation
//   reports it by throwing [ExecutionCancelled] (a control signal, not an
//   error — the executor maps it to the `cancelled` terminal). Cancel-before-
//   start never contacts the operation at all (executor-side rule).
// Phase 2.0 slice. Depends on atlas_core + atlas_provider_api (+ siblings).

import '../../../../atlas_provider_api/lib/atlas_provider_api.dart';
import '../entries/cache_entry.dart';
import 'execution_context.dart';

/// Cooperation signal: the operation observed a requested cancellation and
/// stopped. Caught by the executor; never a failure, never user-visible as
/// an error.
final class ExecutionCancelled {
  const ExecutionCancelled();
}

/// Abstract executable behavior for one bound identity (2.0-H §3). Transport-
/// blind by contract: context in, closed-vocabulary outcome out. Exactly one
/// method per command kind — no generic execute method exists.
abstract class AtlasExecutionOperation {
  /// Serves a cache entry: returns the served entry (echo/normalized).
  /// Must return a structurally valid entry; anything else is a violation.
  Future<AtlasCacheEntry> serveEntry(
    AtlasCacheEntry entry,
    ExecutionContext context,
  );

  /// Runs one acquisition attempt: returns a TERMINAL acquisition result.
  /// Pending results are contract violations (never waited upon).
  Future<AtlasAcquisitionResult> runAcquisition(
    AtlasAcquisitionRequest request,
    ExecutionContext context,
  );

  /// Accepts a handoff offer: returns the acknowledged entry.
  Future<AtlasCacheEntry> storeHandoff(
    AtlasCacheEntry handoff,
    ExecutionContext context,
  );
}

/// Explicit identity→operation declaration (caller-owned value).
final class AtlasOperationBinding {
  const AtlasOperationBinding([
    Map<AtlasResourceIdentity, AtlasExecutionOperation>? operations,
  ]) : _operations = operations ?? const {};

  final Map<AtlasResourceIdentity, AtlasExecutionOperation> _operations;

  /// Exact triple-equality lookup. Null = unbound (unsupported, no probing).
  AtlasExecutionOperation? lookup(AtlasResourceIdentity identity) =>
      _operations[identity];
}
