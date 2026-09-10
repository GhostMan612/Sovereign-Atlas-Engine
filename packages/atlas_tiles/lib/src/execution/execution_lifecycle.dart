// Sovereign Atlas Engine — atlas_tiles
// Execution lifecycle: states of the serving act (never of the domain).
//
// Contract: 2.0-E (ownership rule — acquisition states stay acquisition's
// vocabulary and travel inside results) + 2.0-F (cancelled terminal).
// - `succeeded` means serving completed and reported truthfully, whatever
//   the carried semantic outcome (a reported failed acquisition is still
//   execution-`succeeded`).
// - `timedOut` is deliberately absent (deadlines are acquisition bounds;
//   timeouts arrive as reported outcomes, never as execution states).
// Phase 2.0 slice. Depends on nothing (pure vocabulary).

/// Serving-act states. Only contract-justified values exist.
enum ExecutionState {
  /// Command exists; serving not begun; no operation touched.
  pending,

  /// Serving begun.
  running,

  /// Serving completed with a truthful report (any carried outcome).
  succeeded,

  /// The serving act malfunctioned (never a semantic failure).
  failed,

  /// Serving stopped by cancellation (never a failure).
  cancelled,
}

/// Terminal states: the only states an execution result may carry (2.0-G).
const executionTerminalStates = {
  ExecutionState.succeeded,
  ExecutionState.failed,
  ExecutionState.cancelled,
};

/// Pure transition validator: the closed 2.0-E §3 set (pending→running,
/// running→terminal, pending→cancelled). Anything else is unrepresentable.
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
