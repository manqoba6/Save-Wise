import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import AuthCard from '../components/AuthCard.jsx';
import api, { errorMessage } from '../api/client.js';

export default function ResetPassword() {
  const [params] = useSearchParams();
  const token = params.get('token') || '';
  const [newPassword, setNewPassword] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  async function submit(e) {
    e.preventDefault();
    setError('');
    try { const { data } = await api.post('/api/auth/reset-password', { token, newPassword }); setMessage(data.message); }
    catch (err) { setError(errorMessage(err)); }
  }

  return (
    <AuthCard title="Choose a new password" footer={<Link to="/login">Back to log in</Link>}>
      {!token ? <div className="alert">This link is missing its token. Request a new reset email.</div> : (
        <form onSubmit={submit}>
          <label>New password
            <input type="password" required minLength={8} maxLength={72} value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)} />
          </label>
          {message && <div className="notice">{message}</div>}
          {error && <div className="alert">{error}</div>}
          <button className="btn" disabled={!!message}>Reset password</button>
        </form>
      )}
    </AuthCard>
  );
}
