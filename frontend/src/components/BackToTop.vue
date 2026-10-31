<script setup lang="ts">
/** 回到顶部（CR-175）：替换 el-backtop，避免 EP backtop 与其依赖进入主包 */
import { onBeforeUnmount, onMounted, ref } from 'vue'
import SvgIcon from './SvgIcon.vue'

const visible = ref(false)

function onScroll() {
  visible.value = window.scrollY > 400
}

function toTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
  onScroll()
})
onBeforeUnmount(() => window.removeEventListener('scroll', onScroll))
</script>

<template>
  <button v-if="visible" type="button" class="cl-backtop" aria-label="回到顶部" @click="toTop">
    <SvgIcon name="chevron-down" :size="18" class="rotate-180" />
  </button>
</template>

<style scoped>
.cl-backtop {
  position: fixed;
  right: 24px;
  bottom: 32px;
  z-index: 100;
  display: flex;
  width: 40px;
  height: 40px;
  align-items: center;
  justify-content: center;
  border: var(--cl-hairline) solid var(--cl-border-base);
  border-radius: 9999px;
  background: var(--cl-bg-card);
  color: var(--cl-text-regular);
  box-shadow: 0 2px 8px rgb(15 23 42 / 8%);
  cursor: pointer;
}
.cl-backtop:hover {
  border-color: var(--cl-color-primary-strong);
  color: var(--cl-color-primary-strong);
}
</style>
