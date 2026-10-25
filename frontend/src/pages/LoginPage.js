// src/pages/LoginPage.js
import React, { useState } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';

// Simple inline SVG icons (no extra dependencies)
const GoogleIcon = () => (
  <svg width="18" height="18" viewBox="0 0 18 18" fill="none" xmlns="http://www.w3.org/2000/svg">
    <path d="M17.64 9.2c0-.637-.057-1.251-.164-1.84H9v3.481h4.844c-.209 1.125-.843 2.078-1.796 2.717v2.258h2.908c1.702-1.567 2.684-3.875 2.684-6.615z" fill="#4285F4"/>
    <path d="M9 18c2.43 0 4.467-.806 5.956-2.184l-2.908-2.258c-.806.54-1.837.86-3.048.86-2.344 0-4.328-1.584-5.036-3.711H.957v2.332A8.997 8.997 0 0 0 9 18z" fill="#34A853"/>
    <path d="M3.964 10.707A5.41 5.41 0 0 1 3.682 9c0-.593.102-1.17.282-1.707V4.961H.957A8.996 8.996 0 0 0 0 9c0 1.452.348 2.827.957 4.039l3.007-2.332z" fill="#FBBC05"/>
    <path d="M9 3.58c1.321 0 2.508.454 3.44 1.345l2.582-2.58C13.463.891 11.426 0 9 0A8.997 8.997 0 0 0 .957 4.961L3.964 6.293C4.672 4.166 6.656 3.58 9 3.58z" fill="#EA4335"/>
  </svg>
);

const GitHubIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" xmlns="http://www.w3.org/2000/svg">
    <path d="M12 0C5.37 0 0 5.37 0 12c0 5.303 3.438 9.8 8.205 11.387.6.113.82-.258.82-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23A11.509 11.509 0 0 1 12 5.803c1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576C20.566 21.797 24 17.3 24 12c0-6.63-5.37-12-12-12z"/>
  </svg>
);

export default function LoginPage() {
  const { login, loginWithOAuth } = useAuth();
  const { addToast } = useToast();
  const navigate = useNavigate();
  const location = useLocation();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading]   = useState(false);
  const [error, setError]       = useState('');

  const from = location.state?.from?.pathname || '/';

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    if (!username.trim() || !password) { setError('Please fill in all fields'); return; }
    setLoading(true);
    const result = await login(username.trim(), password);
    setLoading(false);
    if (result.success) {
      addToast(`Welcome back, ${username}!`, 'success');
      navigate(from, { replace: true });
    } else {
      setError(result.error);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-card">
        <h1 className="auth-title">SIGN<span> IN</span></h1>
        <p className="auth-subtitle">Enter your credentials to access discussions</p>

        {/* ── OAuth buttons ── */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginBottom: 20 }}>
          <button
            className="btn btn-secondary"
            style={{ width: '100%', justifyContent: 'center', gap: 10 }}
            onClick={() => loginWithOAuth('google')}
          >
            <GoogleIcon /> Continue with Google
          </button>
          <button
            className="btn btn-secondary"
            style={{ width: '100%', justifyContent: 'center', gap: 10 }}
            onClick={() => loginWithOAuth('github')}
          >
            <GitHubIcon /> Continue with GitHub
          </button>
        </div>

        <div style={{
          display: 'flex', alignItems: 'center', gap: 10, marginBottom: 20,
          color: 'var(--text-muted)', fontSize: '0.8rem',
        }}>
          <hr style={{ flex: 1, borderColor: 'var(--border)' }} />
          OR
          <hr style={{ flex: 1, borderColor: 'var(--border)' }} />
        </div>

        {/* ── Existing username/password form (unchanged) ── */}
        <form className="auth-form" onSubmit={handleSubmit}>
          <div className="form-group">
            <label className="form-label">Username</label>
            <input
              className="form-input" type="text" placeholder="your_username"
              value={username} onChange={e => setUsername(e.target.value)}
              autoComplete="username" autoFocus
            />
          </div>
          <div className="form-group">
            <label className="form-label">Password</label>
            <input
              className="form-input" type="password" placeholder="••••••••"
              value={password} onChange={e => setPassword(e.target.value)}
              autoComplete="current-password"
            />
          </div>

          {error && (
            <div style={{
              padding: '10px 14px',
              background: 'rgba(230,57,70,0.1)',
              border: '1px solid var(--border-accent)',
              borderRadius: 'var(--radius-sm)',
              color: 'var(--accent-2)',
              fontSize: '0.85rem',
            }}>
              ✕ {error}
            </div>
          )}

          <button
            type="submit" className="btn btn-primary"
            style={{ width: '100%', justifyContent: 'center', padding: '12px' }}
            disabled={loading}
          >
            {loading ? <><span className="spinner" style={{ width: 16, height: 16 }} /> Signing in...</> : 'Sign In →'}
          </button>
        </form>

        <div className="auth-switch">
          New here? <Link to="/register">Create an account</Link>
        </div>
      </div>
    </div>
  );
}