import { createContext, useContext, useEffect, useState } from 'react';
import api, { refreshSession, session } from '../api/client.js';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);   // true while we check whether the browser still has a session

  useEffect(() => {
    let active = true;
    // Nothing is stored in the page, so on every load we ask the server whether the cookie is still good.
    refreshSession()
      .then((data) => { if (active) setUser(data.user); })
      .catch(() => {})
      .finally(() => { if (active) setLoading(false); });

    const onSignedOut = () => { session.clear(); setUser(null); };
    window.addEventListener('sw:signed-out', onSignedOut);
    return () => { active = false; window.removeEventListener('sw:signed-out', onSignedOut); };
  }, []);

  async function authenticate(path, body) {
    const { data } = await api.post(path, body);
    session.set(data.accessToken);
    setUser(data.user);
  }

  const value = {
    user,
    loading,
    login: (email, password) => authenticate('/api/auth/login', { email, password }),
    register: (form) => authenticate('/api/auth/register', form),
    /** Merge changes into the signed-in user (e.g. after editing the profile) so the header updates at once. */
    updateUser(changes) { setUser((current) => ({ ...current, ...changes })); },
    async logout() {
      try { await api.post('/api/auth/logout'); } catch { /* the cookie may already be gone */ }
      session.clear();
      setUser(null);
    },
  };
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export const useAuth = () => useContext(AuthContext);
