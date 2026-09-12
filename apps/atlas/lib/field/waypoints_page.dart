// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:flutter/material.dart';
import 'package:latlong2/latlong.dart';

import 'field_journal.dart';

Future<void> showWaypointCreateSheet(
  BuildContext context,
  FieldJournal journal,
  LatLng point,
) {
  return showModalBottomSheet<void>(
    context: context,
    builder: (_) => _WaypointCreateSheet(journal: journal, point: point),
  );
}

Future<String?> showWaypointDetailSheet(
  BuildContext context,
  FieldJournal journal,
  String id,
) {
  final record = journal.lookup(id);
  if (record == null) return Future<String?>.value();
  return showModalBottomSheet<String>(
    context: context,
    builder: (_) => _WaypointDetailSheet(journal: journal, id: id),
  );
}

final class _WaypointCreateSheet extends StatefulWidget {
  const _WaypointCreateSheet({required this.journal, required this.point});

  final FieldJournal journal;
  final LatLng point;

  @override
  State<_WaypointCreateSheet> createState() => _WaypointCreateSheetState();
}

class _WaypointCreateSheetState extends State<_WaypointCreateSheet> {
  final TextEditingController _label = TextEditingController();
  final TextEditingController _note = TextEditingController();

  @override
  void dispose() {
    _label.dispose();
    _note.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Waypoint ${widget.point.latitude.toStringAsFixed(4)}, '
              '${widget.point.longitude.toStringAsFixed(4)}',
            ),
            TextField(
              key: const ValueKey<String>('waypoint-label'),
              controller: _label,
              decoration: const InputDecoration(labelText: 'Label'),
            ),
            TextField(
              key: const ValueKey<String>('waypoint-note'),
              controller: _note,
              decoration: const InputDecoration(labelText: 'Note'),
            ),
            const SizedBox(height: 12.0),
            Row(
              children: [
                ElevatedButton(
                  key: const ValueKey<String>('waypoint-create-save'),
                  onPressed: () {
                    widget.journal.create(
                      latitude: widget.point.latitude,
                      longitude: widget.point.longitude,
                      label: _label.text,
                      note: _note.text,
                    );
                    if (context.mounted) Navigator.of(context).pop();
                  },
                  child: const Text('Save waypoint'),
                ),
                const SizedBox(width: 12.0),
                TextButton(
                  key: const ValueKey<String>('waypoint-create-cancel'),
                  onPressed: () => Navigator.of(context).pop(),
                  child: const Text('Cancel'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

final class WaypointsPage extends StatelessWidget {
  const WaypointsPage({super.key, required this.journal, this.onSelect});

  final FieldJournal journal;
  final void Function(String id)? onSelect;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Waypoints')),
      body: ListenableBuilder(
        listenable: journal,
        builder: (context, _) {
          final error = journal.lastError;
          final records = journal.waypoints;
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
                  child: Text('No waypoints yet. Long-press the map to add one.'),
                ),
              Expanded(
                child: ListView(
                  children: [
                    for (final record in records)
                      ListTile(
                        key: ValueKey<String>('waypoint-${record.id}'),
                        title: Text(
                          record.label.isEmpty ? record.id : record.label,
                        ),
                        subtitle: Text(
                          '${record.latitude.toStringAsFixed(4)}, '
                          '${record.longitude.toStringAsFixed(4)}',
                        ),
                        onTap: () async {
                          final selected = await showWaypointDetailSheet(
                            context,
                            journal,
                            record.id,
                          );
                          if (selected != null) onSelect?.call(selected);
                        },
                      ),
                  ],
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}

final class _WaypointDetailSheet extends StatefulWidget {
  const _WaypointDetailSheet({required this.journal, required this.id});

  final FieldJournal journal;
  final String id;

  @override
  State<_WaypointDetailSheet> createState() => _WaypointDetailSheetState();
}

class _WaypointDetailSheetState extends State<_WaypointDetailSheet> {
  late final TextEditingController _label;
  late final TextEditingController _note;

  @override
  void initState() {
    super.initState();
    final record = widget.journal.lookup(widget.id);
    _label = TextEditingController(text: record?.label ?? '');
    _note = TextEditingController(text: record?.note ?? '');
  }

  @override
  void dispose() {
    _label.dispose();
    _note.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final record = widget.journal.lookup(widget.id);
    if (record == null) return const SizedBox.shrink();
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              '${record.latitude.toStringAsFixed(4)}, '
              '${record.longitude.toStringAsFixed(4)}',
            ),
            Text('Source: ${waypointSourceName(record.source)}'),
            TextField(
              key: const ValueKey<String>('waypoint-edit-label'),
              controller: _label,
              decoration: const InputDecoration(labelText: 'Label'),
            ),
            TextField(
              key: const ValueKey<String>('waypoint-edit-note'),
              controller: _note,
              decoration: const InputDecoration(labelText: 'Note'),
            ),
            const SizedBox(height: 12.0),
            Row(
              children: [
                ElevatedButton(
                  key: const ValueKey<String>('waypoint-save'),
                  onPressed: () {
                    widget.journal.updateLabel(widget.id, _label.text);
                    widget.journal.updateNote(widget.id, _note.text);
                    if (context.mounted) Navigator.of(context).pop();
                  },
                  child: const Text('Save'),
                ),
                const SizedBox(width: 12.0),
                TextButton(
                  key: const ValueKey<String>('waypoint-delete'),
                  onPressed: () {
                    widget.journal.remove(widget.id);
                    if (context.mounted) Navigator.of(context).pop();
                  },
                  child: const Text('Delete'),
                ),
                const SizedBox(width: 12.0),
                ElevatedButton(
                  key: const ValueKey<String>('waypoint-go-to'),
                  onPressed: () {
                    if (context.mounted) {
                      Navigator.of(context).pop(widget.id);
                    }
                  },
                  child: const Text('Go-To'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
