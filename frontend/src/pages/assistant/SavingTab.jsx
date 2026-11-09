import useFetch from '../../hooks/useFetch.js';
import { useSettings } from '../../context/SettingsContext.jsx';
import Panel from './Panel.jsx';
import InsightList from './InsightList.jsx';

export default function SavingTab() {
  const { money } = useSettings();
  const state = useFetch('/api/assistant/saving-recommendations');
  return (
    <Panel state={state}>
      {(d) => (
        <>
          {d.monthsAnalysed > 0 && (
            <>
              <p className="muted">Based on your average over the last {d.monthsAnalysed} full month{d.monthsAnalysed === 1 ? '' : 's'}.</p>
              <div className="grid stats">
                <div className="card stat"><span className="muted">Avg income / month</span><strong>{money(d.averageMonthlyIncome)}</strong></div>
                <div className="card stat"><span className="muted">Avg spending / month</span><strong>{money(d.averageMonthlyExpenses)}</strong></div>
                <div className="card stat"><span className="muted">Avg left over / month</span>
                  <strong className={Number(d.averageMonthlySurplus) < 0 ? 'bad' : 'good'}>{money(d.averageMonthlySurplus)}</strong></div>
                <div className="card stat"><span className="muted">Savings rate</span>
                  <strong>{d.averageSavingsRatePercent == null ? 'n/a' : `${d.averageSavingsRatePercent}%`}</strong></div>
              </div>
            </>
          )}
          <InsightList insights={d.recommendations} />
        </>
      )}
    </Panel>
  );
}
