import { defineStore } from 'pinia'
import { assignmentApi } from '@/api/client'
import type { Assignment } from '@/types'

export const useAssignmentsStore = defineStore('assignments', () => {
  async function create(data: Omit<Assignment, 'id'>) {
    return await assignmentApi.create(data)
  }

  async function remove(id: number) {
    await assignmentApi.delete(id)
  }

  return { create, remove }
})
