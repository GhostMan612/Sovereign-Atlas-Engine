# BP-03: Plug-and-Play Meshtastic & LoRa Tactical Transport

> **Status:** APPROVED DESIGN SPECIFICATION  
> **Target:** `tactical/mesh/`, `android/transport/`, `apps/atlas-android/`  
> **Lineage & Source Ground Truth:** Mined directly from `C:\sovereign_mantle\android_node\app\src\main\java\com\sovereign\mantle\mesh\`

---

## 1. Executive Summary

The Sovereign Atlas Engine currently broadcasts and receives Cursor-on-Target (CoT) and Position Location Information (PLI) via ATAK-compatible multicast UDP on Wi-Fi/Ethernet subnets (`239.2.3.1:6969`).

While highly effective on local Wi-Fi, modern off-grid operations require long-range radio telemetry spanning miles across mountains and tree canopies where Wi-Fi cannot reach.

This blueprint extracts and ports the proven mesh architecture from `Sovereign_Mantle` into Atlas, providing:
1. **Meshtastic Radio Hardware Integration:** Direct USB-Serial (OTG) and Bluetooth Low Energy (BLE) connectivity to off-the-shelf Meshtastic hardware (Heltec V3, T-Beam, RAK4631, LilyGO T-Echo).
2. **LoRa MTU Fragmentation & Reassembly:** Using `FragmentAckCodec` and `MeshSubsegmentReassembly` from `Sovereign_Mantle` to fragment large CoT XML and Protobuf packets across 237-byte LoRa radio frames.
3. **Delay-Tolerant Networking (DTN):** Store-and-forward bundle retention (`BundleStore`) so peer markers and emergency alerts propagate across the mesh even when nodes move in and out of radio line-of-sight.

---

## 2. Architectural Blueprint & Mined Lineage

```text
  [ Atlas Tactical Engine (CoT / PLI / Markers) ]
                        │
         TacticalMeshBridge (Pure Domain)
                        │
         ┌──────────────┴──────────────┐
         ▼                             ▼
   Local ATAK UDP               MeshtasticLoRaTransport
  (Multicast 6969)                     │
                               ┌───────┴───────┐
                               ▼               ▼
                           USB-Serial     BLE Peripheral
                           (CP210x/CH340) (Nordic UART/Protobuf)
                               │               │
                        [ Heltec V3 / RAK4631 / T-Beam ]
                               │
                        ((( 915MHz / 868MHz LoRa )))
```

### 2.1 Proven Components from `Sovereign_Mantle`
The following core assets in `C:\sovereign_mantle\` will be ported into `:atlas-sdk` (pure logic) and `:app` (Android radio drivers):

| Sovereign Mantle Component | Atlas Role & Destination | Purity Classification |
|---|---|---|
| `FragmentAckCodec.kt` | Encodes/decodes packets into $\le 237$-byte fragments with bitmask ACK | **Pure Domain** (`tactical/mesh/`) |
| `FragmentHeader.kt` | 8-byte header: `msgId` (16b), `fragIndex` (8b), `fragCount` (8b), `crc` (32b) | **Pure Domain** (`tactical/mesh/`) |
| `MeshSubsegmentReassembly.kt` | Reassembles out-of-order radio fragments with TTL eviction | **Pure Domain** (`tactical/mesh/`) |
| `PeerPenaltyLedger.kt` | Blacklists spamming, jammed, or malformed peer radios | **Pure Domain** (`tactical/mesh/`) |
| `dtn/BundleStore.kt` | SQLite/In-memory store-and-forward bundle spooling | **Pure Domain** (`tactical/dtn/`) |
| `SovereignMeshTransport.kt` | Coroutine-driven Inbound/Outbound supervisor channel architecture | **Android Transport** (`android/mesh/`) |

---

## 3. Protocol & Packet Specifications

### 3.1 LoRa Radio MTU Adaptation
LoRa radio packets have a physical payload ceiling of 237 bytes in standard Meshtastic settings. A typical ATAK CoT marker is 400–1200 bytes of XML.

* **Fragmentation Algorithm:**
  1. Compresses CoT payload using GZIP or Protobuf wire format (reducing 800-byte XML to ~180 bytes).
  2. If payload exceeds 200 bytes, `FragmentAckCodec` splits the packet into $\le 200$-byte chunks.
  3. Prepend 8-byte `FragmentHeader`.
  4. Transmit fragments via Meshtastic `PortNum.ATAK_PLUGIN` or `PortNum.PRIVATE_APP`.
  5. Receiving nodes reassemble the subsegments and emit the complete `CotMarker` to `AtlasServices.markerStore`.

### 3.2 Hardware Driver Layer (`android/mesh/hardware/`)
* **USB-Serial Driver:**
  - Implements Android USB Host API (`UsbManager`, `UsbDeviceConnection`).
  - Supports CH340, CP2102, FTDI, and CDC-ACM (standard chips on ESP32/nRF52 boards).
  - Baud rate: 115200 8N1. Zero root required; prompts user for one-tap USB permission.
* **BLE Driver:**
  - Connects to Meshtastic BLE Service UUID: `6ba1b218-15a8-461f-9fa8-5dcae273eafd`.
  - Characteristics: `fromRadio` (Notify), `toRadio` (Write), `fromNum` (Read).

---

## 4. UI/UX Integration (Sovereign Cockpit)

1. **Hardware Link Pill in HUD:**
   - When a LoRa radio is connected, top-left telemetry renders:  
     `LORA: CONNECTED • 915 MHz • 18 dBm • 4 NODES`
   - Active radio icon pulses cyan on TX, amber on RX.
2. **Node Directory in Sovereign Command Center:**
   - Tap "LoRa Mesh" row in Command Center to open the **Mesh Node Roster**:
     - Lists discovered nodes, hop counts, battery percentage, SNR (Signal-to-Noise Ratio), and RSSI.
     - One-tap "Ping Node" and "Send Direct Message".
3. **Off-Grid Radio Silence (EMCON) Interlock:**
   - When Network Profile is set to **Radio Silence**, hardware TX is disabled at the driver level (hardware listen-only mode).

---

## 5. Implementation Roadmap
* **Step 1:** Port pure `FragmentAckCodec.kt`, `FragmentHeader.kt`, and `MeshSubsegmentReassembly.kt` into `:atlas-sdk:tactical.mesh` with unit tests.
* **Step 2:** Port `PeerPenaltyLedger.kt` to ensure zero trust against replay attacks.
* **Step 3:** Implement `AndroidUsbSerialTransport` and `AndroidBleMeshTransport` behind the pure `MeshTransport` interface.
* **Step 4:** Wire received packets into `AtlasServices.markerStore` and `pliStore`.
