// Sovereign Atlas Engine — atlas_providers
// Tile fetch + local bundle operations: the first REAL executor bindings.
//
// Contract: ADR-003 + 2.0-J (one command, one attempt, one report) + 2.0-L
// (transport failure ⇒ semantic `unavailable`, truthfully collapsible detail).
// - Address parsing is the inverse of the canonical rendering
//   (`z=<z>/x=<x>/y=<y>@<scheme>`); unparseable tile-kind addresses ⇒
//   `invalidTarget`; non-tile kinds ⇒ `unsupported` (tile fetchers serve
//   tiles only — kind honesty, never coercion).
// - Transport is INJECTED (`AtlasTransport`); production wires dart:io,
//   tests wire fakes. Fetched bytes are DISCARDED after receipt: the result
//   carries payloadId = resource address (deterministic, no invented ids).
// - serveEntry/storeHandoff throw UnsupportedError (documented Phase-3 seam:
//   serving without a store would conflate cache/acquisition, 2.0-I/J).
// Phase 2 slice. Depends on core + provider_api + tiles (operation iface).

import 'dart:io';

import '../../../atlas_core/lib/atlas_core.dart';
import '../../../atlas_provider_api/lib/atlas_provider_api.dart';
import '../../../atlas_tiles/lib/atlas_tiles.dart';
import 'provider_endpoint.dart';

/// Injected byte transport: URL + headers in, raw bytes out (or throw).
/// Production: [httpTransport]. Tests: fakes. No other transport exists.
typedef AtlasTransport = Future<List<int>> Function(
  Uri url,
  Map<String, String> headers,
);

/// Transport failure detail (operations collapse this to `unavailable`).
final class AtlasTransportException implements Exception {
  const AtlasTransportException(this.message, {this.statusCode});

  final String message;
  final int? statusCode;

  @override
  String toString() =>
      'AtlasTransportException(${statusCode ?? 'no-status'}: $message)';
}

/// Production transport over dart:io (non-2xx ⇒ throw; redirects followed
/// by HttpClient default policy).
Future<List<int>> httpTransport(Uri url, Map<String, String> headers) async {
  final client = HttpClient();
  try {
    final request = await client.getUrl(url);
    headers.forEach(request.headers.set);
    final response = await request.close();
    final bytes = await response.fold<List<int>>(
      <int>[],
      (acc, chunk) => acc..addAll(chunk),
    );
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw AtlasTransportException(
        'unexpected status ${response.statusCode} for $url',
        statusCode: response.statusCode,
      );
    }
    return bytes;
  } finally {
    client.close();
  }
}

/// Parses the canonical tile address rendering back to coordinate + scheme.
/// Null = not tile-form (caller maps to invalidTarget/unsupported).
({AtlasTileCoordinate coordinate, AtlasTileScheme scheme})? parseTileAddress(
  String address,
) {
  final match = RegExp(r'^z=(\d+)/x=(\d+)/y=(\d+)@([A-Za-z0-9_]+)$')
      .firstMatch(address);
  if (match == null) return null;
  AtlasTileScheme? scheme;
  for (final candidate in AtlasTileScheme.values) {
    if (candidate.name == match.group(4)) scheme = candidate;
  }
  if (scheme == null) return null;
  return (
    coordinate: AtlasTileCoordinate(
      z: int.parse(match.group(1)!),
      x: int.parse(match.group(2)!),
      y: int.parse(match.group(3)!),
    ),
    scheme: scheme,
  );
}

/// Executor operation fetching tiles for one endpoint definition.
final class AtlasTileFetchOperation implements AtlasExecutionOperation {
  AtlasTileFetchOperation({required this.endpoint, required this.transport});

  final AtlasProviderEndpoint endpoint;
  final AtlasTransport transport;

  @override
  Future<AtlasAcquisitionResult> runAcquisition(
    AtlasAcquisitionRequest request,
    ExecutionContext context,
  ) async {
    final resource = request.resource;
    if (!request.validate().isValid) {
      return AtlasAcquisitionResult(
        request: request,
        state: AtlasAcquisitionState.failed,
        failure: AtlasAcquisitionFailure.invalidTarget,
      );
    }
    if (resource.kind != AtlasDataKind.rasterTiles &&
        resource.kind != AtlasDataKind.vectorTiles) {
      return AtlasAcquisitionResult(
        request: request,
        state: AtlasAcquisitionState.failed,
        failure: AtlasAcquisitionFailure.unsupported,
      );
    }
    if (endpoint.policy.requiresKey) {
      // No key plumbing exists (secrets ADR open): truthful refusal.
      return AtlasAcquisitionResult(
        request: request,
        state: AtlasAcquisitionState.failed,
        failure: AtlasAcquisitionFailure.policyRejected,
      );
    }
    final parsed = parseTileAddress(resource.address);
    if (parsed == null) {
      return AtlasAcquisitionResult(
        request: request,
        state: AtlasAcquisitionState.failed,
        failure: AtlasAcquisitionFailure.invalidTarget,
      );
    }
    if (context.cancellation.isCancelled) {
      throw const ExecutionCancelled();
    }
    final identity = AtlasTileIdentity(
      provider: resource.provider,
      layer: const AtlasId(''),
      coordinate: parsed.coordinate,
      scheme: parsed.scheme,
    );
    final url = AtlasTileRequest(
      identity: identity,
      params: endpoint.params,
    ).resolveUrl(endpoint.urlTemplate!);
    try {
      await transport(Uri.parse(url), endpoint.headers);
    } on ExecutionCancelled {
      rethrow;
    } catch (_) {
      // Any transport failure: the resource cannot currently be provided
      // (1.8 `unavailable`, retryable advisory preserved downstream).
      return AtlasAcquisitionResult(
        request: request,
        state: AtlasAcquisitionState.failed,
        failure: AtlasAcquisitionFailure.unavailable,
      );
    }
    // Bytes discarded on receipt; the reference IS the address
    // (deterministic — no invented payload ids).
    return AtlasAcquisition.start(
      request,
      context.nowSeconds,
    ).complete(AtlasId(resource.address), context.nowSeconds).toResult();
  }

  @override
  Future<AtlasCacheEntry> serveEntry(
    AtlasCacheEntry entry,
    ExecutionContext context,
  ) => throw UnsupportedError(
    'tile fetch operations do not serve cache entries (Phase-3 store scope)',
  );

  @override
  Future<AtlasCacheEntry> storeHandoff(
    AtlasCacheEntry handoff,
    ExecutionContext context,
  ) => throw UnsupportedError(
    'tile fetch operations do not store handoffs (Phase-3 store scope)',
  );
}

/// File-tree bundle reader (flat `{root}/{z}/{x}/{y}.{ext}`, no sqlite).
typedef AtlasFileReader = Future<List<int>?> Function(String path);

/// Executor operation serving a local file-tree bundle.
final class AtlasLocalBundleOperation implements AtlasExecutionOperation {
  AtlasLocalBundleOperation({
    required this.endpoint,
    required this.root,
    required this.ext,
    AtlasFileReader? readFile,
  }) : _readFile = readFile ?? _diskRead;

  final AtlasProviderEndpoint endpoint;
  final String root;
  final String ext;
  final AtlasFileReader _readFile;

  static Future<List<int>?> _diskRead(String path) async {
    final file = File(path);
    if (!await file.exists()) return null;
    return file.readAsBytes();
  }

  @override
  Future<AtlasAcquisitionResult> runAcquisition(
    AtlasAcquisitionRequest request,
    ExecutionContext context,
  ) async {
    final resource = request.resource;
    if (!request.validate().isValid) {
      return AtlasAcquisitionResult(
        request: request,
        state: AtlasAcquisitionState.failed,
        failure: AtlasAcquisitionFailure.invalidTarget,
      );
    }
    if (resource.kind != AtlasDataKind.rasterTiles &&
        resource.kind != AtlasDataKind.vectorTiles) {
      return AtlasAcquisitionResult(
        request: request,
        state: AtlasAcquisitionState.failed,
        failure: AtlasAcquisitionFailure.unsupported,
      );
    }
    final parsed = parseTileAddress(resource.address);
    if (parsed == null) {
      return AtlasAcquisitionResult(
        request: request,
        state: AtlasAcquisitionState.failed,
        failure: AtlasAcquisitionFailure.invalidTarget,
      );
    }
    if (context.cancellation.isCancelled) {
      throw const ExecutionCancelled();
    }
    final path =
        '$root/${parsed.coordinate.z}/${parsed.coordinate.x}/'
        '${parsed.coordinate.y}.$ext';
    final bytes = await _readFile(path);
    if (bytes == null) {
      return AtlasAcquisitionResult(
        request: request,
        state: AtlasAcquisitionState.failed,
        failure: AtlasAcquisitionFailure.unavailable,
      );
    }
    return AtlasAcquisition.start(
      request,
      context.nowSeconds,
    ).complete(AtlasId(resource.address), context.nowSeconds).toResult();
  }

  @override
  Future<AtlasCacheEntry> serveEntry(
    AtlasCacheEntry entry,
    ExecutionContext context,
  ) => throw UnsupportedError(
    'bundle operations do not serve cache entries (Phase-3 store scope)',
  );

  @override
  Future<AtlasCacheEntry> storeHandoff(
    AtlasCacheEntry handoff,
    ExecutionContext context,
  ) => throw UnsupportedError(
    'bundle operations do not store handoffs (Phase-3 store scope)',
  );
}
