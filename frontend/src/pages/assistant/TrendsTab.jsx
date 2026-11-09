import { useState } from 'react';
import { Bar } from 'react-chartjs-2';
import '../../chartSetup.js';
import useFetch from '../../hooks/useFetch.js';
import { useSettings } from '../../context/SettingsContext.jsx';
import Panel from './Panel.jsx';

const WORDS = { RISING: 'Rising', FALLING: 'Falling', STABLE: 'Stable', NEW: 'New', INSUFFICIENT_DATA: 'Not enough data' };

/** More income is good news, more spending is not. */
function tone(direction, kind) {
  if (direction === 'RISING') return kind === 'income' ? 'good' : 'bad';
  if (direction === 'FALLING') return kind === 'income' ? 'bad' : 'good';
  return 'muted';
}

function TrendChip({ label, direction, perMonth, kind }) {
  const sign = Number(perMonth) > 0 ? '+' : '';
  return (
    <div className="card stat">
      <span className="muted">{label}</span>
      <strong className={tone(direction, kind)}>{WORDS[direction] || direction}</strong>
      {direction !== 'INSUFFICIENT_DATA' && <span className="muted small">{sign}{perMonth}% a month</span>}
    </div>
  );
}

export default function TrendsTab() {
  const { money } = useSettings();
  const [months, setMonths] = useState(6);
  const state = useFetch('/api/assistant/spending-trends', { months });

  return (
    <>
      <div className="head-actions spaced">
        <label className="inline">Look back
          <select value={months} onChange={(e) => setMonths(Number(e.target.value))}>
            {[3, 6, 12].map((n) => <option key={n} value={n}>{n} months</option>)}
          </select>
        </label>
      </div>
      <Panel state={state}>
        {(d) => (
          <>
            {d.message && <div className="notice spaced">{d.message}</div>}
            {d.months.length > 0 && (
              <section className="card spaced">
                <h2>Income vs spending</h2>
                <Bar data={{
                  labels: d.months.map((m) => m.month),
                  datasets: [
                    { label: 'Income', data: d.months.map((m) => Number(m.income)), backgroundColor: '#2e9e6b' },
                    { label: 'Expenses', data: d.months.map((m) => Number(m.expenses)), backgroundColor: '#e0603d' },
                  ],
                }} options={{ responsive: true, plugins: { legend: { position: 'bottom' } } }} />
              </section>
            )}
            {d.monthsAnalysed >= 2 && (
              <div className="grid stats">
                <TrendChip label="Income" kind="income" direction={d.incomeTrend} perMonth={d.incomeChangePerMonthPercent} />
                <TrendChip label="Spending" kind="expense" direction={d.expenseTrend} perMonth={d.expenseChangePerMonthPercent} />
              </div>
            )}
            {d.categories.length > 0 && (
              <section className="card table-card">
                <h2 className="pad">Categories: last month vs the months before</h2>
                <div className="scroll">
                  <table>
                    <thead><tr><th>Category</th><th className="right">Last month</th><th className="right">Usual before</th><th className="right">Change</th></tr></thead>
                    <tbody>
                      {d.categories.map((c) => (
                        <tr key={c.categoryName}>
                          <td>{c.categoryName}</td>
                          <td className="right">{money(c.lastMonth)}</td>
                          <td className="right">{money(c.previousAverage)}</td>
                          <td className={`right ${tone(c.direction, 'expense')}`}>
                            {c.changePercent == null ? WORDS[c.direction] : `${Number(c.changePercent) > 0 ? '+' : ''}${c.changePercent}%`}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </section>
            )}
          </>
        )}
      </Panel>
    </>
  );
}
