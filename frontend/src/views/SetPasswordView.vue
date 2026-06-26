<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { passwordChecks, passwordValid } from '@/utils/password'

const auth = useAuthStore()
const router = useRouter()
const currentPassword = ref('')
const newPassword = ref('')
const confirm = ref('')
const error = ref('')
const submitting = ref(false)

const checks = computed(() => passwordChecks(newPassword.value))
const classCount = computed(() =>
  [checks.value.upper, checks.value.lower, checks.value.digit, checks.value.symbol].filter(Boolean).length)
const valid = computed(() => passwordValid(newPassword.value))
const matches = computed(() => newPassword.value.length > 0 && newPassword.value === confirm.value)
const canSubmit = computed(() => valid.value && matches.value && currentPassword.value.length > 0)

async function submit() {
  error.value = ''
  submitting.value = true
  try {
    await auth.changePassword(currentPassword.value, newPassword.value)
    router.push('/timeline')
  } catch (e: any) {
    error.value = e?.response?.status === 400
      ? 'Current password is incorrect'
      : 'Password does not meet the policy'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="sp-wrap">
    <form class="sp-card" @submit.prevent="submit">
      <h1>Set your password</h1>
      <label>Current password<input v-model="currentPassword" type="password" autocomplete="current-password" required></label>
      <label>New password<input v-model="newPassword" type="password" autocomplete="new-password" required></label>
      <ul class="policy">
        <li :class="{ ok: checks.length }" data-testid="check-length">At least 12 characters</li>
        <li :class="{ ok: classCount >= 3 }" data-testid="check-classes">
          At least 3 of: uppercase, lowercase, digit, symbol ({{ classCount }}/4)
        </li>
      </ul>
      <label>Confirm new password<input v-model="confirm" type="password" autocomplete="new-password" required></label>
      <p v-if="confirm.length > 0 && !matches" class="error">Passwords do not match</p>
      <p v-if="error" class="error" data-testid="sp-error">{{ error }}</p>
      <button type="submit" :disabled="!canSubmit || submitting" data-testid="sp-submit">Save password</button>
    </form>
  </div>
</template>

<style scoped>
.sp-wrap { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: #f1f5f9; }
.sp-card { background: #fff; padding: 2rem; border-radius: 10px; box-shadow: 0 4px 24px rgba(0,0,0,0.08); width: 360px; display: flex; flex-direction: column; gap: 0.6rem; }
.sp-card h1 { font-size: 1.2rem; margin: 0 0 0.5rem; }
.sp-card label { display: flex; flex-direction: column; font-size: 0.8rem; gap: 0.25rem; color: #475569; }
.sp-card input { padding: 0.5rem; border: 1px solid #cbd5e1; border-radius: 6px; }
.policy { list-style: none; padding: 0; margin: 0; font-size: 0.78rem; }
.policy li { color: #94a3b8; }
.policy li::before { content: '✗ '; color: #dc2626; }
.policy li.ok { color: #16a34a; }
.policy li.ok::before { content: '✓ '; color: #16a34a; }
.sp-card button { margin-top: 0.5rem; padding: 0.5rem; background: #3b82f6; color: #fff; border: none; border-radius: 6px; cursor: pointer; }
.sp-card button:disabled { opacity: 0.6; cursor: default; }
.error { color: #dc2626; font-size: 0.8rem; margin: 0; }
</style>
