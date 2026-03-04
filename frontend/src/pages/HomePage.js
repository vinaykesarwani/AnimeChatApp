import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import Modal from '../components/Modal';

// Curated list of popular anime for the dropdown
const ANIME_OPTIONS = [
  'Attack on Titan', 'Demon Slayer', 'Jujutsu Kaisen', 'One Piece',
  'Naruto', 'Dragon Ball Z', 'Bleach', 'My Hero Academia',
  'Fullmetal Alchemist: Brotherhood', 'Death Note', 'Hunter x Hunter',
  'Sword Art Online', 'Re:Zero', 'Tokyo Revengers', 'Chainsaw Man',
  'Spy x Family', 'Vinland Saga', 'Mushoku Tensei', 'Black Clover',
  'Overlord', 'That Time I Got Reincarnated as a Slime', 'Fairy Tail',
  'Steins;Gate', 'Code Geass', 'Cowboy Bebop', 'Neon Genesis Evangelion',
  'Mob Psycho 100', 'One Punch Man', 'No Game No Life', 'Violet Evergarden',
];

const ANIME_EMOJIS = ['⚔️','🔥','👺','🌊','🍃','⚡','🌸','🏯','💀','🎭','🧿','🐉','🌀','👁️','💥'];

function getEmoji(name) {
  let hash = 0;
  for (let c of name) hash += c.charCodeAt(0);
  return ANIME_EMOJIS[hash % ANIME_EMOJIS.length];
}

export default function HomePage() {
  const { user } = useAuth();
  const { addToast } = useToast();
  const navigate = useNavigate();

  const [animeList, setAnimeList] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [selectedAnime, setSelectedAnime] = useState('');
  const [creating, setCreating] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');

  const fetchAnime = () => {
    api.get('/api/anime-rooms')
      .then(res => setAnimeList(res.data))
      .catch(() => addToast('Failed to load anime list', 'error'))
      .finally(() => setLoading(false));
  };

  useEffect(() => { fetchAnime(); }, []);

  const handleCardClick = (anime) => {
    if (!user) {
      addToast('Sign in to explore discussion rooms', 'info');
      navigate('/login');
      return;
    }
    navigate(`/anime/${anime.id}`);
  };

  const handleCreate = async () => {
    if (!selectedAnime) return;
    if (!user) { navigate('/login'); return; }
    setCreating(true);
    try {
      await api.post(`/api/anime-rooms?name=${encodeURIComponent(selectedAnime)}`);
      addToast(`${selectedAnime} added!`, 'success');
      setShowModal(false);
      setSelectedAnime('');
      fetchAnime();
    } catch (err) {
      addToast(err.response?.data || 'Anime already exists or failed to create', 'error');
    } finally {
      setCreating(false);
    }
  };

  const filtered = animeList.filter(a =>
    a.name.toLowerCase().includes(searchTerm.toLowerCase())
  );

  // Filter out already-added anime from dropdown
  const existingNames = animeList.map(a => a.name.toLowerCase());
  const availableOptions = ANIME_OPTIONS.filter(
    o => !existingNames.includes(o.toLowerCase())
  );

  return (
    <div className="page">
      {/* Hero section */}
      <div style={{ position: 'relative', borderBottom: '1px solid var(--border)', overflow: 'hidden' }}>
        <div className="hero-scanline" />
        <div style={{
          position: 'absolute', inset: 0,
          background: 'radial-gradient(ellipse at 20% 50%, rgba(230,57,70,0.08) 0%, transparent 60%), radial-gradient(ellipse at 80% 50%, rgba(123,47,255,0.06) 0%, transparent 60%)',
          pointerEvents: 'none'
        }} />
        <div className="container">
          <div className="page-header">
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '0.75rem' }}>
              <span style={{ fontFamily: 'var(--font-mono)', fontSize: '0.75rem', color: 'var(--accent)', letterSpacing: '2px' }}>
                // COMMUNITY FORUMS
              </span>
            </div>
            <h1 className="page-title">
              ANIME<br /><span>DISCUSSION</span>
            </h1>
            <p className="page-subtitle">
              {user
                ? `Welcome back, ${user.username}. Pick an anime to discuss.`
                : 'Browse communities. Sign in to join discussions.'}
            </p>
          </div>
        </div>
      </div>

      <div className="container" style={{ padding: '2rem 1.5rem' }}>
        {/* Controls row */}
        <div style={{ display: 'flex', gap: '12px', marginBottom: '1.5rem', flexWrap: 'wrap', alignItems: 'center' }}>
          <input
            className="form-input"
            style={{ maxWidth: '280px', flex: '1 1 200px' }}
            placeholder="Search anime..."
            value={searchTerm}
            onChange={e => setSearchTerm(e.target.value)}
          />
          <div style={{ flex: 1 }} />
          {user && (
            <button className="btn btn-primary" onClick={() => setShowModal(true)}>
              <span>＋</span> Add Anime
            </button>
          )}
          {!user && (
            <button className="btn btn-secondary" onClick={() => navigate('/login')}>
              Sign In to Add Anime
            </button>
          )}
        </div>

        {/* Section header */}
        <div className="section-header">
          <h2 className="section-title">
            All Communities
            <span className="section-count">{filtered.length}</span>
          </h2>
          {!user && (
            <span style={{ fontSize: '0.78rem', color: 'var(--text-muted)', fontFamily: 'var(--font-mono)' }}>
              👁 Browse mode
            </span>
          )}
        </div>

        {/* Grid */}
        {loading ? (
          <div className="loading-screen">
            <div className="spinner" />
            <span className="loading-text">LOADING COMMUNITIES...</span>
          </div>
        ) : filtered.length === 0 ? (
          <div className="empty-state">
            <div className="empty-icon">📺</div>
            <div className="empty-title">NO ANIME FOUND</div>
            <div className="empty-sub">
              {searchTerm ? 'Try a different search' : 'Be the first to add an anime community'}
            </div>
            {user && !searchTerm && (
              <button className="btn btn-primary" style={{ marginTop: '1rem' }} onClick={() => setShowModal(true)}>
                Add First Anime
              </button>
            )}
          </div>
        ) : (
          <div className="anime-grid">
            {filtered.map((anime, i) => (
              <div
                key={anime.id}
                className="anime-card"
                style={{ animationDelay: `${i * 0.05}s` }}
                onClick={() => handleCardClick(anime)}
              >
                <div className="anime-card-corner" />
                <div className="anime-card-icon">
                  {getEmoji(anime.name)}
                </div>
                <div className="anime-card-name">{anime.name}</div>
                <div className="anime-card-meta">
                  {anime.createdBy ? `by ${anime.createdBy.username}` : 'Community'}
                </div>
                {!user && (
                  <div style={{ marginTop: '8px' }}>
                    <span className="tag tag-default">🔒 Sign in</span>
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Add Anime Modal */}
      {showModal && (
        <Modal
          title="ADD ANIME"
          onClose={() => { setShowModal(false); setSelectedAnime(''); }}
          footer={
            <>
              <button className="btn btn-secondary" onClick={() => { setShowModal(false); setSelectedAnime(''); }}>
                Cancel
              </button>
              <button
                className="btn btn-primary"
                onClick={handleCreate}
                disabled={!selectedAnime || creating}
              >
                {creating ? <span className="spinner" style={{ width: 14, height: 14 }} /> : '＋ Add'}
              </button>
            </>
          }
        >
          <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: '0.5rem' }}>
            Choose an anime to create a new community
          </p>
          <div className="form-group">
            <label className="form-label">Select Anime</label>
            <select
              className="form-select"
              value={selectedAnime}
              onChange={e => setSelectedAnime(e.target.value)}
            >
              <option value="">— Choose an anime —</option>
              {availableOptions.map(opt => (
                <option key={opt} value={opt}>{opt}</option>
              ))}
              {availableOptions.length === 0 && (
                <option disabled>All popular anime already added!</option>
              )}
            </select>
          </div>
          {selectedAnime && (
            <div style={{
              padding: '10px 14px',
              background: 'rgba(230,57,70,0.08)',
              border: '1px solid var(--border-accent)',
              borderRadius: 'var(--radius-sm)',
              fontSize: '0.85rem',
              display: 'flex', alignItems: 'center', gap: '8px'
            }}>
              <span style={{ fontSize: '1.2rem' }}>{getEmoji(selectedAnime)}</span>
              <span>{selectedAnime}</span>
            </div>
          )}
        </Modal>
      )}
    </div>
  );
}
