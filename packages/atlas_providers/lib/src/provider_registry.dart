// Sovereign Atlas Engine — atlas_providers
// Provider registry: explicit closed declaration of available providers.
//
// Contract: blueprint 2.1 (registration) + 2.0-H (explicit declaration, no
// discovery). Registration order is catalog order (deterministic; feeds
// resolution eligibility + attribution order). Duplicates are programmer
// misuse (loud StateError, never silent replace).
// Phase 2 slice. Depends on atlas_core + atlas_provider_api only.

import '../../../atlas_provider_api/lib/atlas_provider_api.dart';
import 'provider_endpoint.dart';

/// Closed provider catalog value.
final class AtlasProviderRegistry {
  AtlasProviderRegistry([Iterable<AtlasProviderEndpoint>? endpoints])
    : _endpoints = {} {
    for (final endpoint in endpoints ?? const <AtlasProviderEndpoint>[]) {
      register(endpoint);
    }
  }

  final Map<String, AtlasProviderEndpoint> _endpoints;

  /// Registers [endpoint]. Duplicate ids throw (closed catalog honesty).
  void register(AtlasProviderEndpoint endpoint) {
    final id = endpoint.descriptor.id.value;
    if (_endpoints.containsKey(id)) {
      throw StateError('duplicate provider id: $id');
    }
    _endpoints[id] = endpoint;
  }

  /// Exact lookup. Null = unregistered (callers map to unsupported).
  AtlasProviderEndpoint? lookup(String id) => _endpoints[id];

  /// Endpoints serving [kind], in registration order.
  List<AtlasProviderEndpoint> providersFor(AtlasDataKind kind) => _endpoints
      .values
      .where((e) => e.descriptor.kinds.contains(kind))
      .toList();

  /// Registered ids in registration order.
  List<String> get ids => _endpoints.keys.toList();

  /// Descriptors in registration order (feeds resolution catalogs).
  List<AtlasProviderDescriptor> get descriptors =>
      _endpoints.values.map((e) => e.descriptor).toList();
}
