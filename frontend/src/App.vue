<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'
import { useUnreadNotifications } from './composables/useUnreadNotifications'
import { useUnreadMessages } from './composables/useUnreadMessages'
import { BOARDS } from './constants/boards'
import UserMenu from './components/UserMenu.vue'
import BackToTop from './components/BackToTop.vue'
import SvgIcon from './components/SvgIcon.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { unreadCount } = useUnreadNotifications()
/** 私信未读角标（F-SOC-003 / CR-135）：30 秒轮询 + 会话页发送 / 已读后主动刷新 */
const { unreadCount: unreadMessages } = useUnreadMessages()
/** 角标显示口径（>99 折成 99+）必须同步进无障碍名称，否则可见数字不在名称里（label-content-name-mismatch） */
const unreadLabel = computed(() => (unreadCount.value > 99 ? '99+' : String(unreadCount.value)))
const unreadMessageLabel = computed(() => (unreadMessages.value > 99 ? '99+' : String(unreadMessages.value)))

const nickname = computed(() => auth.user?.nickname || '')
const isLoggedIn = computed(() => auth.isLoggedIn)
/** 登录页用独立极简页头（设计稿 page-05），不渲染全局顶栏与页脚 */
const bare = computed(() => Boolean(route.meta.bare))

const searchKeyword = ref('')

/** 顶栏滚动态（CR-052）：向下滚动超过 4px 后转半透明毛玻璃，回顶复原 */
const scrolled = ref(false)
function onScroll() {
  scrolled.value = window.scrollY > 4
}
onMounted(() => {
  onScroll()
  window.addEventListener('scroll', onScroll, { passive: true })
})
onBeforeUnmount(() => window.removeEventListener('scroll', onScroll))

function logout() {
  auth.logout()
  router.push('/')
}

/** 跳转链接（CR-171）：把焦点送到主区而不是只滚动，读屏与键盘用户从此处继续 Tab */
function skipToMain() {
  const main = document.getElementById('main-content')
  if (!main) return
  main.focus()
  main.scrollIntoView({ block: 'start' })
}

/** 顶栏回车即搜；关键词为空时进搜索页（该页自带输入框并自动聚焦） */
function onSearchSubmit() {
  const kw = searchKeyword.value.trim()
  router.push(kw ? { path: '/search', query: { keyword: kw } } : { path: '/search' })
}
</script>

<template>
  <div class="flex min-h-screen flex-col">
    <!-- 键盘用户跳过顶栏与版块导航（CR-171）：首 Tab 可见，激活后把焦点送到主区 -->
    <a href="#main-content" class="cl-skip-link" @click.prevent="skipToMain">跳到主内容</a>
    <header
      v-if="bare"
      class="cl-app-header border-b-[0.5px] border-line bg-card"
      :class="{ 'is-scrolled': scrolled }"
    >
      <!-- 窄屏下 --el-header-height 为 auto（tokens.css），补上下内边距撑回约 56px -->
      <div class="mx-auto flex h-full max-w-page items-center justify-between max-xl:py-4">
        <!-- 品牌字样用主色：WCAG 对 logo / 品牌名的文字不计对比度要求 -->
        <RouterLink to="/" class="flex shrink-0 items-center gap-2 py-1 -my-1 text-title font-medium text-primary">
          <img src="/logo.svg" alt="" class="h-[22px] w-auto" width="64" height="64" />
          <img src="/wordmark.png" alt="Campus-Link" class="h-[20px] w-auto" width="565" height="98" />
        </RouterLink>
        <RouterLink to="/" class="flex items-center gap-1 text-body">
          <SvgIcon name="arrow-left" :size="16" />
          返回首页
        </RouterLink>
      </div>
    </header>
    <header
      v-else
      class="cl-app-header border-b-[0.5px] border-line bg-card"
      :class="{ 'is-scrolled': scrolled }"
    >
      <!-- 窄屏折成两行（设计稿 page-09）：第一行 logo + 图标，第二行版块胶囊带；
           行高由 tokens.css 的窄屏媒体查询把 --el-header-height 放开为 auto -->
      <div class="mx-auto flex h-full max-w-page flex-wrap items-center gap-x-5 gap-y-1 max-xl:py-2 xl:flex-nowrap">
        <!-- 品牌字样用主色：WCAG 对 logo / 品牌名的文字不计对比度要求 -->
        <RouterLink to="/" class="order-1 flex shrink-0 items-center gap-2 py-1 -my-1 text-title font-medium text-primary">
          <img src="/logo.svg" alt="" class="h-[22px] w-auto" width="64" height="64" />
          <img src="/wordmark.png" alt="Campus-Link" class="h-[20px] w-auto" width="565" height="98" />
        </RouterLink>
        <div class="order-2 ml-auto flex shrink-0 items-center gap-4 max-xl:gap-2 xl:order-3 xl:ml-0 xl:min-w-0 xl:shrink">
          <div
            class="hidden w-[320px] min-w-0 transition-[width] duration-fast ease-standard xl:block xl:ml-auto xl:mr-auto xl:focus-within:w-[380px] xl:min-w-[200px]"
          >
            <SvgIcon
              name="search"
              :size="16"
              class="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-ink-meta"
            />
            <input
              v-model="searchKeyword"
              type="text"
              maxlength="50"
              aria-label="搜索帖子标题 / 正文 / 标签"
              placeholder="搜索帖子标题 / 正文 / 标签"
              class="cl-header-search"
              @keyup.enter="onSearchSubmit"
            />
            <button
              v-if="searchKeyword"
              type="button"
              class="cl-header-search-clear"
              aria-label="清空搜索"
              @click="searchKeyword = ''"
            >
              ×
            </button>
          </div>
          <!-- 窄屏折叠为图标（ui-guideline §7）：进搜索页，该页输入框自动聚焦 -->
          <button
            type="button"
            class="-mx-1 -my-1 inline-flex h-7 w-7 cursor-pointer items-center justify-center text-ink-regular hover:text-link xl:hidden"
            aria-label="搜索"
            @click="onSearchSubmit"
          >
            <SvgIcon name="search" :size="20" />
          </button>
          <RouterLink to="/publish" class="cl-header-cta hidden md:inline-flex">
            <SvgIcon name="pencil" :size="16" />
            提问
          </RouterLink>
          <RouterLink
            to="/notifications"
            class="-mx-1 -my-1 inline-flex h-7 w-7 items-center justify-center text-ink-regular hover:text-link"
            :aria-label="unreadCount ? `通知中心，${unreadLabel} 条未读` : '通知中心'"
          >
            <span class="relative inline-flex">
              <SvgIcon name="bell" :size="20" />
              <span v-if="unreadCount" class="cl-header-badge" aria-hidden="true">{{ unreadLabel }}</span>
            </span>
          </RouterLink>
          <RouterLink
            to="/messages"
            class="-mx-1 -my-1 inline-flex h-7 w-7 items-center justify-center text-ink-regular hover:text-link"
            :aria-label="unreadMessages ? `私信，${unreadMessageLabel} 条未读` : '私信'"
          >
            <span class="relative inline-flex">
              <SvgIcon name="mail" :size="20" />
              <span v-if="unreadMessages" class="cl-header-badge" aria-hidden="true">{{ unreadMessageLabel }}</span>
            </span>
          </RouterLink>
          <UserMenu
            v-if="isLoggedIn"
            :nickname="nickname"
            :avatar-url="auth.user?.avatarUrl"
            :is-admin="auth.user?.role === 'SUPERADMIN'"
            @logout="logout"
          />
          <RouterLink v-else to="/login" class="cl-header-login">登录 / 注册</RouterLink>
        </div>
        <!-- 窄屏整行铺满并出血到页头边缘：-mx-4 / +2rem 对应 tokens.css 窄屏下的 --el-header-padding: 0 16px
             ≥1280 改为按内容定宽（xl:flex-none）：导航不再被右侧搜索框挤压，空间不足时由搜索框收缩（xl:min-w-[200px]） -->
        <nav
          class="order-3 -mx-4 flex w-[calc(100%+2rem)] gap-2 overflow-x-auto bg-page px-4 py-2 [scrollbar-width:none] xl:order-2 xl:mx-0 xl:w-auto xl:flex-none xl:gap-0.5 xl:bg-transparent xl:px-0 xl:py-0 [&::-webkit-scrollbar]:hidden"
        >
          <RouterLink
            v-for="b in BOARDS"
            :key="b.code"
            :to="`/board/${b.code}`"
            class="whitespace-nowrap rounded-full border-[0.5px] border-line bg-card px-3 py-1 text-body text-ink transition-colors hover:bg-primary-soft hover:text-link md:rounded-md md:border-0 md:bg-transparent md:px-2.5 md:py-1.5 [&.router-link-active]:bg-primary-soft [&.router-link-active]:font-medium [&.router-link-active]:text-link"
          >
            {{ b.name }}
          </RouterLink>
          <!-- 组队广场（F-TEAM-001 / CR-137）：与 6 个版块并列，保持同一胶囊样式 -->
          <RouterLink
            to="/teams"
            class="whitespace-nowrap rounded-full border-[0.5px] border-line bg-card px-3 py-1 text-body text-ink transition-colors hover:bg-primary-soft hover:text-link md:rounded-md md:border-0 md:bg-transparent md:px-2.5 md:py-1.5 [&.router-link-active]:bg-primary-soft [&.router-link-active]:font-medium [&.router-link-active]:text-link"
          >
            组队
          </RouterLink>
        </nav>
      </div>
    </header>
    <main id="main-content" tabindex="-1" class="cl-main mx-auto w-full max-w-page">
      <!-- key 用 fullPath：同组件换参（如 /board/qna → /board/chat）也要触发淡入；
           外包一层 div 保证 Transition 子节点恒为单元素（部分视图是多根 fragment，直接动画会告警且淡入失效） -->
      <RouterView v-slot="{ Component: viewComponent, route: viewRoute }">
        <transition name="route-fade" mode="out-in">
          <div :key="viewRoute.fullPath">
            <component :is="viewComponent" />
          </div>
        </transition>
      </RouterView>
    </main>
    <footer v-if="!bare" class="cl-footer text-center">
      <p class="text-note text-ink-regular">问有所答 · 学有同伴 · 求职有路</p>
      <p class="mt-1 text-caption text-ink-meta">Campus-Link · 重庆工程学院计算机专业学生社区</p>
    </footer>
    <!-- 返回顶部（CR-091，参考站通用交互）：全局窗口滚动，长列表页共用 -->
    <BackToTop />
  </div>
</template>

<style scoped>
/* sticky 的前提：滚动容器必须是文档本身。EP 给 .el-main 默认 overflow:auto——即使它从不
   内部滚动，非 visible 溢出也会使其成为 sticky 的定位参照（规范行为），导致 HomeView 两栏、
   详情页 TOC 等所有 sticky 侧栏永不吸附、表现为"跟着页面滚走"（CR-091 修复）。
   scoped 的属性选择器特异性高于 EP 的同名单类规则，可稳定覆盖。 */
.cl-main {
  flex: 1 1 auto;
  box-sizing: border-box;
  padding: 20px;
  overflow: visible;
}

/* 移动端首屏高度基线（CR-170）：路由组件是懒加载 chunk，App 壳（页头 + 页脚）先绘制，
   chunk 到达后内容长高会把首屏底部的页脚顶出视口——实测设置页 CLS 0.089（Good 但非 0）。
   让主区在 <768 至少占「视口 − 页头」，页脚从首帧起就在折线以下，内容增长不再移动可见元素；
   桌面不改（CLS 0.009）。取 60px 是页头最小高度，宁可把页脚多推下去也不留可见缝隙。 */
@media (max-width: 767px) {
  .cl-main {
    min-height: calc(100vh - 60px);
    min-height: calc(100svh - 60px);
  }
}

/* 跳转链接只在聚焦时出现在左上角；主区要留出粘性顶栏的高度，否则跳过来会被页头盖住 */
.cl-skip-link {
  position: fixed;
  top: -64px;
  left: 8px;
  z-index: 3000;
  padding: 8px 14px;
  border-radius: var(--cl-radius-sm);
  background: var(--cl-color-primary-strong);
  color: #fff;
  font-size: var(--cl-font-body);
}
.cl-skip-link:focus {
  top: 8px;
}
#main-content {
  scroll-margin-top: 68px;
}
</style>

<style scoped>
/* 顶栏原生控件（CR-175）：去掉 EP input / button / badge，避免把组件与图标链带进主包 */
.cl-header-search {
  width: 100%;
  height: 32px;
  padding: 0 28px 0 32px;
  border: var(--cl-hairline) solid var(--cl-border-base);
  border-radius: var(--cl-radius-md);
  background: var(--cl-bg-card);
  color: var(--cl-text-primary);
  font-size: var(--cl-font-body);
  font-family: var(--cl-font-family);
}
.cl-header-search::placeholder {
  color: var(--cl-text-meta);
}
.cl-header-search:focus-visible {
  outline: 2px solid var(--cl-color-primary-strong);
  outline-offset: 2px;
  border-color: transparent;
}
.cl-header-search-clear {
  position: absolute;
  right: 4px;
  top: 50%;
  transform: translateY(-50%);
  width: 22px;
  height: 22px;
  border: 0;
  border-radius: 9999px;
  background: transparent;
  color: var(--cl-text-meta);
  font-size: 16px;
  line-height: 1;
  cursor: pointer;
}
.cl-header-search-clear:hover {
  background: var(--cl-bg-hover);
  color: var(--cl-text-primary);
}
.cl-header-cta {
  height: 28px;
  align-items: center;
  gap: 4px;
  padding: 0 10px;
  border-radius: var(--cl-radius-md);
  background: var(--cl-color-primary-strong);
  color: #fff;
  font-size: var(--cl-font-body);
  white-space: nowrap;
}
.cl-header-cta:hover {
  background: var(--cl-color-primary-strong-hover);
}
.cl-header-login {
  display: inline-flex;
  height: 28px;
  align-items: center;
  padding: 0 10px;
  border: var(--cl-hairline) solid var(--cl-border-base);
  border-radius: var(--cl-radius-md);
  background: var(--cl-bg-card);
  color: var(--cl-text-primary);
  font-size: var(--cl-font-body);
  white-space: nowrap;
}
.cl-header-login:hover {
  border-color: var(--cl-color-primary-strong);
  color: var(--cl-color-primary-strong);
}
.cl-header-badge {
  position: absolute;
  top: -4px;
  right: -6px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 9999px;
  background: var(--cl-color-danger-strong);
  color: #fff;
  font-size: var(--cl-font-caption);
  line-height: 16px;
  text-align: center;
  font-variant-numeric: tabular-nums;
}
</style>
