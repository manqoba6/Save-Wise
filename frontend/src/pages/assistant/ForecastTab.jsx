import { Line } from 'react-chartjs-2';
import '../../chartSetup.js';
import useFetch from '../../hooks/useFetch.js';
import { useSettings } from '../../context/SettingsContext.jsx';
import Panel from './Panel.jsx';

const CONFIDENCE = { LOW: 'Low confidence', MEDIUM: 'Medium confidence', HIGH: 'High confidence' };

export default function ForecastTab() {
  const { money } = useSettings();
  const state = useFetch('/api/predictions');

  return (
    <Panel state={state}>
      {(d) => {
        if (!d.enoughData) return <div className="notice">{d.message}</div>;
        const points = d.projections;
        const series = (key, label, color, dashed) => ({
          label, data: [Number(d.currentBalance), ...points.map((p) => Number(p[key]))],
          borderColor: color, backgroundColor: color, borderDash: dashed ? [6, 4] : [], tension: 0.25, pointRadius: dashed ? 0 : 3,
        });
        return (
          <>
            <p className="muted">{d.message} <span className="pill info">{CONFIDENCE[d.confidence]}</span></p>
            <div className="grid stats">
              <div className="card stat"><span className="muted">Balance today</span><strong>{money(d.currentBalance)}</strong></div>
              <div className="card stat"><span className="muted">Avg left over / month</span>
                <strong className={Number(d.averageMonthlyNet) < 0 ? 'bad' : 'good'}>{money(d.averageMonthlyNet)}</strong></div>
              <div className="card stat"><span className="muted">Savings today</span><strong>{money(d.currentSavings)}</strong></div>
              <div className="card stat"><span className="muted">Avg added to savings / month</span><strong>{money(d.averageMonthlySavingsContribution)}</strong></div>
            </div>

            <section className="card spaced">
              <h2>Projected balance</h2>
              <Line data={{
                labels: ['Today', ...points.map((p) => p.month)],
                datasets: [series('balanceHigh', 'Optimistic', '#9aa5b8', true), series('projectedBalance', 'Expected', '#4f7cff', false), series('balanceLow', 'Cautious', '#9aa5b8', true)],
              }} options={{ responsive: true, plugins: { legend: { position: 'bottom' } } }} />
            </section>

            <section className="card table-card spaced">
              <div className="scroll">
                <table>
                  <thead><tr><th>In</th><th className="right">Expected balance</th><th className="right">Likely range</th><th className="right">Expected savings</th></tr></thead>
                  <tbody>
                    {points.map((p) => (
                      <tr key={p.monthsAhead}>
                        <td>{p.monthsAhead} month{p.monthsAhead === 1 ? '' : 's'} <span className="muted small">({p.month})</span></td>
                        <td className="right">{money(p.projectedBalance)}</td>
                        <td className="right muted">{money(p.balanceLow)} to {money(p.balanceHigh)}</td>
                        <td className="right">{money(p.projectedSavings)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>

            {d.goals.length > 0 && (
              <section className="card spaced">
                <h2>Your goals at this pace</h2>
                {d.goals.map((g) => (
                  <div key={g.goalId} className="item">
                    <div className="between"><strong>{g.title}</strong><span className={`pill ${g.onTrack ? 'good' : 'bad'}`}>{g.onTrack ? 'On track' : 'At risk'}</span></div>
                    <div className="small muted">
                      {money(g.remaining)} to go
                      {g.requiredMonthly != null && ` · needs ${money(g.requiredMonthly)} a month${g.deadline ? ` to reach it by ${g.deadline}` : ''}`}
                      {g.monthsToGoalAtCurrentPace != null && ` · about ${g.monthsToGoalAtCurrentPace} month${g.monthsToGoalAtCurrentPace === 1 ? '' : 's'} at your current surplus`}
                    </div>
                  </div>
                ))}
              </section>
            )}
            <p className="muted small">These are estimates based on your recent average, not promises. The range shows how much your months usually vary.</p>
          </>
        );
      }}
    </Panel>
  );
}
