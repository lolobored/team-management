import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import WorldMapView from '@/components/WorldMapView.vue'
import type { TeamMemberUsage } from '@/types'

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

const testUsageData: TeamMemberUsage[] = [
  {
    teamMemberId: 1,
    teamMemberName: 'Alice Smith',
    country: 'Singapore',
    months: {
      '2026-05': {
        total: 40,
        assignments: [
          { assignmentId: 1, customerId: 1, customerName: 'Acme', usage: 40, status: 'CONFIRMED' },
        ],
      },
    },
  },
  {
    teamMemberId: 2,
    teamMemberName: 'Bob Lee',
    country: 'Singapore',
    months: {
      '2026-05': {
        total: 20,
        assignments: [
          { assignmentId: 2, customerId: 2, customerName: 'BetaCo', usage: 20, status: 'PROBABLE' },
        ],
      },
    },
  },
  {
    teamMemberId: 3,
    teamMemberName: 'Carol Tan',
    country: 'Australia',
    months: {
      '2026-05': {
        total: 90,
        assignments: [
          { assignmentId: 3, customerId: 3, customerName: 'GammaCo', usage: 90, status: 'CONFIRMED' },
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
    expect(rows[0].text()).toContain('1 team member')
    expect(rows[1].text()).toContain('Singapore')
    expect(rows[1].text()).toContain('30%')
    expect(rows[1].text()).toContain('2 team members')
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

  it('shows (probable) label for PROBABLE assignment and not (tentative) in the popup', () => {
    const wrapper = mount(WorldMapView, {
      props: { usageData: testUsageData, month: '2026-05' },
    })

    // The PROBABLE assignment (BetaCo, Bob Lee, Singapore) should show (probable) in the popup
    const popups = wrapper.findAll('.leaflet-popup')
    const singaporePopup = popups.find(p => p.text().includes('Singapore'))!
    expect(singaporePopup).toBeTruthy()
    expect(singaporePopup.text()).toContain('(probable)')
    expect(singaporePopup.text()).not.toContain('(tentative)')

    // The CONFIRMED assignment (Acme, Alice Smith) should not show any status suffix
    expect(singaporePopup.text()).not.toContain('(potential)')

    // CONFIRMED Australia popup should have no status suffix at all
    const australiaPopup = popups.find(p => p.text().includes('Australia'))!
    expect(australiaPopup.text()).not.toContain('(probable)')
    expect(australiaPopup.text()).not.toContain('(tentative)')
    expect(australiaPopup.text()).not.toContain('(potential)')
  })

  it('handles team members with unknown country — shows in side panel but no marker', () => {
    const data: TeamMemberUsage[] = [
      {
        teamMemberId: 99,
        teamMemberName: 'Unknown Person',
        country: 'Narnia',
        months: {
          '2026-05': {
            total: 50,
            assignments: [
              { assignmentId: 10, customerId: 10, customerName: 'Fantasy', usage: 50, status: 'CONFIRMED' },
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
