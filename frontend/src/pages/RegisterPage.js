import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';

export default function RegisterPage() {
  const { register } = useAuth();
  const { addToast } = useToast();
  const navigate = useNavigate();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [loading, setLoading] = useState(false);
  const [errors, setErrors] = useState({});

  const validate = () => {
    const e = {};
    if (!username.trim()) e.username = 'Username is required';
    else if (username.length < 3) e.username = 'At least 3 characters';
    if (!password) e.password = 'Password is required';
    else if (password.length < 4) e.password = 'At least 4 characters';
    if (password !== confirm) e.confirm = 'Passwords do not match';
    return e;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const errs = validate();
    if (Object.keys(errs).length) { setErrors(errs); return; }
    setErrors({});
    setLoading(true);
    const result = await register(username.trim(), password);
    setLoading(false);
    if (result.success) {
      addToast('Account created! Welcome aboard.', 'success');
      navigate('/');
    } else {
      setErrors({ general: typeof result.error === 'string' ? result.error : 'Registration failed' });
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-card">
        <h1 className="auth-title">JOIN<span> US</span></h1>
        <p className="auth-subtitle">Create your account to start discussing anime</p>

        <form className="auth-form" onSubmit={handleSubmit}>
          <div className="form-group">
            <label className="form-label">Username</label>
            <input
              className="form-input"
              type="text"
              placeholder="choose_username"
              value={username}
              onChange={e => { setUsername(e.target.value); setErrors(prev => ({ ...prev, username: '' })); }}
              autoFocus
            />
            {errors.username && <span className="form-error">{errors.username}</span>}
          </div>
          <div className="form-group">
            <label className="form-label">Password</label>
            <input
              className="form-input"
              type="password"
              placeholder="••••••••"
              value={password}
              onChange={e => { setPassword(e.target.value); setErrors(prev => ({ ...prev, password: '' })); }}
            />
            {errors.password && <span className="form-error">{errors.password}</span>}
          </div>
          <div className="form-group">
            <label className="form-label">Confirm Password</label>
            <input
              className="form-input"
              type="password"
              placeholder="••••••••"
              value={confirm}
              onChange={e => { setConfirm(e.target.value); setErrors(prev => ({ ...prev, confirm: '' })); }}
            />
            {errors.confirm && <span className="form-error">{errors.confirm}</span>}
          </div>

          {errors.general && (
            <div style={{
              padding: '10px 14px',
              background: 'rgba(230,57,70,0.1)',
              border: '1px solid var(--border-accent)',
              borderRadius: 'var(--radius-sm)',
              color: 'var(--accent-2)',
              fontSize: '0.85rem'
            }}>
              ✕ {errors.general}
            </div>
          )}

          <button
            type="submit"
            className="btn btn-primary"
            style={{ width: '100%', justifyContent: 'center', padding: '12px' }}
            disabled={loading}
          >
            {loading ? <><span className="spinner" style={{ width: 16, height: 16 }} /> Creating...</> : 'Create Account →'}
          </button>
        </form>

        <div className="auth-switch">
          Already have an account? <Link to="/login">Sign in</Link>
        </div>
      </div>
    </div>
  );
}
