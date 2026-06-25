# Team Management

Internal tool for managing team members and their assignments to customers.
Java 25 (Spring Boot) backend + Vue 3 (Vite) frontend; PostgreSQL via Docker Compose.

See `CLAUDE.md` for development, running, and database details.

## Docker images

Published to Docker Hub on each release:

- `lolobored/team-management-backend`
- `lolobored/team-management-frontend`

`docker compose up` builds locally; `docker compose pull` fetches the published `:latest` images.

## Releasing

Releases are cut by pushing a git tag in the form `<year>.<quarter>.x`:

- `<year>` — four-digit year (e.g. `2026`)
- `<quarter>` — `1`–`4`
- `x` — patch number, starting at `0` and incremented per release within the quarter (reset to `0` at the start of a new quarter)

```bash
git tag 2026.2.0
git push origin 2026.2.0
```

The **Release** workflow then builds and pushes both Docker images
(`:<tag>` and `:latest`) to Docker Hub and creates a GitHub Release with generated notes.

To test images from a branch without releasing, run the **Dev Image** workflow:

```bash
gh workflow run "Dev Image" --ref <branch> -f tag=dev
```

## Dependencies

Dependabot opens weekly PRs for Gradle, npm, Docker, and GitHub Actions updates.
Minor and patch updates auto-merge once CI passes; major updates are reviewed manually.
