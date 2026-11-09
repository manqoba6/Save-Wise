import useFetch from '../../hooks/useFetch.js';
import Panel from './Panel.jsx';
import InsightList from './InsightList.jsx';

export default function AdviceTab({ month }) {
  const state = useFetch('/api/assistant/advice', { month });
  return (
    <Panel state={state}>
      {(d) => {
        const h = d.financialHealth;
        const tone = h.score >= 60 ? 'good' : h.score >= 40 ? 'warn' : 'bad';
        return (
          <>
            <section className="card spaced">
              <h2>Financial health</h2>
              {h.label === 'NOT_ENOUGH_DATA'
                ? <p className="muted">Add some income for this month and your score will appear here.</p>
                : <>
                    <div className="score"><strong>{h.score}</strong><span className="muted">/ 100 &middot; {h.label.replace('_', ' ')}</span></div>
                    <div className="bar big"><div className={`fill ${tone}`} style={{ width: `${h.score}%` }} /></div>
                    <ul className="plain">
                      {h.components.map((c) => <li key={c.name}><span>{c.name}</span><span>{c.score}/{c.maxScore}</span></li>)}
                    </ul>
                  </>}
            </section>
            <h2 className="section-title">What to know</h2>
            <InsightList insights={d.insights} />
          </>
        );
      }}
    </Panel>
  );
}
