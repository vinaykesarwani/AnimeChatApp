import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const THEMES = [
  { id: 'void',   label: 'VOID',   title: 'Void — The Abyss (Dark)',        cls: 'theme-btn-void'   },
  { id: 'sakura', label: 'SAKURA', title: 'Sakura — Twilight Blossom (Mid)', cls: 'theme-btn-sakura' },
  { id: 'solar',  label: 'SOLAR',  title: "Solar — Hero's Dawn (Light)",     cls: 'theme-btn-solar'  },
];

export default function Navbar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const [theme, setTheme] = useState(() => localStorage.getItem('animechat_theme') || 'void');

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme);
    localStorage.setItem('animechat_theme', theme);
  }, [theme]);

  const handleLogout = () => {
    logout();
    navigate('/');
  };

  const initials = user?.username?.slice(0, 2).toUpperCase() || '??';

  return (
    <nav className="navbar">
      <Link to="/" className="navbar-logo">
        <div className="logo-dot" />
        ANIME<span>CHAT</span>
      </Link>

      <div className="navbar-actions">
        <div className="theme-switcher" title="Choose your anime world">
          {THEMES.map(t => (
            <button
              key={t.id}
              className={"theme-btn " + t.cls + (theme === t.id ? ' active' : '')}
              onClick={() => setTheme(t.id)}
              title={t.title}
              aria-label={t.title}
            />
          ))}
        </div>

        {user ? (
          <>
            <div className="nav-user">
              <div className="nav-avatar">{initials}</div>
              <span>{user.username}</span>
              {user.role === 'ADMIN' && (
                <span className="tag tag-default">ADMIN</span>
              )}
            </div>
            <button className="btn btn-secondary btn-sm" onClick={handleLogout}>
              Sign Out
            </button>
          </>
        ) : (
          <>
            <Link to="/login" className="btn btn-ghost btn-sm">Sign In</Link>
            <Link to="/register" className="btn btn-primary btn-sm">Join Now</Link>
          </>
        )}
      </div>
    </nav>
  );
}