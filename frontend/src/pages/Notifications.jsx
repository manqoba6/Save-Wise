import { useEffect, useState } from 'react';
import api, { errorMessage } from '../api/client.js';
import { useNotifications } from '../context/NotificationsContext.jsx';

const PAGE_SIZE = 15;
const TONE = { BUDGET_CRITICAL: 'bad', BUDGET_WARNING: 'warn', GOAL_ACHIEVED: 'good', LOW_BALANCE: 'warn' };

export default function Notifications() {
  const { refresh } = useNotifications();
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [page, setPage] = useState(0);
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const [reload, setReload] = useState(0);

  useEffect(() => {
    api.get('/api/notifications', { params: { unreadOnly, page, size: PAGE_SIZE } })
      .then(({ data }) => { setResult(data); setError(''); })
      .catch((e) => setError(errorMessage(e)));
  }, [unreadOnly, page, reload]);

  async function act(request) {
    try { await request(); setReload((n) => n + 1); refresh(); }
    catch (err) { setError(errorMessage(err)); }
  }

  const hasUnread = result?.content.some((n) => !n.read);

  return (
    <>
      <div className="page-head">
        <h1>Notifications</h1>
        <div className="head-actions">
          <label className="inline"><input type="checkbox" checked={unreadOnly} onChange={(e) => { setUnreadOnly(e.target.checked); setPage(0); }} /> Unread only</label>
          <button className="btn ghost" disabled={!hasUnread} onClick={() => act(() => api.put('/api/notifications/read-all'))}>Mark all as read</button>
        </div>
      </div>

      {error && <div className="alert">{error}</div>}

      <div className="card table-card">
        {!result ? <p className="muted pad">Loading...</p> : result.content.length === 0 ? (
          <p className="muted pad">{unreadOnly ? 'You are all caught up.' : 'No notifications yet. Budget alerts and goal milestones will show up here.'}</p>
        ) : (
          <ul className="notifs">
            {result.content.map((n) => (
              <li key={n.id} className={n.read ? 'read' : 'unread'}>
                <span className={`dot ${TONE[n.type] || 'info'}`} />
                <div className="body">
                  <strong>{n.title}</strong>
                  <span>{n.message}</span>
                  <small className="muted">{new Date(n.createdAt).toLocaleString()}</small>
                </div>
                <div className="notif-actions">
                  {!n.read && <button className="link" onClick={() => act(() => api.put(`/api/notifications/${n.id}/read`))}>Mark read</button>}
                  <button className="link danger" onClick={() => act(() => api.delete(`/api/notifications/${n.id}`))}>Delete</button>
                </div>
              </li>
            ))}
          </ul>
        )}
        {result && result.totalPages > 1 && (
          <div className="pager">
            <span className="muted">Page {result.page + 1} of {result.totalPages}</span>
            <span>
              <button className="btn ghost" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>{' '}
              <button className="btn ghost" disabled={page + 1 >= result.totalPages} onClick={() => setPage(page + 1)}>Next</button>
            </span>
          </div>
        )}
      </div>
    </>
  );
}
