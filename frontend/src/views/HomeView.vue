<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { listFollowingPosts, listPosts, type PostSort, type PostSummaryVo } from '../api/forum'
import { BOARDS } from '../constants/boards'
import { useAuthStore } from '../stores/auth'
import PostListItem from '../components/PostListItem.vue'
import SvgIcon from '../components/SvgIcon.vue'
import type { IconName } from '../assets/icons'

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
const sortNavRef = ref<HTMLElement | null>(null)
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
  // 窄屏（<lg）没有固定高度，列表容器与内容等高、自身不可滚：触底要按窗口滚动算，
  // 否则 distance 恒为 0，首屏会一口气把所有分页拉完（CR-174 实测拉 4 页 / 24 条）。
  const containerScrollable = el.scrollHeight > el.clientHeight + 1
  const distance = containerScrollable
    ? el.scrollHeight - el.scrollTop - el.clientHeight
    : document.documentElement.scrollHeight - window.scrollY - window.innerHeight
  if (distance > 320 || (distance > 0 && !userScrolled)) return
  void loadMore()
}

/** 窄屏整页滚动时驱动触底加载（容器可滚时由 onListScroll 负责，本监听自动跳过） */
function onWindowScroll() {
  const el = listWrapRef.value
  if (!el || el.scrollHeight > el.clientHeight + 1) return
  userScrolled = true
  maybeLoadMore()
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

/** 热榜「更多」展示全站热门，不再误跳到按最新排序的技术问答版块。 */
async function showHotPosts() {
  sort.value = 'hot'
  await nextTick()
  listWrapRef.value?.scrollTo({ top: 0 })
  sortNavRef.value?.querySelector<HTMLButtonElement>('[aria-pressed="true"]')?.focus()
}

watch(sort, () => {
  posts.value = []
  total.value = 0
  page.value = 1
  moreError.value = false
  void loadFirst()
})

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
  loadUnanswered()
  window.addEventListener('scroll', onWindowScroll, { passive: true })
})

onBeforeUnmount(() => window.removeEventListener('scroll', onWindowScroll))

const myEntries: { label: string; to: string; icon: IconName }[] = [
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
    <aside class="hidden w-[184px] flex-none lg:block lg:overflow-y-auto">
      <!-- CR-201 批次 A：侧栏不再套白卡，改透明分组导航——白卡只留给中栏，页面才有单一焦点 -->
      <nav aria-label="版块导航" class="py-1">
        <p class="px-3 pb-2 text-caption font-medium text-ink-meta">版块</p>
        <RouterLink
          v-for="b in BOARDS"
          :key="b.code"
          :to="`/board/${b.code}`"
          class="group flex items-center gap-2.5 rounded-md px-3 py-2 text-body text-ink-regular transition-colors duration-fast ease-standard hover:bg-bg-subtle"
          active-class="bg-bg-subtle font-medium text-primary-strong"
        >
          <SvgIcon :name="b.icon" :size="20" :class="['flex-none', `cl-board-ink--${b.code}`]" />
          {{ b.name }}
        </RouterLink>
      </nav>
      <nav aria-label="我的入口" class="mt-2 border-t-[0.5px] border-divider pt-3">
        <p class="px-3 pb-2 text-caption font-medium text-ink-meta">我的</p>
        <RouterLink
          v-for="m in myEntries"
          :key="m.to"
          :to="m.to"
          class="group flex items-center gap-2.5 rounded-md px-3 py-2 text-body text-ink-regular transition-colors duration-fast ease-standard hover:bg-bg-subtle"
          active-class="bg-bg-subtle font-medium text-primary-strong"
        >
          <SvgIcon
            :name="m.icon"
            :size="20"
            class="flex-none text-ink-meta transition-colors group-hover:text-primary-strong"
          />
          {{ m.label }}
        </RouterLink>
      </nav>
    </aside>

    <!-- ─── 中栏：信息流（定高列，帖子列表在卡片体内自滚） ─── -->
    <main class="relative flex min-w-0 flex-1 flex-col">
      <el-card shadow="never" class="sort-card min-h-0 flex-1" :body-style="{ padding: 0 }">
        <template #header>
          <!-- 排序 Tab（CR-091）：文字型自有 Tab，选中态主色 + 底部指示条（参考站分类 Tab 风格）；
               真按钮保留键盘可达与 aria-pressed。不在卡头展示总条数——万级数据没有展示价值 -->
          <nav ref="sortNavRef" class="flex items-center gap-1" aria-label="帖子排序">
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
            <SvgIcon name="messages" :size="24" />
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
              <span v-else-if="!hasMore && posts.length">已经到底了</span>
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

    <!-- ─── 右栏：等你来答 + 热榜 + 站点卡（xl 以上显示，栏内自滚） ─── -->
    <aside class="hidden w-[264px] flex-none xl:block xl:overflow-y-auto">
      <!-- CR-201 批次 A：右栏去卡后统一成「小标签 + 内容」的模块语法——
           三块共用同一套 12px 分组标签（图标 + 名称 + 右侧动作），模块之间留 24px + 分隔线，
           让右栏读起来是一列索引，而不是三段互相竞争的标题。
           等你来答（CR-142，N1）：问答帖且未采纳，把「提问 → 回答 → 采纳」循环的供给侧亮出来 -->
      <section class="pt-1">
        <header class="mb-2.5 flex items-center justify-between">
          <span class="inline-flex items-center gap-1.5 text-note font-semibold text-ink">
            <SvgIcon name="message-circle" :size="14" class="text-primary-strong" />
            等你来答
          </span>
          <RouterLink
            to="/board/qna?filter=unanswered"
            class="text-caption text-link hover:underline"
          >更多</RouterLink>
        </header>
        <ol v-if="unansweredTop5.length" class="min-h-[190px]">
          <li
            v-for="(p, i) in unansweredTop5"
            :key="p.id"
            class="flex items-start gap-2 rounded-md py-1.5 transition-colors duration-fast ease-standard hover:bg-bg-subtle"
          >
            <span class="w-3.5 flex-none pt-0.5 text-caption tabular-nums text-ink-meta">
              {{ i + 1 }}
            </span>
            <RouterLink :to="`/post/${p.id}`" class="min-w-0 flex-1 line-clamp-2 break-words text-note leading-tight text-ink-regular hover:text-link">
              {{ p.title }}
            </RouterLink>
            <!-- 0 回答用主色：最需要帮助的排进视觉焦点；已有回答未采纳是次优先 -->
            <span
              class="flex-none text-caption tabular-nums"
              :class="p.replyCount === 0 ? 'font-medium text-primary-strong' : 'text-ink-meta'"
            >{{ p.replyCount }} 回答</span>
          </li>
        </ol>
        <p v-else class="min-h-[190px] text-note text-ink-meta">暂时没有待解答的问题</p>
      </section>

      <section class="mt-6 border-t-[0.5px] border-line pt-5">
        <header class="mb-2.5 flex items-center justify-between">
          <span class="inline-flex items-center gap-1.5 text-note font-semibold text-ink">
            <SvgIcon name="trophy" :size="14" class="text-rank-gold" />
            全站热榜
          </span>
          <button type="button" class="cursor-pointer text-caption text-link hover:underline" aria-label="更多全站热榜" @click="showHotPosts">更多</button>
        </header>
        <ol v-if="hotTop5.length" class="min-h-[190px]">
          <li
            v-for="(p, i) in hotTop5"
            :key="p.id"
            class="flex items-start gap-2 rounded-md py-1.5 transition-colors duration-fast ease-standard hover:bg-bg-subtle"
          >
            <!-- 前三名奖牌徽章（2026-11-05）：只保留奖杯 / 奖牌自身的轮廓（不铺圆形底色），
                 名次数字按各自图形的空腔落位——皇冠落在冠身、奖牌落在盘面、绶带落在圆章；
                 颜色只表达金 / 银 / 铜档，不用实心底纹把图标碾成水印 -->
            <span
              v-if="i < 3"
              role="img"
              class="relative inline-flex h-6 w-6 flex-none"
              :class="i === 0 ? 'text-rank-gold' : i === 1 ? 'text-rank-silver' : 'text-rank-bronze'"
              :aria-label="`第 ${i + 1} 名`"
            >
              <SvgIcon
                :name="i === 0 ? 'crown' : i === 1 ? 'medal' : 'award'"
                :size="24"
                class="absolute inset-0"
              />
              <span
                class="absolute left-1/2 -translate-x-1/2 -translate-y-1/2 text-[10px] font-semibold leading-none tabular-nums"
                :class="i === 0 ? 'top-[64%]' : i === 1 ? 'top-[62.5%]' : 'top-[37.5%]'"
              >{{ i + 1 }}</span>
            </span>
            <span
              v-else
              class="inline-flex h-6 w-6 flex-none items-center justify-center text-caption tabular-nums text-ink-meta"
            >
              {{ i + 1 }}
            </span>
            <RouterLink :to="`/post/${p.id}`" class="min-w-0 flex-1 line-clamp-2 break-words text-note leading-tight text-ink-regular hover:text-link">
              {{ p.title }}
            </RouterLink>
          </li>
        </ol>
        <p v-else class="min-h-[190px] text-note text-ink-meta">热榜计算中，稍后再来看</p>
      </section>

      <!-- 关于区（CR-201 批次 A）：从「标题 + 正文 + 实心按钮」降为注脚——
           它与上方两个模块不是同级内容，实心按钮在侧栏里还会抢走中栏的视觉重量 -->
      <section class="mt-6 border-t-[0.5px] border-line pt-5">
        <p class="text-note font-semibold text-ink">关于 Campus-Link</p>
        <p class="mt-1.5 text-caption leading-body text-ink-meta">
          重庆工程学院计算机专业学生的垂直交流论坛——问有所答、学有同伴、求职有路。
        </p>
        <RouterLink to="/publish" class="mt-2 inline-block text-caption text-primary-strong hover:underline">
          我要提问 →
        </RouterLink>
      </section>
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

/* 卡片头层（CR-201 批次 A）：排序条恢复白底，只靠 EP 自带的 0.5px 下边框分界。
   之前的主色淡底把整条卡头刷成浅蓝，反而削弱了中栏白卡的独立性 */
.sort-card :deep(.el-card__body) {
  flex: 1;
  min-height: 0;
}
</style>
