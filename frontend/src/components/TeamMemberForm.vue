<script setup lang="ts">
import { reactive, ref, computed, onMounted, watch } from 'vue'
import { useGeoStore } from '@/stores/geo'
import AutocompleteInput from '@/components/AutocompleteInput.vue'
import type { TeamMember } from '@/types'

const props = defineProps<{ teamMember?: TeamMember }>()
const emit = defineEmits<{
  submit: [data: Omit<TeamMember, 'id'>, photo?: File]
  cancel: []
}>()

const geo = useGeoStore()

const form = reactive({
  firstName: props.teamMember?.firstName ?? '',
  lastName: props.teamMember?.lastName ?? '',
  email: props.teamMember?.email ?? '',
  country: props.teamMember?.country ?? '',
  city: props.teamMember?.city ?? '',
})

const photoFile = ref<File | undefined>()

const availableCities = computed(() =>
  form.country ? geo.getCities(form.country) : []
)

watch(() => form.country, (newCountry, oldCountry) => {
  if (newCountry && geo.countries.includes(newCountry)) {
    geo.fetchCities(newCountry)
  }
  if (!newCountry) {
    form.city = ''
  }
})

onMounted(() => {
  geo.fetchCountries()
  if (form.country) {
    geo.fetchCities(form.country)
  }
})

function onPhotoChange(event: Event) {
  const input = event.target as HTMLInputElement
  photoFile.value = input.files?.[0]
}

function onSubmit() {
  emit('submit', {
    firstName: form.firstName,
    lastName: form.lastName,
    email: form.email || undefined,
    country: form.country || undefined,
    city: form.city || undefined,
  }, photoFile.value)
}
</script>

<template>
  <form @submit.prevent="onSubmit" class="team-member-form">
    <div class="form-row">
      <label>First Name</label>
      <input v-model="form.firstName" required />
    </div>
    <div class="form-row">
      <label>Last Name</label>
      <input v-model="form.lastName" required />
    </div>
    <div class="form-row">
      <label>Email</label>
      <input v-model="form.email" type="email" />
    </div>
    <div class="form-row">
      <label>Country</label>
      <AutocompleteInput v-model="form.country" :suggestions="geo.countries" placeholder="Type a country..." :loading="geo.loadingCountries" />
    </div>
    <div class="form-row">
      <label>City</label>
      <AutocompleteInput v-model="form.city" :suggestions="availableCities" placeholder="Type a city..." :disabled="!form.country" :loading="geo.loadingCities" />
    </div>
    <div class="form-row">
      <label>Photo</label>
      <input type="file" accept="image/*" @change="onPhotoChange" />
    </div>
    <div class="form-actions">
      <button type="submit" class="primary">Save</button>
      <button type="button" @click="emit('cancel')">Cancel</button>
    </div>
  </form>
</template>

<style scoped>
.team-member-form { display: flex; flex-direction: column; gap: 0.75rem; max-width: 400px; }
.form-row { display: flex; flex-direction: column; gap: 0.25rem; }
.form-actions { display: flex; gap: 0.5rem; margin-top: 0.5rem; }
</style>
