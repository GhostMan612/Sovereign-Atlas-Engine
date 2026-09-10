// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

final class ExecutionCancellation {
  bool _requested = false;

  void requestCancel() {
    _requested = true;
  }

  bool get isCancelled => _requested;
}
