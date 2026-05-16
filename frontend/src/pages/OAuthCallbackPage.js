import React, { useEffect, useRef } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';

/**
 * The backend redirects here after a successful Google login:
 *   http://localhost:3000/oauth/callback?token=<jwt>
 *
 * This page reads the token, stores it via AuthContext, and sends
 * the user to the homepage. If no token is present it redirects to /login.
 */
export default function OAuthCallbackPage() {
  const [searchParams] = useSearchParams();
  const { loginWithToken } = useAuth();
  const { addToast } = useToast();
  const navigate = useNavigate();
  const handled = useRef(false); // prevent double-fire in React StrictMode

  useEffect(() => {
    if (handled.current) return;
    handled.current = true;

    const token = searchParams.get('token');

    if (!token) {
      addToast('Google login failed. Please try again.', 'error');
      navigate('/login', { replace: true });
      return;
    }

    loginWithToken(token).then(result => {
      if (result.success) {
        addToast('Signed in with Google!', 'success');
        navigate('/', { replace: true });
      } else {
        addToast(result.error || 'Login failed', 'error');
        navigate('/login', { replace: true });
      }
    });
  }, []);

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      height: '60vh',
      gap: '16px',
    }}>
      <span className="spinner" style={{ width: 32, height: 32 }} />
      <p style={{ color: 'var(--text-secondary)' }}>Completing sign-in…</p>
    </div>
  );
}
