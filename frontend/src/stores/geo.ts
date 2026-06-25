import { ref } from 'vue'
import { defineStore } from 'pinia'
import axios from 'axios'

interface CountryInfo {
  latlng: [number, number]
  flagUrl: string
}

interface CachedData<T> {
  data: T
  timestamp: number
}

const CACHE_TTL_MS = 30 * 24 * 60 * 60 * 1000 // 30 days

function readCache<T>(key: string): T | null {
  try {
    const raw = localStorage.getItem(key)
    if (!raw) return null
    const cached: CachedData<T> = JSON.parse(raw)
    if (Date.now() - cached.timestamp > CACHE_TTL_MS) {
      localStorage.removeItem(key)
      return null
    }
    return cached.data
  } catch {
    return null
  }
}

function writeCache<T>(key: string, data: T): void {
  try {
    const entry: CachedData<T> = { data, timestamp: Date.now() }
    localStorage.setItem(key, JSON.stringify(entry))
  } catch {
    // localStorage full or unavailable — non-fatal
  }
}

const CACHE_KEY_COUNTRIES = 'geo:countries'
const CACHE_KEY_POSITIONS = 'geo:positions'
const CACHE_KEY_FLAGS = 'geo:flags'

export const useGeoStore = defineStore('geo', () => {
  const countries = ref<string[]>([])
  const countryData = ref<Record<string, CountryInfo>>({})
  const citiesByCountry = ref<Record<string, string[]>>({})
  const loadingCountries = ref(false)
  const loadingCities = ref(false)

  let countriesLoaded = false

  async function fetchCountries() {
    if (countriesLoaded) return
    loadingCountries.value = true
    try {
      // Try loading all 3 datasets from cache
      const cachedCountries = readCache<{ country: string; cities: string[] }[]>(CACHE_KEY_COUNTRIES)
      const cachedPositions = readCache<{ name: string; lat: number; long: number }[]>(CACHE_KEY_POSITIONS)
      const cachedFlags = readCache<{ name: string; flag: string }[]>(CACHE_KEY_FLAGS)

      let countriesData: { country: string; cities: string[] }[]
      let positionsData: { name: string; lat: number; long: number }[]
      let flagsData: { name: string; flag: string }[]

      if (cachedCountries && cachedPositions && cachedFlags) {
        countriesData = cachedCountries
        positionsData = cachedPositions
        flagsData = cachedFlags
      } else {
        const [countriesResp, positionsResp, flagsResp] = await Promise.all([
          axios.get<{ data: { country: string; cities: string[] }[] }>(
            'https://countriesnow.space/api/v0.1/countries'
          ),
          axios.get<{ data: { name: string; lat: number; long: number }[] }>(
            'https://countriesnow.space/api/v0.1/countries/positions'
          ),
          axios.get<{ data: { name: string; flag: string }[] }>(
            'https://countriesnow.space/api/v0.1/countries/flag/images'
          ),
        ])
        countriesData = countriesResp.data.data
        positionsData = positionsResp.data.data
        flagsData = flagsResp.data.data

        writeCache(CACHE_KEY_COUNTRIES, countriesData)
        writeCache(CACHE_KEY_POSITIONS, positionsData)
        writeCache(CACHE_KEY_FLAGS, flagsData)
      }

      // Build position and flag lookup maps
      const posMap = new Map<string, [number, number]>()
      for (const p of positionsData) {
        posMap.set(p.name, [p.lat, p.long])
      }
      const flagMap = new Map<string, string>()
      for (const f of flagsData) {
        flagMap.set(f.name, f.flag)
      }

      // Build sorted country list, countryData, and citiesByCountry
      const sorted = [...countriesData].sort((a, b) => a.country.localeCompare(b.country))
      countries.value = sorted.map(c => c.country)

      const data: Record<string, CountryInfo> = {}
      const cities: Record<string, string[]> = {}
      for (const c of sorted) {
        const coords = posMap.get(c.country)
        const flag = flagMap.get(c.country)
        data[c.country] = {
          latlng: coords ?? [0, 0],
          flagUrl: flag ?? null as unknown as string,
        }
        cities[c.country] = c.cities.sort((a, b) => a.localeCompare(b))
      }
      countryData.value = data
      citiesByCountry.value = cities
      countriesLoaded = true
    } catch {
      countries.value = []
      countryData.value = {}
    } finally {
      loadingCountries.value = false
    }
  }

  async function fetchCities(_country: string) {
    // Cities already loaded with countries — no-op for API compat
  }

  function getCities(country: string): string[] {
    return citiesByCountry.value[country] ?? []
  }

  function getCoords(country: string): [number, number] | null {
    const info = countryData.value[country]
    if (!info || (info.latlng[0] === 0 && info.latlng[1] === 0)) return null
    return info.latlng
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
