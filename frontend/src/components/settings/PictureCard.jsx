import { useEffect, useRef, useState } from 'react';
import api, { errorMessage } from '../../api/client.js';

const ALLOWED = ['image/jpeg', 'image/png', 'image/webp'];
const MAX_BYTES = 2 * 1024 * 1024;

export default function PictureCard({ profile, onChange }) {
  const [url, setUrl] = useState(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const input = useRef(null);

  // The image endpoint needs the login token, so fetch it as a blob rather than using a plain <img src>.
  useEffect(() => {
    if (!profile.hasProfilePicture) { setUrl(null); return undefined; }
    let objectUrl;
    let cancelled = false;
    api.get('/api/users/me/picture', { responseType: 'blob' })
      .then(({ data }) => { if (!cancelled) { objectUrl = URL.createObjectURL(data); setUrl(objectUrl); } })
      .catch(() => {});
    return () => { cancelled = true; if (objectUrl) URL.revokeObjectURL(objectUrl); };
  }, [profile.hasProfilePicture]);

  async function upload(e) {
    const file = e.target.files[0];
    e.target.value = '';
    if (!file) return;
    if (!ALLOWED.includes(file.type)) { setError('Please choose a JPEG, PNG or WebP image.'); return; }
    if (file.size > MAX_BYTES) { setError('That image is over 2 MB. Please choose a smaller one.'); return; }
    setBusy(true); setError('');
    try {
      const body = new FormData();
      body.append('file', file);
      const { data } = await api.post('/api/users/me/picture', body);
      onChange(data);
    } catch (err) { setError(errorMessage(err)); }
    finally { setBusy(false); }
  }

  async function remove() {
    setBusy(true); setError('');
    try { await api.delete('/api/users/me/picture'); onChange({ ...profile, hasProfilePicture: false }); }
    catch (err) { setError(errorMessage(err)); }
    finally { setBusy(false); }
  }

  const initials = `${profile.firstName?.[0] || ''}${profile.lastName?.[0] || ''}`.toUpperCase();

  return (
    <section className="card">
      <h2>Profile picture</h2>
      <div className="avatar-row">
        {url ? <img className="avatar" src={url} alt="Your profile" /> : <div className="avatar placeholder" aria-label="No picture">{initials}</div>}
        <div className="btn-row">
          <button className="btn ghost" disabled={busy} onClick={() => input.current.click()}>{profile.hasProfilePicture ? 'Change picture' : 'Upload picture'}</button>
          {profile.hasProfilePicture && <button className="btn ghost" disabled={busy} onClick={remove}>Remove</button>}
        </div>
        <input ref={input} type="file" hidden accept="image/jpeg,image/png,image/webp" aria-label="Choose picture" onChange={upload} />
      </div>
      <p className="muted small">JPEG, PNG or WebP, up to 2 MB.</p>
      {error && <div className="alert">{error}</div>}
    </section>
  );
}
