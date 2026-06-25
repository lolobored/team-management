<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useUsageStore } from '@/stores/usage'
import { useCustomersStore } from '@/stores/customers'
import { useAssignmentsStore } from '@/stores/assignments'
import { useTeamMembersStore } from '@/stores/teamMembers'
import { useGeoStore } from '@/stores/geo'
import { assignmentApi, usageApi } from '@/api/client'
import type { AssignmentStatus } from '@/types'
import TimelineGrid from '@/components/TimelineGrid.vue'
import WorldMapView from '@/components/WorldMapView.vue'
import CustomerSidebar from '@/components/CustomerSidebar.vue'

const usageStore = useUsageStore()
const customerStore = useCustomersStore()
const assignmentStore = useAssignmentsStore()
const teamMemberStore = useTeamMembersStore()
const geo = useGeoStore()

const countryFilter = ref('')
const toast = ref('')

const teamMemberCountries = computed(() => {
  const countries = new Set<string>()
  for (const a of teamMemberStore.teamMembers) {
    if (a.country) countries.add(a.country)
  }
  return [...countries].sort()
})
const nameFilter = ref('')
const activeTab = ref<'timeline' | 'map'>('timeline')

function currentYearMonth(): string {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

function addMonths(ym: string, n: number): string {
  const [y, m] = ym.split('-').map(Number)
  const date = new Date(y, m - 1 + n)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
}

const fromMonth = ref(currentYearMonth())
const monthCount = ref(6)
const toMonth = computed(() => addMonths(fromMonth.value, monthCount.value - 1))
const zoom = ref(Number(typeof localStorage !== 'undefined' ? localStorage.getItem('timeline.zoom') : null) || 160)
watch(zoom, (v) => { if (typeof localStorage !== 'undefined') localStorage.setItem('timeline.zoom', String(v)) })

const months = computed(() => {
  const result: string[] = []
  let current = fromMonth.value
  while (current <= toMonth.value) {
    result.push(current)
    current = addMonths(current, 1)
  }
  return result
})

const filteredUsage = computed(() => {
  let data = [...usageStore.usageData].sort((a, b) =>
    a.teamMemberName.localeCompare(b.teamMemberName)
  )
  if (nameFilter.value) {
    const search = nameFilter.value.toLowerCase()
    data = data.filter(a => a.teamMemberName.toLowerCase().includes(search))
  }
  return data
})

async function loadData() {
  await Promise.all([
    usageStore.fetchUsage(fromMonth.value, toMonth.value, countryFilter.value || undefined),
    customerStore.fetchAll(),
    teamMemberStore.fetchAll(),
    geo.fetchCountries(),
  ])
}

onMounted(loadData)
watch([countryFilter, fromMonth, monthCount], loadData)

function showToast(message: string) {
  toast.value = message
  setTimeout(() => { toast.value = '' }, 3000)
}

async function onDrop(
  teamMemberId: number,
  month: string,
  data: { customerId: number; status: AssignmentStatus; defaultUsagePercent: number; usagePercent?: number }
) {
  try {
    await assignmentStore.create({
      teamMemberId,
      customerId: data.customerId,
      usagePercent: data.usagePercent ?? data.defaultUsagePercent,
      status: data.status,
      month,
    })
  } catch (e: any) {
    if (e.response?.status === 409) {
      showToast('Already assigned for this month')
      return
    }
    throw e
  }
  await loadData()
}

async function onUnassign(assignmentIds: number[]) {
  await Promise.all(assignmentIds.map(id => assignmentApi.delete(id)))
  await loadData()
}

async function onEditUsage(assignmentIds: number[], usage: number) {
  await Promise.all(assignmentIds.map(id => assignmentApi.update(id, { usagePercent: usage })))
  await loadData()
}

async function onExtend(plan: {
  teamMemberId: number; customerId: number; usagePercent: number; status: AssignmentStatus
  addMonths: string[]; removeAssignmentIds: number[]
  replaceMonths: { month: string; oldAssignmentId: number }[]
}) {
  // deletes first (shrink + replace), then creates (add + replace)
  try {
    await Promise.all([
      ...plan.removeAssignmentIds.map(id => assignmentApi.delete(id)),
      ...plan.replaceMonths.map(r => assignmentApi.delete(r.oldAssignmentId)),
    ])
    const createMonths = [...plan.addMonths, ...plan.replaceMonths.map(r => r.month)]
    await Promise.all(createMonths.map(month => assignmentStore.create({
      teamMemberId: plan.teamMemberId,
      customerId: plan.customerId,
      usagePercent: plan.usagePercent,
      status: plan.status,
      month,
    })))
  } catch (e: any) {
    if (e.response?.status === 409) {
      showToast('Some assignments could not be updated')
    } else {
      throw e
    }
  } finally {
    await loadData()
  }
}

async function onSetStatus(assignmentIds: number[], status: AssignmentStatus) {
  try {
    await Promise.all(assignmentIds.map(id => assignmentApi.update(id, { status })))
  } catch {
    showToast('Could not update status')
  } finally {
    await loadData()
  }
}

const exporting = ref(false)

async function exportToExcel() {
  exporting.value = true
  try {
    await usageApi.exportExcel(fromMonth.value, toMonth.value, countryFilter.value || undefined)
  } finally {
    exporting.value = false
  }
}
</script>

<template>
  <div class="timeline-view">
    <div class="main-area">
      <div class="header">
        <h1>Usage Timeline</h1>
        <div class="controls">
          <input v-model="nameFilter" placeholder="Filter by name..." data-testid="name-filter" />
          <select v-model="countryFilter" data-testid="country-filter">
            <option value="">All countries</option>
            <option v-for="c in teamMemberCountries" :key="c" :value="c">{{ c }}</option>
          </select>
          <template v-if="activeTab === 'timeline'">
            <div class="period-nav">
              <button class="month-nav-btn" data-testid="period-prev" title="Previous period"
                @click="fromMonth = addMonths(fromMonth, -monthCount)">&laquo;</button>
              <label>From: <input v-model="fromMonth" type="month" /></label>
              <button class="month-nav-btn" data-testid="period-next" title="Next period"
                @click="fromMonth = addMonths(fromMonth, monthCount)">&raquo;</button>
            </div>
            <label>Months:
              <select v-model.number="monthCount">
                <option :value="3">3</option>
                <option :value="6">6</option>
                <option :value="9">9</option>
                <option :value="12">12</option>
              </select>
            </label>
            <label>Zoom:
              <input type="range" min="80" max="320" step="20" v-model.number="zoom" data-testid="zoom-slider" />
            </label>
            <button class="export-btn" @click="exportToExcel" :disabled="exporting">
              {{ exporting ? 'Exporting...' : 'Export Excel' }}
            </button>
            <div class="status-legend">
              <span class="legend-item"><span class="legend-swatch confirmed"></span>Confirmed</span>
              <span class="legend-item"><span class="legend-swatch probable"></span>Probable</span>
              <span class="legend-item"><span class="legend-swatch potential"></span>Potential</span>
            </div>
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
        :zoom="zoom"
        @drop="onDrop"
        @unassign="onUnassign"
        @edit-usage="onEditUsage"
        @extend="onExtend"
        @set-status="onSetStatus"
      />
      <WorldMapView
        v-else
        :usage-data="filteredUsage"
        :month="fromMonth"
      />
    </div>
    <CustomerSidebar
      v-if="activeTab === 'timeline'"
      :customers="customerStore.customers"
    />
    <div v-if="toast" class="toast">{{ toast }}</div>
  </div>
</template>

<style scoped>
.timeline-view { display: flex; gap: 0; height: calc(100vh - 3rem); position: relative; }
.main-area { flex: 1; min-width: 0; overflow: hidden; display: flex; flex-direction: column; }
.header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem; flex-shrink: 0; }
.controls { display: flex; gap: 0.5rem; align-items: center; }
.controls label { display: flex; align-items: center; gap: 0.25rem; font-size: 0.85rem; }
.tab-bar { display: flex; gap: 0; margin-bottom: 0.75rem; border-bottom: 2px solid #e2e8f0; flex-shrink: 0; }
.tab-btn { padding: 0.5rem 1.25rem; border: none; background: none; cursor: pointer; font-size: 0.9rem; color: #64748b; border-bottom: 2px solid transparent; margin-bottom: -2px; transition: color 0.15s, border-color 0.15s; }
.tab-btn:hover { color: #334155; }
.tab-btn.active { color: #0f172a; border-bottom-color: #3b82f6; font-weight: 600; }
.month-nav { display: flex; align-items: center; gap: 0.25rem; }
.period-nav { display: flex; align-items: center; gap: 0.25rem; }
.month-nav-btn { padding: 0.25rem 0.5rem; background: #e2e8f0; border: 1px solid #cbd5e1; border-radius: 4px; cursor: pointer; font-size: 1rem; line-height: 1; }
.month-nav-btn:hover { background: #cbd5e1; }
.export-btn { padding: 0.35rem 0.75rem; background: #059669; color: #fff; border: none; border-radius: 4px; cursor: pointer; font-size: 0.8rem; font-weight: 600; }
.export-btn:hover { background: #047857; }
.export-btn:disabled { opacity: 0.6; cursor: not-allowed; }
.toast { position: fixed; bottom: 1.5rem; left: 50%; transform: translateX(-50%); background: #334155; color: #fff; padding: 0.5rem 1.25rem; border-radius: 6px; font-size: 0.85rem; z-index: 300; box-shadow: 0 4px 12px rgba(0,0,0,0.2); }
.status-legend { display: flex; gap: 0.75rem; align-items: center; font-size: 0.75rem; color: #475569; }
.legend-item { display: flex; align-items: center; gap: 0.25rem; }
.legend-swatch { width: 12px; height: 12px; border-radius: 3px; display: inline-block; }
.legend-swatch.confirmed { background: #bfdbfe; border: 1px solid #93c5fd; }
.legend-swatch.probable { background: #fed7aa; border: 1px dashed #fb923c; }
.legend-swatch.potential { background: #fef9c3; border: 1px dotted #facc15; }
</style>
