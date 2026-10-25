// src/services/api.js
import axios from 'axios';

export const AUTH_EXPIRED_EVENT = 'animechat:auth-expired';
export const TOKEN_KEY = 'animechat_token';

export function apiError(error, fallback = 'Request failed') {
  const data = error.response?.data;
  if (typeof data === 'string' && data) return data;
  return data?.message || data?.detail || fallback;
}

const api = axios.create({
  baseURL: process.env.REACT_APP_API_URL || 'http://localhost:8080',
  headers: { 'Content-Type': 'application/json' },
  timeout: 15000,
});

// Attach JWT Bearer token on every request (set after login/OAuth)
api.interceptors.request.use((config) => {
  const token = config.skipAuth ? null : localStorage.getItem(TOKEN_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
    config.authToken = token;
  }
  return config;
});

// Only expire the session whose token was rejected; login failures stay in the form.
api.interceptors.response.use(
  (res) => res,
  (err) => {
    const token = err.config?.authToken;
    if (err.response?.status === 401 && token && localStorage.getItem(TOKEN_KEY) === token) {
      localStorage.removeItem(TOKEN_KEY);
      window.dispatchEvent(new Event(AUTH_EXPIRED_EVENT));
    }
    return Promise.reject(err);
  }
);

export default api;