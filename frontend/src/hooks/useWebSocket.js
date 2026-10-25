import { useEffect, useRef, useState, useCallback } from 'react';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

const BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080';

export function useWebSocket(roomId, credentials, onMessage, onConnected, onError) {
  const clientRef = useRef(null);
  const callbacks = useRef({});
  callbacks.current = { onMessage, onConnected, onError };
  const [status, setStatus] = useState('disconnected'); // 'connecting' | 'connected' | 'disconnected'

  useEffect(() => {
    if (!roomId || !credentials?.token) {
      setStatus('disconnected');
      return;
    }

    // Mark as connecting immediately — prevents the brief window where the old
    // client's onDisconnect fires 'disconnected' and the user sees "not connected"
    setStatus('connecting');

    // Use a flag to ignore disconnect events from the OLD client during cleanup
    let active = true;

    const client = new Client({
      webSocketFactory: () => new SockJS(`${BASE_URL}/ws`),
      connectHeaders: {
        login: '__jwt__',
        passcode: credentials?.token,
      },
      reconnectDelay: 3000,
      onConnect: () => {
        if (!active) return;
        setStatus('connected');
        client.subscribe(`/topic/chat/${roomId}`, (frame) => {
          if (!active) return;
          try {
            const event = JSON.parse(frame.body);
            callbacks.current.onMessage?.(event);
          } catch (e) {
            callbacks.current.onError?.('Unable to read a chat update');
          }
        });
        client.subscribe('/user/queue/errors', (frame) => {
          if (!active) return;
          try { callbacks.current.onError?.(JSON.parse(frame.body).message); }
          catch { callbacks.current.onError?.('Message operation failed'); }
        });
        callbacks.current.onConnected?.();
      },
      onDisconnect: () => { if (active) setStatus('disconnected'); },
      onWebSocketClose: () => { if (active) setStatus('disconnected'); },
      onWebSocketError: () => { if (active) setStatus('disconnected'); },
      onStompError: () => {
        if (!active) return;
        setStatus('disconnected');
        callbacks.current.onError?.('Chat connection was rejected. Please reconnect or sign in again.');
        client.deactivate();
      },
    });

    client.activate();
    clientRef.current = client;

    return () => {
      active = false; // stop old client from updating status
      client.deactivate();
      if (clientRef.current === client) clientRef.current = null;
    };
  }, [roomId, credentials?.token]);

  const sendMessage = useCallback((content, replyToMessageId = null) => {
    if (!clientRef.current?.connected) return false;
    const payload = { content, replyToMessageId };
    try {
      clientRef.current.publish({
        destination: `/app/chat.send/${roomId}`,
        body: JSON.stringify(payload),
      });
    } catch {
      callbacks.current.onError?.('Connection lost. Please try again.');
      return false;
    }
    return true;
  }, [roomId]);

  const editMessage = useCallback((messageId, content) => {
    if (!clientRef.current?.connected) return false;
    try {
      clientRef.current.publish({
        destination: '/app/chat.edit',
        body: JSON.stringify({ messageId, content }),
      });
    } catch {
      callbacks.current.onError?.('Connection lost. Please try again.');
      return false;
    }
    return true;
  }, []);

  const deleteMessage = useCallback((messageId) => {
    if (!clientRef.current?.connected) return false;
    try {
      clientRef.current.publish({
        destination: '/app/chat.delete',
        body: JSON.stringify({ messageId }),
      });
    } catch {
      callbacks.current.onError?.('Connection lost. Please try again.');
      return false;
    }
    return true;
  }, []);

  return { status, sendMessage, editMessage, deleteMessage };
}