# MK110 PLUS RGW 02 Android

Android configuration and management app for the MK110 PLUS RGW 02 remote gateway. Provision the gateway over BLE, then manage it remotely via MQTT. After the gateway is online, you can connect to scanned Beacons through the gateway and configure them downlink. This product additionally supports **power metering** (real-time power, energy, load-change events).

Cross-platform reference (same protocol): [MK110PLUS-02_Flutter](https://github.com/MKScannerPro/MK110PLUS-02_Flutter).


---

## 1. Overview

```
┌────────────────────┐  MQTT (APP)  ┌──────────────┐  MQTT (device) ┌─────────────────────┐
│ MK110 PLUS RGW 02  │◄────────────►│  MQTT Broker │◄──────────────►│ MK110 PLUS RGW 02   │
│       APP          │              └──────────────┘                └──────────┬──────────┘
└─────────┬──────────┘                                                        │ BLE scan
          │ BLE provisioning                                                  │
          └──────────────────────► MK110 PLUS RGW 02 ◄────────────────────────┘
                                                                              │
                                                                       Nearby Beacons
                                                                       (downlink config
                                                                        after connect)
```

Main flow:

1. **Connect APP MQTT** — Connect to the broker using locally saved MQTT settings.
2. **Scan and add a gateway** — Long-press the device button to enter config mode, then scan for Service Data `0xAA0E` (device type `0x10` / `0x11`).
3. **Provision over BLE** — Write MQTT, Wi‑Fi, NTP, metering params, and related settings; after leaving config mode the gateway joins the network.
4. **Subscribe to device topics** — Subscribe to the gateway Publish / LWT topics; inbound data means the device is online.
5. **Device detail (remote)** — Configure filters, upload options, power metering, output control, and system features (LED, time, OTA, device info, etc.).
6. **Beacon downlink** — From scanned Beacon data, instruct the gateway to BLE-connect and configure the peripheral; pages differ by product type and firmware (V1 / V2).

---

## 2. Project Structure

| Module | Description |
|--------|-------------|
| `app` | UI and business flow: provisioning, device list, detail, filter/upload, metering, Beacon config |
| `mokosupport` | SDK: BLE scan/command assembly, MQTT `msg_id` constants, entities and callbacks |

Package names: `com.moko.mkremotegw02` (app), `com.moko.support.remotegw02` (SDK).

Initialize in `RemoteMainWithMetering02Activity`:

```java
MokoSupport.getInstance().init(getApplicationContext());
MQTTSupport.getInstance().init(getApplicationContext());
```

Include the module:

```
include ':app', ':mokosupport'
```

```gradle
implementation project(path: ':mokosupport')
```

---

## 3. Business Flow

### 3.1 APP MQTT Settings

Entry: `SetAppMQTT02Activity`

Fields are similar to the gateway MQTT params: Host, Port, ClientId, username/password, QoS, Clean Session, Keepalive, subscribe/publish topics, LWT, and encryption mode (`0` none / `1`–`3` SSL certificate modes).

After a successful connection, the home screen (`RemoteMainWithMetering02Activity`) subscribes to:

- The APP subscribe topic (if configured)
- Each stored device’s **Publish topic** (device uplink)
- Each device’s **LWT topic** (last will / offline)

### 3.2 BLE Scan & Provisioning

Entry: `DeviceScanner02Activity` → `DeviceConfig02Activity`

Long-press the device button so the gateway enters config advertising. Identification:

- Service Data UUID: `0xAA0E` + device type (`0x10` = V1.X, `0x11` = V2.X)
- Manufacturer Specific Data company ID `0xAA0E` (or no MS data) → first-config flag
- Password: entered by user on connect (last value remembered locally)

Custom BLE service `0xAA00`:

| Characteristic | Purpose |
|----------------|---------|
| `0xAA00` | Password verification (required first) |
| `0xAA01` | Disconnect reason notify |
| `0xAA03` | Current device parameter R/W |

Key BLE capabilities on `0xAA03`:

- **System** — Reboot, device name, LED, NTP/timezone, version info, etc.
- **MQTT** — Host/Port/ClientId/credentials, QoS, topics, LWT, certificates (long fields use packetized `0xEE`)
- **Network** — Wi‑Fi security/SSID/password (EAP/certs supported); static IP / DHCP via network settings (`1023`)
- **Scan filter & upload** — RSSI / MAC / Adv Name / Raw type filters, upload interval, Beacon parse switches, PHY, scan mode, etc.
- **Power metering** — Enable, report intervals, load-change detection (provisioned via `MeteringSettings02Activity`)

After writing parameters, call exit-config-mode: BLE disconnects and the gateway connects to MQTT. The APP stores the device locally and subscribes to its topics.

Related screens: `MqttSettings02Activity`, `WifiSettings02Activity`, `NtpSettings02Activity`, `ScannerFilter02Activity`, `ScanAndUploadActivity`, `AdvSettingsActivity`, `NearbyWifiActivity`, `MeteringSettings02Activity`, etc.

### 3.3 Online Status

The gateway publishes JSON notifications (`msg_id` starting at 3000), including:

- **Network status** (e.g. `3004`) — mark the device online
- **Scan data** (`3070`) — nearby Beacon list
- **Power data** (`3082`) / **Energy data** (`3084`) / **Load change** (`3086`)
- **LWT / offline / button reset** — mark offline or remove

Default topic format:

| Direction | Default topic |
|-----------|---------------|
| Device subscribe (APP → device) | `/MK110Plus 02/{mac}/receive` |
| Device publish (device → APP) | `/MK110Plus 02/{mac}/send` |
| LWT | Usually same as Publish |

Example: `MK110Plus-1F65/testDeviceId/device_to_app`

The APP publishes config/read commands to the device subscribe topic, and listens on Publish/LWT for results and reports.

### 3.4 Device Detail & Remote Config

Entry: `DeviceDetail02Activity`

| Capability | Screen / notes |
|------------|----------------|
| Scan filter & upload | `ScannerUploadOption02Activity` → filter / upload |
| Gateway system settings | `DeviceSetting02Activity` |
| Power metering | `PowerMetering02Activity` → `MeteringSettings02Activity` |
| Output switch / control | `DeviceSetting02Activity` (`1015` / `1016`) |
| BLE manager (connected list) | `BleManager02Activity` (V1) / `BleManager02V2Activity` (V2) |
| Modify MQTT / Wi‑Fi | `modify/*` |

**Filters** (MQTT config `104x` / read `204x`): RSSI, MAC, Adv Name, iBeacon, Eddystone UID/URL/TLM, BXP-DeviceInfo/ACC/TH/Button/Tag, PIR, Other, TOF, NanoBeacon, PHY, duplicate filter, filter relationship, etc.

**Upload** (`1058` / `1059` / `1063` / `1065` / `1066`, etc.): report timeout, upload content switches, upload interval, parse switches, scan mode (active/passive).

**Power metering** (`1080`–`1087` / read `2080`–`2085`):

| Feature | Typical config `msg_id` |
|---------|-------------------------|
| Metering enable | `1080` |
| Power report interval | `1081` |
| Energy report interval | `1083` |
| Load-change enable | `1085` |
| Reset energy | `1087` |

**System settings:**

| Feature | Typical config `msg_id` |
|---------|-------------------------|
| LED indicator | `1011` |
| System time / NTP | `1009` / `1008` |
| Gateway OTA | `1006` |
| BLE module OTA | `1017` |
| Device info | Read `2002` |
| Network status report interval | `1003` |
| MQTT/Wi‑Fi reconnect timeout | `1005` |
| Communication timeout | `1010` |
| Output switch | `1015` |
| Output control by button | `1016` |
| Network settings (DHCP / static IP) | `1023` |
| Modify MQTT / Wi‑Fi | `1030` / `1020`, etc. |
| Reboot / factory reset | `1000` / `1013` |

### 3.5 Beacon Downlink from Scan Data

After the gateway reports scan results (`msg_id = 3070`), the detail screen can select a Beacon and send MQTT commands so the gateway **BLE-connects and configures** that peripheral.

Protocol rule: write commands return success when the JSON is valid; the actual BLE outcome is reported later via **notify** messages (3xxx).

Supported connection types (pages differ by product; V2 uses type picker):

| Type | Connect `msg_id` | Example screen |
|------|------------------|----------------|
| BXP-B-D | `1100` | `BXPBDActivity` (V2) / `BXPButtonInfoActivity` (V1) |
| BXP-B-CR | `1150` | `BXPBCRActivity` |
| BXP-C | `1350` | `BXPCActivity` |
| BXP-D | `1400` | `BXPDActivity` |
| BXP-T | `1450` | `BXPTActivity` |
| BXP-S | `1500` | `BXPSActivity` |
| MK PIR | `1550` | `MKPIRActivity` |
| MK TOF | `1600` | `MKTOFActivity` |
| Generic Other | `1300` | `BleOtherInfo02Activity` |

Common ops: disconnect `1200`, Beacon DFU `1202` / batch `1205`, connect timeout `1209`.

Per-type features vary, e.g. device info, realtime ACC/TH, history data, alarm/hall/motion events, remote reminder (LED/buzzer/vibration), advertising params, sensor params, power-off, battery mode, etc.

---

## 4. MQTT Protocol Summary

Common JSON envelope:

```json
{
  "msg_id": 1000,
  "device_info": { "mac": "xxxxxxxxxxxx" },
  "data": { }
}
```

| Category | `msg_id` range | Direction |
|----------|----------------|-----------|
| Config / control | `1000+` | APP → device |
| Read | `2000+` | APP → device; response includes `data` |
| Device notify / report | `3000+` | Device → APP |

Config responses typically include `result_code` (`0` = success) and `result_msg`.

Constants: `mokosupport/.../MQTTConstants.java`.

Scan report `type_code`:

| Code | Type |
|------|------|
| 0 | iBeacon |
| 1 | Eddystone-UID |
| 2 | Eddystone-URL |
| 3 | Eddystone-TLM |
| 4 | BXP-DeviceInfo |
| 5 | BXP-ACC |
| 6 | BXP-TH |
| 7 | BXP-Button |
| 8 | BXP-Tag/Sensor |
| 9 | PIR |
| 10 | Other |
| 11 | MK TOF |
| 12 | NanoBeacon Info |

Firmware capability: advertising device type `0x10` (V1.X) vs `0x11` (V2.X); some fields and screens are V2-only (e.g. Nano filter, data parsing/interval, scan mode, nearby Wi‑Fi, BLE connect timeout).

---

## 5. BLE Frame Format Summary

**Single packet** `HEAD=0xED`: `HEAD + FLAG + CMD + LEN + DATA`  
**Multi packet** `HEAD=0xEE`: adds `PACKET_NUM` / `PACKET_SEQ` (username, password, certificates, etc.)

| FLAG | Meaning |
|------|---------|
| `0x00` | Read |
| `0x01` | Write |
| `0x02` | Device notify |

Write reply last byte: `0x01` success, `0x00` failure. Command assembly: `OrderTaskAssembler` / `ParamsTask`.

---

## 6. SDK Quick Start

### 6.1 BLE Scan

```java
mokoBleScanner.startScanDevice(new MokoScanDeviceCallback() {
    @Override public void onStartScan() { }
    @Override public void onScanDevice(DeviceInfo device) { }
    @Override public void onStopScan() { }
});
```

### 6.2 BLE Connect & Orders

- Connection status via EventBus `ConnectStatusEvent` (discover success / disconnect)
- Send: `MokoSupport.getInstance().sendOrder(OrderTask...)`
- Response: `OrderTaskResponseEvent` (timeout / finish / result)

Typical provisioning order: verify password → write MQTT → write Wi‑Fi → (optional) write filter/upload/metering → `exitConfigMode()`.

### 6.3 MQTT

```java
MQTTSupport.getInstance().connectMqtt(mqttAppConfigJson);
MQTTSupport.getInstance().subscribe(topic, qos);
MQTTSupport.getInstance().publish(topic, message, msgId, qos);
MQTTSupport.getInstance().unSubscribe(topic);
MQTTSupport.getInstance().disconnectMqtt();
```

Events: `MQTTConnectionCompleteEvent` / `MQTTConnectionFailureEvent` / `MQTTConnectionLostEvent` / `MQTTMessageArrivedEvent`, plus Publish/Subscribe success and failure events.

SSL: `connectMode` `0` plain; `1` encrypt without server verify; `2` verify server CA; `3` mutual certificates.

### 6.4 Logging

Based on [xLog](https://github.com/elvishew/xLog). Folder and file names are configured in `BaseApplication` (e.g. `MKRemoteGW02` / `MKRemoteGW02.txt`). Keeps today and yesterday (`.bak`).

```java
LogModule.d("log info");
```

---

## 7. Main Screen Index

```
GuideActivity                     Splash & permissions
SetAppMQTT02Activity              APP MQTT settings
RemoteMainWithMetering02Activity  Device list / online status
add/
  DeviceScanner02Activity         BLE scan & add
  DeviceConfig02Activity          Provisioning flow hub
  MqttSettings02Activity          Device MQTT
  WifiSettings02Activity          Wi‑Fi
  NearbyWifiActivity              Nearby Wi‑Fi (V2)
  ScannerFilter02Activity         Filters during provisioning
  ScanAndUploadActivity           Upload during provisioning
  MeteringSettings02Activity      Metering during provisioning
DeviceDetail02Activity            Detail + scan list
ScannerUploadOption02Activity     Remote filter & upload
PowerMetering02Activity           Remote power metering
DeviceSetting02Activity           Remote system settings
settings/                         LED, time, OTA, device info…
filter/  upload/                  Filter & upload subpages
beacon/                           Beacon downlink pages
modify/                           Remote MQTT / Wi‑Fi modify
```

---

## 8. References

- `msg_id` constants: `mokosupport/src/main/java/com/moko/support/remotegw02/MQTTConstants.java`
- Order assembly: `mokosupport/src/main/java/com/moko/support/remotegw02/OrderTaskAssembler.java`
