import React, { createContext, useContext, useState, useEffect } from 'react';
import api from '../services/api';

const AuthContext = createContext(null);

const TOKEN_KEY = 'animechat_token';

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  // Keep the token in state so useWebSocket can read it reactively
  const [token, setToken] = useState(null);
  const [loading, setLoading] = useState(true);

  // ── Helpers ────────────────────────────────────────────────────────────────

  /** Persist token, set axios header, and fetch the current user. */
  const applyToken = async (jwt) => {
    localStorage.setItem(TOKEN_KEY, jwt);
    api.defaults.headers.common['Authorization'] = `Bearer ${jwt}`;
    setToken(jwt);
    const res = await api.get('/api/users/self');
    setUser(res.data);
  };

  const clearSession = () => {
    localStorage.removeItem(TOKEN_KEY);
    delete api.defaults.headers.common['Authorization'];
    setToken(null);
    setUser(null);
  };

  // ── Restore session on page load ───────────────────────────────────────────

  useEffect(() => {
    const savedToken = localStorage.getItem(TOKEN_KEY);
    if (savedToken) {
      api.defaults.headers.common['Authorization'] = `Bearer ${savedToken}`;
      api.get('/api/users/self')
        .then(res => {
          setToken(savedToken);
          setUser(res.data);
        })
        .catch(() => clearSession())
        .finally(() => setLoading(false));
    } else {
      setLoading(false);
    }
  }, []);

  // ── Auth actions ───────────────────────────────────────────────────────────

  const logout = () => clearSession();

  /**
   * Username/password login:
   * 1. Send Basic credentials to /api/auth/token
   * 2. Receive a JWT — store it and never touch the raw password again
   */
  const login = async (username, password) => {
    clearSession();
    try {
      // Temporarily set Basic auth just for this one call
      const res = await api.post('/api/auth/token', {}, {
        auth: { username, password },
      });
      await applyToken(res.data.token);
      return { success: true };
    } catch (err) {
      clearSession();
      return {
        success: false,
        error: err.response?.status === 401
          ? 'Invalid username or password'
          : 'Login failed',
      };
    }
  };

  /**
   * Called by OAuthCallbackPage after Google redirects back with a token.
   * The token was minted by the backend's OAuthSuccessHandler.
   */
  const loginWithToken = async (jwt) => {
    try {
      await applyToken(jwt);
      return { success: true };
    } catch {
      clearSession();
      return { success: false, error: 'OAuth login failed' };
    }
  };

  const register = async (username, password) => {
    try {
      await api.post('/api/users', { username, password });
    } catch (err) {
      return { success: false, error: err.response?.data || 'Registration failed' };
    }
    return await login(username, password);
  };

  return (
    <AuthContext.Provider value={{ user, token, loading, login, loginWithToken, logout, register }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
