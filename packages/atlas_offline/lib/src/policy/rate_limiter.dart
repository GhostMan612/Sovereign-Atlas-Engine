// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

final class AtlasRateLimiter {
  AtlasRateLimiter({
    required this.capacity,
    required this.refillPerSecond,
    int? available,
    int? lastRefill,
  })  : assert(capacity >= 0, 'Capacity must be non-negative.'),
        assert(refillPerSecond >= 0, 'Refill must be non-negative.'),
        _available = available ?? capacity,
        _lastRefill = lastRefill ?? 0;

  final int capacity;
  final int refillPerSecond;
  int _available;
  int _lastRefill;

  int get available => _available;

  void _refill(int nowSeconds) {
    if (nowSeconds <= _lastRefill) return;
    _available = (_available + (nowSeconds - _lastRefill) * refillPerSecond)
        .clamp(0, capacity);
    _lastRefill = nowSeconds;
  }

  bool take(int nowSeconds) {
    _refill(nowSeconds);
    if (_available <= 0) return false;
    _available -= 1;
    return true;
  }
}
