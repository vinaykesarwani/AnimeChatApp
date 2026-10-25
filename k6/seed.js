#!/usr/bin/env node
'use strict';

// Prepare fixtures before a benchmark so registration is outside the measured load.
const TIMEOUT_MS = 15000;

function positiveInteger(name, fallback) {
  const value = process.env[name] === undefined ? String(fallback) : process.env[name];
  if (!/^[1-9]\d*$/.test(value) || !Number.isSafeInteger(Number(value))) {
    throw new Error(`${name} must be a positive integer.`);
  }
  return Number(value);
}

async function main() {
  const users = positiveInteger('USERS', 100);
  const roomCount = positiveInteger('ROOM_COUNT', 3);
  const prefix = process.env.USER_PREFIX || 'loaduser_';
  const password = process.env.PASSWORD;
  const base = (process.env.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
  let target;
  try { target = new URL(base); }
  catch { throw new Error('BASE_URL must be an HTTP or HTTPS URL.'); }
  if (!['http:', 'https:'].includes(target.protocol) || target.username || target.password
      || target.search || target.hash) {
    throw new Error('BASE_URL must be an HTTP or HTTPS URL without credentials, a query, or a fragment.');
  }
  if (typeof password !== 'string' || password.trim().length === 0
      || password.length < 4 || Buffer.byteLength(password, 'utf8') > 72) {
    throw new Error('Set PASSWORD to at least 4 characters and at most 72 UTF-8 bytes.');
  }
  if (prefix.trim() !== prefix || `${prefix}1`.length < 3 || `${prefix}${users}`.length > 255
      || `${prefix}benchmark`.length > 255) {
    throw new Error('USER_PREFIX must produce usernames and an anime name of 3 to 255 characters without surrounding spaces.');
  }

  async function request(path, method = 'GET', body, token) {
    const headers = {};
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (token) headers.Authorization = `Bearer ${token}`;
    try {
      return await fetch(`${base}${path}`, {
        method, headers,
        body: body === undefined ? undefined : JSON.stringify(body),
        signal: AbortSignal.timeout(TIMEOUT_MS),
      });
    } catch (error) {
      const reason = error.name === 'TimeoutError' ? 'timed out' : 'failed';
      throw new Error(`${method} ${path.split('?')[0]} ${reason}.`);
    }
  }

  async function json(response, label) {
    try { return await response.json(); }
    catch { throw new Error(`${label} returned invalid JSON.`); }
  }

  async function list(path, token) {
    const response = await request(path, 'GET', undefined, token);
    if (response.status !== 200) throw new Error(`GET ${path} returned HTTP ${response.status}.`);
    const items = await json(response, path);
    if (!Array.isArray(items)) throw new Error(`GET ${path} did not return a list.`);
    return items;
  }

  function validId(value) {
    return Number.isSafeInteger(value) && value > 0;
  }

  let fixtureToken;
  let createdUsers = 0;
  for (let index = 1; index <= users; index += 1) {
    const username = `${prefix}${index}`;
    const registered = await request('/api/users', 'POST', { username, password });
    // A conflict is acceptable only if the supplied password subsequently authenticates.
    if (![201, 409].includes(registered.status)) {
      throw new Error(`Registration for user ${index} returned HTTP ${registered.status}.`);
    }
    if (registered.status === 201) createdUsers += 1;
    await registered.arrayBuffer();
    const loggedIn = await request('/api/auth/login', 'POST', { username, password });
    if (loggedIn.status !== 200) {
      throw new Error(`Login verification for user ${index} returned HTTP ${loggedIn.status}.`);
    }
    const session = await json(loggedIn, 'Login verification');
    if (!session || typeof session.token !== 'string' || session.token.length === 0
        || !session.user || session.user.username !== username) {
      throw new Error(`Login verification for user ${index} returned an unexpected identity or token.`);
    }
    if (index === 1) fixtureToken = session.token;
  }

  const animeName = `${prefix}benchmark`;
  const animeRooms = await list('/api/anime-rooms', fixtureToken);
  let anime = animeRooms.find(room => room.name === animeName);
  if (!anime) {
    const created = await request(`/api/anime-rooms?name=${encodeURIComponent(animeName)}`,
      'POST', undefined, fixtureToken);
    if (created.status !== 200 && created.status !== 201) {
      throw new Error(`Anime fixture creation returned HTTP ${created.status}.`);
    }
    anime = await json(created, 'Anime fixture creation');
  }
  if (!anime || !validId(anime.id) || anime.name !== animeName) {
    throw new Error('Anime fixture returned an unexpected room.');
  }

  const discussions = await list('/api/discussion-rooms', fixtureToken);
  const roomIds = [];
  for (let index = 1; index <= roomCount; index += 1) {
    const title = `benchmark-room-${index}`;
    let room = discussions.find(item => item.title === title && item.animeRoom?.id === anime.id);
    if (!room) {
      const created = await request('/api/discussion-rooms', 'POST', {
        animeRoomId: anime.id, title,
      }, fixtureToken);
      if (created.status !== 200 && created.status !== 201) {
        throw new Error(`Discussion fixture ${index} creation returned HTTP ${created.status}.`);
      }
      room = await json(created, `Discussion fixture ${index} creation`);
    }
    if (!room || !validId(room.id) || room.animeRoom?.id !== anime.id || room.title !== title) {
      throw new Error(`Discussion fixture ${index} returned an unexpected room.`);
    }
    roomIds.push(room.id);
  }

  console.log(`Ready: ${users} verified users (${createdUsers} created, ${users - createdUsers} existing).`);
  console.log(`USER_PREFIX=${prefix}`);
  console.log(`ROOM_IDS=${roomIds.join(',')}`);
}

main().catch(error => {
  console.error(`Fixture setup failed: ${error.message}`);
  process.exitCode = 1;
});
