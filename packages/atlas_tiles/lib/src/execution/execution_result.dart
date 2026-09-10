// Sovereign Atlas Engine — atlas_tiles
// Execution result: what serving did (never a reinterpretation).
//
// Contract: 2.0-G (report, don't reinterpret; terminal-only; carried outcome
// verbatim; no bytes) + 2.0-L (mechanical malfunctions use the closed 3-
// member set — semantic failures keep the 1.8 taxonomy inside the carried
// result, never here).
// - One result type per command kind keeps the I/J/K boundaries typed:
//   serve/store results carry entries; acquisition results carry acquisition
//   outcomes. No generic result envelope exists.
// Phase 2.0 slice. Depends on atlas_core + atlas_provider_api (+ siblings).

import 'package:atlas_provider_api/atlas_provider_api.dart';
import '../entries/cache_entry.dart';
import 'execution_command.dart';
import 'execution_lifecycle.dart';

/// Mechanical malfunction categories (substrate level ONLY — 2.0-L §1).
/// Semantic failures keep the acquisition taxonomy inside carried results.
enum ExecutionMalfunction {
  /// No operation bound for the identity (the 2.0-H `unsupported`).
  unsupportedBinding,

  /// The operation threw; type + message preserved, never reinterpreted.
  operationThrown,

  /// The operation broke protocol (pending result, invalid entry).
  contractViolation,
}

/// Shared terminal-result core: command echo + terminal state + reason.
/// Results are constructed by the executor only (terminal states enforced).
abstract base class ExecutionResultBase {
  const ExecutionResultBase({required this.state, this.reason = ''})
      : assert(
          state == ExecutionState.succeeded ||
              state == ExecutionState.failed ||
              state == ExecutionState.cancelled,
          'Execution results are terminal-only (2.0-G).',
        );

  final ExecutionState state;

  /// Deterministic reason (categories/identities, never stacks/timestamps).
  final String reason;

  /// Mechanical malfunction detail (execution-`failed` only; null otherwise).
  ExecutionMalfunction? get malfunction => null;
}

/// Result of serving a cache entry: the served entry echo + fallback flag.
final class ServeEntryResult extends ExecutionResultBase {
  const ServeEntryResult({
    required this.command,
    required super.state,
    super.reason,
    this.servedEntry,
    this.malfunction,
  });

  final ServeEntryCommand command;
  final AtlasCacheEntry? servedEntry;

  @override
  final ExecutionMalfunction? malfunction;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is ServeEntryResult &&
          command == other.command &&
          state == other.state &&
          reason == other.reason &&
          servedEntry == other.servedEntry &&
          malfunction == other.malfunction;

  @override
  int get hashCode =>
      Object.hash(command, state, reason, servedEntry, malfunction);
}

/// Result of one acquisition attempt: the reported outcome, verbatim.
final class RunAcquisitionResult extends ExecutionResultBase {
  const RunAcquisitionResult({
    required this.command,
    required super.state,
    super.reason,
    this.acquisition,
    this.malfunction,
  });

  final RunAcquisitionCommand command;

  /// The operation's reported outcome (any terminal state incl. failed/
  /// cancelled/timedOut — carried, never converted).
  final AtlasAcquisitionResult? acquisition;

  @override
  final ExecutionMalfunction? malfunction;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is RunAcquisitionResult &&
          command == other.command &&
          state == other.state &&
          reason == other.reason &&
          acquisition == other.acquisition &&
          malfunction == other.malfunction;

  @override
  int get hashCode =>
      Object.hash(command, state, reason, acquisition, malfunction);
}

/// Result of a handoff offer: the acknowledged entry.
final class StoreHandoffResult extends ExecutionResultBase {
  const StoreHandoffResult({
    required this.command,
    required super.state,
    super.reason,
    this.storedEntry,
    this.malfunction,
  });

  final StoreHandoffCommand command;
  final AtlasCacheEntry? storedEntry;

  @override
  final ExecutionMalfunction? malfunction;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is StoreHandoffResult &&
          command == other.command &&
          state == other.state &&
          reason == other.reason &&
          storedEntry == other.storedEntry &&
          malfunction == other.malfunction;

  @override
  int get hashCode =>
      Object.hash(command, state, reason, storedEntry, malfunction);
}
