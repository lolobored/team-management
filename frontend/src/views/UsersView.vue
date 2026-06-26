<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { userApi } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import type { AppUser, Role } from '@/types'

const auth = useAuthStore()
const users = ref<AppUser[]>([])
const newEmail = ref('')
const newRole = ref<Role>('VIEW')
const newPassword = ref('')
const error = ref('')

async function load() {
  users.value = await userApi.list()
}
onMounted(load)

function isSelf(u: AppUser) {
  return u.email === auth.currentUser?.email
}

async function create() {
  error.value = ''
  try {
    await userApi.create(newEmail.value, newRole.value, newPassword.value)
    newEmail.value = ''
    newPassword.value = ''
    newRole.value = 'VIEW'
    await load()
  } catch (e: any) {
    error.value = e?.response?.status === 409 ? 'Email already exists' : 'Could not create user'
  }
}

async function changeRole(u: AppUser, role: Role) {
  await userApi.changeRole(u.id, role)
  await load()
}

async function toggleEnabled(u: AppUser) {
  await userApi.setEnabled(u.id, !u.enabled)
  await load()
}

async function resetPassword(u: AppUser) {
  const pw = prompt(`New temporary password for ${u.email}:`)
  if (pw) {
    await userApi.resetPassword(u.id, pw)
    await load()
  }
}

async function remove(u: AppUser) {
  if (confirm(`Delete ${u.email}?`)) {
    await userApi.remove(u.id)
    await load()
  }
}
</script>

<template>
  <div class="users-view">
    <h1>Users</h1>

    <form class="create-form" data-testid="create-user" @submit.prevent="create">
      <input v-model="newEmail" data-testid="new-email" type="email" placeholder="email" required>
      <select v-model="newRole">
        <option value="VIEW">View</option>
        <option value="VIEW_WRITE">View / Write</option>
        <option value="ADMIN">Admin</option>
      </select>
      <input v-model="newPassword" data-testid="new-password" type="text" placeholder="temp password" required>
      <button type="submit" data-testid="create-user-btn">Add user</button>
    </form>
    <p v-if="error" class="error">{{ error }}</p>

    <table class="users-table">
      <thead>
        <tr><th>Email</th><th>Role</th><th>Status</th><th>Actions</th></tr>
      </thead>
      <tbody>
        <tr v-for="u in users" :key="u.id">
          <td>{{ u.email }}<span v-if="isSelf(u)" class="you"> (you)</span></td>
          <td>
            <select :value="u.role" :disabled="isSelf(u)"
                    @change="changeRole(u, ($event.target as HTMLSelectElement).value as Role)">
              <option value="VIEW">View</option>
              <option value="VIEW_WRITE">View / Write</option>
              <option value="ADMIN">Admin</option>
            </select>
          </td>
          <td>
            <span :class="u.enabled ? 'on' : 'off'">{{ u.enabled ? 'Enabled' : 'Disabled' }}</span>
            <span v-if="u.mustChangePassword" class="pending"> · must set password</span>
          </td>
          <td class="actions">
            <button @click="resetPassword(u)">Reset password</button>
            <button :disabled="isSelf(u)" @click="toggleEnabled(u)">{{ u.enabled ? 'Disable' : 'Enable' }}</button>
            <button :disabled="isSelf(u)" :data-testid="`delete-${u.id}`" class="danger" @click="remove(u)">Delete</button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.users-view { max-width: 900px; }
h1 { font-size: 1.3rem; }
.create-form { display: flex; gap: 0.5rem; margin-bottom: 1rem; flex-wrap: wrap; }
.create-form input, .create-form select { padding: 0.4rem; border: 1px solid #cbd5e1; border-radius: 6px; }
.create-form button { padding: 0.4rem 0.8rem; background: #3b82f6; color: #fff; border: none; border-radius: 6px; cursor: pointer; }
.users-table { width: 100%; border-collapse: collapse; }
.users-table th, .users-table td { text-align: left; padding: 0.5rem; border-bottom: 1px solid #e2e8f0; font-size: 0.85rem; }
.actions { display: flex; gap: 0.4rem; }
.actions button { padding: 0.3rem 0.6rem; border: 1px solid #cbd5e1; background: #f8fafc; border-radius: 6px; cursor: pointer; font-size: 0.78rem; }
.actions button:disabled { opacity: 0.5; cursor: default; }
.actions .danger { color: #dc2626; border-color: #fecaca; }
.you { color: #94a3b8; font-size: 0.75rem; }
.on { color: #16a34a; } .off { color: #dc2626; }
.pending { color: #d97706; font-size: 0.75rem; }
.error { color: #dc2626; font-size: 0.85rem; }
</style>
