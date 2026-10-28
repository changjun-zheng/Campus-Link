<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
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
/** 触底加载（CR-091）：每批 6 条，距列表容器底部 320px 内自动续载 */
const loadingMore = ref(false)
const moreError = ref(false)
/** 列表自滚容器（CR-100）：桌面端窗口不滚，触底判定与滚动门都挂在这个元素上 */
const listWrapRef = ref<HTMLElement | null>(null)
/** 门（CR-091）：首屏 6 条时容器可能无溢出，必须等用户真的滚过列表才续载 */
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

/**
 * 触底续载判定（CR-100 改挂列表容器）：距底 320px 内 + 还有更多即追加。
 * ⚠️ 首屏 6 条几乎填满容器（仅溢出几十 px，滚不动）时若仍要求"用户先滚过"会死锁——
 * 容器无溢出（distance ≤ 0）必须自动续载直到可滚；有溢出后保留用户滚动门（CR-091 防抖语义）。
 */
function maybeLoadMore() {
  if (loading.value || loadingMore.value || moreError.value || !hasMore.value) return
  const el = listWrapRef.value
  if (!el) return
  const distance = el.scrollHeight - el.scrollTop - el.clientHeight
  if (distance > 320 || (distance > 0 && !userScrolled)) return
  void loadMore()
}

/** 列表容器滚动（模板 @scroll）：滚过即开门、跟踪位置驱动回到顶部、做触底判定 */
const listScrollTop = ref(0)

function onListScroll() {
  const el = listWrapRef.value
  if (!el) return
  listScrollTop.value = el.scrollTop
  if (el.scrollTop > 0) userScrolled = true
  maybeLoadMore()
}

function scrollListTop() {
  listWrapRef.value?.scrollTo({ top: 0, behavior: 'smooth' })
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

/** 右栏「等你来答」Top5（CR-142，N1）：问答帖且未采纳，最新在前；0 回答高亮提示最需要帮助 */
const unansweredTop5 = ref<PostSummaryVo[]>([])

async function loadUnanswered() {
  try {
    const res = await listPosts(1, 5, undefined, 'latest', 'unanswered')
    unansweredTop5.value = res.list
  } catch {
    unansweredTop5.value = []
  }
}

onMounted(() => {
  void loadFirst()
  loadHotTop()
  loadLatest()
  loadUnanswered()
})

const myEntries = [
  { label: '我的收藏', to: '/favorites', icon: 'bookmark' },
  { label: '我的帖子', to: '/me/posts', icon: 'article' },
]
</script>

<template>
  <!-- 三栏固定框架（CR-100，应用式布局）：lg+ 整框 sticky 钉满视口（顶栏下沿到底），左右栏与排序条
       完全不动，滚轮只让中栏帖子列表在自身容器内滚动（触底加载改挂列表容器 scroll 事件）；
       窄屏无固定高度，回退整页滚动 -->
  <div class="flex gap-5 lg:sticky lg:top-[60px] lg:h-[calc(100vh-60px)]">
    <!-- ─── 左栏：版块导航（lg 以上显示，栏内自滚） ─── -->
    <aside class="hidden w-[200px] flex-none lg:block lg:overflow-y-auto">
      <el-card shadow="never" :body-style="{ padding: '8px' }">
        <nav aria-label="版块导航">
          <RouterLink
            v-for="b in BOARDS"
            :key="b.code"
            :to="`/board/${b.code}`"
            class="flex items-center gap-2.5 rounded-md px-3 py-2.5 text-body text-ink-regular transition-colors duration-fast ease-standard hover:bg-bg-hover"
          >
            <span :class="['cl-board-icon', 'cl-board-icon--sm', `cl-board-icon--${b.code}`]">
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

    <!-- ─── 中栏：信息流（定高列，帖子列表在卡片体内自滚） ─── -->
    <main class="relative flex min-w-0 flex-1 flex-col">
      <el-card shadow="never" class="sort-card min-h-0 flex-1" :body-style="{ padding: 0 }">
        <template #header>
          <!-- 排序 Tab（CR-091）：文字型自有 Tab，选中态主色 + 底部指示条（参考站分类 Tab 风格）；
               真按钮保留键盘可达与 aria-pressed -->
          <nav class="flex items-center gap-1" aria-label="帖子排序">
            <button
              v-for="s in sortOptions"
              :key="s.value"
              type="button"
              class="relative cursor-pointer border-0 bg-transparent px-3 py-1.5 text-body transition-colors duration-fast ease-standard"
              :class="sort === s.value ? 'font-medium text-primary-strong' : 'text-ink-regular hover:text-link'"
              :aria-pressed="sort === s.value"
              @click="sort = s.value"
            >
              {{ s.label }}
              <span
                aria-hidden="true"
                class="inset-x-3 -bottom-px absolute h-0.5 rounded-full transition-opacity duration-fast ease-standard"
                :class="sort === s.value ? 'bg-primary-strong opacity-100' : 'opacity-0'"
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
            <el-button size="small" @click="loadFirst">重新加载</el-button>
          </el-alert>
        </div>

        <div v-else-if="!posts.length" class="flex flex-col items-center py-16 text-center">
          <span class="flex h-14 w-14 items-center justify-center rounded-full bg-primary-soft text-primary-strong">
            <SvgIcon name="messages" :size="26" />
          </span>
          <p class="mt-4 text-body font-medium text-ink">
            {{ sort === 'following' ? '还没有关注任何作者' : '还没有帖子' }}
          </p>
          <p class="mt-1 text-note text-ink-meta">
            {{ sort === 'following' ? '去帖子详情页点「＋ 关注」，TA 的新帖会出现在这里' : '来发第一帖，让同学看到你的问题' }}
          </p>
          <RouterLink v-if="sort !== 'following'" to="/publish" class="mt-5">
            <el-button type="primary">去发第一帖</el-button>
          </RouterLink>
        </div>

        <template v-else>
          <!-- 列表自滚容器（CR-100）：触底加载改挂本容器 scroll 事件（窗口在桌面端不再滚动） -->
          <div ref="listWrapRef" class="relative h-full overflow-y-auto" @scroll="onListScroll">
            <ul>
              <PostListItem v-for="p in posts" :key="p.id" :post="p" show-board />
            </ul>
            <!-- 触底加载（CR-091，参考 CSDN）：距底 320px 内续载下一批并追加 -->
            <div class="flex items-center gap-3 px-5 py-4 text-caption text-ink-meta">
              <span aria-hidden="true" class="h-[0.5px] flex-1 bg-divider" />
              <template v-if="moreError">
                加载失败 ·
                <el-link type="primary" :underline="false" @click="retryMore">重试</el-link>
              </template>
              <span v-else-if="loadingMore">正在加载…</span>
              <span v-else-if="!hasMore && posts.length" class="tabular-nums">
                已经到底了 · 共 {{ total }} 条
              </span>
              <span aria-hidden="true" class="h-[0.5px] flex-1 bg-divider" />
            </div>
          </div>
        </template>
      </el-card>
      <!-- 回到顶部（CR-102）：固定框架下窗口不滚、全局 backtop（App.vue，监听窗口）在首页永不出现。
           挂在不滚动的中栏列上 absolute 定位（挂进滚动容器会随内容坐标系跑偏），滚过 400px 浮现 -->
      <button
        v-show="listScrollTop > 400"
        type="button"
        class="absolute bottom-5 right-5 z-10 flex h-9 w-9 cursor-pointer items-center justify-center rounded-full border-[0.5px] border-line bg-card text-ink-regular transition-colors duration-fast ease-standard hover:border-primary hover:text-primary-strong"
        aria-label="回到顶部"
        @click="scrollListTop"
      >
        <SvgIcon name="chevron-down" :size="16" class="rotate-180" />
      </button>
    </main>

    <!-- ─── 右栏：热榜 + 最新发布 + 站点卡（xl 以上显示，栏内自滚） ─── -->
    <aside class="hidden w-[300px] flex-none xl:block xl:overflow-y-auto">
      <el-card shadow="never" class="side-card" :body-style="{ padding: '16px' }">
        <template #header>
          <div class="flex items-center justify-between">
            <span class="text-title-sm font-medium">全站热榜</span>
            <RouterLink to="/board/qna" class="text-caption text-link hover:underline">更多</RouterLink>
          </div>
        </template>
        <ol v-if="hotTop5.length">
          <li
            v-for="(p, i) in hotTop5"
            :key="p.id"
            class="-mx-2 flex items-start gap-2 rounded-md border-b-[0.5px] border-divider px-2 py-1.5 transition-colors duration-fast ease-standard last:border-b-0 hover:bg-bg-hover"
          >
            <!-- 前三名徽章分级（CR-102）：第 1 实心主色白字、2/3 浅底主色，其余灰字——层级一眼可读 -->
            <span
              class="inline-flex h-5 w-5 flex-none items-center justify-center rounded-sm text-caption tabular-nums"
              :class="i === 0 ? 'bg-primary-strong font-medium text-white' : i < 3 ? 'bg-primary-soft font-medium text-primary-strong' : 'text-ink-meta'"
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

      <!-- 等你来答（CR-142，N1）：问答帖且未采纳，把「提问 → 回答 → 采纳」循环的供给侧亮出来 -->
      <el-card shadow="never" class="side-card mt-4" :body-style="{ padding: '16px' }">
        <template #header>
          <div class="flex items-center justify-between">
            <span class="text-title-sm font-medium">等你来答</span>
            <RouterLink
              to="/board/qna?filter=unanswered"
              class="text-caption text-link hover:underline"
            >更多</RouterLink>
          </div>
        </template>
        <ol v-if="unansweredTop5.length">
          <li
            v-for="(p, i) in unansweredTop5"
            :key="p.id"
            class="-mx-2 flex items-start gap-2 rounded-md border-b-[0.5px] border-divider px-2 py-1.5 transition-colors duration-fast ease-standard last:border-b-0 hover:bg-bg-hover"
          >
            <span class="inline-flex h-5 w-5 flex-none items-center justify-center rounded-sm text-caption tabular-nums text-ink-meta">
              {{ i + 1 }}
            </span>
            <RouterLink :to="`/post/${p.id}`" class="min-w-0 flex-1 truncate text-note text-ink-regular hover:text-link">
              {{ p.title }}
            </RouterLink>
            <!-- 0 回答用主色：最需要帮助的排进视觉焦点；已有回答未采纳是次优先 -->
            <span
              class="flex-none text-caption tabular-nums"
              :class="p.replyCount === 0 ? 'font-medium text-primary-strong' : 'text-ink-meta'"
            >{{ p.replyCount }} 回答</span>
          </li>
        </ol>
        <p v-else class="text-note text-ink-meta">暂时没有待解答的问题</p>
      </el-card>

      <!-- 最新发布挂件（CR-091，参考 CSDN 右栏滚动接力）：随页面滚动，与热榜形成"滚动后换内容"的节奏 -->
      <el-card shadow="never" class="side-card mt-4" :body-style="{ padding: '16px' }">
        <template #header>
          <div class="flex items-center justify-between">
            <span class="text-title-sm font-medium">最新发布</span>
          </div>
        </template>
        <ol v-if="latestTop5.length">
          <li
            v-for="p in latestTop5"
            :key="p.id"
            class="-mx-2 rounded-md border-b-[0.5px] border-divider px-2 py-1.5 transition-colors duration-fast ease-standard last:border-b-0 hover:bg-bg-hover"
          >
            <RouterLink :to="`/post/${p.id}`" class="block truncate text-note text-ink-regular hover:text-link">
              {{ p.title }}
            </RouterLink>
            <p class="mt-0.5 text-caption text-ink-meta">{{ p.authorNickname }} · {{ formatTime(p.createdAt) }}</p>
          </li>
        </ol>
        <p v-else class="text-note text-ink-meta">还没有帖子</p>
      </el-card>

      <!-- 关于卡：整栏已 sticky 钉住（CR-098），此处回归普通排布 -->
      <div class="mt-4">
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

<style scoped>
/* 排序卡弹性列（CR-100）：卡片在定高中栏里撑满，卡片体（帖子列表）成为唯一滚动容器；
   排序 header 不再 sticky——卡片自身不滚，header 天然固定 */
.sort-card {
  display: flex;
  flex-direction: column;
}
.sort-card :deep(.el-card__body) {
  flex: 1;
  min-height: 0;
}
/* 右栏三张异步卡片（CR-155）：数据回来前先把卡片体撑到成品高度，
   否则「热榜计算中」等占位文本换成 5 条列表时会把下面的卡片顶下去（CLS 0.062） */
.side-card :deep(.el-card__body) {
  min-height: 230px;
}
</style>
