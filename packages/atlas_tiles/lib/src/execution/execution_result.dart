// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_provider_api/atlas_provider_api.dart';
import '../entries/cache_entry.dart';
import 'execution_command.dart';
import 'execution_lifecycle.dart';

enum ExecutionMalfunction {

  unsupportedBinding,

  operationThrown,

  contractViolation,
}

abstract base class ExecutionResultBase {
  const ExecutionResultBase({required this.state, this.reason = ''})
      : assert(
          state == ExecutionState.succeeded ||
              state == ExecutionState.failed ||
              state == ExecutionState.cancelled,
          'Execution results are terminal-only (2.0-G).',
        );

  final ExecutionState state;

  final String reason;

  ExecutionMalfunction? get malfunction => null;
}

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

final class RunAcquisitionResult extends ExecutionResultBase {
  const RunAcquisitionResult({
    required this.command,
    required super.state,
    super.reason,
    this.acquisition,
    this.malfunction,
  });

  final RunAcquisitionCommand command;

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
