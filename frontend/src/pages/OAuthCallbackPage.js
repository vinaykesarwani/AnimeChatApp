// src/pages/OAuthCallbackPage.js
import React, { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';

export default function OAuthCallbackPage() {
  const [searchParams]   = useSearchParams();
  const { handleOAuthCallback } = useAuth();
  const { addToast }     = useToast();
  const navigate         = useNavigate();
  const [error, setError] = useState('');

  useEffect(() => {
    const token = searchParams.get('token');
    const err   = searchParams.get('error');

    if (err || !token) {
      setError(err || 'OAuth login failed. Please try again.');
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
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

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