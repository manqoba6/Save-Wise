import { useEffect, useState } from 'react';
import api, { errorMessage } from '../api/client.js';
import { useSettings } from '../context/SettingsContext.jsx';
import Modal from '../components/Modal.jsx';

const pad = (n) => String(n).padStart(2, '0');
const thisMonth = () => { const d = new Date(); return `${d.getFullYear()}-${pad(d.getMonth() + 1)}`; };
const monthStart = (m) => `${m}-01`;
const previousMonth = (m) => { const [y, mo] = m.split('-').map(Number); const d = new Date(y, mo - 2, 1); return `${d.getFullYear()}-${pad(d.getMonth() + 1)}`; };

const STATUS = {
  OK: { label: 'On track', tone: 'good' },
  WARNING: { label: 'Near limit', tone: 'warn' },
  CRITICAL: { label: 'Over budget', tone: 'bad' },
};

export default function Budgets() {
  const { money } = useSettings();
  const [month, setMonth] = useState(thisMonth());
  const [budgets, setBudgets] = useState(null);
  const [categories, setCategories] = useState([]);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [reload, setReload] = useState(0);

  const [editing, setEditing] = useState(null);   // null = closed, {} = new, {id,...} = editing
  const [form, setForm] = useState({ categoryId: '', amount: '' });
  const [formError, setFormError] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    api.get('/api/categories', { params: { type: 'EXPENSE' } }).then(({ data }) => setCategories(data)).catch((e) => setError(errorMessage(e)));
  }, []);

  useEffect(() => {
    api.get('/api/budgets', { params: { month: monthStart(month) } })
      .then(({ data }) => { setBudgets(data); setError(''); })
      .catch((e) => setError(errorMessage(e)));
  }, [month, reload]);

  const changeMonth = (m) => { if (m) { setMonth(m); setBudgets(null); setNotice(''); } };

  // when adding, only offer categories that do not have a budget this month yet
  const choices = editing?.id ? categories : categories.filter((c) => !(budgets || []).some((b) => b.categoryId === c.id));

  function openNew() { setForm({ categoryId: '', amount: '' }); setFormError(''); setEditing({}); }
  function openEdit(b) { setForm({ categoryId: String(b.categoryId), amount: String(b.budgetAmount) }); setFormError(''); setEditing(b); }

  async function save(e) {
    e.preventDefault();
    setSaving(true); setFormError('');
    const body = {
      categoryId: Number(form.categoryId),
      budgetMonth: editing.id ? editing.budgetMonth : monthStart(month),
      budgetAmount: Number(form.amount),
    };
    try {
      if (editing.id) await api.put(`/api/budgets/${editing.id}`, body);
      else await api.post('/api/budgets', body);
      setEditing(null);
      setReload((n) => n + 1);
    } catch (err) { setFormError(errorMessage(err)); }
    finally { setSaving(false); }
  }

  async function remove(b) {
    if (!window.confirm(`Delete the ${b.categoryName} budget?`)) return;
    try { await api.delete(`/api/budgets/${b.id}`); setReload((n) => n + 1); }
    catch (err) { setError(errorMessage(err)); }
  }

  async function copyLastMonth() {
    setNotice(''); setError('');
    try {
      const { data: previous } = await api.get('/api/budgets', { params: { month: monthStart(previousMonth(month)) } });
      const have = new Set(budgets.map((b) => b.categoryId));
      const toCopy = previous.filter((b) => !have.has(b.categoryId));
      if (toCopy.length === 0) { setNotice('Nothing to copy: last month has no budgets that are missing here.'); return; }
      await Promise.all(toCopy.map((b) => api.post('/api/budgets',
        { categoryId: b.categoryId, budgetMonth: monthStart(month), budgetAmount: b.budgetAmount })));
      setNotice(`Copied ${toCopy.length} budget${toCopy.length === 1 ? '' : 's'} from last month.`);
      setReload((n) => n + 1);
    } catch (err) { setError(errorMessage(err)); }
  }

  const total = (key) => (budgets || []).reduce((sum, b) => sum + Number(b[key]), 0);

  return (
    <>
      <div className="page-head">
        <h1>Budgets</h1>
        <div className="head-actions">
          <input type="month" value={month} onChange={(e) => changeMonth(e.target.value)} />
          <button className="btn ghost" onClick={copyLastMonth} disabled={!budgets}>Copy last month</button>
          <button className="btn" onClick={openNew} disabled={!budgets}>+ Add budget</button>
        </div>
      </div>

      {error && <div className="alert">{error}</div>}
      {notice && <div className="notice spaced">{notice}</div>}

      {budgets && budgets.length > 0 && (
        <div className="grid stats">
          <div className="card stat"><span className="muted">Budgeted</span><strong>{money(total('budgetAmount'))}</strong></div>
          <div className="card stat"><span className="muted">Spent</span><strong className="bad">{money(total('spent'))}</strong></div>
          <div className="card stat"><span className="muted">Left to spend</span><strong className="good">{money(total('budgetAmount') - total('spent'))}</strong></div>
        </div>
      )}

      {!budgets ? <p className="muted">Loading...</p> : budgets.length === 0 ? (
        <div className="card"><p className="muted">No budgets for this month yet. Add one, or copy last month's.</p></div>
      ) : (
        <div className="grid budgets">
          {budgets.map((b) => {
            const st = STATUS[b.status] || STATUS.OK;
            const over = Number(b.remaining) < 0;
            return (
              <div className="card budget" key={b.id}>
                <div className="between"><strong>{b.categoryName}</strong><span className={`pill ${st.tone}`}>{st.label}</span></div>
                <div className="bar big"><div className={`fill ${st.tone}`} style={{ width: `${Math.min(100, Number(b.percentUsed) || 0)}%` }} /></div>
                <div className="between small">
                  <span>{money(b.spent)} of {money(b.budgetAmount)}</span>
                  <span>{b.percentUsed}%</span>
                </div>
                <div className={`small ${over ? 'bad' : 'muted'}`}>{over ? `Over by ${money(-Number(b.remaining))}` : `${money(b.remaining)} left`}</div>
                <div className="card-actions">
                  <button className="link" onClick={() => openEdit(b)}>Edit</button>
                  <button className="link danger" onClick={() => remove(b)}>Delete</button>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {editing && (
        <Modal title={editing.id ? 'Edit budget' : 'Add budget'} onClose={() => setEditing(null)}>
          <form onSubmit={save}>
            <label>Category
              <select required value={form.categoryId} onChange={(e) => setForm({ ...form, categoryId: e.target.value })}>
                <option value="" disabled>Choose a category</option>
                {choices.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            </label>
            <label>Monthly budget
              <input type="number" required min="0.01" step="0.01" value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })} />
            </label>
            <p className="muted small">You will be warned at 80% and again at 100% of this amount.</p>
            {formError && <div className="alert">{formError}</div>}
            <div className="modal-actions">
              <button type="button" className="btn ghost" onClick={() => setEditing(null)}>Cancel</button>
              <button className="btn" disabled={saving}>{saving ? 'Saving...' : 'Save'}</button>
            </div>
          </form>
        </Modal>
      )}
    </>
  );
}
