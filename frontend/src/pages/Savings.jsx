import { useEffect, useState } from 'react';
import api, { errorMessage } from '../api/client.js';
import { useSettings } from '../context/SettingsContext.jsx';
import Modal from '../components/Modal.jsx';
import { today } from '../utils.js';

const PAGE_SIZE = 10;
const TONE = { DEPOSIT: 'good', WITHDRAW: 'bad', TRANSFER: 'info' };
const SIGN = { DEPOSIT: '+', WITHDRAW: '-', TRANSFER: '' };
const TITLES = { account: 'Savings account', deposit: 'Deposit', withdraw: 'Withdraw', transfer: 'Transfer' };
const blank = () => ({ name: '', target: '', amount: '', description: '', date: today(), to: '' });

export default function Savings() {
  const { money } = useSettings();
  const [accounts, setAccounts] = useState(null);
  const [history, setHistory] = useState(null);
  const [historyAccount, setHistoryAccount] = useState('');
  const [page, setPage] = useState(0);
  const [error, setError] = useState('');
  const [reload, setReload] = useState(0);

  const [dialog, setDialog] = useState(null);      // { kind, account }
  const [form, setForm] = useState(blank());
  const [formError, setFormError] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    api.get('/api/savings/accounts').then(({ data }) => { setAccounts(data); setError(''); }).catch((e) => setError(errorMessage(e)));
  }, [reload]);

  useEffect(() => {
    const params = { page, size: PAGE_SIZE };
    if (historyAccount) params.accountId = historyAccount;
    api.get('/api/savings/history', { params }).then(({ data }) => setHistory(data)).catch((e) => setError(errorMessage(e)));
  }, [historyAccount, page, reload]);

  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value });
  const total = (accounts || []).reduce((sum, a) => sum + Number(a.currentBalance), 0);

  function open(kind, account = null) {
    setForm(kind === 'account' && account
      ? { ...blank(), name: account.accountName, target: account.targetAmount == null ? '' : String(account.targetAmount) }
      : blank());
    setFormError('');
    setDialog({ kind, account });
  }

  async function submit(e) {
    e.preventDefault();
    setSaving(true); setFormError('');
    const { kind, account } = dialog;
    const note = form.description.trim() || null;
    try {
      if (kind === 'account') {
        const body = { accountName: form.name.trim(), targetAmount: form.target ? Number(form.target) : null };
        if (account) await api.put(`/api/savings/accounts/${account.id}`, body);
        else await api.post('/api/savings/accounts', body);
      } else if (kind === 'transfer') {
        await api.post('/api/savings/transfer', {
          fromAccountId: account.id, toAccountId: Number(form.to), amount: Number(form.amount),
          description: note, transactionDate: form.date,
        });
      } else {
        await api.post(`/api/savings/${kind}`, {
          accountId: account.id, amount: Number(form.amount), description: note, transactionDate: form.date,
        });
      }
      setDialog(null); setPage(0); setReload((n) => n + 1);
    } catch (err) { setFormError(errorMessage(err)); }
    finally { setSaving(false); }
  }

  async function removeAccount(a) {
    if (!window.confirm(`Delete the "${a.accountName}" account?`)) return;
    try { await api.delete(`/api/savings/accounts/${a.id}`); setHistoryAccount(''); setReload((n) => n + 1); }
    catch (err) { setError(errorMessage(err)); }
  }

  const isMoney = dialog && ['deposit', 'withdraw', 'transfer'].includes(dialog.kind);

  return (
    <>
      <div className="page-head">
        <h1>Savings</h1>
        <button className="btn" onClick={() => open('account')}>+ New account</button>
      </div>

      {error && <div className="alert">{error}</div>}

      {accounts && accounts.length > 0 && (
        <div className="card stat spaced"><span className="muted">Total saved</span><strong className="good">{money(total)}</strong></div>
      )}

      {!accounts ? <p className="muted">Loading...</p> : accounts.length === 0 ? (
        <div className="card"><p className="muted">No savings accounts yet. Create one (an emergency fund, a holiday pot) and start putting money aside.</p></div>
      ) : (
        <div className="grid budgets">
          {accounts.map((a) => {
            const pct = a.targetAmount ? Math.min(100, (Number(a.currentBalance) / Number(a.targetAmount)) * 100) : null;
            return (
              <div className="card budget" key={a.id}>
                <strong>{a.accountName}</strong>
                <div className="big-number">{money(a.currentBalance)}</div>
                {pct !== null && (
                  <>
                    <div className="bar big"><div className="fill good" style={{ width: `${pct}%` }} /></div>
                    <div className="small muted">{Math.round(pct)}% of {money(a.targetAmount)} target</div>
                  </>
                )}
                <div className="btn-row">
                  <button className="btn small-btn" onClick={() => open('deposit', a)}>Deposit</button>
                  <button className="btn ghost small-btn" onClick={() => open('withdraw', a)}>Withdraw</button>
                  {accounts.length > 1 && <button className="btn ghost small-btn" onClick={() => open('transfer', a)}>Transfer</button>}
                </div>
                <div className="card-actions">
                  <button className="link" onClick={() => open('account', a)}>Edit</button>
                  <button className="link danger" onClick={() => removeAccount(a)}>Delete</button>
                </div>
              </div>
            );
          })}
        </div>
      )}

      <div className="page-head sub">
        <h2>History</h2>
        {accounts && accounts.length > 1 && (
          <select aria-label="Filter history by account" value={historyAccount} onChange={(e) => { setHistoryAccount(e.target.value); setPage(0); }}>
            <option value="">All accounts</option>
            {accounts.map((a) => <option key={a.id} value={a.id}>{a.accountName}</option>)}
          </select>
        )}
      </div>
      <div className="card table-card">
        {!history ? <p className="muted pad">Loading...</p> : history.content.length === 0 ? (
          <p className="muted pad">No savings activity yet.</p>
        ) : (
          <div className="scroll">
            <table>
              <thead><tr><th>Date</th><th>Account</th><th>Type</th><th>Description</th><th className="right">Amount</th></tr></thead>
              <tbody>
                {history.content.map((t) => (
                  <tr key={t.id}>
                    <td>{t.transactionDate}</td>
                    <td>{t.accountName}</td>
                    <td><span className={`pill ${TONE[t.transactionType]}`}>{t.transactionType}</span></td>
                    <td className="wrap">{t.description || <span className="muted">&mdash;</span>}</td>
                    <td className={`right ${TONE[t.transactionType] === 'info' ? '' : TONE[t.transactionType]}`}>{SIGN[t.transactionType]}{money(t.amount)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {history && history.totalPages > 1 && (
          <div className="pager">
            <span className="muted">Page {history.page + 1} of {history.totalPages}</span>
            <span>
              <button className="btn ghost" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>{' '}
              <button className="btn ghost" disabled={page + 1 >= history.totalPages} onClick={() => setPage(page + 1)}>Next</button>
            </span>
          </div>
        )}
      </div>

      {dialog && (
        <Modal title={dialog.kind === 'account' ? (dialog.account ? 'Edit account' : 'New savings account') : `${TITLES[dialog.kind]}: ${dialog.account.accountName}`}
               onClose={() => setDialog(null)}>
          <form onSubmit={submit}>
            {dialog.kind === 'account' && (
              <>
                <label>Account name<input required maxLength={100} value={form.name} onChange={set('name')} placeholder="e.g. Emergency fund" /></label>
                <label>Target amount (optional)<input type="number" min="0.01" step="0.01" value={form.target} onChange={set('target')} /></label>
              </>
            )}
            {dialog.kind === 'transfer' && (
              <label>Move to
                <select required value={form.to} onChange={set('to')}>
                  <option value="" disabled>Choose an account</option>
                  {accounts.filter((a) => a.id !== dialog.account.id).map((a) => <option key={a.id} value={a.id}>{a.accountName}</option>)}
                </select>
              </label>
            )}
            {isMoney && (
              <>
                <div className="row">
                  <label>Amount<input type="number" required min="0.01" step="0.01" value={form.amount} onChange={set('amount')} /></label>
                  <label>Date<input type="date" required value={form.date} onChange={set('date')} /></label>
                </div>
                {dialog.kind !== 'deposit' && <p className="muted small">Available: {money(dialog.account.currentBalance)}</p>}
                <label>Note<input maxLength={200} value={form.description} onChange={set('description')} placeholder="Optional" /></label>
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
