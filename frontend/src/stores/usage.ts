import { ref } from 'vue'
import { defineStore } from 'pinia'
import { usageApi } from '@/api/client'
import type { TeamMemberUsage } from '@/types'

export const useUsageStore = defineStore('usage', () => {
  const usageData = ref<TeamMemberUsage[]>([])
  const loading = ref(false)

  async function fetchUsage(from: string, to: string, country?: string, teamMemberId?: number) {
    loading.value = true
    try {
      usageData.value = await usageApi.get(from, to, country, teamMemberId)
    } finally {
      loading.value = false
    }
  }

  return { usageData, loading, fetchUsage }
})
