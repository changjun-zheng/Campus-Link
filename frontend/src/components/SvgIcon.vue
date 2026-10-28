<script setup lang="ts">
/**
 * 统一图标组件（CR-055 批次 3）：Tabler Icons（MIT）线性图标。
 *
 * 不复用 `el-icon`——EP 给 `.el-icon` 加了 `fill: currentColor`，作为 CSS 规则它的优先级
 * 高于 SVG 自身的 `fill="none"` presentation attribute，描边图标会被灌成实心块。
 * 故自带 `.cl-icon` 容器（样式见 styles/tokens.css），并用 `fill: none` 再兜一层。
 *
 * `v-html` 注入的是本仓库编译期内联的静态 SVG（src/assets/icons/），不含任何用户输入，
 * 不存在注入面；ADR-005 的"前端不得二次渲染 / 净化"约束只针对 Markdown 正文，不涉及图标。
 */
import { computed } from 'vue'
import { iconSvg, type IconName } from '../assets/icons'

/** 尺寸档位（CR-160，ui-ux-pro-max「Consistent Icon Sizing」）：12 元信息 / 16 正文 /
 *  20 导航 / 24 卡片头 / 60 空态展示。新增尺寸须落入既有档位，不要引入散值。 */
const props = withDefaults(defineProps<{ name: IconName; size?: number }>(), { size: 16 })

const svg = computed(() => iconSvg(props.name))
const box = computed(() => `${props.size}px`)
</script>

<template>
  <span class="cl-icon" :style="{ width: box, height: box }" aria-hidden="true" v-html="svg" />
</template>
