<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  getPost,
  listReplies,
  publishReply,
  acceptReply,
  togglePostLike,
  togglePostFavorite,
  toggleReplyLike,
  deletePost,
  type PostDetailVo,
  type ReplyVo,
} from '../api/forum'
import { followUser, getFollowState, unfollowUser } from '../api/follow'
import { submitReport } from '../api/admin'
import { ApiError } from '../api/client'
import SvgIcon from '../components/SvgIcon.vue'
import TableOfContents from '../components/TableOfContents.vue'
import { useAuthStore } from '../stores/auth'
import { useNarrowScreen } from '../composables/useNarrowScreen'
import { formatTime } from '../utils/time'
import { vHighlight } from '../utils/highlight'
import { ensureHeadingIds } from '../utils/heading'
import { injectCopyButtons } from '../utils/copyCode'
import UserAvatar from '../components/UserAvatar.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const narrow = useNarrowScreen()
const postId = computed(() => String(route.params.id || ''))
const post = ref<PostDetailVo | null>(null)
const missing = ref(false)
const postError = ref('')
const replies = ref<ReplyVo[]>([])
const total = ref(0)
const page = ref(1)
const size = 20
const repliesLoading = ref(false)
const repliesError = ref('')
const replyMd = ref('')
const submitting = ref(false)
const acceptingReplyId = ref<number | null>(null)
const togglingLike = ref(false)
const togglingFavorite = ref(false)
const likingReplyId = ref<number | null>(null)
/** 引用回复（F-009 / CR-085）：当前引用目标；null = 未在引用态。切换帖子时随 watch 重置 */
const quoting = ref<{ replyId: number; floorNo: number; authorNickname: string } | null>(null)
/** 从通知跳入时被点名的楼层 id（?floor=），命中则滚动 + 高亮 */
const focusedFloorId = ref('')

/** 帖子正文容器（v-html 宿主）—— 目录 id 补写与代码块按钮注入都从这里下手 */
const postBodyRef = ref<HTMLElement | null>(null)

/** 回复列表容器（`<ul>`）—— 回复楼层里的代码块同样需要复制按钮 */
const repliesListRef = ref<HTMLElement | null>(null)

/**
 * v-html 落定后：① 给 h1~h3 补 id，② 给 pre 注入复制按钮。
 * highlight 指令异步给 code 上色，不动 pre / heading，因此这里与其并发即可。
 */
async function enhancePostBody() {
  await nextTick()
  const el = postBodyRef.value
  if (!el) return
  ensureHeadingIds(el)
  injectCopyButtons(el)
}

/**
 * 内容变化（切换帖子 / 采纳后重拉）都要重跑一遍：v-html 会替换整棵子树，
 * 旧的按钮与 id 随子树被丢弃，需要基于新 DOM 重新注入。
 */
watch(
  () => post.value?.contentHtml,
  (html) => {
    if (html) void enhancePostBody()
  },
)

/**
 * 回复区代码块同样要能复制：`injectCopyButtons` 本身是幂等的（按 `.cl-pre-wrap`
 * 包裹判定），直接对整个 `<ul>` 调一次即可覆盖所有楼层，无需逐条处理。
 * 只在 `replies` 整体被重新赋值（加载 / 翻页 / 采纳后重拉）时触发，点赞等
 * 就地修改字段不会改变 v-html 内容，无需重跑。
 */
async function enhanceReplies() {
  await nextTick()
  injectCopyButtons(repliesListRef.value)
}

watch(replies, () => {
  if (replies.value.length) void enhanceReplies()
})

// 未登录点击互动按钮 → 登录后回跳本页（与回帖同一口径）
function requireLoginOrRedirect(): boolean {
  if (auth.isLoggedIn) return true
  router.push({ path: '/login', query: { redirect: route.fullPath } })
  return false
}

async function toggleLike() {
  if (!post.value || !requireLoginOrRedirect()) return
  togglingLike.value = true
  try {
    const res = await togglePostLike(post.value.id)
    post.value.likeCount = res.count
    post.value.likedByMe = res.active
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '点赞失败')
  } finally {
    togglingLike.value = false
  }
}

async function toggleFavorite() {
  if (!post.value || !requireLoginOrRedirect()) return
  togglingFavorite.value = true
  try {
    const res = await togglePostFavorite(post.value.id)
    post.value.favoritedByMe = res.active
    ElMessage.success(res.active ? '已收藏，可在「我的收藏」查看' : '已取消收藏')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '收藏操作失败')
  } finally {
    togglingFavorite.value = false
  }
}

async function likeReply(r: ReplyVo) {
  if (!post.value || !requireLoginOrRedirect()) return
  likingReplyId.value = r.id
  try {
    const res = await toggleReplyLike(post.value.id, r.id)
    r.likeCount = res.count
    r.likedByMe = res.active
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '点赞失败')
  } finally {
    likingReplyId.value = null
  }
}

// 采纳按钮可见性（F-QA-001）：仅提问者 + 问答帖；自己楼层不显示（后端 3003 兜底）
const isAskerOfQuestion = computed(
  () => !!post.value && post.value.boardType === 'QUESTION' && auth.user?.id === post.value.authorId,
)

// 删除入口（F-FORUM-006 前端一半，CR-072）：仅作者可见；后端仍以 403 兜底非作者
const isAuthor = computed(() => !!post.value && auth.user?.id === post.value.authorId)
const deleting = ref(false)

// 关注（CR-074 / F-SOC-002）：非作者且已登录时可见；状态随帖子加载拉取，切换失败静默（详情页非关注管理页）
const following = ref(false)

async function toggleFollow() {
  if (!post.value || !requireLoginOrRedirect()) return
  const targetId = post.value.authorId
  if (targetId === null) return
  try {
    if (following.value) {
      await unfollowUser(targetId)
      following.value = false
    } else {
      await followUser(targetId)
      following.value = true
    }
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '关注操作失败')
  }
}

async function onDelete() {
  if (!post.value) return
  const boardCode = post.value.boardCode
  try {
    await ElMessageBox.confirm('删除后前台将不可见，且无法恢复。确认删除该帖子？', '删除帖子', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  deleting.value = true
  try {
    await deletePost(post.value.id)
    ElMessage.success('帖子已删除')
    router.push(`/board/${boardCode}`)
  } catch (e) {
    // 幂等语义（CR-065）：已被删除（404 / 3001）也按"已删除"收尾，不留死链
    if (e instanceof ApiError && e.code === 3001) {
      ElMessage.success('帖子已删除')
      router.push(`/board/${boardCode}`)
    } else {
      ElMessage.error(e instanceof Error ? e.message : '删除失败')
    }
  } finally {
    deleting.value = false
  }
}

function canAccept(r: ReplyVo): boolean {
  return isAskerOfQuestion.value && !r.accepted && auth.user?.id !== r.authorId
}

async function adopt(r: ReplyVo) {
  if (!post.value) return
  const tip = post.value.accepted
    ? `更换采纳为 #${r.floorNo} 楼？原最佳答案将被替换`
    : `采纳 #${r.floorNo} 楼为最佳答案？`
  try {
    await ElMessageBox.confirm(tip, '采纳最佳答案', { confirmButtonText: '采纳', cancelButtonText: '取消' })
  } catch {
    return
  }
  acceptingReplyId.value = r.id
  try {
    await acceptReply(post.value.id, r.id)
    ElMessage.success('已采纳为最佳答案')
    await Promise.all([loadPost(), loadReplies()])
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '采纳失败')
  } finally {
    acceptingReplyId.value = null
  }
}

async function loadPost() {
  missing.value = false
  postError.value = ''
  post.value = null
  try {
    post.value = await getPost(postId.value)
    // 关注状态随帖拉取（CR-074）：非作者 + 已登录才查；失败静默（按钮回落「＋ 关注」）
    following.value = false
    // 匿名帖对非作者 authorId 为 null（CR-139）：没有可关注的目标，也不该去猜
    if (post.value.authorId !== null && auth.isLoggedIn && auth.user?.id !== post.value.authorId) {
      getFollowState(post.value.authorId)
        .then((s) => (following.value = s.following))
        .catch(() => {})
    }
  } catch (e) {
    // 3001 = 帖子不存在 / 已删除 / 未发布（设计 §3.4），单独给「不存在」态而非通用报错
    if (e instanceof ApiError && e.code === 3001) {
      missing.value = true
    } else {
      postError.value = e instanceof Error ? e.message : '帖子加载失败'
    }
  }
}

async function loadReplies() {
  repliesLoading.value = true
  repliesError.value = ''
  try {
    const res = await listReplies(postId.value, page.value, size)
    replies.value = res.list
    total.value = res.total
  } catch (e) {
    replies.value = []
    total.value = 0
    // 帖子本身不存在（3001）时由 loadPost 渲染整页「不存在」态；两个请求并发，此处可能先于 loadPost 失败，
    // 只按错误码判断才能保证不重复报错
    if (!(e instanceof ApiError && e.code === 3001)) {
      repliesError.value = e instanceof Error ? e.message : '楼层加载失败'
    }
  } finally {
    repliesLoading.value = false
  }
}

function onPageChange(next: number) {
  page.value = next
  loadReplies()
}

async function submitReply() {
  if (!auth.isLoggedIn) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  const contentMd = replyMd.value.trim()
  if (!contentMd) {
    ElMessage.warning('回复内容不能为空')
    return
  }
  submitting.value = true
  try {
    const res = await publishReply(postId.value, contentMd, quoting.value?.replyId)
    ElMessage.success(`回复成功，你在 ${res.floorNo} 楼`)
    replyMd.value = ''
    quoting.value = null
    page.value = Math.max(1, Math.ceil(res.floorNo / size))
    await Promise.all([loadReplies(), loadPost()])
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '回复失败')
  } finally {
    submitting.value = false
  }
}

/**
 * 楼层「引用」（F-009 / CR-085）：记录引用目标并滚到 composer。重复点击即换引用对象（只引用最后一次），
 * 取消走 cancelQuote；提交后清空，引用块由楼层列表刷新时按服务端数据渲染。
 */
function quoteReply(r: ReplyVo) {
  if (!requireLoginOrRedirect()) return
  quoting.value = { replyId: r.id, floorNo: r.floorNo, authorNickname: r.authorNickname }
  document.getElementById('replyComposer')?.scrollIntoView({ block: 'center' })
}

function cancelQuote() {
  quoting.value = null
}

/**
 * 通知跳转定位（F-SOC-001）：`?floor=<replyId>` 命中当前页楼层时滚动并高亮。
 * 楼层不在本页时不猜页码——最佳答案置顶会改变分页组成，猜错反而跳错楼层，落到帖子即可。
 */
async function focusFloorFromQuery() {
  const raw = String(route.query.floor ?? '')
  if (!raw) {
    return
  }
  await nextTick()
  const el = document.getElementById(`floor-${raw}`)
  if (!el) {
    return
  }
  focusedFloorId.value = raw
  el.scrollIntoView({ block: 'center' })
}

/* ─── 举报（F-SAFE-002 / CR-084）：帖子与楼层入口，对话框收集理由与补充说明 ─── */
const reportDialogVisible = ref(false)
const reportTarget = ref<{ type: 'POST' | 'REPLY'; id: number } | null>(null)
const reportForm = reactive({ reason: 'SPAM', detail: '' })
const REPORT_REASONS = [
  { value: 'ILLEGAL', label: '违法违规' },
  { value: 'HARASS', label: '骚扰广告' },
  { value: 'COPYRIGHT', label: '侵权' },
  { value: 'SPAM', label: '低质灌水' },
  { value: 'OTHER', label: '其他' },
]

function openReport(type: 'POST' | 'REPLY', id: number) {
  if (!requireLoginOrRedirect()) return
  reportTarget.value = { type, id }
  reportForm.reason = 'SPAM'
  reportForm.detail = ''
  reportDialogVisible.value = true
}

const reporting = ref(false)

async function doReport() {
  const target = reportTarget.value
  if (!target) return
  reporting.value = true
  try {
    await submitReport({
      targetType: target.type,
      targetId: target.id,
      reason: reportForm.reason,
      detail: reportForm.detail.trim() || undefined,
    })
    ElMessage.success('举报已提交，运营将尽快处理')
    reportDialogVisible.value = false
    // 举报后内容对本人即时不可见（F-SAFE-002 / CR-140）：帖子重载进 3001 空态，楼层重载后该层消失
    if (target.type === 'POST') {
      await loadPost()
    } else {
      await loadReplies()
    }
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '举报提交失败')
  } finally {
    reporting.value = false
  }
}

watch(
  postId,
  () => {
    page.value = 1
    replyMd.value = ''
    quoting.value = null
    focusedFloorId.value = ''
    loadPost()
    loadReplies().then(focusFloorFromQuery)
  },
  { immediate: true },
)
</script>

<template>
  <div>
    <el-result v-if="missing" icon="warning" title="帖子不存在或已删除" sub-title="它可能已被作者删除，或链接有误">
      <template #extra>
        <RouterLink to="/">
          <el-button type="primary">回到首页</el-button>
        </RouterLink>
      </template>
    </el-result>

    <el-alert
      v-else-if="postError"
      type="error"
      :closable="false"
      show-icon
      :title="postError"
    >
      <el-button size="small" @click="loadPost">重新加载</el-button>
    </el-alert>

    <template v-else-if="post">
      <div class="cl-post-shell">
        <div class="cl-post-main">
      <el-card shadow="never" class="mb-5">
        <!-- 标题与正文同处 760px 阅读列（ui-guideline §6），否则标题贴卡片边、正文内缩，左缘不齐 -->
        <div class="mx-auto max-w-reading">
          <div class="flex items-center gap-2">
            <RouterLink :to="`/board/${post.boardCode}`">
              <el-tag type="primary" effect="light" size="small">{{ post.boardName }}</el-tag>
            </RouterLink>
            <el-tag v-if="post.accepted" type="success" effect="light" size="small">已采纳</el-tag>
            <el-tag v-for="t in post.tags" :key="t" size="small" effect="plain">{{ t }}</el-tag>
          </div>
          <h1 class="my-2.5 text-h1 font-medium leading-snug text-ink">{{ post.title }}</h1>
          <div class="flex flex-wrap items-center gap-1.5 text-caption text-ink-meta">
            <!-- 作者名可点（F-ACC-002）：头像与昵称同在一个 RouterLink 里，保留 href 供中键新开 / 键盘聚焦。
                 匿名帖对非作者 authorId 为 null（CR-139）：不给链接，只显示「匿名用户」 -->
            <RouterLink
              v-if="post.authorId !== null"
              :to="`/u/${post.authorId}`"
              class="inline-flex items-center gap-1.5 hover:text-link hover:underline"
            >
              <UserAvatar :name="post.authorNickname" :size="20" />
              <span>{{ post.authorNickname }}</span>
            </RouterLink>
            <span v-else class="inline-flex items-center gap-1.5">
              <UserAvatar :name="post.authorNickname" :size="20" />
              <span>{{ post.authorNickname }}</span>
            </span>
            <el-tag v-if="post.anonymous" size="small" effect="plain">匿名发布</el-tag>
            <span v-if="post.anonymous && auth.user?.id === post.authorId" class="text-ink-regular">（你）</span>
            <span>·</span>
            <span>发布于 {{ formatTime(post.createdAt) }}</span>
            <span>·</span>
            <span>{{ post.replyCount }} 回复</span>
            <span>·</span>
            <span class="inline-flex items-center gap-0.5" title="阅读数（同一账号每天只计一次）">
              <SvgIcon name="eye" :size="12" />
              {{ post.viewCount }}
            </span>
            <el-button
              v-if="post.authorId !== null && auth.isLoggedIn && auth.user?.id !== post.authorId"
              link
              size="small"
              :type="following ? 'default' : 'primary'"
              class="ml-1"
              @click="toggleFollow"
            >
              {{ following ? '已关注' : '＋ 关注' }}
            </el-button>
          </div>
          <el-divider />
          <div
            ref="postBodyRef"
            class="markdown-body cl-post-body"
            v-highlight
            v-html="post.contentHtml"
          />
          <!-- 互动操作行（F-FORUM-005）：toggle 后用响应 {active, count} 回填本地状态 -->
          <div class="mt-4 flex items-center gap-2">
            <el-button
              size="small"
              :type="post.likedByMe ? 'primary' : 'default'"
              :plain="!post.likedByMe"
              :loading="togglingLike"
              @click="toggleLike"
            >
              点赞{{ post.likeCount > 0 ? ` ${post.likeCount}` : '' }}
            </el-button>
            <el-button
              size="small"
              :type="post.favoritedByMe ? 'warning' : 'default'"
              :plain="!post.favoritedByMe"
              :loading="togglingFavorite"
              @click="toggleFavorite"
            >
              {{ post.favoritedByMe ? '已收藏' : '收藏' }}
            </el-button>
            <el-button
              v-if="isAuthor"
              link
              size="small"
              class="ml-auto"
              @click="router.push(`/post/${post.id}/edit`)"
            >
              编辑
            </el-button>
            <el-button
              v-if="isAuthor"
              link
              type="danger"
              size="small"
              :loading="deleting"
              @click="onDelete"
            >
              删除
            </el-button>
            <el-button
              v-else
              link
              size="small"
              :class="{ 'ml-auto max-md:!h-7': true }"
              @click="openReport('POST', post.id)"
            >
              举报
            </el-button>
          </div>
        </div>
      </el-card>

      <el-card shadow="never" class="mb-4" :body-style="{ padding: 0 }">
        <template #header>
          <span class="font-medium">
            全部回复<template v-if="!repliesLoading && !repliesError">（{{ total }}）</template>
          </span>
        </template>

        <el-skeleton v-if="repliesLoading" animated class="px-5 pt-4">
          <template #template>
            <div
              v-for="i in 3"
              :key="i"
              class="border-b-[0.5px] border-divider py-3 last:border-b-0"
            >
              <div class="mx-auto max-w-reading">
                <el-skeleton-item variant="text" style="width: 42%" />
                <el-skeleton-item variant="text" class="mt-2" style="width: 100%" />
                <el-skeleton-item variant="text" class="mt-1" style="width: 68%" />
              </div>
            </div>
          </template>
        </el-skeleton>

        <div v-else-if="repliesError" class="p-5">
          <el-alert
            type="error"
            :closable="false"
            show-icon
            :title="repliesError"
          >
            <el-button size="small" @click="loadReplies">重新加载</el-button>
          </el-alert>
        </div>

        <template v-else>
          <p v-if="!replies.length" class="px-5 py-6 text-center text-body text-ink-regular">
            还没有回复，来占一楼
          </p>
          <ul v-else ref="repliesListRef">
            <li
              v-for="r in replies"
              :id="`floor-${r.id}`"
              :key="r.id"
              class="border-b-[0.5px] border-divider px-5 py-3 transition-colors duration-fast ease-standard last:border-b-0"
              :class="{
                'cl-floor-accepted': r.accepted,
                'cl-floor-focused': focusedFloorId === String(r.id),
              }"
            >
              <div class="mx-auto max-w-reading">
                <div class="mb-1.5 flex flex-wrap items-center gap-1.5 text-caption text-ink-meta">
                  <span class="inline-flex items-center rounded-sm bg-code px-1.5 py-px text-ink-regular">
                    #{{ r.floorNo }} 楼
                  </span>
                  <!-- 楼层作者同样可点进主页：回复区里“这人还答过什么”是主页最自然的入口 -->
                  <RouterLink
                    :to="`/u/${r.authorId}`"
                    class="inline-flex items-center gap-1.5 text-ink hover:text-link hover:underline"
                  >
                    <UserAvatar :name="r.authorNickname" :size="20" />
                    <span>{{ r.authorNickname }}</span>
                  </RouterLink>
                  <!-- 匿名帖 authorId 对非作者为 null（CR-139）：不能拿它和楼层作者比对，否则会反推出楼主 -->
                  <el-tag
                    v-if="post.authorId !== null && post.authorId === r.authorId"
                    type="primary"
                    effect="light"
                    size="small"
                  >
                    楼主
                  </el-tag>
                  <span aria-hidden="true">·</span>
                  <span>{{ formatTime(r.createdAt) }}</span>
                  <el-tag v-if="r.accepted" type="success" effect="light" size="small">
                    <SvgIcon name="circle-check" :size="12" class="mr-0.5" />最佳答案
                  </el-tag>
                  <el-button
                    link
                    size="small"
                    :type="r.likedByMe ? 'primary' : 'default'"
                    :loading="likingReplyId === r.id"
                    class="ml-auto"
                    @click="likeReply(r)"
                  >
                    赞{{ r.likeCount > 0 ? ` ${r.likeCount}` : '' }}
                  </el-button>
                  <el-button
                    v-if="canAccept(r)"
                    type="success"
                    effect="plain"
                    size="small"
                    class="ml-2"
                    :loading="acceptingReplyId === r.id"
                    @click="adopt(r)"
                  >
                    采纳
                  </el-button>
                  <el-button
                    v-if="auth.isLoggedIn"
                    link
                    size="small"
                    class="ml-1"
                    @click="quoteReply(r)"
                  >
                    引用
                  </el-button>
                  <el-button
                    v-if="auth.user?.id !== r.authorId"
                    link
                    size="small"
                    class="ml-1"
                    @click="openReport('REPLY', r.id)"
                  >
                    举报
                  </el-button>
                </div>
                <!-- 引用块（F-009 / CR-085）：服务端摘要，被引楼层不可见时 quoted 为 null 不渲染 -->
                <div
                  v-if="r.quoted"
                  class="mb-1.5 rounded-sm border-[0.5px] border-divider bg-code px-2.5 py-1.5 text-caption text-ink-meta"
                >
                  <span class="text-ink-regular">引用 #{{ r.quoted.floorNo }} 楼 · {{ r.quoted.authorNickname }}</span>
                  <span class="ml-1.5">{{ r.quoted.excerpt }}</span>
                </div>
                <div class="markdown-body" v-highlight v-html="r.contentHtml" />
              </div>
            </li>
          </ul>

          <div v-if="total > size" class="flex justify-center px-5 py-4">
            <el-pagination
              layout="prev, pager, next"
              :total="total"
              :page-size="size"
              :current-page="page"
              @current-change="onPageChange"
            />
          </div>

          <div class="mx-auto mt-4 max-w-reading px-5 pb-5">
            <template v-if="auth.isLoggedIn">
              <div id="replyComposer">
                <!-- 引用条（F-009 / CR-085）：点击楼层「引用」后出现，可取消；提交成功后由 submitReply 清空 -->
                <div
                  v-if="quoting"
                  class="mb-2 flex items-center gap-2 rounded-sm border-[0.5px] border-divider bg-code px-2.5 py-1.5 text-caption text-ink-meta"
                >
                  <span>
                    回复 <span class="text-ink-regular">#{{ quoting.floorNo }} 楼 · {{ quoting.authorNickname }}</span>
                  </span>
                  <el-button link size="small" class="ml-auto" @click="cancelQuote">取消引用</el-button>
                </div>
                <el-input
                  v-model="replyMd"
                  type="textarea"
                  :rows="narrow ? 4 : 6"
                  maxlength="20000"
                  show-word-limit
                  placeholder="写下你的回复…支持 Markdown（回复发布后按楼层展示）"
                />
              </div>
              <div class="mt-3 flex justify-end">
                <el-button type="primary" :loading="submitting" @click="submitReply">回复</el-button>
              </div>
            </template>
            <el-alert v-else type="info" :closable="false" show-icon>
              <template #title>
                登录后才能回帖 ·
                <RouterLink :to="{ path: '/login', query: { redirect: route.fullPath } }">去登录</RouterLink>
              </template>
            </el-alert>
          </div>
        </template>
      </el-card>
        </div>

        <!-- 右侧目录侧栏：≥ lg 才显示，sticky 跟随滚动；无标题时 TOC 组件自行不渲染 -->
        <aside class="cl-post-toc" aria-label="帖子目录侧栏">
          <div class="cl-post-toc__inner">
            <TableOfContents :html="post.contentHtml" />
          </div>
        </aside>
      </div>
    </template>

    <el-card v-else shadow="never">
      <el-skeleton animated>
        <template #template>
          <div class="mx-auto max-w-reading">
            <el-skeleton-item variant="text" style="width: 88px" />
            <el-skeleton-item variant="h1" class="mt-3" style="width: 72%" />
            <div class="mt-3 flex items-center gap-2">
              <el-skeleton-item variant="text" style="width: 56px" />
              <el-skeleton-item variant="text" style="width: 136px" />
              <el-skeleton-item variant="text" style="width: 56px" />
            </div>
            <el-skeleton-item variant="text" class="mt-5" style="width: 100%" />
            <el-skeleton-item variant="text" class="mt-2" style="width: 100%" />
            <el-skeleton-item variant="text" class="mt-2" style="width: 64%" />
          </div>
        </template>
      </el-skeleton>
    </el-card>

    <!-- 举报对话框（F-SAFE-002 / CR-084）：理由五选一 + 补充说明；24h 同对象去重由后端兜底（429 / 2012）。
         ⚠️ 必须放在根 div 之内：App.vue 用 <Transition mode="out-in"> 包路由组件，多根组件（fragment）
         无法被过渡渲染成注释节点——CR-084 把对话框写在根外曾导致详情页整页空白，CR-091 修复 -->
    <el-dialog v-model="reportDialogVisible" title="提交举报" width="440px">
      <el-form label-position="top">
        <el-form-item label="举报理由">
          <el-select v-model="reportForm.reason" class="w-full">
            <el-option v-for="r in REPORT_REASONS" :key="r.value" :label="r.label" :value="r.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="补充说明（可选）">
          <el-input
            v-model="reportForm.detail"
            type="textarea"
            :rows="3"
            maxlength="1024"
            show-word-limit
            placeholder="补充问题描述（可选）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reportDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="reporting" @click="doReport">提交举报</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
/* ---------- 双栏布局：主内容 + 右侧目录 ---------- */
.cl-post-shell {
  display: flex;
  align-items: flex-start;
  gap: 24px;
}

.cl-post-main {
  flex: 1 1 auto;
  min-width: 0;
}

.cl-post-toc {
  display: none;
  flex: 0 0 auto;
  width: 224px;
  position: sticky;
  top: 80px;
  /* 侧栏内部自己滚动，避免长目录把页面拉长 */
  max-height: calc(100vh - 112px);
  overflow-y: auto;
  overscroll-behavior: contain;
  /* 细滚动条，不抢视觉 */
  scrollbar-width: thin;
  scrollbar-color: var(--cl-border-base) transparent;
}

.cl-post-toc::-webkit-scrollbar {
  width: 4px;
}
.cl-post-toc::-webkit-scrollbar-thumb {
  background: var(--cl-border-base);
  border-radius: 2px;
}
.cl-post-toc::-webkit-scrollbar-track {
  background: transparent;
}

.cl-post-toc__inner {
  padding: 4px 0 8px;
}

/* ≥ lg 才开双栏；窄屏目录隐藏，不影响阅读 */
@media (min-width: 1024px) {
  .cl-post-toc {
    display: block;
  }
}

/* ---------- 正文标题锚点偏移：避免 sticky 头遮挡 ---------- */
.cl-post-body :deep(h1),
.cl-post-body :deep(h2),
.cl-post-body :deep(h3) {
  scroll-margin-top: 88px;
}

/* ---------- 代码块复制按钮（v-html 注入的 DOM，必须 :deep） ----------
 * 选择器以 `.markdown-body` 为锚点而非 `.cl-post-body`：回复楼层的 v-html 容器
 * 同样带 `.markdown-body`（但不带 `.cl-post-body`），两处共用一套按钮样式。
 */

/* pre 外包一层 wrapper：wrapper 接管外边距，pre 自身 margin 归零，
   避免 wrapper / pre 两层 margin 叠加（或坍缩）导致间距与改造前不一致 */
.markdown-body :deep(.cl-pre-wrap) {
  margin: 1em 0;
}

.markdown-body :deep(.cl-pre-wrap > pre) {
  margin: 0;
}

/* 语言标注（CR-091，参考 paicoding 的 attr(lang)）：常驻显示在复制按钮左侧；
   只对带 language-* 的代码块出现（data-lang 由 copyCode.ts 写入），无语言不占位 */
.markdown-body :deep(.cl-pre-wrap[data-lang])::after {
  content: attr(data-lang);
  position: absolute;
  top: 14px;
  right: 72px;
  font-family: ui-monospace, SFMono-Regular, 'JetBrains Mono', Menlo, Consolas, monospace;
  font-size: 11px;
  letter-spacing: 0.05em;
  line-height: 1;
  color: var(--cl-text-meta);
  opacity: 0.75;
  pointer-events: none;
  user-select: none;
}

.markdown-body :deep(.cl-copy-btn) {
  position: absolute;
  top: 8px;
  right: 10px;
  z-index: 2;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 26px;
  padding: 0 9px;
  font-family: ui-monospace, SFMono-Regular, 'JetBrains Mono', Menlo, Consolas, monospace;
  font-size: 11.5px;
  font-weight: 500;
  letter-spacing: 0.04em;
  line-height: 1;
  color: var(--cl-text-regular);
  background: color-mix(in srgb, #fff 82%, transparent);
  border: 1px solid var(--cl-border-base);
  border-radius: 4px;
  cursor: pointer;
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
  opacity: 0;
  transform: translateY(-2px);
  transition:
    opacity var(--cl-duration-fast) var(--cl-ease-standard),
    transform var(--cl-duration-fast) var(--cl-ease-standard),
    color var(--cl-duration-fast) var(--cl-ease-standard),
    background-color var(--cl-duration-fast) var(--cl-ease-standard),
    border-color var(--cl-duration-fast) var(--cl-ease-standard);
}

.markdown-body :deep(.cl-pre-wrap:hover .cl-copy-btn),
.markdown-body :deep(.cl-copy-btn:focus-visible),
.markdown-body :deep(.cl-copy-btn.is-success),
.markdown-body :deep(.cl-copy-btn.is-error) {
  opacity: 1;
  transform: translateY(0);
}

.markdown-body :deep(.cl-copy-btn:hover) {
  color: var(--cl-color-primary);
  border-color: color-mix(in srgb, var(--cl-color-primary) 40%, var(--cl-border-base));
  background: #fff;
}

.markdown-body :deep(.cl-copy-btn:focus-visible) {
  outline: none;
  box-shadow: var(--cl-focus-ring);
}

.markdown-body :deep(.cl-copy-btn.is-success) {
  color: #16a34a;
  border-color: color-mix(in srgb, #16a34a 40%, var(--cl-border-base));
  background: color-mix(in srgb, #16a34a 6%, #fff);
}

.markdown-body :deep(.cl-copy-btn.is-error) {
  color: #dc2626;
  border-color: color-mix(in srgb, #dc2626 40%, var(--cl-border-base));
  background: color-mix(in srgb, #dc2626 6%, #fff);
}

.markdown-body :deep(.cl-copy-btn__icon) {
  position: relative;
  display: inline-flex;
  width: 12px;
  height: 12px;
  flex: 0 0 12px;
}

.markdown-body :deep(.cl-copy-btn__icon > svg) {
  position: absolute;
  inset: 0;
  transition:
    opacity var(--cl-duration-fast) var(--cl-ease-standard),
    transform var(--cl-duration-base) var(--cl-ease-standard);
}

.markdown-body :deep(.cl-copy-btn__check) {
  opacity: 0;
  transform: scale(0.6);
}

.markdown-body :deep(.cl-copy-btn.is-success .cl-copy-btn__icon > svg:first-child) {
  opacity: 0;
  transform: scale(0.6);
}

.markdown-body :deep(.cl-copy-btn.is-success .cl-copy-btn__check) {
  opacity: 1;
  transform: scale(1);
}

/* 触摸设备无 hover，直接常驻显示（否则按钮永远看不到） */
@media (hover: none) {
  .markdown-body :deep(.cl-copy-btn) {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
