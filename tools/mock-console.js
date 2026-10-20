// ---------------------------------------------------------------------------
// Mock DM20 console - a tiny reference implementation of the recovered wire
// protocol, used to exercise the app without hardware.
//
//   node tools/mock-console.js
//
// It listens on the three documented ports (all overridable via environment
// variables) and:
//   * answers UDP discovery probes (any payload starting with 0x5A) with "OK ..."
//   * replies to the control-channel init request with a SKDMCData snapshot and
//     a MusicList (193) of the files on the fake USB stick
//   * streams 100-byte meter frames (80 mapped slots) at ~6 Hz, the measured
//     rate and shape of a real console
//   * streams the music channel as bare delimited PlayCommand frames (no magic,
//     no protoId) with a playback position that advances while playing
//   * applies play/pause/stop/select/record/play-mode commands (protoId 192) to
//     that player and reports the resulting state on 195
//   * mirrors accepted control changes back on the matching "change event" id
//     (PEQ 104 -> 103, dynamics 106 -> 105, input 110 -> 109, bus 114 -> 113),
//     carrying **only the fields that changed** — see `sparseEcho` below
//
// From an Android emulator, connect the app to 10.0.2.2 (host loopback).
// The supported ports and framing are specified in docs/protocol-spec.md.
// ---------------------------------------------------------------------------
'use strict';

const net = require('net');
const dgram = require('dgram');

const MAGIC = 0x5a;

// Command protoId -> the "change event" id a real console echoes it back on.
const ECHO_IDS = {
  104: 103, // PEQ
  106: 105, // dynamics
  110: 109, // input channel
  114: 113, // bus output
};

const controlPort = Number(process.env.DM20_CONTROL_PORT || 6400);
const meterPort = Number(process.env.DM20_METER_PORT || 6401);
const musicPort = Number(process.env.DM20_MUSIC_PORT || 6402);
const discoveryPort = Number(process.env.DM20_DISCOVERY_PORT || 6399);

// ---------------------------------------------------------------- proto encode
function varint(n) {
  const out = [];
  let v = n >>> 0;
  for (;;) {
    if ((v & ~0x7f) === 0) { out.push(v); break; }
    out.push((v & 0x7f) | 0x80);
    v >>>= 7;
  }
  return Buffer.from(out);
}
const tag = (f, wt) => varint((f << 3) | wt);
const i32 = (f, v) => Buffer.concat([tag(f, 0), varint(v)]);
const bool = (f, v) => Buffer.concat([tag(f, 0), varint(v ? 1 : 0)]);
const f32 = (f, v) => { const b = Buffer.alloc(4); b.writeFloatLE(v); return Buffer.concat([tag(f, 5), b]); };
const bytes = (f, b) => Buffer.concat([tag(f, 2), varint(b.length), b]);
const str = (f, s) => bytes(f, Buffer.from(s, 'utf8'));
const msg = (parts) => Buffer.concat(parts.filter(Boolean));

// ---------------------------------------------------------------- proto decode
function readVarint(buf, i) {
  let result = 0, shift = 0;
  while (true) {
    const b = buf[i++];
    result |= (b & 0x7f) << shift;
    if ((b & 0x80) === 0) return [result >>> 0, i];
    shift += 7;
  }
}
function parseFields(buf) {
  const out = {};
  let i = 0;
  while (i < buf.length) {
    let v; [v, i] = readVarint(buf, i);
    const field = v >>> 3, wt = v & 7;
    if (wt === 0) { let val; [val, i] = readVarint(buf, i); out[field] = val; }
    else if (wt === 5) { out[field] = buf.readFloatLE(i); i += 4; }
    else if (wt === 2) { let len; [len, i] = readVarint(buf, i); out[field] = buf.slice(i, i + len); i += len; }
    else if (wt === 1) { i += 8; }
    else throw new Error('bad wire type ' + wt);
  }
  return out;
}

/**
 * Same walk as `parseFields`, but keeps each field's *raw* tag+value bytes.
 *
 * The sparse echo (spec §4.1) needs to compare what a command says against what
 * the console last knew, byte for byte, and re-emit only the fields that moved.
 * Re-encoding from decoded values would need a wire type per field; carrying the
 * original bytes avoids that and keeps floats bit-exact.
 */
function parseRawFields(buf) {
  const out = {};
  let i = 0;
  while (i < buf.length) {
    const start = i;
    let v; [v, i] = readVarint(buf, i);
    const field = v >>> 3, wt = v & 7;
    if (wt === 0) { let val; [val, i] = readVarint(buf, i); }
    else if (wt === 5) i += 4;
    else if (wt === 2) { let len; [len, i] = readVarint(buf, i); i += len; }
    else if (wt === 1) i += 8;
    else throw new Error('bad wire type ' + wt);
    out[field] = { wt, raw: buf.slice(start, i) };
  }
  return out;
}

// ---------------------------------------------------------------- wire framing
function controlFrame(protoId, payload) {
  const v = varint(payload.length);
  const header = Buffer.alloc(14);
  header.fill(MAGIC, 0, 6);
  header.writeInt32BE(protoId, 6);
  header.writeInt32BE(v.length + payload.length, 10);
  return Buffer.concat([header, v, payload]);
}
function meterFrame(payload) {
  const v = varint(payload.length);
  const header = Buffer.alloc(10);
  header[0] = MAGIC; header[1] = MAGIC;
  header.writeInt32BE(0, 2);
  header.writeInt32BE(v.length + payload.length, 6);
  return Buffer.concat([header, v, payload]);
}

/**
 * Wraps the meter array in a `MeterLevelData { bytes gainValue = 1; }` message.
 *
 * The real console sends a protobuf *message*, not a bare array, so the envelope
 * (tag `0x0a` + length) has to be here too: an app that skipped it would read
 * the tag and length as the first two meter values and shift every slot by two.
 */
function meterMessage(values) {
  return msg([bytes(1, values)]);
}

// ---------------------------------------------------------------- model builders
function outputPart(moduleId, fader, pan, mute, solo) {
  return msg([
    i32(1, moduleId),
    f32(2, pan),
    bool(3, solo),
    bool(4, mute),
    f32(5, fader),
    bool(6, true),
  ]);
}
function channel(moduleId, name, fader, pan, mute, solo) {
  return msg([
    i32(1, moduleId),
    str(2, name),
    bytes(4, eqPart(moduleId)),
    bytes(5, dynamicPart(moduleId)),
    bytes(7, outputPart(moduleId, fader, pan, mute, solo)),
  ]);
}
// EqPartModel: four flattened bands (high/highMid/lowMid/low x freq/gain/Q),
// selectedEq (15), selectedLibIndex (16), highType (17), lowType (18).
function eqPart(moduleId) {
  return msg([
    i32(1, moduleId),
    bool(2, false),
    f32(3, 4000), f32(4, (moduleId % 3 - 1) * 2), f32(5, 0.8),
    f32(6, 1000), f32(7, 0), f32(8, 3),
    f32(9, 250), f32(10, -3), f32(11, 2),
    f32(12, 80), f32(13, 1.5), f32(14, 0.7),
    i32(15, 0), i32(17, 0), i32(18, moduleId % 2),
  ]);
}
// MicInDynamicPartModel: gate (2..7) and compressor (8..13).
function dynamicPart(moduleId) {
  return msg([
    i32(1, moduleId),
    bool(2, moduleId % 2 === 0),
    f32(3, -40), f32(4, 10), f32(5, 200), f32(6, 100), f32(7, -60),
    bool(8, moduleId % 2 === 1),
    f32(9, -20), f32(10, 10), f32(11, 200), f32(12, 0), f32(13, 4),
  ]);
}
function busOutput(moduleId, gain, pan, mute, solo) {
  return msg([
    i32(1, moduleId),
    bool(2, solo),
    bool(3, mute),
    bool(4, false),
    f32(5, 0),
    f32(6, pan),
    f32(7, gain),
    bool(8, false),
  ]);
}
function outBus(index, name, gain, pan) {
  const moduleId = 128 + index;
  return msg([
    i32(1, moduleId),
    str(2, name),
    bytes(6, busOutput(moduleId, gain, pan, false, false)),
  ]);
}

// PlayCommand (protoId 192 out, 191/195 in, and the bare music-channel frames).
// Fields: 1 playMode, 2 songPath, 3 songDuration, 4 currentPosition,
// 5 instruction, 6 isRecord, 7 isPlay, 8 recordTimeMs.
function playCommand(s) {
  return msg([
    s.playMode ? i32(1, s.playMode) : null,
    s.songPath ? str(2, s.songPath) : null,
    i32(3, s.songDuration),
    i32(4, s.currentPosition),
    s.instruction ? i32(5, s.instruction) : null,
    bool(6, !!s.isRecord),
    bool(7, !!s.isPlay),
  ]);
}
function musicItem(name, path) {
  return msg([str(1, name), str(2, path)]);
}
function musicList(tracks) {
  return msg(tracks.map((t) => bytes(1, musicItem(t.name, t.path))));
}

/** The reference USB player's contents; `filename` is the display title. */
const MUSIC_TRACKS = [
  { name: 'Demo Song', path: '/usb1/demo-song.mp3', duration: 214000 },
  { name: 'Soundcheck Sweep', path: '/usb1/soundcheck.mp3', duration: 95000 },
  { name: 'Walk-in Loop', path: '/usb1/walkin.mp3', duration: 180000 },
];

const INPUT_NAMES = ['Vox 1','Vox 2','Gtr L','Gtr R','Bass','Keys L','Keys R','Kick','Snare','OH L','OH R','Talk'];
const FADERS      = [760, 745, 700, 690, 720, 680, 680, 780, 765, 640, 640, 600];

// The console's stored model, one entry per module. The snapshot is built from
// these and the sparse echo diffs against them, so both sides agree by
// construction — a mock whose echo state drifted from its snapshot would
// manufacture events the real desk never sends.
function inputState(i) {
  return i < 8
    ? { fader: FADERS[i], pan: i % 2 ? 25 : -25, mute: false, solo: i === 0 }
    : { fader: FADERS[i], pan: 0, mute: false, solo: false };
}
function busState(b) {
  return { gain: 700 + b * 5, pan: 0, mute: false, solo: false };
}

function buildSnapshot() {
  const in1 = msg(INPUT_NAMES.slice(0, 8).map((n, i) => {
    const s = inputState(i);
    return bytes(i + 1, channel(i, n, s.fader, s.pan, s.mute, s.solo));
  }));
  const in2 = msg(INPUT_NAMES.slice(8, 12).map((n, i) => {
    const s = inputState(8 + i);
    return bytes(i + 1, channel(8 + i, n, s.fader, s.pan, s.mute, s.solo));
  }));

  const bus = msg([1,2,3,4,5,6,7,8].map(b => {
    const s = busState(b);
    return bytes(b, outBus(b - 1, 'BUS ' + b, s.gain, s.pan));
  }));

  // SetupInfo holds a selector tag (1) followed by the proto3-agnostic fields.
  const info = msg([
    str(15, '1.0.3-MOCK'),
    str(16, 'dsp-2.4.1'),
    str(17, 'SN-MOCK-0001'),
  ]);
  const setup = msg([bytes(1, info)]);

  const scenes = ['Soundcheck', 'Service 1', 'Service 2'].map((name, i) =>
    bytes(8, msg([bool(1, i === 0), bool(2, i === 1), str(3, 'guid-' + i), str(4, name)])));

  return msg([
    bytes(1, in1),
    bytes(2, in2),
    bytes(3, bus),
    bytes(4, setup),
    Buffer.concat(scenes),
    str(9, 'guid-0'),
  ]);
}

// ---------------------------------------------------------------- console state
let toggle = 0;

// The USB player state, shared by the control channel (192 commands -> 195 state)
// and the music channel (bare PlayCommand ticks). The app only ever *sends*
// commands; this is the single source of truth the mock reports back.
const playback = {
  playMode: 0,
  songPath: MUSIC_TRACKS[0].path,
  songDuration: MUSIC_TRACKS[0].duration,
  currentPosition: 0,
  isPlay: true,
  isRecord: false,
};

function transportState() {
  return playCommand(playback);
}

// Mirrors the instruction handling recovered from the original RecorderView:
// the app sends the complete PlayCommand with one `instruction` change.
function applyTransport(fields) {
  const instruction = fields[5] || 0;
  const path = fields[2] ? fields[2].toString('utf8') : '';
  const track = MUSIC_TRACKS.find((t) => t.path === path);
  switch (instruction) {
    case 1: // select song (also how previous/next is expressed)
      if (path) playback.songPath = path;
      if (track) playback.songDuration = track.duration;
      playback.currentPosition = 0;
      playback.isPlay = true;
      playback.isRecord = false;
      break;
    case 3: // stop / restart at 0
      playback.currentPosition = 0;
      playback.isPlay = false;
      break;
    case 4: // play / pause toggle
      playback.isPlay = !playback.isPlay;
      break;
    case 5: // play mode
      playback.playMode = fields[1] || 0;
      break;
    case 6: // record toggle
      playback.isRecord = !!fields[6];
      break;
    default:
      break;
  }
}

// ---------------------------------------------------------------- sparse change events
// Spec §4.1: a real console echoes only the fields that *changed*, not the
// whole model. The mock used to echo its input back verbatim, which meant it
// always carried every field and could never reproduce the defect that hid
// behind that for months — a client replacing its model from a sparse event
// wipes everything the event left out (reported as "mute drops by itself").
const echoState = new Map(); // "<echoProtoId>:<moduleId>" -> { field: { wt, raw } }

function seedEcho(echoId, moduleId, model) {
  echoState.set(echoId + ':' + moduleId, parseRawFields(model));
}
function seedEchoState() {
  for (let i = 0; i < 12; i++) {
    const s = inputState(i);
    seedEcho(109, i, outputPart(i, s.fader, s.pan, s.mute, s.solo));
    seedEcho(103, i, eqPart(i));
    seedEcho(105, i, dynamicPart(i));
  }
  for (let b = 1; b <= 8; b++) {
    const s = busState(b);
    seedEcho(113, 127 + b, busOutput(127 + b, s.gain, s.pan, s.mute, s.solo));
  }
}
seedEchoState();

/**
 * Diffs a command against the stored model and returns the sparse event to echo.
 *
 * Rules, all taken from the capture (`wire-20261018-102004.log`):
 *  - `module_id` rides along on every observed event, changed or not, because an
 *    event without one decodes as module 0 and would retarget the wrong channel.
 *  - Anything else is included only when its bytes differ from the stored value.
 *  - The stored model is updated with everything the command carried, so the
 *    next diff starts from what the console now believes.
 */
function sparseEcho(echoId, moduleId, payload) {
  const key = echoId + ':' + moduleId;
  const stored = echoState.get(key);
  if (!stored) return payload; // unknown module: fall back to mirroring
  const incoming = parseRawFields(payload);
  const out = [];
  for (const f of Object.keys(incoming).sort((a, b) => a - b)) {
    const prev = stored[f];
    const now = incoming[f];
    if (!prev || !prev.raw.equals(now.raw)) out.push(now.raw);
    stored[f] = now;
  }
  const moduleIdField = incoming[1];
  if (moduleIdField && !out.some((b) => b.equals(moduleIdField.raw))) {
    out.unshift(moduleIdField.raw);
  }
  return Buffer.concat(out);
}

const controlServer = net.createServer((socket) => {
  console.log('[control] client connected', socket.remoteAddress + ':' + socket.remotePort);
  let pending = Buffer.alloc(0);

  socket.on('data', (chunk) => {
    pending = Buffer.concat([pending, chunk]);
    for (;;) {
      if (pending.length < 14) break;
      if (pending[0] !== MAGIC || pending[5] !== MAGIC) {
        console.log('[control] WARN bad magic, dropping buffer');
        pending = Buffer.alloc(0);
        break;
      }
      const protoId = pending.readInt32BE(6);
      const declared = pending.readInt32BE(10);
      if (declared === 0) {
        console.log('[control] <- init request (protoId 1, 14-byte frame)');
        pending = pending.slice(14);
        const snapshot = buildSnapshot();
        socket.write(controlFrame(1, snapshot));
        console.log('[control] -> snapshot (' + snapshot.length + ' bytes)');
        const songs = musicList(MUSIC_TRACKS);
        socket.write(controlFrame(193, songs));
        console.log('[control] -> music list (' + MUSIC_TRACKS.length + ' items)');
        continue;
      }
      let size, cursor;
      try { [size, cursor] = readVarint(pending, 14); } catch (e) { break; }
      if (pending.length < cursor + size) break;
      const payload = pending.slice(cursor, cursor + size);
      pending = pending.slice(cursor + size);

      let fields = {};
      try { fields = parseFields(payload); } catch (e) { /* not a message we log deeply */ }
      const detail = protoId === 110
        ? `moduleId=${fields[1] ?? 0} fader=${fields[5] ?? 0} pan=${fields[2] ?? 0} mute=${!!fields[4]} solo=${!!fields[3]}`
        : protoId === 114
        ? `moduleId=${fields[1] ?? 0} gain=${fields[7] ?? 0} pan=${fields[6] ?? 0} mute=${!!fields[3]} solo=${!!fields[2]}`
        : protoId === 104
        ? `moduleId=${fields[1] ?? 0} bypass=${!!fields[2]} high=${fields[4] ?? 0}dB@${fields[3] ?? 0}Hz low=${fields[13] ?? 0}dB@${fields[12] ?? 0}Hz`
        : protoId === 106
        ? `moduleId=${fields[1] ?? 0} gate=${!!fields[2]} gateThr=${fields[3] ?? 0}dB comp=${!!fields[8]} compThr=${fields[9] ?? 0}dB ratio=${fields[13] ?? 0}`
        : protoId === 192
        ? `instruction=${fields[5] ?? 0} songPath=${fields[2] ?? ''} playMode=${fields[1] ?? 0} isPlay=${!!fields[7]} isRecord=${!!fields[6]}`
        : `${size} bytes`;
      console.log(`[control] <- protoId ${protoId}  ${detail}  (declaredLen=${declared}, varintLen=${size})`);

      // Recorder/transport command: update the player and report the new state on 195.
      if (protoId === 192) {
        try { applyTransport(fields); } catch (e) { console.log('[control] transport apply failed', e.message); }
        socket.write(controlFrame(195, transportState()));
        console.log(`[control] -> protoId 195 state (instruction ${fields[5] || 0})`);
      }

      // A real console mirrors accepted changes to the client using the
      // "change event" id (one below the command id) — and only the fields
      // that actually moved (§4.1).
      const echoId = ECHO_IDS[protoId];
      if (echoId) {
        const echoPayload = sparseEcho(echoId, fields[1] ?? 0, payload);
        socket.write(controlFrame(echoId, echoPayload));
        console.log(`[control] -> echo protoId ${echoId} (${echoPayload.length} B, ${payload.length} B command)`);
      }
    }
  });

  socket.on('error', (e) => console.log('[control] socket error', e.message));
  socket.on('close', () => console.log('[control] client disconnected'));
});

const meterServer = net.createServer((socket) => {
  console.log('[meter] client connected', socket.remoteAddress + ':' + socket.remotePort);
  const timer = setInterval(() => {
    // The console reports 100 values; only the first 80 have a recovered slot
    // map, so the trailing 20 are sent silent (0x80) as on hardware.
    const raw = Buffer.alloc(100, 0x80);
    for (let i = 0; i < 80; i++) {
      // Slow sine sweep so the bars visibly move; keep it below 0 dBFS.
      const phase = (toggle + i * 7) % 40;
      const db = -60 + (1 - Math.abs(phase - 20) / 20) * 55;
      raw[i] = Math.max(-120, Math.min(0, Math.round(db))) & 0xff;
    }
    toggle++;
    socket.write(meterFrame(meterMessage(raw)));
    // ~6 Hz: the measured rate of a real console (median inter-frame gap 194 ms).
  }, 167);
  socket.on('close', () => { clearInterval(timer); console.log('[meter] client disconnected'); });
  socket.on('error', () => clearInterval(timer));
});

// The music channel carries nothing but a bare delimited PlayCommand
// ([varint len][payload]), with no magic and no protoId, because the original
// handler feeds the raw buffer straight to PlayCommand.parseDelimitedFrom.
// It ticks the shared player state once a second so a transport UI visibly moves.
const musicServer = net.createServer((socket) => {
  console.log('[music] client connected', socket.remoteAddress + ':' + socket.remotePort);

  function tick() {
    const play = transportState();
    socket.write(Buffer.concat([varint(play.length), play]));
  }

  tick(); // first frame immediately, then advance once a second while playing
  const timer = setInterval(() => {
    if (playback.isPlay && playback.songDuration > 0) {
      playback.currentPosition = (playback.currentPosition + 1000) % playback.songDuration;
    }
    tick();
  }, 1000);
  socket.on('close', () => { clearInterval(timer); console.log('[music] client disconnected'); });
  socket.on('error', () => clearInterval(timer));
});

const udp = dgram.createSocket('udp4');
udp.on('message', (msgBuf, rinfo) => {
  console.log('[udp] probe from', rinfo.address + ':' + rinfo.port, msgBuf.toString('hex'));
  if (msgBuf.length >= 4 && msgBuf[0] === MAGIC) {
    udp.send(Buffer.from('OK DM20 MOCK'), rinfo.port, rinfo.address);
  }
});
udp.bind(discoveryPort, () => console.log('[udp] discovery listening on ' + discoveryPort));

controlServer.listen(controlPort, () => console.log('[control] listening on ' + controlPort));
meterServer.listen(meterPort, () => console.log('[meter] listening on ' + meterPort));
musicServer.listen(musicPort, () => console.log('[music] listening on ' + musicPort));

process.on('SIGINT', () => process.exit(0));
