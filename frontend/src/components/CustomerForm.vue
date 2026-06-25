<script setup lang="ts">
import { reactive, ref, computed, onMounted, watch } from 'vue'
import { useGeoStore } from '@/stores/geo'
import { customerApi, logoSearchApi } from '@/api/client'
import AutocompleteInput from '@/components/AutocompleteInput.vue'
import type { Customer, LogoSearchResult } from '@/types'

const props = defineProps<{ customer?: Customer }>()
const emit = defineEmits<{ submit: [data: Omit<Customer, 'id'>]; cancel: [] }>()

const geo = useGeoStore()

const form = reactive({
  name: props.customer?.name ?? '',
  country: props.customer?.country ?? '',
  city: props.customer?.city ?? '',
})

const logoPreview = ref<string | null>(null)
const showLogoSearch = ref(false)
const logoQuery = ref('')
const logoResults = ref<LogoSearchResult[]>([])
const logoSearching = ref(false)
const dragOver = ref(false)

const availableCities = computed(() =>
  form.country ? geo.getCities(form.country) : []
)

watch(() => form.country, (newCountry) => {
  if (newCountry && geo.countries.includes(newCountry)) {
    geo.fetchCities(newCountry)
  }
  if (!newCountry) form.city = ''
})

onMounted(() => {
  geo.fetchCountries()
  if (form.country) geo.fetchCities(form.country)
  if (props.customer) {
    logoPreview.value = customerApi.logoUrl(props.customer.id)
  }
})

function onFileSelect(event: Event) {
  const input = event.target as HTMLInputElement
  if (input.files?.[0]) uploadFile(input.files[0])
}

function onDrop(event: DragEvent) {
  dragOver.value = false
  const file = event.dataTransfer?.files?.[0]
  if (file && file.type.startsWith('image/')) uploadFile(file)
}

async function uploadFile(file: File) {
  if (!props.customer) return
  await customerApi.uploadLogo(props.customer.id, file)
  logoPreview.value = customerApi.logoUrl(props.customer.id) + '?t=' + Date.now()
}

async function searchLogos() {
  if (!logoQuery.value.trim()) return
  logoSearching.value = true
  try {
    logoResults.value = await logoSearchApi.search(logoQuery.value)
  } finally {
    logoSearching.value = false
  }
}

async function selectLogo(result: LogoSearchResult) {
  if (!props.customer) return
  await customerApi.setLogoFromUrl(props.customer.id, result.url)
  logoPreview.value = customerApi.logoUrl(props.customer.id) + '?t=' + Date.now()
  showLogoSearch.value = false
  logoResults.value = []
}

async function removeLogo() {
  if (!props.customer) return
  await customerApi.deleteLogo(props.customer.id)
  logoPreview.value = null
}

function onSubmit() {
  emit('submit', {
    name: form.name,
    country: form.country || undefined,
    city: form.city || undefined,
  })
}
</script>

<template>
  <form @submit.prevent="onSubmit" class="form">
    <div v-if="customer" class="logo-section">
      <div class="logo-row">
        <img v-if="logoPreview" :src="logoPreview" class="logo-preview"
             @error="logoPreview = null" />
        <div v-else class="logo-placeholder">No logo</div>
        <div class="logo-actions">
          <label class="btn-small">
            Browse
            <input type="file" accept="image/*" @change="onFileSelect" hidden />
          </label>
          <button type="button" class="btn-small" @click="showLogoSearch = !showLogoSearch">Search</button>
          <button v-if="logoPreview" type="button" class="btn-small danger" @click="removeLogo">Remove</button>
        </div>
      </div>
      <div class="drop-zone" :class="{ active: dragOver }"
           @dragover.prevent="dragOver = true" @dragleave="dragOver = false" @drop.prevent="onDrop">
        Drop an image here
      </div>
      <div v-if="showLogoSearch" class="logo-search-panel">
        <div class="search-row">
          <input v-model="logoQuery" placeholder="Search logos..." @keyup.enter="searchLogos" />
          <button type="button" @click="searchLogos" :disabled="logoSearching">
            {{ logoSearching ? 'Searching...' : 'Go' }}
          </button>
        </div>
        <div class="logo-grid">
          <img v-for="(result, i) in logoResults" :key="i" :src="result.thumbnailUrl"
               class="logo-result" @click="selectLogo(result)" title="Click to use this logo" />
        </div>
        <div v-if="logoResults.length === 0 && !logoSearching" class="empty-search">
          Type a company name and click Go
        </div>
      </div>
    </div>
    <div v-else class="logo-note">Save the customer first, then edit to add a logo.</div>

    <div class="form-row"><label>Name</label><input v-model="form.name" required /></div>
    <div class="form-row">
      <label>Country</label>
      <AutocompleteInput v-model="form.country" :suggestions="geo.countries" placeholder="Type a country..." :loading="geo.loadingCountries" />
    </div>
    <div class="form-row">
      <label>City</label>
      <AutocompleteInput v-model="form.city" :suggestions="availableCities" placeholder="Type a city..." :disabled="!form.country" :loading="geo.loadingCities" />
    </div>
    <div class="form-actions">
      <button type="submit" class="primary">Save</button>
      <button type="button" @click="emit('cancel')">Cancel</button>
    </div>
  </form>
</template>

<style scoped>
.form { display: flex; flex-direction: column; gap: 0.75rem; max-width: 450px; }
.form-row { display: flex; flex-direction: column; gap: 0.25rem; }
.form-actions { display: flex; gap: 0.5rem; margin-top: 0.5rem; }
.logo-section { border: 1px solid #e2e8f0; border-radius: 6px; padding: 0.75rem; background: #f8fafc; }
.logo-row { display: flex; align-items: center; gap: 0.75rem; margin-bottom: 0.5rem; }
.logo-preview { width: 64px; height: 64px; border-radius: 6px; object-fit: contain; background: #fff; border: 1px solid #e2e8f0; }
.logo-placeholder { width: 64px; height: 64px; border-radius: 6px; background: #e2e8f0; display: flex; align-items: center; justify-content: center; font-size: 0.7rem; color: #94a3b8; }
.logo-actions { display: flex; gap: 0.25rem; flex-wrap: wrap; }
.btn-small { padding: 0.25rem 0.5rem; font-size: 0.8rem; border: 1px solid #cbd5e1; border-radius: 4px; background: #fff; cursor: pointer; }
.btn-small:hover { background: #f1f5f9; }
.btn-small.danger { color: #dc2626; border-color: #fca5a5; }
.btn-small.danger:hover { background: #fef2f2; }
.drop-zone { border: 2px dashed #cbd5e1; border-radius: 6px; padding: 0.5rem; text-align: center; font-size: 0.8rem; color: #94a3b8; transition: all 0.2s; }
.drop-zone.active { border-color: #3b82f6; background: #eff6ff; color: #3b82f6; }
.logo-search-panel { margin-top: 0.5rem; border-top: 1px solid #e2e8f0; padding-top: 0.5rem; }
.search-row { display: flex; gap: 0.25rem; margin-bottom: 0.5rem; }
.search-row input { flex: 1; padding: 0.3rem 0.5rem; font-size: 0.85rem; border: 1px solid #cbd5e1; border-radius: 4px; }
.logo-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: 0.5rem; }
.logo-result { width: 100%; aspect-ratio: 1; object-fit: contain; border: 1px solid #e2e8f0; border-radius: 4px; cursor: pointer; background: #fff; padding: 4px; }
.logo-result:hover { border-color: #3b82f6; box-shadow: 0 0 0 2px #bfdbfe; }
.empty-search { text-align: center; font-size: 0.8rem; color: #94a3b8; padding: 1rem; }
.logo-note { font-size: 0.8rem; color: #94a3b8; font-style: italic; }
</style>
