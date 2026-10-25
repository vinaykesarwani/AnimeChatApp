import http from 'k6/http';
import ws from 'k6/ws';
import { check, fail, sleep } from 'k6';
import execution from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';
import { readConfig, publicConfig } from './config.js';
import { frame, sockjsUrl, createDecoder } from './protocol.js';

const config = readConfig(__ENV);
export const options = {
  scenarios: {
    chat: {
      executor: 'ramping-vus',
      startVUs: config.profile === 'smoke' ? config.peak : 0,
      stages: config.stages,
      gracefulRampDown: `${config.graceMs}ms`,
      gracefulStop: `${config.graceMs}ms`,
    },
  },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
  systemTags: ['status', 'method', 'name', 'scenario', 'check', 'error', 'error_code', 'expected_response'],
  thresholds: {
    checks: ['rate==1'],
    login_success: ['rate==1'],
    history_success: ['rate==1'],
    chat_connect_success: ['rate>0.99'],
    chat_connect_ms: [`p(95)<${config.connectP95}`],
    history_ms: [`p(95)<${config.historyP95}`],
    chat_echo_ms: [`p(95)<${config.echoP95}`, `p(99)<${config.echoP99}`],
    chat_echo_success: ['rate>0.99'],
    chat_sent: ['count>0'],
    chat_acked: ['count>0'],
    chat_errors: ['count==0'],
    chat_early_closes: ['count==0'],
    http_req_failed: ['rate==0'],
  },
};

// A fast low-load phase must not hide a slow held load level in the aggregate.
const heldStages = { smoke: [], steady: [2], stress: [2, 4, 6], spike: [3, 5], soak: [2] };
for (const stage of heldStages[config.profile]) {
  const selector = `{phase:stage_${stage}}`;
  options.thresholds[`chat_echo_ms${selector}`] = [`p(95)<${config.echoP95}`, `p(99)<${config.echoP99}`];
  options.thresholds[`chat_echo_success${selector}`] = ['rate>0.99'];
  options.thresholds[`chat_acked${selector}`] = ['count>0'];
}

const loginSuccess = new Rate('login_success');
const loginMs = new Trend('login_ms', true);
const historySuccess = new Rate('history_success');
const historyMs = new Trend('history_ms', true);
const connectSuccess = new Rate('chat_connect_success');
const connectMs = new Trend('chat_connect_ms', true);
const sent = new Counter('chat_sent');
const acked = new Counter('chat_acked');
const echoMs = new Trend('chat_echo_ms', true);
const echoSuccess = new Rate('chat_echo_success');
const unacknowledged = new Counter('chat_unacknowledged');
const received = new Counter('chat_received');
const errors = new Counter('chat_errors');
const earlyCloses = new Counter('chat_early_closes');
const sessionsStarted = new Counter('chat_sessions_started');
const sessionsFinished = new Counter('chat_sessions_finished');
let token; // Each VU has its own module state and logs in once, like the browser.

function json(response) {
  try { return response.json(); } catch { return null; }
}

function phase() {
  const elapsed = (Date.now() - execution.scenario.startTime) / 1000;
  let end = 0;
  for (let index = 0; index < config.stages.length; index++) {
    end += Number(config.stages[index].duration.slice(0, -1));
    if (elapsed < end) return `stage_${index + 1}`;
  }
  return 'drain';
}

function tags(room, extra = {}) {
  return { profile: config.profile, topology: config.topology, room, phase: phase(), ...extra };
}

export function setup() {
  // Check fixtures before starting load. Registration belongs in seed.js.
  const response = http.post(`${config.baseUrl}/api/auth/login`, JSON.stringify({
    username: `${config.userPrefix}1`, password: config.password,
  }), { headers: { 'Content-Type': 'application/json' }, timeout: '10s', tags: { name: 'preflight_login' } });
  const auth = json(response);
  if (response.status !== 200 || !auth?.token || auth.user?.username !== `${config.userPrefix}1`) {
    fail('Preflight login failed. Run seed.js with the same USER_PREFIX and PASSWORD.');
  }
  const roomsResponse = http.get(`${config.baseUrl}/api/discussion-rooms`, {
    headers: { Authorization: `Bearer ${auth.token}` }, timeout: '10s', tags: { name: 'preflight_rooms' },
  });
  const rooms = json(roomsResponse);
  if (roomsResponse.status !== 200 || !Array.isArray(rooms)
      || config.rooms.some(id => !rooms.some(room => String(room.id) === id))) {
    fail('Preflight failed: one or more ROOM_IDS do not exist or cannot be read.');
  }
  return { runId: config.runId || `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}` };
}

export default function (data) {
  const room = config.rooms[(__VU - 1) % config.rooms.length];
  const sessionTags = tags(room);
  const username = `${config.userPrefix}${__VU}`;
  errors.add(0, sessionTags);
  earlyCloses.add(0, sessionTags);
  if (!token) {
    const response = http.post(`${config.baseUrl}/api/auth/login`, JSON.stringify({
      username, password: config.password,
    }), { headers: { 'Content-Type': 'application/json' }, timeout: '10s', tags: { ...sessionTags, name: 'login' } });
    const auth = json(response);
    const ok = response.status === 200 && typeof auth?.token === 'string' && auth.user?.username === username;
    loginSuccess.add(ok, sessionTags);
    loginMs.add(response.timings.duration, sessionTags);
    if (!ok) { sleep(1); return; }
    token = auth.token;
  }

  // The chat page loads history when it joins/rejoins a room.
  const history = http.get(`${config.baseUrl}/api/messages/discussion/${room}`, {
    headers: { Authorization: `Bearer ${token}` }, timeout: '10s',
    tags: { ...sessionTags, name: 'room_history' },
  });
  const historyOk = history.status === 200 && Array.isArray(json(history));
  historySuccess.add(historyOk, sessionTags);
  historyMs.add(history.timings.duration, sessionTags);
  if (!historyOk) { sleep(1); return; }

  const pending = new Map();
  const decode = createDecoder();
  let connected = false;
  let plannedClose = false;
  let socketError = false;
  let sequence = 0;
  let sendUntil = 0;
  const openedAt = Date.now();
  const sessionId = `${data.runId}-${__VU}-${__ITER}`;

  function settle(id, success) {
    const message = pending.get(id);
    if (!message) return;
    echoSuccess.add(success, message.tags);
    if (success) {
      acked.add(1, message.tags);
      echoMs.add(Date.now() - message.time, message.tags);
    } else {
      unacknowledged.add(1, message.tags);
    }
    pending.delete(id);
  }

  sessionsStarted.add(1, sessionTags);
  try {
    const response = ws.connect(sockjsUrl(config.baseUrl, sessionId), {
      headers: { Origin: config.origin }, tags: { ...sessionTags, name: 'sockjs_websocket' },
    }, socket => {
      function send(command, headers, body) {
        socket.send(JSON.stringify([frame(command, headers, body)]));
      }
      function reject(kind) {
        errors.add(1, tags(room, { kind }));
        socket.close();
      }
      socket.on('message', packet => {
        try {
          for (const event of decode(String(packet))) {
            if (event.command === 'SOCKJS_OPEN') {
              send('CONNECT', {
                'accept-version': '1.2', 'heart-beat': '10000,10000',
                host: 'localhost', login: '__jwt__', passcode: token,
              });
            } else if (event.command === 'CONNECTED') {
              if (connected) throw new Error('Duplicate CONNECTED frame');
              connected = true;
              connectMs.add(Date.now() - openedAt, sessionTags);
              send('SUBSCRIBE', { id: 'chat', destination: `/topic/chat/${room}` });
              send('SUBSCRIBE', { id: 'errors', destination: '/user/queue/errors' });
              // Ordered inbound processing preserves SUBSCRIBE before SEND.
              // The simple broker has no subscription receipt support.
              sendUntil = Date.now() + config.sessionMs;
              socket.setInterval(() => {
                if (Date.now() >= sendUntil) return;
                const id = `${sessionId}-${++sequence}`;
                const messageTags = tags(room);
                const content = `[load:${data.runId}] ${id}`.padEnd(200, '.');
                pending.set(id, { time: Date.now(), tags: messageTags, content });
                sent.add(1, messageTags);
                try {
                  send('SEND', { destination: `/app/chat.send/${room}`, 'content-type': 'application/json' },
                    JSON.stringify({ content, replyToMessageId: null, tempId: id }));
                } catch {
                  settle(id, false);
                  reject('send');
                }
              }, config.intervalMs);
              socket.setInterval(() => {
                const now = Date.now();
                for (const [id, message] of pending) {
                  if (now - message.time >= config.ackTimeoutMs) settle(id, false);
                }
              }, Math.min(250, config.ackTimeoutMs));
              socket.setInterval(() => socket.send(JSON.stringify(['\n'])), 10000);
              socket.setTimeout(() => {
                plannedClose = true;
                send('DISCONNECT');
                socket.close();
              }, config.sessionMs + config.ackTimeoutMs + 250);
            } else if (event.command === 'MESSAGE') {
              if (event.headers.subscription === 'errors') { reject('application'); return; }
              const update = JSON.parse(event.body);
              const message = update.message;
              if (update.type !== 'CREATE' || !message?.tempId?.startsWith(`${data.runId}-`)) continue;
              if (String(message.discussionRoom?.id) !== room || !message.id) {
                throw new Error('Unexpected room or unsaved message');
              }
              received.add(1, tags(room)); // Subscriber observations, not unique sends.
              const own = pending.get(message.tempId);
              if (own) {
                if (message.content !== own.content || message.sender?.username !== username) {
                  reject('echo_mismatch'); return;
                }
                settle(message.tempId, Date.now() - own.time < config.ackTimeoutMs);
              }
            } else if (event.command === 'ERROR') {
              reject('stomp'); return;
            } else if (event.command === 'SOCKJS_CLOSE') {
              socket.close(); return;
            }
          }
        } catch {
          reject('protocol');
        }
      });
      socket.on('error', () => {
        socketError = true;
        errors.add(1, tags(room, { kind: 'transport' }));
        socket.setTimeout(() => socket.close(), 1);
      });
      socket.on('close', () => {
        if (!plannedClose) earlyCloses.add(1, tags(room));
      });
      socket.setTimeout(() => {
        if (!connected) reject('connect_timeout');
      }, config.connectTimeoutMs);
    });
    check(response, { 'SockJS WebSocket upgraded': result => result?.status === 101 }, sessionTags);
  } catch {
    socketError = true;
    errors.add(1, tags(room, { kind: 'transport' }));
  } finally {
    // A closed socket cannot acknowledge pending sends. Never discard this tail.
    for (const id of pending.keys()) settle(id, false);
    connectSuccess.add(connected, sessionTags); // Exactly one outcome per attempt.
    sessionsFinished.add(1, sessionTags);
  }
  if (!connected || socketError) sleep(1);
}

export function handleSummary(data) {
  const value = (name, statistic) => data.metrics[name]?.values?.[statistic] ?? null;
  const samples = name => (value(name, 'passes') || 0) + (value(name, 'fails') || 0);
  const thresholds = Object.entries(data.metrics).flatMap(([metric, result]) =>
    Object.entries(result.thresholds || {}).filter(([, threshold]) => !threshold.ok)
      .map(([rule]) => `${metric}: ${rule}`));
  const measurements = {
    sends: value('chat_sent', 'count'), sendsPerSecond: value('chat_sent', 'rate'),
    acknowledged: value('chat_acked', 'count'), unacknowledged: value('chat_unacknowledged', 'count') || 0,
    subscriberObservations: value('chat_received', 'count'),
    echoP95Ms: value('chat_acked', 'count') > 0 ? value('chat_echo_ms', 'p(95)') : null,
    echoP99Ms: value('chat_acked', 'count') > 0 ? value('chat_echo_ms', 'p(99)') : null,
    connectionSuccess: samples('chat_connect_success') ? value('chat_connect_success', 'rate') : null,
    connectionP95Ms: value('chat_connect_success', 'passes') > 0 ? value('chat_connect_ms', 'p(95)') : null,
    loginP95Ms: samples('login_success') ? value('login_ms', 'p(95)') : null,
    historyP95Ms: samples('history_success') ? value('history_ms', 'p(95)') : null,
  };
  const complete = (value('chat_sessions_started', 'count') || 0) === (value('chat_sessions_finished', 'count') || 0)
    && (measurements.sends || 0) === (measurements.acknowledged || 0) + measurements.unacknowledged;
  const format = number => number === null ? 'n/a' : Number(number).toFixed(2);
  const report = {
    createdAt: new Date().toISOString(), config: publicConfig(config), measurements, complete,
    failedThresholds: thresholds, metrics: data.metrics,
    note: 'Echo timeouts are unacknowledged sends, not proof of lost database records. Receiver counts do not prove complete fanout.',
  };
  const output = [
    `Chat benchmark: ${config.profile} / ${config.topology}; rooms ${config.rooms.join(',')}`,
    `Accounting: ${complete ? 'complete' : 'INCOMPLETE - discard this run; sessions or sends were interrupted'}`,
    `Sends: ${measurements.sends ?? 0}; acknowledged: ${measurements.acknowledged ?? 0}; unacknowledged: ${measurements.unacknowledged}`,
    `Sender echo p95 / p99: ${format(measurements.echoP95Ms)} / ${format(measurements.echoP99Ms)} ms`,
    `Mean attempted sends/s (including ramp/drain): ${format(measurements.sendsPerSecond)}`,
    `Failed thresholds: ${thresholds.length ? thresholds.join('; ') : 'none'}`,
    report.note, '',
  ].join('\n');
  const files = { stdout: output };
  if (config.reportPrefix) {
    files[`${config.reportPrefix}.json`] = JSON.stringify(report, null, 2);
    files[`${config.reportPrefix}.txt`] = output;
  }
  return files;
}
