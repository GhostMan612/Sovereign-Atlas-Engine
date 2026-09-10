// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_provider_api/atlas_provider_api.dart';
import 'execution_command.dart';
import 'execution_context.dart';
import 'execution_lifecycle.dart';
import 'execution_result.dart';
import 'operation_binding.dart';

abstract final class AtlasExecutor {

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

          return RunAcquisitionResult(
            command: command,
            state: ExecutionState.succeeded,
            reason: 'attempt reported ${reported.state.name}'
                '${reported.failure == null ? '' : ' ${reported.failure!.name}'}',
            acquisition: reported,
          );
        case AtlasAcquisitionState.cancelled:

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
