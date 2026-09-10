// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'cancellation.dart';
import 'operation_binding.dart';

final class ExecutionContext {
  const ExecutionContext({
    required this.nowSeconds,
    required this.cancellation,
    required this.binding,
  });

  final int nowSeconds;

  final ExecutionCancellation cancellation;

  final AtlasOperationBinding binding;
}
