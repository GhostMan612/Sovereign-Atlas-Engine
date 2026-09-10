// Sovereign Atlas Engine — atlas_tiles
// Cooperative cancellation handle (observation, not control).
//
// Contract: 2.0-C (held handle) + 2.0-F (cooperative observation).
// - One handle per execution, created by the caller, shared into the
//   execution context. Requesting is an act on the held handle; nothing is
//   passed anywhere to cancel, and no registry, token, tree, or deadline
//   exists (refusals, not gaps).
// - Request and completion stay distinct: this handle records the REQUEST;
//   the lifecycle terminal records what ACTUALLY happened (2.0-F §3).
// Phase 2.0 slice. Depends on atlas_core only (no engine dependency at all).

/// Per-execution cooperative cancellation handle. Plain value semantics for
/// observation; single-writer expectation (the holder requests).
final class ExecutionCancellation {
  bool _requested = false;

  /// Records a cancellation request. Idempotent; late requests after
  /// completion are observable facts, never retroactive edits.
  void requestCancel() {
    _requested = true;
  }

  /// Whether cancellation has been requested (cooperation point check).
  bool get isCancelled => _requested;
}
