<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { teamMemberApi } from '@/api/client'
import type { TeamMemberUsage, AssignmentStatus } from '@/types'
import { buildTimelineLayout, computeExtendPlan, type LayoutPill, type LayoutRow } from '@/composables/useTimelineLayout'

const props = defineProps<{ usageData: TeamMemberUsage[]; months: string[]; zoom: number }>()

const emit = defineEmits<{
  drop: [teamMemberId: number, month: string, data: { customerId: number; status: AssignmentStatus; defaultUsagePercent: number; usagePercent?: number }]
  unassign: [assignmentIds: number[]]
  'edit-usage': [assignmentIds: number[], usage: number]
  extend: [{ teamMemberId: number; customerId: number; usagePercent: number; status: AssignmentStatus; addMonths: string[]; removeAssignmentIds: number[]; replaceMonths: { month: string; oldAssignmentId: number }[] }]
  'set-status': [assignmentIds: number[], status: AssignmentStatus]
}>()

const layout = computed(() => buildTimelineLayout(props.usageData, props.months, props.zoom))

const editing = ref<{ assignmentIds: number[]; usage: number; el: HTMLElement } | null>(null)
const hoverPhoto = ref<{ src: string; top: number; left: number } | null>(null)

const drag = ref<{
  row: LayoutRow; pill: LayoutPill; edge: 'l' | 'r'; areaEl: HTMLElement
  previewStart: number; previewSpan: number
} | null>(null)

const conflict = ref<{
  teamMemberId: number; pill: LayoutPill
  base: { addMonths: string[]; removeAssignmentIds: number[] }
  months: { month: string; oldAssignmentId: number }[]
} | null>(null)

const STATUS_OPTIONS: { value: AssignmentStatus; label: string; swatch: string }[] = [
  { value: 'CONFIRMED', label: 'Confirmed', swatch: '#bfdbfe' },
  { value: 'PROBABLE', label: 'Probable', swatch: '#fed7aa' },
  { value: 'POTENTIAL', label: 'Potential', swatch: '#fef9c3' },
]

const statusMenu = ref<{ assignmentIds: number[]; current: AssignmentStatus; x: number; y: number } | null>(null)

function openStatusMenu(event: MouseEvent, pill: LayoutPill) {
  event.preventDefault()
  event.stopPropagation()
  statusMenu.value = { assignmentIds: pill.assignmentIds, current: pill.status, x: event.clientX, y: event.clientY }
}

function pickStatus(status: AssignmentStatus) {
  if (statusMenu.value) emit('set-status', statusMenu.value.assignmentIds, status)
  statusMenu.value = null
}

function closeStatusMenu() { statusMenu.value = null }

function onKey(e: KeyboardEvent) { if (e.key === 'Escape') closeStatusMenu() }
onMounted(() => document.addEventListener('keydown', onKey))
onUnmounted(() => document.removeEventListener('keydown', onKey))

// Map monthIdx -> existing assignmentId for this team member+customer across the window.
function existingForCustomer(row: LayoutRow, customerId: number): Map<number, number> {
  const m = new Map<number, number>()
  const lane = row.lanes.find(l => l.customerId === customerId)
  if (!lane) return m
  for (const p of lane.pills) {
    for (let k = 0; k < p.span; k++) m.set(p.startIdx + k, p.assignmentIds[k])
  }
  return m
}

function startDrag(event: PointerEvent, row: LayoutRow, pill: LayoutPill, edge: 'l' | 'r') {
  event.stopPropagation()
  event.preventDefault()
  const areaEl = (event.currentTarget as HTMLElement).closest('.lane-area') as HTMLElement
  drag.value = { row, pill, edge, areaEl, previewStart: pill.startIdx, previewSpan: pill.span }
  window.addEventListener('pointermove', onDragMove)
  window.addEventListener('pointerup', onDragEnd, { once: true })
}

function pointerIdx(d: NonNullable<typeof drag.value>, clientX: number): number {
  const rect = d.areaEl.getBoundingClientRect()
  const frac = (clientX - rect.left) / rect.width
  const idx = Math.floor(frac * props.months.length)
  return Math.max(0, Math.min(props.months.length - 1, idx))
}

function onDragMove(event: PointerEvent) {
  const d = drag.value
  if (!d) return
  const idx = pointerIdx(d, event.clientX)
  if (d.edge === 'r') {
    const start = d.pill.startIdx
    d.previewStart = start
    d.previewSpan = Math.max(1, idx - start + 1)
  } else {
    const end = d.pill.startIdx + d.pill.span - 1
    d.previewStart = Math.min(idx, end)
    d.previewSpan = end - d.previewStart + 1
  }
}

function onDragEnd() {
  window.removeEventListener('pointermove', onDragMove)
  const d = drag.value
  drag.value = null
  if (!d) return
  if (d.previewStart === d.pill.startIdx && d.previewSpan === d.pill.span) return
  applyExtend(d.pill, d.previewStart, d.previewSpan, d.row.teamMemberId, existingForCustomer(d.row, d.pill.customerId))
}

// Exposed for tests and called by onDragEnd. existingByIdx defaults to the customer's real occupancy.
function applyExtend(
  pill: LayoutPill, newStartIdx: number, newSpan: number, teamMemberId: number,
  existingByIdx?: Map<number, number>,
) {
  const row = layout.value.find(r => r.teamMemberId === teamMemberId)!
  const existing = existingByIdx ?? existingForCustomer(row, pill.customerId)
  const plan = computeExtendPlan(pill, newStartIdx, newSpan, props.months, existing)
  if (plan.conflicts.length > 0) {
    conflict.value = {
      teamMemberId, pill,
      base: { addMonths: plan.addMonths, removeAssignmentIds: plan.removeAssignmentIds },
      months: plan.conflicts,
    }
    return
  }
  emitExtend(teamMemberId, pill, plan.addMonths, plan.removeAssignmentIds, [])
}

function emitExtend(
  teamMemberId: number, pill: LayoutPill,
  addMonths: string[], removeAssignmentIds: number[],
  replaceMonths: { month: string; oldAssignmentId: number }[],
) {
  emit('extend', {
    teamMemberId, customerId: pill.customerId, usagePercent: pill.usage, status: pill.status,
    addMonths, removeAssignmentIds, replaceMonths,
  })
}

function resolveConflict(mode: 'replace' | 'skip' | 'cancel') {
  const c = conflict.value
  conflict.value = null
  if (!c || mode === 'cancel') return
  const replaceMonths = mode === 'replace' ? c.months : []
  emitExtend(c.teamMemberId, c.pill, c.base.addMonths, c.base.removeAssignmentIds, replaceMonths)
}

defineExpose({ applyExtend })

function onAvatarEnter(event: MouseEvent, teamMemberId: number) {
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  hoverPhoto.value = { src: teamMemberApi.photoUrl(teamMemberId), top: rect.top - 20, left: rect.right + 8 }
}
function onAvatarLeave() { hoverPhoto.value = null }

function openEditor(event: MouseEvent, pill: LayoutPill) {
  event.stopPropagation()
  if (editing.value) saveUsage()
  editing.value = { assignmentIds: pill.assignmentIds, usage: pill.usage, el: event.currentTarget as HTMLElement }
}
function onSlider(value: number) { if (editing.value) editing.value.usage = value }
function saveUsage() {
  if (!editing.value) return
  emit('edit-usage', editing.value.assignmentIds, editing.value.usage)
  editing.value = null
}
function closeEditor() { if (editing.value) saveUsage() }

function onRootClick() { closeEditor(); closeStatusMenu() }

function usageColor(total: number): string {
  if (total === 0) return 'transparent'
  if (total >= 50 && total <= 70) return '#dcfce7'
  if (total > 30 && total < 50) return '#fef3c7'
  return '#fecaca'
}
function usageLabelColor(total: number): string {
  if (total === 0) return '#94a3b8'
  if (total >= 50 && total <= 70) return '#16a34a'
  if (total > 30 && total < 50) return '#d97706'
  return '#dc2626'
}
function formatMonth(ym: string): string {
  const [year, month] = ym.split('-')
  const names = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
  return `${names[parseInt(month) - 1]} ${year.slice(2)}`
}

function onDragOver(event: DragEvent) {
  event.preventDefault()
  event.dataTransfer!.dropEffect = 'copy'
}
function onDrop(event: DragEvent, teamMemberId: number, month: string) {
  event.preventDefault()
  const raw = event.dataTransfer!.getData('application/json')
  if (!raw) return
  const data = JSON.parse(raw)
  if (data.type === 'customer') {
    emit('drop', teamMemberId, month, {
      customerId: data.customerId,
      status: data.status,
      defaultUsagePercent: data.defaultUsagePercent,
    })
  }
}
</script>

<template>
  <div class="timeline-grid" @click="onRootClick">
    <div class="grid-head">
      <div class="team-member-col head">Team Members</div>
      <div class="months-head">
        <div v-for="month in months" :key="month" class="month-col">{{ formatMonth(month) }}</div>
      </div>
    </div>

    <div v-for="row in layout" :key="row.teamMemberId" class="grid-row card">
      <div class="team-member-col">
        <div class="team-member-info">
          <img :src="teamMemberApi.photoUrl(row.teamMemberId)" class="team-member-avatar"
            @mouseenter="onAvatarEnter($event, row.teamMemberId)" @mouseleave="onAvatarLeave"
            @error="($event.target as HTMLImageElement).style.display = 'none'" />
          <div>
            <div class="team-member-name">{{ row.teamMemberName.split(' ')[0] }}</div>
            <div class="team-member-name">{{ row.teamMemberName.split(' ').slice(1).join(' ') }}</div>
            <div class="team-member-country">{{ row.country }}</div>
          </div>
        </div>
      </div>

      <div class="lane-area" :style="{ height: row.rowHeightPx + 'px' }">
        <!-- per-month remaining-capacity blocks + drop targets -->
        <div class="cell-grid">
          <div v-for="mo in row.months" :key="mo.month" class="bg-cell"
            @dragover="onDragOver" @drop="onDrop($event, row.teamMemberId, mo.month)">
            <div class="remaining" :class="{ over: mo.over }"
              :data-testid="`remaining-${mo.month}`"
              :style="{
                height: (mo.over ? 0 : Math.max(0, 100 - mo.total)) + '%',
                background: mo.over ? undefined : usageColor(mo.total),
              }">
              <span class="total-label"
                :style="{ color: mo.over ? '#dc2626' : usageLabelColor(mo.total) }">
                {{ mo.total }}%
              </span>
            </div>
          </div>
        </div>

        <!-- pill layer (continuous, bottom-anchored lanes) -->
        <div v-for="lane in row.lanes" :key="lane.customerId" class="lane"
          :style="{ bottom: lane.bottomOffsetPx + 'px', height: lane.laneHeightPx + 'px' }">
          <div v-for="pill in lane.pills" :key="pill.startIdx" data-testid="pill"
            class="pill" :class="{ 'status-probable': pill.status === 'PROBABLE', 'status-potential': pill.status === 'POTENTIAL' }"
            :style="{
              left: (pill.leftPct * 100) + '%',
              width: (pill.widthPct * 100) + '%',
              height: pill.heightPx + 'px',
            }"
            :title="`${pill.customerName} - ${pill.usage}%`"
            @click.stop="openEditor($event, pill)"
            @contextmenu="openStatusMenu($event, pill)">
            <span class="grip grip-l" @pointerdown="startDrag($event, row, pill, 'l')"></span>
            <span class="pill-label">{{ pill.customerName }} {{ pill.usage }}%</span>
            <button class="pill-unassign" data-testid="pill-unassign"
              @click.stop="emit('unassign', pill.assignmentIds)" title="Unassign">&minus;</button>
            <span class="grip grip-r" @pointerdown="startDrag($event, row, pill, 'r')"></span>
          </div>
        </div>
        <div v-if="drag && drag.row.teamMemberId === row.teamMemberId" class="drag-preview" :style="{
          left: (drag.previewStart / months.length * 100) + '%',
          width: (drag.previewSpan / months.length * 100) + '%',
        }"></div>
      </div>
    </div>

    <div v-if="editing" class="usage-editor-overlay" @click="closeEditor">
      <div class="usage-editor" @click.stop :style="{
        top: (editing.el.getBoundingClientRect().bottom + 4) + 'px',
        left: editing.el.getBoundingClientRect().left + 'px',
      }">
        <div class="usage-editor-value">{{ editing.usage }}%</div>
        <input type="range" min="0" max="100" step="5" :value="editing.usage"
          @input="onSlider(Number(($event.target as HTMLInputElement).value))" />
        <div class="usage-editor-labels"><span>0%</span><span>50%</span><span>100%</span></div>
        <button class="usage-editor-save" data-testid="usage-apply" @click="saveUsage">Apply</button>
      </div>
    </div>
    <Teleport to="body">
      <div v-if="hoverPhoto" class="avatar-popup" :style="{ top: hoverPhoto.top + 'px', left: hoverPhoto.left + 'px' }">
        <img :src="hoverPhoto.src" />
      </div>
    </Teleport>
    <div v-if="conflict" class="conflict-overlay" @click="resolveConflict('cancel')">
      <div class="conflict-popup" data-testid="conflict-popup" @click.stop>
        <p><strong>{{ conflict.months.length }} month(s)</strong> already have
          {{ conflict.pill.customerName }}.</p>
        <p>Replace them with the extended {{ conflict.pill.usage }}%?</p>
        <div class="conflict-btns">
          <button class="primary" data-testid="conflict-replace" @click="resolveConflict('replace')">Replace</button>
          <button data-testid="conflict-skip" @click="resolveConflict('skip')">Skip month</button>
          <button data-testid="conflict-cancel" @click="resolveConflict('cancel')">Cancel</button>
        </div>
      </div>
    </div>
    <div v-if="statusMenu" class="status-menu-overlay" @click="closeStatusMenu" @contextmenu.prevent>
      <div class="status-menu" data-testid="status-menu" @click.stop
        :style="{ top: statusMenu.y + 'px', left: statusMenu.x + 'px' }">
        <button v-for="opt in STATUS_OPTIONS" :key="opt.value"
          class="status-option" :data-testid="`status-option-${opt.value}`"
          @click="pickStatus(opt.value)">
          <span class="status-swatch" :style="{ background: opt.swatch }"></span>
          <span class="status-label">{{ opt.label }}</span>
          <span v-if="statusMenu.current === opt.value" class="status-check">✓</span>
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.timeline-grid { overflow: auto; background: #f1f5f9; border: 1px solid #e2e8f0; border-radius: 6px; flex: 1; min-height: 0; padding: 0 6px 6px; }
.grid-head { display: flex; position: sticky; top: 0; z-index: 2; background: #f8fafc; border-bottom: 1px solid #e2e8f0; }
.grid-row { display: flex; }
.grid-row.card { margin-top: 6px; border-radius: 6px; outline: 1px solid #e2e8f0; outline-offset: -1px; box-shadow: 0 1px 2px rgba(0,0,0,0.06); background: #fff; }
.team-member-col { width: 160px; flex-shrink: 0; position: sticky; left: 0; background: #f8fafc; z-index: 1; padding: 4px; box-sizing: border-box; }
.team-member-col.head { display: flex; align-items: center; font-weight: 600; font-size: 0.8rem; }
.team-member-col { border-radius: 6px 0 0 6px; }
.team-member-info { display: flex; align-items: center; gap: 8px; }
.team-member-avatar { width: 36px; height: 36px; border-radius: 50%; object-fit: cover; flex-shrink: 0; border: 2px solid #e2e8f0; cursor: pointer; }
.team-member-name { font-weight: 600; font-size: 0.85rem; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.team-member-country { font-size: 0.75rem; color: #64748b; }
.months-head { display: grid; grid-auto-flow: column; grid-auto-columns: 1fr; column-gap: 4px; flex: 1; }
.month-col { text-align: center; font-size: 0.8rem; padding: 4px 0; }
.lane-area { position: relative; flex: 1; min-width: 0; border-radius: 0 6px 6px 0; overflow: hidden; }
.cell-grid { position: absolute; inset: 0; display: grid; grid-auto-flow: column; grid-auto-columns: 1fr; column-gap: 4px; }
.bg-cell { position: relative; display: flex; flex-direction: column; transition: background 0.15s; }
.bg-cell:hover { outline: 2px dashed #3b82f6; outline-offset: -2px; }
.remaining { width: 100%; position: relative; transition: height 0.15s; }
.remaining.over { box-shadow: inset 0 3px 0 #dc2626; }
.total-label { position: absolute; top: 1px; right: 3px; font-size: 0.7rem; font-weight: 700; }
.lane { position: absolute; left: 0; right: 0; }
.pill { position: absolute; bottom: 0; box-sizing: border-box; font-size: 0.7rem; padding: 1px 4px; border-radius: 3px;
  background: #bfdbfe; border: 1px solid #93c5fd; white-space: nowrap; overflow: hidden; display: flex; align-items: flex-end;
  gap: 2px; cursor: pointer; }
.pill:hover { background: #93c5fd; }
.pill.status-probable { background: #fed7aa; border: 1px dashed #fb923c; }
.pill.status-probable:hover { background: #fdba74; }
.pill.status-potential { background: #fef9c3; border: 1px dotted #facc15; }
.pill.status-potential:hover { background: #fde68a; }
.pill-label { overflow: hidden; text-overflow: ellipsis; flex: 1; }
.pill-unassign { background: none; border: none; color: #94a3b8; cursor: pointer; font-size: 0.85rem; font-weight: 700; line-height: 1; padding: 0 2px; flex-shrink: 0; border-radius: 3px; }
.pill-unassign:hover { color: #dc2626; background: rgba(220,38,38,0.1); }
.usage-editor-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; z-index: 200; }
.usage-editor { position: fixed; background: #fff; border: 1px solid #e2e8f0; border-radius: 8px; padding: 12px; box-shadow: 0 4px 24px rgba(0,0,0,0.15); width: 180px; z-index: 201; }
.usage-editor-value { text-align: center; font-size: 1.1rem; font-weight: 700; margin-bottom: 6px; color: #0f172a; }
.usage-editor input[type="range"] { width: 100%; accent-color: #3b82f6; }
.usage-editor-labels { display: flex; justify-content: space-between; font-size: 0.7rem; color: #94a3b8; margin-top: 2px; }
.usage-editor-save { width: 100%; margin-top: 8px; padding: 4px; font-size: 0.8rem; background: #3b82f6; color: #fff; border: none; border-radius: 4px; cursor: pointer; }
.usage-editor-save:hover { background: #2563eb; }
.pill .grip { position: absolute; top: 0; bottom: 0; width: 7px; cursor: ew-resize; opacity: 0; background: #2563eb; border-radius: 3px; }
.pill:hover .grip { opacity: 0.85; }
.grip-l { left: -3px; }
.grip-r { right: -3px; }
.drag-preview { position: absolute; top: 0; bottom: 0; background: #eff6ff; outline: 2px dashed #3b82f6; outline-offset: -2px; pointer-events: none; z-index: 1; }
.conflict-overlay { position: fixed; inset: 0; z-index: 210; background: rgba(15,23,42,0.15); }
.conflict-popup { position: fixed; top: 40%; left: 50%; transform: translateX(-50%); background: #fff; border: 1px solid #e2e8f0; border-radius: 8px; box-shadow: 0 6px 24px rgba(0,0,0,0.18); padding: 14px; width: 240px; font-size: 0.85rem; z-index: 211; }
.conflict-btns { display: flex; gap: 6px; margin-top: 10px; }
.conflict-btns button { font-size: 0.8rem; padding: 4px 8px; border-radius: 4px; border: 1px solid #cbd5e1; background: #f8fafc; cursor: pointer; }
.conflict-btns button.primary { background: #3b82f6; color: #fff; border-color: #3b82f6; }
.status-menu-overlay { position: fixed; inset: 0; z-index: 220; }
.status-menu { position: fixed; background: #fff; border: 1px solid #e2e8f0; border-radius: 8px; box-shadow: 0 6px 24px rgba(0,0,0,0.18); padding: 4px; z-index: 221; min-width: 150px; }
.status-option { display: flex; align-items: center; gap: 8px; width: 100%; padding: 6px 8px; border: none; background: none; cursor: pointer; font-size: 0.8rem; border-radius: 4px; text-align: left; }
.status-option:hover { background: #f1f5f9; }
.status-swatch { width: 14px; height: 14px; border-radius: 3px; border: 1px solid #cbd5e1; flex-shrink: 0; }
.status-label { flex: 1; }
.status-check { color: #2563eb; font-weight: 700; }
</style>

<style>
.avatar-popup { position: fixed; z-index: 300; background: #fff; border: 1px solid #e2e8f0; border-radius: 8px; box-shadow: 0 4px 24px rgba(0,0,0,0.2); padding: 4px; pointer-events: none; }
.avatar-popup img { width: 200px; height: 200px; object-fit: cover; border-radius: 6px; display: block; }
</style>
