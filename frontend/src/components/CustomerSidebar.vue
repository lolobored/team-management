<script setup lang="ts">
import { ref, computed } from 'vue'
import { customerApi } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import type { Customer } from '@/types'

const props = defineProps<{ customers: Customer[] }>()
const auth = useAuthStore()

const collapsed = ref(false)
const filter = ref('')

const filteredCustomers = computed(() => {
  let list = [...props.customers]
  if (filter.value) {
    const search = filter.value.toLowerCase()
    list = list.filter(c =>
      c.name.toLowerCase().includes(search) ||
      c.country?.toLowerCase().includes(search)
    )
  }
  return list.sort((a, b) => a.name.localeCompare(b.name))
})

function onDragStart(event: DragEvent, customer: Customer) {
  if (!auth.canWrite) return
  event.dataTransfer!.setData('application/json', JSON.stringify({
    type: 'customer',
    customerId: customer.id,
    status: 'CONFIRMED',
    defaultUsagePercent: 20,
  }))
  event.dataTransfer!.effectAllowed = 'copy'
}
</script>

<template>
  <div class="sidebar-panel" :class="{ collapsed }">
    <button class="toggle-btn" @click="collapsed = !collapsed" :title="collapsed ? 'Show customers' : 'Hide customers'">
      <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
        <line x1="4" y1="5" x2="16" y2="5" />
        <line x1="8" y1="10" x2="16" y2="10" />
        <line x1="4" y1="15" x2="16" y2="15" />
      </svg>
    </button>
    <template v-if="!collapsed">
      <h3>Customers</h3>
      <input v-model="filter" placeholder="Filter customers..." class="customer-filter" />
      <div>
        <div v-for="customer in filteredCustomers" :key="customer.id"
          class="customer-item"
          :draggable="auth.canWrite" @dragstart="onDragStart($event, customer)">
          <img :src="customerApi.logoUrl(customer.id)" class="customer-logo"
               @error="($event.target as HTMLImageElement).style.display = 'none'" />
          <div class="customer-text">
            <div class="customer-name">{{ customer.name }}</div>
            <div v-if="customer.country" class="customer-country">{{ customer.country }}</div>
          </div>
        </div>
      </div>
      <div v-if="filteredCustomers.length === 0" class="empty">{{ filter ? 'No matching customers' : 'No customers' }}</div>
    </template>
  </div>
</template>

<style scoped>
.sidebar-panel { width: 200px; border-left: 1px solid #e2e8f0; padding: 1rem; background: #fff; overflow-y: auto; flex-shrink: 0; transition: width 0.2s ease; }
.sidebar-panel.collapsed { width: 44px; padding: 0.5rem; overflow: hidden; }
.toggle-btn { background: none; border: none; color: #94a3b8; cursor: pointer; padding: 6px; border-radius: 6px; display: flex; align-items: center; justify-content: center; transition: color 0.2s, background 0.2s; margin-bottom: 0.5rem; }
.toggle-btn:hover { color: #1e293b; background: #f1f5f9; }
.sidebar-panel h3 { margin-bottom: 0.5rem; font-size: 0.9rem; text-transform: uppercase; color: #64748b; }
.customer-filter { width: 100%; padding: 4px 8px; font-size: 0.8rem; border: 1px solid #e2e8f0; border-radius: 4px; margin-bottom: 0.5rem; box-sizing: border-box; }
.customer-filter:focus { outline: none; border-color: #3b82f6; }
.customer-item { display: flex; align-items: center; gap: 8px; padding: 0.5rem; border: 1px solid #e2e8f0; border-radius: 4px; margin-bottom: 0.5rem; cursor: grab; background: #fff; transition: border-color 0.2s, background 0.2s; }
.customer-item:hover { border-color: #3b82f6; background: #eff6ff; }
.customer-logo { width: 24px; height: 24px; border-radius: 4px; object-fit: contain; flex-shrink: 0; }
.customer-text { overflow: hidden; }
.customer-name { font-weight: 600; font-size: 0.85rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.customer-country { font-size: 0.7rem; color: #94a3b8; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.empty { color: #94a3b8; font-size: 0.85rem; text-align: center; padding: 1rem; }
</style>
