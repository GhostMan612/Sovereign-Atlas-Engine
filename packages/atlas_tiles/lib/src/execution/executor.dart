// Sovereign Atlas Engine — atlas_tiles
// Executor: serves the three command types against bound operations.
//
// Contract: 2.0-I/J (boundary crossings), 2.0-E (lifecycle truthfulness),
// 2.0-F (cooperation protocol), 2.0-L (total translation table).
// - Exactly ONE method per command type — no generic serve method exists
//   (the I/J/K guard, structurally enforced).
// - Cancel-before-start never contacts the operation. Binding miss reports
//   unsupportedBinding without contact. The executor performs no lifecycle
//   transition on anything but its own serving report.
// - Total: no throw escapes except Dart-level misuse (null required args).
//   Operation throws map to operationThrown with type + message only.
// Phase 2.0 slice. Depends on atlas_core + atlas_provider_api (+ siblings).

import '../../../../atlas_provider_api/lib/atlas_provider_api.dart';
import 'execution_command.dart';
import 'execution_context.dart';
import 'execution_lifecycle.dart';
import 'execution_result.dart';
import 'operation_binding.dart';

/// Serves execution commands. Stateless; all inputs explicit per call.
abstract final class AtlasExecutor {
  /// Serves [command] against the operation bound to the entry's resource
  /// identity (2.0-I: named entry served, never re-decided).
  static Future<ServeEntryResult> serveEntry({
    required ServeEntryCommand command,
    required ExecutionContext context,
  }) async {
    if (context.cancellation.isCancelled) {
      return ServeEntryResult(
        command: command,
        state: ExecutionState.cancelled,
        reason: 'cancel requested before serving began',
      );
    }
    final identity = command.entry.resource;
    if (identity == null) {
      return ServeEntryResult(
        command: command,
        state: ExecutionState.failed,
        reason: 'entry carries no resource identity; unbindable',
        malfunction: ExecutionMalfunction.unsupportedBinding,
      );
    }
    final operation = context.binding.lookup(identity);
    if (operation == null) {
      return ServeEntryResult(
        command: command,
        state: ExecutionState.failed,
        reason: 'no operation bound for $identity',
        malfunction: ExecutionMalfunction.unsupportedBinding,
      );
    }
    try {
      final served = await operation.serveEntry(command.entry, context);
      if (!served.validate().isValid) {
        return ServeEntryResult(
          command: command,
          state: ExecutionState.failed,
          reason: 'operation returned a structurally invalid entry',
          malfunction: ExecutionMalfunction.contractViolation,
        );
      }
      return ServeEntryResult(
        command: command,
        state: ExecutionState.succeeded,
        reason: command.fallback
            ? 'declared fallback entry served'
            : 'usable cached entry served',
        servedEntry: served,
      );
    } on ExecutionCancelled {
      return ServeEntryResult(
        command: command,
        state: ExecutionState.cancelled,
        reason: 'operation observed cancellation and stopped',
      );
    } catch (error) {
      return ServeEntryResult(
        command: command,
        state: ExecutionState.failed,
        reason: '${error.runtimeType}: $error',
        malfunction: ExecutionMalfunction.operationThrown,
      );
    }
  }

  /// Runs one acquisition attempt through the bound operation (2.0-J: one
  /// command, one attempt, one report — never retried, never scheduled).
  static Future<RunAcquisitionResult> runAcquisition({
    required RunAcquisitionCommand command,
    required ExecutionContext context,
  }) async {
    if (context.cancellation.isCancelled) {
      return RunAcquisitionResult(
        command: command,
        state: ExecutionState.cancelled,
        reason: 'cancel requested before serving began',
      );
    }
    final operation = context.binding.lookup(command.request.resource);
    if (operation == null) {
      return RunAcquisitionResult(
        command: command,
        state: ExecutionState.failed,
        reason: 'no operation bound for ${command.request.resource}',
        malfunction: ExecutionMalfunction.unsupportedBinding,
      );
    }
    final cancelRequested = () => context.cancellation.isCancelled;
    try {
      final reported = await operation.runAcquisition(command.request, context);
      switch (reported.state) {
        case AtlasAcquisitionState.succeeded:
        case AtlasAcquisitionState.failed:
        case AtlasAcquisitionState.timedOut:
          // Carried verbatim — even inner failure is execution-succeeded
          // (2.0-E §4: truthful reporting completes the serving act).
          return RunAcquisitionResult(
            command: command,
            state: ExecutionState.succeeded,
            reason:
                'attempt reported ${reported.state.name}'
                '${reported.failure == null ? '' : ' ${reported.failure!.name}'}',
            acquisition: reported,
          );
        case AtlasAcquisitionState.cancelled:
          // Requested + reported ⇒ serving stopped by cancellation;
          // unrequested ⇒ operation-level event, carried like any outcome.
          if (cancelRequested()) {
            return RunAcquisitionResult(
              command: command,
              state: ExecutionState.cancelled,
              reason: 'cancel requested and attempt reported cancelled',
              acquisition: reported,
            );
          }
          return RunAcquisitionResult(
            command: command,
            state: ExecutionState.succeeded,
            reason: 'unrequested operation-level cancel carried',
            acquisition: reported,
          );
        case AtlasAcquisitionState.notStarted:
        case AtlasAcquisitionState.inProgress:
          return RunAcquisitionResult(
            command: command,
            state: ExecutionState.failed,
            reason: 'operation returned a non-terminal acquisition result',
            malfunction: ExecutionMalfunction.contractViolation,
          );
      }
    } on ExecutionCancelled {
      return RunAcquisitionResult(
        command: command,
        state: ExecutionState.cancelled,
        reason: 'operation observed cancellation and stopped',
      );
    } catch (error) {
      return RunAcquisitionResult(
        command: command,
        state: ExecutionState.failed,
        reason: '${error.runtimeType}: $error',
        malfunction: ExecutionMalfunction.operationThrown,
      );
    }
  }

  /// Offers the handoff entry to the bound operation (2.0-I §2: offer and
  /// acknowledged-report, never a persistence claim).
  static Future<StoreHandoffResult> storeHandoff({
    required StoreHandoffCommand command,
    required ExecutionContext context,
  }) async {
    if (context.cancellation.isCancelled) {
      return StoreHandoffResult(
        command: command,
        state: ExecutionState.cancelled,
        reason: 'cancel requested before serving began',
      );
    }
    final identity = command.handoff.resource;
    if (identity == null) {
      return StoreHandoffResult(
        command: command,
        state: ExecutionState.failed,
        reason: 'handoff carries no resource identity; unbindable',
        malfunction: ExecutionMalfunction.unsupportedBinding,
      );
    }
    final operation = context.binding.lookup(identity);
    if (operation == null) {
      return StoreHandoffResult(
        command: command,
        state: ExecutionState.failed,
        reason: 'no operation bound for $identity',
        malfunction: ExecutionMalfunction.unsupportedBinding,
      );
    }
    try {
      final stored = await operation.storeHandoff(command.handoff, context);
      if (!stored.validate().isValid) {
        return StoreHandoffResult(
          command: command,
          state: ExecutionState.failed,
          reason: 'operation returned a structurally invalid entry',
          malfunction: ExecutionMalfunction.contractViolation,
        );
      }
      return StoreHandoffResult(
        command: command,
        state: ExecutionState.succeeded,
        reason: 'handoff acknowledged by bound operation',
        storedEntry: stored,
      );
    } on ExecutionCancelled {
      return StoreHandoffResult(
        command: command,
        state: ExecutionState.cancelled,
        reason: 'operation observed cancellation and stopped',
      );
    } catch (error) {
      return StoreHandoffResult(
        command: command,
        state: ExecutionState.failed,
        reason: '${error.runtimeType}: $error',
        malfunction: ExecutionMalfunction.operationThrown,
      );
    }
  }
}
