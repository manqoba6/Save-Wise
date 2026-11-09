#!/usr/bin/env bash
# Creates .env.production with strong random secrets. Run from the project folder:  ./scripts/generate-secrets.sh
set -euo pipefail
[ -e .env.production ] && { echo ".env.production already exists. Delete it first if you really want new secrets."; exit 1; }
secret() { openssl rand -base64 "$1" | tr '+/' '-_' | tr -d '=\n'; }
umask 077
cat > .env.production <<ENV
POSTGRES_PASSWORD=$(secret 24)
JWT_SECRET=$(secret 48)
PUBLIC_URL=https://your-domain.example
COOKIE_SECURE=true
APP_TIMEZONE=Africa/Johannesburg
WEB_PORT=80
ENV
echo "Created .env.production. Now edit PUBLIC_URL, and keep this file private (it is git-ignored)."
