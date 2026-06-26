import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

const routeMeta = { public: undefined as boolean | undefined }
vi.mock('vue-router', () => ({
  useRoute: () => ({ meta: routeMeta }),
  RouterView: { name: 'RouterView', template: '<div data-testid="router-view" />' },
}))

const authState = {
  isAuthenticated: false,
  currentUser: null as { mustChangePassword: boolean } | null,
}
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    get isAuthenticated() { return authState.isAuthenticated },
    get currentUser() { return authState.currentUser },
  }),
}))

// Must import App after mocks are set up
import App from '@/App.vue'

describe('App.vue shell gating', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    routeMeta.public = undefined
    authState.isAuthenticated = false
    authState.currentUser = null
  })

  it('(a) public route → AppLayout NOT rendered', () => {
    routeMeta.public = true
    authState.isAuthenticated = true
    authState.currentUser = { mustChangePassword: false }
    const wrapper = mount(App, {
      global: { stubs: { AppLayout: true } },
    })
    expect(wrapper.find('app-layout-stub').exists()).toBe(false)
    expect(wrapper.find('[data-testid="router-view"]').exists()).toBe(true)
  })

  it('(b) authenticated non-public, no mustChangePassword → AppLayout rendered', () => {
    routeMeta.public = undefined
    authState.isAuthenticated = true
    authState.currentUser = { mustChangePassword: false }
    const wrapper = mount(App, {
      global: { stubs: { AppLayout: true } },
    })
    expect(wrapper.find('app-layout-stub').exists()).toBe(true)
    expect(wrapper.find('[data-testid="router-view"]').exists()).toBe(false)
  })

  it('(c) authenticated but mustChangePassword → AppLayout NOT rendered', () => {
    routeMeta.public = undefined
    authState.isAuthenticated = true
    authState.currentUser = { mustChangePassword: true }
    const wrapper = mount(App, {
      global: { stubs: { AppLayout: true } },
    })
    expect(wrapper.find('app-layout-stub').exists()).toBe(false)
    expect(wrapper.find('[data-testid="router-view"]').exists()).toBe(true)
  })
})
