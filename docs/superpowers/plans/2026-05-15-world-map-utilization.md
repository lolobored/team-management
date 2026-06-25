# World Map Utilization View — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an interactive world map tab to the Usage Timeline page showing per-country architect utilization as colored/sized bubbles, with a side panel listing all countries and hover popups showing architect detail breakdowns.

**Architecture:** The map is a new Vue component (`WorldMapView.vue`) rendered inside the existing `UsageTimelineView.vue` via a tab toggle. The geo store is extended to provide country coordinates and flags from the existing `restcountries.com` API. All utilization aggregation (grouping architects by country, computing averages) happens as a computed property in the map component using data from the existing usage store. No backend changes needed.

**Tech Stack:** Vue 3 + Composition API, Leaflet via `@vue-leaflet/vue-leaflet`, Pinia stores, Vitest + @vue/test-utils

---

## File Structure

| File | Action | Responsibility |
|------|--------|----------------|
| `frontend/package.json` | Modify | Add `leaflet`, `@vue-leaflet/vue-leaflet`, `@types/leaflet` deps |
| `frontend/src/stores/geo.ts` | Modify | Expand restcountries API to include `latlng` and `flags` fields; expose `countryData` with coords/flags |
| `frontend/src/views/UsageTimelineView.vue` | Modify | Add tab bar (Timeline / Map), conditionally render `TimelineGrid` or `WorldMapView`, add single-month nav for map mode |
| `frontend/src/components/WorldMapView.vue` | Create | Leaflet map with bubble markers, side panel, hover popups |
| `frontend/src/__tests__/geo.spec.ts` | Create | Tests for geo store's expanded country data |
| `frontend/src/__tests__/WorldMapView.spec.ts` | Create | Tests for map component aggregation logic and rendering |
| `frontend/src/__tests__/UsageTimelineView.spec.ts` | Modify | Add tests for tab switching behavior |

---

### Task 1: Install Leaflet Dependencies

**Files:**
- Modify: `frontend/package.json`

- [ ] **Step 1: Install packages**

```bash
cd frontend && npm install leaflet @vue-leaflet/vue-leaflet && npm install -D @types/leaflet
```

- [ ] **Step 2: Verify installation**

```bash
cd frontend && node -e "require('@vue-leaflet/vue-leaflet'); console.log('OK')"
```

Expected: `OK`

- [ ] **Step 3: Commit**

```bash
git add frontend/package.json frontend/package-lock.json
git commit -m "deps: add leaflet and vue-leaflet for world map feature"
```

---

### Task 2: Extend Geo Store with Country Coordinates and Flags

**Files:**
- Modify: `frontend/src/stores/geo.ts`
- Create: `frontend/src/__tests__/geo.spec.ts`

- [ ] **Step 1: Write the failing test**

Create `frontend/src/__tests__/geo.spec.ts`:

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useGeoStore } from '@/stores/geo'
import axios from 'axios'

vi.mock('axios')
const mockedAxios = vi.mocked(axios)

describe('geo store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('fetchCountries populates countries list and countryData with latlng and flags', async () => {
    mockedAxios.get.mockResolvedValueOnce({
      data: [
        { name: { common: 'Singapore' }, latlng: [1.3521, 103.8198], flags: { png: 'https://flagcdn.com/sg.png' } },
        { name: { common: 'Australia' }, latlng: [-25.2744, 133.7751], flags: { png: 'https://flagcdn.com/au.png' } },
      ],
    })

    const store = useGeoStore()
    await store.fetchCountries()

    expect(store.countries).toEqual(['Australia', 'Singapore'])
    expect(store.countryData['Singapore']).toEqual({
      latlng: [1.3521, 103.8198],
      flagUrl: 'https://flagcdn.com/sg.png',
    })
    expect(store.countryData['Australia']).toEqual({
      latlng: [-25.2744, 133.7751],
      flagUrl: 'https://flagcdn.com/au.png',
    })
  })

  it('getCoords returns latlng for known country', async () => {
    mockedAxios.get.mockResolvedValueOnce({
      data: [
        { name: { common: 'Japan' }, latlng: [36.2048, 138.2529], flags: { png: 'https://flagcdn.com/jp.png' } },
      ],
    })

    const store = useGeoStore()
    await store.fetchCountries()

    expect(store.getCoords('Japan')).toEqual([36.2048, 138.2529])
    expect(store.getCoords('Unknown')).toBeNull()
  })

  it('getFlagUrl returns flag URL for known country', async () => {
    mockedAxios.get.mockResolvedValueOnce({
      data: [
        { name: { common: 'Japan' }, latlng: [36.2048, 138.2529], flags: { png: 'https://flagcdn.com/jp.png' } },
      ],
    })

    const store = useGeoStore()
    await store.fetchCountries()

    expect(store.getFlagUrl('Japan')).toBe('https://flagcdn.com/jp.png')
    expect(store.getFlagUrl('Unknown')).toBeNull()
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd frontend && npx vitest run src/__tests__/geo.spec.ts
```

Expected: FAIL — `countryData` property does not exist on the store.

- [ ] **Step 3: Implement the geo store changes**

Update `frontend/src/stores/geo.ts` to:

```typescript
import { ref } from 'vue'
import { defineStore } from 'pinia'
import axios from 'axios'

interface CountryInfo {
  latlng: [number, number]
  flagUrl: string
}

export const useGeoStore = defineStore('geo', () => {
  const countries = ref<string[]>([])
  const countryData = ref<Record<string, CountryInfo>>({})
  const citiesByCountry = ref<Record<string, string[]>>({})
  const loadingCountries = ref(false)
  const loadingCities = ref(false)

  async function fetchCountries() {
    if (countries.value.length > 0) return
    loadingCountries.value = true
    try {
      const response = await axios.get<Array<{
        name: { common: string }
        latlng: [number, number]
        flags: { png: string }
      }>>(
        'https://restcountries.com/v3.1/all?fields=name,latlng,flags'
      )
      const sorted = response.data.sort((a, b) => a.name.common.localeCompare(b.name.common))
      countries.value = sorted.map(c => c.name.common)
      const data: Record<string, CountryInfo> = {}
      for (const c of sorted) {
        data[c.name.common] = {
          latlng: c.latlng,
          flagUrl: c.flags.png,
        }
      }
      countryData.value = data
    } catch {
      countries.value = []
      countryData.value = {}
    } finally {
      loadingCountries.value = false
    }
  }

  async function fetchCities(country: string) {
    if (citiesByCountry.value[country]) return
    loadingCities.value = true
    try {
      const response = await axios.post<{ data: string[] }>(
        'https://countriesnow.space/api/v0.1/countries/cities',
        { country }
      )
      citiesByCountry.value[country] = response.data.data.sort((a, b) => a.localeCompare(b))
    } catch {
      citiesByCountry.value[country] = []
    } finally {
      loadingCities.value = false
    }
  }

  function getCities(country: string): string[] {
    return citiesByCountry.value[country] ?? []
  }

  function getCoords(country: string): [number, number] | null {
    return countryData.value[country]?.latlng ?? null
  }

  function getFlagUrl(country: string): string | null {
    return countryData.value[country]?.flagUrl ?? null
  }

  return {
    countries, countryData, citiesByCountry,
    loadingCountries, loadingCities,
    fetchCountries, fetchCities, getCities, getCoords, getFlagUrl,
  }
})
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cd frontend && npx vitest run src/__tests__/geo.spec.ts
```

Expected: All 3 tests PASS.

- [ ] **Step 5: Run all existing tests to check for regressions**

```bash
cd frontend && npx vitest run
```

Expected: All tests pass. The existing geo store mock in `UsageTimelineView.spec.ts` doesn't depend on the new fields, so it should still work.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/stores/geo.ts frontend/src/__tests__/geo.spec.ts
git commit -m "feat: extend geo store with country coordinates and flags from restcountries API"
```

---

### Task 3: Add Tab Navigation to UsageTimelineView

**Files:**
- Modify: `frontend/src/views/UsageTimelineView.vue`
- Modify: `frontend/src/__tests__/UsageTimelineView.spec.ts`

- [ ] **Step 1: Write the failing test for tab switching**

Add to the existing `frontend/src/__tests__/UsageTimelineView.spec.ts`, inside the `describe` block:

```typescript
  it('renders tab bar with Timeline and Map tabs', async () => {
    const wrapper = mount(UsageTimelineView)
    await flushPromises()

    const tabs = wrapper.findAll('[data-testid="view-tab"]')
    expect(tabs).toHaveLength(2)
    expect(tabs[0].text()).toBe('Timeline')
    expect(tabs[1].text()).toBe('Map')
  })

  it('defaults to Timeline tab', async () => {
    const wrapper = mount(UsageTimelineView)
    await flushPromises()

    const activeTab = wrapper.find('[data-testid="view-tab"].active')
    expect(activeTab.text()).toBe('Timeline')
    expect(wrapper.find('.timeline-grid').exists()).toBe(true)
  })
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd frontend && npx vitest run src/__tests__/UsageTimelineView.spec.ts
```

Expected: FAIL — `[data-testid="view-tab"]` elements not found.

- [ ] **Step 3: Add tab bar to UsageTimelineView template and script**

In `frontend/src/views/UsageTimelineView.vue`, add `activeTab` ref to the script:

After the `const nameFilter = ref('')` line, add:

```typescript
const activeTab = ref<'timeline' | 'map'>('timeline')
```

Replace the `<template>` section with:

```html
<template>
  <div class="timeline-view">
    <div class="main-area">
      <div class="header">
        <h1>Usage Timeline</h1>
        <div class="controls">
          <input v-model="nameFilter" placeholder="Filter by name..." data-testid="name-filter" />
          <select v-model="countryFilter" data-testid="country-filter">
            <option value="">All countries</option>
            <option v-for="c in geo.countries" :key="c" :value="c">{{ c }}</option>
          </select>
          <template v-if="activeTab === 'timeline'">
            <label>From: <input v-model="fromMonth" type="month" /></label>
            <label>Months:
              <select v-model.number="monthCount">
                <option :value="3">3</option>
                <option :value="6">6</option>
                <option :value="9">9</option>
                <option :value="12">12</option>
              </select>
            </label>
          </template>
          <template v-else>
            <div class="month-nav">
              <button class="month-nav-btn" @click="fromMonth = addMonths(fromMonth, -1)" data-testid="month-prev">&laquo;</button>
              <input v-model="fromMonth" type="month" data-testid="month-picker" />
              <button class="month-nav-btn" @click="fromMonth = addMonths(fromMonth, 1)" data-testid="month-next">&raquo;</button>
            </div>
          </template>
        </div>
      </div>
      <div class="tab-bar">
        <button
          v-for="tab in (['timeline', 'map'] as const)"
          :key="tab"
          class="tab-btn"
          :class="{ active: activeTab === tab }"
          data-testid="view-tab"
          @click="activeTab = tab"
        >
          {{ tab === 'timeline' ? 'Timeline' : 'Map' }}
        </button>
      </div>
      <TimelineGrid
        v-if="activeTab === 'timeline'"
        :usage-data="filteredUsage"
        :months="months"
        @drop="onDrop"
        @unassign="onUnassign"
      />
      <WorldMapView
        v-else
        :usage-data="filteredUsage"
        :month="fromMonth"
      />
    </div>
    <ProjectSidebar
      v-if="activeTab === 'timeline'"
      :projects="projectStore.projects"
      :customers="customerStore.customers"
    />
  </div>
</template>
```

Add the import for WorldMapView at the top of the script (after the TimelineGrid import):

```typescript
import WorldMapView from '@/components/WorldMapView.vue'
```

Add these styles to the `<style scoped>` block:

```css
.tab-bar { display: flex; gap: 0; margin-bottom: 0.75rem; border-bottom: 2px solid #e2e8f0; flex-shrink: 0; }
.tab-btn { padding: 0.5rem 1.25rem; border: none; background: none; cursor: pointer; font-size: 0.9rem; color: #64748b; border-bottom: 2px solid transparent; margin-bottom: -2px; transition: color 0.15s, border-color 0.15s; }
.tab-btn:hover { color: #334155; }
.tab-btn.active { color: #0f172a; border-bottom-color: #3b82f6; font-weight: 600; }
.month-nav { display: flex; align-items: center; gap: 0.25rem; }
.month-nav-btn { padding: 0.25rem 0.5rem; background: #e2e8f0; border: 1px solid #cbd5e1; border-radius: 4px; cursor: pointer; font-size: 1rem; line-height: 1; }
.month-nav-btn:hover { background: #cbd5e1; }
```

- [ ] **Step 4: Create a stub WorldMapView component**

Create `frontend/src/components/WorldMapView.vue`:

```vue
<script setup lang="ts">
import type { ArchitectUsage } from '@/types'

defineProps<{
  usageData: ArchitectUsage[]
  month: string
}>()
</script>

<template>
  <div class="world-map-view" data-testid="world-map-view">
    <p>Map view placeholder</p>
  </div>
</template>

<style scoped>
.world-map-view { flex: 1; display: flex; min-height: 0; }
</style>
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
cd frontend && npx vitest run src/__tests__/UsageTimelineView.spec.ts
```

Expected: All tests PASS (including the 2 new ones).

- [ ] **Step 6: Commit**

```bash
git add frontend/src/views/UsageTimelineView.vue frontend/src/components/WorldMapView.vue frontend/src/__tests__/UsageTimelineView.spec.ts
git commit -m "feat: add Timeline/Map tab bar to usage view with stub map component"
```

---

### Task 4: Build WorldMapView — Aggregation Logic and Side Panel

**Files:**
- Create: `frontend/src/__tests__/WorldMapView.spec.ts`
- Modify: `frontend/src/components/WorldMapView.vue`

- [ ] **Step 1: Write the failing tests for aggregation and side panel**

Create `frontend/src/__tests__/WorldMapView.spec.ts`:

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import WorldMapView from '@/components/WorldMapView.vue'
import type { ArchitectUsage } from '@/types'

vi.mock('@vue-leaflet/vue-leaflet', () => ({
  LMap: { template: '<div class="leaflet-map"><slot /></div>', props: ['zoom', 'center'] },
  LTileLayer: { template: '<div />', props: ['url', 'attribution'] },
  LCircleMarker: {
    template: '<div class="leaflet-circle" :data-country="$attrs[\'data-country\']"><slot /></div>',
    props: ['latLng', 'radius', 'color', 'fillColor', 'fillOpacity', 'weight'],
  },
  LPopup: { template: '<div class="leaflet-popup"><slot /></div>' },
  LTooltip: { template: '<div class="leaflet-tooltip"><slot /></div>', props: ['options'] },
}))

vi.mock('@/stores/geo', () => ({
  useGeoStore: () => ({
    countryData: {
      'Singapore': { latlng: [1.35, 103.82], flagUrl: 'https://flagcdn.com/sg.png' },
      'Australia': { latlng: [-25.27, 133.78], flagUrl: 'https://flagcdn.com/au.png' },
    },
    getCoords: (c: string) => {
      const coords: Record<string, [number, number]> = {
        'Singapore': [1.35, 103.82],
        'Australia': [-25.27, 133.78],
      }
      return coords[c] ?? null
    },
    getFlagUrl: (c: string) => {
      const flags: Record<string, string> = {
        'Singapore': 'https://flagcdn.com/sg.png',
        'Australia': 'https://flagcdn.com/au.png',
      }
      return flags[c] ?? null
    },
  }),
}))

const testUsageData: ArchitectUsage[] = [
  {
    architectId: 1,
    architectName: 'Alice Smith',
    country: 'Singapore',
    months: {
      '2026-05': {
        total: 40,
        assignments: [
          { projectId: 1, projectName: 'KYC', customerName: 'Acme', usage: 40, tentative: false },
        ],
      },
    },
  },
  {
    architectId: 2,
    architectName: 'Bob Lee',
    country: 'Singapore',
    months: {
      '2026-05': {
        total: 20,
        assignments: [
          { projectId: 2, projectName: 'Beta', customerName: 'BetaCo', usage: 20, tentative: true },
        ],
      },
    },
  },
  {
    architectId: 3,
    architectName: 'Carol Tan',
    country: 'Australia',
    months: {
      '2026-05': {
        total: 90,
        assignments: [
          { projectId: 3, projectName: 'Gamma', customerName: 'GammaCo', usage: 90, tentative: false },
        ],
      },
    },
  },
]

describe('WorldMapView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders side panel with country aggregations sorted by utilization descending', () => {
    const wrapper = mount(WorldMapView, {
      props: { usageData: testUsageData, month: '2026-05' },
    })

    const rows = wrapper.findAll('[data-testid="country-row"]')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('Australia')
    expect(rows[0].text()).toContain('90%')
    expect(rows[0].text()).toContain('1 architect')
    expect(rows[1].text()).toContain('Singapore')
    expect(rows[1].text()).toContain('30%')
    expect(rows[1].text()).toContain('2 architects')
  })

  it('renders circle markers on the map for countries with coords', () => {
    const wrapper = mount(WorldMapView, {
      props: { usageData: testUsageData, month: '2026-05' },
    })

    const circles = wrapper.findAll('.leaflet-circle')
    expect(circles).toHaveLength(2)
  })

  it('shows empty state when no usage data for selected month', () => {
    const wrapper = mount(WorldMapView, {
      props: { usageData: testUsageData, month: '2026-12' },
    })

    expect(wrapper.text()).toContain('No utilization data for this month')
  })

  it('handles architects with unknown country — shows in side panel but no marker', () => {
    const data: ArchitectUsage[] = [
      {
        architectId: 99,
        architectName: 'Unknown Person',
        country: 'Narnia',
        months: {
          '2026-05': {
            total: 50,
            assignments: [
              { projectId: 10, projectName: 'Quest', customerName: 'Fantasy', usage: 50, tentative: false },
            ],
          },
        },
      },
    ]

    const wrapper = mount(WorldMapView, {
      props: { usageData: data, month: '2026-05' },
    })

    const rows = wrapper.findAll('[data-testid="country-row"]')
    expect(rows).toHaveLength(1)
    expect(rows[0].text()).toContain('Narnia')
    expect(rows[0].text()).toContain('Location unknown')
    expect(wrapper.findAll('.leaflet-circle')).toHaveLength(0)
  })
})
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd frontend && npx vitest run src/__tests__/WorldMapView.spec.ts
```

Expected: FAIL — the stub component doesn't have side panel or aggregation logic.

- [ ] **Step 3: Implement WorldMapView with aggregation, map, and side panel**

Replace `frontend/src/components/WorldMapView.vue` with:

```vue
<script setup lang="ts">
import { computed } from 'vue'
import { LMap, LTileLayer, LCircleMarker, LPopup, LTooltip } from '@vue-leaflet/vue-leaflet'
import 'leaflet/dist/leaflet.css'
import { useGeoStore } from '@/stores/geo'
import type { ArchitectUsage } from '@/types'

const props = defineProps<{
  usageData: ArchitectUsage[]
  month: string
}>()

const geo = useGeoStore()

interface ArchitectDetail {
  architectId: number
  architectName: string
  total: number
  assignments: { projectName: string; customerName: string; usage: number; tentative: boolean }[]
}

interface CountryAggregation {
  country: string
  avgUtilization: number
  architectCount: number
  architects: ArchitectDetail[]
  coords: [number, number] | null
  flagUrl: string | null
}

const countryAggregations = computed<CountryAggregation[]>(() => {
  const byCountry = new Map<string, ArchitectDetail[]>()

  for (const architect of props.usageData) {
    const monthData = architect.months[props.month]
    if (!monthData) continue

    const country = architect.country || 'Unknown'
    if (!byCountry.has(country)) byCountry.set(country, [])
    byCountry.get(country)!.push({
      architectId: architect.architectId,
      architectName: architect.architectName,
      total: monthData.total,
      assignments: monthData.assignments.map(a => ({
        projectName: a.projectName,
        customerName: a.customerName,
        usage: a.usage,
        tentative: a.tentative,
      })),
    })
  }

  const result: CountryAggregation[] = []
  for (const [country, architects] of byCountry) {
    const totalUtil = architects.reduce((sum, a) => sum + a.total, 0)
    const avg = Math.round(totalUtil / architects.length)
    result.push({
      country,
      avgUtilization: avg,
      architectCount: architects.length,
      architects,
      coords: geo.getCoords(country),
      flagUrl: geo.getFlagUrl(country),
    })
  }

  return result.sort((a, b) => b.avgUtilization - a.avgUtilization)
})

const mappableCountries = computed(() =>
  countryAggregations.value.filter(c => c.coords !== null)
)

function utilizationColor(total: number): string {
  if (total >= 100) return '#f44336'
  if (total >= 80) return '#ff9800'
  if (total > 0) return '#4caf50'
  return '#9e9e9e'
}

function markerRadius(architectCount: number): number {
  return Math.min(12 + (architectCount - 1) * 6, 40)
}

function utilizationTextColor(total: number): string {
  if (total >= 100) return '#f44336'
  if (total >= 80) return '#ff9800'
  if (total > 0) return '#4caf50'
  return '#9e9e9e'
}
</script>

<template>
  <div class="world-map-view" data-testid="world-map-view">
    <div class="map-container">
      <LMap
        :zoom="2"
        :center="[20, 15]"
        :use-global-leaflet="false"
        :options="{ zoomControl: true, scrollWheelZoom: true }"
      >
        <LTileLayer
          url="https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png"
          attribution="&copy; OpenStreetMap &copy; CARTO"
        />
        <LCircleMarker
          v-for="agg in mappableCountries"
          :key="agg.country"
          :lat-lng="agg.coords!"
          :radius="markerRadius(agg.architectCount)"
          :color="utilizationColor(agg.avgUtilization)"
          :fill-color="utilizationColor(agg.avgUtilization)"
          :fill-opacity="0.6"
          :weight="2"
          :data-country="agg.country"
        >
          <LTooltip :options="{ permanent: true, direction: 'center', className: 'bubble-label' }">
            {{ agg.avgUtilization }}% · {{ agg.architectCount }}
          </LTooltip>
          <LPopup>
            <div class="popup-content">
              <div class="popup-header">
                <img v-if="agg.flagUrl" :src="agg.flagUrl" class="popup-flag" :alt="agg.country" />
                <strong>{{ agg.country }}</strong>
                <span class="popup-avg" :style="{ color: utilizationColor(agg.avgUtilization) }">
                  {{ agg.avgUtilization }}% avg
                </span>
              </div>
              <div v-for="arch in agg.architects" :key="arch.architectId" class="popup-architect">
                <div class="popup-arch-header">
                  <span class="popup-arch-name">{{ arch.architectName }}</span>
                  <span :style="{ color: utilizationTextColor(arch.total) }">{{ arch.total }}%</span>
                </div>
                <div class="popup-assignments">
                  <div v-for="(a, i) in arch.assignments" :key="i" class="popup-assignment">
                    <span>{{ a.customerName }} — {{ a.projectName }}</span>
                    <span>{{ a.usage }}%<template v-if="a.tentative"> (tentative)</template></span>
                  </div>
                </div>
              </div>
            </div>
          </LPopup>
        </LCircleMarker>
      </LMap>
    </div>
    <div class="side-panel">
      <h3 class="side-panel-title">Countries</h3>
      <div v-if="countryAggregations.length === 0" class="empty-state">
        No utilization data for this month
      </div>
      <div
        v-for="agg in countryAggregations"
        :key="agg.country"
        class="country-row"
        data-testid="country-row"
      >
        <div class="country-info">
          <img v-if="agg.flagUrl" :src="agg.flagUrl" class="country-flag" :alt="agg.country" />
          <div>
            <div class="country-name">{{ agg.country }}</div>
            <div class="country-meta">
              {{ agg.architectCount }} architect{{ agg.architectCount !== 1 ? 's' : '' }}
              <span v-if="!agg.coords" class="location-unknown"> · Location unknown</span>
            </div>
          </div>
        </div>
        <div class="country-util" :style="{ color: utilizationTextColor(agg.avgUtilization) }">
          {{ agg.avgUtilization }}%
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.world-map-view { flex: 1; display: flex; min-height: 0; gap: 0; }
.map-container { flex: 1; min-width: 0; border-radius: 6px; overflow: hidden; border: 1px solid #e2e8f0; }
.side-panel { width: 240px; flex-shrink: 0; background: #fff; border: 1px solid #e2e8f0; border-left: none; border-radius: 0 6px 6px 0; overflow-y: auto; padding: 0.75rem; }
.side-panel-title { margin: 0 0 0.75rem 0; font-size: 0.95rem; color: #334155; }
.empty-state { color: #94a3b8; font-size: 0.85rem; text-align: center; padding: 2rem 0; }
.country-row { display: flex; justify-content: space-between; align-items: center; padding: 0.5rem 0; border-bottom: 1px solid #f1f5f9; cursor: pointer; }
.country-row:hover { background: #f8fafc; }
.country-row:last-child { border-bottom: none; }
.country-info { display: flex; align-items: center; gap: 0.5rem; min-width: 0; }
.country-flag { width: 20px; height: 14px; object-fit: cover; border-radius: 2px; flex-shrink: 0; }
.country-name { font-size: 0.85rem; font-weight: 600; color: #1e293b; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.country-meta { font-size: 0.7rem; color: #94a3b8; }
.location-unknown { color: #f59e0b; }
.country-util { font-size: 0.9rem; font-weight: 700; flex-shrink: 0; }

:deep(.bubble-label) { background: none !important; border: none !important; box-shadow: none !important; color: #fff; font-size: 10px; font-weight: 700; text-shadow: 1px 1px 2px rgba(0,0,0,0.8); white-space: nowrap; }
.popup-content { min-width: 220px; max-width: 300px; font-family: system-ui, sans-serif; }
.popup-header { display: flex; align-items: center; gap: 6px; padding-bottom: 8px; border-bottom: 1px solid #e2e8f0; margin-bottom: 8px; }
.popup-flag { width: 20px; height: 14px; object-fit: cover; border-radius: 2px; }
.popup-avg { margin-left: auto; font-weight: 600; font-size: 0.85rem; }
.popup-architect { margin-bottom: 8px; }
.popup-architect:last-child { margin-bottom: 0; }
.popup-arch-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 2px; font-weight: 600; font-size: 0.85rem; }
.popup-arch-name { color: #334155; }
.popup-assignments { padding-left: 8px; border-left: 2px solid #e2e8f0; }
.popup-assignment { display: flex; justify-content: space-between; font-size: 0.75rem; color: #64748b; padding: 1px 0; }
</style>
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd frontend && npx vitest run src/__tests__/WorldMapView.spec.ts
```

Expected: All 4 tests PASS.

- [ ] **Step 5: Run all tests to check for regressions**

```bash
cd frontend && npx vitest run
```

Expected: All tests pass.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/WorldMapView.vue frontend/src/__tests__/WorldMapView.spec.ts
git commit -m "feat: implement WorldMapView with Leaflet map, side panel, and hover popups"
```

---

### Task 5: Add Side Panel Click-to-Pan and Verify Full Integration

**Files:**
- Modify: `frontend/src/components/WorldMapView.vue`
- Modify: `frontend/src/__tests__/WorldMapView.spec.ts`

- [ ] **Step 1: Write the failing test for click-to-pan**

Add to the existing `describe` block in `frontend/src/__tests__/WorldMapView.spec.ts`:

```typescript
  it('emits panTo when clicking a country row with known coords', async () => {
    const wrapper = mount(WorldMapView, {
      props: { usageData: testUsageData, month: '2026-05' },
    })

    const rows = wrapper.findAll('[data-testid="country-row"]')
    const australiaRow = rows.find(r => r.text().includes('Australia'))!
    await australiaRow.trigger('click')

    expect(wrapper.emitted('panTo')).toBeTruthy()
    expect(wrapper.emitted('panTo')![0]).toEqual([[-25.27, 133.78]])
  })
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd frontend && npx vitest run src/__tests__/WorldMapView.spec.ts
```

Expected: FAIL — `panTo` event not emitted.

- [ ] **Step 3: Add panTo emit and click handler**

In `frontend/src/components/WorldMapView.vue`, add after the `defineProps`:

```typescript
const emit = defineEmits<{
  panTo: [coords: [number, number]]
}>()

function onCountryClick(agg: CountryAggregation) {
  if (agg.coords) {
    emit('panTo', agg.coords)
  }
}
```

In the template, update the country-row div to add the click handler:

```html
        @click="onCountryClick(agg)"
```

Add this to the existing `@click` on the `country-row` div.

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd frontend && npx vitest run src/__tests__/WorldMapView.spec.ts
```

Expected: All 5 tests PASS.

- [ ] **Step 5: Type-check the full frontend**

```bash
cd frontend && npx vue-tsc --build --force
```

Expected: No type errors.

- [ ] **Step 6: Run all tests**

```bash
cd frontend && npx vitest run
```

Expected: All tests pass.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/components/WorldMapView.vue frontend/src/__tests__/WorldMapView.spec.ts
git commit -m "feat: add click-to-pan from side panel country rows to map"
```

---

### Task 6: Manual Browser Testing and Polish

**Files:**
- Possibly tweak: `frontend/src/components/WorldMapView.vue`, `frontend/src/views/UsageTimelineView.vue`

- [ ] **Step 1: Start the backend**

```bash
cd backend && ./gradlew bootRun &
```

- [ ] **Step 2: Start the frontend dev server**

```bash
cd frontend && npm run dev
```

- [ ] **Step 3: Test in browser**

Open `http://localhost:5173/timeline` and verify:
1. Tab bar shows "Timeline" and "Map" tabs
2. Timeline tab works as before (no regressions)
3. Clicking "Map" tab shows the Leaflet map with CartoDB dark tiles
4. Bubbles appear for countries that have architects with assignments in the selected month
5. Bubble sizes vary by architect count
6. Bubble colors match utilization thresholds (green/amber/red/grey)
7. Hovering a bubble shows the popup with architect details and assignment breakdowns
8. Side panel lists countries sorted by utilization
9. Clicking a side panel row pans the map
10. Month prev/next navigation works and updates both map and side panel
11. Country filter works — filtering to a specific country shows only that country's data
12. Name filter works — filtering reduces the aggregated data
13. Switching between tabs preserves the selected month
14. Edge case: a month with no data shows empty state message

- [ ] **Step 4: Fix any visual polish issues found during testing**

Adjust CSS, sizing, colors as needed based on real browser rendering.

- [ ] **Step 5: Commit any polish fixes**

```bash
git add -u
git commit -m "fix: polish world map view after browser testing"
```
