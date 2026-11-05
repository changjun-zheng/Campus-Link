<script lang="ts">
/**
 * 环形图分段（2026-11-05，管理后台数据看板）。
 * color 由调用方传入令牌变量（如 var(--cl-color-primary-strong)），组件本身不写色值。
 */
export interface DonutSegment {
  label: string
  value: number
  color: string
}
</script>

<script setup lang="ts">
/**
 * 纯 SVG 环形图：不引图表库、不用渐变，描边弧段 + 同屏图例。
 * 数值与百分比始终以文字给出，颜色不是唯一信息载体；总量为 0 时只画底环。
 */
import { computed } from 'vue'

const props = defineProps<{
  segments: DonutSegment[]
  /** 圆心下方说明，如「内容」 */
  caption?: string
}>()

const R = 38
const CIRC = 2 * Math.PI * R

const total = computed(() => props.segments.reduce((sum, s) => sum + s.value, 0))

const arcs = computed(() => {
  let offset = 0
  return props.segments
    .filter((s) => s.value > 0)
    .map((s) => {
      const length = (s.value / total.value) * CIRC
      const arc = { ...s, length, offset }
      offset += length
      return arc
    })
})

function pct(value: number): number {
  return total.value ? Math.round((value / total.value) * 100) : 0
}

const ariaLabel = computed(
  () => `环形图：${props.segments.map((s) => `${s.label} ${s.value}`).join('，')}`,
)
</script>

<template>
  <div class="flex flex-wrap items-center justify-center gap-5">
    <div class="relative h-32 w-32 flex-none">
      <svg viewBox="0 0 100 100" class="h-full w-full" role="img" :aria-label="ariaLabel">
        <circle
          cx="50"
          cy="50"
          :r="R"
          fill="none"
          stroke="var(--cl-border-divider)"
          stroke-width="12"
        />
        <circle
          v-for="arc in arcs"
          :key="arc.label"
          cx="50"
          cy="50"
          :r="R"
          fill="none"
          :stroke="arc.color"
          stroke-width="12"
          :stroke-dasharray="`${arc.length} ${CIRC - arc.length}`"
          :stroke-dashoffset="-arc.offset"
          transform="rotate(-90 50 50)"
        />
      </svg>
      <div class="absolute inset-0 flex flex-col items-center justify-center">
        <span class="text-title-lg font-medium tabular-nums text-ink">{{ total }}</span>
        <span v-if="caption" class="text-caption text-ink-meta">{{ caption }}</span>
      </div>
    </div>
    <ul class="min-w-[150px] max-w-[220px] flex-1 space-y-2">
      <li
        v-for="s in segments"
        :key="s.label"
        class="flex items-center justify-between gap-3 text-note"
      >
        <span class="inline-flex min-w-0 items-center gap-2">
          <span
            class="h-2 w-2 flex-none rounded-full"
            :style="{ background: s.color }"
            aria-hidden="true"
          />
          <span class="truncate text-ink-regular">{{ s.label }}</span>
        </span>
        <span class="flex-none tabular-nums text-ink">
          {{ s.value }}<span class="ml-1 text-caption text-ink-meta">{{ pct(s.value) }}%</span>
        </span>
      </li>
    </ul>
  </div>
</template>
