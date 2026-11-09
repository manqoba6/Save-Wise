import { useEffect, useState } from 'react';
import api, { errorMessage } from '../api/client.js';
import useFetch from '../hooks/useFetch.js';
import { useSettings } from '../context/SettingsContext.jsx';
import Modal from '../components/Modal.jsx';
import { monthlyEquivalent, today } from '../utils.js';

const FREQUENCIES = [['DAILY', 'Every day'], ['WEEKLY', 'Every week'], ['MONTHLY', 'Every month'], ['YEARLY', 'Every year']];
const FREQ_LABEL = Object.fromEntries(FREQUENCIES);
const STATUS = { ACTIVE: ['Active', 'good'], PAUSED: ['Paused', 'warn'], ENDED: ['Ended', 'muted'] };
const TABS = [['', 'All'], ['ACTIVE', 'Active'], ['PAUSED', 'Paused'], ['ENDED', 'Ended']];
const blank = () => ({ type: 'EXPENSE', categoryId: '', amount: '', frequency: 'MONTHLY', startDate: today(), endDate: '', description: '' });

function HistoryModal({ schedule, onClose }) {
  const { money } = useSettings();
  const [page, setPage] = useState(0);
  const state = useFetch(`/api/recurring-transactions/${schedule.id}/history`, { page, size: 10 });
  const sign = schedule.transactionType === 'INCOME' ? '+' : '-';

  return (
    <Modal title={`Posted so far: ${schedule.description || schedule.categoryName}`} onClose={onClose}>
      {state.loading ? <p className="muted">Loading...</p> : state.error ? <div className="alert">{state.error}</div> : (
        state.data.content.length === 0 ? (
          <p className="muted">Nothing posted yet. The first posting happens on the next run date.</p>
        ) : (
          <>
            <table>
              <thead><tr><th>Date</th><th>Description</th><th className="right">Amount</th></tr></thead>
              <tbody>
                {state.data.content.map((r) => (
                  <tr key={r.recordId}><td>{r.date}</td><td className="wrap">{r.description}</td><td className="right">{sign}{money(r.amount)}</td></tr>
                ))}
              </tbody>
            </table>
            {state.data.totalPages > 1 && (
              <div className="pager">
                <span className="muted">Page {state.data.page + 1} of {state.data.totalPages}</span>
                <span>
                  <button className="btn ghost" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>{' '}
                  <button className="btn ghost" disabled={page + 1 >= state.data.totalPages} onClick={() => setPage(page + 1)}>Next</button>
                </span>
              </div>
            )}
          </>
        )
      )}
    </Modal>
  );
}

export default function Recurring() {
  const { money } = useSettings();
  const [items, setItems] = useState(null);
  const [categories, setCategories] = useState([]);
  const [tab, setTab] = useState('');
  const [error, setError] = useState('');
  const [reload, setReload] = useState(0);

  const [editing, setEditing] = useState(null);     // null = closed, {} = new, {id,...} = editing
  const [form, setForm] = useState(blank());
  const [formError, setFormError] = useState('');
  const [saving, setSaving] = useState(false);
  const [historyFor, setHistoryFor] = useState(null);

  useEffect(() => {
    api.get('/api/categories').then(({ data }) => setCategories(data)).catch((e) => setError(errorMessage(e)));
  }, []);

  useEffect(() => {
    api.get('/api/recurring-transactions').then(({ data }) => { setItems(data); setError(''); }).catch((e) => setError(errorMessage(e)));
  }, [reload]);

  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value });
  const categoryChoices = categories.filter((c) => c.type === form.type);
  const visible = (items || []).filter((i) => !tab || i.status === tab);

  // what the active schedules add up to in a typical month
  const monthly = (type) => (items || []).filter((i) => i.status === 'ACTIVE' && i.transactionType === type)
    .reduce((sum, i) => sum + monthlyEquivalent(i.amount, i.frequency), 0);

  function openNew() { setForm(blank()); setFormError(''); setEditing({}); }
  function openEdit(item) {
    setForm({
      type: item.transactionType, categoryId: String(item.categoryId), amount: String(item.amount), frequency: item.frequency,
      startDate: item.startDate, endDate: item.endDate || '', description: item.description || '',
    });
    setFormError(''); setEditing(item);
  }

  async function save(e) {
    e.preventDefault();
    setSaving(true); setFormError('');
    const body = {
      transactionType: form.type, categoryId: Number(form.categoryId), amount: Number(form.amount), frequency: form.frequency,
      startDate: form.startDate, endDate: form.endDate || null, description: form.description.trim() || null,
    };
    try {
      if (editing.id) await api.put(`/api/recurring-transactions/${editing.id}`, body);
      else await api.post('/api/recurring-transactions', body);
      setEditing(null); setReload((n) => n + 1);
    } catch (err) { setFormError(errorMessage(err)); }
    finally { setSaving(false); }
  }

  async function act(request, confirmText) {
    if (confirmText && !window.confirm(confirmText)) return;
    setError('');
    try { await request(); setReload((n) => n + 1); }
    catch (err) { setError(errorMessage(err)); }
  }

  const dayOfMonth = Number(form.startDate.slice(8, 10));

  return (
    <>
      <div className="page-head">
        <h1>Recurring</h1>
        <button className="btn" onClick={openNew}>+ New recurring</button>
      </div>
      <p className="muted spaced">Salary, rent, subscriptions: set them up once and SaveWise records them for you every night. If the app was switched off for a few days, anything missed is caught up.</p>

      {error && <div className="alert">{error}</div>}

      {items && items.some((i) => i.status === 'ACTIVE') && (
        <div className="grid stats">
          <div className="card stat"><span className="muted">Income a month (approx.)</span><strong className="good">{money(monthly('INCOME'))}</strong></div>
          <div className="card stat"><span className="muted">Expenses a month (approx.)</span><strong className="bad">{money(monthly('EXPENSE'))}</strong></div>
        </div>
      )}

      <div className="tabs">
        {TABS.map(([value, label]) => <button key={label} className={tab === value ? 'tab active' : 'tab'} onClick={() => setTab(value)}>{label}</button>)}
      </div>

      {!items ? <p className="muted">Loading...</p> : visible.length === 0 ? (
        <div className="card"><p className="muted">{items.length === 0 ? 'No recurring transactions yet. Add your salary or rent to get started.' : 'Nothing here.'}</p></div>
      ) : (
        <div className="grid budgets">
          {visible.map((i) => {
            const [statusLabel, statusTone] = STATUS[i.status] || STATUS.ACTIVE;
            const income = i.transactionType === 'INCOME';
            return (
              <div className="card budget" key={i.id}>
                <div className="between"><strong>{i.description || i.categoryName}</strong><span className={`pill ${statusTone}`}>{statusLabel}</span></div>
                <div className={`big-number ${income ? 'good' : 'bad'}`}>{income ? '+' : '-'}{money(i.amount)}</div>
                <div className="small">{FREQ_LABEL[i.frequency]} &middot; <span className="muted">{i.categoryName}</span></div>
                <div className="small muted">
                  {i.status === 'ACTIVE' ? `Next: ${i.nextExecution}` : i.status === 'PAUSED' ? 'Paused: nothing will post until you resume' : 'Finished'}
                  {` · from ${i.startDate}`}{i.endDate ? ` to ${i.endDate}` : ''}
                </div>
                <div className="btn-row">
                  {i.status === 'ACTIVE' && <button className="btn ghost small-btn" onClick={() => act(() => api.put(`/api/recurring-transactions/${i.id}/pause`))}>Pause</button>}
                  {i.status === 'PAUSED' && <button className="btn small-btn" onClick={() => act(() => api.put(`/api/recurring-transactions/${i.id}/resume`))}>Resume</button>}
                  <button className="btn ghost small-btn" onClick={() => setHistoryFor(i)}>History</button>
                </div>
                <div className="card-actions">
                  <button className="link" onClick={() => openEdit(i)}>Edit</button>
                  <button className="link danger" onClick={() => act(() => api.delete(`/api/recurring-transactions/${i.id}`),
                    'Delete this schedule? Records it has already created are kept.')}>Delete</button>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {editing && (
        <Modal title={editing.id ? 'Edit recurring transaction' : 'New recurring transaction'} onClose={() => setEditing(null)}>
          <form onSubmit={save}>
            <fieldset className="choices">
              <legend>Type</legend>
              {[['EXPENSE', 'Expense'], ['INCOME', 'Income']].map(([value, label]) => (
                <label key={value} className="choice">
                  <input type="radio" name="type" value={value} checked={form.type === value}
                    onChange={() => setForm({ ...form, type: value, categoryId: '' })} />{label}
                </label>
              ))}
            </fieldset>
            <label>Category
              <select required value={form.categoryId} onChange={set('categoryId')}>
                <option value="" disabled>Choose a category</option>
                {categoryChoices.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            </label>
            <div className="row">
              <label>Amount<input type="number" required min="0.01" step="0.01" value={form.amount} onChange={set('amount')} /></label>
              <label>How often
                <select value={form.frequency} onChange={set('frequency')}>
                  {FREQUENCIES.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                </select>
              </label>
            </div>
            <div className="row">
              <label>First posting<input type="date" required value={form.startDate} onChange={set('startDate')} /></label>
              <label>Stop after (optional)<input type="date" min={form.startDate} value={form.endDate} onChange={set('endDate')} /></label>
            </div>
            {form.frequency === 'MONTHLY' && dayOfMonth >= 29 && (
              <p className="muted small">In months with fewer than {dayOfMonth} days this posts on the last day of the month.</p>
            )}
            <label>Description<input maxLength={200} value={form.description} placeholder="e.g. Rent, Salary, Netflix" onChange={set('description')} /></label>
            {formError && <div className="alert">{formError}</div>}
            <div className="modal-actions">
              <button type="button" className="btn ghost" onClick={() => setEditing(null)}>Cancel</button>
              <button className="btn" disabled={saving}>{saving ? 'Saving...' : 'Save'}</button>
            </div>
          </form>
        </Modal>
      )}

      {historyFor && <HistoryModal schedule={historyFor} onClose={() => setHistoryFor(null)} />}
    </>
  );
}
