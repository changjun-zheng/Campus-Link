<script setup lang="ts">
/**
 * 组队广场（F-TEAM-001 / CR-137）：公开可读的招募列表。
 *
 * <p>三条口径由后端定死、这里只如实呈现：① 默认只列「招募中」；② 列表不含联系方式；
 * ③ 匿名可浏览，发起组队 / 申请加入需登录（入口处引导登录并带回跳）。
 */
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  listTeams,
  TEAM_DIRECTIONS,
  TEAM_STATUS_LABELS,
  type TeamStatusCode,
  type TeamSummaryVo,
} from '../api/teams'
import { useAuthStore } from '../stores/auth'
import { formatRelativeTime } from '../utils/time'
import SvgIcon from '../components/SvgIcon.vue'
import UserAvatar from '../components/UserAvatar.vue'

const router = useRouter()
const auth = useAuthStore()

const direction = ref('')
const status = ref<TeamStatusCode>('RECRUITING')
const statusTabs: TeamStatusCode[] = ['RECRUITING', 'FULL', 'CLOSED']

const teams = ref<TeamSummaryVo[]>([])
const total = ref(0)
const page = ref(1)
const size = 20
const loading = ref(false)
const loadError = ref('')

let reqSeq = 0

async function loadPage(next: number) {
  const seq = ++reqSeq
  loading.value = true
  loadError.value = ''
  try {
    const res = await listTeams({
      direction: direction.value || undefined,
      status: status.value,
      page: next,
      size,
    })
    if (seq !== reqSeq) return
    teams.value = res.list
    total.value = res.total
    page.value = res.page
  } catch (e) {
    if (seq !== reqSeq) return
    teams.value = []
    total.value = 0
    loadError.value = e instanceof Error ? e.message : '组队加载失败'
  } finally {
    if (seq === reqSeq) loading.value = false
  }
}

watch([direction, status], () => {
  page.value = 1
  void loadPage(1)
}, { immediate: true })

function onPageChange(next: number) {
  void loadPage(next)
}

function goCreate() {
  if (!auth.isLoggedIn) {
    router.push({ path: '/login', query: { redirect: '/teams/new' } })
    return
  }
  router.push('/teams/new')
}

function statusTagType(s: TeamStatusCode): 'success' | 'warning' | 'info' {
  if (s === 'RECRUITING') return 'success'
  if (s === 'FULL') return 'warning'
  return 'info'
}
</script>

<template>
  <!-- TRANSITION-SINGLE-ROOT：App.vue 以 <Transition mode="out-in"> 包路由组件，多根会卡死路由切换（CR-091） -->
  <div>
    <el-card shadow="never" class="mb-5">
      <div class="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 class="text-title-lg font-medium text-ink">组队广场</h2>
          <p class="mt-1.5 text-note text-ink-meta">
            发布招募集结，申请加入后由发起人审批；满员自动停止招募。联系方式仅发起人与已加入成员可见。
          </p>
        </div>
        <div class="flex shrink-0 items-center gap-2">
          <RouterLink to="/teams/mine">
            <el-button>我的组队</el-button>
          </RouterLink>
          <el-button type="primary" @click="goCreate">
            <SvgIcon name="pencil" :size="13" class="mr-1" />
            发起组队
          </el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="mb-5">
      <div class="flex flex-wrap items-center gap-3">
        <el-select v-model="direction" placeholder="全部方向" clearable class="w-[150px]">
          <el-option label="全部方向" value="" />
          <el-option v-for="d in TEAM_DIRECTIONS" :key="d.code" :label="d.label" :value="d.code" />
        </el-select>

        <nav class="flex items-center gap-1" aria-label="组队状态">
          <button
            v-for="s in statusTabs"
            :key="s"
            type="button"
            class="relative cursor-pointer px-3 py-1.5 text-body transition-colors duration-fast ease-standard"
            :class="status === s ? 'font-medium text-primary' : 'text-ink-regular hover:text-link'"
            :aria-pressed="status === s"
            @click="status = s"
          >
            {{ TEAM_STATUS_LABELS[s] }}
            <span
              aria-hidden="true"
              class="inset-x-3 -bottom-px absolute h-0.5 rounded-full transition-opacity duration-fast ease-standard"
              :class="status === s ? 'bg-primary opacity-100' : 'opacity-0'"
            />
          </button>
        </nav>
      </div>
    </el-card>

    <el-card shadow="never" :body-style="{ padding: 0 }">
      <el-skeleton v-if="loading" animated class="px-5 pt-4">
        <template #template>
          <div v-for="i in 4" :key="i" class="border-b-[0.5px] border-divider py-4 last:border-b-0">
            <el-skeleton-item variant="text" style="width: 40%" />
            <el-skeleton-item variant="text" class="mt-2" style="width: 100%" />
            <el-skeleton-item variant="text" class="mt-1" style="width: 66%" />
          </div>
        </template>
      </el-skeleton>

      <div v-else-if="loadError" class="p-5">
        <el-alert type="error" :closable="false" show-icon :title="loadError">
          <el-button size="small" @click="loadPage(page)">重新加载</el-button>
        </el-alert>
      </div>

      <div v-else-if="!teams.length" class="py-12 text-center">
        <el-empty description="还没有符合条件的组队">
          <el-button type="primary" @click="goCreate">发起第一支组队</el-button>
        </el-empty>
      </div>

      <template v-else>
        <ul>
          <li
            v-for="t in teams"
            :key="t.id"
            class="border-b-[0.5px] border-divider px-5 py-4 last:border-b-0 hover:bg-page"
          >
            <RouterLink :to="`/teams/${t.id}`" class="block">
              <div class="flex flex-wrap items-center gap-2">
                <h3 class="min-w-0 flex-1 truncate text-title-sm font-medium text-ink hover:text-link">
                  {{ t.title }}
                </h3>
                <el-tag size="small" effect="light">{{ t.directionLabel }}</el-tag>
                <el-tag size="small" effect="light" :type="statusTagType(t.status)">
                  {{ TEAM_STATUS_LABELS[t.status] }}
                </el-tag>
              </div>

              <p v-if="t.target" class="mt-1 text-note text-ink-regular">目标：{{ t.target }}</p>

              <div class="mt-2 flex flex-wrap items-center gap-1.5 text-caption text-ink-meta">
                <UserAvatar :name="t.ownerNickname" :src="undefined" :size="18" />
                <span>{{ t.ownerNickname }}</span>
                <span aria-hidden="true">·</span>
                <span>已加入 {{ t.acceptedCount }}/{{ t.capacity }} 人</span>
                <template v-if="t.deadline">
                  <span aria-hidden="true">·</span>
                  <span>截止 {{ t.deadline }}</span>
                </template>
                <span aria-hidden="true">·</span>
                <span>{{ formatRelativeTime(t.createdAt) }}</span>
              </div>
            </RouterLink>
          </li>
        </ul>

        <div v-if="total > size" class="flex justify-center border-t-[0.5px] border-divider py-3">
          <el-pagination
            background
            layout="prev, pager, next"
            :total="total"
            :page-size="size"
            :current-page="page"
            @current-change="onPageChange"
          />
        </div>
      </template>
    </el-card>
  </div>
</template>
