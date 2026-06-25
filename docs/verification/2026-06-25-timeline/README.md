# Timeline Verification — 2026-06-25

Runtime verification of the usage timeline, captured by driving the running app
(Postgres + backend + frontend) with Playwright. Verdict: **PASS**.

Covers F1 (proportional spanning pills), F2 (drag-to-extend grips), F5 (confidence
status), F3 (period navigation), the world map tab, and the Architect → Team Member
rename.

| Screenshot | Shows |
|---|---|
| `01-timeline.png` | Full timeline — "Team Members" rows/nav, proportional pills spanning months, split-by-usage (e.g. NAB 5%→10%), utilization heatmap, period arrows, zoom, status legend |
| `02-status-menu.png` | Right-click status context menu (Confirmed ✓ / Probable / Potential) |
| `03-usage-editor.png` | Click-to-edit usage slider on a pill |
| `04-worldmap.png` | World map tab (Leaflet geographic utilization) |
| `05-probable-status.png` | A pill changed to Probable — orange/dashed variant amongst blue Confirmed pills, legend top-right |

Observed: proportional heights (20%→32px, 5%→16px floor), spanning widths (100% / 83%),
hover grip opacity 0.85, status change persisted via PATCH + refetch, period arrows shift
by `monthCount` (6) and back. Test data left clean (probe status reverted to Confirmed).
