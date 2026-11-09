import { useState } from 'react';
import api, { errorMessage } from '../../api/client.js';
import { useAuth } from '../../context/AuthContext.jsx';

export default function ProfileForm({ profile, onSaved }) {
  const { updateUser } = useAuth();
  const [form, setForm] = useState({
    firstName: profile.firstName, lastName: profile.lastName,
    phone: profile.phone || '', monthlyIncome: profile.monthlyIncome == null ? '' : String(profile.monthlyIncome),
  });
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const set = (key) => (e) => { setForm({ ...form, [key]: e.target.value }); setMessage(''); };

  async function submit(e) {
    e.preventDefault();
    setSaving(true); setError(''); setMessage('');
    try {
      const { data } = await api.put('/api/users/me', {
        firstName: form.firstName.trim(), lastName: form.lastName.trim(),
        phone: form.phone.trim() || null,
        monthlyIncome: form.monthlyIncome === '' ? null : Number(form.monthlyIncome),
      });
      onSaved(data);
      updateUser({ firstName: data.firstName, lastName: data.lastName });
      setMessage('Profile saved.');
    } catch (err) { setError(errorMessage(err)); }
    finally { setSaving(false); }
  }

  return (
    <section className="card">
      <h2>Profile</h2>
      <form onSubmit={submit}>
        <div className="row">
          <label>First name<input required maxLength={100} value={form.firstName} onChange={set('firstName')} /></label>
          <label>Last name<input required maxLength={100} value={form.lastName} onChange={set('lastName')} /></label>
        </div>
        <label>Email<input value={profile.email} disabled /></label>
        <div className="row">
          <label>Phone<input value={form.phone} placeholder="+27 82 123 4567" onChange={set('phone')} /></label>
          <label>Monthly income<input type="number" min="0" step="0.01" value={form.monthlyIncome} onChange={set('monthlyIncome')} /></label>
        </div>
        {message && <div className="notice">{message}</div>}
        {error && <div className="alert">{error}</div>}
        <div><button className="btn" disabled={saving}>{saving ? 'Saving...' : 'Save profile'}</button></div>
      </form>
    </section>
  );
}
