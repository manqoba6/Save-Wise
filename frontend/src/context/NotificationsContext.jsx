import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import api from '../api/client.js';
import { useAuth } from './AuthContext.jsx';

const NotificationsContext = createContext(null);

/** Keeps the unread count fresh: every minute, and whenever a page calls refresh() (e.g. after adding an expense). */
export function NotificationsProvider({ children }) {
  const { user } = useAuth();
  const [unread, setUnread] = useState(0);

  const refresh = useCallback(() => {
    api.get('/api/notifications/unread-count').then(({ data }) => setUnread(data.unread)).catch(() => {});
  }, []);

  useEffect(() => {
    if (!user) { setUnread(0); return undefined; }
    refresh();
    const timer = setInterval(refresh, 60000);
    return () => clearInterval(timer);
  }, [user, refresh]);

  return <NotificationsContext.Provider value={{ unread, refresh }}>{children}</NotificationsContext.Provider>;
}

export const useNotifications = () => useContext(NotificationsContext) ?? { unread: 0, refresh() {} };
