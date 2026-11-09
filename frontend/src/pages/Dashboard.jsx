import { useEffect, useState } from 'react';
import { Chart as ChartJS, CategoryScale, LinearScale, BarElement, ArcElement, Tooltip, Legend } from 'chart.js';
import { Bar, Doughnut } from 'react-chartjs-2';
import api, { errorMessage } from '../api/client.js';
import { useSettings } from '../context/SettingsContext.jsx';

ChartJS.register(CategoryScale, LinearScale, BarElement, ArcElement, Tooltip, Legend);
ChartJS.defaults.color = '#8a94a6';

const PALETTE = ['#4f7cff', '#2e9e6b', '#e0603d', '#f2b134', '#9b59d0', '#22b8cf', '#e6679a', '#7f8c8d'];

function thisMonth() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

function Stat({ label, value, tone }) {
  return <div className="card stat"><span className="muted">{label}</span><strong className={tone}>{value}</strong></div>;
}

function Progress({ percent, status }) {
  const tone = status === 'CRITICAL' ? 'bad' : status === 'WARNING' ? 'warn' : 'good';
  return <div className="bar"><div className={`fill ${tone}`} style={{ width: `${Math.min(100, Number(percent) || 0)}%` }} /></div>;
}

export default function Dashboard() {
  const { money } = useSettings();
  const [month, setMonth] = useState(thisMonth());
  const [data, setData] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    setError('');
    api.get('/api/dashboard', { params: { month: `${month}-01` } })
      .then((res) => setData(res.data))
      .catch((err) => setError(errorMessage(err)));
  }, [month]);

  if (error) return <div className="alert">{error}</div>;
  if (!data) return <p className="muted">Loading your dashboard...</p>;

  const { summary: s, financialHealth: h } = data;
  const healthTone = h.score >= 60 ? 'good' : h.score >= 40 ? 'warn' : 'bad';

  return (
    <>
      <div className="page-head">
        <h1>Dashboard</h1>
        <input type="month" value={month} onChange={(e) => e.target.value && setMonth(e.target.value)} />
      </div>

      <div className="grid stats">
        <Stat label="Income this month" value={money(s.monthIncome)} tone="good" />
        <Stat label="Expenses this month" value={money(s.monthExpenses)} tone="bad" />
        <Stat label="Current balance" value={money(s.currentBalance)} />
        <Stat label="Total savings" value={money(s.totalSavings)} />
      </div>

      <div className="grid two">
        <section className="card">
          <h2>Income vs expenses</h2>
          <Bar data={{
            labels: data.monthlyChart.map((m) => m.month),
            datasets: [
              { label: 'Income', data: data.monthlyChart.map((m) => Number(m.income)), backgroundColor: '#2e9e6b' },
              { label: 'Expenses', data: data.monthlyChart.map((m) => Number(m.expenses)), backgroundColor: '#e0603d' },
            ],
          }} options={{ responsive: true, plugins: { legend: { position: 'bottom' } } }} />
        </section>

        <section className="card">
          <h2>Spending by category</h2>
          {data.spendingByCategory.length === 0 ? <p className="muted">No expenses this month.</p> : (
            <Doughnut data={{
              labels: data.spendingByCategory.map((c) => c.categoryName),
              datasets: [{ data: data.spendingByCategory.map((c) => Number(c.amount)), backgroundColor: PALETTE }],
            }} options={{ plugins: { legend: { position: 'bottom' } } }} />
          )}
        </section>
      </div>

      <div className="grid two">
        <section className="card">
          <h2>Financial health</h2>
          {h.label === 'NOT_ENOUGH_DATA'
            ? <p className="muted">Add some income for this month and your score will appear here.</p>
            : <>
                <div className="score"><strong>{h.score}</strong><span className="muted">/ 100 &middot; {h.label.replace('_', ' ')}</span></div>
                <div className="bar big"><div className={`fill ${healthTone}`} style={{ width: `${h.score}%` }} /></div>
                <ul className="plain">
                  {h.components.map((c) => <li key={c.name}><span>{c.name}</span><span>{c.score}/{c.maxScore}</span></li>)}
                </ul>
              </>}
        </section>

        <section className="card">
          <h2>Budgets</h2>
          {data.budgets.length === 0 ? <p className="muted">No budgets for this month yet.</p> : data.budgets.map((b) => (
            <div key={b.id} className="item">
              <div className="between"><span>{b.categoryName}</span><span className="muted">{money(b.spent)} / {money(b.budgetAmount)}</span></div>
              <Progress percent={b.percentUsed} status={b.status} />
            </div>
          ))}
        </section>
      </div>

      <div className="grid two">
        <section className="card">
          <h2>Goals</h2>
          {data.activeGoals.length === 0 ? <p className="muted">No active goals.</p> : data.activeGoals.map((g) => (
            <div key={g.id} className="item">
              <div className="between"><span>{g.title}</span><span className="muted">{money(g.currentAmount)} / {money(g.targetAmount)}</span></div>
              <Progress percent={g.percentComplete} status="OK" />
            </div>
          ))}
        </section>

        <section className="card">
          <h2>Recent transactions</h2>
          {data.recentTransactions.length === 0 ? <p className="muted">Nothing recorded yet.</p> : (
            <ul className="plain tx">
              {data.recentTransactions.map((t) => (
                <li key={`${t.type}-${t.id}`}>
                  <div><strong>{t.categoryName}</strong><small className="muted">{t.date}{t.description ? ` · ${t.description}` : ''}</small></div>
                  <span className={t.type === 'INCOME' ? 'good' : 'bad'}>{t.type === 'INCOME' ? '+' : '-'}{money(t.amount)}</span>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </>
  );
}
