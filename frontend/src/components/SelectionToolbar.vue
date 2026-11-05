<script setup lang="ts">
/**
 * 正文选区浮出「问 AI」（CR-200）。
 *
 * <p>只负责检测「选区是否落在帖子正文容器内」与定位；内容长度、登录与面板开关由调用方处理。
 * `mousedown.prevent` 是必需的：点击工具条会让浏览器收起选区，先阻止默认行为才能把选中的
 * 文本完整交给面板。
 */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import SvgIcon from './SvgIcon.vue'

const props = defineProps<{
  container: HTMLElement | null
  enabled: boolean
}>()

const emit = defineEmits<{ ask: [text: string] }>()

const visible = ref(false)
const x = ref(0)
const y = ref(0)
const below = ref(false)
const text = ref('')
let selectionTimer: number | null = null

function hide() {
  visible.value = false
}

function update() {
  if (!props.enabled || !props.container) {
    hide()
    return
  }
  const selection = window.getSelection()
  if (!selection || selection.rangeCount === 0 || selection.isCollapsed) {
    hide()
    return
  }
  const range = selection.getRangeAt(0)
  if (!props.container.contains(range.commonAncestorContainer)) {
    hide()
    return
  }
  const value = selection.toString().trim()
  if (!value) {
    hide()
    return
  }
  const rect = range.getBoundingClientRect()
  if (rect.width === 0 && rect.height === 0) {
    hide()
    return
  }
  const toolbarWidth = 96
  const margin = 12
  const center = rect.left + rect.width / 2
  x.value = Math.min(
    Math.max(center, margin + toolbarWidth / 2),
    window.innerWidth - margin - toolbarWidth / 2,
  )
  // 选中区域贴近页面顶部时放到选区下方，避免被吸顶栏遮住
  below.value = rect.top < 68
  y.value = below.value ? rect.bottom + 8 : rect.top - 8
  text.value = value
  visible.value = true
}

function onSelectionChange() {
  if (selectionTimer !== null) window.clearTimeout(selectionTimer)
  // 拖动选择时 selectionchange 很密集，停手后再定位，避免工具条抖动
  selectionTimer = window.setTimeout(() => {
    selectionTimer = null
    update()
  }, 120)
}

function onMouseUp(event: MouseEvent) {
  const target = event.target
  if (target instanceof Element && target.closest('.cl-selection-toolbar')) return
  update()
}

function onScroll() {
  hide()
}

function onAsk() {
  if (!text.value) return
  emit('ask', text.value)
  hide()
}

watch(
  () => props.enabled,
  (value) => {
    if (!value) hide()
  },
)
watch(
  () => props.container,
  () => hide(),
)

onMounted(() => {
  document.addEventListener('mouseup', onMouseUp)
  document.addEventListener('keyup', onSelectionChange)
  document.addEventListener('selectionchange', onSelectionChange)
  window.addEventListener('scroll', onScroll, { capture: true, passive: true })
  window.addEventListener('resize', onScroll)
})

onBeforeUnmount(() => {
  document.removeEventListener('mouseup', onMouseUp)
  document.removeEventListener('keyup', onSelectionChange)
  document.removeEventListener('selectionchange', onSelectionChange)
  window.removeEventListener('scroll', onScroll, { capture: true })
  window.removeEventListener('resize', onScroll)
  if (selectionTimer !== null) window.clearTimeout(selectionTimer)
})
</script>

<template>
  <Teleport to="body">
    <button
      v-if="visible"
      type="button"
      class="cl-selection-toolbar"
      :class="{ 'is-below': below }"
      :style="{ left: `${x}px`, top: `${y}px` }"
      @mousedown.prevent
      @click="onAsk"
    >
      <SvgIcon name="sparkles" :size="15" />
      问 AI
    </button>
  </Teleport>
</template>

<style scoped>
.cl-selection-toolbar {
  position: fixed;
  z-index: 950;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 34px;
  padding: 0 12px;
  border: 0.5px solid var(--cl-border-base);
  border-radius: var(--cl-radius-md);
  background: var(--cl-bg-card);
  box-shadow: var(--el-box-shadow-light);
  color: var(--cl-color-primary-strong);
  font-size: var(--cl-font-caption);
  font-weight: 500;
  line-height: 1;
  cursor: pointer;
  transform: translate(-50%, -100%);
}

.cl-selection-toolbar.is-below {
  transform: translate(-50%, 0);
}

.cl-selection-toolbar:hover {
  border-color: color-mix(in srgb, var(--cl-color-primary) 42%, var(--cl-border-base));
  background: var(--cl-color-primary-soft);
}

.cl-selection-toolbar:focus-visible {
  outline: none;
  box-shadow: var(--cl-focus-ring), var(--el-box-shadow-light);
}
</style>
