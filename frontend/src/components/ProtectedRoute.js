import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function ProtectedRoute({ children }) {
  const { user, loading, authError, restoreSession, logout } = useAuth();
  const location = useLocation();

  if (loading) {
    return (
      <div className="loading-screen">
        <div className="spinner" />
        <span className="loading-text">AUTHENTICATING...</span>
      </div>
    );
  }

  if (authError) {
    return (
      <div className="loading-screen" role="alert">
        <p>{authError}</p>
        <button onClick={restoreSession}>Retry</button>
        <button onClick={logout}>Sign in again</button>
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  return children;
}
