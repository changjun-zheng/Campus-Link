<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'
import { useUnreadNotifications } from './composables/useUnreadNotifications'
import { useUnreadMessages } from './composables/useUnreadMessages'
import { BOARDS } from './constants/boards'
import UserAvatar from './components/UserAvatar.vue'
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
  <el-container class="min-h-screen">
    <!-- 键盘用户跳过顶栏与版块导航（CR-171）：首 Tab 可见，激活后把焦点送到主区 -->
    <a href="#main-content" class="cl-skip-link" @click.prevent="skipToMain">跳到主内容</a>
    <el-header
      v-if="bare"
      class="cl-app-header border-b-[0.5px] border-line bg-card"
      :class="{ 'is-scrolled': scrolled }"
    >
      <!-- 窄屏下 --el-header-height 为 auto（tokens.css），补上下内边距撑回约 56px -->
      <div class="mx-auto flex h-full max-w-page items-center justify-between max-md:py-4">
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
    </el-header>
    <el-header
      v-else
      class="cl-app-header border-b-[0.5px] border-line bg-card"
      :class="{ 'is-scrolled': scrolled }"
    >
      <!-- 窄屏折成两行（设计稿 page-09）：第一行 logo + 图标，第二行版块胶囊带；
           行高由 tokens.css 的窄屏媒体查询把 --el-header-height 放开为 auto -->
      <div class="mx-auto flex h-full max-w-page flex-wrap items-center gap-x-5 gap-y-1 max-md:py-2 xl:flex-nowrap">
        <!-- 品牌字样用主色：WCAG 对 logo / 品牌名的文字不计对比度要求 -->
        <RouterLink to="/" class="order-1 flex shrink-0 items-center gap-2 py-1 -my-1 text-title font-medium text-primary">
          <img src="/logo.svg" alt="" class="h-[22px] w-auto" width="64" height="64" />
          <img src="/wordmark.png" alt="Campus-Link" class="h-[20px] w-auto" width="565" height="98" />
        </RouterLink>
        <div class="order-2 ml-auto flex shrink-0 items-center gap-4 max-md:gap-2 md:order-3 md:ml-0 xl:min-w-0 xl:shrink">
          <div
            class="hidden w-[320px] min-w-0 transition-[width] duration-fast ease-standard md:block md:ml-auto md:mr-auto md:focus-within:w-[380px] xl:min-w-[200px]"
          >
            <el-input
              v-model="searchKeyword"
              placeholder="搜索帖子标题 / 正文 / 标签"
              maxlength="50"
              clearable
              @keyup.enter="onSearchSubmit"
            >
              <template #prefix>
                <SvgIcon name="search" :size="16" />
              </template>
            </el-input>
          </div>
          <!-- 窄屏折叠为图标（ui-guideline §7）：进搜索页，该页输入框自动聚焦 -->
          <button
            type="button"
            class="-mx-1 -my-1 inline-flex h-7 w-7 cursor-pointer items-center justify-center text-ink-regular hover:text-link md:hidden"
            aria-label="搜索"
            @click="onSearchSubmit"
          >
            <SvgIcon name="search" :size="20" />
          </button>
          <RouterLink to="/publish" class="hidden md:block">
            <el-button type="primary" size="small">
              <SvgIcon name="pencil" :size="16" class="mr-1" />
              提问
            </el-button>
          </RouterLink>
          <RouterLink
            to="/notifications"
            class="-mx-1 -my-1 inline-flex h-7 w-7 items-center justify-center text-ink-regular hover:text-link"
            :aria-label="unreadCount ? `通知中心，${unreadLabel} 条未读` : '通知中心'"
          >
            <el-badge :value="unreadCount" :max="99" :hidden="!unreadCount">
              <SvgIcon name="bell" :size="20" />
            </el-badge>
          </RouterLink>
          <RouterLink
            to="/messages"
            class="-mx-1 -my-1 inline-flex h-7 w-7 items-center justify-center text-ink-regular hover:text-link"
            :aria-label="unreadMessages ? `私信，${unreadMessageLabel} 条未读` : '私信'"
          >
            <el-badge :value="unreadMessages" :max="99" :hidden="!unreadMessages">
              <SvgIcon name="mail" :size="20" />
            </el-badge>
          </RouterLink>
          <el-dropdown v-if="isLoggedIn">
            <span class="flex cursor-pointer items-center gap-1.5">
              <UserAvatar :src="auth.user?.avatarUrl" :name="nickname" :size="28" />
              <span class="hidden max-w-[100px] truncate text-body text-ink md:inline">{{ nickname }}</span>
              <SvgIcon name="chevron-down" :size="12" class="text-ink-meta" />
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="router.push('/notifications')">通知中心</el-dropdown-item>
                <el-dropdown-item @click="router.push('/messages')">私信</el-dropdown-item>
                  <el-dropdown-item v-if="auth.user?.role === 'SUPERADMIN'" divided @click="router.push('/admin')">管理后台</el-dropdown-item>
                <el-dropdown-item @click="router.push('/me/posts')">我的帖子</el-dropdown-item>
                <el-dropdown-item @click="router.push('/me/replies')">我的回帖</el-dropdown-item>
                <el-dropdown-item @click="router.push('/favorites')">我的收藏</el-dropdown-item>
                <el-dropdown-item divided @click="router.push('/settings')">设置</el-dropdown-item>
                <el-dropdown-item divided @click="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <RouterLink v-else to="/login">
            <el-button size="small">登录 / 注册</el-button>
          </RouterLink>
        </div>
        <!-- 窄屏整行铺满并出血到页头边缘：-mx-4 / +2rem 对应 tokens.css 窄屏下的 --el-header-padding: 0 16px
             ≥1280 改为按内容定宽（xl:flex-none）：导航不再被右侧搜索框挤压，空间不足时由搜索框收缩（xl:min-w-[200px]） -->
        <nav
          class="order-3 -mx-4 flex w-[calc(100%+2rem)] gap-2 overflow-x-auto bg-page px-4 py-2 [scrollbar-width:none] md:order-2 md:mx-0 md:w-auto md:flex-1 md:gap-0.5 md:bg-transparent md:px-0 md:py-0 xl:flex-none [&::-webkit-scrollbar]:hidden"
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
    </el-header>
    <el-main id="main-content" tabindex="-1" class="mx-auto w-full max-w-page">
      <!-- key 用 fullPath：同组件换参（如 /board/qna → /board/chat）也要触发淡入；
           外包一层 div 保证 Transition 子节点恒为单元素（部分视图是多根 fragment，直接动画会告警且淡入失效） -->
      <RouterView v-slot="{ Component: viewComponent, route: viewRoute }">
        <transition name="route-fade" mode="out-in">
          <div :key="viewRoute.fullPath">
            <component :is="viewComponent" />
          </div>
        </transition>
      </RouterView>
    </el-main>
    <el-footer v-if="!bare" class="text-center">
      <p class="text-note text-ink-regular">问有所答 · 学有同伴 · 求职有路</p>
      <p class="mt-1 text-caption text-ink-meta">Campus-Link · 重庆工程学院计算机专业学生社区</p>
    </el-footer>
    <!-- 返回顶部（CR-091，参考站通用交互）：全局窗口滚动，长列表页共用 -->
    <el-backtop :right="24" :bottom="32" />
  </el-container>
</template>

<style scoped>
/* sticky 的前提：滚动容器必须是文档本身。EP 给 .el-main 默认 overflow:auto——即使它从不
   内部滚动，非 visible 溢出也会使其成为 sticky 的定位参照（规范行为），导致 HomeView 两栏、
   详情页 TOC 等所有 sticky 侧栏永不吸附、表现为"跟着页面滚走"（CR-091 修复）。
   scoped 的属性选择器特异性高于 EP 的同名单类规则，可稳定覆盖。 */
.el-main {
  overflow: visible;
}

/* 移动端首屏高度基线（CR-170）：路由组件是懒加载 chunk，App 壳（页头 + 页脚）先绘制，
   chunk 到达后内容长高会把首屏底部的页脚顶出视口——实测设置页 CLS 0.089（Good 但非 0）。
   让主区在 <768 至少占「视口 − 页头」，页脚从首帧起就在折线以下，内容增长不再移动可见元素；
   桌面不改（CLS 0.009）。取 60px 是页头最小高度，宁可把页脚多推下去也不留可见缝隙。 */
@media (max-width: 767px) {
  .el-main {
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
