import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/timeline' },
    { path: '/team-members', name: 'team-members', component: () => import('@/views/TeamMembersView.vue') },
    { path: '/customers', name: 'customers', component: () => import('@/views/CustomersView.vue') },
    { path: '/timeline', name: 'timeline', component: () => import('@/views/UsageTimelineView.vue') },
  ],
})

export default router
