import React, { createContext, useContext, useState, useEffect } from 'react';
import api from '../services/api';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  // credentials stored as React state so they are ALWAYS in sync with user —
  // no render can have user set but credentials missing (which caused the WS
  // to briefly see null credentials, tear down, and show "not connected")
  const [credentials, setCredentials] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const savedCreds = localStorage.getItem('animechat_creds');
    if (savedCreds) {
      const { username, password } = JSON.parse(savedCreds);
      api.defaults.auth = { username, password };
      api.get('/api/users/self')
        .then(res => {
          // Set user AND credentials in the same tick
          setUser(res.data);
          setCredentials({ username, password });
        })
        .catch(() => {
          localStorage.removeItem('animechat_creds');
          api.defaults.auth = null;
        })
        .finally(() => setLoading(false));
    } else {
      setLoading(false);
    }
  }, []);

  const logout = () => {
    setUser(null);
    setCredentials(null);
    api.defaults.auth = null;
    localStorage.removeItem('animechat_creds');
  };

  const login = async (username, password) => {
    // Clear previous session atomically before attempting new login
    setUser(null);
    setCredentials(null);
    api.defaults.auth = null;
    localStorage.removeItem('animechat_creds');

    api.defaults.auth = { username, password };
    try {
      const res = await api.get('/api/users/self');
      // Set user AND credentials atomically — one render, no gap
      setUser(res.data);
      setCredentials({ username, password });
      localStorage.setItem('animechat_creds', JSON.stringify({ username, password }));
      return { success: true };
    } catch (err) {
      api.defaults.auth = null;
      return { success: false, error: err.response?.status === 401 ? 'Invalid username or password' : 'Login failed' };
    }
  };

  const register = async (username, password) => {
    try {
      await api.post('/api/users', { username, password });
      return await login(username, password);
    } catch (err) {
      return { success: false, error: err.response?.data || 'Registration failed' };
    }
  };

  return (
    <AuthContext.Provider value={{ user, credentials, loading, login, logout, register }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}