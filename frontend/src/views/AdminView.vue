<script setup lang="ts">
/**
 * 管理后台（CR-084）：举报工单闭环 + 内容处置台。
 *
 * <p>权限口径与全仓一致：前端显隐只是体验，**真正的安全边界在后端**
 * （requireRole = G9 机器强制）。CR-196 起运营（OPS）可读工单 / 巡查 / 内容检索，
 * 处置、封禁、看板、标签、角色授予仍仅 SUPERADMIN；本页对两者之外的角色渲染「无权限」占位。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  closeReport,
  deleteAdminTag,
  getAdminDashboard,
  getReviewQueue,
  grantOpsRole,
  importRoster,
  listAdminTags,
  listReports,
  moderatePost,
  moderateReply,
  muteUser,
  searchAdminContents,
  searchAdminUsers,
  setUserStatus,
  unmuteUser,
  warnUser,
  type AdminContentItemVo,
  type AdminDashboardVo,
  type AdminReportVo,
  type AdminTagVo,
  type AdminUserItemVo,
  type ReviewQueueItemVo,
  type RosterImportResultVo,
} from '../api/admin'
import { useAuthStore } from '../stores/auth'
import { formatRelativeTime, formatTime } from '../utils/time'
import DonutChart from '../components/DonutChart.vue'
import PageHeader from '../components/PageHeader.vue'

const router = useRouter()
const auth = useAuthStore()

const isSuperadmin = computed(() => auth.user?.role === 'SUPERADMIN')
const canAccessAdmin = computed(() => isSuperadmin.value || auth.user?.role === 'OPS')

const tab = ref<'reports' | 'moderation' | 'dashboard' | 'tags' | 'review' | 'roster'>('reports')

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

/* ─── 警告 / 禁言（F-ADMIN-002 / CR-209）：均以 banUserId 为目标，仅 SUPERADMIN ─── */
const warnReason = ref('')
const warning = ref(false)

async function doWarn() {
  const id = Number(banUserId.value)
  if (!id || id < 1) {
    ElMessage.warning('请先填写有效的用户 id（正整数）')
    return
  }
  if (!warnReason.value.trim()) {
    ElMessage.warning('请填写警告原因')
    return
  }
  warning.value = true
  try {
    await warnUser(id, warnReason.value.trim())
    ElMessage.success('警告已发送（站内通知）')
    warnReason.value = ''
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '警告发送失败')
  } finally {
    warning.value = false
  }
}

const muteHours = ref(24)
const muteReason = ref('')
const muting = ref(false)
const unmuting = ref(false)

async function doMute() {
  const id = Number(banUserId.value)
  if (!id || id < 1) {
    ElMessage.warning('请先填写有效的用户 id（正整数）')
    return
  }
  if (!muteReason.value.trim()) {
    ElMessage.warning('请填写禁言原因')
    return
  }
  muting.value = true
  try {
    await muteUser(id, muteHours.value, muteReason.value.trim())
    ElMessage.success(`已禁言 ${muteHours.value} 小时，期间发帖 / 回帖 / 组队写入被拒绝`)
    muteReason.value = ''
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '禁言失败')
  } finally {
    muting.value = false
  }
}

async function doUnmute() {
  const id = Number(banUserId.value)
  if (!id || id < 1) {
    ElMessage.warning('请先填写有效的用户 id（正整数）')
    return
  }
  unmuting.value = true
  try {
    await unmuteUser(id)
    ElMessage.success('已解除禁言')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '解除禁言失败')
  } finally {
    unmuting.value = false
  }
}

/* ─── 授予运营角色（F-ADMIN-001 / CR-196）：仅 SUPERADMIN；仅 USER → OPS ─── */
const opsUserId = ref('')
const grantingOps = ref(false)

async function doGrantOps() {
  const id = Number(opsUserId.value)
  if (!id || id < 1) {
    ElMessage.warning('请输入有效的用户 id（正整数）')
    return
  }
  grantingOps.value = true
  try {
    await grantOpsRole(id)
    ElMessage.success('已授予运营角色；目标用户需重新登录后生效')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '授予失败')
  } finally {
    grantingOps.value = false
  }
}

/* ─── 用户检索（CR-211，仅 SUPERADMIN）：按 id 或昵称找账号，一键填入下方处置目标 ─── */
const userKeyword = ref('')
const userResults = ref<AdminUserItemVo[]>([])
const userTruncated = ref(false)
const userSearched = ref(false)
const usersLoading = ref(false)
const usersError = ref('')

async function loadUsers() {
  const keyword = userKeyword.value.trim()
  if (!keyword) {
    ElMessage.warning('请输入用户 id 或昵称关键字')
    return
  }
  usersLoading.value = true
  usersError.value = ''
  try {
    const res = await searchAdminUsers(keyword)
    userResults.value = res.items
    userTruncated.value = res.truncated
    userSearched.value = true
  } catch (e) {
    userResults.value = []
    userTruncated.value = false
    userSearched.value = true
    usersError.value = e instanceof Error ? e.message : '用户检索失败'
  } finally {
    usersLoading.value = false
  }
}

function resetUserSearch() {
  userKeyword.value = ''
  userResults.value = []
  userTruncated.value = false
  userSearched.value = false
  usersError.value = ''
}

function fillUserTarget(id: number, nickname: string) {
  banUserId.value = String(id)
  ElMessage.success(`已填入用户 #${id}（${nickname}），可在下方执行封禁 / 警告 / 禁言`)
  document.getElementById('admin-user-actions')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function selectUserTarget(user: AdminUserItemVo) {
  fillUserTarget(user.id, user.nickname)
}

function selectOpsTarget(user: AdminUserItemVo) {
  opsUserId.value = String(user.id)
  ElMessage.success(`已填入运营授予目标 #${user.id}（${user.nickname}）`)
  document.getElementById('admin-user-actions')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function selectAuthorTarget(item: AdminContentItemVo) {
  fillUserTarget(item.authorId, item.authorNickname)
}

function isMuted(user: AdminUserItemVo): boolean {
  return !!user.mutedUntil && new Date(user.mutedUntil).getTime() > Date.now()
}

function mutedLabel(user: AdminUserItemVo): string {
  return user.mutedUntil ? formatTime(user.mutedUntil) : '—'
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

/**
 * 看板呈现（2026-11-05）：四张 KPI 卡给出总量与状态拆分，下方「总量对比」横向柱状图
 * 与「内容状态」环形图负责量感；颜色全部取既有语义令牌，文字与数值始终同屏。
 */
const dashboardKpis = computed(() => {
  const d = dashboard.value
  if (!d) return []
  const primary = 'var(--cl-color-primary-strong)'
  const warning = 'var(--cl-color-warning-strong)'
  const success = 'var(--cl-color-success-strong)'
  const danger = 'var(--cl-color-danger-strong)'
  const muted = 'var(--cl-text-placeholder)'
  return [
    {
      title: '用户',
      total: d.userTotal,
      items: [
        { label: '活跃', value: d.userActive, color: primary },
        { label: '封禁', value: d.userBanned, color: danger },
        { label: '已注销', value: d.userDeactivated, color: muted },
      ],
      extra: null,
    },
    {
      title: '帖子',
      total: d.postTotal,
      items: [
        { label: '已发布', value: d.postPublished, color: primary },
        { label: '已下架', value: d.postRemoved, color: warning },
      ],
      extra: { label: '今日新增', value: d.postTodayCreated },
    },
    {
      title: '楼层',
      total: d.replyTotal,
      items: [
        { label: '已发布', value: d.replyPublished, color: primary },
        { label: '已下架', value: d.replyRemoved, color: warning },
      ],
      extra: { label: '今日新增', value: d.replyTodayCreated },
    },
    {
      title: '举报',
      total: d.reportTotal,
      items: [
        { label: '待处理', value: d.reportPending, color: warning },
        { label: '已闭环', value: d.reportClosed, color: success },
      ],
      extra: null,
    },
  ]
})

/** 总量对比（横向柱状图）：保持线性刻度，不做对数压缩以免误读数 */
const totalBars = computed(() => {
  const d = dashboard.value
  if (!d) return []
  return [
    { label: '用户', value: d.userTotal },
    { label: '帖子', value: d.postTotal },
    { label: '楼层', value: d.replyTotal },
    { label: '举报', value: d.reportTotal },
  ]
})

const maxTotal = computed(() => Math.max(1, ...totalBars.value.map((item) => item.value)))

/** 非零值至少给 2% 宽度，避免个位数被压成看不见的细线 */
function barWidth(value: number): string {
  if (!value) return '0%'
  return `${Math.max((value / maxTotal.value) * 100, 2)}%`
}

/** 内容状态环形图：帖子与楼层合并，只保留「已发布 / 已下架」两段 */
const contentDonut = computed(() => {
  const d = dashboard.value
  if (!d) return []
  return [
    {
      label: '已发布',
      value: d.postPublished + d.replyPublished,
      color: 'var(--cl-color-primary-strong)',
    },
    {
      label: '已下架',
      value: d.postRemoved + d.replyRemoved,
      color: 'var(--cl-color-warning-strong)',
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

/* ─── 名册导入（F-ACC-004 / CR-210）：仅 SUPERADMIN；CSV 原文提交，后端哈希入库并写审计 ─── */
const rosterFileEl = ref<HTMLInputElement | null>(null)
const rosterFileName = ref('')
const rosterFileSize = ref(0)
const rosterEncoding = ref<'UTF-8' | 'GBK'>('UTF-8')
const rosterText = ref('')
const rosterLineCount = ref(0)
const rosterPreview = ref<string[]>([])
const rosterBatch = ref(defaultRosterBatch())
const rosterImporting = ref(false)
const rosterResult = ref<RosterImportResultVo | null>(null)
const rosterResultBatch = ref('')
const rosterError = ref('')
const rosterReady = computed(() => rosterText.value.trim().length > 0)

function defaultRosterBatch(): string {
  const now = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `manual-${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function pickRosterFile() {
  rosterFileEl.value?.click()
}

function clearRosterFile() {
  rosterFileName.value = ''
  rosterFileSize.value = 0
  rosterEncoding.value = 'UTF-8'
  rosterText.value = ''
  rosterLineCount.value = 0
  rosterPreview.value = []
  rosterResult.value = null
  rosterResultBatch.value = ''
  rosterError.value = ''
  if (rosterFileEl.value) rosterFileEl.value.value = ''
}

async function onRosterFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (file.size > 5 * 1024 * 1024) {
    clearRosterFile()
    ElMessage.warning('CSV 文件不能超过 5 MB')
    return
  }
  rosterResult.value = null
  rosterResultBatch.value = ''
  rosterError.value = ''
  try {
    const buffer = await file.arrayBuffer()
    let text: string
    let encoding: 'UTF-8' | 'GBK' = 'UTF-8'
    try {
      text = new TextDecoder('utf-8', { fatal: true }).decode(buffer)
    } catch {
      // Excel 在中文 Windows 上常把 CSV 存成 GBK；按 GBK 读取后再以 UTF-8 提交，避免姓名入库为乱码
      text = new TextDecoder('gbk').decode(buffer)
      encoding = 'GBK'
    }
    // UTF-8 BOM 不是表头内容；留着会让「学号」表头识别失败
    if (text.charCodeAt(0) === 0xfeff) text = text.slice(1)
    const lines = text.split(/\r\n?|\n/).filter((line) => line.trim())
    rosterFileName.value = file.name
    rosterFileSize.value = file.size
    rosterEncoding.value = encoding
    rosterText.value = text
    rosterLineCount.value = lines.length
    rosterPreview.value = lines.slice(0, 5)
    if (!lines.length) ElMessage.warning('文件为空或只有空行')
  } catch {
    clearRosterFile()
    ElMessage.error('文件读取失败，请重试')
  } finally {
    // 允许重新选择同一个文件（change 事件只在文件变化时触发）
    input.value = ''
  }
}

async function doImportRoster() {
  if (!rosterReady.value) {
    ElMessage.warning('请先选择 CSV 文件')
    return
  }
  const batch = rosterBatch.value.trim()
  if (!batch) {
    ElMessage.warning('请填写批次号')
    return
  }
  if (batch.length > 64) {
    ElMessage.warning('批次号最长 64 个字符')
    return
  }
  rosterImporting.value = true
  rosterError.value = ''
  rosterResult.value = null
  rosterResultBatch.value = ''
  try {
    const res = await importRoster(rosterText.value, batch)
    rosterResult.value = res
    rosterResultBatch.value = batch
    if (res.failed > 0) {
      ElMessage.warning(`导入完成：新增 ${res.inserted}，跳过 ${res.skipped}，失败 ${res.failed}`)
    } else {
      ElMessage.success(`导入完成：新增 ${res.inserted}，跳过 ${res.skipped}`)
    }
  } catch (e) {
    rosterError.value = e instanceof Error ? e.message : '导入失败'
  } finally {
    rosterImporting.value = false
  }
}

watch([reportStatus, reportPage], () => loadReports())
watch(tab, () => {
  if (tab.value === 'reports' && !reports.value.length) loadReports()
  if (tab.value === 'dashboard' && isSuperadmin.value && !dashboard.value) loadDashboard()
  if (tab.value === 'moderation' && !contents.value.length) loadContents()
  if (tab.value === 'tags' && isSuperadmin.value && !tags.value.length) loadTags()
  if (tab.value === 'review' && !reviewItems.value.length) loadReviewQueue()
})
onMounted(() => {
  if (canAccessAdmin.value) loadReports()
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
    v-if="!canAccessAdmin"
    icon="warning"
    title="需要运营或超级管理员权限"
    sub-title="本页面仅对 OPS / SUPERADMIN 角色开放"
  >
    <template #extra>
      <el-button v-if="!auth.isLoggedIn" type="primary" @click="goLogin">去登录</el-button>
      <RouterLink v-else to="/">
        <el-button>回到首页</el-button>
      </RouterLink>
    </template>
  </el-result>

  <template v-else>
    <!-- 页头（CR-203）：本页原先没有标题，直接以 Tab 起头；补上与其余页面一致的页头 -->
    <PageHeader
      title="管理后台"
      :description="isSuperadmin ? '举报工单、内容处置、运营数据、标签与名册管理；仅运营 / 超级管理员可见' : '举报工单、内容处置与内容巡查；仅运营 / 超级管理员可见'"
    >
      <el-tabs v-model="tab">
        <el-tab-pane label="举报工单" name="reports" />
        <el-tab-pane :label="isSuperadmin ? '内容处置台' : '内容检索'" name="moderation" />
        <el-tab-pane v-if="isSuperadmin" label="数据看板" name="dashboard" />
        <el-tab-pane v-if="isSuperadmin" label="标签管理" name="tags" />
        <el-tab-pane label="内容巡查" name="review" />
        <el-tab-pane v-if="isSuperadmin" label="名册导入" name="roster" />
      </el-tabs>
    </PageHeader>

    <!-- ─── 数据看板（F-ADMIN-005 / CR-131）：仅 SUPERADMIN；进入 Tab 才加载 ─── -->
    <el-card v-if="isSuperadmin" v-show="tab === 'dashboard'" shadow="never" class="mb-5">
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
      <template v-else-if="dashboard">
        <!-- KPI 卡：总量做视觉锚点，状态拆分用色点 + 数值，不做整块彩色背景 -->
        <div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <section
            v-for="card in dashboardKpis"
            :key="card.title"
            class="rounded-md border-[0.5px] border-divider p-4"
          >
            <h3 class="text-body font-medium text-ink">{{ card.title }}</h3>
            <div class="mt-2 flex items-baseline gap-2">
              <span class="text-hero font-medium tabular-nums text-ink">{{ card.total }}</span>
              <span class="text-caption text-ink-meta">总数</span>
            </div>
            <ul class="mt-3 space-y-1.5 border-t-[0.5px] border-divider pt-3 text-caption">
              <li
                v-for="item in card.items"
                :key="item.label"
                class="flex items-center justify-between gap-2"
              >
                <span class="inline-flex items-center gap-1.5 text-ink-regular">
                  <span
                    class="h-1.5 w-1.5 flex-none rounded-full"
                    :style="{ background: item.color }"
                    aria-hidden="true"
                  />
                  {{ item.label }}
                </span>
                <span class="tabular-nums text-ink">{{ item.value }}</span>
              </li>
              <li
                v-if="card.extra"
                class="flex items-center justify-between gap-2 pt-1.5 text-ink-meta"
              >
                <span>{{ card.extra.label }}</span>
                <span class="tabular-nums">{{ card.extra.value }}</span>
              </li>
            </ul>
          </section>
        </div>

        <!-- 图表区：横向柱状图看总量，环形图看内容状态；数值与图例同屏 -->
        <div class="mt-4 grid gap-4 lg:grid-cols-2">
          <section class="rounded-md border-[0.5px] border-divider p-4">
            <div class="flex flex-wrap items-baseline justify-between gap-2">
              <h3 class="text-body font-medium text-ink">总量对比</h3>
              <span class="text-caption text-ink-meta">含已下架 / 已注销</span>
            </div>
            <ul class="mt-4 space-y-3">
              <li v-for="bar in totalBars" :key="bar.label">
                <div class="flex items-baseline justify-between gap-3 text-note">
                  <span class="text-ink-regular">{{ bar.label }}</span>
                  <span class="tabular-nums text-ink">{{ bar.value }}</span>
                </div>
                <div class="mt-1.5 h-2 overflow-hidden rounded-full bg-code">
                  <div
                    class="h-full rounded-full bg-primary-strong transition-[width] duration-base ease-standard"
                    :style="{ width: barWidth(bar.value) }"
                  />
                </div>
              </li>
            </ul>
          </section>

          <section class="rounded-md border-[0.5px] border-divider p-4">
            <div class="flex flex-wrap items-baseline justify-between gap-2">
              <h3 class="text-body font-medium text-ink">内容状态</h3>
              <span class="text-caption text-ink-meta">帖子 + 楼层</span>
            </div>
            <div class="mt-4">
              <DonutChart :segments="contentDonut" caption="内容" />
            </div>
          </section>
        </div>
      </template>
    </el-card>

    <!-- ─── 标签管理（F-ADMIN-004 标签侧 / CR-132）：仅 SUPERADMIN；进入 Tab 才加载 ─── -->
    <el-card v-if="isSuperadmin" v-show="tab === 'tags'" shadow="never">
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
            按发布时间倒序；只列未删内容，已下架仍列出可恢复{{ isSuperadmin ? '；处置会记录审计' : '；运营仅可查看，处置需超级管理员' }}
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
                v-if="isSuperadmin"
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

    <!-- ─── 名册导入（F-ACC-004 / CR-210）：仅 SUPERADMIN；CSV 原文提交，后端哈希入库并写审计 ─── -->
    <el-card v-if="isSuperadmin" v-show="tab === 'roster'" shadow="never">
      <template #header>
        <div class="flex flex-wrap items-baseline justify-between gap-2">
          <span class="font-medium">学籍名册导入</span>
          <span class="text-caption text-ink-meta">
            学号哈希入库；导入操作写 ROSTER_IMPORT 审计
          </span>
        </div>
      </template>

      <div class="max-w-[760px]">
        <el-form label-width="90px">
          <el-form-item label="CSV 文件">
            <div class="flex flex-wrap items-center gap-2">
              <el-button @click="pickRosterFile">选择文件</el-button>
              <span v-if="rosterFileName" class="text-note text-ink-regular">
                {{ rosterFileName }} · {{ formatFileSize(rosterFileSize) }} · {{ rosterLineCount }} 行 · {{ rosterEncoding }}
              </span>
              <span v-else class="text-note text-ink-meta">UTF-8 / GBK，≤ 5 MB</span>
              <el-button v-if="rosterFileName" link @click="clearRosterFile">清除</el-button>
            </div>
            <input
              ref="rosterFileEl"
              type="file"
              accept=".csv,text/csv"
              hidden
              aria-label="名册 CSV 文件"
              @change="onRosterFileChange"
            />
          </el-form-item>

          <el-form-item label="批次号">
            <div class="flex flex-wrap items-center gap-2">
              <el-input
                v-model="rosterBatch"
                maxlength="64"
                show-word-limit
                class="w-[320px]"
                placeholder="例如 2026级-秋季-20261110"
              />
              <span class="text-caption text-ink-meta">写入 source_batch，便于追溯</span>
            </div>
          </el-form-item>

          <el-form-item v-if="rosterPreview.length" label="前 5 行">
            <ul class="w-full rounded-sm border-[0.5px] border-divider bg-code px-3 py-2">
              <li
                v-for="(line, index) in rosterPreview"
                :key="index"
                class="truncate font-mono text-caption leading-relaxed text-ink-regular"
              >
                {{ line }}
              </li>
            </ul>
          </el-form-item>

          <el-form-item>
            <el-button
              type="primary"
              :loading="rosterImporting"
              :disabled="!rosterReady"
              @click="doImportRoster"
            >
              开始导入
            </el-button>
          </el-form-item>
        </el-form>

        <el-alert
          v-if="rosterEncoding === 'GBK'"
          type="warning"
          :closable="false"
          show-icon
          class="mb-3"
          title="检测到非 UTF-8 编码，已按 GBK 读取；提交时会统一转为 UTF-8。建议后续另存为 UTF-8 CSV。"
        />

        <el-alert v-if="rosterError" type="error" :closable="false" show-icon class="mb-3" :title="rosterError" />

        <div v-if="rosterResult" role="status" aria-live="polite" class="mb-4 rounded-md border-[0.5px] border-divider p-4">
          <div class="flex flex-wrap items-baseline justify-between gap-2">
            <span class="text-body font-medium text-ink">导入完成</span>
            <span class="text-caption text-ink-meta">批次 {{ rosterResultBatch }}</span>
          </div>
          <dl class="mt-3 grid grid-cols-3 gap-3 text-center">
            <div class="rounded-sm bg-code px-3 py-2">
              <dt class="text-caption text-ink-meta">新增</dt>
              <dd class="mt-1 text-hero font-medium tabular-nums text-ink">{{ rosterResult.inserted }}</dd>
            </div>
            <div class="rounded-sm bg-code px-3 py-2">
              <dt class="text-caption text-ink-meta">跳过（学号已存在）</dt>
              <dd class="mt-1 text-hero font-medium tabular-nums text-ink">{{ rosterResult.skipped }}</dd>
            </div>
            <div class="rounded-sm bg-code px-3 py-2">
              <dt class="text-caption text-ink-meta">失败（格式不合规）</dt>
              <dd class="mt-1 text-hero font-medium tabular-nums text-ink">{{ rosterResult.failed }}</dd>
            </div>
          </dl>
          <p class="mt-3 text-caption leading-body text-ink-meta">
            失败行未入库，可修正后重新导入；重复学号按跳过处理，不会覆盖已有记录。本次操作已写入 ROSTER_IMPORT 审计。
          </p>
        </div>

        <div class="rounded-md border-[0.5px] border-divider p-4 text-caption leading-body text-ink-meta">
          <p class="font-medium text-ink-regular">CSV 格式</p>
          <p class="mt-1">
            每行 <code class="font-mono">学号,姓名[,年级[,专业]]</code>；学号必须 9 位数字，姓名必填，年级 / 专业可空。
          </p>
          <p class="mt-1">
            首行表头可写「学号,姓名」或 Student ID，后端自动跳过；UTF-8 / GBK 均可，逗号分隔，不支持引号包裹的字段。
          </p>
          <p class="mt-1">
            导入始终写名册，不受 <code class="font-mono">campuslink.roster.bypass</code> 开关影响；注册核验是否走名册由后端配置决定。
          </p>
        </div>
      </div>
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
      <template #header>
        <div class="flex flex-wrap items-baseline justify-between gap-2">
          <span class="font-medium">{{ isSuperadmin ? '内容处置台' : '内容检索' }}</span>
          <span v-if="!isSuperadmin" class="text-caption text-ink-meta">运营仅可检索与查看；下架 / 恢复 / 封禁需超级管理员</span>
        </div>
      </template>

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
          <el-table-column label="作者" width="150">
            <template #default="{ row }">
              {{ row.authorNickname }}
              <span class="text-caption text-ink-meta">#{{ row.authorId }}</span>
            </template>
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
          <el-table-column v-if="isSuperadmin" label="操作" width="190" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.authorId"
                type="primary"
                link
                @click="selectAuthorTarget(row as AdminContentItemVo)"
              >处置作者</el-button>
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

      <!-- ─── 用户检索（F-ADMIN-002 / CR-211）：按 id 或昵称找账号，点击填入下方处置目标 ─── -->
      <template v-if="isSuperadmin">
        <el-divider>用户检索</el-divider>
        <div class="px-5">
          <p class="mb-3 text-caption text-ink-meta">
            纯数字按用户 id 精确查，其余按昵称关键字模糊查；最多 50 条，只显示账号基础状态（不含邮箱 / 学号）。
          </p>
          <el-form inline class="gap-y-2">
            <el-form-item label="用户 id / 昵称">
              <el-input
                v-model="userKeyword"
                placeholder="如 123 或 张三"
                clearable
                class="w-[200px]"
                @keyup.enter="loadUsers"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="usersLoading" @click="loadUsers">检索</el-button>
              <el-button @click="resetUserSearch">重置</el-button>
            </el-form-item>
          </el-form>
          <el-alert v-if="usersError" type="error" :closable="false" :title="usersError" class="mb-3" />
          <el-alert
            v-else-if="userTruncated"
            type="warning"
            :closable="false"
            title="匹配超过 50 条，仅显示前 50 条；请输入更精确的关键字"
            class="mb-3"
          />
          <el-table
            v-if="userSearched && userResults.length"
            v-loading="usersLoading"
            :data="userResults"
            size="small"
            class="mb-3"
          >
            <el-table-column prop="id" label="用户 id" width="90" />
            <el-table-column prop="nickname" label="昵称" min-width="120" show-overflow-tooltip />
            <el-table-column label="角色" width="100">
              <template #default="{ row }">
                <el-tag
                  :type="row.role === 'SUPERADMIN' ? 'danger' : row.role === 'OPS' ? 'warning' : 'info'"
                  size="small"
                >
                  {{ row.role === 'SUPERADMIN' ? '超级管理员' : row.role === 'OPS' ? '运营' : '用户' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag
                  :type="row.status === 'ACTIVE' ? 'success' : row.status === 'BANNED' ? 'danger' : 'info'"
                  size="small"
                >
                  {{ row.status === 'ACTIVE' ? '正常' : row.status === 'BANNED' ? '已封禁' : '已注销' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="禁言" width="150">
              <template #default="{ row }">
                <span v-if="isMuted(row as AdminUserItemVo)" class="text-[var(--cl-color-danger-strong)]">
                  至 {{ mutedLabel(row as AdminUserItemVo) }}
                </span>
                <span v-else class="text-ink-meta">—</span>
              </template>
            </el-table-column>
            <el-table-column label="注册时间" width="120">
              <template #default="{ row }">{{ formatRelativeTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="190" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link @click="selectUserTarget(row as AdminUserItemVo)">
                  设为处置目标
                </el-button>
                <el-button
                  v-if="row.role === 'USER' && row.status === 'ACTIVE'"
                  link
                  @click="selectOpsTarget(row as AdminUserItemVo)"
                >填入运营授予</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty
            v-else-if="userSearched && !usersLoading"
            description="未找到匹配用户"
            :image-size="56"
          />
        </div>
      </template>

      <el-divider v-if="isSuperadmin">按 id 直达</el-divider>
      <el-form v-if="isSuperadmin" id="admin-user-actions" label-width="90px" class="max-w-[560px]">
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
        <el-divider />
        <el-form-item label="警告原因">
          <el-input
            v-model="warnReason"
            maxlength="200"
            show-word-limit
            placeholder="将作为站内通知正文发送给用户（最长 200 字）"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="warning" :loading="warning" @click="doWarn">发送警告</el-button>
        </el-form-item>
        <el-divider />
        <el-form-item label="禁言时长">
          <el-select v-model="muteHours" aria-label="禁言时长" class="w-[140px]">
            <el-option :value="1" label="1 小时" />
            <el-option :value="6" label="6 小时" />
            <el-option :value="24" label="24 小时" />
            <el-option :value="72" label="3 天" />
            <el-option :value="168" label="7 天" />
          </el-select>
        </el-form-item>
        <el-form-item label="禁言原因">
          <el-input
            v-model="muteReason"
            maxlength="200"
            show-word-limit
            placeholder="会写入审计与禁言通知（最长 200 字）"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="danger" :loading="muting" @click="doMute">禁言</el-button>
          <el-button :loading="unmuting" @click="doUnmute">解除禁言</el-button>
        </el-form-item>
        <el-divider />
        <el-form-item label="运营用户 id">
          <el-input v-model="opsUserId" placeholder="用户 id（正整数）" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="grantingOps" @click="doGrantOps">授予运营角色</el-button>
        </el-form-item>
      </el-form>
      <p class="px-5 pb-5 text-caption leading-body text-ink-meta">
        <template v-if="isSuperadmin">
          下架即从全站列表 / 搜索 / 热榜 / 详情消失（不碰作者删除墓碑，可恢复）；封禁即时拒绝登录与发帖；
          警告与禁言使用上方同一个用户 id——警告只发站内通知，禁言期间仅内容写入被拒（浏览与登录不受影响）；
          授予运营角色需目标用户重新登录后生效。全部操作写审计日志。
        </template>
        <template v-else>运营可处理举报工单、查看内容巡查与检索结果；处置与封禁由超级管理员执行。</template>
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

/* Tab 移入页头后（CR-203）：EP 的 nav 下划线是 2px 灰线，会与 PageHeader 的 0.5px 收底叠成双线 */
:deep(.el-tabs__nav-wrap::after) {
  display: none;
}
</style>
