// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'dart:io';

import 'package:atlas_core/atlas_core.dart';
import 'package:atlas_provider_api/atlas_provider_api.dart';
import 'package:atlas_tiles/atlas_tiles.dart';
import 'provider_endpoint.dart';

typedef AtlasTransport = Future<List<int>> Function(
  Uri url,
  Map<String, String> headers,
);

final class AtlasTransportException implements Exception {
  const AtlasTransportException(this.message, {this.statusCode});

  final String message;
  final int? statusCode;

  @override
  String toString() =>
      'AtlasTransportException(${statusCode ?? 'no-status'}: $message)';
}

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

({AtlasTileCoordinate coordinate, AtlasTileScheme scheme})? parseTileAddress(
  String address,
) {
  final match =
      RegExp(r'^z=(\d+)/x=(\d+)/y=(\d+)@([A-Za-z0-9_]+)$').firstMatch(address);
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

    final parsed = parseTileAddress(resource.address);
    if (parsed == null) {
      final tileKind = resource.kind == AtlasDataKind.rasterTiles ||
          resource.kind == AtlasDataKind.vectorTiles;
      return AtlasAcquisitionResult(
        request: request,
        state: AtlasAcquisitionState.failed,
        failure: tileKind
            ? AtlasAcquisitionFailure.invalidTarget
            : AtlasAcquisitionFailure.unsupported,
      );
    }
    if (endpoint.policy.requiresKey) {

      return AtlasAcquisitionResult(
        request: request,
        state: AtlasAcquisitionState.failed,
        failure: AtlasAcquisitionFailure.policyRejected,
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
  ) =>
      throw UnsupportedError(
        'tile fetch operations do not serve cache entries (Phase-3 store scope)',
      );

  @override
  Future<AtlasCacheEntry> storeHandoff(
    AtlasCacheEntry handoff,
    ExecutionContext context,
  ) =>
      throw UnsupportedError(
        'tile fetch operations do not store handoffs (Phase-3 store scope)',
      );
}

typedef AtlasFileReader = Future<List<int>?> Function(String path);

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

    final parsed = parseTileAddress(resource.address);
    if (parsed == null) {
      final tileKind = resource.kind == AtlasDataKind.rasterTiles ||
          resource.kind == AtlasDataKind.vectorTiles;
      return AtlasAcquisitionResult(
        request: request,
        state: AtlasAcquisitionState.failed,
        failure: tileKind
            ? AtlasAcquisitionFailure.invalidTarget
            : AtlasAcquisitionFailure.unsupported,
      );
    }
    if (context.cancellation.isCancelled) {
      throw const ExecutionCancelled();
    }
    final path = '$root/${parsed.coordinate.z}/${parsed.coordinate.x}/'
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
  ) =>
      throw UnsupportedError(
        'bundle operations do not serve cache entries (Phase-3 store scope)',
      );

  @override
  Future<AtlasCacheEntry> storeHandoff(
    AtlasCacheEntry handoff,
    ExecutionContext context,
  ) =>
      throw UnsupportedError(
        'bundle operations do not store handoffs (Phase-3 store scope)',
      );
}
