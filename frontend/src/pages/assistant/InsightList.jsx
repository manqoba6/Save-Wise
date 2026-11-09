const LABEL = { CRITICAL: 'Urgent', WARNING: 'Heads up', INFO: 'Tip' };

/** Insights arrive most-urgent first; colour and a text label carry the severity (not colour alone). */
export default function InsightList({ insights }) {
  return (
    <ul className="insights">
      {insights.map((i, index) => (
        <li key={`${i.type}-${i.categoryName || ''}-${index}`} className={`insight ${i.severity.toLowerCase()}`}>
          <div className="between">
            <strong>{i.title}</strong>
            <span className="sev">{LABEL[i.severity] || i.severity}</span>
          </div>
          <p>{i.message}</p>
          {i.categoryName && <span className="chip">{i.categoryName}</span>}
        </li>
      ))}
    </ul>
  );
}
