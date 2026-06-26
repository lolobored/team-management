import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/timeline' },
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { public: true } },
    { path: '/set-password', name: 'set-password', component: () => import('@/views/SetPasswordView.vue') },
    { path: '/team-members', name: 'team-members', component: () => import('@/views/TeamMembersView.vue') },
    { path: '/customers', name: 'customers', component: () => import('@/views/CustomersView.vue') },
    { path: '/timeline', name: 'timeline', component: () => import('@/views/UsageTimelineView.vue') },
    { path: '/users', name: 'users', component: () => import('@/views/UsersView.vue'), meta: { admin: true } },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (!auth.loaded) {
    await auth.fetchMe()
  }
  if (to.meta.public) {
    return auth.isAuthenticated ? '/timeline' : true
  }
  if (!auth.isAuthenticated) {
    return '/login'
  }
  if (auth.currentUser!.mustChangePassword && to.path !== '/set-password') {
    return '/set-password'
  }
  if (to.meta.admin && !auth.isAdmin) {
    return '/timeline'
  }
  return true
})

export default router
