# REA Analysis Report — DM20 Mixing Console Remote (`com.esunny.sound`)

**Analyzed artifact:** `DM20_1.0.3_APKPure.apk` (proprietary; not distributed with this repository)
**Artifact SHA-256:** `f7baf233fe271faee3c7ba6d9897a148b3b8c0a0662ddf0b34b5b11f7f34e440`
**Size:** 5,498,039 bytes (5.24 MiB)
**Format:** Android APK
**Tooling:** REA 6.4.0 + `jadx-headless-mcp` 0.7.1 (JADX engine) on Temurin JDK 21
**Evidence IDs:** `ev_0a3f0c78f01d7b1e9156a98249c536c91a63a862448974879f7351e2f7890373` (package), `ev_548dd3f1246dfff701996430af03aaea0edb1521f10118231f7c5ee426f0d788` (method)
**Authority:** shipped-artifact (static). The APK was never executed.

> This report replaces the earlier one, which described a different (incorrect) APK.

---

## 1. Executive summary

This is the **Android remote-control app for the DM20 digital audio mixing console** (a live-sound / studio mixer). It is a small, pure-Java app (~2,185 classes, no native libraries) whose only real job is to talk to the console over the local network.

The app uses a **custom binary protocol on top of TCP** (Netty), framed with a `0x5A` magic header and carrying **Protocol Buffers** messages. It discovers the console by **UDP broadcast**, then opens **three TCP connections** to the console:

| Channel | Port | Purpose |
|---|---|---|
| Control | **6400** | commands + initial state |
| Meters | **6401** | real-time level meters |
| Music | **6402** | music list / recorder |

Default console address: **`192.168.43.1`**, default control port **`6400`**, discovery broadcast port **`6399`**.

The app declares **only the `INTERNET` permission** and contains **no second-party/analytics endpoints, no bundled secrets, and no native code**. Its principal security caveats are typical of LAN control apps: the control protocol is **unauthenticated and cleartext**, and it exposes an **exported, permission-less `Service`**.

---

## 2. Identity & packaging

| Property | Value |
|---|---|
| Package | `com.esunny.sound` |
| Version | `1.0.3` (versionCode `4`) |
| App label | `@string/app_name_dm20` → **"DM20"** |
| Icon | `@drawable/ic_launcher_dm20` |
| `minSdkVersion` | 16 (Android 4.1) |
| `targetSdkVersion` | 26 (Android 8.0) |
| Build platform | API 28 (Android 9) — `platformBuildVersionName="9"` |
| Classes | 2,185 |
| Resources | 760 |
| Code | `classes.dex` (7.37 MB) + `classes2.dex` (0.84 MB) |
| Native libs | **none** |
| Vendor namespace | `com.amo.skdmc` (mixer-control SDK) |

The APK is a single-ABI-independent, Java/Kotlin-free, fully reviewable DEX package.

---

## 3. Signing

REA does **not** verify signatures; the following was read directly from the `META-INF` PKCS#7 block and the APK Signing Block.

- **Schemes present:** v1 (JAR signature) **and** APK Signature Scheme **v2** (`0x7109871a`) **and v3** (`0xf05368c0`). No v2.1/source-stamp (`0x1b93ad61`).
- **v1 alias:** `GOOGPLAY` (`META-INF/GOOGPLAY.SF`, `META-INF/GOOGPLAY.RSA`, `META-INF/MANIFEST.MF`).
- **Signer certificate:** self-signed, **RSA-4096 / SHA-256**.

| Field | Value |
|---|---|
| Subject | `CN=Android, OU=Android, O=Google Inc., L=Mountain View, S=California, C=US` |
| Issuer | same as subject (self-signed) |
| Serial | `00DC8353B38AE210086AD73A455BD690E12A98A1A7` |
| Validity | 2018-09-05 → 2048-09-05 |
| Signature alg | `sha256RSA` |
| Key | RSA 4096 |
| Certificate SHA-1 | `913E8ED560066B137978437A7F51FBD7C60C71F3` |
| Certificate SHA-256 | `2AEE00E0A528BD7D14953F3371DD102D8E0E2FF2831466F773CFC47C273A65D9` |

The generic "Google Inc." subject on a self-signed key is a common placeholder for OEM build tooling / repackaging — it does not indicate Google authorship. (This copy came from APKPure and is signed with a self-signed key, as expected for a vendor utility.)

---

## 4. Manifest, components & permissions

**Permissions:** `android.permission.INTERNET` **only**.

**Application:** `com.esunny.sound.BaseApplication`; `allowBackup="true"` (no `fullBackupContent`); support library meta-data `android.support.VERSION=25.3.1`; `com.android.vending.derived.apk.id=1`.

**Components:**

| Type | Name | Exported | Notes |
|---|---|---|---|
| Activity | `com.esunny.sound.ui.MainActivity` | (implicit launcher) | `MAIN` + `LAUNCHER`, landscape |
| Activity | `com.esunny.sound.ui.ConnectActivity` | — | also declares an `android.intent.action.MAIN` filter; landscape |
| Service | `com.esunny.sound.service.ConnectService` | **`exported="true"`, no permission** | IPC surface (see §8) |

No providers, receivers, or widgets. No network-security-config; because `targetSdk = 26`, cleartext traffic is permitted by default.

---

## 5. Third-party / technology stack

| Component | Evidence | Role |
|---|---|---|
| **Netty 4** | `io.netty.handler/channel/util/buffer` (~730 classes), `META-INF/io.netty.versions.properties` | TCP + UDP networking |
| **RxJava 1** | `rx.*` (319 classes), `META-INF/rxjava.properties` | async/event composition |
| **RxPermissions** | `com.tbruyelle.rxpermissions` (5) | runtime permission requests |
| **Protocol Buffers** | `com.google.protobuf` (140), `google/protobuf/*.proto` | wire message serialization |
| **EventBus** | `org.greenrobot.eventbus` (28) | in-process events |
| **Android Support 25.3.1** | `android.support.*` (708) | UI/lifecycle |
| **AMO mixer SDK** | `com.amo.skdmc` (132) | console model & controls |

No Kotlin, no NDK, no analytics SDK, no ad SDK.

---

## 5.1 First-party structure (`com.esunny.sound`, 119 classes)

| Area | Classes |
|---|---|
| Networking | `netty.NettyConnect`, `netty.NettyCommandManager`, `netty.proto.CmdHandler`, `netty.data.{SendCommandManager,DataCommand,DataCommandModel,ServerModel}`, `netty.model.{DataManager,AllCommonData}`, `netty.utils.{CommandUtils,DataUtil}` |
| Service/IPC | `service.ConnectService`, `service.ICommandManager` |
| UI | `ui.MainActivity`, `ui.ConnectActivity`, `ui.persenter.{MainPersenter,IN1Persenter}`, adapters (`DeviceListAdapter`, `ComAdapter`, `FxGeqFaderListAdapter`, `RecoderAdapter`, …) |
| Helpers | `utils.{ConfigUtils,SpUtils,LogUtils,DataTemUtils,DisplayUtil,TimeUtil,ToastUtil,UltimateBar,ShowModelUtils}` |

The `com.amo.skdmc` SDK provides `model` (45), `data` (45), `controls` (28), `hwinterface` (5), `base` (2), `util` (3), `event` (2) — i.e. channel/bus/EQ/FX/scene models plus custom UI controls (faders, EQ graphs, meters).

---

## 6. Discovery & connection flow (reconstructed)

### 6.1 UDP discovery
`ConnectActivity` "Scan" posts a `Runnable` that:

1. Creates a Netty `NioEventLoopGroup` and a `Bootstrap` with `NioDatagramChannel` and `SO_BROADCAST=true`.
2. Sends the 4-byte payload `{0x5A, 0x5A, 0x5A, 0x5A}` to **`255.255.255.255:6399`**.
3. Any console that answers with a datagram whose first two bytes are **`"OK"`** is added to the device list as `"Console"` (keyed by source host name). A `5000 ms` timeout yields a Chinese "query timed out" log and hides the progress dialog.
4. Every device is stored in `BaseApplication.mDeviceList`.

```
UDP -> 255.255.255.255:6399   payload 5A 5A 5A 5A
UDP <- console               payload "OK..."
```

### 6.2 TCP connection
On "Connect", `NettyConnect.connect()` starts **three** Netty `NioSocketChannel` clients:

| Thread | Target | Pipeline |
|---|---|---|
| 1 | `HOST:6400` | `channelInboundHandler1` + `LengthFieldBasedFrameDecoder(MAX, 4, 4, -8, 0)` |
| 2 | `HOST:6401` | `meterHandlerAdapter` |
| 3 | `HOST:6402` | `musicHandlerAdapter` |

Only the control pipeline installs a decoder, and it is inert: `channelInboundHandler1` (the custom
`0x5A` scanner) never forwards the buffer down the pipeline. The meter and music pipelines have no
decoder at all — each handler does its own framing.

`NettyConnect.HOST` is set from the IP field on successful discovery (`connectSuccess()`), and defaults to **`192.168.43.1`**; `PORT` defaults to **`6400`**.

### 6.3 Default constants (from `NettyConnect.<clinit>`)
Disassembled straight from the DEX static initializer:

```
const-string v0, "192.168.43.1"   ; HOST
sput-object v0, NettyConnect.HOST
const/16    v0, #6400             ; 0x1900
sput        v0, NettyConnect.PORT
```

`ConnectActivity.port = NettyConnect.PORT - 1` → the **discovery** port (`6399`).

---

## 7. Wire protocol

### 7.1 Frame format
Commands are sent as:

```
+--------+--------+------------------+------------------+-------------------+
| 5A x6  | int32  | int32            | protobuf payload |
| header | protoId| length (bytes)   | (writeDelimitedTo)|
+--------+--------+------------------+------------------+
```

- `CmdHandler.sendCommand()` and `CmdHandler.requestInitData()` write **six** `0x5A` bytes.
- `NettyConnect.send110Data()` writes **four** `0x5A` bytes (variant).
- Inbound parsing (`NettyConnect.analyzeReadData`) scans for **six consecutive `0x5A`** (`CommandUtils.readToFour0x5aHeaderByte`, despite its name), then reads `protoId` and `length`.
- Payloads are standard protobuf "delimited" (varint-length-prefixed) messages.

### 7.2 protoId semantics
| protoId | Meaning |
|---|---|
| `1` | full state snapshot (`SKDMCData`); sent by the client (empty) on connect and streamed back by the console |
| `102`, `104`, … `128`, `192` (even) | client → console per-model commands (see §7.3) |
| `101`, `103`, … `233` (odd) | console → client change events / command results → `CmdHandler.processCommand(protoId, stream, ctx)` |
| `193` | music list (`MusicList`) |

The complete protoId→message tables (outbound commands and all 45 inbound dispatch cases) are in
[`protocol-spec.md`](./protocol-spec.md) §5.

### 7.3 Command IDs (from `SendCommandManager`)
`102` input stage · `104` PEQ · `106` dynamics · `108` mic bus send · `110` channel · `112` output input ·
`114` output · `116`/`118` main setup · `120`–`126` FX (module/delay/reverb/GEQ) · `128` monitor ·
`192` recorder, plus scene/name/library helper commands in the same range.

### 7.4 Message layer & recovered schema
Protobuf message types live under `com.amo.skdmc.data.*` (Google Protobuf generated outer classes such as
`DataInOutput.MicInOutputPartModel`), keyed by the AMO mixer SDK. The whole snapshot is aggregated as
`SKDMCData` and delivered to the UI through `DataManager` / `AllCommonData` / `IDataChange`.

The **entire wire schema was recovered** and is written to [`dm20-recovered-schema.proto`](./dm20-recovered-schema.proto):
**45 application `.proto` files, 107 messages, 14 enums, 704 fields** (plus the standard `google/protobuf/*`
definitions). It was reconstructed by disassembling each generated `<clinit>` and reassembling the embedded
`descriptorData` chunks — see [`protocol-spec.md`](./protocol-spec.md) §8.

### 7.5 Meter channel framing
`AnonymousClass5.channelRead` reads `[5A 5A]`, an `int32` that must be `0`, an `int32` length, then
`MeterLevelData.parseDelimitedFrom(stream)`. `MeterLevelData { bytes gainValue = 1; }`.

`gainValue` is **80 signed 8-bit dBFS values** (one per meter), indexed by the static
`MeterListener.channelMeterMapper` — channels 0–11 (NG/COMP/OUT), ch12/13, then all pre-fader inputs, then
monitor (`MonitorParam=273`, 65–66), channel 255 (67–68) and bus modules **128…135** (69…80). The UI
(`MeterAdapter.getMeterValue` → `MeterView`) averages stereo pairs (`(L+R)/2`) and adds the channel fader
dB for post-fader metering, then linearly maps the byte onto the `MeterBmpDispControl` range from XML
(`view_meter_show`: **−68…−2 dB**; bus/dynamics −80…0; monitor/recorder −60…0). `0x80` (−128) is the
silent floor; `0x00` is 0 dBFS. Full slot table in [`protocol-spec.md`](./protocol-spec.md) §3.3.

The music channel (`6402`) performs **no** header scan: `NettyConnect.AnonymousClass4.channelRead` wraps
the raw buffer in a `ByteBufInputStream` and hands it straight to `CmdHandler.proccessMediaMusicTimeEvent`,
whose first statement is `PlayCommand.parseDelimitedFrom(stream)`. So the wire layout is a bare
`[varint length][PlayCommand]` — no `0x5A` magic, no protoId (contrast the meter handler
`AnonymousClass5`, which validates `[5A 5A][int32 0]`). `proccessMediaMusicTimeEvent` then discards the
parsed value (calling `onMusciTime()`), so the payload is only *used* by the replacement client; anything
about the real console's payload type still wants hardware confirmation. See `protocol-spec.md` §3.4.

### 7.6 Units & scaling (recovered)
The wire carries **display units**; `com.amo.skdmc.util.HardwareUtil` converts to engineering units:

- **Fader/gain `GainValue`** = a `0…1000` slider **position**, mapped to dB by the polynomial table
  `fConvTab` (`InitConvTab`): ≈ **−80.5 dB (pos 0) … +10.1 dB (pos 1000)**, 0 dB at pos ≈ **755**.
- **PEQ freq** = **Hz** on the wire; the encoder spans `0…1000` ↔ **20 Hz…20 kHz** logarithmically
  (`ConvertFrequencyValue` / `ConvertEnableValue`).
- **PEQ gain** = **dB** (`−18…+18`, 0.1 steps); **PEQ Q** = **Q** (`0.5…10`, 0.05 steps) — sent raw.
- **GEQ** = **−18…+18 dB** ↔ DSP `0…1000` (`ConvertGeqDspValue` / `ConvertGeqFaderValue`).
- **Delay** = **ms** (`ConvertMValue` → metres, `ConvertFtValue` → feet).
- Display unit codes (XML `edc_unit`): `1 ms · 2 Hz · 3 dB · 4 dBu · 5 % · 6 BPM · 7 ratio · 8 unitless`.
- Bus module IDs are `128…135`; `ModuleConst.MonitorParam = 273`.

Details and worked formulas in [`protocol-spec.md`](./protocol-spec.md) §6.1.

### 7.7 Transport / recorder control (protoId 192)
The recorder tab does not use the `Command`/`EnmCommand` messages. Its UI (`RecorderView`) mutates one
`instruction` field on a `ui.model.MainRecorderItemModel` and posts the whole model on the event bus;
`MainActivity.sendCommand(Object)` → `SendCommandManager.send192Command(model)` sends
`model.getProtoModel()`, which is a **`PlayCommand`** — the same message the console echoes on `191`/`195`.
So protoId `192` on the wire is a `PlayCommand`, not the `MainRecorderItemModel` the method signature
suggests (`BusParam4 = 192`; the `ModuleConst` field *values* are not exposed by the **Java** DEX API, but
they are readable from the encoded static-field arrays — see `protocol-spec.md` §5.1.1).

The ad-hoc `instruction` values (from `RecorderView.onClick` / `startAndPause` / `selectPlay`) are
`1` = select a file by `songPath` (also used for previous/next, picked client-side from the last `193`
`MusicList`), `3` = stop/restart, `4` = play/pause, `5` = change play mode (`EnmPlayMode`
`Loop=0 … Once=4`), `6` = toggle record. `proccessMediaMusicListEvent` (protoId `193`) parses a
`MusicList { repeated MusicItem { fileName, filePathName } }`; `proccessMediaMusicStateEvent` (protoId
`195`) logs and forwards a `PlayCommand`. Full tables in [`protocol-spec.md`](./protocol-spec.md) §5.1–5.3.

---

## 8. Service / IPC surface

`ConnectService` is **exported and unprotected**:

```java
@Override public IBinder onBind(Intent intent) { return this.binder; }
@Override public void onCreate() { super.onCreate(); this.mCommandManager = new NettyCommandManager(); }
```

Its binder exposes `ICommandManager`:

```
connect() / disconnect() / sendCommand(...) / setOnViewDataChangeListener(...)
```

**Implication:** any app installed on the same device (no permission required) can bind to `com.esunny.sound.service.ConnectService` and drive the mixer / subscribe to its state. This is a local-IPC exposure, not a remote one, but it is real and trivially reachable.

---

## 9. Security assessment

| Finding | Severity | Notes |
|---|---|---|
| Unauthenticated, cleartext control protocol | Moderate (LAN) | No TLS, no credentials/nonce in the protocol; anyone able to reach the console's control ports (e.g. on the console's hotspot `192.168.43.0/24`) can issue commands or spoof responses. |
| Exported, permission-less `ConnectService` | Low–Moderate (local) | Any on-device app can bind and control/observe the mixer. |
| `android:allowBackup="true"`, no `fullBackupContent` | Low | Device list / prefs could be extracted via `adb backup` on legacy devices. |
| `targetSdkVersion=26` (old) | Low | Cleartext permitted; no modern runtime protections for the sockets. |
| v1 JAR signature included alongside v2/v3 | Informational | v1 is weaker, but v2/v3 are present. |
| Self-signed signing key | Informational | Expected for a vendor utility; not tied to a trusted CA. |
| No remote endpoints / analytics / secrets | Positive | `INTERNET` is used solely for LAN sockets; no exfiltration surface found. |
| No native code | Positive | Entire logic is in DEX and statically reviewable. |

No hardcoded API keys, tokens, or passwords were found. URL hits in the string pool are library documentation links only (`netty.io`, `eclipse.org/jetty`, `github.com/golang`, `example.com`, XML schemas).

---

## 10. Notable implementation details

- `CommandUtils.readToFour0x5aHeaderByte` actually requires **six** consecutive `0x5A` header bytes (unrolled check of six `isStartHeaderByte`). The name is misleading.
- `NettyConnect.send110Data` emits the deprecated 4-byte header form and a hard-coded `MicInOutputPartModel` (`setOutFaderValue(233.45f)`). It has **zero callers** — a legacy/test path; the app uses `SendCommandManager.send110Command` instead, and `233.45f` is an ordinary `0…1000` output-fader position (see `protocol-spec.md` §6.1).
- The UI is a single large `MainActivity` (≥35 anonymous inner classes) with a presenter (`MainPersenter`) and a per-channel view (`IN1View`), reflecting a dense mixer surface (channels, buses, EQ/dynamics/FX, scenes, meters, recorder).
- Three separate TCP channels are used rather than multiplexing: control, meter, and music — a simple way to isolate real-time meter traffic from command traffic.
- `com.amo.skdmc.hwinterface.ModuleConst` encodes hardware parameter groups (`EfxParam1`/`EfxParam2`,
  `MonitorParam` = 273, `PatchParam`, `SystemParam`) and a nested `Bus` holder whose `Bus1…Bus8` are
  module IDs **128…135**, used both by the console model and the 80-slot meter map.

---

## 11. Reproducing / extending this analysis

Environment (user-local, no admin): Temurin JDK 21 + Node 24 + REA 6.4.0 + `jadx-headless-mcp-0.7.1-all.jar` (`REA_JADX_MCP_JAR`). REA is registered as an OpenCode MCP server.

The commands below assume `$APK` points at a copy of the original `DM20_1.0.3_APKPure.apk` (SHA-256 above).
That artifact is proprietary and is **not** distributed with this repository.

```powershell
$APK = 'path\to\DM20_1.0.3_APKPure.apk'
rea inspect-android-package $APK --json
rea search-android-classes $APK com.esunny.sound
rea inspect-android-method  $APK com.esunny.sound.netty.NettyConnect connect
```

**Deliverables**
- `docs/rea-report.md` — this report (static analysis of the original app).
- `docs/protocol-spec.md` — reconstructed wire-protocol specification (framing, discovery, protoId tables),
  including the end-to-end verification results in its §10.
- `docs/dm20-recovered-schema.proto` — complete reconstructed protobuf schema (45 app files / 107 messages / 704 fields).
- `app/` — scratch-built replacement app (`com.dm20remote`, `targetSdk 36`); see the top-level `README.md`.
  No original resources or code are reused.

**Remaining dives (see `protocol-spec.md` §8)**
- Confirm the scene import/export file format.
- ~~Confirm the music-channel (6402) framing.~~ **Done** — a bare delimited `PlayCommand` with no header;
  see `protocol-spec.md` §3.4. Only the real-console payload type remains for hardware (issue #5).
- ~~Trace the non-standard `SelectedFader` ranges used by dynamics/monitor.~~ **Done** — the ranges are the
  wire values themselves; see `protocol-spec.md` §6.1 and `Units.DynamicsRanges` / `Units.MonitorRanges`.

*Resolved by the replacement implementation:* the fader/pan/EQ units and the meter buffer encoding were
validated by feeding a mock console's frames into the new app (see `protocol-spec.md` §10), so they
are no longer open questions.

---

## 12. Limitations

- **Static only.** The APK was never installed or executed; runtime behavior, actual console interaction, and gating are inferred from code.
- **Signatures not verified** (`signature_verification = not_performed`); the certificate details above are read from the padded PKCS#7 block and the APK Signing Block, not validated against a trust root.
- JADX decompilation is best-effort; a few synthetic/anonymous members and a static initializer lacked decompiled bodies (the `HOST`/`PORT` values were recovered by disassembling the DEX `<clinit>` directly).
- No device or console was available to capture live traffic, so the protocol description is derived from the client implementation (send/parse code) rather than a packet capture. The recovered protocol was subsequently validated against a mock console implementing it — see `DM20-protocol-spec.md` §10 — but a capture against real DM20 hardware is still outstanding.
