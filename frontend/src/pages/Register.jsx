import { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import AuthCard from '../components/AuthCard.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { errorMessage } from '../api/client.js';

export default function Register() {
  const { user, loading, register } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ firstName: '', lastName: '', email: '', password: '' });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  if (loading) return null;
  if (user) return <Navigate to="/" replace />;
  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value });

  async function submit(e) {
    e.preventDefault();
    setBusy(true); setError('');
    try { await register(form); navigate('/'); }
    catch (err) { setError(errorMessage(err)); }
    finally { setBusy(false); }
  }

  return (
    <AuthCard title="Create your account" subtitle="Start tracking income, spending and savings."
      footer={<>Already registered? <Link to="/login">Log in</Link></>}>
      <form onSubmit={submit}>
        <div className="row">
          <label>First name<input required value={form.firstName} onChange={set('firstName')} /></label>
          <label>Last name<input required value={form.lastName} onChange={set('lastName')} /></label>
        </div>
        <label>Email<input type="email" required value={form.email} onChange={set('email')} /></label>
        <label>Password
          <input type="password" required minLength={8} maxLength={72} value={form.password} onChange={set('password')} />
          <small className="muted">8 to 72 characters</small>
        </label>
        {error && <div className="alert">{error}</div>}
        <button className="btn" disabled={busy}>{busy ? 'Creating...' : 'Create account'}</button>
      </form>
    </AuthCard>
  );
}
