# BP-03: Plug-and-Play Meshtastic & LoRa Tactical Transport

> **Status:** COMPLETE & INTEGRATED (Phase 13, Commit Verified 2026-10-23)  
> **Target:** `tactical/mesh/`, `android/transport/`, `apps/atlas-android/`  
> **Lineage & Source Ground Truth:** Architecture adapted from `C:\sovereign_mantle\` DTN reassembly patterns (`FragmentStore`, `FragmentAckCodec`) & official Meshtastic Protobuf specifications.

---

## 1. Executive Summary

The Sovereign Atlas Engine currently broadcasts and receives Cursor-on-Target (CoT) and Position Location Information (PLI) via ATAK-compatible multicast UDP on Wi-Fi/Ethernet subnets (`239.2.3.1:6969`).

While highly effective on local Wi-Fi, modern off-grid operations require long-range radio telemetry spanning miles across mountains and tree canopies where Wi-Fi cannot reach.

This blueprint establishes:
1. **Meshtastic Radio Hardware Integration:** Direct USB-Serial (OTG) and Bluetooth Low Energy (BLE) connectivity to off-the-shelf Meshtastic hardware (Heltec V3, T-Beam, RAK4631, LilyGO T-Echo) using the official Meshtastic Client Protobuf API (`ToRadio` / `FromRadio`).
2. **LoRa MTU Adaptation & DTN Reassembly:** Handling payload constraints across 237-byte LoRa radio frames using compact Protobuf payloads and delay-tolerant chunk reassembly based on Mantle's `FragmentStore` and `FragmentAckCodec` patterns.
3. **Strict Tactical Profile Interlocks:** Enforcing `AGENTS.md` law: CoT marker transmissions require **"Off-Grid Mesh Only"** or **"Hybrid"** network profiles. When in **Radio Silence** (EMCON) or **Cloud-Only**, transmissions are strictly blocked at the supervisor layer.

---

## 2. Architectural Blueprint & Lineage

```text
  [ Atlas Tactical Engine (CoT / PLI / Markers) ]
                        │
          TacticalMeshBridge (Pure Domain)
                        │
         ┌──────────────┴──────────────┐
         ▼                             ▼
   Local ATAK UDP               MeshtasticRadioClient (Adapter)
  (Multicast 6969)                     │
                               ┌───────┴───────┐
                               ▼               ▼
                           USB-Serial     BLE Peripheral
                           (CDC/CP210x)   (Meshtastic BLE Service)
                               │               │
                        [ Heltec V3 / RAK4631 / T-Beam ]
                               │
                        ((( 915MHz / 868MHz LoRa )))
```

### 2.1 Proven Components Adapted from `Sovereign_Mantle`
The following core assets from `C:\sovereign_mantle\` inform Atlas mesh logic:

| Component / Pattern | Atlas Role & Destination | Purity Classification |
|---|---|---|
| `FragmentStore` (from `com.sovereign.mantle.dtn`) | Out-of-order chunk storage, expiration TTL, and reassembly | **Pure Domain** (`tactical/dtn/`) |
| `FragmentAckCodec.kt` | Encodes/decodes `FragmentAckReport` (high-water mark + missing bitmap) | **Pure Domain** (`tactical/mesh/`) |
| `PeerPenaltyLedger.kt` | Tracks peer node health, latency, and penalizes malformed radios | **Pure Domain** (`tactical/mesh/`) |
| `MeshtasticRadioClient` | Handles Meshtastic protobuf wire communication (`ToRadio`/`FromRadio`) | **Android Transport** (`android/mesh/`) |

---

## 3. Protocol & Packet Specifications

### 3.1 Meshtastic Wire Protocol
External host devices (Android phones) do not emit raw RF LoRa chirps directly; they communicate with the connected Meshtastic radio firmware over serial or BLE using **Meshtastic Protobufs**:
* **BLE Service:** `6ba1b218-15a8-461f-9fa8-5dcae273eafd`
  - `toRadio` Characteristic (`f75de93c-4530-4ec4-b399-f442377b8359`): Host writes serialized `ToRadio` packets.
  - `fromRadio` Characteristic (`2c55e69e-4993-11ea-b77f-2e728ce88125`): Radio notifies serialized `FromRadio` packets.
* **USB-Serial Framing:** Standard Meshtastic 4-byte framing header (`0x94 0xC3` magic sync bytes, followed by 16-bit big-endian payload length) encapsulating `ToRadio`/`FromRadio` protobuf payloads.
* **Port Numbers:** CoT markers and tactical telemetry are dispatched on `PortNum.PORTNUM_ATAK_PLUGIN` or `PortNum.PORTNUM_PRIVATE_APP`.

### 3.2 LoRa Payload Compression
A raw XML CoT marker can be 600–1200 bytes, exceeding the 237-byte LoRa physical MTU:
1. **Compact Protobuf CoT:** Convert XML CoT markers to compact Protobuf binary representations (reducing typical marker footprints to $\le 120$ bytes, fitting comfortably within a single LoRa frame).
2. **Chunking & Multi-Packet Transport:** For large mission payloads (tactical overlays, route plans), large bundles are partitioned into sequenced chunks with `FragmentAckReport` tracking.

### 3.3 Hardware Drivers (`android/mesh/hardware/`)
* **USB-Serial Driver:**
  - Implements Android USB Host API (`UsbManager`, `UsbDeviceConnection`).
  - Supports CH340, CP2102, FTDI, and CDC-ACM without root permissions.
  - Default baud: 115200 8N1.
* **BLE Driver:**
  - Background BLE scanner and GATT client targeting the Meshtastic BLE Service UUID.

---

## 4. UI/UX Integration (Sovereign Cockpit)

1. **Hardware Link Pill in HUD:**
   - When a LoRa radio is connected, top-left telemetry renders:  
     `LORA: CONNECTED • 915 MHz • 4 NODES`
   - Active radio icon pulses cyan on TX, amber on RX.
2. **Node Directory in Sovereign Command Center:**
   - Tap "LoRa Mesh" row in Command Center to open the **Mesh Node Roster**:
     - Lists discovered nodes, hop counts, battery percentage, SNR (Signal-to-Noise Ratio), and RSSI.
     - One-tap "Ping Node" and "Send Direct Message".
3. **Strict Network Profile Interlocks:**
   - Transmissions are gated by `AtlasServices.networkProfile`.
   - **Allowed:** "Off-Grid Mesh Only" and "Hybrid".
   - **Refused:** "Radio Silence" (hardware listen-only, TX disabled) and "Cloud-Only".

---

## 5. Implementation Roadmap
* **Step 1:** Implement pure `FragmentStore` and `FragmentAckCodec` in `:atlas-sdk:tactical.mesh` with 100% deterministic unit tests.
* **Step 2:** Define pure `MeshRadioClient` interface in domain logic with zero Android platform imports.
* **Step 3:** Implement `AndroidUsbSerialRadioClient` and `AndroidBleRadioClient` in `android/mesh/` using Meshtastic Protobuf framing.
* **Step 4:** Integrate received peer tracks and markers into `AtlasServices.markerStore` and `pliStore`.
