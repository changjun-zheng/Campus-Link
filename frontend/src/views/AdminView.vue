<script setup lang="ts">
/**
 * 管理后台（CR-084）：举报工单闭环 + 内容处置台。
 *
 * <p>权限口径与全仓一致：前端显隐只是体验，**真正的安全边界在后端**
 * （所有 /admin 接口仅 SUPERADMIN，requireRole = G9 机器强制）。本页对非超管
 * 渲染「无权限」占位，仍不影响安全性。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  closeReport,
  deleteAdminTag,
  getAdminDashboard,
  getReviewQueue,
  listAdminTags,
  listReports,
  moderatePost,
  moderateReply,
  searchAdminContents,
  setUserStatus,
  type AdminContentItemVo,
  type AdminDashboardVo,
  type AdminReportVo,
  type AdminTagVo,
  type ReviewQueueItemVo,
} from '../api/admin'
import { useAuthStore } from '../stores/auth'
import { formatRelativeTime } from '../utils/time'

const router = useRouter()
const auth = useAuthStore()

const isSuperadmin = computed(() => auth.user?.role === 'SUPERADMIN')

const tab = ref<'reports' | 'moderation' | 'dashboard' | 'tags' | 'review'>('reports')

/* ─── 工单 ─── */
const reportStatus = ref<'PENDING' | 'CLOSED' | ''>('')
const reports = ref<AdminReportVo[]>([])
const reportTotal = ref(0)
const reportPage = ref(1)
const reportSize = 20
const reportsLoading = ref(false)
const reportsError = ref('')
const processingId = ref<number | null>(null)
const closeDialogVisible = ref(false)
const closeTarget = ref<AdminReportVo | null>(null)
const closeAction = ref<'REMOVE' | 'DISMISS'>('REMOVE')
const closeNote = ref('')

const REASON_LABELS: Record<string, string> = {
  ILLEGAL: '违法违规',
  HARASS: '骚扰广告',
  COPYRIGHT: '侵权',
  SPAM: '低质灌水',
  OTHER: '其他',
}
const TARGET_LABELS: Record<string, string> = { POST: '帖子', REPLY: '回复', USER: '用户' }

async function loadReports() {
  reportsLoading.value = true
  reportsError.value = ''
  try {
    const res = await listReports({
      status: reportStatus.value || undefined,
      page: reportPage.value,
      size: reportSize,
    })
    reports.value = res.list
    reportTotal.value = res.total
  } catch (e) {
    reports.value = []
    reportTotal.value = 0
    reportsError.value = e instanceof Error ? e.message : '工单加载失败'
  } finally {
    reportsLoading.value = false
  }
}

function targetLink(r: AdminReportVo): string {
  if (r.targetType === 'POST') return `/post/${r.targetId}`
  return ''
}

function openClose(r: AdminReportVo, action: 'REMOVE' | 'DISMISS') {
  closeTarget.value = r
  closeAction.value = action
  closeNote.value = ''
  closeDialogVisible.value = true
}

async function confirmClose() {
  if (!closeTarget.value) return
  processingId.value = closeTarget.value.id
  try {
    await closeReport(closeTarget.value.id, closeAction.value, closeNote.value.trim() || undefined)
    ElMessage.success(closeAction.value === 'REMOVE' ? '已处置并闭环' : '已驳回并闭环')
    closeDialogVisible.value = false
    await loadReports()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '处理失败')
  } finally {
    processingId.value = null
  }
}

/* ─── 处置台：搜索（F-ADMIN-002 / CR-141）+ 按 id 直达 ─── */
const contentType = ref<'post' | 'reply'>('post')
const contentKeyword = ref('')
const contentUserId = ref('')
const contentAuthorKeyword = ref('')
const contentDays = ref<'' | '7' | '30' | '90'>('')
const contents = ref<AdminContentItemVo[]>([])
const contentTotal = ref(0)
const contentPage = ref(1)
const contentSize = 20
const contentsLoading = ref(false)
const contentsError = ref('')
const moderating = ref(false)

async function loadContents() {
  contentsLoading.value = true
  contentsError.value = ''
  try {
    const res = await searchAdminContents({
      type: contentType.value,
      keyword: contentKeyword.value.trim() || undefined,
      userId: Number(contentUserId.value) > 0 ? Number(contentUserId.value) : undefined,
      authorKeyword: contentAuthorKeyword.value.trim() || undefined,
      days: contentDays.value ? (Number(contentDays.value) as 7 | 30 | 90) : undefined,
      page: contentPage.value,
      size: contentSize,
    })
    contents.value = res.items
    contentTotal.value = res.total
    contentType.value = res.kind
  } catch (e) {
    contents.value = []
    contentTotal.value = 0
    contentsError.value = e instanceof Error ? e.message : '内容搜索失败'
  } finally {
    contentsLoading.value = false
  }
}

function resetContentSearch() {
  contentKeyword.value = ''
  contentUserId.value = ''
  contentAuthorKeyword.value = ''
  contentDays.value = ''
  contentPage.value = 1
  loadContents()
}

function onContentPageChange(next: number) {
  contentPage.value = next
  loadContents()
}

async function moderateContent(item: AdminContentItemVo, next: 'REMOVED' | 'PUBLISHED') {
  if (next === 'REMOVED') {
    try {
      await ElMessageBox.confirm(
        `下架后「${item.title}」将从前台隐藏，可在本表随时恢复。`,
        '确认下架',
        { type: 'warning', confirmButtonText: '下架', cancelButtonText: '取消' },
      )
    } catch {
      return
    }
  }
  moderating.value = true
  try {
    if (item.kind === 'post') {
      await moderatePost(item.id, next, '处置台搜索')
    } else {
      await moderateReply(item.id, next, '处置台搜索')
    }
    ElMessage.success(next === 'REMOVED' ? '已下架' : '已恢复')
    await loadContents()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '处置失败')
  } finally {
    moderating.value = false
  }
}

const targetType = ref<'POST' | 'REPLY'>('POST')
const targetIdInput = ref('')

async function doModerate(next: 'REMOVED' | 'PUBLISHED') {
  const id = Number(targetIdInput.value)
  if (!id || id < 1) {
    ElMessage.warning('请输入有效的目标 id（正整数）')
    return
  }
  moderating.value = true
  try {
    if (targetType.value === 'POST') {
      await moderatePost(id, next, '处置台操作')
    } else {
      await moderateReply(id, next, '处置台操作')
    }
    ElMessage.success(next === 'REMOVED' ? '已下架' : '已恢复')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '处置失败')
  } finally {
    moderating.value = false
  }
}

/* ─── 封禁（工单 REMOVE USER 已联动；此处支持对任意用户 id 直达） ─── */
const banUserId = ref('')
const banning = ref(false)

async function doSetUserStatus(status: 'ACTIVE' | 'BANNED') {
  const id = Number(banUserId.value)
  if (!id || id < 1) {
    ElMessage.warning('请输入有效的用户 id（正整数）')
    return
  }
  banning.value = true
  try {
    await setUserStatus(id, status)
    ElMessage.success(status === 'BANNED' ? '已封禁（登录与发帖即时拒绝）' : '已解封')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '操作失败')
  } finally {
    banning.value = false
  }
}

/* ─── 数据看板（F-ADMIN-005 / CR-131）：进入 Tab 才加载，不在首屏多打一次聚合 ─── */
const dashboard = ref<AdminDashboardVo | null>(null)
const dashboardLoading = ref(false)
const dashboardError = ref('')

async function loadDashboard() {
  dashboardLoading.value = true
  dashboardError.value = ''
  try {
    dashboard.value = await getAdminDashboard()
  } catch (e) {
    dashboard.value = null
    dashboardError.value = e instanceof Error ? e.message : '看板加载失败'
  } finally {
    dashboardLoading.value = false
  }
}

/** 四张卡片的数据源：分组与标签只在模板外组装一次，模板只负责渲染 */
const dashboardCards = computed(() => {
  const d = dashboard.value
  if (!d) return []
  return [
    {
      title: '用户',
      items: [
        { label: '总数', value: d.userTotal },
        { label: '活跃', value: d.userActive },
        { label: '封禁', value: d.userBanned },
        { label: '已注销', value: d.userDeactivated },
      ],
    },
    {
      title: '帖子',
      items: [
        { label: '总数', value: d.postTotal },
        { label: '已发布', value: d.postPublished },
        { label: '已下架', value: d.postRemoved },
        { label: '今日新增', value: d.postTodayCreated },
      ],
    },
    {
      title: '楼层',
      items: [
        { label: '总数', value: d.replyTotal },
        { label: '已发布', value: d.replyPublished },
        { label: '已下架', value: d.replyRemoved },
        { label: '今日新增', value: d.replyTodayCreated },
      ],
    },
    {
      title: '举报',
      items: [
        { label: '总数', value: d.reportTotal },
        { label: '待处理', value: d.reportPending },
        { label: '已闭环', value: d.reportClosed },
      ],
    },
  ]
})

/* ─── 标签管理（F-ADMIN-004 标签侧 / CR-132）：进入 Tab 才加载 ─── */
const tags = ref<AdminTagVo[]>([])
const tagTotal = ref(0)
const tagPage = ref(1)
const tagSize = 20
const tagKeyword = ref('')
const tagSort = ref<'use_count' | 'name'>('use_count')
const tagLoading = ref(false)
const tagError = ref('')

async function loadTags(next = tagPage.value) {
  tagLoading.value = true
  tagError.value = ''
  try {
    const res = await listAdminTags({
      keyword: tagKeyword.value.trim() || undefined,
      sort: tagSort.value,
      page: next,
      size: tagSize,
    })
    tags.value = res.items
    tagTotal.value = res.total
    tagPage.value = res.page
  } catch (e) {
    tags.value = []
    tagTotal.value = 0
    tagError.value = e instanceof Error ? e.message : '标签加载失败'
  } finally {
    tagLoading.value = false
  }
}

function onTagSearch() {
  void loadTags(1)
}

async function onDeleteTag(tag: AdminTagVo) {
  try {
    await ElMessageBox.confirm(
      `确定删除标签「${tag.name}」？它会从所有帖子上移除，且该操作不可撤销。`,
      '删除标签',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    // 用户取消 / 关闭弹窗：不删、不提示
    return
  }
  try {
    await deleteAdminTag(tag.id)
    ElMessage.success(`已删除标签「${tag.name}」`)
    // 删掉当前页最后一条时回退一页，避免落在空页上
    const nextPage = tagPage.value > 1 && tags.value.length === 1 ? tagPage.value - 1 : tagPage.value
    await loadTags(nextPage)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

/* ─── 内容巡查（F-SAFE-004 / CR-133）：进入 Tab 才加载；处置复用既有端点 ─── */
const reviewKind = ref<'post' | 'reply'>('post')
const reviewItems = ref<ReviewQueueItemVo[]>([])
const reviewTotal = ref(0)
const reviewPage = ref(1)
const reviewSize = 20
const reviewLoading = ref(false)
const reviewError = ref('')

async function loadReviewQueue(next = reviewPage.value) {
  reviewLoading.value = true
  reviewError.value = ''
  try {
    const res = await getReviewQueue({ kind: reviewKind.value, page: next, size: reviewSize })
    reviewItems.value = res.items
    reviewTotal.value = res.total
    reviewPage.value = res.page
    reviewKind.value = res.kind
  } catch (e) {
    reviewItems.value = []
    reviewTotal.value = 0
    reviewError.value = e instanceof Error ? e.message : '巡查队列加载失败'
  } finally {
    reviewLoading.value = false
  }
}

function onReviewKindChange() {
  void loadReviewQueue(1)
}

async function onReviewModerate(item: ReviewQueueItemVo) {
  const next = item.status === 'REMOVED' ? 'PUBLISHED' : 'REMOVED'
  const actionLabel = next === 'REMOVED' ? '下架' : '恢复'
  try {
    await ElMessageBox.confirm(
      `确定${actionLabel}「${item.title}」？该操作会记录审计。`,
      `${actionLabel}内容`,
      { type: 'warning', confirmButtonText: actionLabel, cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    if (item.kind === 'reply') {
      await moderateReply(item.id, next, '巡查队列操作')
    } else {
      await moderatePost(item.id, next, '巡查队列操作')
    }
    ElMessage.success(`已${actionLabel}`)
    await loadReviewQueue()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : `${actionLabel}失败`)
  }
}

watch([reportStatus, reportPage], () => loadReports())
watch(tab, () => {
  if (tab.value === 'reports' && !reports.value.length) loadReports()
  if (tab.value === 'dashboard' && !dashboard.value) loadDashboard()
  if (tab.value === 'moderation' && !contents.value.length) loadContents()
  if (tab.value === 'tags' && !tags.value.length) loadTags()
  if (tab.value === 'review' && !reviewItems.value.length) loadReviewQueue()
})
onMounted(() => {
  if (isSuperadmin.value) loadReports()
})

function goLogin() {
  router.push({ path: '/login', query: { redirect: '/admin' } })
}
</script>

<template>
  <!-- TRANSITION-SINGLE-ROOT：App.vue 以 <Transition mode="out-in"> 包路由组件，多根组件（fragment）在该模式下会随机卡死路由切换（CR-091 修复详情页同类问题） -->
  <div>
  <!-- 非超管（含未登录）：占位与 /admin 既有口径一致，权限边界在后端 -->
  <el-result
    v-if="!isSuperadmin"
    icon="warning"
    title="需要超级管理员权限"
    sub-title="本页面仅对 SUPERADMIN 角色开放"
  >
    <template #extra>
      <el-button v-if="!auth.isLoggedIn" type="primary" @click="goLogin">去登录</el-button>
      <RouterLink v-else to="/">
        <el-button>回到首页</el-button>
      </RouterLink>
    </template>
  </el-result>

  <template v-else>
    <el-tabs v-model="tab" class="mb-5">
      <el-tab-pane label="举报工单" name="reports" />
      <el-tab-pane label="内容处置台" name="moderation" />
      <el-tab-pane label="数据看板" name="dashboard" />
      <el-tab-pane label="标签管理" name="tags" />
      <el-tab-pane label="内容巡查" name="review" />
    </el-tabs>

    <!-- ─── 数据看板（F-ADMIN-005 / CR-131）：仅 SUPERADMIN；进入 Tab 才加载 ─── -->
    <el-card v-show="tab === 'dashboard'" shadow="never" class="mb-5">
      <template #header>
        <div class="flex flex-wrap items-baseline justify-between gap-2">
          <span class="font-medium">数据看板</span>
          <span class="text-caption text-ink-meta">今日按 UTC 自然日；作者自删不计，平台下架单列</span>
        </div>
      </template>

      <el-skeleton v-if="dashboardLoading" :rows="4" animated />
      <div v-else-if="dashboardError" class="py-2">
        <el-alert type="error" :closable="false" show-icon :title="dashboardError">
          <el-button size="small" @click="loadDashboard">重新加载</el-button>
        </el-alert>
      </div>
      <div v-else-if="dashboard" class="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <section
          v-for="card in dashboardCards"
          :key="card.title"
          class="rounded-md border-[0.5px] border-divider p-4"
        >
          <h3 class="text-body font-medium text-ink">{{ card.title }}</h3>
          <div class="mt-3 grid grid-cols-2 gap-3">
            <div v-for="item in card.items" :key="item.label">
              <p class="text-title-lg font-medium text-ink tabular-nums">{{ item.value }}</p>
              <p class="mt-0.5 text-caption text-ink-meta">{{ item.label }}</p>
            </div>
          </div>
        </section>
      </div>
    </el-card>

    <!-- ─── 标签管理（F-ADMIN-004 标签侧 / CR-132）：仅 SUPERADMIN；进入 Tab 才加载 ─── -->
    <el-card v-show="tab === 'tags'" shadow="never">
      <template #header>
        <div class="flex flex-wrap items-baseline justify-between gap-2">
          <span class="font-medium">标签管理</span>
          <span class="text-caption text-ink-meta">
            「使用次数」是累计挂载次数（软删帖子不回退）；删除会同时移除帖子上的该标签，不可撤销
          </span>
        </div>
      </template>

      <div class="mb-4 flex flex-wrap items-center gap-2">
        <el-input
          v-model="tagKeyword"
          placeholder="按名称搜索"
          clearable
          style="width: 220px"
          @keyup.enter="onTagSearch"
        />
        <el-select v-model="tagSort" aria-label="标签排序" style="width: 150px" @change="onTagSearch">
          <el-option label="按使用次数" value="use_count" />
          <el-option label="按名称" value="name" />
        </el-select>
        <el-button type="primary" @click="onTagSearch">搜索</el-button>
      </div>

      <el-skeleton v-if="tagLoading" :rows="4" animated />
      <div v-else-if="tagError" class="py-2">
        <el-alert type="error" :closable="false" show-icon :title="tagError">
          <el-button size="small" @click="loadTags()">重新加载</el-button>
        </el-alert>
      </div>
      <div v-else-if="!tags.length" class="py-8 text-center text-body text-ink-meta">
        没有匹配的标签
      </div>
      <template v-else>
        <ul>
          <li
            v-for="tag in tags"
            :key="tag.id"
            class="flex items-center justify-between gap-3 border-b-[0.5px] border-divider py-2.5 last:border-b-0"
          >
            <div class="flex min-w-0 items-center gap-2">
              <span class="truncate text-body font-medium text-ink">{{ tag.name }}</span>
              <span class="flex-none text-caption text-ink-meta">使用 {{ tag.useCount }} 次</span>
            </div>
            <div class="flex flex-none items-center gap-3">
              <span class="text-caption text-ink-meta">{{ formatRelativeTime(tag.createdAt) }}</span>
              <el-button size="small" type="danger" plain @click="onDeleteTag(tag)">删除</el-button>
            </div>
          </li>
        </ul>
        <div v-if="tagTotal > tagSize" class="flex justify-center pt-3">
          <el-pagination
            layout="prev, pager, next, total"
            :total="tagTotal"
            :page-size="tagSize"
            :current-page="tagPage"
            @current-change="(next: number) => loadTags(next)"
          />
        </div>
      </template>
    </el-card>

    <!-- ─── 内容巡查（F-SAFE-004 / CR-133）：仅 SUPERADMIN；进入 Tab 才加载 ─── -->
    <el-card v-show="tab === 'review'" shadow="never">
      <template #header>
        <div class="flex flex-wrap items-baseline justify-between gap-2">
          <span class="font-medium">内容巡查</span>
          <span class="text-caption text-ink-meta">
            按发布时间倒序；只列未删内容，已下架仍列出可恢复；处置会记录审计
          </span>
        </div>
      </template>

      <div class="mb-4 flex flex-wrap items-center gap-2">
        <el-select v-model="reviewKind" aria-label="巡查对象" style="width: 140px" @change="onReviewKindChange">
          <el-option label="帖子" value="post" />
          <el-option label="楼层" value="reply" />
        </el-select>
      </div>

      <el-skeleton v-if="reviewLoading" :rows="4" animated />
      <div v-else-if="reviewError" class="py-2">
        <el-alert type="error" :closable="false" show-icon :title="reviewError">
          <el-button size="small" @click="loadReviewQueue()">重新加载</el-button>
        </el-alert>
      </div>
      <div v-else-if="!reviewItems.length" class="py-8 text-center text-body text-ink-meta">
        暂无内容
      </div>
      <template v-else>
        <ul>
          <li
            v-for="item in reviewItems"
            :key="`${item.kind}-${item.id}`"
            class="flex items-center justify-between gap-3 border-b-[0.5px] border-divider py-2.5 last:border-b-0"
          >
            <div class="min-w-0 flex-1">
              <p class="truncate text-body font-medium text-ink">{{ item.title }}</p>
              <p class="mt-0.5 text-caption text-ink-meta">
                {{ item.boardName }} · {{ item.authorNickname }} · {{ formatRelativeTime(item.createdAt) }}
              </p>
            </div>
            <div class="flex flex-none items-center gap-3">
              <el-tag :type="item.status === 'REMOVED' ? 'danger' : 'success'" size="small">
                {{ item.status === 'REMOVED' ? '已下架' : '正常' }}
              </el-tag>
              <el-button
                size="small"
                :type="item.status === 'REMOVED' ? 'primary' : 'danger'"
                plain
                @click="onReviewModerate(item)"
              >
                {{ item.status === 'REMOVED' ? '恢复' : '下架' }}
              </el-button>
            </div>
          </li>
        </ul>
        <div v-if="reviewTotal > reviewSize" class="flex justify-center pt-3">
          <el-pagination
            layout="prev, pager, next, total"
            :total="reviewTotal"
            :page-size="reviewSize"
            :current-page="reviewPage"
            @current-change="(next: number) => loadReviewQueue(next)"
          />
        </div>
      </template>
    </el-card>

    <!-- ─── 举报工单 ─── -->
    <el-card v-show="tab === 'reports'" shadow="never" :body-style="{ padding: 0 }">
      <template #header>
        <div class="flex items-center justify-between">
          <span class="font-medium">举报工单</span>
          <el-radio-group v-model="reportStatus" size="small">
            <el-radio-button value="">全部</el-radio-button>
            <el-radio-button value="PENDING">待处理</el-radio-button>
            <el-radio-button value="CLOSED">已闭环</el-radio-button>
          </el-radio-group>
        </div>
      </template>

      <el-skeleton v-if="reportsLoading" animated class="px-5 pt-4">
        <template #template>
          <div v-for="i in 4" :key="i" class="border-b-[0.5px] border-divider py-3 last:border-b-0">
            <el-skeleton-item variant="text" style="width: 48%" />
            <el-skeleton-item variant="text" class="mt-2" style="width: 88%" />
          </div>
        </template>
      </el-skeleton>

      <div v-else-if="reportsError" class="p-5">
        <el-alert type="error" :closable="false" show-icon :title="reportsError">
          <el-button size="small" @click="loadReports">重新加载</el-button>
        </el-alert>
      </div>

      <p v-else-if="!reports.length" class="py-10 text-center text-body text-ink-regular">
        暂无{{ reportStatus === 'PENDING' ? '待处理' : '' }}工单
      </p>

      <template v-else>
        <ul>
          <li
            v-for="r in reports"
            :key="r.id"
            class="border-b-[0.5px] border-divider px-5 py-3 transition-colors duration-fast ease-standard last:border-b-0"
          >
            <div class="flex flex-wrap items-center gap-2 text-caption text-ink-meta">
              <el-tag size="small" :type="r.status === 'PENDING' ? 'warning' : 'info'" effect="light">
                {{ r.status === 'PENDING' ? '待处理' : '已闭环' }}
              </el-tag>
              <span class="text-ink-regular">{{ TARGET_LABELS[r.targetType] || r.targetType }}</span>
              <span>#{{ r.targetId }}</span>
              <span aria-hidden="true">·</span>
              <span class="text-ink-regular">{{ REASON_LABELS[r.reason] || r.reason }}</span>
              <span aria-hidden="true">·</span>
              <span>举报人 #{{ r.reporterId }}</span>
              <span aria-hidden="true">·</span>
              <span>{{ formatRelativeTime(r.createdAt) }}</span>
              <span v-if="targetLink(r)" aria-hidden="true">·</span>
              <RouterLink v-if="targetLink(r)" :to="targetLink(r)" class="text-link hover:underline">
                查看目标
              </RouterLink>
            </div>
            <p v-if="r.detail" class="mt-1 line-clamp-2 text-note leading-body text-ink-regular">
              {{ r.detail }}
            </p>
            <p v-if="r.status === 'CLOSED'" class="mt-1 text-note text-ink-meta">
              处理结论：{{ r.resolution || '—' }}
            </p>
            <div v-if="r.status === 'PENDING'" class="mt-2 flex gap-2">
              <el-button
                type="danger"
                size="small"
                :loading="processingId === r.id"
                @click="openClose(r, 'REMOVE')"
              >
                处置并闭环
              </el-button>
              <el-button size="small" :disabled="processingId === r.id" @click="openClose(r, 'DISMISS')">
                驳回
              </el-button>
            </div>
          </li>
        </ul>
        <div class="flex justify-center px-5 py-4">
          <el-pagination
            v-if="reportTotal > reportSize"
            layout="prev, pager, next"
            :total="reportTotal"
            :page-size="reportSize"
            :current-page="reportPage"
            @current-change="(n: number) => (reportPage = n)"
          />
        </div>
      </template>
    </el-card>

    <!-- ─── 内容处置台：搜索（F-ADMIN-002 / CR-141）+ 按 id 直达 ─── -->
    <el-card v-show="tab === 'moderation'" shadow="never">
      <template #header><span class="font-medium">内容处置台</span></template>

      <div class="px-5">
        <el-form inline class="gap-y-2">
          <el-form-item label="类型">
            <el-radio-group v-model="contentType" @change="contentPage = 1; loadContents()">
              <el-radio-button value="post">帖子</el-radio-button>
              <el-radio-button value="reply">楼层</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="关键词">
            <el-input
              v-model="contentKeyword"
              placeholder="标题 / 正文，2~50 字"
              clearable
              class="w-[220px]"
              @keyup.enter="contentPage = 1; loadContents()"
            />
          </el-form-item>
          <el-form-item label="用户 id">
            <el-input v-model="contentUserId" placeholder="可选" clearable class="w-[120px]" />
          </el-form-item>
          <el-form-item label="昵称">
            <el-input
              v-model="contentAuthorKeyword"
              placeholder="可选，优先用户 id"
              clearable
              class="w-[150px]"
            />
          </el-form-item>
          <el-form-item label="时间">
            <el-select v-model="contentDays" aria-label="时间范围" placeholder="全部" clearable class="w-[110px]">
              <el-option label="近 7 天" value="7" />
              <el-option label="近 30 天" value="30" />
              <el-option label="近 90 天" value="90" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="contentsLoading" @click="contentPage = 1; loadContents()">搜索</el-button>
            <el-button @click="resetContentSearch">重置</el-button>
          </el-form-item>
        </el-form>

        <el-alert v-if="contentsError" type="error" :closable="false" :title="contentsError" class="mb-3" />
        <el-table v-loading="contentsLoading" :data="contents" size="small" class="mb-3">
          <el-table-column prop="kind" label="类型" width="70">
            <template #default="{ row }">{{ row.kind === 'post' ? '帖子' : '楼层' }}</template>
          </el-table-column>
          <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
          <el-table-column prop="summary" label="摘要" min-width="240" show-overflow-tooltip />
          <el-table-column prop="boardName" label="版块" width="110" />
          <el-table-column label="作者" width="120">
            <template #default="{ row }">{{ row.authorNickname }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'info'" size="small">
                {{ row.status === 'PUBLISHED' ? '正常' : '已下架' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="时间" width="110">
            <template #default="{ row }">{{ formatRelativeTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 'PUBLISHED'"
                type="danger"
                link
                :loading="moderating"
                @click="moderateContent(row as AdminContentItemVo, 'REMOVED')"
              >下架</el-button>
              <el-button
                v-else
                type="success"
                link
                :loading="moderating"
                @click="moderateContent(row as AdminContentItemVo, 'PUBLISHED')"
              >恢复</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="mb-4 flex justify-end">
          <el-pagination
            layout="total, prev, pager, next"
            :total="contentTotal"
            :page-size="contentSize"
            :current-page="contentPage"
            @current-change="onContentPageChange"
          />
        </div>
      </div>

      <el-divider>按 id 直达</el-divider>
      <el-form label-width="90px" class="max-w-[560px]">
        <el-form-item label="目标类型">
          <el-radio-group v-model="targetType">
            <el-radio-button value="POST">帖子</el-radio-button>
            <el-radio-button value="REPLY">楼层</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="目标 id">
          <el-input v-model="targetIdInput" placeholder="帖子 / 楼层 id（正整数）" />
        </el-form-item>
        <el-form-item>
          <el-button type="danger" :loading="moderating" @click="doModerate('REMOVED')">下架</el-button>
          <el-button type="success" :loading="moderating" @click="doModerate('PUBLISHED')">恢复</el-button>
        </el-form-item>
        <el-divider />
        <el-form-item label="用户 id">
          <el-input v-model="banUserId" placeholder="用户 id（正整数）" />
        </el-form-item>
        <el-form-item>
          <el-button type="danger" :loading="banning" @click="doSetUserStatus('BANNED')">封禁</el-button>
          <el-button :loading="banning" @click="doSetUserStatus('ACTIVE')">解封</el-button>
        </el-form-item>
      </el-form>
      <p class="px-5 pb-5 text-caption leading-body text-ink-meta">
        下架即从全站列表 / 搜索 / 热榜 / 详情消失（不碰作者删除墓碑，可恢复）；封禁即时拒绝登录与发帖。
        全部操作写审计日志。
      </p>
    </el-card>

    <!-- 工单处置确认弹窗：CR-103 接入 ESLint 时检出「按钮点了没反应」——
         openClose 早已置 closeDialogVisible、confirmClose 也早已写好，但模板自 CR-084 起从未渲染该弹窗、
         确认动作无从触达（ESLint 报 confirmClose 定义未使用 + 无 el-dialog 佐证）。此处补齐意图中的弹窗。 -->
    <el-dialog
      v-model="closeDialogVisible"
      :title="closeAction === 'REMOVE' ? '处置并闭环' : '驳回举报'"
      width="440px"
    >
      <p class="text-body text-ink-regular">
        {{ closeAction === 'REMOVE' ? '确认下架目标 #' : '确认驳回举报 #' }}{{ closeTarget?.id }}
      </p>
      <el-input
        v-model="closeNote"
        type="textarea"
        :rows="3"
        maxlength="200"
        show-word-limit
        placeholder="处理结论（可留空，最长 200 字）"
        class="mt-3"
      />
      <template #footer>
        <el-button @click="closeDialogVisible = false">取消</el-button>
        <el-button
          :type="closeAction === 'REMOVE' ? 'danger' : 'primary'"
          :loading="processingId === closeTarget?.id"
          @click="confirmClose"
        >
          确认
        </el-button>
      </template>
    </el-dialog>
  </template>
  </div>
</template>

<script lang="ts">
// 工单处理确认对话框（内联 ElMessageBox 风格改为 el-dialog 更可控，此处用 dialog 变量驱动）
export default {}
</script>

<style scoped>
:deep(.el-dialog) {
  max-width: 480px;
}
</style>
