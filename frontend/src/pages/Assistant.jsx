import { useState } from 'react';
import AdviceTab from './assistant/AdviceTab.jsx';
import OverspendingTab from './assistant/OverspendingTab.jsx';
import SavingTab from './assistant/SavingTab.jsx';
import TrendsTab from './assistant/TrendsTab.jsx';
import ForecastTab from './assistant/ForecastTab.jsx';

const TABS = [['advice', 'Advice'], ['overspending', 'Overspending'], ['saving', 'Saving tips'], ['trends', 'Trends'], ['forecast', 'Forecast']];
const MONTH_TABS = ['advice', 'overspending'];

const pad = (n) => String(n).padStart(2, '0');
const thisMonth = () => { const d = new Date(); return `${d.getFullYear()}-${pad(d.getMonth() + 1)}`; };

export default function Assistant() {
  const [tab, setTab] = useState('advice');
  const [month, setMonth] = useState(thisMonth());
  const monthStart = `${month}-01`;

  return (
    <>
      <div className="page-head">
        <h1>Assistant</h1>
        {MONTH_TABS.includes(tab) && <input type="month" aria-label="Month" value={month} onChange={(e) => e.target.value && setMonth(e.target.value)} />}
      </div>
      <p className="muted spaced">Plain-English advice from your own numbers. Every message comes from a simple calculation you could check yourself.</p>

      <div className="tabs">
        {TABS.map(([value, label]) => (
          <button key={value} className={tab === value ? 'tab active' : 'tab'} onClick={() => setTab(value)}>{label}</button>
        ))}
      </div>

      {tab === 'advice' && <AdviceTab month={monthStart} />}
      {tab === 'overspending' && <OverspendingTab month={monthStart} />}
      {tab === 'saving' && <SavingTab />}
      {tab === 'trends' && <TrendsTab />}
      {tab === 'forecast' && <ForecastTab />}
    </>
  );
}
