<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const email = ref('')
const password = ref('')
const error = ref('')
const submitting = ref(false)

async function submit() {
  error.value = ''
  submitting.value = true
  try {
    await auth.login(email.value, password.value)
    router.push(auth.currentUser?.mustChangePassword ? '/set-password' : '/timeline')
  } catch {
    error.value = 'Invalid email or password'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login-wrap">
    <form class="login-card" @submit.prevent="submit">
      <h1>Team Management</h1>
      <label>Email<input v-model="email" type="email" autocomplete="username" required></label>
      <label>Password<input v-model="password" type="password" autocomplete="current-password" required></label>
      <p v-if="error" class="error" data-testid="login-error">{{ error }}</p>
      <button type="submit" :disabled="submitting">Log in</button>
    </form>
  </div>
</template>

<style scoped>
.login-wrap { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: #f1f5f9; }
.login-card { background: #fff; padding: 2rem; border-radius: 10px; box-shadow: 0 4px 24px rgba(0,0,0,0.08); width: 320px; display: flex; flex-direction: column; gap: 0.75rem; }
.login-card h1 { font-size: 1.2rem; margin: 0 0 0.5rem; color: #38bdf8; }
.login-card label { display: flex; flex-direction: column; font-size: 0.8rem; gap: 0.25rem; color: #475569; }
.login-card input { padding: 0.5rem; border: 1px solid #cbd5e1; border-radius: 6px; }
.login-card button { margin-top: 0.5rem; padding: 0.5rem; background: #3b82f6; color: #fff; border: none; border-radius: 6px; cursor: pointer; }
.login-card button:disabled { opacity: 0.6; cursor: default; }
.error { color: #dc2626; font-size: 0.8rem; margin: 0; }
</style>
