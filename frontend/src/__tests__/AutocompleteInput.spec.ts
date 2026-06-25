import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import AutocompleteInput from '@/components/AutocompleteInput.vue'

const suggestions = ['Australia', 'Austria', 'Brazil', 'Canada']

function mountAC(props: Record<string, unknown> = {}) {
  return mount(AutocompleteInput, { props: { modelValue: '', suggestions, ...props } })
}

describe('AutocompleteInput', () => {
  it('shows all suggestions on focus and filters case-insensitively as you type', async () => {
    const wrapper = mountAC()
    await wrapper.find('input').trigger('focus')
    expect(wrapper.findAll('li')).toHaveLength(4)

    await wrapper.find('input').setValue('aus')
    expect(wrapper.findAll('li').map(li => li.text())).toEqual(['Australia', 'Austria'])
  })

  it('emits update:modelValue while typing', async () => {
    const wrapper = mountAC()
    await wrapper.find('input').setValue('can')
    expect(wrapper.emitted('update:modelValue')!.at(-1)).toEqual(['can'])
  })

  it('selecting a suggestion emits update:modelValue + select and closes the list', async () => {
    const wrapper = mountAC()
    await wrapper.find('input').trigger('focus')
    await wrapper.findAll('li')[2].trigger('mousedown') // Brazil

    expect(wrapper.emitted('update:modelValue')!.at(-1)).toEqual(['Brazil'])
    expect(wrapper.emitted('select')![0]).toEqual(['Brazil'])
    expect(wrapper.findAll('li')).toHaveLength(0)
  })

  it('ArrowDown then Enter selects the active suggestion', async () => {
    const wrapper = mountAC()
    const input = wrapper.find('input')
    await input.trigger('focus')
    await input.trigger('keydown', { key: 'ArrowDown' }) // active index 0 -> Australia
    await input.trigger('keydown', { key: 'Enter' })

    expect(wrapper.emitted('select')![0]).toEqual(['Australia'])
  })

  it('disables the input when disabled or loading', () => {
    expect((mountAC({ disabled: true }).find('input').element as HTMLInputElement).disabled).toBe(true)
    expect((mountAC({ loading: true }).find('input').element as HTMLInputElement).disabled).toBe(true)
  })
})
