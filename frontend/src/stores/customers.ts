import { ref } from 'vue'
import { defineStore } from 'pinia'
import { customerApi } from '@/api/client'
import type { Customer } from '@/types'

export const useCustomersStore = defineStore('customers', () => {
  const customers = ref<Customer[]>([])
  const loading = ref(false)

  async function fetchAll() {
    loading.value = true
    try {
      customers.value = await customerApi.list()
    } finally {
      loading.value = false
    }
  }

  async function create(data: Omit<Customer, 'id'>) {
    const created = await customerApi.create(data)
    customers.value.push(created)
    return created
  }

  async function update(id: number, data: Omit<Customer, 'id'>) {
    const updated = await customerApi.update(id, data)
    const index = customers.value.findIndex(c => c.id === id)
    if (index !== -1) customers.value[index] = updated
    return updated
  }

  async function remove(id: number) {
    await customerApi.delete(id)
    customers.value = customers.value.filter(c => c.id !== id)
  }

  return { customers, loading, fetchAll, create, update, remove }
})
