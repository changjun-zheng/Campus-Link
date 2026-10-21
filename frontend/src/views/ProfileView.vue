<script setup lang="ts">
/**
 * 他人主页（`/u/:id`，F-ACC-002 本体最小版）：资料卡 + 公开帖子时间线。
 *
 * <p>三条口径由后端定死，前端只如实呈现：
 * ① <b>匿名可读</b>——两个端点都是公开的，故本页不设 `requiresAuth`；未登录时关注按钮照旧显示，
 *    点击走登录回跳（与帖子详情页的 `requireLoginOrRedirect` 同一口径），而不是把按钮藏起来
 *    ——藏起来等于告诉访客"这里没有可关注的东西"；
 * ② <b>时间线只含 PUBLISHED 且未删除的帖</b>，与「我的帖子」相反（那份要给作者露出被下架的条目）；
 *    出参与全站列表同形，故直接复用 `PostListItem`；
 * ③ <b>不存在与已注销同为 404 / 2007</b>，本页统一渲染「用户不存在」，不给"这个 id 注销过没有"的探针。
 *
 * <p>资料端点匿名，故**不下发 `following`**（那一位需要 viewer）。已登录且不是本人时另调
 * `getFollowState` 补这一位，失败静默——按钮回落「＋ 关注」，点一下由后端幂等兜底（重复关注不报错）。
 *
 * <p>路由参数变化要重新加载：从帖子详情页点作者名跳到本页、再在本页内点另一个作者时组件会被复用，
 * 只靠 `onMounted` 会留在上一个人的资料上。故用 `watch(userId, ..., { immediate: true })` 驱动。
 *
 * <p>异步响应要带序号守卫：从 `/u/1` 快速切到 `/u/2` 时，先发出但后返回的响应会覆盖新数据
 * （资料卡是 A 的、帖子列表是 B 的）。与 PublishView 相似推荐的 reqSeq 同模式：每轮加载领一个
 * 递增序号，响应回来先对序号——不是最新一轮的一律丢弃（含 catch 分支：陈旧请求的报错
 * 不该盖掉新用户页的正常态）。
 */
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getUserPosts, getUserProfile, getUserReplies, type PublicReplyVo, type UserProfileVo } from '../api/user'
import { followUser, getFollowState, unfollowUser } from '../api/follow'
import { ApiError } from '../api/client'
import type { PostSummaryVo } from '../api/forum'
import PostListItem from '../components/PostListItem.vue'
import UserAvatar from '../components/UserAvatar.vue'
import SvgIcon from '../components/SvgIcon.vue'
import { useAuthStore } from '../stores/auth'
import { formatRelativeTime } from '../utils/time'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

/** 路径参数不是正整数即视为不存在（`/u/abc` 与 `/u/0` 都不该打到后端去撞 404） */
const userId = computed(() => {
  const raw = Number(Array.isArray(route.params.id) ? route.params.id[0] : route.params.id)
  return Number.isInteger(raw) && raw > 0 ? raw : 0
})

const isSelf = computed(() => userId.value !== 0 && auth.user?.id === userId.value)

const profile = ref<UserProfileVo | null>(null)
const profileLoading = ref(false)
const profileError = ref('')
/** 用户不存在 / 已注销 / id 非法：整页只渲染这一态，不再发第二个请求 */
const missing = ref(false)

const posts = ref<PostSummaryVo[]>([])
const total = ref(0)
const page = ref(1)
const size = 20
const postsLoading = ref(false)
const postsError = ref('')

const replies = ref<PublicReplyVo[]>([])
const repliesTotal = ref(0)
const repliesLoading = ref(false)
const repliesError = ref('')
/**
 * 双时间线的当前档（CR-104）：PRD F-ACC-002 的验收句写的是「发帖<b>回帖</b>时间线」，
 * 原先只有发帖那一半。默认停在帖子——"这人发过什么"比"这人答过什么"更是主页的第一信号。
 */
const activeTab = ref<'posts' | 'replies'>('posts')
const replyPage = ref(1)
const replySize = 10
/** 楼层那一档按需加载：默认不拉，切过去才拉——多数访客不看楼层，没必要每次进页多打一趟 */
const repliesFetched = ref(false)

const following = ref(false)
const followBusy = ref(false)

/** 请求序号守卫：只有最新一轮加载的响应才能落到 ref（快速切换用户时丢弃陈旧响应） */
let reqSeq = 0

/** 「加入于 2026年09月」：只到月——具体到日在这张卡上没有信息量，反而像在报账龄 */
const joinedText = computed(() => {
  const iso = profile.value?.createdAt
  if (!iso) return ''
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return ''
  return `加入于 ${date.getFullYear()} 年 ${String(date.getMonth() + 1).padStart(2, '0')} 月`
})

/** 专业可能为空（名册未导入 / 本人未填）；空则整段不出现，不留一个孤零零的「·」 */
const metaText = computed(() => {
  const parts = [profile.value?.major, joinedText.value].filter(Boolean)
  return parts.join(' · ')
})

function requireLoginOrRedirect(): boolean {
  if (auth.isLoggedIn) return true
  router.push({ path: '/login', query: { redirect: route.fullPath } })
  return false
}

async function loadProfile(seq: number) {
  profileLoading.value = true
  profileError.value = ''
  try {
    const data = await getUserProfile(userId.value)
    if (seq !== reqSeq) return
    profile.value = data
    missing.value = false
  } catch (e) {
    if (seq !== reqSeq) return
    profile.value = null
    // 2007 = 用户不存在或已注销（两者后端同码，前端也不区分）
    missing.value = e instanceof ApiError && e.code === 2007
    profileError.value = missing.value ? '' : e instanceof Error ? e.message : '资料加载失败'
  } finally {
    if (seq === reqSeq) profileLoading.value = false
  }
}

async function loadPosts(seq: number) {
  postsLoading.value = true
  postsError.value = ''
  try {
    const res = await getUserPosts(userId.value, page.value, size)
    if (seq !== reqSeq) return
    posts.value = res.list
    total.value = res.total
  } catch (e) {
    if (seq !== reqSeq) return
    posts.value = []
    total.value = 0
    postsError.value = e instanceof Error ? e.message : '帖子加载失败'
  } finally {
    if (seq === reqSeq) postsLoading.value = false
  }
}

/**
 * 公开楼层时间线（CR-104）：与帖子档共用同一把 reqSeq 守卫——切用户时两档都要作废。
 * 出参没有 status 位（路人视角下不可见楼层根本不出现），故这里也没有「已下架」标签分支。
 */
async function loadReplies(seq: number) {
  repliesLoading.value = true
  repliesError.value = ''
  try {
    const res = await getUserReplies(userId.value, replyPage.value, replySize)
    if (seq !== reqSeq) return
    replies.value = res.list
    repliesTotal.value = res.total
    repliesFetched.value = true
  } catch (e) {
    if (seq !== reqSeq) return
    replies.value = []
    repliesTotal.value = 0
    repliesError.value = e instanceof Error ? e.message : '回帖加载失败'
  } finally {
    if (seq === reqSeq) repliesLoading.value = false
  }
}

/** 关注状态：只在「已登录 + 不是本人」时查；失败静默（按钮回落未关注态） */
async function loadFollowState(seq: number) {
  following.value = false
  if (!auth.isLoggedIn || isSelf.value) return
  try {
    const state = await getFollowState(userId.value)
    if (seq !== reqSeq) return
    following.value = state.following
  } catch {
    // 静默：关注状态不是本页主体，拉不到不该弹错
  }
}

async function loadAll() {
  const seq = ++reqSeq
  posts.value = []
  total.value = 0
  page.value = 1
  // 换用户 = 两档一起作废：不复位的话，在 /u/1 拉过的楼层会带着上一个人的数据停在 /u/2 的缓存里
  replies.value = []
  repliesTotal.value = 0
  replyPage.value = 1
  repliesFetched.value = false
  activeTab.value = 'posts'
  if (userId.value === 0) {
    profile.value = null
    missing.value = true
    return
  }
  await loadProfile(seq)
  // 陈旧一轮不再继续：missing 可能已被新用户轮改写，照它行事会错发 / 漏发帖子请求
  if (seq !== reqSeq) return
  if (missing.value) return
  void loadPosts(seq)
  void loadFollowState(seq)
}

function onPageChange(next: number) {
  page.value = next
  void loadPosts(++reqSeq)
}

function onReplyPageChange(next: number) {
  replyPage.value = next
  void loadReplies(++reqSeq)
}

/** 切档才发第一趟请求；已拉过则复用（翻页走 onReplyPageChange 自己领序号） */
function switchTab(next: 'posts' | 'replies') {
  activeTab.value = next
  if (next === 'replies' && !repliesFetched.value && !repliesLoading.value) void loadReplies(++reqSeq)
}

/** 重试也要领新序号：否则重试轮自己就成了“陈旧轮”，响应回来被自己丢弃 */
function retryProfile() {
  void loadProfile(++reqSeq)
}

function retryPosts() {
  void loadPosts(++reqSeq)
}

function retryReplies() {
  void loadReplies(++reqSeq)
}

/** 楼层条目的父帖链接（回帖档用）：与「我的回帖」页同一路由口径 */
function linkToPost(postId: number) {
  return '/post/' + postId
}

async function toggleFollow() {
  if (!requireLoginOrRedirect()) return
  followBusy.value = true
  try {
    if (following.value) {
      await unfollowUser(userId.value)
      following.value = false
    } else {
      await followUser(userId.value)
      following.value = true
    }
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '关注操作失败')
  } finally {
    followBusy.value = false
  }
}

watch(userId, () => void loadAll(), { immediate: true })
</script>

<template>
  <!-- TRANSITION-SINGLE-ROOT：App.vue 以 <Transition mode="out-in"> 包路由组件，多根组件（fragment）在该模式下会随机卡死路由切换（CR-091 修复详情页同类问题） -->
  <div>
  <!-- 用户不存在 / 已注销 / id 非法：整页只有这一态 -->
  <el-card v-if="missing && !profileLoading" shadow="never">
    <div class="py-10 text-center">
      <p class="text-title-sm font-medium text-ink">用户不存在</p>
      <p class="mt-1.5 text-note text-ink-meta">该主页可能已被注销，或链接有误</p>
      <RouterLink to="/">
        <el-button type="primary" class="mt-4">回首页</el-button>
      </RouterLink>
    </div>
  </el-card>

  <template v-else>
    <!-- 资料卡 -->
    <el-card shadow="never" class="mb-5">
      <el-skeleton v-if="profileLoading" animated>
        <template #template>
          <div class="flex gap-4">
            <el-skeleton-item variant="circle" style="width: 64px; height: 64px" />
            <div class="min-w-0 flex-1">
              <el-skeleton-item variant="text" style="width: 32%" />
              <el-skeleton-item variant="text" class="mt-2" style="width: 54%" />
              <el-skeleton-item variant="text" class="mt-3" style="width: 78%" />
            </div>
          </div>
        </template>
      </el-skeleton>

      <div v-else-if="profileError" class="py-4">
        <el-alert type="error" :closable="false" show-icon :title="profileError">
          <el-button size="small" @click="retryProfile">重新加载</el-button>
        </el-alert>
      </div>

      <div v-else-if="profile" class="flex items-start gap-4">
        <UserAvatar :name="profile.nickname" :size="64" class="flex-none" />
        <div class="min-w-0 flex-1">
          <div class="flex flex-wrap items-center gap-2">
            <h2 class="min-w-0 truncate text-title-lg font-medium text-ink">{{ profile.nickname }}</h2>
            <!-- 本人：给编辑与收藏入口而不是关注按钮（后端 2009 禁止关注自己）。
                 「我的收藏」随 CR-104 / D-3 补在这里——PRD 那句「本人主页额外可见我的收藏」走
                 "链到既有 /favorites 页"而不是在本页再嵌一份列表：收藏项的可见性口径与本页相反
                 （被下架的条目本人仍要看得见），嵌进来等于把两套口径塞进同一张列表 -->
            <template v-if="isSelf">
              <RouterLink to="/settings">
                <el-button size="small">编辑资料</el-button>
              </RouterLink>
              <RouterLink to="/favorites">
                <el-button size="small">我的收藏</el-button>
              </RouterLink>
            </template>
            <el-button
              v-else
              size="small"
              :type="following ? 'default' : 'primary'"
              :loading="followBusy"
              @click="toggleFollow"
            >
              {{ following ? '已关注' : '＋ 关注' }}
            </el-button>
          </div>

          <p v-if="metaText" class="mt-1 text-note text-ink-meta">{{ metaText }}</p>
          <p v-if="profile.bio" class="mt-2 text-body leading-body text-ink-regular">{{ profile.bio }}</p>

          <div class="mt-3 flex flex-wrap items-center gap-4 text-note text-ink-meta">
            <span>
              <span class="text-title-sm font-medium text-ink tabular-nums">{{ profile.postCount }}</span>
              帖子
            </span>
            <span>
              <span class="text-title-sm font-medium text-ink tabular-nums">{{ profile.likeCount }}</span>
              获赞
            </span>
            <span>
              <span class="text-title-sm font-medium text-ink tabular-nums">{{ profile.acceptedCount }}</span>
              被采纳
            </span>
            <span>
              <span class="text-title-sm font-medium text-ink tabular-nums">{{ profile.followerCount }}</span>
              粉丝
            </span>
            <span>
              <span class="text-title-sm font-medium text-ink tabular-nums">{{ profile.followingCount }}</span>
              关注
            </span>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 双时间线（CR-104）：帖子 / 回帖。Tab 沿用首页 CR-091 的文字型自有 Tab
         （主色 + 底部指示条，真 button 保键盘可达与 aria-pressed），不复用 EP 组件 -->
    <el-card shadow="never" :body-style="{ padding: 0 }">
      <div class="flex items-center justify-between border-b-[0.5px] border-divider px-5 py-3">
        <nav class="flex items-center gap-1" aria-label="TA 的内容">
          <button
            type="button"
            class="relative cursor-pointer border-0 bg-transparent px-3 py-1.5 text-body transition-colors duration-fast ease-standard"
            :class="activeTab === 'posts' ? 'font-medium text-primary' : 'text-ink-regular hover:text-link'"
            :aria-pressed="activeTab === 'posts'"
            @click="switchTab('posts')"
          >
            帖子
            <span
              aria-hidden="true"
              class="inset-x-3 -bottom-px absolute h-0.5 rounded-full transition-opacity duration-fast ease-standard"
              :class="activeTab === 'posts' ? 'bg-primary opacity-100' : 'opacity-0'"
            />
          </button>
          <button
            type="button"
            class="relative cursor-pointer border-0 bg-transparent px-3 py-1.5 text-body transition-colors duration-fast ease-standard"
            :class="activeTab === 'replies' ? 'font-medium text-primary' : 'text-ink-regular hover:text-link'"
            :aria-pressed="activeTab === 'replies'"
            @click="switchTab('replies')"
          >
            回帖
            <span
              aria-hidden="true"
              class="inset-x-3 -bottom-px absolute h-0.5 rounded-full transition-opacity duration-fast ease-standard"
              :class="activeTab === 'replies' ? 'bg-primary opacity-100' : 'opacity-0'"
            />
          </button>
        </nav>
        <span class="text-caption text-ink-meta tabular-nums">
          按时间倒序 · 共 {{ activeTab === 'posts' ? total : repliesTotal }} 条
        </span>
      </div>

      <template v-if="activeTab === 'posts'">
      <el-skeleton v-if="postsLoading" animated class="px-5 pt-4">
        <template #template>
          <div
            v-for="i in 4"
            :key="i"
            class="border-b-[0.5px] border-divider py-3 last:border-b-0"
          >
            <el-skeleton-item variant="text" style="width: 46%" />
            <el-skeleton-item variant="text" class="mt-2" style="width: 100%" />
            <el-skeleton-item variant="text" class="mt-1" style="width: 72%" />
          </div>
        </template>
      </el-skeleton>

      <div v-else-if="postsError" class="p-5">
        <el-alert type="error" :closable="false" show-icon :title="postsError">
          <el-button size="small" @click="retryPosts">重新加载</el-button>
        </el-alert>
      </div>

      <div v-else-if="!posts.length" class="py-10 text-center">
        <p class="text-body text-ink-regular">
          {{ isSelf ? '你还没有发过帖子' : 'TA 还没有发过帖子' }}
        </p>
        <RouterLink v-if="isSelf" to="/publish">
          <el-button type="primary" class="mt-4">去发帖</el-button>
        </RouterLink>
        <RouterLink v-else to="/">
          <el-button type="primary" class="mt-4">去逛逛版块</el-button>
        </RouterLink>
      </div>

      <template v-else>
        <ul>
          <PostListItem v-for="p in posts" :key="p.id" :post="p" show-board />
        </ul>
        <div v-if="total > size" class="flex justify-center px-5 py-4">
          <el-pagination
            layout="prev, pager, next, total"
            :total="total"
            :page-size="size"
            :current-page="page"
            @current-change="onPageChange"
          />
        </div>
      </template>
      </template>

      <!-- 回帖档（CR-104）：条目形状取自「我的回帖」页，唯一差别是这里没有「已下架」标签
           （后端出参就没有 status 位，路人视角下被处置的楼层在 SQL 侧已整条滤掉） -->
      <template v-else>
        <el-skeleton v-if="repliesLoading" animated class="px-5 pt-4">
          <template #template>
            <div v-for="i in 3" :key="i" class="border-b-[0.5px] border-divider py-3 last:border-b-0">
              <el-skeleton-item variant="text" style="width: 38%" />
              <el-skeleton-item variant="text" class="mt-2" style="width: 100%" />
              <el-skeleton-item variant="text" class="mt-1" style="width: 64%" />
            </div>
          </template>
        </el-skeleton>

        <div v-else-if="repliesError" class="p-5">
          <el-alert type="error" :closable="false" show-icon :title="repliesError">
            <el-button size="small" @click="retryReplies">重新加载</el-button>
          </el-alert>
        </div>

        <div v-else-if="!replies.length" class="py-10 text-center">
          <p class="text-body text-ink-regular">
            {{ isSelf ? '你还没有回过帖' : 'TA 还没有回过帖' }}
          </p>
          <RouterLink to="/">
            <el-button type="primary" class="mt-4">{{ isSelf ? '去回答问题' : '去逛逛版块' }}</el-button>
          </RouterLink>
        </div>

        <template v-else>
          <ul>
            <li v-for="r in replies" :key="r.id" class="border-b-[0.5px] border-divider px-5 py-3 last:border-b-0">
              <div class="flex items-center gap-2">
                <span class="flex-none text-caption text-ink-meta tabular-nums">第 {{ r.floorNo }} 楼</span>
                <RouterLink
                  :to="linkToPost(r.postId)"
                  class="min-w-0 flex-1 truncate text-body text-ink hover:text-link"
                >
                  {{ r.postTitle }}
                </RouterLink>
              </div>
              <p v-if="r.summary" class="mt-1 line-clamp-2 text-note leading-body text-ink-regular">
                {{ r.summary }}
              </p>
              <p class="mt-1.5 text-caption text-ink-meta">{{ formatRelativeTime(r.createdAt) }}</p>
            </li>
          </ul>
          <div v-if="repliesTotal > replySize" class="flex justify-center px-5 py-4">
            <el-pagination
              layout="prev, pager, next, total"
              :total="repliesTotal"
              :page-size="replySize"
              :current-page="replyPage"
              @current-change="onReplyPageChange"
            />
          </div>
        </template>
      </template>
    </el-card>

    <div class="mt-4 flex justify-center">
      <RouterLink to="/" class="flex items-center gap-1 text-note text-link hover:underline">
        <SvgIcon name="arrow-left" :size="14" />
        回首页
      </RouterLink>
    </div>
  </template>
  </div>
</template>
