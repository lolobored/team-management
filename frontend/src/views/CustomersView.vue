<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useCustomersStore } from '@/stores/customers'
import { customerApi } from '@/api/client'
import CustomerForm from '@/components/CustomerForm.vue'
import type { Customer } from '@/types'

const customerStore = useCustomersStore()

const filterText = ref('')
const sortBy = ref<'name' | 'country'>('name')
const showForm = ref(false)
const editingCustomer = ref<Customer | undefined>()

const sortedCustomers = computed(() => {
  const search = filterText.value.toLowerCase()
  let list = [...customerStore.customers]
  if (search) {
    list = list.filter(c =>
      c.name.toLowerCase().includes(search) ||
      c.country?.toLowerCase().includes(search) ||
      c.city?.toLowerCase().includes(search)
    )
  }
  return list.sort((a, b) => {
    if (sortBy.value === 'country') {
      return (a.country ?? '').localeCompare(b.country ?? '') || a.name.localeCompare(b.name)
    }
    return a.name.localeCompare(b.name)
  })
})

onMounted(() => customerStore.fetchAll())

function openAdd() { editingCustomer.value = undefined; showForm.value = true }
function openEdit(c: Customer) { editingCustomer.value = c; showForm.value = true }

async function onSubmit(data: Omit<Customer, 'id'>) {
  if (editingCustomer.value) await customerStore.update(editingCustomer.value.id, data)
  else await customerStore.create(data)
  showForm.value = false
}

async function onDelete(id: number) {
  if (confirm('Delete this customer?')) await customerStore.remove(id)
}
</script>

<template>
  <div>
    <div class="header">
      <h1>Customers</h1>
      <div class="header-controls">
        <input v-model="filterText" placeholder="Filter by name, country, city..." class="filter-input" />
        <label class="sort-label">Sort by:
          <select v-model="sortBy">
            <option value="name">Name</option>
            <option value="country">Country</option>
          </select>
        </label>
        <button class="primary" @click="openAdd">+ Add Customer</button>
      </div>
    </div>

    <div v-if="showForm" class="form-overlay">
      <div class="form-panel">
        <h2>{{ editingCustomer ? 'Edit' : 'Add' }} Customer</h2>
        <CustomerForm :customer="editingCustomer" @submit="onSubmit" @cancel="showForm = false" />
      </div>
    </div>

    <div class="customer-grid">
      <div v-for="customer in sortedCustomers" :key="customer.id" class="customer-card">
        <img :src="customerApi.logoUrl(customer.id)" class="customer-logo"
             @error="($event.target as HTMLImageElement).style.display = 'none'" />
        <div class="customer-info">
          <strong>{{ customer.name }}</strong>
          <span v-if="customer.country || customer.city" class="location">
            {{ [customer.city, customer.country].filter(Boolean).join(', ') }}
          </span>
        </div>
        <div class="customer-actions">
          <button @click="openEdit(customer)">Edit</button>
          <button class="danger" @click="onDelete(customer.id)">Delete</button>
        </div>
      </div>
    </div>

    <div v-if="sortedCustomers.length === 0 && filterText" class="empty">No matching customers.</div>
    <div v-else-if="customerStore.customers.length === 0" class="empty">No customers yet. Add one to get started.</div>
  </div>
</template>

<style scoped>
.header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem; }
.header-controls { display: flex; align-items: center; gap: 0.75rem; }
.filter-input { padding: 0.4rem 0.6rem; border: 1px solid #cbd5e1; border-radius: 4px; font-size: 0.85rem; width: 250px; }
.filter-input:focus { outline: none; border-color: #3b82f6; }
.sort-label { display: flex; align-items: center; gap: 0.25rem; font-size: 0.85rem; color: #64748b; }
.customer-grid { display: flex; flex-direction: column; gap: 0.5rem; }
.customer-card { display: flex; align-items: center; gap: 1rem; padding: 0.75rem 1rem; border: 1px solid #e2e8f0; border-radius: 6px; background: #fff; }
.customer-logo { width: 40px; height: 40px; border-radius: 6px; object-fit: contain; flex-shrink: 0; background: #f1f5f9; }
.customer-info { flex: 1; }
.location { color: #64748b; font-size: 0.85rem; margin-left: 0.5rem; }
.customer-actions { display: flex; gap: 0.25rem; }
.form-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.3); display: flex; align-items: center; justify-content: center; z-index: 100; }
.form-panel { background: #fff; padding: 1.5rem; border-radius: 8px; box-shadow: 0 4px 24px rgba(0,0,0,0.15); min-width: 450px; }
.form-panel h2 { margin-bottom: 1rem; }
.empty { text-align: center; color: #94a3b8; padding: 3rem; }
</style>
