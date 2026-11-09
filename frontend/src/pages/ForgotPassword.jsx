import { useState } from 'react';
import { Link } from 'react-router-dom';
import AuthCard from '../components/AuthCard.jsx';
import api, { errorMessage } from '../api/client.js';

export default function ForgotPassword() {
  const [email, setEmail] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  async function submit(e) {
    e.preventDefault();
    setError(''); setMessage('');
    try { const { data } = await api.post('/api/auth/forgot-password', { email }); setMessage(data.message); }
    catch (err) { setError(errorMessage(err)); }
  }

  return (
    <AuthCard title="Forgot your password?" subtitle="Enter your email and we will send you a reset link."
      footer={<Link to="/login">Back to log in</Link>}>
      <form onSubmit={submit}>
        <label>Email<input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} /></label>
        {message && <div className="notice">{message}</div>}
        {error && <div className="alert">{error}</div>}
        <button className="btn">Send reset link</button>
      </form>
    </AuthCard>
  );
}
