import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL  || 'http://localhost:8080';
const COUNT    = parseInt(__ENV.COUNT    || '100');
const PREFIX   = __ENV.PREFIX    || 'testuser';
const PASSWORD = __ENV.PASSWORD  || 'Password123';

export const options = {
  vus: 10,
  iterations: COUNT,
  thresholds: {
    'checks{type:register}': ['rate>0.95'],
  },
};

export default function () {
  const index = __ITER + 1;
  const username = `${PREFIX}${index}`;

  const res = http.post(
    `${BASE_URL}/api/users`,
    JSON.stringify({ username, password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  const ok = check(res, {
    'registered or exists': (r) =>
      r.status === 200 ||
      r.status === 201 ||
      r.status === 409 ||
      (r.status === 400 && r.body.toLowerCase().includes('exist')),
  }, { type: 'register' });

  if (!ok) {
    console.error(`Failed to register ${username}: HTTP ${res.status} — ${res.body}`);
  }
}

export function handleSummary(data) {
  const checks  = data.metrics['checks{type:register}'];
  const passes  = checks?.values?.passes  ?? 0;
  const fails   = checks?.values?.fails   ?? 0;
  console.log(`\n✓ Seeded: ${passes}   ✗ Failed: ${fails}`);
  console.log(`  Users ready: ${PREFIX}1 … ${PREFIX}${COUNT}`);
  console.log(`  Password   : ${PASSWORD}\n`);
  return {};
}