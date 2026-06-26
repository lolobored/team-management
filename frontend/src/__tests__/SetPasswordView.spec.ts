import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ changePassword: vi.fn().mockResolvedValue(undefined) }),
}))

import SetPasswordView from '@/views/SetPasswordView.vue'

describe('SetPasswordView', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('disables submit until policy + confirm satisfied', async () => {
    const wrapper = mount(SetPasswordView)
    const submit = () => wrapper.get('[data-testid="sp-submit"]').element as HTMLButtonElement

    expect(submit().disabled).toBe(true)

    await wrapper.findAll('input')[0].setValue('OldPass1234')   // current
    await wrapper.findAll('input')[1].setValue('weak')          // new — fails policy
    await wrapper.findAll('input')[2].setValue('weak')          // confirm
    expect(submit().disabled).toBe(true)

    await wrapper.findAll('input')[1].setValue('NewStrongPass9') // valid 3-class 13-char
    await wrapper.findAll('input')[2].setValue('NewStrongPass9')
    expect(submit().disabled).toBe(false)
  })

  it('shows the length check ticking green', async () => {
    const wrapper = mount(SetPasswordView)
    await wrapper.findAll('input')[1].setValue('NewStrongPass9')
    expect(wrapper.get('[data-testid="check-length"]').classes()).toContain('ok')
  })
})
