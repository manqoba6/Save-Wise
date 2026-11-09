# Deploying SaveWise

## What is protected, and how
| Risk | What the app does |
|---|---|
| Stealing a login token with injected JavaScript (XSS) | The refresh token is an **HttpOnly cookie**: scripts cannot read it. The short-lived access token (15 min) is kept only in memory, never in `localStorage`. A strict Content-Security-Policy blocks injected scripts in the first place. |
| Using a token from another website (CSRF) | The cookie is **SameSite=Strict** and only sent to `/api/auth`. |
| Sniffing tokens on the network | The cookie is **Secure** (HTTPS only) in production, and HSTS is sent. |
| A database leak handing out sessions | Refresh tokens and password-reset tokens are stored as **SHA-256 hashes**; passwords are BCrypt hashes. |
| Reusing a stolen refresh token | Refresh tokens are **single-use** (rotated every time). Changing your password, or an admin deactivating you, ends all sessions. |
| Password guessing and sign-up abuse | **Rate limiting**: 10 attempts per minute per IP on login, register, forgot- and reset-password (HTTP 429). |
| A guessable signing key | With `SPRING_PROFILES_ACTIVE=prod` the app **refuses to start** unless `JWT_SECRET` is random and 32+ characters. No default exists. |
| Attacking the database directly | In the production compose file the database and API have **no published ports**; only nginx is reachable. Containers run without extra privileges. |

## Before you deploy
1. **Use a different, random database password.** `Manqoba6` is fine on your own computer, but it is short, built from your name, and has appeared in chat and screenshots. Run `scripts/generate-secrets.ps1` (Windows) or `scripts/generate-secrets.sh`: they create `.env.production` with a random database password and token key.
2. Set `PUBLIC_URL` in `.env.production` to your real address (`https://...`).
3. You need **HTTPS**. Most hosts give it for free; on your own server use the included `Caddyfile.example` (automatic certificates).
4. Never commit `.env`, `.env.production` or the `uploads/` folder (all are git-ignored).

## Start it
```bash
docker compose -f docker-compose.prod.yml --env-file .env.production up -d --build
docker compose -f docker-compose.prod.yml logs -f backend     # look for "Started SavewiseApplication"
```
Then register your account on the site, and promote yourself to admin once:
```bash
docker compose -f docker-compose.prod.yml exec db psql -U savewise -d savewise_db -c \
 "UPDATE users SET role_id=(SELECT role_id FROM roles WHERE role_name='ADMIN') WHERE email='you@example.com';"
```

## Still to do before real users arrive
- **Backups:** schedule `pg_dump` (e.g. daily) and store copies off the server.
- **Email:** password-reset emails are only printed to the log (`LoggingEmailService`). Add a real mail sender before launch or reset will not work for users.
- **Updates:** rebuild regularly (`docker compose ... up -d --build`) to pick up security fixes in the base images.
- **Monitoring:** watch the backend log for `429` bursts and repeated failed logins.
