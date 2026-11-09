import useFetch from '../../hooks/useFetch.js';
import { useSettings } from '../../context/SettingsContext.jsx';
import Panel from './Panel.jsx';
import InsightList from './InsightList.jsx';

export default function OverspendingTab({ month }) {
  const { money } = useSettings();
  const state = useFetch('/api/assistant/overspending', { month });
  return (
    <Panel state={state}>
      {(d) => (
        <>
          <div className="grid stats">
            <div className="card stat"><span className="muted">Income</span><strong className="good">{money(d.income)}</strong></div>
            <div className="card stat"><span className="muted">Expenses</span><strong className="bad">{money(d.expenses)}</strong></div>
          </div>
          <InsightList insights={d.findings} />
        </>
      )}
    </Panel>
  );
}
