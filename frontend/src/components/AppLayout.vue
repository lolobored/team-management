<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, RouterView, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const collapsed = ref(false)
const auth = useAuthStore()
const router = useRouter()

async function logout() {
  await auth.logout()
  router.push('/login')
}
</script>

<template>
  <div class="app-layout">
    <nav class="sidebar" :class="{ collapsed }">
      <div class="sidebar-header">
        <h2 v-show="!collapsed">Team Mgmt</h2>
        <button class="toggle-btn" :class="{ flipped: collapsed }" @click="collapsed = !collapsed" :title="collapsed ? 'Expand menu' : 'Collapse menu'">
          <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <line x1="4" y1="5" x2="16" y2="5" />
            <line x1="4" y1="10" x2="12" y2="10" />
            <line x1="4" y1="15" x2="16" y2="15" />
          </svg>
        </button>
      </div>
      <ul>
        <li><RouterLink to="/timeline" :title="collapsed ? 'Usage Timeline' : undefined">
          <span class="nav-icon">📊</span><span v-show="!collapsed" class="nav-label">Usage Timeline</span>
        </RouterLink></li>
        <li><RouterLink to="/team-members" :title="collapsed ? 'Team Members' : undefined">
          <span class="nav-icon">👥</span><span v-show="!collapsed" class="nav-label">Team Members</span>
        </RouterLink></li>
        <li><RouterLink to="/customers" :title="collapsed ? 'Customers' : undefined">
          <span class="nav-icon">🏢</span><span v-show="!collapsed" class="nav-label">Customers</span>
        </RouterLink></li>
        <li v-if="auth.isAdmin"><RouterLink to="/users" :title="collapsed ? 'Users' : undefined">
          <span class="nav-icon">🔐</span><span v-show="!collapsed" class="nav-label">Users</span>
        </RouterLink></li>
      </ul>
      <div class="sidebar-footer" v-if="auth.currentUser">
        <div v-show="!collapsed" class="user-meta">
          <span class="user-email">{{ auth.currentUser.email }}</span>
          <span class="user-role">{{ auth.currentUser.role }}</span>
        </div>
        <button class="logout-btn" data-testid="logout" :title="collapsed ? 'Log out' : undefined" @click="logout">
          <span class="nav-icon">⏻</span><span v-show="!collapsed" class="nav-label">Log out</span>
        </button>
      </div>
    </nav>
    <main class="content">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.app-layout { display: flex; min-height: 100vh; }
.sidebar { width: 220px; background: #1e293b; color: #e2e8f0; padding: 1rem; flex-shrink: 0; transition: width 0.2s ease; overflow: hidden; }
.sidebar.collapsed { width: 56px; padding: 1rem 0.5rem; }
.sidebar-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 1.5rem; min-height: 1.5rem; }
.sidebar h2 { font-size: 1.1rem; margin: 0; color: #38bdf8; white-space: nowrap; }
.toggle-btn { background: none; border: none; color: #94a3b8; cursor: pointer; padding: 6px; border-radius: 6px; flex-shrink: 0; display: flex; align-items: center; justify-content: center; transition: color 0.2s, background 0.2s, transform 0.3s ease; }
.toggle-btn:hover { color: #fff; background: #334155; }
.toggle-btn.flipped { transform: scaleX(-1); }
.sidebar ul { list-style: none; padding: 0; }
.sidebar li { margin-bottom: 0.5rem; }
.sidebar a { color: #cbd5e1; text-decoration: none; display: flex; align-items: center; gap: 0.5rem; padding: 0.5rem 0.75rem; border-radius: 4px; white-space: nowrap; }
.sidebar a:hover, .sidebar a.router-link-active { background: #334155; color: #fff; }
.nav-icon { flex-shrink: 0; font-size: 1.1rem; width: 1.3rem; text-align: center; }
.nav-label { overflow: hidden; text-overflow: ellipsis; }
.content { flex: 1; padding: 1.5rem; background: #f8fafc; }
.sidebar { display: flex; flex-direction: column; }
.sidebar ul { flex: 1; }
.sidebar-footer { border-top: 1px solid #334155; padding-top: 0.75rem; margin-top: 0.5rem; }
.user-meta { display: flex; flex-direction: column; margin-bottom: 0.4rem; }
.user-email { font-size: 0.78rem; color: #cbd5e1; overflow: hidden; text-overflow: ellipsis; }
.user-role { font-size: 0.65rem; color: #64748b; text-transform: uppercase; letter-spacing: 0.04em; }
.logout-btn { width: 100%; display: flex; align-items: center; gap: 0.5rem; padding: 0.5rem 0.75rem; background: none; border: none; color: #cbd5e1; cursor: pointer; border-radius: 4px; }
.logout-btn:hover { background: #334155; color: #fff; }
</style>
