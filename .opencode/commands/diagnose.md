---
description: Diagnose a "nothing happens" bug end to end across UI, adapter, pure logic, and the wire
agent: build
---

Trace a Sovereign Atlas feature that appears to do nothing, from the tap to the data. Do not
edit anything.

The question is almost always answered by naming the FIRST place the chain can silently
stop. Walk the hops and cite `file:line` at each:

1. `ui/` Compose entry point and its state holder. In the map screen this is usually a
   remembered `MutableStateFlow` inside `AtlasMap.kt`.
2. The controller or provider in `map/` or a platform package that does the work.
3. The pure-logic package that computes it: `geo/`, `offline/`, `track/`, `measure/`,
   `tactical/`, `core/`.
4. The edge: DataStore, SQLite, a repository, `AtakBroadcaster`, or `PackTileServer`.

Then check the silent-failure points, which is where these bugs actually live:
- A `runCatching` that discards the error, or an early `return` that reads as success.
  `AtakMulticastListener.sendMulticast` did exactly this: it returned on a null socket and
  swallowed `send` failures, so a dead radio looked identical to a working one.
- A guard that throws where the caller swallows it. `sendMarker` correctly refuses under
  Radio Silence, but the message must reach the operator.
- `getSourceAs<GeoJsonSource>(id)` returning null instead of throwing, so a push is a no-op.
- A layer that exists in `style.layers` but never paints. `addLayerBelow` has produced
  exactly this on device; `style.addLayer` rendered when the identical layer did not.
- `GeoJsonSource.setGeoJson` being asynchronous on the render thread.
- A guard keyed on app state the operator never changed, such as the Network Profile.
- A single-listener MapLibre callback being registered twice, so the first handler is gone.

Report the hop chain with `file:line`, then the first point the chain can silently stop, and
the single cheapest check that would distinguish the candidates. Under 30 lines. Mark
anything unverified as such.
