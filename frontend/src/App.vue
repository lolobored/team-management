<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, RouterView } from 'vue-router'
import AppLayout from '@/components/AppLayout.vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const auth = useAuthStore()

// Show the app shell only for authenticated users on non-public routes who are
// not in the forced password-change state (login & set-password get a bare card).
const showShell = computed(() =>
  route.meta.public !== true
  && auth.isAuthenticated
  && !auth.currentUser?.mustChangePassword,
)
</script>

<template>
  <AppLayout v-if="showShell" />
  <RouterView v-else />
</template>
