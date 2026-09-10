// Sovereign Atlas Engine — atlas_offline
// Token-bucket rate limiter: pure quota primitive (no timers, no threads).
//
// Contract: phase-3 note §6. Integer-second refill against EXPLICIT time;
// fractional tokens never exist (floor on refill). Standalone value +
// downloader hook (quotaPaused terminal). Enforcement of provider-declared
// rates (blueprint 3.4) consumes this; the limiter itself knows no provider.
// Phase 3 slice. Depends on nothing (pure arithmetic).

/// Pure token-bucket limiter (explicit time, deterministic).
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

  /// Attempts one token at [nowSeconds]. True = admitted (token consumed).
  bool take(int nowSeconds) {
    _refill(nowSeconds);
    if (_available <= 0) return false;
    _available -= 1;
    return true;
  }
}
