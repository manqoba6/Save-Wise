import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';
import { useNotifications } from '../context/NotificationsContext.jsx';

const NAV = [
  ['/', 'Dashboard'], ['/income', 'Income'], ['/expenses', 'Expenses'], ['/budgets', 'Budgets'],
  ['/savings', 'Savings'], ['/goals', 'Goals'], ['/recurring', 'Recurring'], ['/reports', 'Reports'],
  ['/assistant', 'Assistant'], ['/notifications', 'Notifications'], ['/settings', 'Settings'],
];

export default function Layout() {
  const { user, logout } = useAuth();
  const { unread } = useNotifications();
  const navigate = useNavigate();

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">Save<span>Wise</span></div>
        <nav>
          {NAV.map(([to, label]) => (
            <NavLink key={to} to={to} end className={({ isActive }) => (isActive ? 'nav active' : 'nav')}>
              {label}{to === '/notifications' && unread > 0 && <span className="badge">{unread}</span>}
            </NavLink>
          ))}
        </nav>
      </aside>
      <div className="main">
        <header className="topbar">
          <div>Hello, <strong>{user.firstName}</strong></div>
          <button className="btn ghost" onClick={async () => { await logout(); navigate('/login'); }}>Log out</button>
        </header>
        <main className="content"><Outlet /></main>
      </div>
    </div>
  );
}
