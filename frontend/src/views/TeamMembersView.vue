<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useTeamMembersStore } from '@/stores/teamMembers'
import { useGeoStore } from '@/stores/geo'
import { teamMemberApi } from '@/api/client'
import TeamMemberForm from '@/components/TeamMemberForm.vue'
import { useAuthStore } from '@/stores/auth'
import type { TeamMember } from '@/types'

const store = useTeamMembersStore()
const auth = useAuthStore()
const geo = useGeoStore()
const countryFilter = ref('')
const showForm = ref(false)
const editingTeamMember = ref<TeamMember | undefined>()
const photoVersion = ref(0)
const hoverPhoto = ref<{ src: string; top: number; left: number } | null>(null)

function onPhotoEnter(event: MouseEvent, teamMemberId: number) {
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  hoverPhoto.value = {
    src: `${teamMemberApi.photoUrl(teamMemberId)}?v=${photoVersion.value}`,
    top: rect.top - 20,
    left: rect.right + 8,
  }
}

function onPhotoLeave() {
  hoverPhoto.value = null
}

const filtered = computed(() => {
  let list = [...store.teamMembers]
  if (countryFilter.value) {
    list = list.filter(a => a.country === countryFilter.value)
  }
  return list.sort((a, b) =>
    `${a.firstName} ${a.lastName}`.localeCompare(`${b.firstName} ${b.lastName}`)
  )
})

const usedCountries = computed(() => {
  const set = new Set(store.teamMembers.map(a => a.country).filter(Boolean) as string[])
  return [...set].sort()
})

onMounted(() => {
  store.fetchAll()
  geo.fetchCountries()
})

function openCreate() {
  editingTeamMember.value = undefined
  showForm.value = true
}

function openEdit(teamMember: TeamMember) {
  editingTeamMember.value = teamMember
  showForm.value = true
}

async function onSubmit(data: Omit<TeamMember, 'id'>, photo?: File) {
  let member: TeamMember
  if (editingTeamMember.value) {
    member = await store.update(editingTeamMember.value.id, data)
  } else {
    member = await store.create(data)
  }
  if (photo) {
    await teamMemberApi.uploadPhoto(member.id, photo)
    photoVersion.value++
  }
  showForm.value = false
}

async function onDelete(id: number) {
  if (confirm('Delete this team member?')) {
    await store.remove(id)
  }
}

function locationDisplay(a: TeamMember): string {
  const parts = [a.city, a.country].filter(Boolean)
  return parts.join(', ') || '-'
}
</script>

<template>
  <div>
    <div class="header">
      <h1>Team Members</h1>
      <div class="controls">
        <select v-model="countryFilter" data-testid="country-filter">
          <option value="">All countries</option>
          <option v-for="c in usedCountries" :key="c" :value="c">{{ c }}</option>
        </select>
        <div v-if="auth.canWrite" data-testid="member-create">
          <button class="primary" @click="openCreate">+ Add Team Member</button>
        </div>
      </div>
    </div>

    <div v-if="showForm && auth.canWrite" class="form-overlay">
      <div class="form-panel">
        <h2>{{ editingTeamMember ? 'Edit' : 'Add' }} Team Member</h2>
        <TeamMemberForm :teamMember="editingTeamMember" @submit="onSubmit" @cancel="showForm = false" />
      </div>
    </div>

    <table>
      <thead>
        <tr><th>Photo</th><th>Name</th><th>Email</th><th>Country</th><th>Location</th><th>Actions</th></tr>
      </thead>
      <tbody>
        <tr v-for="a in filtered" :key="a.id">
          <td class="photo-cell">
            <img :src="`${teamMemberApi.photoUrl(a.id)}?v=${photoVersion}`"
              class="team-member-photo"
              @mouseenter="onPhotoEnter($event, a.id)"
              @mouseleave="onPhotoLeave"
              @error="($event.target as HTMLImageElement).style.display = 'none'" />
          </td>
          <td>{{ a.firstName }} {{ a.lastName }}</td>
          <td>{{ a.email ?? '-' }}</td>
          <td>{{ a.country ?? '-' }}</td>
          <td>{{ locationDisplay(a) }}</td>
          <td>
            <template v-if="auth.canWrite">
              <button @click="openEdit(a)">Edit</button>
              <button class="danger" @click="onDelete(a.id)">Delete</button>
            </template>
          </td>
        </tr>
        <tr v-if="filtered.length === 0">
          <td colspan="6" style="text-align: center; color: #94a3b8;">No team members found</td>
        </tr>
      </tbody>
    </table>
    <Teleport to="body">
      <div v-if="hoverPhoto" class="avatar-popup"
        :style="{ top: hoverPhoto.top + 'px', left: hoverPhoto.left + 'px' }">
        <img :src="hoverPhoto.src" />
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem; }
.controls { display: flex; gap: 0.5rem; }
.form-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.3); display: flex; align-items: center; justify-content: center; z-index: 100; }
.form-panel { background: #fff; padding: 1.5rem; border-radius: 8px; box-shadow: 0 4px 24px rgba(0,0,0,0.15); }
.form-panel h2 { margin-bottom: 1rem; }
td button + button { margin-left: 0.25rem; }
.photo-cell { width: 56px; }
.team-member-photo { width: 40px; height: 40px; border-radius: 50%; object-fit: cover; border: 2px solid #e2e8f0; cursor: pointer; }
</style>

<style>
.avatar-popup { position: fixed; z-index: 300; background: #fff; border: 1px solid #e2e8f0; border-radius: 8px; box-shadow: 0 4px 24px rgba(0,0,0,0.2); padding: 4px; pointer-events: none; }
.avatar-popup img { width: 200px; height: 200px; object-fit: cover; border-radius: 6px; display: block; }
</style>
