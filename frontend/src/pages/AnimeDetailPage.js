import React, { useEffect, useState, useRef } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import api from '../services/api';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import Modal from '../components/Modal';

// Thresholds — mirror these in your docs if you ever tune them
const THRESHOLD_BLOCK  = 0.90;  // > 0.90  → almost duplicate, block creation
const THRESHOLD_WARN   = 0.75;  // 0.75–0.90 → similar, show warning + list
// < 0.65 → allow immediately

export default function AnimeDetailPage() {
  const { animeId } = useParams();
  const { user } = useAuth();
  const { addToast } = useToast();
  const navigate = useNavigate();

  const [anime, setAnime] = useState(null);
  const [rooms, setRooms] = useState([]);
  const [loading, setLoading] = useState(true);

  // ── Create-room modal ──
  const [showModal, setShowModal] = useState(false);
  const [newTitle, setNewTitle] = useState('');
  const [creating, setCreating] = useState(false);
  const [titleError, setTitleError] = useState('');

  // ── Similarity states ──
  const [checking, setChecking] = useState(false);      // spinner while calling /api/similarity/check
  const [showDuplicateModal, setShowDuplicateModal] = useState(false);
  const [similarRooms, setSimilarRooms] = useState([]);  // { room, score }[]
  const [isBlocked, setIsBlocked] = useState(false);     // score > THRESHOLD_BLOCK

  // Debounce timer ref
  const debounceRef = useRef(null);

  useEffect(() => {
    api.get('/api/anime-rooms').then(res => {
      const found = res.data.find(a => String(a.id) === String(animeId));
      if (!found) { navigate('/'); return; }
      setAnime(found);
    });
    fetchRooms();
  }, [animeId]);

  const fetchRooms = () => {
    api.get('/api/discussion-rooms')
      .then(res => {
        const filtered = res.data.filter(r => String(r.animeRoom?.id) === String(animeId));
        setRooms(filtered);
      })
      .catch(() => addToast('Failed to load discussion rooms', 'error'))
      .finally(() => setLoading(false));
  };

  // ── Live similarity check (debounced 400 ms) ──
  const handleTitleChange = (value) => {
    setNewTitle(value);
    setTitleError('');
    setSimilarRooms([]);
    setIsBlocked(false);

    clearTimeout(debounceRef.current);
    if (!value.trim() || rooms.length === 0) { setChecking(false); return; }

    setChecking(true);
    debounceRef.current = setTimeout(async () => {
      try {
        const res = await api.post('/api/similarity/check', {
          candidate: value.trim(),
          animeRoomId: Number(animeId),
        });
        const results = res.data.results ?? [];  // [{ room, score }]

        // Find blocking room (highest score > BLOCK threshold)
const blockingRoom = results
  .filter(r => r.score > THRESHOLD_BLOCK)
  .sort((a, b) => b.score - a.score)[0];

// Warning rooms (excluding blocking ones)
const warnRooms = results
  .filter(r => r.score >= THRESHOLD_WARN && r.score <= THRESHOLD_BLOCK)
  .sort((a, b) => b.score - a.score);

        if (blockingRoom) {
  setIsBlocked(true);
  setSimilarRooms([blockingRoom]); // 👈 ONLY ONE
} else {
  setIsBlocked(false);
  setSimilarRooms(warnRooms);
}
        
      } catch {
        // Silently degrade — similarity check failing shouldn't block the user
      } finally {
        setChecking(false);
      }
    }, 400);
  };

  // ── Actual room creation (no further checks) ──
  const createRoom = async () => {
    setCreating(true);
    try {
      await api.post('/api/discussion-rooms', {
        animeRoomId: Number(animeId),
        title: newTitle.trim(),
      });
      addToast('Discussion room created!', 'success');
      closeBoth();
      fetchRooms();
    } catch (err) {
      addToast(err.response?.data || 'Failed to create room', 'error');
    } finally {
      setCreating(false);
    }
  };

  // ── "Create" button handler ──
  const handleCreate = async () => {
    setTitleError('');
    if (!newTitle.trim()) { setTitleError('Title is required'); return; }

    // If still checking, wait — shouldn't normally happen but guard it
    if (checking) return;

    // Block: exact/near-duplicate
    if (isBlocked) {
      setTitleError('A room with this title already exists. Please choose a different title.');
      return;
    }

    // Warn: similar rooms exist → show confirmation dialog
    if (similarRooms.length > 0) {
      setShowModal(false);
      setShowDuplicateModal(true);
      return;
    }

    // Clear → create immediately
    await createRoom();
  };

  const closeBoth = () => {
    setShowModal(false);
    setShowDuplicateModal(false);
    setNewTitle('');
    setTitleError('');
    setSimilarRooms([]);
    setIsBlocked(false);
    clearTimeout(debounceRef.current);
  };

  const handleGoBack = () => {
    setShowDuplicateModal(false);
    setShowModal(true);
  };

  // ── Helpers ──
  const ROOM_EMOJIS = ['💬','🔥','⚡','🎯','📖','🗡️','🌊','🤔','👥','🏆'];
  const getRoomEmoji = (title) => {
    let hash = 0;
    for (let c of title) hash += c.charCodeAt(0);
    return ROOM_EMOJIS[hash % ROOM_EMOJIS.length];
  };

  const scoreLabel = (score) => {
    if (score > THRESHOLD_BLOCK) return { label: 'Near duplicate', color: 'var(--accent)' };
    if (score >= THRESHOLD_WARN) return { label: 'Similar', color: 'var(--gold)' };
    return { label: 'Low match', color: 'var(--text-muted)' };
  };

  const scoreBarColor = (score) => {
    if (score > THRESHOLD_BLOCK) return 'var(--accent)';
    if (score >= THRESHOLD_WARN) return 'var(--gold)';
    return 'var(--accent-blue)';
  };

  return (
    <div className="page">
      {/* Top stripe */}
      <div style={{ background: 'var(--bg-secondary)', borderBottom: '1px solid var(--border)' }}>
        <div className="container" style={{ padding: '1.5rem 1.5rem 0' }}>
          <div className="breadcrumb">
            <Link to="/">Home</Link>
            <span className="breadcrumb-sep">/</span>
            <span className="breadcrumb-current">{anime?.name || '...'}</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', paddingBottom: '1.5rem', flexWrap: 'wrap', gap: '1rem' }}>
            <div>
              <div style={{ fontFamily: 'var(--font-mono)', fontSize: '0.7rem', color: 'var(--accent)', letterSpacing: '2px', marginBottom: '6px' }}>
                // ANIME COMMUNITY
              </div>
              <h1 style={{ fontFamily: 'var(--font-display)', fontSize: 'clamp(2rem, 4vw, 3rem)', letterSpacing: '3px', lineHeight: 1 }}>
                {anime?.name || <span style={{ opacity: 0.4 }}>Loading...</span>}
              </h1>
              {anime?.createdBy && (
                <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)', marginTop: '6px', fontFamily: 'var(--font-mono)' }}>
                  Created by {anime.createdBy.username}
                </div>
              )}
            </div>
            <button className="btn btn-primary" onClick={() => setShowModal(true)}>
              <span>＋</span> New Room
            </button>
          </div>
        </div>
      </div>

      {/* Room list */}
      <div className="container" style={{ padding: '2rem 1.5rem', background: 'var(--bg-primary)', minHeight: '100%' }}>
        <div className="section-header">
          <h2 className="section-title">
            Discussion Rooms <span className="section-count">{rooms.length}</span>
          </h2>
          <span className="live-badge">LIVE</span>
        </div>

        {loading ? (
          <div className="loading-screen">
            <div className="spinner" />
            <span className="loading-text">LOADING ROOMS...</span>
          </div>
        ) : rooms.length === 0 ? (
          <div className="empty-state">
            <div className="empty-icon">💬</div>
            <div className="empty-title">NO DISCUSSION ROOMS</div>
            <div className="empty-sub">Start the conversation by creating the first room</div>
            <button className="btn btn-primary" style={{ marginTop: '1rem' }} onClick={() => setShowModal(true)}>
              Create First Room
            </button>
          </div>
        ) : (
          <div className="discussions-list">
            {rooms.map((room, i) => (
              <div
                key={room.id}
                className="discussion-card"
                style={{ animationDelay: `${i * 0.06}s`, cursor: 'pointer' }}
                onClick={() => navigate(`/chat/${room.id}`)}
              >
                <div className="discussion-card-left">
                  <div className="discussion-card-icon">{getRoomEmoji(room.title)}</div>
                  <div>
                    <div className="discussion-card-title">{room.title}</div>
                    <div className="discussion-card-sub">
                      {room.createdBy ? `by ${room.createdBy.username}` : 'Community Room'} · #{room.id}
                    </div>
                  </div>
                </div>
                <div className="discussion-card-right">
                  <span className="live-badge">LIVE</span>
                  <span style={{ color: 'var(--text-muted)', fontSize: '1.1rem' }}>›</span>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* ── New Room Modal ── */}
      {showModal && (
        <Modal
          title="NEW DISCUSSION ROOM"
          onClose={closeBoth}
          footer={
            <>
              <button className="btn btn-secondary" onClick={closeBoth}>Cancel</button>
              <button
                className="btn btn-primary"
                onClick={handleCreate}
                disabled={creating || checking || !newTitle.trim() || isBlocked}
              >
                {creating || checking
                  ? <span className="spinner" style={{ width: 14, height: 14 }} />
                  : '＋ Create'}
              </button>
            </>
          }
        >
          <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
            Create a new discussion room for{' '}
            <strong style={{ color: 'var(--text-primary)' }}>{anime?.name}</strong>
          </p>

          <div className="form-group">
            <label className="form-label">Room Title</label>

            {/* Input + spinner wrapper */}
            <div style={{ position: 'relative' }}>
              <input
                className="form-input"
                style={{ paddingRight: checking ? '2.5rem' : undefined }}
                placeholder="e.g. Best moments, Episode 1 reaction..."
                value={newTitle}
                onChange={e => handleTitleChange(e.target.value)}
                onKeyDown={e => e.key === 'Enter' && handleCreate()}
                autoFocus
              />
              {checking && (
                <span style={{
                  position: 'absolute', right: '0.75rem', top: '50%', transform: 'translateY(-50%)',
                }}>
                  <span className="spinner" style={{ width: 14, height: 14, opacity: 0.6 }} />
                </span>
              )}
            </div>

            {titleError && <span className="form-error">{titleError}</span>}

            {/* Inline live feedback — blocked */}
            {!checking && isBlocked && !titleError && (
              <div style={{
                display: 'flex', alignItems: 'center', gap: '0.5rem',
                marginTop: '0.5rem', padding: '0.6rem 0.8rem',
                background: 'rgba(230,57,70,0.08)', border: '1px solid var(--border-accent)',
                borderRadius: 'var(--radius-sm)', fontSize: '0.8rem', color: 'var(--accent)',
              }}>
                <span>🚫</span>
                <span>A near-identical room already exists. Please choose a different title.</span>
              </div>
            )}

            {/* Inline live feedback — similar (warn) */}
            {!checking && !isBlocked && similarRooms.length > 0 && (
              <div style={{
                marginTop: '0.5rem', padding: '0.6rem 0.8rem',
                background: 'rgba(255,214,10,0.07)', border: '1px solid rgba(255,214,10,0.25)',
                borderRadius: 'var(--radius-sm)', fontSize: '0.8rem', color: 'var(--gold)',
                display: 'flex', alignItems: 'center', gap: '0.5rem',
              }}>
                <span>⚠️</span>
                <span>{similarRooms.length} similar room{similarRooms.length > 1 ? 's' : ''} found — review before creating.</span>
              </div>
            )}

            {/* Inline similar rooms preview */}
{!checking && similarRooms.length > 0 && (
  <div style={{
    marginTop: '0.6rem',
    display: 'flex',
    flexDirection: 'column',
    gap: '0.4rem'
  }}>
    {similarRooms.slice(0, 3).map(({ room, score }) => (
      <div
        key={room.id}
        onClick={() => {
          closeBoth();
          navigate(`/chat/${room.id}`);
        }}
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: '0.6rem',
          padding: '0.5rem 0.7rem',
          background: 'var(--bg-input)',
          border: '1px solid var(--border)',
          borderRadius: 'var(--radius-sm)',
          cursor: 'pointer'
        }}
      >
        <span>{getRoomEmoji(room.title)}</span>

        <div style={{ flex: 1 }}>
          <div style={{
            fontSize: '0.8rem',
            fontWeight: 500,
            color: 'var(--text-primary)'
          }}>
            {room.title}
          </div>

          <div style={{
            fontSize: '0.7rem',
            color: 'var(--text-muted)'
          }}>
            {/* {Math.round(score * 100)}% match */}
          </div>
        </div>

        <span style={{ fontSize: '0.7rem', color: 'var(--accent)' }}>
          JOIN →
        </span>
      </div>
    ))}
  </div>
)}

            {/* Hint */}
            {!checking && !isBlocked && similarRooms.length === 0 && newTitle.trim() && (
              <div style={{ marginTop: '0.4rem', fontSize: '0.75rem', color: 'var(--text-muted)', fontFamily: 'var(--font-mono)' }}>
                ✓ No similar rooms found
              </div>
            )}
          </div>
        </Modal>
      )}

      {/* ── Similar Rooms Confirmation Modal ── */}
      {showDuplicateModal && (
        <Modal
          title="SIMILAR ROOMS FOUND"
          onClose={closeBoth}
          footer={
            <>
              <button className="btn btn-secondary" onClick={handleGoBack}>← Go Back</button>
              <button className="btn btn-primary" onClick={createRoom} disabled={creating}>
                {creating
                  ? <span className="spinner" style={{ width: 14, height: 14 }} />
                  : '＋ Create Anyway'}
              </button>
            </>
          }
        >
          {/* Warning banner */}
          <div style={{
            display: 'flex', alignItems: 'flex-start', gap: '0.75rem',
            padding: '0.85rem 1rem',
            background: 'rgba(255,214,10,0.07)', border: '1px solid rgba(255,214,10,0.25)',
            borderRadius: 'var(--radius-sm)',
          }}>
            <span style={{ fontSize: '1.2rem', lineHeight: 1.3 }}>⚠️</span>
            <p style={{ fontSize: '0.83rem', color: 'var(--text-primary)', lineHeight: 1.5 }}>
              Rooms similar to{' '}
              <strong style={{ color: 'var(--gold)' }}>"{newTitle}"</strong>{' '}
              already exist. You can join one of them or create your room anyway.
            </p>
          </div>

          {/* Similar rooms list */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            <p style={{ fontSize: '0.72rem', color: 'var(--text-muted)', fontFamily: 'var(--font-mono)', letterSpacing: '1px' }}>
              // SIMILAR ROOMS ({similarRooms.length})
            </p>

            {similarRooms.map(({ room, score }) => {
              const { label, color } = scoreLabel(score);
              return (
                <div
                  key={room.id}
                  onClick={() => { closeBoth(); navigate(`/chat/${room.id}`); }}
                  style={{
                    display: 'flex', alignItems: 'center', gap: '0.75rem',
                    padding: '0.75rem 0.9rem',
                    background: 'var(--bg-input)', border: '1px solid var(--border)',
                    borderRadius: 'var(--radius-sm)', cursor: 'pointer',
                    transition: 'border-color 0.15s, background 0.15s',
                  }}
                  onMouseEnter={e => {
                    e.currentTarget.style.borderColor = 'var(--border-accent)';
                    e.currentTarget.style.background = 'var(--bg-card-hover)';
                  }}
                  onMouseLeave={e => {
                    e.currentTarget.style.borderColor = 'var(--border)';
                    e.currentTarget.style.background = 'var(--bg-input)';
                  }}
                >
                  <span style={{ fontSize: '1.1rem' }}>{getRoomEmoji(room.title)}</span>

                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{
                      fontSize: '0.88rem', fontWeight: 600, color: 'var(--text-primary)',
                      whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
                    }}>
                      {room.title}
                    </div>
                    <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)', fontFamily: 'var(--font-mono)', marginTop: '2px' }}>
                      {room.createdBy ? `by ${room.createdBy.username}` : 'Community Room'} · #{room.id}
                    </div>

                    {/* Score bar */}
                    <div style={{ marginTop: '6px', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                      <div style={{
                        flex: 1, height: '3px', borderRadius: '99px',
                        background: 'var(--border)',
                        maxWidth: '120px',
                      }}>
                        <div style={{
                          width: `${Math.round(score * 100)}%`,
                          height: '100%', borderRadius: '99px',
                          background: scoreBarColor(score),
                          transition: 'width 0.3s ease',
                        }} />
                      </div>
                      <span style={{ fontSize: '0.68rem', fontFamily: 'var(--font-mono)', color }}>
                        {Math.round(score * 100)}% · {label}
                      </span>
                    </div>
                  </div>

                  <span style={{ fontSize: '0.72rem', color: 'var(--accent)', fontFamily: 'var(--font-mono)', whiteSpace: 'nowrap' }}>
                    JOIN →
                  </span>
                </div>
              );
            })}
          </div>
        </Modal>
      )}
    </div>
  );
}
