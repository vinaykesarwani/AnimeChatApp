#!/usr/bin/env node
/**
 * seed-users.js
 * Registers N test users against your Spring Boot backend.
 * Usage:
 *   node seed-users.js [count] [prefix] [password] [baseUrl]
 * Defaults:
 *   node seed-users.js 100 testuser Password123 http://localhost:8080
 */

const http = require('http');
const https = require('https');

const COUNT    = parseInt(process.argv[2])  || 100;
const PREFIX   = process.argv[3]            || 'testuser';
const PASSWORD = process.argv[4]            || 'Password123';
const BASE_URL = process.argv[5]            || 'http://localhost:8080';

const CONCURRENCY = 10; // parallel registration requests

function post(url, body) {
  return new Promise((resolve, reject) => {
    const data = JSON.stringify(body);
    const parsed = new URL(url);
    const lib = parsed.protocol === 'https:' ? https : http;
    const req = lib.request({
      hostname: parsed.hostname,
      port: parsed.port,
      path: parsed.pathname,
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(data),
      },
    }, (res) => {
      let raw = '';
      res.on('data', d => raw += d);
      res.on('end', () => resolve({ status: res.statusCode, body: raw }));
    });
    req.on('error', reject);
    req.write(data);
    req.end();
  });
}

async function registerUser(index) {
  const username = `${PREFIX}${index}`;
  try {
    const res = await post(`${BASE_URL}/api/users`, { username, password: PASSWORD });
    if (res.status === 200 || res.status === 201) {
      process.stdout.write(`\r✓ ${username} registered (${index}/${COUNT})`);
      return { username, ok: true };
    } else if (res.status === 409 || res.body?.toLowerCase().includes('exist')) {
      process.stdout.write(`\r~ ${username} already exists (${index}/${COUNT})`);
      return { username, ok: true, skipped: true };
    } else {
      return { username, ok: false, error: `HTTP ${res.status}: ${res.body}` };
    }
  } catch (err) {
    return { username, ok: false, error: err.message };
  }
}

async function runInBatches(total, concurrency, fn) {
  const results = [];
  for (let i = 0; i < total; i += concurrency) {
    const batch = [];
    for (let j = i; j < Math.min(i + concurrency, total); j++) {
      batch.push(fn(j + 1));
    }
    results.push(...await Promise.all(batch));
  }
  return results;
}

(async () => {
  console.log(`\nSeeding ${COUNT} users → ${BASE_URL}`);
  console.log(`Prefix: ${PREFIX}1 … ${PREFIX}${COUNT}  |  Password: ${PASSWORD}`);
  console.log(`Concurrency: ${CONCURRENCY} parallel requests\n`);

  const start = Date.now();
  const results = await runInBatches(COUNT, CONCURRENCY, registerUser);
  const elapsed = ((Date.now() - start) / 1000).toFixed(1);

  const ok      = results.filter(r => r.ok && !r.skipped).length;
  const skipped = results.filter(r => r.skipped).length;
  const failed  = results.filter(r => !r.ok);

  console.log(`\n\n──────────────────────────────`);
  console.log(`Done in ${elapsed}s`);
  console.log(`  Registered : ${ok}`);
  console.log(`  Already existed : ${skipped}`);
  console.log(`  Failed     : ${failed.length}`);
  if (failed.length) {
    console.log(`\nFailed users:`);
    failed.forEach(f => console.log(`  ${f.username}: ${f.error}`));
  }
  console.log(`\nUsers ready: ${PREFIX}1 … ${PREFIX}${COUNT}`);
  console.log(`Password  : ${PASSWORD}`);
})();
