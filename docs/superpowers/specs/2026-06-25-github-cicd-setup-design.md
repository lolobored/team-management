# GitHub Repo + CI/CD Setup — Design Spec

**Date:** 2026-06-25
**Feature:** Recreate the git repository, publish it to GitHub, and add CI/CD: a tag-driven
release that publishes both Docker images to Docker Hub, automated dependency bumping with
auto-merge, and a manual dev-image build.
**Reference:** modeled on `lolobored/plex-downloader` (`.github/workflows/release.yml`,
`dev-image.yml`, `dependabot.yml`).

## Summary

The project has no git remote and no CI. This sets up:

1. A fresh git history pushed to a new **public** GitHub repo `lolobored/team-management`.
2. A **release** workflow triggered by a `<year>.<quarter>.x` tag that builds and pushes the
   backend and frontend Docker images to Docker Hub and cuts a GitHub Release.
3. A **CI** workflow (tests) gating pull requests.
4. **Dependabot** with **auto-merge** of minor/patch updates.
5. A manual **dev-image** workflow for throwaway images.
6. Docker Hub image bumps in `docker-compose.yml`.

## Decisions (from brainstorming)

- **Release trigger:** manual tag push (plex-style). The maintainer pushes a tag; CI builds +
  releases. No auto-computed version.
- **Version scheme:** `<year>.<quarter>.x` — four-digit year, quarter `1`–`4`, incrementing
  patch `x` starting at `0` per quarter. First Q2-2026 release = `2026.2.0`. Tag glob
  `[0-9]+.[0-9]+.[0-9]+` matches it (convention enforced by docs, not regex).
- **Repo visibility:** public.
- **Dependency bumps:** Dependabot PRs + auto-merge of semver **minor/patch** once CI passes;
  **major** stays manual.
- **dev-image workflow:** keep (plex parity).
- **Docker image names:** `lolobored/team-management-backend`, `lolobored/team-management-frontend`.
- **Docker Hub auth:** username `lolobored` hardcoded; password from `secrets.DOCKERHUB_TOKEN`
  (maintainer supplies the token; set via `gh secret set`).

## Maintainer-driven steps (interactive / external — done by the operator, not the plan)

These are one-off, irreversible, or require credentials, so they run outside the
subagent-implemented plan:

1. **Reset history:** `rm -rf .git` → `git init -b main` → re-add the tracked fileset →
   single `Initial commit`. (Same clean-slate approach used previously.)
2. **Create + push:** `gh repo create lolobored/team-management --public --source=. --remote=origin --push`.
3. **Set secret:** `gh secret set DOCKERHUB_TOKEN` with a Docker Hub access token the
   maintainer provides.
4. **Repo settings:** enable "Allow auto-merge"; add branch protection on `main` requiring the
   CI status checks — so Dependabot auto-merge waits for green CI.

## Dockerfile fixes (prerequisite — the release builds these)

- `backend/Dockerfile`: `eclipse-temurin:17-jdk` (build stage) and `17-jre` (runtime) →
  **`25-jdk`** / **`25-jre`**. The Gradle toolchain targets Java 25; building under JDK 17
  fails. Build context is `./backend` (the Dockerfile COPYs `gradlew`, `build.gradle.kts`,
  `src/` relative to that dir).
- `frontend/Dockerfile`: `node:18-alpine` → **`node:20-alpine`** (Vite build safety). Build
  context is `./frontend` (multi-stage node build → nginx serving `dist/`; `nginx.conf`
  already present).
- Both must `docker build` cleanly; this is verified during implementation before the release
  workflow is trusted.

## docker-compose.yml

Add the published image name to each built service so `docker compose build` tags the image
and `docker compose pull` fetches the released one:

- `backend` service: add `image: lolobored/team-management-backend:latest` (keep `build: ./backend`).
- `frontend` service: add `image: lolobored/team-management-frontend:latest` (keep `build: ./frontend`).
- `postgres` (`postgres:16`) unchanged.

## Workflows (`.github/workflows/`)

### `ci.yml`

Trigger: `pull_request` and `push` to `main`. Two independent jobs (these are the required
checks that gate auto-merge):

- **backend:** `actions/checkout@v4`, `actions/setup-java@v4` (distribution `temurin`,
  java-version `25`), then `cd backend && ./gradlew test`. The `test` profile uses H2, so no
  PostgreSQL service is needed.
- **frontend:** `actions/checkout@v4`, `actions/setup-node@v4` (node 20, cache npm), then
  `cd frontend && npm ci && npx vitest run && npx vue-tsc --noEmit`.

### `release.yml`

Trigger: `push` tags matching `'[0-9]+.[0-9]+.[0-9]+'`. One job, `permissions: contents: write`:

1. `actions/checkout@v4`.
2. `VERSION=${GITHUB_REF_NAME}` to `$GITHUB_OUTPUT`.
3. `docker/setup-buildx-action@v3`.
4. `docker/login-action@v3` — `username: lolobored`, `password: ${{ secrets.DOCKERHUB_TOKEN }}`.
5. `docker/build-push-action@v6` — backend: `context: ./backend`, `push: true`, tags
   `lolobored/team-management-backend:${VERSION}` + `:latest`, `cache-from/to: type=gha`.
6. `docker/build-push-action@v6` — frontend: `context: ./frontend`, tags
   `lolobored/team-management-frontend:${VERSION}` + `:latest`, gha cache.
7. `softprops/action-gh-release@v2` — `tag_name: ${{ github.ref_name }}`,
   `generate_release_notes: true`, body with `docker pull` lines for both images and a Docker
   Hub link.

(Action major versions are the current stable ones; Dependabot's `github-actions` ecosystem
keeps them current thereafter.)

### `dependabot.yml` (`.github/dependabot.yml`)

`version: 2`, weekly Monday, `open-pull-requests-limit: 5`, with labels:

- `gradle` — `directory: /backend` (labels `dependencies`, `java`)
- `npm` — `directory: /frontend` (labels `dependencies`, `javascript`)
- `docker` — `directory: /backend` (labels `dependencies`, `docker`)
- `docker` — `directory: /frontend` (labels `dependencies`, `docker`)
- `github-actions` — `directory: /` (labels `dependencies`, `ci`)

(Two `docker` entries because the Dockerfiles live in `/backend` and `/frontend`, not the root.)

### `dependabot-automerge.yml`

Trigger: `pull_request`. Condition `github.actor == 'dependabot[bot]'`,
`permissions: contents: write, pull-requests: write`:

1. `dependabot/fetch-metadata@v2` → exposes `update-type`.
2. If `update-type` is `version-update:semver-minor` or `version-update:semver-patch`:
   `gh pr merge --auto --squash "$PR_URL"` (uses `GITHUB_TOKEN`). `--auto` queues the merge
   until the required `ci` checks pass. Major updates fall through and stay manual.

### `dev-image.yml`

Trigger: `workflow_dispatch` with a `tag` input (default `dev`). Logs in to Docker Hub, builds
+ pushes **both** images at `:${tag}` (and `:dev-${{ github.sha }}` for traceability). Does not
touch `:latest` or create a release. Step summary lists the pushed tags.

## Versioning doc

Add a short **"Releasing"** section to `README.md` (or `CLAUDE.md`) documenting the convention:
tag `<year>.<quarter>.x` (e.g. `git tag 2026.2.0 && git push origin 2026.2.0`) → the release
workflow builds + pushes both images and creates the GitHub Release; bump `x` for each release
within a quarter, reset to `0` at the start of a new quarter.

## Testing / verification

- Dockerfile fixes: `docker build` both images locally → succeed.
- Workflow YAML: validate syntax (e.g. `actionlint` if available, otherwise careful review);
  confirm `ci.yml` job names match the branch-protection required-check names.
- End-to-end release is verified by pushing a real tag after merge (operator step): confirm the
  two images appear on Docker Hub and a GitHub Release is created — or dry-run the build jobs
  via `dev-image.yml` first.
- CI workflow proven by opening the initial PR / first push and watching the checks go green.

## Non-Goals

- No auto-computed version or release-on-merge (manual tag chosen).
- No CD/deploy step beyond publishing images (no server deployment).
- No change to application code or the DB; this is repo/CI infrastructure only (besides the
  Dockerfile base-image bumps).
- No Renovate (Dependabot chosen).

## Risks

- **Docker Hub repos** `team-management-backend`/`-frontend` must be creatable by the
  `lolobored` account on first push (default for Docker Hub).
- **Auto-merge** requires both the repo setting and a required status check, or `gh pr merge
  --auto` errors — both are set in the maintainer steps.
- **Backend image build** pulls the Gradle toolchain for Java 25 inside the container; the base
  image bump to `temurin:25` avoids a second JDK download.
