import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useGeoStore } from '@/stores/geo'
import axios from 'axios'

vi.mock('axios')
const mockedAxios = vi.mocked(axios)

function mockCountriesnowResponses(
  countries: { country: string; cities: string[] }[],
  positions: { name: string; lat: number; long: number }[],
  flags: { name: string; flag: string }[],
) {
  mockedAxios.get
    .mockResolvedValueOnce({ data: { data: countries } })
    .mockResolvedValueOnce({ data: { data: positions } })
    .mockResolvedValueOnce({ data: { data: flags } })
}

describe('geo store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('fetchCountries populates countries list, countryData, and citiesByCountry', async () => {
    mockCountriesnowResponses(
      [
        { country: 'Singapore', cities: ['Singapore'] },
        { country: 'Australia', cities: ['Sydney', 'Melbourne'] },
      ],
      [
        { name: 'Singapore', lat: 1.3521, long: 103.8198 },
        { name: 'Australia', lat: -25.2744, long: 133.7751 },
      ],
      [
        { name: 'Singapore', flag: 'https://upload.wikimedia.org/sg.svg' },
        { name: 'Australia', flag: 'https://upload.wikimedia.org/au.svg' },
      ],
    )

    const store = useGeoStore()
    await store.fetchCountries()

    expect(store.countries).toEqual(['Australia', 'Singapore'])
    expect(store.countryData['Singapore']).toEqual({
      latlng: [1.3521, 103.8198],
      flagUrl: 'https://upload.wikimedia.org/sg.svg',
    })
    expect(store.countryData['Australia']).toEqual({
      latlng: [-25.2744, 133.7751],
      flagUrl: 'https://upload.wikimedia.org/au.svg',
    })
    expect(store.getCities('Australia')).toEqual(['Melbourne', 'Sydney'])
  })

  it('getCoords returns latlng for known country', async () => {
    mockCountriesnowResponses(
      [{ country: 'Japan', cities: ['Tokyo'] }],
      [{ name: 'Japan', lat: 36.2048, long: 138.2529 }],
      [{ name: 'Japan', flag: 'https://upload.wikimedia.org/jp.svg' }],
    )

    const store = useGeoStore()
    await store.fetchCountries()

    expect(store.getCoords('Japan')).toEqual([36.2048, 138.2529])
    expect(store.getCoords('Unknown')).toBeNull()
  })

  it('getFlagUrl returns flag URL for known country', async () => {
    mockCountriesnowResponses(
      [{ country: 'Japan', cities: ['Tokyo'] }],
      [{ name: 'Japan', lat: 36.2048, long: 138.2529 }],
      [{ name: 'Japan', flag: 'https://upload.wikimedia.org/jp.svg' }],
    )

    const store = useGeoStore()
    await store.fetchCountries()

    expect(store.getFlagUrl('Japan')).toBe('https://upload.wikimedia.org/jp.svg')
    expect(store.getFlagUrl('Unknown')).toBeNull()
  })

  it('uses localStorage cache on second call', async () => {
    mockCountriesnowResponses(
      [{ country: 'France', cities: ['Paris', 'Lyon'] }],
      [{ name: 'France', lat: 46.2276, long: 2.2137 }],
      [{ name: 'France', flag: 'https://upload.wikimedia.org/fr.svg' }],
    )

    const store1 = useGeoStore()
    await store1.fetchCountries()
    expect(mockedAxios.get).toHaveBeenCalledTimes(3)

    // Reset store but keep localStorage
    setActivePinia(createPinia())
    vi.clearAllMocks()

    const store2 = useGeoStore()
    await store2.fetchCountries()

    expect(mockedAxios.get).not.toHaveBeenCalled()
    expect(store2.countries).toEqual(['France'])
    expect(store2.getCoords('France')).toEqual([46.2276, 2.2137])
  })
})
