<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'

const props = withDefaults(defineProps<{
  modelValue: string
  suggestions: string[]
  placeholder?: string
  disabled?: boolean
  loading?: boolean
}>(), {
  placeholder: '',
  disabled: false,
  loading: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  select: [value: string]
}>()

const query = ref(props.modelValue)
const open = ref(false)
const activeIndex = ref(-1)
const inputEl = ref<HTMLInputElement>()
const listEl = ref<HTMLUListElement>()

watch(() => props.modelValue, (v) => {
  query.value = v
})

const filtered = computed(() => {
  if (!query.value) return props.suggestions
  const q = query.value.toLowerCase()
  return props.suggestions.filter(s => s.toLowerCase().includes(q))
})

function onInput(e: Event) {
  const val = (e.target as HTMLInputElement).value
  query.value = val
  emit('update:modelValue', val)
  open.value = true
  activeIndex.value = -1
}

function selectItem(item: string) {
  query.value = item
  emit('update:modelValue', item)
  emit('select', item)
  open.value = false
  activeIndex.value = -1
}

function onFocus() {
  open.value = true
}

function onBlur() {
  setTimeout(() => { open.value = false }, 150)
}

function scrollToActive() {
  nextTick(() => {
    const el = listEl.value?.children[activeIndex.value] as HTMLElement | undefined
    el?.scrollIntoView({ block: 'nearest' })
  })
}

function onKeydown(e: KeyboardEvent) {
  if (!open.value && e.key !== 'Escape') {
    open.value = true
    return
  }
  switch (e.key) {
    case 'ArrowDown':
      e.preventDefault()
      activeIndex.value = Math.min(activeIndex.value + 1, filtered.value.length - 1)
      scrollToActive()
      break
    case 'ArrowUp':
      e.preventDefault()
      activeIndex.value = Math.max(activeIndex.value - 1, -1)
      scrollToActive()
      break
    case 'Enter':
      e.preventDefault()
      if (activeIndex.value >= 0 && activeIndex.value < filtered.value.length) {
        selectItem(filtered.value[activeIndex.value])
      }
      break
    case 'Escape':
      open.value = false
      activeIndex.value = -1
      break
  }
}
</script>

<template>
  <div class="autocomplete" :class="{ disabled }">
    <input
      ref="inputEl"
      :value="query"
      :placeholder="loading ? 'Loading...' : placeholder"
      :disabled="disabled || loading"
      @input="onInput"
      @focus="onFocus"
      @blur="onBlur"
      @keydown="onKeydown"
      autocomplete="off"
    />
    <ul v-if="open && filtered.length > 0" ref="listEl" class="suggestions">
      <li
        v-for="(item, i) in filtered"
        :key="item"
        :class="{ active: i === activeIndex }"
        @mousedown.prevent="selectItem(item)"
      >
        {{ item }}
      </li>
    </ul>
  </div>
</template>

<style scoped>
.autocomplete { position: relative; }
.autocomplete.disabled input { opacity: 0.5; }
.suggestions {
  position: absolute; top: 100%; left: 0; right: 0; z-index: 50;
  max-height: 200px; overflow-y: auto;
  background: #fff; border: 1px solid #e2e8f0; border-radius: 4px;
  margin: 2px 0 0; padding: 0; list-style: none;
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
}
.suggestions li {
  padding: 6px 10px; cursor: pointer; font-size: 0.9rem;
}
.suggestions li:hover, .suggestions li.active {
  background: #eff6ff; color: #1d4ed8;
}
</style>
