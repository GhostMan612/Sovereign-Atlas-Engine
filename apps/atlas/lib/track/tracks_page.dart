// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_geo/atlas_geo.dart';
import 'package:flutter/material.dart';

import '../field/field_journal.dart';
import 'track_recorder.dart';

String formatTrackDuration(int millis) {
  final totalSeconds = millis ~/ 1000;
  final hours = totalSeconds ~/ 3600;
  final minutes = (totalSeconds % 3600) ~/ 60;
  final seconds = totalSeconds % 60;
  final mm = minutes.toString().padLeft(2, '0');
  final ss = seconds.toString().padLeft(2, '0');
  if (hours > 0) return '$hours:$mm:$ss';
  return '$mm:$ss';
}

String formatTrackStart(int createdAt) {
  final start = DateTime.fromMillisecondsSinceEpoch(createdAt).toLocal();
  final month = start.month.toString().padLeft(2, '0');
  final day = start.day.toString().padLeft(2, '0');
  final hour = start.hour.toString().padLeft(2, '0');
  final minute = start.minute.toString().padLeft(2, '0');
  return '${start.year}-$month-$day $hour:$minute';
}

String formatTrackDistance(double lengthMeters) {
  return AtlasGeoMath.formatDistance(lengthMeters / 1000.0);
}

final class TracksPage extends StatelessWidget {
  const TracksPage({super.key, required this.journal, required this.recorder});

  final FieldJournal journal;
  final TrackRecorder recorder;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Tracks')),
      body: Column(
        children: [
          ListenableBuilder(
            listenable: recorder,
            builder: (context, _) {
              if (!recorder.isRecording) {
                return Padding(
                  padding: const EdgeInsets.all(16.0),
                  child: ElevatedButton(
                    key: const ValueKey<String>('track-start'),
                    onPressed: recorder.start,
                    child: const Text('Start recording'),
                  ),
                );
              }
              return Padding(
                padding: const EdgeInsets.all(16.0),
                child: Row(
                  children: [
                    Text(
                      key: const ValueKey<String>('track-status'),
                      'Recording • ${recorder.pointCount} pts',
                    ),
                    const Spacer(),
                    ElevatedButton(
                      key: const ValueKey<String>('track-stop'),
                      onPressed: () {
                        final fixes = recorder.stop();
                        if (fixes.isNotEmpty) {
                          journal.saveTrack(fixes: fixes);
                        }
                      },
                      child: const Text('Stop'),
                    ),
                  ],
                ),
              );
            },
          ),
          Expanded(
            child: ListenableBuilder(
              listenable: journal,
              builder: (context, _) {
                final error = journal.lastError;
                final records = journal.tracks;
                return Column(
                  children: [
                    if (error != null)
                      Padding(
                        padding: const EdgeInsets.all(8.0),
                        child: Text('Journal unavailable: $error'),
                      ),
                    if (records.isEmpty)
                      const Padding(
                        padding: EdgeInsets.all(16.0),
                        child: Text(
                          'No tracks yet. Start recording to capture one.',
                        ),
                      ),
                    Expanded(
                      child: ListView(
                        children: [
                          for (final record in records)
                            ListTile(
                              key: ValueKey<String>('track-${record.id}'),
                              title: Text(
                                '${record.id} · ${record.pointCount} pts',
                              ),
                              subtitle: Text(
                                formatTrackDistance(
                                  record.toTrack().lengthMeters,
                                ),
                              ),
                              onTap: () => showTrackDetailSheet(
                                context,
                                journal,
                                record.id,
                              ),
                            ),
                        ],
                      ),
                    ),
                  ],
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}

Future<void> showTrackDetailSheet(
  BuildContext context,
  FieldJournal journal,
  String id,
) {
  final record = journal.lookupTrack(id);
  if (record == null) return Future<void>.value();
  return showModalBottomSheet<void>(
    context: context,
    builder: (_) => _TrackDetailSheet(journal: journal, id: id),
  );
}

final class _TrackDetailSheet extends StatelessWidget {
  const _TrackDetailSheet({required this.journal, required this.id});

  final FieldJournal journal;
  final String id;

  @override
  Widget build(BuildContext context) {
    final record = journal.lookupTrack(id);
    if (record == null) return const SizedBox.shrink();
    final track = record.toTrack();
    final points = track.points;
    final durationMs = points.isEmpty
        ? 0
        : points.last.createdAt - points.first.createdAt;
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(record.id),
            Text('Points: ${record.pointCount}'),
            Text(
              'Distance: ${formatTrackDistance(track.lengthMeters)}',
            ),
            Text('Started: ${formatTrackStart(record.createdAt)}'),
            Text('Duration: ${formatTrackDuration(durationMs)}'),
            const SizedBox(height: 12.0),
            Row(
              children: [
                TextButton(
                  key: const ValueKey<String>('track-delete'),
                  onPressed: () {
                    journal.removeTrack(id);
                    if (context.mounted) Navigator.of(context).pop();
                  },
                  child: const Text('Delete'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
