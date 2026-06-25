import { ref } from 'vue'
import { defineStore } from 'pinia'
import { teamMemberApi } from '@/api/client'
import type { TeamMember } from '@/types'

export const useTeamMembersStore = defineStore('team-members', () => {
  const teamMembers = ref<TeamMember[]>([])
  const loading = ref(false)

  async function fetchAll() {
    loading.value = true
    try {
      teamMembers.value = await teamMemberApi.list()
    } finally {
      loading.value = false
    }
  }

  async function create(data: Omit<TeamMember, 'id'>): Promise<TeamMember> {
    const created = await teamMemberApi.create(data)
    teamMembers.value.push(created)
    return created
  }

  async function update(id: number, data: Omit<TeamMember, 'id'>): Promise<TeamMember> {
    const updated = await teamMemberApi.update(id, data)
    const index = teamMembers.value.findIndex(a => a.id === id)
    if (index !== -1) teamMembers.value[index] = updated
    return updated
  }

  async function remove(id: number) {
    await teamMemberApi.delete(id)
    teamMembers.value = teamMembers.value.filter(a => a.id !== id)
  }

  return { teamMembers, loading, fetchAll, create, update, remove }
})
