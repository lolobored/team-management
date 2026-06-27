# Production deployment — team-management

Single-host Docker Compose deploy (e.g. `nuc.local`). **No root required** — runs under
any user that can use Docker, in any writable directory (e.g.
`~/docker/team-management`). Runs the **published images** — nothing is built on the host.
Only the frontend is published, on host port **3615** (`http://<host>:3615`); backend and
postgres stay on the internal network.

Storage is a Docker **named volume** (`pgdata`) — the daemon manages its permissions, so
there's no host-side `chown`/root dance (unlike a bind-mount).

Files in this bundle:
- `docker-compose.yml` — the production stack
- `.env.example` — config template (copy to `.env`)

---

## 0. Prerequisites (on the host)

- Docker Engine + Compose plugin, usable by your user (`docker ps` works without sudo).

## 1. Create the directory + copy the files (host)

```bash
mkdir -p ~/docker/team-management
cd ~/docker/team-management
```
From the repo on your Mac:
```bash
scp deploy/docker-compose.yml <host>:docker/team-management/
scp deploy/.env.example       <host>:docker/team-management/.env
```
Then edit `.env` — set a strong `ADMIN_EMAIL` / `ADMIN_PASSWORD` (the first-login admin),
optional Google keys, and confirm `APP_VERSION` (default `2026.2.6`).

## 2. Bring existing data (optional)

Skip this for a brand-new install — the backend creates the schema and bootstraps the
admin on first start. To migrate an existing database, use a logical dump (version-safe,
no file-permission issues):

```bash
# On the source machine (local stack running):
docker compose exec -T postgres pg_dump -U tm teammanagement > tm.sql
# To start with a clean set of login accounts but keep all app data, append:
echo 'TRUNCATE TABLE users;' >> tm.sql       # backend then bootstraps a fresh admin from .env
scp tm.sql <host>:docker/team-management/
```

Auto-load it on first start: in `docker-compose.yml`, **uncomment** the
`./tm.sql:/docker-entrypoint-initdb.d/01-restore.sql:ro` line under the postgres service.
On the first `up` (empty volume) postgres runs the dump once; later starts ignore it.
(Alternatively, leave it commented and restore manually after step 3:
`docker compose exec -T postgres psql -U tm -d teammanagement < tm.sql`.)

## 3. Start the stack (host)

```bash
cd ~/docker/team-management
docker compose pull
docker compose up -d
docker compose ps          # backend becomes "healthy", frontend "Up"
```

First start with no imported data → log in as the `.env` `ADMIN_EMAIL` / `ADMIN_PASSWORD`
(forced to set a new password on first login). Manage further users in the **Users** view.

## 4. Verify

```bash
curl -f http://localhost:3615/api/health           # -> {"status":"ok"}
curl -o /dev/null -w '%{http_code}\n' http://localhost:3615/   # -> 200 (the SPA)
```
Browser: `http://<host>:3615` → login screen.

After a successful first start you can delete `tm.sql` and re-comment its mount line; the
data now lives in the `pgdata` volume.

---

## Updating to a new release

```bash
cd ~/docker/team-management
# edit .env -> APP_VERSION=<new tag>
docker compose pull
docker compose up -d
```
Liquibase applies new migrations automatically on backend start; the volume persists.

## Backups

```bash
docker compose exec -T postgres pg_dump -U tm teammanagement | gzip > tm-$(date +%F).sql.gz
```
The data lives in the `pgdata` named volume (under Docker's storage, typically
`/var/lib/docker/volumes/team-management_pgdata`).

## Notes

- **Postgres is internal-only** (no published port). Credentials are `tm`/`tm` to match the
  backend `docker` profile — acceptable because nothing outside the compose network can
  reach it. Don't add a `ports:` entry for postgres.
- **Account lockout / self-lockout:** 5 failed logins lock an account for 15 min. An admin
  can lock themselves out; recovery is wait it out or have another admin unlock. With a
  single admin, keep the password somewhere safe.
- **HTTPS:** the app is served plain HTTP on 3615. For external exposure put a reverse
  proxy (Caddy/Traefik/nginx) with TLS in front; on a trusted LAN, 3615 direct is fine.
