// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_provider_api/atlas_provider_api.dart';
import '../entries/cache_entry.dart';
import 'execution_context.dart';

final class ExecutionCancelled {
  const ExecutionCancelled();
}

abstract class AtlasExecutionOperation {

  Future<AtlasCacheEntry> serveEntry(
    AtlasCacheEntry entry,
    ExecutionContext context,
  );

  Future<AtlasAcquisitionResult> runAcquisition(
    AtlasAcquisitionRequest request,
    ExecutionContext context,
  );

  Future<AtlasCacheEntry> storeHandoff(
    AtlasCacheEntry handoff,
    ExecutionContext context,
  );
}

final class AtlasOperationBinding {
  const AtlasOperationBinding([
    Map<AtlasResourceIdentity, AtlasExecutionOperation>? operations,
  ]) : _operations = operations ?? const {};

  final Map<AtlasResourceIdentity, AtlasExecutionOperation> _operations;

  AtlasExecutionOperation? lookup(AtlasResourceIdentity identity) =>
      _operations[identity];
}
