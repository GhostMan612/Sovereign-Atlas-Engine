// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

enum ExecutionState {

  pending,

  running,

  succeeded,

  failed,

  cancelled,
}

const executionTerminalStates = {
  ExecutionState.succeeded,
  ExecutionState.failed,
  ExecutionState.cancelled,
};

bool isExecutionTransitionAllowed(ExecutionState from, ExecutionState to) {
  switch (from) {
    case ExecutionState.pending:
      return to == ExecutionState.running || to == ExecutionState.cancelled;
    case ExecutionState.running:
      return executionTerminalStates.contains(to);
    case ExecutionState.succeeded:
    case ExecutionState.failed:
    case ExecutionState.cancelled:
      return false;
  }
}
