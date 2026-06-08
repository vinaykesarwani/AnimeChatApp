import React, { useEffect, useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import api from '../services/api';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import Modal from '../components/Modal';

export default function AnimeDetailPage() {
  const { animeId } = useParams();
  const { user } = useAuth();
  const { addToast } = useToast();
  const navigate = useNavigate();

  const [anime, setAnime] = useState(null);
  const [rooms, setRooms] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [newTitle, setNewTitle] = useState('');
  const [creating, setCreating] = useState(false);
  const [titleError, setTitleError] = useState('');

  useEffect(() => {
    // Fetch all anime then filter, since there's no GET /api/anime-rooms/:id
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

  const handleCreate = async () => {
    setTitleError('');
    if (!newTitle.trim()) { setTitleError('Title is required'); return; }
    setCreating(true);
    try {
      await api.post('/api/discussion-rooms', {
        animeRoomId: Number(animeId),
        title: newTitle.trim(),
      });
      addToast('Discussion room created!', 'success');
      setShowModal(false);
      setNewTitle('');
      fetchRooms();
    } catch (err) {
      addToast(err.response?.data || 'Failed to create room', 'error');
    } finally {
      setCreating(false);
    }
  };

  const ROOM_EMOJIS = ['💬','🔥','⚡','🎯','📖','🗡️','🌊','🤔','👥','🏆'];
  const getRoomEmoji = (title) => {
    let hash = 0;
    for (let c of title) hash += c.charCodeAt(0);
    return ROOM_EMOJIS[hash % ROOM_EMOJIS.length];
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
              <h1 style={{
                fontFamily: 'var(--font-display)',
                fontSize: 'clamp(2rem, 4vw, 3rem)',
                letterSpacing: '3px',
                lineHeight: 1
              }}>
                {anime?.name || (
                  <span style={{ opacity: 0.4 }}>Loading...</span>
                )}
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

      <div className="container" style={{ padding: '2rem 1.5rem', background: 'var(--bg-primary)', minHeight: '100%' }}>
        <div className="section-header">
          <h2 className="section-title">
            Discussion Rooms
            <span className="section-count">{rooms.length}</span>
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
                  <div className="discussion-card-icon">
                    {getRoomEmoji(room.title)}
                  </div>
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

      {showModal && (
        <Modal
          title="NEW DISCUSSION ROOM"
          onClose={() => { setShowModal(false); setNewTitle(''); setTitleError(''); }}
          footer={
            <>
              <button className="btn btn-secondary" onClick={() => { setShowModal(false); setNewTitle(''); setTitleError(''); }}>
                Cancel
              </button>
              <button className="btn btn-primary" onClick={handleCreate} disabled={creating || !newTitle.trim()}>
                {creating ? <span className="spinner" style={{ width: 14, height: 14 }} /> : '＋ Create'}
              </button>
            </>
          }
        >
          <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
            Create a new discussion room for <strong style={{ color: 'var(--text-primary)' }}>{anime?.name}</strong>
          </p>
          <div className="form-group">
            <label className="form-label">Room Title</label>
            <input
              className="form-input"
              placeholder="e.g. Best moments, Episode 1 reaction..."
              value={newTitle}
              onChange={e => { setNewTitle(e.target.value); setTitleError(''); }}
              onKeyDown={e => e.key === 'Enter' && handleCreate()}
              autoFocus
            />
            {titleError && <span className="form-error">{titleError}</span>}
          </div>
        </Modal>
      )}
    </div>
  );
}