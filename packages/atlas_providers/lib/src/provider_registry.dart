// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_provider_api/atlas_provider_api.dart';
import 'provider_endpoint.dart';

final class AtlasProviderRegistry {
  AtlasProviderRegistry([Iterable<AtlasProviderEndpoint>? endpoints])
      : _endpoints = {} {
    for (final endpoint in endpoints ?? const <AtlasProviderEndpoint>[]) {
      register(endpoint);
    }
  }

  final Map<String, AtlasProviderEndpoint> _endpoints;

  void register(AtlasProviderEndpoint endpoint) {
    final id = endpoint.descriptor.id.value;
    if (_endpoints.containsKey(id)) {
      throw StateError('duplicate provider id: $id');
    }
    _endpoints[id] = endpoint;
  }

  AtlasProviderEndpoint? lookup(String id) => _endpoints[id];

  List<AtlasProviderEndpoint> providersFor(AtlasDataKind kind) =>
      _endpoints.values
          .where((e) => e.descriptor.kinds.contains(kind))
          .toList();

  List<String> get ids => _endpoints.keys.toList();

  List<AtlasProviderDescriptor> get descriptors =>
      _endpoints.values.map((e) => e.descriptor).toList();
}
