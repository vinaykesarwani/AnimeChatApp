// These are experiment inputs, not claims about the application's capacity.
export function readConfig(env) {
  function number(name, fallback, minimum = 1) {
    const value = Number(env[name] === undefined ? fallback : env[name]);
    if (!Number.isSafeInteger(value) || value < minimum) {
      throw new Error(`${name} must be an integer >= ${minimum}`);
    }
    return value;
  }

  const profile = env.PROFILE || 'smoke';
  if (!['smoke', 'steady', 'stress', 'spike', 'soak'].includes(profile)) {
    throw new Error('PROFILE must be smoke, steady, stress, spike or soak');
  }
  const topology = env.TOPOLOGY || 'single';
  if (!['single', 'multi'].includes(topology)) throw new Error('TOPOLOGY must be single or multi');
  const rooms = (env.ROOM_IDS || '').split(',').map(id => id.trim());
  if (rooms.some(id => !/^[1-9][0-9]*$/.test(id)) || new Set(rooms).size !== rooms.length) {
    throw new Error('ROOM_IDS must contain distinct positive discussion-room IDs');
  }
  if (topology === 'multi' && rooms.length < 2) throw new Error('multi needs at least two ROOM_IDS');
  if (!env.PASSWORD) throw new Error('Set PASSWORD to the seeded users\' password');
  if (env.RUN_ID && !/^[A-Za-z0-9_-]{1,64}$/.test(env.RUN_ID)) {
    throw new Error('RUN_ID must be 1 to 64 letters, numbers, underscores or hyphens');
  }
  const baseUrl = (env.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
  if (!/^https?:\/\/[^/?#]+(?:\/[^?#]*)?$/.test(baseUrl) || /[\s@]/.test(baseUrl)) {
    throw new Error('BASE_URL must be an HTTP(S) URL without credentials or query parameters');
  }

  const peak = number('PEAK_VUS', profile === 'smoke' ? 2 : ['stress', 'spike'].includes(profile) ? 100 : 20);
  if (topology === 'multi' && peak < rooms.length) throw new Error('PEAK_VUS must cover every room');
  const low = Math.max(1, Math.floor(peak / 10));
  const quarter = Math.max(1, Math.floor(peak / 4));
  const half = Math.max(1, Math.floor(peak / 2));
  const hold = number('HOLD_SECONDS', profile === 'soak' ? 1800 : profile === 'smoke' ? 20 : 300);
  const stage = (seconds, target) => ({ duration: `${seconds}s`, target });
  const profiles = {
    smoke: [stage(hold, peak)],
    steady: [stage(30, peak), stage(hold, peak), stage(30, 0)],
    stress: [stage(30, quarter), stage(60, quarter), stage(30, half), stage(60, half),
      stage(30, peak), stage(60, peak), stage(30, 0)],
    spike: [stage(30, low), stage(5, peak), stage(30, peak), stage(5, low), stage(30, low), stage(10, 0)],
    soak: [stage(30, peak), stage(hold, peak), stage(30, 0)],
  };
  const config = {
    profile, topology, baseUrl, peak, stages: profiles[profile],
    rooms: topology === 'single' ? [rooms[0]] : rooms,
    userPrefix: env.USER_PREFIX || 'loaduser_', password: env.PASSWORD,
    origin: env.ORIGIN || 'http://localhost:3000',
    sessionMs: number('SESSION_SECONDS', 30, 2) * 1000,
    intervalMs: number('MSG_INTERVAL_MS', 1000, 50),
    ackTimeoutMs: number('ACK_TIMEOUT_MS', 5000),
    connectTimeoutMs: number('CONNECT_TIMEOUT_MS', 10000),
    echoP95: number('ECHO_P95_MS', 500), echoP99: number('ECHO_P99_MS', 1000),
    connectP95: number('CONNECT_P95_MS', 2000), historyP95: number('HISTORY_P95_MS', 1000),
    reportPrefix: env.REPORT_PREFIX || '',
    runId: env.RUN_ID || '',
  };
  if (config.intervalMs >= config.sessionMs) throw new Error('MSG_INTERVAL_MS must be shorter than a session');
  // k6/ws allows up to 60s for the HTTP upgrade before our STOMP timer can run.
  // Include login + history, upgrade, STOMP negotiation, sending and echo drain.
  config.graceMs = 20000 + 60000 + config.connectTimeoutMs + config.sessionMs + config.ackTimeoutMs + 12000;
  return config;
}

export function publicConfig(config) {
  const { password, ...safe } = config;
  return safe;
}
