# GitHub Repo + CI/CD Setup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add CI/CD config — fixed Dockerfiles, a tag-driven release that publishes both Docker images, Dependabot with auto-merge, a manual dev-image build, and compose image refs — ready to push to a new GitHub repo.

**Architecture:** All file-based config (Dockerfiles, `docker-compose.yml`, `.github/` workflows + dependabot, README). The git-reset, GitHub-repo creation, `DOCKERHUB_TOKEN` secret, and branch protection are operator steps done outside this plan (they need credentials / are irreversible).

**Tech Stack:** GitHub Actions, Docker / Docker Hub, Dependabot. App: Java 25 (Spring Boot, Gradle) backend + Vue 3 (Vite) frontend.

## Global Constraints

- Docker images: `lolobored/team-management-backend`, `lolobored/team-management-frontend`. Docker Hub username `lolobored` hardcoded; password `secrets.DOCKERHUB_TOKEN`.
- Release tag convention: `<year>.<quarter>.x` (e.g. `2026.2.0`); workflow tag glob `'[0-9]+.[0-9]+.[0-9]+'`.
- Backend Docker base image must be Java **25** (Gradle toolchain is 25). Frontend node **20**.
- Backend tests run on H2 (`test` profile) — no PostgreSQL service needed in CI.
- Auto-merge applies to Dependabot **semver minor/patch** only; major stays manual.
- GitHub Actions versions match the reference repo `lolobored/plex-downloader` where shared.

---

## Task 1: Dockerfile fixes + docker-compose image refs

Make both images build under the current toolchains and point compose at the published images.

**Files:**
- Modify: `backend/Dockerfile`
- Modify: `frontend/Dockerfile`
- Modify: `docker-compose.yml`

**Interfaces:**
- Produces: buildable images with contexts `./backend` and `./frontend` (consumed by `release.yml`/`dev-image.yml` in Task 2).

- [ ] **Step 1: Bump the backend base image to Java 25**

Replace the two `eclipse-temurin:17-*` lines in `backend/Dockerfile`. Final file:

```dockerfile
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY gradle/ gradle/
COPY gradlew build.gradle.kts settings.gradle.kts ./
RUN ./gradlew dependencies --no-daemon || true
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 2: Bump the frontend build image to node 20**

In `frontend/Dockerfile`, change the first line `FROM node:18-alpine AS build` to `FROM node:20-alpine AS build`. Leave the rest (npm ci, `npm run build-only`, nginx stage) unchanged.

- [ ] **Step 3: Point docker-compose at the published images**

In `docker-compose.yml`, add an `image:` line to the `backend` and `frontend` services alongside their existing `build:` lines:

```yaml
  backend:
    image: lolobored/team-management-backend:latest
    build: ./backend
```
```yaml
  frontend:
    image: lolobored/team-management-frontend:latest
    build: ./frontend
```

(Keep every other key in those services exactly as-is — ports, env, depends_on, etc. Only the `image:` line is added. `postgres` is unchanged.)

- [ ] **Step 4: Verify both images build**

```bash
cd /Users/laurentlaborde/projects/team-management
docker build -t tm-backend-test ./backend
docker build -t tm-frontend-test ./frontend
```
Expected: both `docker build` runs finish `naming to ... done` / exit 0. The backend build runs `./gradlew bootJar` under JDK 25 (no toolchain download error); the frontend build runs `vite build` under node 20 and copies `dist/` into nginx. Clean up: `docker image rm tm-backend-test tm-frontend-test`.

- [ ] **Step 5: Validate the compose file**

```bash
docker compose config -q && echo "compose valid"
```
Expected: `compose valid` (no schema error; the new `image:` keys parse).

- [ ] **Step 6: Commit**

```bash
git add backend/Dockerfile frontend/Dockerfile docker-compose.yml
git commit -m "build: bump Docker base images (Java 25, node 20) and add compose image refs"
```

---

## Task 2: GitHub Actions, Dependabot, and the Releasing doc

Add CI, release, dev-image, dependabot, and auto-merge config, plus a README documenting the release convention.

**Files:**
- Create: `.github/workflows/ci.yml`
- Create: `.github/workflows/release.yml`
- Create: `.github/workflows/dev-image.yml`
- Create: `.github/workflows/dependabot-automerge.yml`
- Create: `.github/dependabot.yml`
- Create: `README.md`

**Interfaces:**
- Consumes: image contexts `./backend`, `./frontend` (Task 1); `secrets.DOCKERHUB_TOKEN` (operator-set).
- Produces: a `backend` and `frontend` CI job (their names are the required status checks the operator wires into branch protection).

- [ ] **Step 1: CI workflow**

Create `.github/workflows/ci.yml`:

```yaml
name: CI

on:
  pull_request:
  push:
    branches: [main]

jobs:
  backend:
    name: Backend tests
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '25'
      - name: Run backend tests
        run: |
          cd backend
          ./gradlew test --no-daemon

  frontend:
    name: Frontend tests
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7
      - uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: npm
          cache-dependency-path: frontend/package-lock.json
      - name: Install, test, type-check
        run: |
          cd frontend
          npm ci
          npx vitest run
          npx vue-tsc --noEmit
```

- [ ] **Step 2: Release workflow**

Create `.github/workflows/release.yml`:

```yaml
name: Release

on:
  push:
    tags:
      - '[0-9]+.[0-9]+.[0-9]+'

jobs:
  release:
    name: Build & publish Docker images
    runs-on: ubuntu-latest
    permissions:
      contents: write
    steps:
      - name: Checkout
        uses: actions/checkout@v7

      - name: Extract version from tag
        id: version
        run: echo "VERSION=${GITHUB_REF_NAME}" >> "$GITHUB_OUTPUT"

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v4

      - name: Log in to Docker Hub
        uses: docker/login-action@v4
        with:
          username: lolobored
          password: ${{ secrets.DOCKERHUB_TOKEN }}

      - name: Build and push backend
        uses: docker/build-push-action@v7
        with:
          context: ./backend
          push: true
          tags: |
            lolobored/team-management-backend:${{ steps.version.outputs.VERSION }}
            lolobored/team-management-backend:latest
          cache-from: type=gha
          cache-to: type=gha,mode=max

      - name: Build and push frontend
        uses: docker/build-push-action@v7
        with:
          context: ./frontend
          push: true
          tags: |
            lolobored/team-management-frontend:${{ steps.version.outputs.VERSION }}
            lolobored/team-management-frontend:latest
          cache-from: type=gha
          cache-to: type=gha,mode=max

      - name: Create GitHub Release
        uses: softprops/action-gh-release@v3
        with:
          tag_name: ${{ github.ref_name }}
          generate_release_notes: true
          body: |
            ## Docker images
            ```
            docker pull lolobored/team-management-backend:${{ steps.version.outputs.VERSION }}
            docker pull lolobored/team-management-frontend:${{ steps.version.outputs.VERSION }}
            ```
            See Docker Hub: [backend](https://hub.docker.com/r/lolobored/team-management-backend) · [frontend](https://hub.docker.com/r/lolobored/team-management-frontend).
```

- [ ] **Step 3: Dev-image workflow**

Create `.github/workflows/dev-image.yml`:

```yaml
name: Dev Image

# Manually build & push throwaway images to Docker Hub WITHOUT cutting a release.
# No version bump, no Git tag, no GitHub Release. Use it to test a branch.
#   gh workflow run "Dev Image" --ref my-branch -f tag=dev

on:
  workflow_dispatch:
    inputs:
      tag:
        description: "Docker tag to push (default: dev). Built from the selected branch."
        required: false
        default: dev

jobs:
  dev-image:
    name: Build & push dev images
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v7

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v4

      - name: Log in to Docker Hub
        uses: docker/login-action@v4
        with:
          username: lolobored
          password: ${{ secrets.DOCKERHUB_TOKEN }}

      - name: Build and push backend
        uses: docker/build-push-action@v7
        with:
          context: ./backend
          push: true
          tags: |
            lolobored/team-management-backend:${{ inputs.tag }}
            lolobored/team-management-backend:dev-${{ github.sha }}
          cache-from: type=gha
          cache-to: type=gha,mode=max

      - name: Build and push frontend
        uses: docker/build-push-action@v7
        with:
          context: ./frontend
          push: true
          tags: |
            lolobored/team-management-frontend:${{ inputs.tag }}
            lolobored/team-management-frontend:dev-${{ github.sha }}
          cache-from: type=gha
          cache-to: type=gha,mode=max

      - name: Summary
        run: |
          {
            echo "### Dev images pushed"
            echo "- \`lolobored/team-management-backend:${{ inputs.tag }}\`"
            echo "- \`lolobored/team-management-frontend:${{ inputs.tag }}\`"
            echo ""
            echo "Built from \`${{ github.ref_name }}\`. Does NOT touch \`:latest\` or create a release."
          } >> "$GITHUB_STEP_SUMMARY"
```

- [ ] **Step 4: Dependabot config**

Create `.github/dependabot.yml`:

```yaml
version: 2
updates:
  - package-ecosystem: "gradle"
    directory: "/backend"
    schedule:
      interval: "weekly"
      day: "monday"
    labels:
      - "dependencies"
      - "java"
    open-pull-requests-limit: 5

  - package-ecosystem: "npm"
    directory: "/frontend"
    schedule:
      interval: "weekly"
      day: "monday"
    labels:
      - "dependencies"
      - "javascript"
    open-pull-requests-limit: 5

  - package-ecosystem: "docker"
    directory: "/backend"
    schedule:
      interval: "weekly"
      day: "monday"
    labels:
      - "dependencies"
      - "docker"

  - package-ecosystem: "docker"
    directory: "/frontend"
    schedule:
      interval: "weekly"
      day: "monday"
    labels:
      - "dependencies"
      - "docker"

  - package-ecosystem: "github-actions"
    directory: "/"
    schedule:
      interval: "weekly"
      day: "monday"
    labels:
      - "dependencies"
      - "ci"
```

- [ ] **Step 5: Dependabot auto-merge workflow**

Create `.github/workflows/dependabot-automerge.yml`:

```yaml
name: Dependabot auto-merge

on: pull_request

permissions:
  contents: write
  pull-requests: write

jobs:
  automerge:
    if: github.actor == 'dependabot[bot]'
    runs-on: ubuntu-latest
    steps:
      - name: Fetch Dependabot metadata
        id: meta
        uses: dependabot/fetch-metadata@v2

      - name: Enable auto-merge for minor/patch updates
        if: steps.meta.outputs.update-type == 'version-update:semver-minor' || steps.meta.outputs.update-type == 'version-update:semver-patch'
        run: gh pr merge --auto --squash "$PR_URL"
        env:
          PR_URL: ${{ github.event.pull_request.html_url }}
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

- [ ] **Step 6: README with the Releasing convention**

Create `README.md`:

```markdown
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

To test images from a branch without releasing, run the **Dev Image** workflow
(`gh workflow run "Dev Image" --ref <branch> -f tag=dev`).

## Dependencies

Dependabot opens weekly PRs for Gradle, npm, Docker, and GitHub Actions updates.
Minor and patch updates auto-merge once CI passes; major updates are reviewed manually.
```

- [ ] **Step 7: Validate the workflow + dependabot YAML**

```bash
cd /Users/laurentlaborde/projects/team-management
# YAML well-formedness for every new file:
for f in .github/workflows/*.yml .github/dependabot.yml; do
  python3 -c "import sys,yaml; yaml.safe_load(open('$f')); print('OK  $f')" || { echo "BAD $f"; exit 1; }
done
# If actionlint is installed, lint the workflows (non-fatal if absent):
command -v actionlint >/dev/null && actionlint || echo "actionlint not installed — skipped (YAML still validated above)"
```
Expected: `OK` for all five files. (`actionlint` is optional; absence is fine.)

- [ ] **Step 8: Commit**

```bash
git add .github README.md
git commit -m "ci: add CI, release, dev-image, and dependabot auto-merge workflows"
```

---

## Operator steps (run AFTER both tasks land — NOT part of subagent execution)

These need credentials / are irreversible, so the controlling operator runs them:

1. Reset history: `rm -rf .git && git init -b main`, re-add the tracked fileset, single `Initial commit`.
2. `gh repo create lolobored/team-management --public --source=. --remote=origin --push`.
3. `gh secret set DOCKERHUB_TOKEN` with a Docker Hub access token (operator supplies it).
4. Enable auto-merge + branch protection requiring the `Backend tests` and `Frontend tests` checks on `main` (so Dependabot auto-merge waits for green CI).
5. Verify: open a PR or push → CI runs; optionally `gh workflow run "Dev Image"` to smoke-test the image builds; then push a real `<year>.<quarter>.x` tag and confirm both images land on Docker Hub + a Release appears.

## Self-Review Notes

- **Spec coverage:** Dockerfile Java-25 / node-20 (T1 S1-2); compose image refs (T1 S3); build verification (T1 S4-5); ci.yml two gating jobs (T2 S1); release.yml tag-triggered two-image push + GitHub Release (T2 S2); dev-image both images (T2 S3); dependabot 5 entries incl. two docker dirs (T2 S4); auto-merge minor/patch (T2 S5); README releasing convention (T2 S6); YAML validation (T2 S7). Git-reset/repo-create/secret/branch-protection are operator steps (documented, out of subagent scope).
- **Consistency:** CI job names `Backend tests` / `Frontend tests` are referenced as the required checks in operator step 4. Image names and the `secrets.DOCKERHUB_TOKEN` reference are identical across release.yml, dev-image.yml, compose, and README.
- **No placeholders:** every file's full content is given.
- **Sequencing:** T1 (buildable images) → T2 (workflows that build them). Operator steps last.
```
