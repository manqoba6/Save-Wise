# Creates .env.production with strong random secrets. Run from the project folder:  .\scripts\generate-secrets.ps1
function New-Secret([int]$Bytes) {
    $b = New-Object byte[] $Bytes
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
    ([Convert]::ToBase64String($b)).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}
if (Test-Path .env.production) { Write-Host ".env.production already exists. Delete it first if you really want new secrets."; exit 1 }
@"
POSTGRES_PASSWORD=$(New-Secret 24)
JWT_SECRET=$(New-Secret 48)
PUBLIC_URL=https://your-domain.example
COOKIE_SECURE=true
APP_TIMEZONE=Africa/Johannesburg
WEB_PORT=80
"@ | Set-Content -Path .env.production -Encoding ascii
Write-Host "Created .env.production. Now edit PUBLIC_URL, and keep this file private (it is git-ignored)."
