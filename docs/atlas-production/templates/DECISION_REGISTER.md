# Decision register

| Decision | Existing authority checked | Alternatives | Choice | Evidence | Risk | Approval | Status |
|---|---|---|---|---|---|---|---|
| Offline socket I/O boundary | RULES §2; SESSION_HANDOFF | split policy/transport vs retain | TBD | `PackTileServer.kt`, `OfflineDownloader.kt` | purity mismatch | operator/architect | OPEN |
| SDK packaging/modules | ARCHITECTURE.md | internal vs published | TBD | independent consumer | ABI breakage | architect | OPEN |
| Historical dataset license | provider manifest | open/local/partner | TBD | terms | redistribution | operator | OPEN |
| Routing graph/source | offline policy | OSM-derived/other | TBD | license+benchmarks | coverage/storage | architect | OPEN |
| Terrain DEM | RADIO-002 | local DEM/defer | TBD | coverage+resolution | false precision | architect | OPEN |
