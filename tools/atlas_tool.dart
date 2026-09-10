// Sovereign Atlas Engine — workspace tool (ADR-002, stdlib only).
//
// Usage: dart tools/atlas_tool.dart [analyze|format-check|test|all]
// Exit code 0 = clean; non-zero names the failing step. No dependencies,
// no network, no inference: every step shells to the Dart SDK.
import 'dart:io';

const packages = [
  'atlas_analysis',
  'atlas_core',
  'atlas_data',
  'atlas_geo',
  'atlas_history',
  'atlas_layers',
  'atlas_location',
  'atlas_map',
  'atlas_offline',
  'atlas_plugins',
  'atlas_provider_api',
  'atlas_providers',
  'atlas_security',
  'atlas_tactical',
  'atlas_terrain',
  'atlas_tiles',
];

Future<void> main(List<String> args) async {
  final step = args.isEmpty ? 'all' : args.first;
  var failed = false;
  Future<void> run(
    String label,
    String exe,
    List<String> argv, {
    String? workdir,
  }) async {
    stdout.writeln('atlas_tool [$label] $exe ${argv.join(' ')}');
    final result = await Process.run(
      exe,
      argv,
      workingDirectory: workdir,
      runInShell: true,
    );
    stdout.write(result.stdout);
    stderr.write(result.stderr);
    if (result.exitCode != 0) {
      failed = true;
      stderr.writeln('atlas_tool FAILED: $label');
    }
  }

  if (step == 'analyze' || step == 'all') {
    for (final package in packages) {
      await run('analyze $package', 'dart', ['analyze', 'packages/$package']);
    }
    await run('analyze runner', 'dart', [
      'analyze',
      'test/phase05_runner.dart',
    ]);
  }
  if (step == 'format-check' || step == 'all') {
    await run('format-check', 'dart', [
      'format',
      '--output=none',
      '--set-exit-if-changed',
      'packages/atlas_analysis',
      'packages/atlas_core',
      'packages/atlas_data',
      'packages/atlas_geo',
      'packages/atlas_history',
      'packages/atlas_layers',
      'packages/atlas_location',
      'packages/atlas_map',
      'packages/atlas_offline',
      'packages/atlas_plugins',
      'packages/atlas_provider_api',
      'packages/atlas_providers',
      'packages/atlas_security',
      'packages/atlas_tactical',
      'packages/atlas_terrain',
      'packages/atlas_tiles',
      'test/phase05_runner.dart',
      'tools/atlas_tool.dart',
    ]);
  }
  if (step == 'test' || step == 'all') {
    await run('regression', 'dart', ['test/phase05_runner.dart']);
  }
  if (failed) exit(1);
  stdout.writeln('atlas_tool [$step] clean');
}
