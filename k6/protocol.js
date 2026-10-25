// The frontend uses SockJS. Its WebSocket transport wraps STOMP in JSON arrays.
export function frame(command, headers = {}, body = '') {
  const lines = Object.entries(headers).map(([name, value]) => `${name}:${value}`);
  return [command, ...lines, '', body].join('\n') + '\0';
}

export function sockjsUrl(baseUrl, sessionId) {
  return baseUrl.replace(/^http/, 'ws') + `/ws/000/${sessionId}/websocket`;
}

export function createDecoder() {
  let buffer = '';
  return function decode(packet) {
    if (packet === 'o') return [{ command: 'SOCKJS_OPEN' }];
    if (packet === 'h') return [];
    if (packet.startsWith('c')) return [{ command: 'SOCKJS_CLOSE' }];
    if (!packet.startsWith('a')) throw new Error('Unexpected SockJS packet');
    const chunks = JSON.parse(packet.slice(1));
    if (!Array.isArray(chunks) || chunks.some(chunk => typeof chunk !== 'string')) {
      throw new Error('Invalid SockJS data');
    }
    const frames = [];
    for (const chunk of chunks) {
      buffer += chunk;
      // JSON message bodies cannot contain a literal NUL, so it delimits our frames.
      let end;
      while ((end = buffer.indexOf('\0')) >= 0) {
        const raw = buffer.slice(0, end).replace(/^[\r\n]+/, '');
        buffer = buffer.slice(end + 1);
        const separator = raw.search(/\r?\n\r?\n/);
        if (separator < 0) throw new Error('Invalid STOMP frame');
        const header = raw.slice(0, separator).split(/\r?\n/);
        const delimiter = raw.slice(separator).match(/^\r?\n\r?\n/)[0];
        const headers = {};
        for (const line of header.slice(1)) {
          const colon = line.indexOf(':');
          if (colon < 1) throw new Error('Invalid STOMP header');
          headers[line.slice(0, colon)] = line.slice(colon + 1);
        }
        frames.push({ command: header[0], headers, body: raw.slice(separator + delimiter.length) });
      }
      buffer = buffer.replace(/^[\r\n]+/, ''); // STOMP heartbeats
      if (buffer.length > 1024 * 1024) throw new Error('Incomplete STOMP frame exceeds 1 MiB');
    }
    return frames;
  };
}
