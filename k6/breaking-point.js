/**
 * breaking-point.js  — finds max concurrent users automatically
 *
 * Uses k6's ramping-arrival-rate executor to keep pushing until
 * thresholds breach. Watch the output — when drop rate > 5% or
 * p95 latency > 500ms, that's your ceiling.
 *
 * Run:
 *   k6 run breaking-point.js
 *   k6 run breaking-point.js -e BASE_URL=http://localhost:8080 -e ROOM_ID=1
 */

import ws   from 'k6/ws';
import { check } from 'k6';
import { Counter, Gauge, Rate, Trend } from 'k6/metrics';

const BASE_URL     = __ENV.BASE_URL  || 'http://localhost:8080';
const ROOM_ID      = __ENV.ROOM_ID   || '1';
const PREFIX       = __ENV.PREFIX    || 'testuser';
const PASSWORD     = __ENV.PASSWORD  || 'Password123';
const MSG_INTERVAL = 500; // fire messages faster to stress the broker

export const options = {
  scenarios: {
    breaking_point: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 10  },
        { duration: '10s', target: 25  },
        { duration: '10s', target: 50  },
        { duration: '10s', target: 100 },
        { duration: '10s', target: 150 },
        { duration: '10s', target: 200 },
        { duration: '10s', target: 300 },
        { duration: '10s', target: 400 },
        { duration: '10s', target: 500 },
        { duration: '20s', target: 500 },  // hold at peak
        { duration: '10s', target: 0   },  // cool down
      ],
      gracefulRampDown: '5s',
    },
  },
  // Test STOPS automatically when any threshold is breached
  thresholds: {
    'ws_msg_latency':      [{ threshold: 'p(95)<1000', abortOnFail: true, delayAbortEval: '15s' }],
    'ws_msg_drop_rate':    [{ threshold: 'rate<0.10',  abortOnFail: true, delayAbortEval: '15s' }],
    'ws_connect_success':  [{ threshold: 'rate>0.90',  abortOnFail: true, delayAbortEval: '10s' }],
  },
};

const msgLatency     = new Trend('ws_msg_latency',      true);
const msgSent        = new Counter('ws_msg_sent');
const msgReceived    = new Counter('ws_msg_received');
const msgDropped     = new Counter('ws_msg_dropped');
const msgDropRate    = new Rate('ws_msg_drop_rate');
const connectSuccess = new Rate('ws_connect_success');
const activeConns    = new Gauge('ws_active_connections');

function randomStr(len = 8) {
  const c = 'abcdefghijklmnopqrstuvwxyz0123456789';
  let s = '';
  for (let i = 0; i < len; i++) s += c[Math.floor(Math.random() * c.length)];
  return s;
}

function stompFrame(command, headers = {}, body = '') {
  let f = command + '\n';
  for (const [k, v] of Object.entries(headers)) f += `${k}:${v}\n`;
  f += '\n' + body + '\0';
  return f;
}

function sockjsWsUrl(base) {
  const srv = String(Math.floor(Math.random() * 999)).padStart(3, '0');
  const sid = randomStr(8);
  return base.replace(/^https/, 'wss').replace(/^http/, 'ws') + `/ws/${srv}/${sid}/websocket`;
}

function unwrapSockJS(raw) {
  raw = String(raw);
  if (raw === 'o') return { type: 'o' };
  if (raw === 'h') return { type: 'h' };
  if (raw.startsWith('c')) return { type: 'close' };
  if (raw.startsWith('a')) {
    try { return { type: 'a', frames: JSON.parse(raw.slice(1)) }; } catch {}
  }
  return null;
}

function parseStompFrame(raw) {
  const clean = raw.replace(/\0+$/, '');
  const sep   = clean.indexOf('\n\n');
  const hPart = sep >= 0 ? clean.slice(0, sep) : clean;
  const body  = sep >= 0 ? clean.slice(sep + 2) : '';
  const lines = hPart.split('\n');
  const command = lines[0].trim();
  const headers = {};
  lines.slice(1).forEach(l => {
    const ci = l.indexOf(':');
    if (ci > 0) headers[l.slice(0, ci).trim()] = l.slice(ci + 1).trim();
  });
  return { command, headers, body };
}

export default function () {
  const username = `${PREFIX}${__VU}`;
  const url = sockjsWsUrl(BASE_URL);
  const pending = {};
  let connected = false;
  let msgCount = 0;

  const res = ws.connect(url, {}, function (socket) {
    socket.on('message', (data) => {
      const sj = unwrapSockJS(data);
      if (!sj) return;

      if (sj.type === 'o') {
        socket.send(JSON.stringify([stompFrame('CONNECT', {
          'accept-version': '1.1,1.0',
          'heart-beat': '10000,10000',
          login: username,
          passcode: PASSWORD,
        })]));
        return;
      }

      if (sj.type === 'close') { connected = false; activeConns.add(-1); return; }
      if (sj.type !== 'a') return;

      sj.frames.forEach(fs => {
        const f = parseStompFrame(fs);

        if (f.command === 'CONNECTED') {
          connected = true;
          connectSuccess.add(1);
          activeConns.add(1);

          socket.send(JSON.stringify([stompFrame('SUBSCRIBE', {
            id: `sub-${randomStr(4)}`,
            destination: `/topic/chat/${ROOM_ID}`,
          })]));

          socket.setInterval(() => {
            if (!connected) return;
            const tempId = `${username}-${Date.now()}-${randomStr(4)}`;
            socket.send(JSON.stringify([stompFrame('SEND',
              { destination: `/app/chat.send/${ROOM_ID}` },
              JSON.stringify({ content: `bp-test #${++msgCount}`, replyToMessageId: null, tempId })
            )]));
            pending[tempId] = Date.now();
            msgSent.add(1);
            socket.setTimeout(() => {
              if (pending[tempId] !== undefined) {
                delete pending[tempId];
                msgDropped.add(1);
                msgDropRate.add(1);
              }
            }, 5000);
          }, MSG_INTERVAL);

          socket.setInterval(() => { if (connected) socket.send('\n'); }, 10000);
        }

        if (f.command === 'MESSAGE') {
          try {
            const ev = JSON.parse(f.body);
            if (ev.type === 'CREATE') {
              msgReceived.add(1);
              const msg = ev.message;
              if (msg?.tempId && pending[msg.tempId] !== undefined) {
                msgLatency.add(Date.now() - pending[msg.tempId]);
                msgDropRate.add(0);
                delete pending[msg.tempId];
              }
            }
          } catch {}
        }

        if (f.command === 'ERROR') {
          connectSuccess.add(0);
          connected = false;
          socket.close();
        }
      });
    });

    socket.on('error', () => { connectSuccess.add(0); });
    socket.on('close', () => { if (connected) { connected = false; activeConns.add(-1); } });

    socket.setTimeout(() => {
      if (connected) socket.send(JSON.stringify([stompFrame('DISCONNECT', { receipt: 'bye' })]));
      socket.close();
    }, 130000);
  });

  check(res, { 'ws 101': (r) => r && r.status === 101 });
}

export function handleSummary(data) {
  const m = data.metrics;
  const v = (name, stat) => m[name]?.values?.[stat] ?? 0;

  console.log('\n╔══════════════════════════════════════════════════╗');
  console.log('║         Breaking Point Test — Summary            ║');
  console.log('╚══════════════════════════════════════════════════╝');
  console.log(`  Peak VUs reached  : ${v('ws_active_connections','max')}`);
  console.log(`  Connect rate      : ${(v('ws_connect_success','rate')*100).toFixed(1)}%`);
  console.log(`  Total sent        : ${v('ws_msg_sent','count')}`);
  console.log(`  Total received    : ${v('ws_msg_received','count')}`);
  console.log(`  Drop rate         : ${(v('ws_msg_drop_rate','rate')*100).toFixed(2)}%`);
  console.log(`  Latency p95       : ${v('ws_msg_latency','p(95)').toFixed(0)}ms`);
  console.log(`  Latency p99       : ${v('ws_msg_latency','p(99)').toFixed(0)}ms`);
  console.log('');

  const breached = Object.entries(data.thresholds || {})
    .filter(([,t]) => !t.ok)
    .map(([k]) => k);
  if (breached.length) {
    console.log(`  ✗ Breached thresholds → breaking point hit:`);
    breached.forEach(b => console.log(`    • ${b}`));
  } else {
    console.log('  ✓ All thresholds passed — try increasing --vus or adding more stages');
  }
  console.log('');
  return {};
}
