# ADR-011: Official Meshtastic Protobuf Protocol, Wire Envelopes, and Transport Readiness

- **Status:** Accepted
- **Date:** 2026-11-14
- **Supersedes / Clarifies:** Initial placeholder transport framing notes in `blueprints/03_MESHTASTIC_LORA_TACTICAL_TRANSPORT.md`
- **Issue:** `#9` — Implement genuine Meshtastic `ToRadio`/`FromRadio` envelopes for BLE and USB

## Context

Prior to this decision, `AndroidBleRadioClient` and `AndroidUsbSerialRadioClient` bypassed Meshtastic's device client protocol:
1. Both transports forwarded raw application bytes rather than `ToRadio` / `FromRadio` protobuf envelopes (`MeshPacket`, `Data`, `Routing`, `MyNodeInfo`, `NodeInfo`, `DeviceMetadata`, `QueueStatus`, `Config.LoRaConfig`).
2. `MeshtasticWireModels.kt` contained incorrect `PortNum` assignments (`32` for `ROUTING_APP`, `67` for `ATAK_PLUGIN`, `69` for `TELEMETRY_APP`) that diverged from official `meshtastic/portnums.proto` (`5`, `72`, `67`).
3. `AndroidBleRadioClient.kt` used non-standard UUIDs for `ToRadio` / `FromRadio`, omitted the `FromNum` notification characteristic, lacked MTU negotiation and serialized GATT queueing, and did not drain `FromRadio` iteratively until empty.
4. Both transports fabricated `RadioHardwareInfo` (`"915 MHz US"`, `"2.3.x"`, local MAC hash / USB `deviceId`) and marked the link ready for transmission before completing a `want_config_id` handshake.

## Decision

### 1. Pinned Upstream Protobuf Schemas & Code Generation

We pin the exact transitive dependency closure (10 `.proto` files rooted at `meshtastic/mesh.proto`) from `meshtastic/protobufs` tag `v2.8.2` (`commit ffd5ad428da6db4a7ddfe62e06548718cc2603e0`) into `apps/atlas-android/atlas-sdk/src/main/proto/meshtastic/` alongside:
- `UPSTREAM_PIN.json` (repository, tag, commit SHA, entrypoint, per-file byte size and SHA-256 digest),
- `LICENSE-GPL-3.0.txt` (verbatim upstream license notice).

Code generation is performed via `com.squareup.wire` `6.4.7` (`wire-runtime:6.4.7`, Apache-2.0) directly in `:atlas-sdk` with `includes = ['meshtastic.*']` and `excludes = ['google.protobuf.*']`, keeping `:app` and `:atlas-sdk` aligned on one Wire toolchain version while keeping generated Wire types free of Android/Compose/MapLibre dependencies.

### 2. Shared Pure Wire Adapter & Protocol Session in `:atlas-sdk`

All wire marshaling and protocol state live in pure JVM package `com.sovereignatlas.atlas.tactical.mesh`:
- `MeshtasticWireAdapter`: maps Atlas domain `MeshPacket`, `PortNum`, `RadioHardwareInfo`, `MeshNodeInfo`, and `QueueStatus` / `Routing.Error` to and from `org.meshtastic.proto.*` Wire messages, strictly preserving uint32 node IDs, signed fixed-point coordinates (`1e-7` degrees), `channel`, `wantAck`, `wantResponse`, `requestId`, `replyId`, `hopLimit`, `hopStart`, and enforcing `MAX_TO_FROM_RADIO_SIZE = 512` bytes.
- `MeshtasticProtocolSession`: transport-agnostic state machine shared by BLE and USB serial transports.

### 3. Official BLE GATT and USB Serial Transport Contracts

- **BLE GATT (`AndroidBleRadioClient` + `BleGattTransport`):**
  - Service UUID: `6ba1b218-15a8-461f-9fa8-5dcae273eafd`
  - `ToRadio` (write): `f75c76d2-129e-4dad-a1dd-7866124401e7`
  - `FromRadio` (read): `2c55e69e-4993-11ed-b878-0242ac120002`
  - `FromNum` (notify via CCCD `00002902-0000-1000-8000-00805f9b34fb`): `ed9da18c-a800-4f66-a670-aa7547e34453`
  - No `0x94 0xC3` serial framing is used over BLE.
  - All GATT requests (`requestMtu`, `discoverServices`, descriptor write, characteristic write, characteristic read) execute through a serialized single-flight operation queue.
  - Negotiated ATT payload capacity `(mtu - 3).coerceAtLeast(20)` (capped at `512`) is enforced prior to writing `ToRadio`.
  - After enabling notifications and writing `ToRadio(want_config_id)`, and on every `FromNum` notification, the client drains `FromRadio` via sequential reads until `FromRadio` returns an empty byte array (`0` bytes).
- **USB Serial (`AndroidUsbSerialRadioClient` + `UsbSerialTransport`):**
  - FTDI vendor `0x0403` is unsupported: its input status bytes and vendor-specific initialization are not implemented. Reject it even when it advertises a CDC interface. Other hardware support remains subject to real-device acceptance.
  - Uses `MeshtasticFrameCodec` (`0x94 0xC3` magic + 2-byte big-endian length, maximum `512` bytes) for both `ToRadio` writes and `FromRadio` reads, prefixed on connect by a 32-byte `0xC3` wake preamble (`StreamAPI` compatible).
  - Read loop begins before transmitting `want_config_id` so early handshake frames are never dropped.
  - Reassembles partial/split USB bulk transfers across read calls and distinguishes benign read poll timeouts from terminal I/O / detach errors.

### 4. Handshake Readiness Gate, Delivery Tracking & EMCON Compliance

- **Readiness Gate:** Connection begins at `CONNECTING`, moves to `HANDSHAKING` when `ToRadio(want_config_id = nonce)` is queued, and transitions to `CONNECTED` (`isReady = true`) strictly after receiving BOTH `FromRadio.my_info` (`MyNodeInfo`) and a `FromRadio.config_complete_id` matching the active non-zero `nonce`. `MeshTransportSupervisor` gates radio transmissions on `radioClient.isReady` in addition to `NetworkProfile` EMCON rules (`RADIO_SILENCE` and `CLOUD_ONLY` always refuse TX).
- **Packet Delivery Stages:** Outgoing packets carry allocated non-zero uint32 packet IDs and emit `PacketDeliveryUpdate`:
  1. `QUEUED`: submitted to transport queue,
  2. `RADIO_ACCEPTED`: `FromRadio.queueStatus` confirms `mesh_packet_id == id` with `res == 0`,
  3. `PEER_DELIVERED`: inbound `PortNum.PORTNUM_ROUTING_APP` (`5`) packet carries `decoded.request_id == id` and `Routing.error_reason == NONE` (and `Routing.variant` is not a NAK error),
  4. `FAILED`: `QueueStatus.res != 0`, `Routing.Error != NONE`, ATT/envelope size exceeded, handshake not ready, or transport write failure.

### 5. Honest Telemetry & Modem Failure Behavior

- Unknown radio fields (`radioModel`, `frequencyBand`, `firmwareVersion`, `snr`, `rssi`, `batteryLevel`, GPS coordinates) remain `null` or render `"Unknown"` in UI overlays/sheets until reported by `DeviceMetadata`, `Config.lora.region`, `NodeInfo`, `Position`, or `Telemetry`. Region `UNSET` (`0`) and `HardwareModel.UNSET` (`0`) map to `null` rather than defaulting to `"915 MHz US"`.
- If `FromRadio.rebooted` is received, if a `want_config_id` handshake times out, if GATT/USB disconnects or errors, or if malformed protobuf payloads fail decoding:
  - Session readiness resets (`isReady = false`, node/hardware snapshot clears on disconnect or reboot),
  - Malformed frames increment diagnostic drop counters without crashing the read loop or mutating verified node state.

## Consequences

- `:atlas-sdk` exposes `api 'com.squareup.wire:wire-runtime:6.4.7'` and compiled `org.meshtastic.proto.*` messages alongside pure Kotlin `MeshtasticWireAdapter` and `MeshtasticProtocolSession`.
- Isolated consumers (`:sdk-consumer`, `samples/sdk-consumer`) require `mavenCentral()` for `wire-runtime` alongside the published `:atlas-sdk` Maven artifact (`com.sovereignatlas:atlas-sdk:1.1.0`).
- Upstream protobuf schemas under `atlas-sdk/src/main/proto/meshtastic/` carry explicit `GPL-3.0` notice (`LICENSE-GPL-3.0.txt`) recorded in `DATA_LICENSE_SECURITY_SBOM.md`.
