import ws from 'k6/ws';
import { check } from 'k6';
import { Counter, Gauge, Rate, Trend } from 'k6/metrics';

// ── Config ─────────────────────────────────────────
const BASE_URL     = __ENV.BASE_URL     || 'http://localhost:8080';
const ROOM_ID      = __ENV.ROOM_ID      || '1';
const PREFIX       = __ENV.PREFIX       || 'testuser';
const PASSWORD     = __ENV.PASSWORD     || 'Password123';
const MSG_INTERVAL = parseInt(__ENV.MSG_INTERVAL || '1000');

// ── Scenario ───────────────────────────────────────
export const options = {
  scenarios: {
    websocket_load: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 150 },
        { duration: '60s', target: 150 },
        { duration: '20s', target: 0   },
      ],
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    'ws_msg_latency':          ['p(95)<500'],   // round-trip
    'ws_fanout_latency':       ['p(95)<500'],   // fanout
    'ws_msg_drop_rate':        ['rate<0.05'],
    'ws_connect_success':      ['rate>0.95'],
  },
};

// ── Metrics ────────────────────────────────────────
const msgLatency     = new Trend('ws_msg_latency',     true);  // round-trip (sender only)
const fanoutLatency  = new Trend('ws_fanout_latency',  true);  // fanout (every subscriber)
const timeToConnect  = new Trend('ws_time_to_connect', true);  // open → STOMP CONNECTED
const msgSent        = new Counter('ws_msg_sent');
const msgReceived    = new Counter('ws_msg_received');
const msgDropped     = new Counter('ws_msg_dropped');
const msgDropRate    = new Rate('ws_msg_drop_rate');
const connectSuccess = new Rate('ws_connect_success');

// ── Helpers ────────────────────────────────────────
function randomStr(len = 8) {
  const c = 'abcdefghijklmnopqrstuvwxyz0123456789';
  let s = '';
  for (let i = 0; i < len; i++) s += c[Math.floor(Math.random() * c.length)];
  return s;
}

function wsUrl(base) {
  return base.replace(/^https/, 'wss').replace(/^http/, 'ws') + '/ws';
}

function stompFrame(command, headers = {}, body = '') {
  let f = command + '\n';
  for (const [k, v] of Object.entries(headers)) {
    f += `${k}:${v}\n`;
  }
  f += '\n' + body + String.fromCharCode(0);
  return f;
}

function parseStompFrame(raw) {
  const clean = raw.replace(/\0+$/, '');
  const sep   = clean.indexOf('\n\n');
  const headerPart = sep >= 0 ? clean.slice(0, sep) : clean;
  const body       = sep >= 0 ? clean.slice(sep + 2) : '';
  const lines      = headerPart.split('\n');
  const command    = lines[0].trim();
  const headers    = {};
  lines.slice(1).forEach(l => {
    const i = l.indexOf(':');
    if (i > 0) headers[l.slice(0, i)] = l.slice(i + 1);
  });
  return { command, headers, body };
}

// ── Main VU ────────────────────────────────────────
export default function () {
  const username = `${PREFIX}${__VU}`;
  const url      = wsUrl(BASE_URL);
  const pending  = {};
  let stompConnected = false;
  let msgCount       = 0;
  let connectStart   = 0;

  const res = ws.connect(url, {}, function (socket) {

    socket.on('open', () => {
      connectStart = Date.now();  // ← start connect timer
      socket.send(
        stompFrame('CONNECT', {
          'accept-version': '1.1,1.0',
          'heart-beat':     '10000,10000',
          login:    username,
          passcode: PASSWORD,
        })
      );
    });

    socket.on('message', (data) => {
      const frame = parseStompFrame(data);

      // ── CONNECTED ──────────────────────────────
      if (frame.command === 'CONNECTED') {
        stompConnected = true;
        connectSuccess.add(1);
        timeToConnect.add(Date.now() - connectStart);  // ← record connect time

        socket.send(
          stompFrame('SUBSCRIBE', {
            id:          `sub-${randomStr(4)}`,
            destination: `/topic/chat/${ROOM_ID}`,
          })
        );

        // Send messages
        socket.setInterval(() => {
          if (!stompConnected) return;

          const tempId  = `vu${__VU}-${Date.now()}-${randomStr(4)}`;
          const payload = JSON.stringify({
            content: `msg #${++msgCount}`,
            tempId,
          });

          socket.send(
            stompFrame('SEND', {
              destination:      `/app/chat.send/${ROOM_ID}`,
              'content-type':   'application/json',
              'content-length': payload.length,
            }, payload)
          );

          pending[tempId] = Date.now();
          msgSent.add(1);

          // Drop timeout
          socket.setTimeout(() => {
            if (pending[tempId]) {
              delete pending[tempId];
              msgDropped.add(1);
              msgDropRate.add(1);
            }
          }, 5000);

        }, MSG_INTERVAL);

        // Heartbeat
        socket.setInterval(() => {
          if (stompConnected) socket.send('\n');
        }, 10000);
      }

      // ── MESSAGE ────────────────────────────────
      if (frame.command === 'MESSAGE') {
        try {
          const event = JSON.parse(frame.body);

          if (event.type === 'CREATE') {
            msgReceived.add(1);
            const now = Date.now();
            const msg = event.message;

            // ── Fanout latency (every subscriber) ──
            // Time from server broadcast → this client received it
            if (event.sentAt) {
              fanoutLatency.add(now - event.sentAt);
            }

            // ── Round-trip latency (sender only) ───
            // Time from sender sent → sender received echo
            if (msg?.tempId && pending[msg.tempId]) {
              msgLatency.add(now - pending[msg.tempId]);
              msgDropRate.add(0);
              delete pending[msg.tempId];
            }
          }
        } catch {}
      }

      // ── ERROR ──────────────────────────────────
      if (frame.command === 'ERROR') {
        connectSuccess.add(0);
        stompConnected = false;
        console.error(`STOMP ERROR | message: ${frame.headers['message']} | body: ${frame.body}`);
        socket.close();
      }
    });

    socket.on('error', (e) => {
      connectSuccess.add(0);
      console.error(`WS error VU${__VU}:`, e);
    });

    socket.on('close', () => {
      if (stompConnected) stompConnected = false;
    });

    socket.setTimeout(() => socket.close(), 130000);
  });

  check(res, { 'ws 101': (r) => r && r.status === 101 });
}

// ── Summary ────────────────────────────────────────
export function handleSummary(data) {
  const m = data.metrics;

  function val(name, stat) {
    return m[name]?.values?.[stat] ?? 0;
  }

  const sent       = val('ws_msg_sent',          'count');
  const received   = val('ws_msg_received',       'count');
  const dropped    = val('ws_msg_dropped',        'count');
  const dropPct    = sent > 0 ? ((dropped / sent) * 100).toFixed(2) : '0.00';

  // Round-trip (sender only)
  const rtAvg      = val('ws_msg_latency',        'avg').toFixed(0);
  const rtP95      = val('ws_msg_latency',        'p(95)').toFixed(0);
  const rtP99      = val('ws_msg_latency',        'p(99)').toFixed(0);
  const rtMax      = val('ws_msg_latency',        'max').toFixed(0);

  // Fanout (every subscriber)
  const foAvg      = val('ws_fanout_latency',     'avg').toFixed(0);
  const foP95      = val('ws_fanout_latency',     'p(95)').toFixed(0);
  const foP99      = val('ws_fanout_latency',     'p(99)').toFixed(0);
  const foMax      = val('ws_fanout_latency',     'max').toFixed(0);

  // Connect time
  const connAvg    = val('ws_time_to_connect',    'avg').toFixed(0);
  const connP95    = val('ws_time_to_connect',    'p(95)').toFixed(0);
  const connMax    = val('ws_time_to_connect',    'max').toFixed(0);

  const connRate   = (val('ws_connect_success',   'rate') * 100).toFixed(1);

  const lines = [
    '',
    '╔══════════════════════════════════════════════════╗',
    '║            k6 WebSocket Load Test Report         ║',
    '╚══════════════════════════════════════════════════╝',
    `  Backend        : ${BASE_URL}`,
    `  Room           : ${ROOM_ID}`,
    `  Msg interval   : ${MSG_INTERVAL}ms / VU`,
    '',
    '── Connections ─────────────────────────────────────',
    `  Success rate   : ${connRate}%`,
    `  Time to connect: avg=${connAvg}ms  p95=${connP95}ms  max=${connMax}ms`,
    '',
    '── Throughput ──────────────────────────────────────',
    `  Sent           : ${sent}`,
    `  Received       : ${received}`,
    `  Dropped        : ${dropped}  (${dropPct}%)`,
    '',
    '── Round-trip Latency (sender only) ────────────────',
    `  avg            : ${rtAvg}ms`,
    `  p95            : ${rtP95}ms`,
    `  p99            : ${rtP99}ms`,
    `  max            : ${rtMax}ms`,
    '',
    '── Fanout Latency (every subscriber) ───────────────',
    `  avg            : ${foAvg}ms`,
    `  p95            : ${foP95}ms`,
    `  p99            : ${foP99}ms`,
    `  max            : ${foMax}ms`,
    '',
    '── Thresholds ──────────────────────────────────────',
  ];

  ['ws_msg_latency', 'ws_fanout_latency', 'ws_msg_drop_rate', 'ws_connect_success'].forEach(name => {
    const t = data.thresholds?.[name];
    if (t) lines.push(`  ${t.ok ? '✓ PASS' : '✗ FAIL'}  ${name}`);
  });

  lines.push('');
  console.log(lines.join('\n'));
  return {};
}

// import ws from 'k6/ws';
// import { check } from 'k6';
// import { Counter, Gauge, Rate, Trend } from 'k6/metrics';

// // ── Config ─────────────────────────────────────────
// const BASE_URL     = __ENV.BASE_URL     || 'http://localhost:8080';
// // Comma-separated list of room IDs, e.g. ROOM_IDS=1,2,3
// // Falls back to a single room "1" if not provided.
// const ROOM_IDS = ['1', '2', '3', '4', '5'];
// const PREFIX       = __ENV.PREFIX       || 'testuser';
// const PASSWORD     = __ENV.PASSWORD     || 'Password123';
// const MSG_INTERVAL = parseInt(__ENV.MSG_INTERVAL || '1000');

// // ── Scenario ───────────────────────────────────────
// export const options = {
//   scenarios: {
//     websocket_load: {
//       executor: 'ramping-vus',
//       startVUs: 0,
//       stages: [
//         { duration: '30s', target: 750 },
//         { duration: '60s', target:  750},
//         { duration: '20s', target: 0  },
//       ],
//       gracefulRampDown: '10s',
//     },
//   },
//   thresholds: {
//     'ws_msg_latency':          ['p(95)<500'],
//     'ws_fanout_latency':       ['p(95)<500'],
//     'ws_msg_drop_rate':        ['rate<0.05'],
//     'ws_connect_success':      ['rate>0.95'],
//   },
// };

// // ── Metrics ────────────────────────────────────────
// const msgLatency     = new Trend('ws_msg_latency',     true);
// const fanoutLatency  = new Trend('ws_fanout_latency',  true);
// const timeToConnect  = new Trend('ws_time_to_connect', true);
// const msgSent        = new Counter('ws_msg_sent');
// const msgReceived    = new Counter('ws_msg_received');
// const msgDropped     = new Counter('ws_msg_dropped');
// const msgDropRate    = new Rate('ws_msg_drop_rate');
// const connectSuccess = new Rate('ws_connect_success');

// // ── Helpers ────────────────────────────────────────
// function randomStr(len = 8) {
//   const c = 'abcdefghijklmnopqrstuvwxyz0123456789';
//   let s = '';
//   for (let i = 0; i < len; i++) s += c[Math.floor(Math.random() * c.length)];
//   return s;
// }

// function wsUrl(base) {
//   return base.replace(/^https/, 'wss').replace(/^http/, 'ws') + '/ws';
// }

// function stompFrame(command, headers = {}, body = '') {
//   let f = command + '\n';
//   for (const [k, v] of Object.entries(headers)) {
//     f += `${k}:${v}\n`;
//   }
//   f += '\n' + body + String.fromCharCode(0);
//   return f;
// }

// function parseStompFrame(raw) {
//   const clean = raw.replace(/\0+$/, '');
//   const sep   = clean.indexOf('\n\n');
//   const headerPart = sep >= 0 ? clean.slice(0, sep) : clean;
//   const body       = sep >= 0 ? clean.slice(sep + 2) : '';
//   const lines      = headerPart.split('\n');
//   const command    = lines[0].trim();
//   const headers    = {};
//   lines.slice(1).forEach(l => {
//     const i = l.indexOf(':');
//     if (i > 0) headers[l.slice(0, i)] = l.slice(i + 1);
//   });
//   return { command, headers, body };
// }

// // ── Main VU ────────────────────────────────────────
// export default function () {
//   // Each VU is assigned a room round-robin based on its VU number.
//   // VU 1 → ROOM_IDS[0], VU 2 → ROOM_IDS[1], …, VU N+1 → ROOM_IDS[0], etc.
//   const roomId   = ROOM_IDS[(__VU - 1) % ROOM_IDS.length];
//   const username = `${PREFIX}${__VU}`;
//   const url      = wsUrl(BASE_URL);
//   const pending  = {};
//   let stompConnected = false;
//   let msgCount       = 0;
//   let connectStart   = 0;

//   const res = ws.connect(url, {}, function (socket) {

//     socket.on('open', () => {
//       connectStart = Date.now();
//       socket.send(
//         stompFrame('CONNECT', {
//           'accept-version': '1.1,1.0',
//           'heart-beat':     '10000,10000',
//           login:    username,
//           passcode: PASSWORD,
//         })
//       );
//     });

//     socket.on('message', (data) => {
//       const frame = parseStompFrame(data);

//       // ── CONNECTED ──────────────────────────────
//       if (frame.command === 'CONNECTED') {
//         stompConnected = true;
//         connectSuccess.add(1);
//         timeToConnect.add(Date.now() - connectStart);

//         socket.send(
//           stompFrame('SUBSCRIBE', {
//             id:          `sub-${randomStr(4)}`,
//             destination: `/topic/chat/${roomId}`,
//           })
//         );

//         // Send messages
//         socket.setInterval(() => {
//           if (!stompConnected) return;

//           const tempId  = `vu${__VU}-${Date.now()}-${randomStr(4)}`;
//           const payload = JSON.stringify({
//             content: `msg #${++msgCount}`,
//             tempId,
//           });

//           socket.send(
//             stompFrame('SEND', {
//               destination:      `/app/chat.send/${roomId}`,
//               'content-type':   'application/json',
//               'content-length': payload.length,
//             }, payload)
//           );

//           pending[tempId] = Date.now();
//           msgSent.add(1);

//           // Drop timeout
//           socket.setTimeout(() => {
//             if (pending[tempId]) {
//               delete pending[tempId];
//               msgDropped.add(1);
//               msgDropRate.add(1);
//             }
//           }, 5000);

//         }, MSG_INTERVAL);

//         // Heartbeat
//         socket.setInterval(() => {
//           if (stompConnected) socket.send('\n');
//         }, 10000);
//       }

//       // ── MESSAGE ────────────────────────────────
//       if (frame.command === 'MESSAGE') {
//         try {
//           const event = JSON.parse(frame.body);

//           if (event.type === 'CREATE') {
//             msgReceived.add(1);
//             const now = Date.now();
//             const msg = event.message;

//             if (event.sentAt) {
//               fanoutLatency.add(now - event.sentAt);
//             }

//             if (msg?.tempId && pending[msg.tempId]) {
//               msgLatency.add(now - pending[msg.tempId]);
//               msgDropRate.add(0);
//               delete pending[msg.tempId];
//             }
//           }
//         } catch {}
//       }

//       // ── ERROR ──────────────────────────────────
//       if (frame.command === 'ERROR') {
//         connectSuccess.add(0);
//         stompConnected = false;
//         console.error(`STOMP ERROR | VU${__VU} room=${roomId} | message: ${frame.headers['message']} | body: ${frame.body}`);
//         socket.close();
//       }
//     });

//     socket.on('error', (e) => {
//       connectSuccess.add(0);
//       console.error(`WS error VU${__VU} room=${roomId}:`, e);
//     });

//     socket.on('close', () => {
//       if (stompConnected) stompConnected = false;
//     });

//     socket.setTimeout(() => socket.close(), 130000);
//   });

//   check(res, { 'ws 101': (r) => r && r.status === 101 });
// }

// // ── Summary ────────────────────────────────────────
// export function handleSummary(data) {
//   const m = data.metrics;

//   function val(name, stat) {
//     return m[name]?.values?.[stat] ?? 0;
//   }

//   const sent       = val('ws_msg_sent',          'count');
//   const received   = val('ws_msg_received',       'count');
//   const dropped    = val('ws_msg_dropped',        'count');
//   const dropPct    = sent > 0 ? ((dropped / sent) * 100).toFixed(2) : '0.00';

//   const rtAvg      = val('ws_msg_latency',        'avg').toFixed(0);
//   const rtP95      = val('ws_msg_latency',        'p(95)').toFixed(0);
//   const rtP99      = val('ws_msg_latency',        'p(99)').toFixed(0);
//   const rtMax      = val('ws_msg_latency',        'max').toFixed(0);

//   const foAvg      = val('ws_fanout_latency',     'avg').toFixed(0);
//   const foP95      = val('ws_fanout_latency',     'p(95)').toFixed(0);
//   const foP99      = val('ws_fanout_latency',     'p(99)').toFixed(0);
//   const foMax      = val('ws_fanout_latency',     'max').toFixed(0);

//   const connAvg    = val('ws_time_to_connect',    'avg').toFixed(0);
//   const connP95    = val('ws_time_to_connect',    'p(95)').toFixed(0);
//   const connMax    = val('ws_time_to_connect',    'max').toFixed(0);

//   const connRate   = (val('ws_connect_success',   'rate') * 100).toFixed(1);

//   const lines = [
//     '',
//     '╔══════════════════════════════════════════════════╗',
//     '║            k6 WebSocket Load Test Report         ║',
//     '╚══════════════════════════════════════════════════╝',
//     `  Backend        : ${BASE_URL}`,
//     `  Rooms          : ${ROOM_IDS.join(', ')}`,
//     `  Msg interval   : ${MSG_INTERVAL}ms / VU`,
//     '',
//     '── Connections ─────────────────────────────────────',
//     `  Success rate   : ${connRate}%`,
//     `  Time to connect: avg=${connAvg}ms  p95=${connP95}ms  max=${connMax}ms`,
//     '',
//     '── Throughput ──────────────────────────────────────',
//     `  Sent           : ${sent}`,
//     `  Received       : ${received}`,
//     `  Dropped        : ${dropped}  (${dropPct}%)`,
//     '',
//     '── Round-trip Latency (sender only) ────────────────',
//     `  avg            : ${rtAvg}ms`,
//     `  p95            : ${rtP95}ms`,
//     `  p99            : ${rtP99}ms`,
//     `  max            : ${rtMax}ms`,
//     '',
//     '── Fanout Latency (every subscriber) ───────────────',
//     `  avg            : ${foAvg}ms`,
//     `  p95            : ${foP95}ms`,
//     `  p99            : ${foP99}ms`,
//     `  max            : ${foMax}ms`,
//     '',
//     '── Thresholds ──────────────────────────────────────',
//   ];

//   ['ws_msg_latency', 'ws_fanout_latency', 'ws_msg_drop_rate', 'ws_connect_success'].forEach(name => {
//     const t = data.thresholds?.[name];
//     if (t) lines.push(`  ${t.ok ? '✓ PASS' : '✗ FAIL'}  ${name}`);
//   });

//   lines.push('');
//   console.log(lines.join('\n'));
//   return {};
// }