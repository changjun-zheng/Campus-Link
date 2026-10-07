<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { listFollowingPosts, listPosts, type PostSort, type PostSummaryVo } from '../api/forum'
import { BOARDS } from '../constants/boards'
import { useAuthStore } from '../stores/auth'
import { formatTime } from '../utils/time'
import PostListItem from '../components/PostListItem.vue'
import SvgIcon from '../components/SvgIcon.vue'

const auth = useAuthStore()

/** 排序（F-FORUM-003 + CR-074）：latest = 最新，hot = 热榜（后端 sort=hot），following = 关注 Feed（仅登录可见） */
const sort = ref<PostSort | 'following'>('latest')

/** 排序 Tab（CR-091，参考 paicoding 分类 Tab）：文字型自有 Tab 取代 EP radio-button，
 * 选中态 = 主色 + 底部指示条；关注项仅登录可见 */
const sortOptions = computed<{ value: PostSort | 'following'; label: string }[]>(() => {
  const base: { value: PostSort | 'following'; label: string }[] = [
    { value: 'latest', label: '最新' },
    { value: 'hot', label: '热门' },
  ]
  return auth.isLoggedIn ? [...base, { value: 'following', label: '关注' }] : base
})

const posts = ref<PostSummaryVo[]>([])
const total = ref(0)
const page = ref(1)
/** 每批条数（CR-091 触底加载）：首屏 6 条，触底自动续载 6 条 */
const size = 6
const loading = ref(false)
const loadError = ref('')
/** 触底加载（CR-091，参考 CSDN）：滚动监听驱动——sentinel 进入视口下方 320px 即续载 */
const loadingMore = ref(false)
const moreError = ref(false)
const sentinelRef = ref<HTMLElement | null>(null)
/** 门（CR-091）：首屏 6 条时 sentinel 天然在视口内，必须等用户真的往下滑过才续载 */
let userScrolled = false
const hasMore = computed(() => posts.value.length < total.value)

/** 右栏热榜 Top5（CR-074 数据源，sort=hot） */
const hotTop5 = ref<PostSummaryVo[]>([])

async function loadHotTop() {
  try {
    const res = await listPosts(1, 5, undefined, 'hot')
    hotTop5.value = res.list
  } catch {
    hotTop5.value = []
  }
}

async function loadFirst() {
  loading.value = true
  loadError.value = ''
  try {
    const res =
      sort.value === 'following'
        ? await listFollowingPosts(1, size)
        : await listPosts(1, size, undefined, sort.value)
    posts.value = res.list
    total.value = res.total
    page.value = 1
  } catch (e) {
    posts.value = []
    total.value = 0
    loadError.value = e instanceof Error ? e.message : '帖子加载失败'
  } finally {
    loading.value = false
  }
  await nextTick()
  maybeLoadMore()
}

/**
 * 触底续载（CR-091，参考 CSDN loadMore）：追加下一批到列表尾部。
 * 追加后若 sentinel 仍在视口内（大屏 / 批量短）则继续补载，直到离开视口或没有更多。
 * 失败不打断浏览：尾部给"重试"，sentinel 仍在，再滚即可重试。
 */
async function loadMore() {
  if (loadingMore.value || loading.value || !hasMore.value) return
  loadingMore.value = true
  try {
    const next = page.value + 1
    const res =
      sort.value === 'following'
        ? await listFollowingPosts(next, size)
        : await listPosts(next, size, undefined, sort.value)
    posts.value.push(...res.list)
    page.value = next
    // 防御：total 虚高而后续页为空时收敛计数，避免无限触发
    if (!res.list.length) total.value = posts.value.length
  } catch {
    moreError.value = true
  } finally {
    loadingMore.value = false
  }
  await nextTick()
  maybeLoadMore()
}

function retryMore() {
  moreError.value = false
  maybeLoadMore()
}

/** 滚动驱动的续载判定：用户已滚动过 + sentinel 进入视口下方 320px + 还有更多 */
function maybeLoadMore() {
  if (loading.value || loadingMore.value || moreError.value || !hasMore.value || !userScrolled) return
  if (!sentinelRef.value) return
  if (sentinelRef.value.getBoundingClientRect().top > innerHeight + 320) return
  void loadMore()
}

function markUserScrolled() {
  if (window.scrollY > 0) {
    userScrolled = true
    maybeLoadMore()
  }
}

watch(sort, () => {
  posts.value = []
  total.value = 0
  page.value = 1
  moreError.value = false
  void loadFirst()
})

/** 右栏「最新发布」挂件（CR-091，参考 CSDN 滚动接力）：复用既有列表接口，仅取 5 条 */
const latestTop5 = ref<PostSummaryVo[]>([])

async function loadLatest() {
  try {
    const res = await listPosts(1, 5, undefined, 'latest')
    latestTop5.value = res.list
  } catch {
    latestTop5.value = []
  }
}

onMounted(() => {
  void loadFirst()
  loadHotTop()
  loadLatest()
  window.addEventListener('scroll', markUserScrolled, { passive: true })
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', markUserScrolled)
})

const myEntries = [
  { label: '我的收藏', to: '/favorites', icon: 'bookmark' },
  { label: '我的帖子', to: '/me/posts', icon: 'article' },
]
</script>

<template>
  <!-- 三栏布局（滚动设计参考 CSDN，CR-091）：两 aside self-stretch 拉伸到与中栏等高——
       左栏卡片 h-full 成为贯穿整页的"全高白条"（滚动时恒在，贴 CSDN 左菜单）；
       右栏前两个挂件随页面滚动，关于卡 sticky 接力（≈CSDN 滚后出现的活动日历位） -->
  <div class="flex items-stretch gap-5">
    <!-- ─── 左栏：版块导航（lg 以上显示） ─── -->
    <aside class="hidden w-[200px] flex-none self-stretch lg:block">
      <el-card shadow="never" class="h-full" :body-style="{ padding: '8px' }">
        <nav aria-label="版块导航">
          <RouterLink
            v-for="b in BOARDS"
            :key="b.code"
            :to="`/board/${b.code}`"
            class="flex items-center gap-2.5 rounded-md px-3 py-2.5 text-body text-ink-regular transition-colors duration-fast ease-standard hover:bg-bg-hover"
          >
            <span class="inline-flex h-7 w-7 flex-none items-center justify-center rounded-sm bg-primary-soft text-primary">
              <SvgIcon :name="b.icon" :size="15" />
            </span>
            {{ b.name }}
          </RouterLink>
        </nav>
        <el-divider class="my-2" />
        <nav aria-label="我的入口">
          <RouterLink
            v-for="m in myEntries"
            :key="m.to"
            :to="m.to"
            class="flex items-center gap-2.5 rounded-md px-3 py-2.5 text-body text-ink-regular transition-colors duration-fast ease-standard hover:bg-bg-hover"
          >
            <span class="inline-flex h-7 w-7 flex-none items-center justify-center rounded-sm bg-code text-ink-meta">
              <SvgIcon :name="m.icon" :size="15" />
            </span>
            {{ m.label }}
          </RouterLink>
        </nav>
      </el-card>
    </aside>

    <!-- ─── 中栏：信息流 ─── -->
    <main class="min-w-0 flex-1">
      <el-card shadow="never" :body-style="{ padding: 0 }">
        <template #header>
          <!-- 排序 Tab（CR-091）：文字型自有 Tab，选中态主色 + 底部指示条（参考站分类 Tab 风格）；
               真按钮保留键盘可达与 aria-pressed -->
          <nav class="flex items-center gap-1" aria-label="帖子排序">
            <button
              v-for="s in sortOptions"
              :key="s.value"
              type="button"
              class="relative cursor-pointer border-0 bg-transparent px-3 py-1.5 text-body transition-colors duration-fast ease-standard"
              :class="sort === s.value ? 'font-medium text-primary' : 'text-ink-regular hover:text-link'"
              :aria-pressed="sort === s.value"
              @click="sort = s.value"
            >
              {{ s.label }}
              <span
                aria-hidden="true"
                class="inset-x-3 -bottom-px absolute h-0.5 rounded-full transition-opacity duration-fast ease-standard"
                :class="sort === s.value ? 'bg-primary opacity-100' : 'opacity-0'"
              />
            </button>
          </nav>
        </template>

        <el-skeleton v-if="loading" animated class="px-5 pt-4">
          <template #template>
            <div v-for="i in 5" :key="i" class="border-b-[0.5px] border-divider py-3 last:border-b-0">
              <el-skeleton-item variant="text" style="width: 56%" />
              <div class="mt-2 flex items-center gap-2">
                <el-skeleton-item variant="text" style="width: 64px" />
                <el-skeleton-item variant="text" style="width: 64px" />
                <el-skeleton-item variant="text" style="width: 88px" />
              </div>
            </div>
          </template>
        </el-skeleton>

        <div v-else-if="loadError" class="p-5">
          <el-alert type="error" :closable="false" show-icon :title="loadError">
            <el-button size="small" @click="loadList">重新加载</el-button>
          </el-alert>
        </div>

        <div v-else-if="!posts.length" class="py-10 text-center">
          <p class="text-body text-ink-regular">
            {{ sort === 'following' ? '还没有关注任何作者，去帖子详情页点「＋ 关注」吧' : '还没有帖子，来发第一帖吧' }}
          </p>
          <RouterLink to="/publish">
            <el-button type="primary" class="mt-4">去发第一帖</el-button>
          </RouterLink>
        </div>

        <template v-else>
          <ul>
            <PostListItem v-for="p in posts" :key="p.id" :post="p" show-board />
          </ul>
          <!-- 触底加载（CR-091，参考 CSDN）：sentinel 进入视口下方 320px 即续载下一批并追加 -->
          <div ref="sentinelRef" class="px-5 py-4 text-center text-caption text-ink-meta">
            <template v-if="moreError">
              加载失败 ·
              <el-link type="primary" :underline="false" @click="retryMore">重试</el-link>
            </template>
            <span v-else-if="loadingMore">正在加载…</span>
            <span v-else-if="!hasMore && posts.length">已经到底了 · 共 {{ total }} 条</span>
          </div>
        </template>
      </el-card>
    </main>

    <!-- ─── 右栏：热榜 + 最新发布 + 站点卡（xl 以上显示）；关于卡 sticky 接力 ─── -->
    <aside class="hidden w-[300px] flex-none self-stretch xl:block">
      <el-card shadow="never" :body-style="{ padding: '16px' }">
        <template #header>
          <div class="flex items-center justify-between">
            <span class="font-medium">全站热榜</span>
            <RouterLink to="/board/qna" class="text-caption text-link hover:underline">更多</RouterLink>
          </div>
        </template>
        <ol v-if="hotTop5.length">
          <li
            v-for="(p, i) in hotTop5"
            :key="p.id"
            class="flex items-start gap-2 border-b-[0.5px] border-divider py-2 last:border-b-0"
          >
            <!-- 前三名徽章化（CR-091，参考 paicoding 热榜前三同色系强调）：底色 + 主色字；其余灰字 -->
            <span
              class="inline-flex h-5 w-5 flex-none items-center justify-center rounded-sm text-caption"
              :class="i < 3 ? 'bg-primary-soft font-medium text-primary' : 'text-ink-meta'"
            >
              {{ i + 1 }}
            </span>
            <RouterLink :to="`/post/${p.id}`" class="min-w-0 flex-1 truncate text-note text-ink-regular hover:text-link">
              {{ p.title }}
            </RouterLink>
          </li>
        </ol>
        <p v-else class="text-note text-ink-meta">热榜计算中，稍后再来看</p>
      </el-card>

      <!-- 最新发布挂件（CR-091，参考 CSDN 右栏滚动接力）：随页面滚动，与热榜形成"滚动后换内容"的节奏 -->
      <el-card shadow="never" class="mt-4" :body-style="{ padding: '16px' }">
        <template #header>
          <div class="flex items-center justify-between">
            <span class="font-medium">最新发布</span>
          </div>
        </template>
        <ol v-if="latestTop5.length">
          <li
            v-for="p in latestTop5"
            :key="p.id"
            class="border-b-[0.5px] border-divider py-2 last:border-b-0"
          >
            <RouterLink :to="`/post/${p.id}`" class="block truncate text-note text-ink-regular hover:text-link">
              {{ p.title }}
            </RouterLink>
            <p class="mt-0.5 text-caption text-ink-meta">{{ p.authorNickname }} · {{ formatTime(p.createdAt) }}</p>
          </li>
        </ol>
        <p v-else class="text-note text-ink-meta">还没有帖子</p>
      </el-card>

      <!-- 关于卡 sticky 接力（CR-091）：前两个挂件滚走后钉在右上，滚动后视口恒有内容（≈CSDN 活动日历位） -->
      <div class="sticky top-4 mt-4">
        <el-card shadow="never" :body-style="{ padding: '16px' }">
          <h3 class="text-title-sm font-medium text-ink">关于 Campus-Link</h3>
          <p class="mt-2 text-note leading-body text-ink-regular">
            重庆工程学院计算机专业学生的垂直交流论坛——问有所答、学有同伴、求职有路。
          </p>
          <RouterLink to="/publish">
            <el-button type="primary" size="small" class="mt-3">我要提问</el-button>
          </RouterLink>
        </el-card>
      </div>
    </aside>
  </div>
</template>
