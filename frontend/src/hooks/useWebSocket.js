import { useEffect, useRef, useState, useCallback } from 'react';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

const BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080';

export function useWebSocket(roomId, credentials, onMessage) {
  const clientRef = useRef(null);
  const [status, setStatus] = useState('disconnected'); // 'connecting' | 'connected' | 'disconnected'

  useEffect(() => {
    if (!roomId || !credentials?.username || !credentials?.password) return;

    // Mark as connecting immediately — prevents the brief window where the old
    // client's onDisconnect fires 'disconnected' and the user sees "not connected"
    setStatus('connecting');

    // Use a flag to ignore disconnect events from the OLD client during cleanup
    let active = true;

    const client = new Client({
      webSocketFactory: () => new SockJS(`${BASE_URL}/ws`),
      connectHeaders: {
        login: credentials.username,
        passcode: credentials.password,
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
      onStompError: () => { if (active) setStatus('disconnected'); },
    });

    client.activate();
    clientRef.current = client;

    return () => {
      active = false; // stop old client from updating status
      client.deactivate();
      // Don't null clientRef here — nulling it creates a gap between old client
      // teardown and new client assignment where sendMessage always returns false
    };
  }, [roomId, credentials?.username, credentials?.password]);

  const sendMessage = useCallback((content, replyToMessageId = null) => {
    if (!clientRef.current?.connected) return false;
    const payload = { content, replyToMessageId };
    clientRef.current.publish({
      destination: `/app/chat.send/${roomId}`,
      body: JSON.stringify(payload),
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