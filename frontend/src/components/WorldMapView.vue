<script setup lang="ts">
import { computed } from 'vue'
import { LMap, LTileLayer, LCircleMarker, LPopup, LTooltip } from '@vue-leaflet/vue-leaflet'
import 'leaflet/dist/leaflet.css'
import { useGeoStore } from '@/stores/geo'
import type { TeamMemberUsage, AssignmentStatus } from '@/types'

const props = defineProps<{
  usageData: TeamMemberUsage[]
  month: string
}>()

const emit = defineEmits<{
  panTo: [coords: [number, number]]
}>()

const geo = useGeoStore()

interface TeamMemberDetail {
  teamMemberId: number
  teamMemberName: string
  total: number
  assignments: { customerName: string; usage: number; status: AssignmentStatus }[]
}

interface CountryAggregation {
  country: string
  avgUtilization: number
  teamMemberCount: number
  teamMembers: TeamMemberDetail[]
  coords: [number, number] | null
  flagUrl: string | null
}

const countryAggregations = computed<CountryAggregation[]>(() => {
  const byCountry = new Map<string, TeamMemberDetail[]>()

  for (const member of props.usageData) {
    const monthData = member.months[props.month]
    if (!monthData) continue

    const country = member.country || 'Unknown'
    if (!byCountry.has(country)) byCountry.set(country, [])
    byCountry.get(country)!.push({
      teamMemberId: member.teamMemberId,
      teamMemberName: member.teamMemberName,
      total: monthData.total,
      assignments: monthData.assignments.map(a => ({
        customerName: a.customerName,
        usage: a.usage,
        status: a.status,
      })),
    })
  }

  const result: CountryAggregation[] = []
  for (const [country, members] of byCountry) {
    const totalUtil = members.reduce((sum, a) => sum + a.total, 0)
    const avg = Math.round(totalUtil / members.length)
    result.push({
      country,
      avgUtilization: avg,
      teamMemberCount: members.length,
      teamMembers: members,
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
  if (total === 0) return '#9e9e9e'
  if (total >= 50 && total <= 70) return '#4caf50'
  if (total > 30 && total < 50) return '#ff9800'
  return '#f44336'
}

function markerRadius(teamMemberCount: number): number {
  return Math.min(12 + (teamMemberCount - 1) * 6, 40)
}

function onCountryClick(agg: CountryAggregation) {
  if (agg.coords) {
    emit('panTo', agg.coords)
  }
}

function utilizationTextColor(total: number): string {
  if (total === 0) return '#9e9e9e'
  if (total >= 50 && total <= 70) return '#4caf50'
  if (total > 30 && total < 50) return '#ff9800'
  return '#f44336'
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
          :radius="markerRadius(agg.teamMemberCount)"
          :color="utilizationColor(agg.avgUtilization)"
          :fill-color="utilizationColor(agg.avgUtilization)"
          :fill-opacity="0.6"
          :weight="2"
          :data-country="agg.country"
        >
          <LTooltip :options="{ permanent: true, direction: 'center', className: 'bubble-label' }">
            {{ agg.avgUtilization }}% · {{ agg.teamMemberCount }}
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
              <div v-for="member in agg.teamMembers" :key="member.teamMemberId" class="popup-team-member">
                <div class="popup-member-header">
                  <span class="popup-member-name">{{ member.teamMemberName }}</span>
                  <span :style="{ color: utilizationTextColor(member.total) }">{{ member.total }}%</span>
                </div>
                <div class="popup-assignments">
                  <div v-for="(a, i) in member.assignments" :key="i" class="popup-assignment">
                    <span>{{ a.customerName }}</span>
                    <span>{{ a.usage }}%<template v-if="a.status === 'PROBABLE'"> (probable)</template><template v-else-if="a.status === 'POTENTIAL'"> (potential)</template></span>
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
        @click="onCountryClick(agg)"
      >
        <div class="country-info">
          <img v-if="agg.flagUrl" :src="agg.flagUrl" class="country-flag" :alt="agg.country" />
          <div>
            <div class="country-name">{{ agg.country }}</div>
            <div class="country-meta">
              {{ agg.teamMemberCount }} team member{{ agg.teamMemberCount !== 1 ? 's' : '' }}
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
.popup-team-member { margin-bottom: 8px; }
.popup-team-member:last-child { margin-bottom: 0; }
.popup-member-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 2px; font-weight: 600; font-size: 0.85rem; }
.popup-member-name { color: #334155; }
.popup-assignments { padding-left: 8px; border-left: 2px solid #e2e8f0; }
.popup-assignment { display: flex; justify-content: space-between; font-size: 0.75rem; color: #64748b; padding: 1px 0; }
</style>
