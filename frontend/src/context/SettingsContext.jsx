import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import api from '../api/client.js';
import { useAuth } from './AuthContext.jsx';

const SettingsContext = createContext(null);
const DEFAULTS = { theme: 'LIGHT', currency: 'ZAR', notificationsEnabled: true };

function applyTheme(theme) {
  const dark = theme === 'DARK'
    || (theme === 'SYSTEM' && window.matchMedia('(prefers-color-scheme: dark)').matches);
  document.documentElement.dataset.theme = dark ? 'dark' : 'light';
}

export function SettingsProvider({ children }) {
  const { user } = useAuth();
  const [settings, setSettings] = useState(DEFAULTS);

  useEffect(() => {
    if (!user) { applyTheme('LIGHT'); return; }
    api.get('/api/settings').then(({ data }) => { setSettings(data); applyTheme(data.theme); }).catch(() => {});
  }, [user]);

  // when the theme is "match my device", follow the device if it switches between light and dark
  useEffect(() => {
    if (settings.theme !== 'SYSTEM') return undefined;
    const query = window.matchMedia('(prefers-color-scheme: dark)');
    const onChange = () => applyTheme('SYSTEM');
    query.addEventListener('change', onChange);
    return () => query.removeEventListener('change', onChange);
  }, [settings.theme]);

  /** Saves all three settings and applies them straight away. */
  const save = useCallback(async (next) => {
    const { data } = await api.put('/api/settings', {
      theme: next.theme, currency: next.currency, notificationsEnabled: next.notificationsEnabled,
    });
    setSettings(data);
    applyTheme(data.theme);
  }, []);

  /** Formats a number in the user's chosen currency. */
  const money = useCallback((value) => {
    const locale = settings.currency === 'ZAR' ? 'en-ZA' : 'en-US';
    try {
      return new Intl.NumberFormat(locale, { style: 'currency', currency: settings.currency }).format(Number(value ?? 0));
    } catch { return String(value); }
  }, [settings.currency]);

  return <SettingsContext.Provider value={{ settings, money, save }}>{children}</SettingsContext.Provider>;
}

export const useSettings = () => useContext(SettingsContext);
