<script setup lang="ts">
/**
 * 列表页统一页头（CR-202）
 *
 * 批次 A 定下的层次是「页面底色 + 唯一白卡」；其余列表页此前是「标题一张小卡 + 内容一张卡」，
 * 两块白卡把页面切成两条，与批次 A 相反。本组件把页头降为透明文字区，白卡只留内容。
 *
 * - 标题固定 h1 20px / 600（--cl-font-title-lg），页面内只此一处一级标题；
 * - `lead` 插槽放标题左侧的视觉标识（如版块图标），`actions` 插槽放右侧动作（筛选、按钮），
 *   `default` 插槽放标题下方的 Tab / 筛选条；
 * - 底部 0.5px 分隔线与内容卡拉开，不再用卡片描边。
 */
withDefaults(
  defineProps<{
    title: string
    /** 标题下的一句说明；传空串且不给 description 插槽时不渲染 */
    description?: string
  }>(),
  { description: '' },
)
</script>

<template>
  <header class="mb-4 border-b-[0.5px] border-line pb-3">
    <div class="flex flex-wrap items-start justify-between gap-3">
      <div class="flex min-w-0 items-start gap-3">
        <div v-if="$slots.lead" class="flex-none pt-0.5">
          <slot name="lead" />
        </div>
        <div class="min-w-0">
          <h1 class="text-title-lg font-semibold leading-snug text-ink">
            <slot name="title">{{ title }}</slot>
          </h1>
          <p v-if="description || $slots.description" class="mt-1 text-note leading-body text-ink-meta">
            <slot name="description">{{ description }}</slot>
          </p>
        </div>
      </div>
      <div v-if="$slots.actions" class="flex shrink-0 flex-wrap items-center gap-2">
        <slot name="actions" />
      </div>
    </div>
    <div v-if="$slots.default" class="mt-3">
      <slot />
    </div>
  </header>
</template>
