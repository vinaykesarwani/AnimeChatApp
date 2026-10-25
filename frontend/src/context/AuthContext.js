// src/context/AuthContext.js
import React, { createContext, useContext, useState, useEffect, useRef, useCallback } from 'react';
import api, { apiError, AUTH_EXPIRED_EVENT } from '../services/api';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser]           = useState(null);
  const [credentials, setCredentials] = useState(null); // kept for WS compatibility
  const [loading, setLoading]     = useState(true);
  const [authError, setAuthError] = useState('');
  const sessionVersion = useRef(0);

  // Restore session from saved JWT on mount
  const restoreSession = useCallback(() => {
    const attempt = ++sessionVersion.current;
    setAuthError('');
    const token = localStorage.getItem('animechat_token');
    if (token) {
      setLoading(true);
      return api.get('/api/users/self')
        .then(res => {
          if (attempt !== sessionVersion.current || localStorage.getItem('animechat_token') !== token) return;
          setUser(res.data);
          // Provide a credentials-like object so WebSocket code keeps working
          setCredentials({ token });
        })
        .catch(err => {
          if (attempt !== sessionVersion.current) return;
          setUser(null);
          setCredentials(null);
          if (err.response?.status === 401) localStorage.removeItem('animechat_token');
          else setAuthError('Unable to restore your session. Check your connection and retry.');
        })
        .finally(() => { if (attempt === sessionVersion.current) setLoading(false); });
    } else {
      setUser(null);
      setCredentials(null);
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    restoreSession();
    return () => { sessionVersion.current += 1; };
  }, [restoreSession]);

  const _applySession = (token, userData) => {
    localStorage.setItem('animechat_token', token);
    setUser(userData);
    setCredentials({ token });
    setAuthError('');
    setLoading(false);
  };

  const logout = useCallback(() => {
    sessionVersion.current += 1;
    setAuthError('');
    setLoading(false);
    setUser(null);
    setCredentials(null);
    localStorage.removeItem('animechat_token');
    // Also clear legacy Basic-Auth key if present
    localStorage.removeItem('animechat_creds');
  }, []);

  useEffect(() => {
    window.addEventListener(AUTH_EXPIRED_EVENT, logout);
    return () => window.removeEventListener(AUTH_EXPIRED_EVENT, logout);
  }, [logout]);

  // ── Username / Password login (unchanged UX, now returns JWT) ──────────────
  const login = async (username, password) => {
    logout(); // clear any existing session first
    const attempt = sessionVersion.current;
    try {
      const res = await api.post('/api/auth/login', { username, password }, { skipAuth: true });
      if (attempt !== sessionVersion.current) return { success: false, error: 'Sign-in was cancelled' };
      const { token, user: userData } = res.data; // backend returns { token, user }
      _applySession(token, userData);
      return { success: true };
    } catch (err) {
      return {
        success: false,
        error: err.response?.status === 401
          ? 'Invalid username or password'
          : apiError(err, 'Login failed'),
      };
    }
  };

  const register = async (username, password) => {
    try {
      await api.post('/api/users', { username, password }, { skipAuth: true });
    } catch (err) {
      return { success: false, error: apiError(err, 'Registration failed') };
    }
    const result = await login(username, password);
    return result.success ? result : {
      success: false, accountCreated: true,
      error: 'Your account was created, but automatic sign-in failed. Please sign in.',
    };
  };

  // ── OAuth2: redirect to Spring's authorization endpoint ───────────────────
  const loginWithOAuth = (provider /* 'google' | 'github' */) => {
    const base = process.env.REACT_APP_API_URL || 'http://localhost:8080';
    // Spring Security's default OAuth2 authorization URL
    window.location.href = `${base}/oauth2/authorization/${provider}`;
  };

  // ── OAuth2 callback: called by OAuthCallbackPage after redirect ────────────
  const handleOAuthCallback = async (token) => {
    const attempt = ++sessionVersion.current;
    try {
      localStorage.setItem('animechat_token', token);
      const res = await api.get('/api/users/self');
      if (attempt !== sessionVersion.current) return { success: false, error: 'Sign-in was cancelled' };
      _applySession(token, res.data);
      return { success: true };
    } catch (err) {
      if (attempt === sessionVersion.current) {
        if (err.response?.status === 401) logout();
        else {
          setAuthError('Unable to restore your session. Check your connection and retry.');
          setLoading(false);
        }
      }
      return { success: false, error: 'OAuth login failed' };
    }
  };

  return (
    <AuthContext.Provider value={{
      user, credentials, loading, authError, restoreSession,
      login, logout, register,
      loginWithOAuth, handleOAuthCallback,
    }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}