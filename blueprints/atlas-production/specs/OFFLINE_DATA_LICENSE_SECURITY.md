# Offline / provider / security policy

Catalog datasets by owner, source, geographic coverage, zoom, format, update date, license, attribution, redistribution permission, offline storage permission, retention, API keys, rate limit and estimated size. Explicitly distinguish open data from freely viewable proprietary tiles. Never bulk-download against provider restrictions. Offline-first means blank/local style boots without network; no fabricated location; user sees coverage gaps. Tiles, route graphs, DEM and knowledge bundles each have independently verified checksums and atomic update/rollback.

Privacy: location/track recording only on explicit action; clear foreground/background behavior; storage/export/delete controls; encryption policy and backup behavior; secrets outside repo; security review of localhost tile server binding (loopback only, no unsafe file traversal, lifecycle and port collisions). Do not claim offline security without testing actual sockets and persistence.
