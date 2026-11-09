import { useEffect, useState } from 'react';
import api, { errorMessage } from '../api/client.js';
import Preferences from '../components/settings/Preferences.jsx';
import ProfileForm from '../components/settings/ProfileForm.jsx';
import PictureCard from '../components/settings/PictureCard.jsx';
import PasswordForm from '../components/settings/PasswordForm.jsx';

export default function Settings() {
  const [profile, setProfile] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    api.get('/api/users/me').then(({ data }) => setProfile(data)).catch((e) => setError(errorMessage(e)));
  }, []);

  return (
    <>
      <div className="page-head"><h1>Settings</h1></div>
      {error && <div className="alert">{error}</div>}
      <div className="settings-grid">
        <Preferences />
        {profile ? <ProfileForm profile={profile} onSaved={setProfile} /> : !error && <p className="muted">Loading your profile...</p>}
        {profile && <PictureCard profile={profile} onChange={setProfile} />}
        <PasswordForm />
      </div>
    </>
  );
}
