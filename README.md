# SaveWise

Personal finance management system (Java 21, Spring Boot 3, PostgreSQL, React).

## Status
- [x] Week 1: project setup, full database schema (Flyway `V1__init.sql`)
- [x] Week 2 (partial): register, login, refresh, logout, JWT security, global error handling
- [x] Categories (read: any user, write: ADMIN), Income and Expenses CRUD with search, filters and pagination
- [x] Budgets (monthly per category, with spend progress) and notifications; 80% / 100% alerts fire automatically when an expense crosses a threshold
- [x] Savings (accounts, deposit, withdraw, transfer, history; balance can never go negative) and Goals (progress, contributions, completion notification)
- [x] Dashboard (`GET /api/dashboard`): totals, balance, budget and goal progress, spending by category, 6-month chart, recent transactions, financial health score
- [x] Forgot / reset / change password, profile (update, picture upload), admin user management
- [x] Recurring transactions: daily/weekly/monthly/yearly schedules, a scheduled job that posts due income/expenses (also catches up after downtime), pause/resume, history
- [x] Settings (theme, currency, notification opt-out); alert messages use the user's currency
- [x] Reports: daily, weekly, monthly, annual (JSON) with PDF and CSV export
- [x] Smart assistant (`/api/assistant/*`: advice, overspending, saving recommendations, spending trends) and predictions (`/api/predictions`)
- [x] React frontend, part 1 (`frontend/`): login, register, forgot/reset password, token refresh, dark mode and currency from settings, dashboard
- [x] React frontend, part 2: Income and Expenses pages (CRUD, search, filters, pagination) with component tests
- [x] React frontend, part 3: Budgets and Notifications (unread badge) pages
- [x] React frontend, part 4: Savings and Goals pages
- [x] React frontend, part 5: Settings and Profile (theme, currency, notifications, profile, picture, change password)
- [x] React frontend, part 6: Assistant page (advice, overspending, saving tips, trends, forecast)
- [x] React frontend, part 7: Recurring transactions page
- [x] React frontend, part 8: Reports page with PDF and CSV download. All planned frontend pages are built.
- [x] Deployment hardening: HttpOnly refresh cookie, in-memory access token, hashed tokens, rate limiting, nginx + production compose (see DEPLOY.md)
- [ ] CI pipeline (GitHub Actions)

> Deploying for real users? Read **DEPLOY.md** first: it uses `docker-compose.prod.yml` and random secrets, not the development files below.

## Run with Docker (PostgreSQL + API in one command)
```bash
cp .env.example .env        # then edit .env: set POSTGRES_PASSWORD and a JWT_SECRET (32+ chars)
docker compose up --build   # API at http://localhost:8080
```
Stop any copy of the backend you started from IntelliJ first (both use port 8080).
The containerised database is separate from any local PostgreSQL, so users must be registered again.
Data survives restarts in Docker volumes; `docker compose down -v` wipes it.
To browse the containerised database in pgAdmin: host `localhost`, port `5433`, database `savewise_db`, user `savewise`.

## Run the frontend
```bash
cd frontend
npm install
npm run dev     # http://localhost:5173 (needs the backend on :8080)
```

## Run the backend without Docker
Put your own database password in `backend/src/main/resources/application-local.properties` (git-ignored; it loads automatically).
1. Install Java 21, Maven and PostgreSQL 17.
2. Create the database: `CREATE DATABASE savewise_db;`
3. Set env vars (or edit `application.properties`): `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET` (32+ chars).
4. `cd backend && mvn spring-boot:run` (Flyway creates all tables and seeds roles/categories.)

## Make an admin
Everyone registers as USER. Promote yourself once in SQL:
```sql
UPDATE users SET role_id = (SELECT role_id FROM roles WHERE role_name = 'ADMIN') WHERE email = 'you@example.com';
```
Log in again afterwards so your token carries the new role.

## Password reset emails
In development the reset email is printed to the application log (`LoggingEmailService`).
Copy the link from the console. Swap in a real mail sender before going to production.

## Try it
```bash
curl -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"firstName":"Test","lastName":"User","email":"test@example.com","password":"password123"}'

curl localhost:8080/api/users/me -H "Authorization: Bearer <accessToken>"
```
