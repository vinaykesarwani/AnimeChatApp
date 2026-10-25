// src/pages/OAuthCallbackPage.js
import React, { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';

export default function OAuthCallbackPage() {
  const [searchParams]   = useSearchParams();
  const { loading, handleOAuthCallback } = useAuth();
  const { addToast }     = useToast();
  const navigate         = useNavigate();
  const [error, setError] = useState('');
  const handled = useRef(false);

  useEffect(() => {
    // Finish session restoration before exchanging the new token. StrictMode replays effects.
    if (loading || handled.current) return;
    handled.current = true;

    const token = searchParams.get('token');
    const err   = searchParams.get('error');

    if (err || !token) {
      setError(err === 'account_link_required'
        ? 'We could not safely match your existing account. Please sign in with your original method.'
        : 'Sign-in failed. Please try again.');
      return;
    }

    handleOAuthCallback(token).then(result => {
      if (result.success) {
        addToast('Signed in successfully!', 'success');
        navigate('/', { replace: true });
      } else {
        setError(result.error);
      }
    });
  }, [loading, searchParams, handleOAuthCallback, addToast, navigate]);

  if (error) {
    return (
      <div className="auth-page">
        <div className="auth-card">
          <h1 className="auth-title">AUTH <span>ERROR</span></h1>
          <p style={{ color: 'var(--accent-2)', marginBottom: 16 }}>{error}</p>
          <a href="/login" className="btn btn-primary" style={{ display: 'inline-block' }}>
            Back to Login
          </a>
        </div>
      </div>
    );
  }

  return (
    <div className="loading-screen">
      <div className="spinner" />
      <span className="loading-text">COMPLETING SIGN IN...</span>
    </div>
  );
}
