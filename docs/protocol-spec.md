# DM20 Console Protocol — Recovered Specification

**Target:** `com.esunny.sound` 1.0.3 (`DM20_1.0.3_APKPure.apk`)
**Artifact SHA-256:** `f7baf233fe271faee3c7ba6d9897a148b3b8c0a0662ddf0b34b5b11f7f34e440`
**Method:** static analysis only (REA / JADX). The app and console were never executed.
**Companion file:** [`dm20-recovered-schema.proto`](./dm20-recovered-schema.proto) — the complete reconstructed protobuf schema.

> Everything below is derived from the client implementation (`sendCommand` / `analyzeReadData` / `processCommand`)
> and from the protobuf `descriptorData` blobs embedded in the DEX. It has been validated against a mock
> console that implements this specification (§10), but not against physical hardware.

---

## 1. Discovery

```
UDP  ->  255.255.255.255:6399   payload: 5A 5A 5A 5A
UDP  <-  console (source IP)    payload: "OK" ...
```

- Sent by `ConnectActivity` on "Search" (`DatagramSocket`, broadcast, `soTimeout = 5000 ms`).
- Any reply whose first two bytes are `"OK"` (`0x4F 0x4B`) marks the sender as a console.
- Default static address `192.168.43.1` (the console's own Wi-Fi AP), control port `6400`.

## 2. Connections

`NettyConnect.connect()` opens **three** independent TCP connections:

| Channel | Port | Client handler | Purpose |
|---|---|---|---|
| Control | `HOST:6400` (`PORT`) | `channelInboundHandler1` (+ `LengthFieldBasedFrameDecoder`) | commands + full state |
| Meter | `HOST:6401` (`PORT+1`) | `meterHandlerAdapter` | real-time level meters |
| Music | `HOST:6402` (`PORT+2`) | `musicHandlerAdapter` | music/recorder time events |

## 3. Framing

### 3.1 Control channel (`6400`) — client → console

```
+--------+--------+--------+------------------+
| 5A x6  | int32  | int32  | protobuf payload |
| magic  | protoId| length | (delimited)      |
+--------+--------+--------+------------------+
```

- `CmdHandler.sendCommand(protoId, msg)` writes **six** `0x5A` bytes, then a big-endian `int32 protoId`, a
  big-endian `int32` payload length, then `msg.writeDelimitedTo(...)` (varint length + message bytes). The
  declared length therefore covers the varint prefix **and** the payload.
- **Init request is the one exception.** `CmdHandler.requestInitData` writes
  `[5A x6][int32 1][int32 0]` — a bare **14-byte** frame with *no* varint and *no* payload. A reader that
  always expects the delimited varint will block forever on it.
- Legacy/debug variant: `CmdHandler.send110Data(ctx)` writes only **four** `0x5A` bytes and hard-codes
  `MicInOutputPartModel(moduleId=0, outFaderValue=233.45f)`. Treat as a test harness, not part of normal flow.

### 3.2 Control channel (`6400`) — console → client

- `channelInboundHandler1` scans for **six consecutive `0x5A`** bytes
  (`CommandUtils.readToFour0x5aHeaderByte` — the name is misleading), then reads `int32 protoId`,
  `int32 length`, and dispatches the payload to `CmdHandler.processCommand(protoId, stream, ctx)`.
- The pipeline also installs `LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 4, 4, -8, 0)` after the
  custom handler.

### 3.3 Meter channel (`6401`) — console → client

From `NettyConnect.AnonymousClass5.channelRead`:

```
read byte  -> must be a start-header byte (0x5A)
read byte  -> must be a start-header byte (0x5A)
read int32 -> must be 0
read int32 -> length
then       -> MeterLevelData.parseDelimitedFrom(stream)
```

So the meter frame is `[5A 5A] [int32 0] [int32 len] [MeterLevelData …]`. `MeterLevelData` has a single
field `bytes gainValue = 1`. Note the *two* length encodings: the big-endian `int32 len` (which covers the
varint prefix plus the payload) **and** the varint prefix that `parseDelimitedFrom` expects. This
double-length layout was confirmed end-to-end by feeding a mock console's frames into the replacement app
(see §10).

**Meter payload encoding (resolved — static).** `gainValue` is **one signed 8-bit dBFS value per meter**,
in the fixed slot order below. `MeterAdapter.getMeterValue` returns `bMeter[id-1]` (or, for a stereo pair,
`(bMeter[left] + bMeter[right]) / 2`); `MeterView.refreshMeter` then calls
`MeterBmpDispControl.setValue(byte + faderDB)`, where `faderDB` is the channel's converted fader dB
(see §6.1) and the control linearly maps `[minValue, maxValue]` (XML `app:mbdc_min_value` /
`mbdc_max_value`, default `0…100`) to its pixel extent (`bor = (value − minValue) · sfact`).

Because the byte is **sign-extended** to `float` while the display minimum is strongly negative
(`view_meter_show.xml` uses **−68 … −2 dB**; bus/dynamics use −80…0; monitor/recorder use −60…0), only a
signed-dB interpretation is self-consistent: `0x80` (−128) is the silent/floor value and `0x00`
(0 dBFS) clamps to the display maximum. The frame always carries **80 values**:

| Slot | Meter |
|---|---|
| 1–36 | channels 0–11: `NG_OUT`, `COMP_OUT`, `OUT` |
| 37–40 | channel 12: `NG_OUT`, `COMP_OUT`, `L_OUT`, `R_OUT` |
| 41–44 | channel 13: `NG_OUT`, `COMP_OUT`, `L_OUT`, `R_OUT` |
| 45–56 | channels 0–11: `IN` (pre-fader) |
| 57–58 | channel 12: `L_IN`, `R_IN` |
| 59–60 | channel 14: `L_IN`/`R_IN`, **shared** with ch14 `L_OUT`/`R_OUT` |
| 61–62 | channel 13 `L_IN`/`R_IN` |
| 63–64 | channel 15 `L_IN`/`R_IN`, **shared** with ch15 `L_OUT`/`R_OUT` |
| 65–66 | `ModuleConst.MonitorParam` (=**273**) `L_OUT`/`R_OUT` |
| 67–68 | channel id **255** `L_OUT`/`R_OUT` |
| 69 | bus **128** (Bus1) `OUT` |
| 70 | bus **129** (Bus2) `OUT` |
| 71 | bus **130** (Bus3) `OUT` |
| 72 | bus **131** (Bus4) `OUT` |
| 73–74 | bus **132** (Bus5) `L_OUT`/`R_OUT` |
| 75–76 | bus **133** (Bus6) `L_OUT`/`R_OUT` |
| 77–78 | bus **134** (Bus7) `L_OUT`/`R_OUT` |
| 79–80 | bus **135** (Bus8) `L_OUT`/`R_OUT` |

The table is the static `MeterListener.channelMeterMapper` (`Map<ChannelMeterItem,Integer>`) — so the frame
is always **80 bytes** even when fewer meters are displayed.

### 3.4 Music channel (`6402`) — console → client

```
+-------------+--------------------+
| varint size | PlayCommand bytes  |
+-------------+--------------------+
```

There is **no** magic prefix and **no** protoId: the handler pipes the whole inbound buffer into
`PlayCommand.parseDelimitedFrom`, which reads the varint length at the current position. The music
channel is therefore a bare length-delimited message — a third framing, unlike both the control and
meter channels.

**Evidence (static, 1.0.3):**

- `NettyConnect.connect` starts three threads; thread 3 connects `HOST:PORT+2` (= `6402`) with the
  **`musicHandlerAdapter`** pipeline alone. It is the only channel with no framing helper, and the only
  one whose handler does not scan a header.
- `NettyConnect.AnonymousClass4.channelRead` wraps the whole inbound `ByteBuf` in a
  `ByteBufInputStream` and calls `CmdHandler.proccessMediaMusicTimeEvent(ctx, stream)`. Contrast
  `AnonymousClass5` (the meter reader), which validates `[5A 5A][int32 0]` before dispatching.
- `CmdHandler.proccessMediaMusicTimeEvent` is referenced from `AnonymousClass4.channelRead` **only**
  and begins with `DataCommonPlaycommand.PlayCommand.parseDelimitedFrom(stream)`, then discards the
  result and calls `onMusciTime()`.

`parseDelimitedFrom` consumes a varint length at the current stream position, so a header (magic or
protoId) would desynchronise it. The bare delimited layout above is the only shape the code can read.

**Caveat.** `proccessMediaMusicTimeEvent` throws the parsed value away and swallows parse errors, so
static analysis fixes the *shape* (`delimited`, no header) while the message *type* is inferred from the
parse call. Confirming what a real DM20 actually sends is tracked by issue `009` (hardware needed).

`PlayCommand` carries `playMode`, `songPath`, `songDuration`, `currentPosition`, `instruction`,
`isRecord`, `isPlay`, `recordTimeMs` (§6). The same message is used for the control-channel media events
(`191`, `195`), where it is *not* discarded.

**Improvement over the original.** The original throws the music-channel value away and only uses the frame
as a tick (`onMusciTime()`), interpolating the position locally. The replacement app instead decodes the
frame into `TransportState` so the position advances from the console's own value; because a bare tick may
omit the path, a frame that carries no `songPath` (or no `songDuration`) does not blank the title the
control channel reported.

## 4. Handshake

- On `channelActive` of the control channel the client immediately sends **protoId 1 with an empty payload**
  (`CmdHandler.requestInitData`).
- The console answers by streaming the full snapshot (`SKDMCData`, protoId 1) and afterwards pushes
  per-model change events (odd protoIds). No authentication or session token is exchanged.
- **Change events mirror commands.** An accepted client command is confirmed by the console re-sending the
  resulting model under the *preceding odd* id — e.g. `110` (`MicInOutputPartModel`) is acknowledged with
  `109`, and `114` (`BusOutputModel`) with `113`. The client does not get a distinct success/failure code, so
  UI state should be treated as confirmed only once the change event comes back.

## 5. protoId map

### 5.1 Client → console (from `SendCommandManager`)

| protoId | Method | Payload message |
|---|---|---|
| `1` | `requestInitData` | *(empty)* — request full state |
| `102` | `send102Command` | `ui.model.InputStageModel` |
| `104` | `send104Command` | `model.PEQPartModel` |
| `106` | `send106Command` | `model.MicInDynamicsPartModel` |
| `108` | `send108Command` | `model.MicInBusModel` |
| `110` | `send110Command` | `ui.model.CHModel` |
| `112` | `send112Command` | `model.OutInputPartModel` |
| `114` | `send114Command` | `model.OutOutputPartModel` / `ui.model.CHModel` |
| `116` | `send116Command` | `ui.model.MainSetupModel` |
| `118` | `send118Command` | `ui.model.MainSetupPathModel` |
| `120` | `send120Command` | `model.SetupFxModulModel` |
| `122` | `send122Command` | `model.SetupFxDelayModel` |
| `124` | `send124Command` | `model.SetupFxReverbModel` |
| `126` | `send126Command` | `model.SetupFxGeqModel` |
| `128` | `send128Command` | `model.SetupMonitorPartModel` |
| `192` | `send192Command` | `PlayCommand` (from `ui.model.MainRecorderItemModel.getProtoModel()`) — transport / recorder, see §5.3 |

The `102–128` even range is also used for scene/name/library commands via
`SendCommandManager.sendChangeNameCommand`, `sendDataCommonCommand`, `sendFxLibCommand`, `sendScensCommand`.

### 5.3 Transport / recorder commands (protoId `192`)

`SendCommandManager.send192Command(MainRecorderItemModel)` calls
`sendCommand(ModuleConst.BusCommandId.BusParam4, model.getProtoModel())`, and `getProtoModel()` returns a
**`PlayCommand`** — the *same* message the console echoes on `191`/`195`. The wire payload is therefore the
`PlayCommand`, **not** the UI-level `MainRecorderItemModel`. (`BusParam4` is presumed `192` from the
`send192Command` name; the `ModuleConst` constants are not exposed as field values in the DEX.)

The original `RecorderView` edits a single `instruction` field on the last `PlayCommand` the console
reported and re-sends the **whole** message, exactly like the channel edits in §4. The recovered values
(recovered from `RecorderView.onClick` / `startAndPause` / `selectPlay`) are:

| `instruction` | UI control | Other fields set | Effect |
|---|---|---|---|
| `1` | file list / previous / next | `songPath`, `currentPosition = 0`, `isPlay=true` | select and start a file |
| `3` | restart | — | stop / rewind to 0 |
| `4` | play / pause | — | toggle playback |
| `5` | play-mode button | `playMode` | change `EnmPlayMode` |
| `6` | record toggle | `isRecord` | toggle recording |

There is **no** dedicated previous/next instruction: the original finds the adjacent `songPath` in the last
`MusicList` (`193`) it received and re-sends it as `instruction = 1`. `EnmPlayMode = Loop(0), Single(1),
Queue(2), Random(3), Once(4)`.

These integers are ad-hoc UI constants, **not** `EnmCommand` members. The `Command` / `EnmCommand`
messages in `data.common.command.proto` (`Record_Stop = 3`, `Record_Play = 4`, …) are a different,
unused path — the numeric overlap is a coincidence.

### 5.2 Console → client (`CmdHandler.processCommand`, 45 cases)

| protoId | Handler | Message |
|---|---|---|
| `1` | `proccessAllDataCmd` | `SKDMCData` (full state) |
| `101` | `proccessMicInputPartModelChangedEvent` | mic input stage |
| `103` | `proccessMicEqPartModelChangedEvent` | `EqPartModel` |
| `105` | `proccessMicDynamicPartModelChangedEvent` | `MicInDynamicPartModel` |
| `107` | `proccessMicBusPartModelChangedEvent` | `MicInBusModel` |
| `109` | `proccessMicOutputPartModelChangedEvent` | `MicInOutputPartModel` |
| `111` | `proccessBusInputPartModelChangedEvent` | `BusInputModel` |
| `113` | `proccessBusOutputPartModelChangedEvent` | `BusOutputModel` |
| `115` | `proccessSetupInfoPartModelChangedEvent` | `SetupInfoModel` |
| `117` | `proccessSetupPatchPartModelChangedEvent` | `SetupPatchModel` |
| `119` | `proccessSetupFxModulPartModelChangedEvent` | `SetupFxModulModel` |
| `121` | `proccessSetupFxDelayPartModelChangedEvent` | `SetupFxDelayModel` |
| `123` | `proccessSetupFxReverbPartModelChangedEvent` | `SetupFxReverbModel` |
| `125` | `proccessSetupFxGeqPartModelChangedEvent` | `SetupFxGeqModel` |
| `127` | `proccessSetupMonitorPartModelChangedEvent` | `SetupMonitorModel` |
| `129` | `proccessSetupMeterPartModelChangedEvent` | `SetupMeterModel` |
| `131` | `proccessIn1AndIn2ChannelNamePartModelChangedEvent` | in-channel names |
| `133` | `proccessOutChannelNamePartModelChangedEvent` | out-channel names |
| `141`–`163` | `proccessSetupScenes*` | scene select/copy/new/save/load/reorder |
| `173` | `proccessEqLibrarySavePartModelChangedEvent` | EQ library save result |
| `177` | `proccessDynamicLibrarySavePartModelChangedEvent` | dynamics library save result |
| `191` | `proccessMediaChangedEvent` | media/player state |
| `193` | `proccessMediaMusicListEvent` | `MusicList` |
| `195` | `proccessMediaMusicStateEvent` | playback state (`PlayCommand`) |
| `201` | `processDynamicLibrayChangedEvent` | dynamics library |
| `203` | `processPeqLibrayChangedEvent` | PEQ library |
| `205` | `processFxGeqLibListEvent` | GEQ library |
| `207` | `handleOutInputPartChangedEvent` | output input part |
| `211` | `processSeceneExportEvent` | scene export |
| `213` | `processGetImportFileListEvent` | scene import file list |
| `215` | `processImportFileEvent` | scene import |
| `221` | `processModuleLibListEvent` | FX module library |
| `227` | `processFxDelayLibListEvent` | delay library |
| `233` | `processReverbLibListEvent` | reverb library |

Outbound command results/acks come back as the corresponding odd change events. `data.common.command.proto`
also defines `Command` / `EnmCommand` (play, record transport, scene export/import) and
`data.common.commandResult.proto` defines `CommandResult { bool result; string text; }`. Note that the
**recorder/transport path does not use `Command`**: play/pause/stop/record go out as a `PlayCommand` on
`192` and come back on `191`/`195` (§5.3).

The **music channel** (`6402`) is deliberately absent from this table: it carries no protoId at all, only
a bare delimited `PlayCommand` (§3.4).

## 6. Message schema

`dm20-protocol.proto` contains the full reconstructed schema:

- **45 application `.proto` files** (package `com.amo.skdmc.data`), **107 messages**, **14 enums**, **704 fields**.
- Root aggregate: `SKDMCData` (protoId 1) =
  `In1Model`, `In2Model`, `BusModel`, `SetupModel`, `repeated PeqModel`, `repeated DynamicModel`,
  `repeated FxgeqModel`, `repeated SceneModel`, `sceneGuid`, `repeated FxdelayModel`,
  `repeated FxmodulModel`, `repeated FxreverbModel`.
- Per-message change events mirror the sub-models: `MicInInputPartModel`, `EqPartModel`, `MicInDynamicPartModel`,
  `MicInBusModel`, `MicInOutputPartModel`, `BusInputModel`, `BusOutputModel`, `SetupModel`,
  `SetupFxModulModel`, `SetupFxDelayModel`, `SetupFxReverbModel`, `SetupFxGeqModel`, `SetupMonitorModel`,
  `SetupMeterModel`, `SetupPatchModel`, `SceneModel`, `MusicItem`/`MusicList`, `PlayCommand`, `MeterLevelData`.
- 13 of the recovered files are the standard `google/protobuf/*.proto` (descriptor/any/type/… bundled with
  the protobuf library) and are included for completeness.

### 6.1 Units & scaling (recovered)

The wire models carry values in **display units**; the `com.amo.skdmc` UI converts to/from engineering
units locally. The central helpers live in `com.amo.skdmc.util.HardwareUtil`.

- **Fader / gain.** `GainValue` on the wire is a **fader position `0…1000`** (the `Fader` control declares
  `max_value=1000`, `min_value=0`). It is converted to dB by a 1024-entry table built in
  `HardwareUtil.InitConvTab`:

  ```
  fConvTab[i] = -1.789e-15·i⁶ + 6.199e-12·i⁵ - 8.398e-9·i⁴
              + 5.669e-6·i³ - 0.002059·i² + 0.4686·i - 80.51        (i = 0…1023)
  ConvertFaderValue(pos) = fConvTab[(int) pos]
  ```

  Range ≈ **−80.5 dB at position 0 → +10.1 dB at 1000**; 0 dB sits at position ≈ **755**. This dB value is
  what post-fader metering adds to the meter byte (§3.3), and it is cached per module as
  `DataTemUtils.fFaderValues[moduleId]` (populated in `MainPersenter.setMeterArrayData` /
  `onBusOutPutStateData`).
- **Dynamics (gate / compressor).** The `MicInDynamicPartModel` fields carry the **engineering value
  itself** — there is no position↔dB encoding like the mixer fader. `DynamicsView.setShowData` copies each
  `model.XValue` straight into the control (`SelectedFader.setCurrValue` / `EncoderDisplayControl.setValue`)
  and each fader listener writes the control's value straight back (`model.GateThresholdValue = currProgress`)
  before posting the whole model. The ranges are the `app:min_value`/`app:max_value` bounds declared in
  `res/layout/view_dynamics_show.xml`; the unit is the paired encoder's `app:edc_unit`:

  | `MicInDynamicPartModel` field | Range | Unit (`edc_unit`) |
  |---|---|---|
  | `GateThresholdValue` | −80…0 | dB (read-out `+24` → dBu, 4) |
  | `GateAttackValue` | 0.5…100 | ms (1) |
  | `GateReleaseValue` | 2…2000 | ms (1) |
  | `GateHoldValue` | 2…2000 | ms (1) |
  | `GateDepthValue` | −80…0 | dB (3) |
  | `CompressorThresholdValue` | −80…0 | dB (read-out `+24` → dBu, 4) |
  | `CompressorAttackValue` | 0.5…100 | ms (1) |
  | `CompressorReleaseValue` | 20…5000 | ms (1) |
  | `CompressorGainValue` | −12…12 | dB (3) |
  | `CompressorRatioValue` | 1…20 | unitless (8) |

  The two threshold read-outs render `value + 24` (`DynamicsRanges.THRESHOLD_TO_DBU_OFFSET`); the wire value
  is the raw `−80…0` figure. Implemented as `Units.DynamicsRanges`; `ParamRange.clamp` is the only
  conversion a command needs.
- **Monitor.** `SetupMonitorModel` values are likewise stored raw (`MonitorView.setData` / `bindEvent`),
  ranges from `res/layout/view_monitor_show.xml`: `MpLevelValue`, `OsciLevelValue` and `SoloAflValue` are
  encoder values over **−76…0 dB**; `OsciFreqValue` is stored in **Hz**, the encoder position mapping through
  `HardwareUtil.ConvertFrequencyValue(10, 20000, pos)` — the one monitor value that is not raw.
  Implemented as `Units.MonitorRanges`.
- **PEQ frequency.** Encoder position `0…1000` ↔ **20 Hz…20 kHz** log-scale:
  `Hz = min·(max/min)^(pos/1000)` with `HardwareUtil.ConvertFrequencyValue(min=20, max=20000, pos)`; the
  inverse is `ConvertEnableValue(min, max, hz)`. The wire `EqPartModel.*FreqValue` field stores **Hz**
  (`PEQPartModel.parseProtoModel` copies it straight through, and `EQView` converts back to an encoder
  position for display via `ConvertEnableValue`).
- **PEQ gain** — wire stores **dB**, encoder range **−18…+18 dB** (step 0.1).
  **PEQ Q** — wire stores **Q**, range **0.5…10** (step 0.05). Neither is rescaled on the wire.
- **PEQ band order and filter shape.** `selectedEq` is the `EqFragment` enum, in the same order as the
  four `EqPartModel` bands: `HIGH` (0) → `HIGH_MID` (1) → `LOW_MID` (2) → `LOW` (3). `highType` /
  `lowType` are the filter-shape code of the high and low bands only (the two mid bands have no type
  field): `0` = bell, `1` = **low** shelf, `2` = **high** shelf. The bands deliberately use *different*
  shelf codes, recovered from `innerview.EQView`. `isByPass` bypasses the **whole** EQ, not one band
  (`EQView` has no per-band enable). `selectedLibIndex` is the chosen preset index.
- **GEQ.** Fader **−18…+18 dB** ↔ DSP value **0…1000**:
  `ConvertGeqDspValue(db) = (18 + db)·1000/36`; `ConvertGeqFaderValue(v) = 0.036·v − 18`.
- **Delay / distance.** The stored time value is in **ms**; `ConvertMValue(ms) = 343.2·ms/1000` (metres)
  and `ConvertFtValue(ms) = 1126·ms/1000` (feet).
- **Display units** (`app:edc_unit` on `EncoderDisplayControl`), recovered from its static map:
  `1` = `" ms"`, `2` = `"Hz"`, `3` = `" dB"`, `4` = `" dBu"`, `5` = `"%"`, `6` = `" BPM"`,
  `7` = `"factor"` (ratio, formatted by `getFactorRatio`), `8` = `""` (unitless).
- **FX ranges (observed, not yet wired up).** The FX views follow the same "raw engineering value" rule
  (`FxModuleView`/`FxDelayView`/`FxReverbView.setData`): `DryWetValue` `0…100`, `FactorValue` `0…13`
  (a time-factor index), `TempoValue` in BPM, `FeedbackValue` `0…100`, `RateValue` `50…200`,
  `IntensityValue` `50…200`. Recovery is incomplete for reverb time: `fx_reverb_view.xml` declares
  `timeFader` as `0…100` while the unused `fx_reverb_view_copy.xml` declares four faders over `2…2000`.
- **Module IDs.** Bus modules are **128…135** (`Bus1…Bus8`); `ModuleConst.MonitorParam = 273`;
  `ModuleConst.EfxParam1/EfxParam2/PatchParam/SystemParam` are the other hardware parameter groups.

## 7. Service / IPC

`com.esunny.sound.service.ConnectService` is **exported with no permission** and returns an
`ICommandManager` binder:

```
connect() / disconnect() / sendCommand(...) / setOnViewDataChangeListener(...)
```

Any local app can bind and drive the mixer or subscribe to state.

## 8. Open questions

**Resolved during this analysis:**

- ✅ **Field units/scaling** — see §6.1 (fader position↔dB table, PEQ freq/gain/Q, GEQ, delay, display
  units).
- ✅ **Meter payload encoding** — see §3.3 (80 signed 8-bit dBFS values, fixed slot map, display ranges).
- ✅ **Fader/gain wire domain** — `GainValue` is a `0…1000` position, not dB.
- ✅ **Non-standard control ranges (dynamics/monitor).** See §6.1. The `SelectedFader` ranges
  (`100…0.5`, `2000…2`, `12…−12`, `20…1`, …) are simply the **range of the wire value itself**: the
  dynamics and monitor fields carry the engineering value (dB/dBu/ms/ratio/Hz), not a position. The legacy
  `NettyConnect.send110Data` hard-code (`outFaderValue = 233.45f`) has **zero callers** and sits inside the
  normal `0…1000` output-fader position domain — a leftover test helper, not reproduced.
- ✅ **Music channel framing** — see §3.4: a bare length-delimited `PlayCommand` with **no** magic and
  **no** protoId. Recovered from `AnonymousClass4.channelRead` piped straight into
  `proccessMediaMusicTimeEvent`'s `parseDelimitedFrom`. Because the original discards the parsed value,
  the *message type* still wants a hardware confirmation (issue `009`).
- ✅ **Outbound transport payload** — see §5.3. protoId `192` carries a `PlayCommand` (not the wire model the
  method name suggests), and the `instruction` / `EnmPlayMode` values used by the recorder UI are recovered.
  What a real console sends on the music channel *and* the literal `BusParam4` value still want hardware. 

**Still open (all non-blocking for a basic client):**

1. **Scene import/export file format** (`ImportSceneList`, `Scene_Export`/`Scene_Import`) and whether the
   bundled sample data is zipped.
2. **Command acknowledgement semantics** vs. change events (whether `CommandResult` is sent reliably).
3. **Music-channel payload on real hardware** — §3.4 fixes the *framing* statically; only the message type
   on `6402` still wants confirmation (issue `009`).

## 9. How the schema was recovered

The generated protobuf classes store the serialized `FileDescriptorProto` as a `String[]` of
**400-byte chunks** in each `<clinit>`. The chunks are stored in the DEX string pool *sorted by content*,
so the original order is only recoverable from the `<clinit>` bytecode. The extraction script:

1. parses both DEX files (string pool, class defs, `class_data`, `code_item`),
2. walks each `<clinit>` instruction stream and collects `const-string` / `const-string/jumbo` operands in
   code order (direct- and virtual-method index diffs are each relative to their own list),
3. concatenates successive chunks until the buffer parses as a complete `FileDescriptorProto`,
4. renders the descriptor back to `.proto` text.

## 10. End-to-end verification

The recovered protocol was exercised without any DM20 hardware by pointing the replacement app
(`com.dm20remote`) at a mock console that implements this document: UDP discovery on `6399`, the control
channel on `6400`, the meter channel on `6401`, and the music channel on `6402`.

**What the mock proved**

| Observation | Result |
|---|---|
| Init request | Received as exactly `[5A x6][int32 1][int32 0]`, 14 bytes, no varint — matching §3.1 |
| Snapshot | `SKDMCData` serialised by the mock was decoded by the app; channel names and bus names rendered |
| Fader conversion | Mock positions 760/745/700/690/720/680/780/765/640 produced `+0.2 / -0.3 / -1.9 / -2.3 / -1.2 / -2.6 / +0.9 / +0.4 / -4.2 dB`, matching `HardwareUtil.ConvertFaderValue` |
| Meter channel | `[5A 5A][int32 0][int32 len][varint][80 bytes]` decoded continuously (thousands of frames) |
| Input fader | Drag produced `protoId 110` with `moduleId`, `panValue`, `isSoloEnable`, `isMuteEnable`, `outFaderValue`; values clamp to `0…1000` |
| Input mute / solo | Each tap produced a correct `protoId 110` with only the changed flag differing |
| Bus fader | Produced `protoId 114` with `moduleId 128` (BUS 1) and the expected `gainValue` |
| Change events | Echoing `109` / `113` back updated the UI, confirming the mirroring described in §4 |
| PEQ | Tapping the editor's **Flat** produced `protoId 104` with all four bands; the `103` echo moved the read-outs to the 4000/1000/200/60 Hz, 0 dB, Q 3 curve |
| Dynamics | Enabling the compressor produced `protoId 106` with the gate still set (state-preserving); the `105` echo flipped the switch and updated the threshold read-out after a drag |
| Music channel | `[varint len][PlayCommand]` frames were decoded continuously; the Console tab showed `Music channel connected` and the song path, and the decoded `currentPosition` advanced one second per second |
| Transport commands | Play/pause, stop, record, play-mode and next produced protoId `192` commands carrying `instruction = 4 / 3 / 6 / 5 / 1`; each was echoed on `195` and the UI followed the echo (pause glyph, "Stop recording") |
| Music list & next | The mock's `193` `MusicList` populated the Music tab's file list; **Next** resolved to the adjacent path in that list and sent it as `instruction = 1` (no dedicated next command exists) |

Note that `moduleId 0` (IN 1) is **absent from the wire** because proto3 omits zero-valued fields — a
decoder must treat a missing `moduleId` as `0`.

**App-side defect found by the exercise.** Every command was silently dropped: the UI invoked the send
path from a tap handler, and the blocking socket write ran on the main thread, throwing
`NetworkOnMainThreadException` inside a `try/catch` that only recorded the failure. Commands are now
serialised through a single dedicated writer thread. The regression is covered by
`ConsoleClientTest`, which drives the real client against an in-process `ServerSocket`.

**Android-version coverage.** The same exercise was run on two emulators: an API 33 (Android 13,
`Pixel_3a_API_33_x86_64`) and an API 36 (Android 16, `Pixel_8_API_36`) device. Both produced identical
protocol traffic — init frame, snapshot decode, meter stream, `110`/`114` commands and `109` / `113`
echoes — so nothing in the framing or the codec depends on the OS version. On API 36 two behaviours
that only exist on modern Android were checked as well:

| Check (API 36) | Result |
|---|---|
| Edge-to-edge insets | With `targetSdk 36` edge-to-edge is enforced. `safeDrawingPadding()` keeps the header and strips clear of the 132 px landscape display cutout and the bars — verified from the view bounds (`header x=174`) |
| Foreground service type | `dumpsys activity services` reports `isForeground=true types=0x00000010` (`connectedDevice`); no `MissingForegroundServiceTypeException` |
| Notifications denied | With `POST_NOTIFICATIONS` denied the UI still shows Online, the service stays foreground, and commands still apply |

**Command payloads carry the whole model.** A fader drag was observed to send
`protoId 110 fader=561.57 pan=-25 mute=true solo=true` — i.e. every field the console had previously
reported for that channel, not just the fader. A command that omitted the untouched fields would reset
them on the hardware, so each outbound payload is built from the channel's current model with only the
touched field changed.

