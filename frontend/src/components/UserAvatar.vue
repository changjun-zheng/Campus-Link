<script setup lang="ts">
/**
 * 用户头像（CR-055 批次 2；CR-174 改原生实现）
 *
 * 后端事实（CR-162 起）：注册时按 `userId % 24` 写入默认头像，论坛列表 / 详情 / 楼层
 * 均下发 `authorAvatarUrl`（匿名帖为 null）——`src` 有值即优先渲染；为空时回落
 * **按昵称派生的占位头像**（首字 + 稳定底色），两者共用同一容器尺寸。
 *
 * 改为原生 span + img（不再用 el-avatar）：EP 的 avatar 会连带 avatar-group-props
 * → tooltip → popper / focus-trap 进入主包，实测占 ~30KB；原生实现保持同样的
 * 圆形、底色与字号，并给图片补 `loading="lazy" decoding="async"`。
 */
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    /** 真实头像 URL */
    src?: string
    /** 昵称，用于派生底色与首字 */
    name?: string
    size?: number
  }>(),
  { src: '', name: '', size: 32 },
)

/** 与 tokens.css 的 --cl-avatar-1~8 一一对应，改档位数须同步改令牌 */
const PALETTE_SIZE = 8

/** FNV-1a 32 位。按码点而非 UTF-16 单元遍历：昵称允许 emoji / 增补平面汉字，截半会撞档 */
function bucketOf(name: string): number {
  let hash = 0x811c9dc5
  for (const char of name) {
    hash ^= char.codePointAt(0) ?? 0
    hash = Math.imul(hash, 0x01000193)
  }
  return (hash >>> 0) % PALETTE_SIZE
}

const trimmed = computed(() => props.name.trim())
/** 首字同样按码点取，`slice(0, 1)` 对 emoji 会截出半个代理对 */
const initial = computed(() => Array.from(trimmed.value)[0] ?? '')

/**
 * EP 的 `--el-avatar-text-size` 恒为 14px，塞进 20px 小头像会溢出；缩到 10px 又不在
 * 本项目字号阶梯（最小 12px）上。故按尺寸在阶梯内二选一，不引入阶梯外字号。
 * 底色按实例派生；昵称为空时用主色浅底兜底。
 */
const avatarStyle = computed(() => {
  const style: Record<string, string> = {
    width: `${props.size}px`,
    height: `${props.size}px`,
    fontSize: props.size >= 28 ? 'var(--cl-font-body)' : 'var(--cl-font-caption)',
    color: 'var(--cl-avatar-ink)',
  }
  if (!props.src) {
    style.background = trimmed.value
      ? `var(--cl-avatar-${bucketOf(trimmed.value) + 1})`
      : 'var(--cl-color-primary-soft)'
  }
  return style
})
</script>

<template>
  <span
    class="inline-flex flex-none items-center justify-center overflow-hidden rounded-full align-middle font-medium"
    :style="avatarStyle"
  >
    <img
      v-if="src"
      :src="src"
      :alt="name"
      loading="lazy"
      decoding="async"
      class="h-full w-full object-cover"
    />
    <span v-else-if="initial" role="img" :aria-label="name || '用户头像'" aria-hidden="false">{{ initial }}</span>
  </span>
</template>
