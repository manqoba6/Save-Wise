# SaveWise frontend

React 18 + Vite, React Router, Axios, Chart.js.

```bash
npm install
npm run dev        # http://localhost:5173
```
The dev server forwards `/api` to the backend on http://localhost:8080 (change with `VITE_API_PROXY`),
so no CORS setup is needed. Start the backend first.

## Built so far
Login, register, forgot/reset password, protected routes, automatic token refresh,
light/dark theme and currency from the user's settings, the dashboard, and the Income and Expenses pages
(add, edit, delete, search, category and date filters, pagination), Budgets (monthly budgets with
progress, copy last month) Notifications (unread badge in the sidebar, mark read, delete), Savings (accounts, deposit, withdraw,
transfer, history) and Goals (progress, add money, cancel, completion celebration), and Settings (theme and currency saved
at once, profile, profile picture, change password), and the Assistant (advice, overspending analysis,
saving tips, spending trends, balance and savings forecast), and Recurring (schedules with pause/resume,
status tabs, monthly totals and a history of what each schedule has posted), and Reports (daily, weekly,
monthly and annual, with charts, category tables, budgets, transactions, and PDF and CSV download).

```bash
npm test           # component tests (Vitest + Testing Library)
```
