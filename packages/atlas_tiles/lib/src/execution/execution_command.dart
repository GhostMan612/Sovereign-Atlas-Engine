// Sovereign Atlas Engine — atlas_tiles
// Execution commands: the closed value set serving pipeline directives.
//
// Contract: 2.0-D (three types, 1:1 with directives, terminals map to none)
// + 2.0-I/J (stage boundaries kept apart by the type system).
// - Immutable values with equality; semantic payloads only — never
//   operations, contexts, callbacks, futures, clocks, or credentials.
// - No generic execute-resource abstraction exists by construction: the
//   executor offers exactly one method per command type.
// Phase 2.0 slice. Depends on atlas_core + atlas_provider_api (+ siblings).

import '../../../../atlas_provider_api/lib/atlas_provider_api.dart';
import '../entries/cache_entry.dart';

/// Directive: serve the named entry (2.0-I). `fallback` carries pipeline
/// provenance (usable hit vs declared stale fallback), never policy.
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

/// Directive: run one acquisition attempt for the request (2.0-J).
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

/// Directive: offer the candidate handoff entry to the bound operation
/// (2.0-I §2: offer, acknowledged — never a persistence claim).
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
