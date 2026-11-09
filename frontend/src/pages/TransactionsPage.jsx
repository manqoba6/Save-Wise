import { useCallback, useEffect, useState } from 'react';
import api, { errorMessage } from '../api/client.js';
import { useSettings } from '../context/SettingsContext.jsx';
import Modal from '../components/Modal.jsx';
import { useNotifications } from '../context/NotificationsContext.jsx';

const PAGE_SIZE = 10;
const PAYMENT_METHODS = ['CASH', 'CARD', 'EFT', 'OTHER'];

// The only differences between the income and expense pages.
const CONFIG = {
  income: {
    title: 'Income', noun: 'income', endpoint: '/api/income', categoryType: 'INCOME',
    dateField: 'incomeDate', payment: false, sign: '+', tone: 'good', empty: 'No income recorded yet.',
  },
  expense: {
    title: 'Expenses', noun: 'expense', endpoint: '/api/expenses', categoryType: 'EXPENSE',
    dateField: 'expenseDate', payment: true, sign: '-', tone: 'bad', empty: 'No expenses recorded yet.',
  },
};

const pad = (n) => String(n).padStart(2, '0');
const today = () => { const d = new Date(); return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`; };
const blankForm = () => ({ categoryId: '', amount: '', description: '', paymentMethod: '', date: today() });
const NO_FILTERS = { q: '', categoryId: '', from: '', to: '' };

export default function TransactionsPage({ kind }) {
  const cfg = CONFIG[kind];
  const { money } = useSettings();
  const { refresh: refreshNotifications } = useNotifications();

  const [categories, setCategories] = useState([]);
  const [filters, setFilters] = useState(NO_FILTERS);
  const [search, setSearch] = useState('');        // what the box shows; `filters.q` follows after a short pause
  const [page, setPage] = useState(0);
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const [reload, setReload] = useState(0);

  const [editing, setEditing] = useState(null);    // null = closed, {} = new, {id,...} = editing
  const [form, setForm] = useState(blankForm());
  const [formError, setFormError] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    api.get('/api/categories', { params: { type: cfg.categoryType } })
      .then(({ data }) => setCategories(data)).catch((e) => setError(errorMessage(e)));
  }, [cfg.categoryType]);

  // wait for the user to stop typing before searching
  useEffect(() => {
    const t = setTimeout(() => { setFilters((f) => (f.q === search ? f : { ...f, q: search })); setPage(0); }, 300);
    return () => clearTimeout(t);
  }, [search]);

  useEffect(() => {
    const params = { page, size: PAGE_SIZE };
    Object.entries(filters).forEach(([k, v]) => { if (v) params[k] = v; });
    api.get(cfg.endpoint, { params })
      .then(({ data }) => { setResult(data); setError(''); })
      .catch((e) => setError(errorMessage(e)));
  }, [cfg.endpoint, filters, page, reload]);

  const setFilter = (key) => (e) => { setFilters({ ...filters, [key]: e.target.value }); setPage(0); };
  const clearFilters = () => { setFilters(NO_FILTERS); setSearch(''); setPage(0); };
  const hasFilters = Object.values(filters).some(Boolean);

  const openNew = useCallback(() => { setForm(blankForm()); setFormError(''); setEditing({}); }, []);
  const openEdit = (row) => {
    setForm({
      categoryId: String(row.categoryId), amount: String(row.amount), description: row.description || '',
      paymentMethod: row.paymentMethod || '', date: row[cfg.dateField],
    });
    setFormError(''); setEditing(row);
  };

  async function save(e) {
    e.preventDefault();
    setSaving(true); setFormError('');
    const body = {
      categoryId: Number(form.categoryId), amount: Number(form.amount),
      description: form.description.trim() || null, [cfg.dateField]: form.date,
    };
    if (cfg.payment) body.paymentMethod = form.paymentMethod || null;
    try {
      if (editing.id) await api.put(`${cfg.endpoint}/${editing.id}`, body);
      else await api.post(cfg.endpoint, body);
      setEditing(null);
      setReload((n) => n + 1);
      refreshNotifications(); // a new expense may have just tripped a budget alert
    } catch (err) { setFormError(errorMessage(err)); }
    finally { setSaving(false); }
  }

  async function remove(row) {
    if (!window.confirm(`Delete this ${cfg.noun} of ${money(row.amount)}?`)) return;
    try {
      await api.delete(`${cfg.endpoint}/${row.id}`);
      if (result.content.length === 1 && page > 0) setPage(page - 1); else setReload((n) => n + 1);
    } catch (err) { setError(errorMessage(err)); }
  }

  return (
    <>
      <div className="page-head">
        <h1>{cfg.title}</h1>
        <button className="btn" onClick={openNew}>+ Add {cfg.noun}</button>
      </div>

      <div className="card toolbar">
        <input type="search" placeholder="Search descriptions..." value={search} onChange={(e) => setSearch(e.target.value)} />
        <select value={filters.categoryId} onChange={setFilter('categoryId')}>
          <option value="">All categories</option>
          {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
        </select>
        <label className="inline">From <input type="date" value={filters.from} onChange={setFilter('from')} /></label>
        <label className="inline">To <input type="date" value={filters.to} onChange={setFilter('to')} /></label>
        {hasFilters && <button className="btn ghost" onClick={clearFilters}>Clear</button>}
      </div>

      {error && <div className="alert">{error}</div>}

      <div className="card table-card">
        {!result ? <p className="muted pad">Loading...</p> : result.content.length === 0 ? (
          <p className="muted pad">{hasFilters ? 'Nothing matches those filters.' : cfg.empty}</p>
        ) : (
          <div className="scroll">
            <table>
              <thead>
                <tr><th>Date</th><th>Category</th><th>Description</th>{cfg.payment && <th>Paid by</th>}<th className="right">Amount</th><th /></tr>
              </thead>
              <tbody>
                {result.content.map((row) => (
                  <tr key={row.id}>
                    <td>{row[cfg.dateField]}</td>
                    <td>{row.categoryName}</td>
                    <td className="wrap">{row.description || <span className="muted">&mdash;</span>}</td>
                    {cfg.payment && <td>{row.paymentMethod || <span className="muted">&mdash;</span>}</td>}
                    <td className={`right ${cfg.tone}`}>{cfg.sign}{money(row.amount)}</td>
                    <td className="actions">
                      <button className="link" onClick={() => openEdit(row)}>Edit</button>
                      <button className="link danger" onClick={() => remove(row)}>Delete</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {result && result.totalElements > 0 && (
          <div className="pager">
            <span className="muted">{result.totalElements} record{result.totalElements === 1 ? '' : 's'} &middot; page {result.page + 1} of {result.totalPages}</span>
            <span>
              <button className="btn ghost" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>{' '}
              <button className="btn ghost" disabled={page + 1 >= result.totalPages} onClick={() => setPage(page + 1)}>Next</button>
            </span>
          </div>
        )}
      </div>

      {editing && (
        <Modal title={`${editing.id ? 'Edit' : 'Add'} ${cfg.noun}`} onClose={() => setEditing(null)}>
          <form onSubmit={save}>
            <label>Category
              <select required value={form.categoryId} onChange={(e) => setForm({ ...form, categoryId: e.target.value })}>
                <option value="" disabled>Choose a category</option>
                {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            </label>
            <div className="row">
              <label>Amount
                <input type="number" required min="0.01" step="0.01" value={form.amount}
                  onChange={(e) => setForm({ ...form, amount: e.target.value })} />
              </label>
              <label>Date
                <input type="date" required value={form.date} onChange={(e) => setForm({ ...form, date: e.target.value })} />
              </label>
            </div>
            {cfg.payment && (
              <label>Paid by
                <select value={form.paymentMethod} onChange={(e) => setForm({ ...form, paymentMethod: e.target.value })}>
                  <option value="">Not specified</option>
                  {PAYMENT_METHODS.map((m) => <option key={m} value={m}>{m}</option>)}
                </select>
              </label>
            )}
            <label>Description
              <input value={form.description} maxLength={200} placeholder="Optional" onChange={(e) => setForm({ ...form, description: e.target.value })} />
            </label>
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
