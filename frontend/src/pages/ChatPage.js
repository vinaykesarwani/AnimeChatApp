import React, { useEffect, useState, useRef, useCallback } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import api from '../services/api';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { useWebSocket } from '../hooks/useWebSocket';

function formatTime(dateStr) {
  if (!dateStr) return '';
  const d = new Date(dateStr);
  return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function formatDate(dateStr) {
  if (!dateStr) return '';
  const d = new Date(dateStr);
  return d.toLocaleDateString([], { weekday: 'long', month: 'short', day: 'numeric' });
}

function isSameDay(a, b) {
  if (!a || !b) return false;
  const da = new Date(a), db = new Date(b);
  return da.getFullYear() === db.getFullYear() && da.getMonth() === db.getMonth() && da.getDate() === db.getDate();
}

export default function ChatPage() {
  const { roomId } = useParams();
  const { user, credentials } = useAuth();
  const { addToast } = useToast();
  const navigate = useNavigate();

  const [roomInfo, setRoomInfo] = useState(null);
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState('');
  const [replyTo, setReplyTo] = useState(null);
  const [editingId, setEditingId] = useState(null);
  const [editContent, setEditContent] = useState('');
  const bottomRef = useRef(null);
  const textareaRef = useRef(null);

  // credentials comes directly from AuthContext where it is set atomically
  // with user — eliminating the render gap that caused "not connected"

  // WS handler
  const handleWsMessage = useCallback((event) => {
    if (event.type === 'CREATE') {
      setMessages(prev => {
        if (prev.find(m => m.id === event.message.id)) return prev;
        return [...prev, event.message];
      });
    } else if (event.type === 'EDIT') {
      setMessages(prev => prev.map(m => m.id === event.message.id ? event.message : m));
    } else if (event.type === 'DELETE') {
      // Backend sends { type: 'DELETE', message: null, messageId: Long }
      // Field is 'messageId' NOT 'deletedMessageId' — mismatch was silently ignoring deletes
      setMessages(prev => prev.filter(m => m.id !== event.messageId));
    }
  }, []);

  const { status, sendMessage, editMessage, deleteMessage } = useWebSocket(
    roomId, credentials, handleWsMessage
  );

  // Load room info & history
  useEffect(() => {
    // Get room info (fetch all, filter)
    api.get('/api/discussion-rooms')
      .then(res => {
        const room = res.data.find(r => String(r.id) === String(roomId));
        if (!room) { navigate('/'); return; }
        setRoomInfo(room);
      })
      .catch(() => navigate('/'));

    // Load message history
    api.get(`/api/messages/discussion/${roomId}`)
      .then(res => setMessages(res.data))
      .catch(() => addToast('Failed to load messages', 'error'));
  }, [roomId]);

  // Auto-scroll on new messages
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSend = () => {
    const content = input.trim();
    if (!content) return;
    const sent = sendMessage(content, replyTo?.id || null);
    if (!sent) {
      addToast('Not connected yet. Please wait...', 'error');
      return;
    }
    setInput('');
    setReplyTo(null);
    textareaRef.current?.focus();
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const handleEditSave = () => {
    if (!editContent.trim()) return;
    editMessage(editingId, editContent.trim());
    setEditingId(null);
    setEditContent('');
  };

  const handleDelete = (msgId) => {
    if (!window.confirm('Delete this message?')) return;
    deleteMessage(msgId);
  };

  const canModify = (msg) => {
    if (!user) return false;
    return user.role === 'ADMIN' || msg.sender?.username === user.username;
  };

  const connStatus = {
    connected: { label: 'CONNECTED — LIVE', cls: 'connected' },
    connecting: { label: 'CONNECTING...', cls: 'connecting' },
    disconnected: { label: 'DISCONNECTED — RECONNECTING', cls: 'disconnected' },
  }[status];

  return (
    <div className="chat-layout">
      <div className="chat-main">
        {/* Header */}
        <div className="chat-header">
          <div className="chat-header-left">
            <button className="btn-icon" onClick={() => roomInfo && navigate(`/anime/${roomInfo.animeRoom?.id}`)}>
              ←
            </button>
            <div>
              <div className="chat-room-name">
                {roomInfo?.title || 'Loading...'}
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginTop: '2px' }}>
                {roomInfo?.animeRoom && (
                  <span className="chat-anime-tag">{roomInfo.animeRoom.name}</span>
                )}
              </div>
            </div>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span className="live-badge">LIVE</span>
            <span style={{ fontFamily: 'var(--font-mono)', fontSize: '0.72rem', color: 'var(--text-muted)' }}>
              #{roomId}
            </span>
          </div>
        </div>

        {/* Connection banner */}
        <div className={`connection-banner ${connStatus.cls}`}>
          <span>◉</span> {connStatus.label}
        </div>

        {/* Messages */}
        <div className="messages-container">
          {messages.length === 0 && (
            <div className="empty-state" style={{ flex: 1 }}>
              <div className="empty-icon">💬</div>
              <div className="empty-title">NO MESSAGES YET</div>
              <div className="empty-sub">Be the first to say something!</div>
            </div>
          )}

          {messages.map((msg, i) => {
            const isOwn = msg.sender?.username === user?.username;
            const showDateDivider = i === 0 || !isSameDay(messages[i - 1]?.createdAt, msg.createdAt);
            const isEditing = editingId === msg.id;
            const initials = (msg.sender?.username || '?').slice(0, 2).toUpperCase();

            return (
              <React.Fragment key={msg.id}>
                {showDateDivider && (
                  <div className="message-date-divider">
                    <span>{formatDate(msg.createdAt)}</span>
                  </div>
                )}
                <div className={`message-row ${isOwn ? 'own' : ''}`} style={{ animationDelay: '0s' }}>
                  <div className={`message-avatar ${isOwn ? 'own' : ''}`}>{initials}</div>
                  <div className="message-content">
                    <div className="message-header">
                      <span className="message-sender">{msg.sender?.username || 'Unknown'}</span>
                      <span className="message-time">{formatTime(msg.createdAt)}</span>
                    </div>

                    {/* Reply preview */}
                    {msg.replyTo && (
                      <div className="reply-preview">
                        ↩ {msg.replyTo.sender?.username}: {msg.replyTo.content?.slice(0, 80)}
                      </div>
                    )}

                    {/* Edit mode */}
                    {isEditing ? (
                      <div style={{ display: 'flex', gap: '6px', alignItems: 'flex-end' }}>
                        <input
                          className="form-input"
                          style={{ fontSize: '0.875rem' }}
                          value={editContent}
                          onChange={e => setEditContent(e.target.value)}
                          onKeyDown={e => {
                            if (e.key === 'Enter') handleEditSave();
                            if (e.key === 'Escape') setEditingId(null);
                          }}
                          autoFocus
                        />
                        <button className="btn btn-primary btn-sm" onClick={handleEditSave}>Save</button>
                        <button className="btn btn-secondary btn-sm" onClick={() => setEditingId(null)}>✕</button>
                      </div>
                    ) : (
                      <div className="message-bubble">
                        {msg.content}
                      </div>
                    )}
                  </div>

                  {/* Hover actions */}
                  {!isEditing && (
                    <div className="message-actions">
                      <button
                        className="msg-action-btn"
                        title="Reply"
                        onClick={() => { setReplyTo(msg); textareaRef.current?.focus(); }}
                      >↩</button>
                      {canModify(msg) && (
                        <>
                          <button
                            className="msg-action-btn"
                            title="Edit"
                            onClick={() => { setEditingId(msg.id); setEditContent(msg.content); }}
                          >✎</button>
                          <button
                            className="msg-action-btn delete"
                            title="Delete"
                            onClick={() => handleDelete(msg.id)}
                          >✕</button>
                        </>
                      )}
                    </div>
                  )}
                </div>
              </React.Fragment>
            );
          })}
          <div ref={bottomRef} />
        </div>

        {/* Input */}
        <div className="chat-input-area">
          {replyTo && (
            <div className="reply-bar">
              <span>↩ Replying to <strong>{replyTo.sender?.username}</strong>: {replyTo.content?.slice(0, 60)}</span>
              <button className="btn-icon" style={{ padding: '2px 6px', fontSize: '0.75rem' }} onClick={() => setReplyTo(null)}>✕</button>
            </div>
          )}
          <div className="chat-input-row">
            <textarea
              ref={textareaRef}
              className="chat-textarea"
              placeholder={`Message ${roomInfo?.title || 'room'}...`}
              value={input}
              onChange={e => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
              rows={1}
              style={{ borderTopLeftRadius: replyTo ? 0 : undefined, borderTopRightRadius: replyTo ? 0 : undefined }}
            />
            <button
              className="send-btn"
              onClick={handleSend}
              disabled={!input.trim() || status !== 'connected'}
              title="Send (Enter)"
            >
              ➤
            </button>
          </div>
          <div style={{ marginTop: '4px', fontFamily: 'var(--font-mono)', fontSize: '0.68rem', color: 'var(--text-muted)' }}>
            Enter to send · Shift+Enter for newline · Hover messages to edit/delete
          </div>
        </div>
      </div>
    </div>
  );
}