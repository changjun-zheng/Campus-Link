<script setup lang="ts">
/**
 * 顶栏账号菜单（CR-174）：替换 el-dropdown，避免把 popper / tooltip / focus-trap
 * 整套随主包下发；行为对齐原菜单（点击开合、Esc 关闭、方向键移动、点击外部关闭）。
 */
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import SvgIcon from './SvgIcon.vue'
import UserAvatar from './UserAvatar.vue'

defineProps<{
  nickname: string
  avatarUrl?: string
  isAdmin?: boolean
}>()
const emit = defineEmits<{ logout: [] }>()

const open = ref(false)
const rootEl = ref<HTMLElement | null>(null)
const triggerEl = ref<HTMLButtonElement | null>(null)

function menuItems(): HTMLElement[] {
  return Array.from(rootEl.value?.querySelectorAll<HTMLElement>('[role="menuitem"]') ?? [])
}

function openMenu() {
  open.value = true
  void nextTick(() => menuItems()[0]?.focus())
}

function closeMenu(refocusTrigger = true) {
  if (!open.value) return
  open.value = false
  if (refocusTrigger) triggerEl.value?.focus()
}

function toggleMenu() {
  if (open.value) closeMenu()
  else openMenu()
}

function onTriggerKeydown(e: KeyboardEvent) {
  if (e.key === 'ArrowDown') {
    e.preventDefault()
    openMenu()
  }
}

function onMenuKeydown(e: KeyboardEvent) {
  const items = menuItems()
  if (!items.length) return
  const index = items.indexOf(document.activeElement as HTMLElement)
  if (e.key === 'Escape') {
    e.preventDefault()
    closeMenu()
  } else if (e.key === 'ArrowDown') {
    e.preventDefault()
    items[(index + 1) % items.length]?.focus()
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    items[(index - 1 + items.length) % items.length]?.focus()
  } else if (e.key === 'Home') {
    e.preventDefault()
    items[0]?.focus()
  } else if (e.key === 'End') {
    e.preventDefault()
    items[items.length - 1]?.focus()
  } else if (e.key === 'Tab') {
    closeMenu(false)
  }
}

function onDocumentClick(e: MouseEvent) {
  if (open.value && !rootEl.value?.contains(e.target as Node)) closeMenu(false)
}

onMounted(() => document.addEventListener('click', onDocumentClick, true))
onBeforeUnmount(() => document.removeEventListener('click', onDocumentClick, true))
</script>

<template>
  <div ref="rootEl" class="relative">
    <button
      ref="triggerEl"
      type="button"
      class="flex cursor-pointer items-center gap-1.5 border-0 bg-transparent p-0"
      aria-haspopup="menu"
      :aria-expanded="open"
      aria-label="账号菜单"
      @click.stop="toggleMenu"
      @keydown="onTriggerKeydown"
    >
      <UserAvatar :src="avatarUrl" :name="nickname" :size="28" />
      <span class="hidden max-w-[100px] truncate text-body text-ink md:inline">{{ nickname }}</span>
      <SvgIcon name="chevron-down" :size="12" class="text-ink-meta" />
    </button>

    <div
      v-if="open"
      role="menu"
      aria-label="账号菜单"
      class="absolute right-0 top-full z-50 mt-2 w-44 rounded-lg border-[0.5px] border-line bg-card py-1"
      @keydown="onMenuKeydown"
    >
      <RouterLink
        to="/notifications"
        role="menuitem"
        tabindex="-1"
        class="block px-3 py-2 text-body text-ink-regular hover:bg-bg-hover hover:text-link"
        @click="closeMenu(false)"
      >
        通知中心
      </RouterLink>
      <RouterLink
        to="/messages"
        role="menuitem"
        tabindex="-1"
        class="block px-3 py-2 text-body text-ink-regular hover:bg-bg-hover hover:text-link"
        @click="closeMenu(false)"
      >
        私信
      </RouterLink>
      <RouterLink
        v-if="isAdmin"
        to="/admin"
        role="menuitem"
        tabindex="-1"
        class="block border-t-[0.5px] border-divider px-3 py-2 text-body text-ink-regular hover:bg-bg-hover hover:text-link"
        @click="closeMenu(false)"
      >
        管理后台
      </RouterLink>
      <RouterLink
        to="/me/posts"
        role="menuitem"
        tabindex="-1"
        class="block px-3 py-2 text-body text-ink-regular hover:bg-bg-hover hover:text-link"
        @click="closeMenu(false)"
      >
        我的帖子
      </RouterLink>
      <RouterLink
        to="/me/replies"
        role="menuitem"
        tabindex="-1"
        class="block px-3 py-2 text-body text-ink-regular hover:bg-bg-hover hover:text-link"
        @click="closeMenu(false)"
      >
        我的回帖
      </RouterLink>
      <RouterLink
        to="/favorites"
        role="menuitem"
        tabindex="-1"
        class="block px-3 py-2 text-body text-ink-regular hover:bg-bg-hover hover:text-link"
        @click="closeMenu(false)"
      >
        我的收藏
      </RouterLink>
      <RouterLink
        to="/settings"
        role="menuitem"
        tabindex="-1"
        class="block border-t-[0.5px] border-divider px-3 py-2 text-body text-ink-regular hover:bg-bg-hover hover:text-link"
        @click="closeMenu(false)"
      >
        设置
      </RouterLink>
      <button
        type="button"
        role="menuitem"
        tabindex="-1"
        class="block w-full border-t-[0.5px] border-divider px-3 py-2 text-left text-body text-ink-regular hover:bg-bg-hover hover:text-link"
        @click="closeMenu(false); emit('logout')"
      >
        退出登录
      </button>
    </div>
  </div>
</template>
