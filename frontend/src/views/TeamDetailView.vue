<script setup lang="ts">
/**
 * 组队详情（F-TEAM-001 / CR-137）：公开可读；联系方式仅发起人与已加入成员可见。
 *
 * <p>页面态四种：匿名 / 未申请（可申请）/ 待审批 / 已加入；发起人额外看到待审批面板与关闭按钮。
 * 所有写操作成功后重拉详情，不在前端自行推算人数与状态（满员由后端自动迁移）。
 */
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError } from '../api/client'
import {
  applyTeam,
  closeTeam,
  decideTeamApplication,
  getTeam,
  listTeamApplications,
  TEAM_STATUS_LABELS,
  type TeamApplicationVo,
  type TeamDetailVo,
} from '../api/teams'
import { useAuthStore } from '../stores/auth'
import { formatRelativeTime, formatTime } from '../utils/time'
import UserAvatar from '../components/UserAvatar.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const teamId = computed(() => Number(route.params.id))
const detail = ref<TeamDetailVo | null>(null)
const loading = ref(false)
const loadError = ref('')
const notFound = ref(false)

const applications = ref<TeamApplicationVo[]>([])
const applicationTotal = ref(0)
const applicationPage = ref(1)
const applicationSize = 10
const applicationsLoading = ref(false)
const applicationsError = ref('')

const applying = ref(false)
const decidingUserId = ref<number | null>(null)
const closing = ref(false)

const isOwner = computed(() => detail.value?.myRole === 'OWNER')
const isMember = computed(() => detail.value?.myApplicationStatus === 'ACCEPTED')
const canApply = computed(() =>
  auth.isLoggedIn &&
  !isOwner.value &&
  detail.value?.status === 'RECRUITING' &&
  !detail.value?.myApplicationStatus,
)

const applyHint = computed(() => {
  if (!detail.value) return ''
  if (isOwner.value) {
    return detail.value.status === 'CLOSED' ? '组队已关闭' : '你是发起人，可在下方审批申请'
  }
  if (isMember.value) return '你已加入该组队'
  if (detail.value.myApplicationStatus === 'PENDING') return '申请已提交，等待发起人审批'
  if (detail.value.myApplicationStatus === 'REJECTED') return '申请已被拒绝，不能再次申请'
  if (detail.value.status === 'FULL') return '组队已满员'
  if (detail.value.status === 'CLOSED') return '组队已关闭'
  return ''
})

const statusTagType = computed<'success' | 'warning' | 'info'>(() => {
  if (detail.value?.status === 'RECRUITING') return 'success'
  if (detail.value?.status === 'FULL') return 'warning'
  return 'info'
})

const contactHint = computed(() => {
  if (detail.value?.contactVisible) return ''
  if (!auth.isLoggedIn) return '登录并加入后可见联系方式'
  if (detail.value?.myApplicationStatus === 'PENDING') return '发起人通过申请后可见联系方式'
  return '加入后可见联系方式'
})

async function loadDetail() {
  loading.value = true
  loadError.value = ''
  notFound.value = false
  try {
    detail.value = await getTeam(teamId.value)
    if (detail.value.myRole === 'OWNER') {
      await loadApplications(1)
    }
  } catch (e) {
    detail.value = null
    if (e instanceof ApiError && e.code === 6001) {
      notFound.value = true
    } else {
      loadError.value = e instanceof Error ? e.message : '组队加载失败'
    }
  } finally {
    loading.value = false
  }
}

async function loadApplications(next: number) {
  applicationsLoading.value = true
  applicationsError.value = ''
  try {
    const res = await listTeamApplications(teamId.value, next, applicationSize)
    applications.value = res.list
    applicationTotal.value = res.total
    applicationPage.value = res.page
  } catch (e) {
    applications.value = []
    applicationTotal.value = 0
    applicationsError.value = e instanceof Error ? e.message : '申请列表加载失败'
  } finally {
    applicationsLoading.value = false
  }
}

watch(teamId, () => {
  applications.value = []
  applicationTotal.value = 0
  void loadDetail()
}, { immediate: true })

function requireLogin(): boolean {
  if (auth.isLoggedIn) return true
  router.push({ path: '/login', query: { redirect: route.fullPath } })
  return false
}

async function onApply() {
  if (!requireLogin()) return
  applying.value = true
  try {
    await applyTeam(teamId.value)
    ElMessage.success('已提交申请，等待发起人审批')
    await loadDetail()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '申请失败')
  } finally {
    applying.value = false
  }
}

async function onDecide(userId: number, decision: 'ACCEPT' | 'REJECT') {
  const label = decision === 'ACCEPT' ? '接受' : '拒绝'
  try {
    await ElMessageBox.confirm(`确认${label}该申请？`, `${label}申请`, {
      confirmButtonText: label,
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  decidingUserId.value = userId
  try {
    await decideTeamApplication(teamId.value, userId, decision)
    ElMessage.success(`已${label}`)
    await loadDetail()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '审批失败')
  } finally {
    decidingUserId.value = null
  }
}

async function onClose() {
  try {
    await ElMessageBox.confirm('关闭后不可重开，其他同学将无法再申请。确认关闭？', '关闭组队', {
      confirmButtonText: '关闭组队',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  closing.value = true
  try {
    await closeTeam(teamId.value)
    ElMessage.success('组队已关闭')
    await loadDetail()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '关闭失败')
  } finally {
    closing.value = false
  }
}
</script>

<template>
  <!-- TRANSITION-SINGLE-ROOT -->
  <div>
    <el-card v-if="loading" shadow="never" class="mb-5">
      <el-skeleton animated :rows="4" />
    </el-card>

    <el-card v-else-if="notFound" shadow="never" class="mb-5">
      <el-result icon="warning" title="组队不存在" sub-title="它可能已被关闭或链接失效">
        <template #extra>
          <RouterLink to="/teams">
            <el-button type="primary">回到组队广场</el-button>
          </RouterLink>
        </template>
      </el-result>
    </el-card>

    <el-card v-else-if="loadError" shadow="never" class="mb-5">
      <el-alert type="error" :closable="false" show-icon :title="loadError">
        <el-button size="small" @click="loadDetail">重新加载</el-button>
      </el-alert>
    </el-card>

    <template v-else-if="detail">
      <el-card shadow="never" class="mb-5">
        <div class="flex flex-wrap items-start justify-between gap-3">
          <div class="min-w-0">
            <div class="flex flex-wrap items-center gap-2">
              <h1 class="text-title-lg font-medium text-ink">{{ detail.title }}</h1>
              <el-tag size="small" effect="light">{{ detail.directionLabel }}</el-tag>
              <el-tag size="small" effect="light" :type="statusTagType">
                {{ TEAM_STATUS_LABELS[detail.status] }}
              </el-tag>
            </div>
            <div class="mt-2 flex flex-wrap items-center gap-1.5 text-caption text-ink-meta">
              <UserAvatar :name="detail.ownerNickname" :size="18" />
              <RouterLink :to="`/u/${detail.ownerId}`" class="text-link hover:underline">
                {{ detail.ownerNickname }}
              </RouterLink>
              <span aria-hidden="true">·</span>
              <span>已加入 {{ detail.acceptedCount }}/{{ detail.capacity }} 人</span>
              <template v-if="detail.target">
                <span aria-hidden="true">·</span>
                <span>目标：{{ detail.target }}</span>
              </template>
              <template v-if="detail.deadline">
                <span aria-hidden="true">·</span>
                <span>截止 {{ detail.deadline }}</span>
              </template>
              <span aria-hidden="true">·</span>
              <span>{{ formatRelativeTime(detail.createdAt) }}发布</span>
            </div>
          </div>

          <div class="flex shrink-0 items-center gap-2">
            <el-button v-if="canApply" type="primary" :loading="applying" @click="onApply">
              申请加入
            </el-button>
            <el-button v-else-if="!auth.isLoggedIn && detail.status === 'RECRUITING'" @click="onApply">
              登录后申请
            </el-button>
            <el-button
              v-if="isOwner && detail.status !== 'CLOSED'"
              :loading="closing"
              @click="onClose"
            >
              关闭组队
            </el-button>
          </div>
        </div>

        <p v-if="applyHint" class="mt-3 text-note text-ink-meta">{{ applyHint }}</p>
      </el-card>

      <el-card shadow="never" class="mb-5">
        <h2 class="text-title font-medium text-ink">组队简介</h2>
        <!-- 服务端 MarkdownRenderer + jsoup 白名单，与帖子详情同一条安全链路 -->
        <div class="markdown-body mt-3" v-html="detail.descriptionHtml" />
      </el-card>

      <el-card shadow="never" class="mb-5">
        <h2 class="text-title font-medium text-ink">成员（{{ detail.members.length }}）</h2>
        <ul class="mt-3 flex flex-wrap gap-3">
          <li
            v-for="m in detail.members"
            :key="m.userId"
            class="flex items-center gap-2 rounded-md border-[0.5px] border-line px-3 py-1.5"
          >
            <UserAvatar :name="m.nickname" :src="m.avatarUrl" :size="22" />
            <RouterLink :to="`/u/${m.userId}`" class="text-body text-ink hover:text-link">
              {{ m.nickname }}
            </RouterLink>
            <el-tag v-if="m.role === 'OWNER'" size="small" effect="plain">发起人</el-tag>
          </li>
        </ul>
      </el-card>

      <el-card shadow="never" class="mb-5">
        <h2 class="text-title font-medium text-ink">联系方式</h2>
        <p v-if="detail.contactVisible" class="mt-2 select-all text-body text-ink">{{ detail.contact }}</p>
        <p v-else class="mt-2 text-note text-ink-meta">{{ contactHint }}</p>
      </el-card>

      <el-card v-if="isOwner" shadow="never" :body-style="{ padding: 0 }">
        <div class="border-b-[0.5px] border-divider px-5 py-3">
          <h2 class="text-title font-medium text-ink">待审批申请（{{ applicationTotal }}）</h2>
          <p class="mt-1 text-note text-ink-meta">接受后对方立即成为成员；拒绝后对方不能再次申请</p>
        </div>

        <div v-if="applicationsLoading" class="px-5 py-4">
          <el-skeleton animated :rows="2" />
        </div>
        <div v-else-if="applicationsError" class="p-5">
          <el-alert type="error" :closable="false" show-icon :title="applicationsError">
            <el-button size="small" @click="loadApplications(applicationPage)">重新加载</el-button>
          </el-alert>
        </div>
        <div v-else-if="!applications.length" class="px-5 py-8 text-center text-note text-ink-meta">
          暂无待审批申请
        </div>
        <ul v-else>
          <li
            v-for="a in applications"
            :key="a.userId"
            class="flex flex-wrap items-center gap-2 border-b-[0.5px] border-divider px-5 py-3 last:border-b-0"
          >
            <UserAvatar :name="a.nickname" :src="a.avatarUrl" :size="24" />
            <RouterLink :to="`/u/${a.userId}`" class="text-body text-ink hover:text-link">
              {{ a.nickname }}
            </RouterLink>
            <span class="text-caption text-ink-meta">{{ formatTime(a.appliedAt) }}申请</span>
            <span class="ml-auto flex items-center gap-2">
              <el-button
                size="small"
                type="primary"
                :loading="decidingUserId === a.userId"
                @click="onDecide(a.userId, 'ACCEPT')"
              >
                接受
              </el-button>
              <el-button
                size="small"
                :loading="decidingUserId === a.userId"
                @click="onDecide(a.userId, 'REJECT')"
              >
                拒绝
              </el-button>
            </span>
          </li>
        </ul>

        <div v-if="applicationTotal > applicationSize" class="flex justify-center border-t-[0.5px] border-divider py-3">
          <el-pagination
            background
            layout="prev, pager, next"
            :total="applicationTotal"
            :page-size="applicationSize"
            :current-page="applicationPage"
            @current-change="loadApplications"
          />
        </div>
      </el-card>
    </template>
  </div>
</template>
