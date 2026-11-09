import { useEffect, useState } from 'react';
import api, { errorMessage } from '../../api/client.js';
import { useSettings } from '../../context/SettingsContext.jsx';

const THEMES = [['LIGHT', 'Light'], ['DARK', 'Dark'], ['SYSTEM', 'Match my device']];

/** Every change is saved straight away and takes effect immediately. */
export default function Preferences() {
  const { settings, save } = useSettings();
  const [currencies, setCurrencies] = useState([]);
  const [status, setStatus] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    api.get('/api/settings/currencies').then(({ data }) => setCurrencies(data)).catch(() => {});
  }, []);

  async function change(patch) {
    setStatus('Saving...'); setError('');
    try { await save({ ...settings, ...patch }); setStatus('Saved'); }
    catch (err) { setStatus(''); setError(errorMessage(err)); }
  }

  // keep a currency the user already has selected even if it is not in the common list
  const options = currencies.some((c) => c.code === settings.currency)
    ? currencies : [{ code: settings.currency, name: settings.currency }, ...currencies];

  return (
    <section className="card">
      <div className="between"><h2>Appearance and currency</h2><span className="muted small" role="status">{status}</span></div>

      <fieldset className="choices">
        <legend>Theme</legend>
        {THEMES.map(([value, label]) => (
          <label key={value} className="choice">
            <input type="radio" name="theme" value={value} checked={settings.theme === value} onChange={() => change({ theme: value })} />
            {label}
          </label>
        ))}
      </fieldset>

      <label>Currency
        <select value={settings.currency} onChange={(e) => change({ currency: e.target.value })}>
          {options.map((c) => <option key={c.code} value={c.code}>{c.code === c.name ? c.code : `${c.code} - ${c.name}`}</option>)}
        </select>
      </label>

      <label className="choice toggle">
        <input type="checkbox" checked={settings.notificationsEnabled} onChange={(e) => change({ notificationsEnabled: e.target.checked })} />
        <span>In-app notifications
          <small className="muted block">When off, budget alerts and goal milestones are not recorded for you.</small>
        </span>
      </label>

      {error && <div className="alert">{error}</div>}
    </section>
  );
}
