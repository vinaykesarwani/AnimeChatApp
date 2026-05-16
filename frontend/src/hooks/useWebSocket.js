import { useEffect, useRef, useState, useCallback } from 'react';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

const BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080';

/**
 * useWebSocket now accepts `token` (a JWT string) instead of `credentials`
 * (username + password). The token is sent as the STOMP passcode header and
 * validated by WebSocketAuthChannelInterceptor on the backend.
 *
 * Usage: const ws = useWebSocket(roomId, token, onMessage);
 */
export function useWebSocket(roomId, token, onMessage) {
  const clientRef = useRef(null);
  const [status, setStatus] = useState('disconnected');

  useEffect(() => {
    if (!roomId || !token) return;

    setStatus('connecting');
    let active = true;

    const client = new Client({
      webSocketFactory: () => new SockJS(`${BASE_URL}/ws`),
      connectHeaders: {
        // Send the JWT as the passcode. The backend interceptor will verify it.
        // We use a fixed "jwt" login so the interceptor knows which auth mode to use.
        login: 'jwt',
        passcode: token,
      },
      reconnectDelay: 3000,
      onConnect: () => {
        if (!active) return;
        setStatus('connected');
        client.subscribe(`/topic/chat/${roomId}`, (frame) => {
          try {
            const event = JSON.parse(frame.body);
            onMessage(event);
          } catch (e) {
            console.error('WS parse error', e);
          }
        });
      },
      onDisconnect: () => { if (active) setStatus('disconnected'); },
      onStompError:  () => { if (active) setStatus('disconnected'); },
    });

    client.activate();
    clientRef.current = client;

    return () => {
      active = false;
      client.deactivate();
    };
  }, [roomId, token]);

  const sendMessage = useCallback((content, replyToMessageId = null) => {
    if (!clientRef.current?.connected) return false;
    clientRef.current.publish({
      destination: `/app/chat.send/${roomId}`,
      body: JSON.stringify({ content, replyToMessageId }),
    });
    return true;
  }, [roomId]);

  const editMessage = useCallback((messageId, content) => {
    if (!clientRef.current?.connected) return;
    clientRef.current.publish({
      destination: '/app/chat.edit',
      body: JSON.stringify({ messageId, content }),
    });
  }, []);

  const deleteMessage = useCallback((messageId) => {
    if (!clientRef.current?.connected) return;
    clientRef.current.publish({
      destination: '/app/chat.delete',
      body: JSON.stringify({ messageId }),
    });
  }, []);

  return { status, sendMessage, editMessage, deleteMessage };
}
