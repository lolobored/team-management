# World Map Utilization View — Design Spec

## Overview

A world map view showing architect utilization by country per month, accessible as a tab within the existing Usage Timeline page. Uses Leaflet with Vue Leaflet for interactive map rendering with colored/sized bubble markers per country.

## Data Flow & Geocoding

No new backend endpoints or database changes required.

- **Usage data**: Reuse the existing `/api/usage?from=YYYY-MM&to=YYYY-MM` endpoint, which returns per-architect, per-month utilization with country field.
- **Country coordinates & flags**: Expand the existing `restcountries.com` API call in the geo store from `?fields=name` to `?fields=name,latlng,flags` to get latitude/longitude and flag images for each country.
- **Frontend aggregation**:
  1. Fetch usage data for the selected month via `useUsageStore`
  2. Group architects by country
  3. Per country: compute average utilization, count architects, collect assignment details
  4. Look up coordinates from the geo store's country data
  5. Render bubbles on the map

## UI Structure & Navigation

The Usage Timeline view (`UsageTimelineView.vue`) becomes a tabbed view:

- **Timeline tab** — the existing grid, unchanged
- **Map tab** — the new world map view

Tab bar sits at the top of the content area. Both tabs share filter controls (country filter, name filter) and the month picker. Switching tabs preserves filter state and selected month.

### Map Tab Layout

- **Map area** (left, flex-grow): Leaflet map with CartoDB Dark Matter tiles and circle markers
- **Side panel** (right, fixed ~240px): Scrollable country list sorted by utilization descending

### Side Panel Content

Each row displays:
- Country flag (from restcountries data)
- Country name
- Average utilization % (color-coded)
- Architect headcount

Clicking a row pans/zooms the map to that country.

## Map Component Details

### Tiles & Setup

- CartoDB Dark Matter tiles (free, no API key, matches app's dark theme)
- Default zoom showing full world, centered roughly on Europe/Asia
- Zoom/pan enabled, scroll-wheel zoom enabled

### Bubble Markers

- Custom `L.circleMarker` for each country with architects
- **Size**: Scales with headcount — base radius ~12px for 1 architect, +6px per additional architect, capped at ~40px
- **Color**: Green (<80%), amber (80-99%), red (≥100%), grey (0%)
- **Label**: Average utilization % and headcount displayed on/beside the bubble
- Semi-transparent fill with solid border

### Hover Popup

Leaflet popup triggered on `mouseover`, closes on `mouseout`. Content:

- Country header with flag and average utilization
- Each architect listed with color-coded individual total utilization
- Indented breakdown per architect: customer name — project name + usage %
- Tentative assignments shown with "(tentative)" label

## Month Navigation

In map mode, month picker becomes a single-month selector:

- **◀ / ▶ buttons** to step one month back/forward
- **Month display** in "May 2026" format, clickable to open native `<input type="month">` picker
- No limit on future navigation (shows projected utilization from POTENTIAL projects)
- Defaults to current month on first switch to map tab
- Stays in sync with timeline mode's `from` month

## Edge Cases

- **Unrecognized country**: Architect data appears in side panel with "Location unknown" label, no bubble on map
- **No data for selected month**: Map shows tiles only, side panel shows "No utilization data for this month"
- **0% utilization country**: Grey bubble still appears (shows idle capacity)
- **Headcount cap**: Bubble radius caps at ~40px to avoid visual overflow
- **Tentative assignments**: Included in utilization calculation, marked "(tentative)" in hover popup

## Technology

- **Map library**: Leaflet via `@vue-leaflet/vue-leaflet` (Vue 3 components)
- **Tiles**: CartoDB Dark Matter (free, no API key)
- **Geocoding**: `restcountries.com` API (already used, adding `latlng` field)
- **No new backend changes**: All aggregation happens in the frontend

## Files Affected

- `frontend/package.json` — add `leaflet`, `@vue-leaflet/vue-leaflet` dependencies
- `frontend/src/stores/geo.ts` — expand restcountries API call to include `latlng`
- `frontend/src/views/UsageTimelineView.vue` — add tab navigation, conditionally render timeline or map
- `frontend/src/components/WorldMapView.vue` — new component: map + side panel + month nav
- `frontend/src/components/TimelineGrid.vue` — no changes (rendered inside timeline tab as-is)
