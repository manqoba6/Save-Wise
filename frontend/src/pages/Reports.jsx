import { useState } from 'react';
import { Bar, Doughnut } from 'react-chartjs-2';
import '../chartSetup.js';
import api, { errorMessage } from '../api/client.js';
import useFetch from '../hooks/useFetch.js';
import { useSettings } from '../context/SettingsContext.jsx';
import { downloadBlob, today } from '../utils.js';

const TYPES = [['DAILY', 'Daily'], ['WEEKLY', 'Weekly'], ['MONTHLY', 'Monthly'], ['ANNUAL', 'Annual']];
const MAX_ROWS = 100;
const PALETTE = ['#4f7cff', '#2e9e6b', '#e0603d', '#f2b134', '#9b59d0', '#22b8cf', '#e6679a', '#7f8c8d'];

/** The backend sends error bodies as JSON; when we asked for a file they arrive as a Blob, so read the message out of it. */
async function exportError(err) {
  try {
    const text = await err.response.data.text();
    return JSON.parse(text).message || errorMessage(err);
  } catch { return errorMessage(err); }
}

function fileNameFrom(response, fallback) {
  const match = /filename="?([^";]+)"?/.exec(response.headers?.['content-disposition'] || '');
  return match ? match[1] : fallback;
}

function CategoryTable({ title, rows, money }) {
  return (
    <section className="card table-card">
      <h2 className="pad">{title}</h2>
      {rows.length === 0 ? <p className="muted pad">Nothing in this period.</p> : (
        <table>
          <thead><tr><th>Category</th><th className="right">Amount</th><th className="right">Share</th></tr></thead>
          <tbody>{rows.map((r) => (
            <tr key={r.categoryName}><td>{r.categoryName}</td><td className="right">{money(r.amount)}</td><td className="right muted">{r.percentOfTotal}%</td></tr>
          ))}</tbody>
        </table>
      )}
    </section>
  );
}

export default function Reports() {
  const { money } = useSettings();
  const [type, setType] = useState('MONTHLY');
  const [ref, setRef] = useState(today());          // any date inside the period; the backend works out the rest
  const [exporting, setExporting] = useState('');
  const [exportErr, setExportErr] = useState('');
  const state = useFetch('/api/reports', { type, date: ref });

  const thisYear = new Date().getFullYear();
  const years = Array.from({ length: 10 }, (_, i) => thisYear - i);

  async function download(format) {
    setExporting(format); setExportErr('');
    try {
      const res = await api.get('/api/reports/export', { params: { type, date: ref, format }, responseType: 'blob' });
      downloadBlob(res.data, fileNameFrom(res, `savewise-report.${format}`));
    } catch (err) { setExportErr(await exportError(err)); }
    finally { setExporting(''); }
  }

  return (
    <>
      <div className="page-head">
        <h1>Reports</h1>
        <div className="head-actions">
          <button className="btn ghost" disabled={!!exporting} onClick={() => download('csv')}>{exporting === 'csv' ? 'Preparing...' : 'Download CSV'}</button>
          <button className="btn" disabled={!!exporting} onClick={() => download('pdf')}>{exporting === 'pdf' ? 'Preparing...' : 'Download PDF'}</button>
        </div>
      </div>

      <div className="report-controls">
        <div className="tabs">
          {TYPES.map(([value, label]) => <button key={value} className={type === value ? 'tab active' : 'tab'} onClick={() => setType(value)}>{label}</button>)}
        </div>
        {type === 'MONTHLY' && <input type="month" aria-label="Month" value={ref.slice(0, 7)} onChange={(e) => e.target.value && setRef(`${e.target.value}-01`)} />}
        {type === 'ANNUAL' && (
          <select aria-label="Year" value={ref.slice(0, 4)} onChange={(e) => setRef(`${e.target.value}-01-01`)}>
            {years.map((y) => <option key={y} value={y}>{y}</option>)}
          </select>
        )}
        {(type === 'DAILY' || type === 'WEEKLY') && <input type="date" aria-label="Date" value={ref} onChange={(e) => e.target.value && setRef(e.target.value)} />}
      </div>
      {exportErr && <div className="alert spaced">{exportErr}</div>}

      {state.loading ? <p className="muted">Building your report...</p> : state.error ? <div className="alert">{state.error}</div> : (() => {
        const r = state.data;
        const s = r.summary;
        const shown = r.transactions.slice(0, MAX_ROWS);
        return (
          <>
            <p className="muted spaced">{r.label}{r.from !== r.to && ` (${r.from} to ${r.to})`}</p>

            <div className="grid stats">
              <div className="card stat"><span className="muted">Income</span><strong className="good">{money(s.totalIncome)}</strong></div>
              <div className="card stat"><span className="muted">Expenses</span><strong className="bad">{money(s.totalExpenses)}</strong></div>
              <div className="card stat"><span className="muted">Net</span><strong className={Number(s.net) < 0 ? 'bad' : 'good'}>{money(s.net)}</strong></div>
              <div className="card stat"><span className="muted">Savings rate</span><strong>{s.savingsRatePercent == null ? 'n/a' : `${s.savingsRatePercent}%`}</strong></div>
            </div>
            <p className="muted small spaced">Moved into savings: {money(s.savingsDeposits)} &middot; taken out: {money(s.savingsWithdrawals)} &middot; {s.transactionCount} transaction{s.transactionCount === 1 ? '' : 's'}</p>

            {(r.breakdown.length > 0 || r.expensesByCategory.length > 0) && (
              <div className="grid two">
                {r.breakdown.length > 0 && (
                  <section className="card">
                    <h2>{r.type === 'ANNUAL' ? 'Month by month' : 'Day by day'}</h2>
                    <Bar data={{
                      labels: r.breakdown.map((b) => b.label),
                      datasets: [
                        { label: 'Income', data: r.breakdown.map((b) => Number(b.income)), backgroundColor: '#2e9e6b' },
                        { label: 'Expenses', data: r.breakdown.map((b) => Number(b.expenses)), backgroundColor: '#e0603d' },
                      ],
                    }} options={{ responsive: true, plugins: { legend: { position: 'bottom' } } }} />
                  </section>
                )}
                {r.expensesByCategory.length > 0 && (
                  <section className="card">
                    <h2>Where the money went</h2>
                    <Doughnut data={{
                      labels: r.expensesByCategory.map((c) => c.categoryName),
                      datasets: [{ data: r.expensesByCategory.map((c) => Number(c.amount)), backgroundColor: PALETTE }],
                    }} options={{ plugins: { legend: { position: 'bottom' } } }} />
                  </section>
                )}
              </div>
            )}

            <div className="grid two">
              <CategoryTable title="Income by category" rows={r.incomeByCategory} money={money} />
              <CategoryTable title="Expenses by category" rows={r.expensesByCategory} money={money} />
            </div>

            {r.budgets.length > 0 && (
              <section className="card table-card spaced">
                <h2 className="pad">Budgets</h2>
                <div className="scroll">
                  <table>
                    <thead><tr><th>Category</th><th className="right">Budget</th><th className="right">Spent</th><th className="right">Used</th><th>Status</th></tr></thead>
                    <tbody>{r.budgets.map((b) => (
                      <tr key={b.id}><td>{b.categoryName}</td><td className="right">{money(b.budgetAmount)}</td><td className="right">{money(b.spent)}</td>
                        <td className="right">{b.percentUsed}%</td><td>{b.status === 'OK' ? 'On track' : b.status === 'WARNING' ? 'Near limit' : 'Over budget'}</td></tr>
                    ))}</tbody>
                  </table>
                </div>
              </section>
            )}

            <section className="card table-card">
              <h2 className="pad">Transactions</h2>
              {shown.length === 0 ? <p className="muted pad">No transactions in this period.</p> : (
                <div className="scroll">
                  <table>
                    <thead><tr><th>Date</th><th>Type</th><th>Category</th><th>Description</th><th className="right">Amount</th></tr></thead>
                    <tbody>{shown.map((t, i) => (
                      <tr key={`${t.date}-${t.type}-${i}`}>
                        <td>{t.date}</td><td>{t.type === 'INCOME' ? 'Income' : 'Expense'}</td><td>{t.categoryName}</td>
                        <td className="wrap">{t.description || <span className="muted">&mdash;</span>}</td>
                        <td className={`right ${t.type === 'INCOME' ? 'good' : 'bad'}`}>{t.type === 'INCOME' ? '+' : '-'}{money(t.amount)}</td>
                      </tr>
                    ))}</tbody>
                  </table>
                </div>
              )}
              {r.transactions.length > MAX_ROWS && (
                <p className="muted small pad">Showing the first {MAX_ROWS} of {r.transactions.length} transactions. Download the CSV for the full list.</p>
              )}
            </section>
          </>
        );
      })()}
    </>
  );
}
