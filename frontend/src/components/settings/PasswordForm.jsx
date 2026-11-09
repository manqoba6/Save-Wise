import { useState } from 'react';
import api, { errorMessage } from '../../api/client.js';
import { useAuth } from '../../context/AuthContext.jsx';

const EMPTY = { current: '', next: '', confirm: '' };

export default function PasswordForm() {
  const { user, login } = useAuth();
  const [form, setForm] = useState(EMPTY);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const set = (key) => (e) => { setForm({ ...form, [key]: e.target.value }); setMessage(''); };

  async function submit(e) {
    e.preventDefault();
    setError(''); setMessage('');
    if (form.next !== form.confirm) { setError('The new passwords do not match.'); return; }
    setSaving(true);
    try {
      await api.post('/api/auth/change-password', { currentPassword: form.current, newPassword: form.next });
      // Changing the password ends every refresh token, this session's included. Log in again quietly
      // with the new password so the user is not thrown out when the 15-minute access token expires.
      try { await login(user.email, form.next); } catch { /* they will simply be asked to log in later */ }
      setForm(EMPTY);
      setMessage('Password changed. Your other devices will need to log in again.');
    } catch (err) { setError(errorMessage(err)); }
    finally { setSaving(false); }
  }

  return (
    <section className="card">
      <h2>Change password</h2>
      <form onSubmit={submit}>
        <label>Current password<input type="password" required autoComplete="current-password" value={form.current} onChange={set('current')} /></label>
        <div className="row">
          <label>New password<input type="password" required minLength={8} maxLength={72} autoComplete="new-password" value={form.next} onChange={set('next')} /></label>
          <label>Confirm new password<input type="password" required autoComplete="new-password" value={form.confirm} onChange={set('confirm')} /></label>
        </div>
        {message && <div className="notice">{message}</div>}
        {error && <div className="alert">{error}</div>}
        <div><button className="btn" disabled={saving}>{saving ? 'Changing...' : 'Change password'}</button></div>
      </form>
    </section>
  );
}
