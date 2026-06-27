# Production deployment — team-management

Single-host Docker Compose deploy (target: `nuc.local`, run as **root**, under
`/opt/docker/team-management`). Runs the **published images** — nothing is built on
the host. Only the frontend is published, on host port **3615**
(`http://nuc.local:3615`); backend and postgres stay on the internal network.

Files in this bundle:
- `docker-compose.yml` — the production stack
- `.env.example` — config template (copy to `.env`)

> ⚠️ **About the volume you're bringing.** The local `volumes/pgdata` holds your real
> team / customer / assignment data **and** throwaway login accounts created during
> development (`admin@example.com`, `viewer@example.com`, `u2@example.com`,
> `u3@example.com`) with dev passwords. Team-member emails are legacy `@quantexa.com`
> values. Recommendation below (Step 4, Option B): bring the volume but **reset the
> `users` table** so the backend bootstraps a clean admin from your `.env` — this keeps
> all app data and drops the dev logins.

---

## 0. Prerequisites (on the NUC)

- Docker Engine + Compose plugin installed (`docker --version`, `docker compose version`).
- You can run as root (the steps use `/opt`).

## 1. Create the directory (on the NUC, as root)

```bash
mkdir -p /opt/docker/team-management/volumes/pgdata
cd /opt/docker/team-management
```

## 2. Copy the compose + env (from your Mac → NUC)

From the repo on your Mac:
```bash
scp deploy/docker-compose.yml root@nuc.local:/opt/docker/team-management/
scp deploy/.env.example       root@nuc.local:/opt/docker/team-management/.env
```
Then on the NUC edit `/opt/docker/team-management/.env` — set a strong `ADMIN_EMAIL` /
`ADMIN_PASSWORD`, optionally the Google keys, and confirm `APP_VERSION` (default
`2026.2.6`).

## 3. Transfer the database volume (from your Mac → NUC)

Postgres must be **stopped** during the copy for a consistent snapshot.

```bash
# On your Mac, in the repo dir:
docker compose down                       # stop the local stack (keeps the bind-mount data)

rsync -a --delete ./volumes/pgdata/ \
  root@nuc.local:/opt/docker/team-management/volumes/pgdata/
```

Then on the NUC, fix ownership — the postgres:16 image runs as uid **999**, and the
files arrive owned by your Mac user:
```bash
chown -R 999:999 /opt/docker/team-management/volumes/pgdata
chmod 700        /opt/docker/team-management/volumes/pgdata
```

> Alternative (cleaner, version-independent): instead of copying the raw data dir, take a
> logical dump on the Mac — `docker compose up -d postgres && docker compose exec -T
> postgres pg_dump -U tm teammanagement > tm.sql` — copy `tm.sql` to the NUC, start an
> empty stack, and `docker compose exec -T postgres psql -U tm -d teammanagement < tm.sql`.
> Use this if the raw copy ever complains about permissions or page versions.

## 4. Start the stack (on the NUC, as root)

```bash
cd /opt/docker/team-management
docker compose pull
docker compose up -d
docker compose ps          # backend should become "healthy", frontend "Up"
```

**Option B (recommended) — reset the login accounts to a clean admin** (keeps all team /
customer / assignment data, drops the dev logins):
```bash
docker compose exec postgres psql -U tm -d teammanagement -c "TRUNCATE TABLE users;"
docker compose restart backend     # bootstrap re-seeds the admin from your .env
```
You can now log in as the `ADMIN_EMAIL` / `ADMIN_PASSWORD` from `.env` (you'll be forced
to set a new password on first login). Manage further users in the **Users** view.

*Option A (keep the dev logins as-is): skip the reset. Then the only working admin is the
dev account `admin@example.com` — not recommended for production.*

## 5. Verify

```bash
curl -f http://localhost:3615/api/health           # -> {"status":"ok"}
curl -o /dev/null -w '%{http_code}\n' http://localhost:3615/   # -> 200 (the SPA)
```
From a browser: `http://nuc.local:3615` → redirected to the login screen.

---

## Updating to a new release

```bash
cd /opt/docker/team-management
# edit .env -> APP_VERSION=<new tag>   (or leave at :latest tracking if you prefer)
docker compose pull
docker compose up -d
```
Liquibase applies any new migrations automatically on backend start; the volume persists.

## Backups

The whole database is in `/opt/docker/team-management/volumes/pgdata`. Back it up with a
logical dump (safe while running):
```bash
docker compose exec -T postgres pg_dump -U tm teammanagement | gzip > tm-$(date +%F).sql.gz
```

## Notes

- **Postgres is internal-only** (no published port). Credentials are `tm`/`tm` to match the
  backend `docker` profile and the existing volume — acceptable because nothing outside the
  compose network can reach it. Don't add a `ports:` entry for postgres.
- **Account lockout / self-lockout:** 5 failed logins lock an account for 15 min. An admin
  can lock themselves out; recovery is wait it out or have another admin unlock. With a
  single admin, keep the password somewhere safe.
- **HTTPS:** the app is served plain HTTP on 3615. For external exposure put a reverse
  proxy (Caddy/Traefik/nginx) with TLS in front; on a trusted LAN, 3615 direct is fine.
