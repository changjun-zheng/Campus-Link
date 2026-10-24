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
  getAdminDashboard,
  listReports,
  moderatePost,
  moderateReply,
  setUserStatus,
  type AdminDashboardVo,
  type AdminReportVo,
} from '../api/admin'
import { useAuthStore } from '../stores/auth'
import { formatRelativeTime } from '../utils/time'

const router = useRouter()
const auth = useAuthStore()

const isSuperadmin = computed(() => auth.user?.role === 'SUPERADMIN')

const tab = ref<'reports' | 'moderation' | 'dashboard'>('reports')

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

/* ─── 处置台：按 id 直达（搜索接口属 P1，MVP 用 id 精确操作） ─── */
const targetType = ref<'POST' | 'REPLY'>('POST')
const targetIdInput = ref('')
const moderating = ref(false)

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

watch([reportStatus, reportPage], () => loadReports())
watch(tab, () => {
  if (tab.value === 'reports' && !reports.value.length) loadReports()
  if (tab.value === 'dashboard' && !dashboard.value) loadDashboard()
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

    <!-- ─── 内容处置台 ─── -->
    <el-card v-show="tab === 'moderation'" shadow="never">
      <template #header><span class="font-medium">内容处置台</span></template>

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
