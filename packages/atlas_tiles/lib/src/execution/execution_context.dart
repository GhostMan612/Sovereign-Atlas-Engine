// Sovereign Atlas Engine — atlas_tiles
// Execution context: the closed set of explicit execution inputs.
//
// Contract: 2.0-C (closed boundary — an execution cannot discover hidden
// environmental state) + 2.0-H (binding declaration owned by the caller).
// - Time is an epoch-int anchor (no clock object, no DateTime).
// - Cancellation is the held per-execution handle (no registry/tokens).
// - Binding is the explicit identity→operation declaration (miss means
//   unsupported downstream, never probing here).
// Phase 2.0 slice. Depends on atlas_core + atlas_provider_api (+ siblings).

import 'cancellation.dart';
import 'operation_binding.dart';

/// Explicit execution inputs. Three members, no ambient state.
final class ExecutionContext {
  const ExecutionContext({
    required this.nowSeconds,
    required this.cancellation,
    required this.binding,
  });

  /// Explicit time anchor (epoch seconds). The only authorized time value.
  final int nowSeconds;

  /// Held per-execution cancellation handle (2.0-F cooperation).
  final ExecutionCancellation cancellation;

  /// Explicit identity→operation declaration (2.0-H ownership: caller).
  final AtlasOperationBinding binding;
}
