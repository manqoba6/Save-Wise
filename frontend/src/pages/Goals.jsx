import { useEffect, useState } from 'react';
import api, { errorMessage } from '../api/client.js';
import { useSettings } from '../context/SettingsContext.jsx';
import { useNotifications } from '../context/NotificationsContext.jsx';
import Modal from '../components/Modal.jsx';
import { daysUntil } from '../utils.js';

const TABS = [['ACTIVE', 'Active'], ['COMPLETED', 'Completed'], ['CANCELLED', 'Cancelled'], ['', 'All']];
const STATUS = { ACTIVE: ['Active', 'info'], COMPLETED: ['Completed', 'good'], CANCELLED: ['Cancelled', 'warn'] };

function deadlineText(goal) {
  if (!goal.deadline) return { text: 'No deadline', tone: 'muted' };
  if (goal.status !== 'ACTIVE') return { text: `Deadline ${goal.deadline}`, tone: 'muted' };
  const d = daysUntil(goal.deadline);
  if (d < 0) return { text: `Overdue by ${-d} day${d === -1 ? '' : 's'}`, tone: 'bad' };
  if (d === 0) return { text: 'Due today', tone: 'warn' };
  return { text: `${d} day${d === 1 ? '' : 's'} left (${goal.deadline})`, tone: 'muted' };
}

export default function Goals() {
  const { money } = useSettings();
  const { refresh } = useNotifications();
  const [status, setStatus] = useState('ACTIVE');
  const [goals, setGoals] = useState(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [reload, setReload] = useState(0);

  const [dialog, setDialog] = useState(null);      // { kind: 'goal' | 'contribute', goal }
  const [form, setForm] = useState({ title: '', target: '', deadline: '', amount: '' });
  const [formError, setFormError] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    api.get('/api/goals', { params: status ? { status } : {} })
      .then(({ data }) => { setGoals(data); setError(''); }).catch((e) => setError(errorMessage(e)));
  }, [status, reload]);

  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value });

  function open(kind, goal = null) {
    setForm({ title: goal?.title || '', target: goal ? String(goal.targetAmount) : '', deadline: goal?.deadline || '', amount: '' });
    setFormError(''); setDialog({ kind, goal });
  }

  async function submit(e) {
    e.preventDefault();
    setSaving(true); setFormError('');
    const { kind, goal } = dialog;
    try {
      if (kind === 'contribute') {
        const { data } = await api.post(`/api/goals/${goal.id}/contribute`, { amount: Number(form.amount) });
        if (data.status === 'COMPLETED') { setNotice(`You reached your goal "${data.title}"!`); refresh(); }
        else setNotice('');
      } else {
        const body = { title: form.title.trim(), targetAmount: Number(form.target), deadline: form.deadline || null };
        if (goal) await api.put(`/api/goals/${goal.id}`, body); else await api.post('/api/goals', body);
      }
      setDialog(null); setReload((n) => n + 1);
    } catch (err) { setFormError(errorMessage(err)); }
    finally { setSaving(false); }
  }

  async function act(request, confirmText) {
    if (confirmText && !window.confirm(confirmText)) return;
    try { await request(); setReload((n) => n + 1); }
    catch (err) { setError(errorMessage(err)); }
  }

  return (
    <>
      <div className="page-head">
        <h1>Goals</h1>
        <button className="btn" onClick={() => open('goal')}>+ New goal</button>
      </div>

      <div className="tabs">
        {TABS.map(([value, label]) => (
          <button key={label} className={status === value ? 'tab active' : 'tab'} onClick={() => { setStatus(value); setGoals(null); setNotice(''); }}>{label}</button>
        ))}
      </div>

      {error && <div className="alert">{error}</div>}
      {notice && <div className="notice spaced">{notice}</div>}

      {!goals ? <p className="muted">Loading...</p> : goals.length === 0 ? (
        <div className="card"><p className="muted">{status === 'ACTIVE' ? 'No active goals. Set one (a laptop, a holiday, a deposit) and track your progress.' : 'Nothing here.'}</p></div>
      ) : (
        <div className="grid budgets">
          {goals.map((g) => {
            const [label, tone] = STATUS[g.status] || STATUS.ACTIVE;
            const due = deadlineText(g);
            return (
              <div className="card budget" key={g.id}>
                <div className="between"><strong>{g.title}</strong><span className={`pill ${tone}`}>{label}</span></div>
                <div className="bar big"><div className={`fill ${g.status === 'COMPLETED' ? 'good' : 'primary'}`} style={{ width: `${Math.min(100, Number(g.percentComplete) || 0)}%` }} /></div>
                <div className="between small"><span>{money(g.currentAmount)} of {money(g.targetAmount)}</span><span>{g.percentComplete}%</span></div>
                <div className={`small ${due.tone}`}>{due.text}</div>
                <div className="btn-row">
                  {g.status === 'ACTIVE' && <button className="btn small-btn" onClick={() => open('contribute', g)}>Add money</button>}
                  {g.status === 'ACTIVE' && (
                    <button className="btn ghost small-btn" onClick={() => act(() => api.put(`/api/goals/${g.id}/cancel`), `Cancel the goal "${g.title}"?`)}>Cancel goal</button>
                  )}
                </div>
                <div className="card-actions">
                  {g.status !== 'CANCELLED' && <button className="link" onClick={() => open('goal', g)}>Edit</button>}
                  <button className="link danger" onClick={() => act(() => api.delete(`/api/goals/${g.id}`), `Delete the goal "${g.title}"?`)}>Delete</button>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {dialog && (
        <Modal title={dialog.kind === 'contribute' ? `Add money: ${dialog.goal.title}` : dialog.goal ? 'Edit goal' : 'New goal'} onClose={() => setDialog(null)}>
          <form onSubmit={submit}>
            {dialog.kind === 'goal' ? (
              <>
                <label>Goal<input required maxLength={100} value={form.title} onChange={set('title')} placeholder="e.g. New laptop" /></label>
                <div className="row">
                  <label>Target amount<input type="number" required min="0.01" step="0.01" value={form.target} onChange={set('target')} /></label>
                  <label>Deadline (optional)<input type="date" value={form.deadline} onChange={set('deadline')} /></label>
                </div>
              </>
            ) : (
              <>
                <label>Amount<input type="number" required min="0.01" step="0.01" value={form.amount} onChange={set('amount')} /></label>
                <p className="muted small">{money(Math.max(0, Number(dialog.goal.targetAmount) - Number(dialog.goal.currentAmount)))} to go.</p>
              </>
            )}
            {formError && <div className="alert">{formError}</div>}
            <div className="modal-actions">
              <button type="button" className="btn ghost" onClick={() => setDialog(null)}>Cancel</button>
              <button className="btn" disabled={saving}>{saving ? 'Saving...' : 'Save'}</button>
            </div>
          </form>
        </Modal>
      )}
    </>
  );
}
