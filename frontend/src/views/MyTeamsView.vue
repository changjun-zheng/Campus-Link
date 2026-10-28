<script setup lang="ts">
/**
 * 我的组队（F-TEAM-001 / CR-137）：两个视角。
 * ① 我发起的：带待审批数，点进详情即可审批；② 我申请的：含待审批 / 已加入 / 已拒绝三种状态，
 * **被拒的仍列出**（用户有权知道申请过什么，藏掉会让历史对不上）。
 */
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listMyTeams, TEAM_STATUS_LABELS, type MyTeamVo } from '../api/teams'
import { formatRelativeTime } from '../utils/time'
import SvgIcon from '../components/SvgIcon.vue'

const route = useRoute()
const router = useRouter()

type Role = 'owner' | 'member'

const role = ref<Role>(route.query.role === 'member' ? 'member' : 'owner')
const teams = ref<MyTeamVo[]>([])
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
    const res = await listMyTeams(role.value, next, size)
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

watch(role, (next) => {
  page.value = 1
  void router.replace({ query: { ...route.query, role: next } })
  void loadPage(1)
}, { immediate: true })

function onPageChange(next: number) {
  void loadPage(next)
}

function applicationLabel(status: MyTeamVo['myApplicationStatus']): string {
  if (status === 'PENDING') return '待审批'
  if (status === 'ACCEPTED') return '已加入'
  return '已拒绝'
}

function statusTagType(s: MyTeamVo['status']): 'success' | 'warning' | 'info' {
  if (s === 'RECRUITING') return 'success'
  if (s === 'FULL') return 'warning'
  return 'info'
}
</script>

<template>
  <!-- TRANSITION-SINGLE-ROOT -->
  <div>
    <el-card shadow="never" class="mb-5">
      <div class="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 class="text-title-lg font-medium text-ink">我的组队</h2>
          <p class="mt-1.5 text-note text-ink-meta">
            我发起的组队可在这里审批申请；我申请的组队保留待审批 / 已加入 / 已拒绝状态。
          </p>
        </div>
        <RouterLink to="/teams/new">
          <el-button type="primary">
            <SvgIcon name="pencil" :size="13" class="mr-1" />
            发起组队
          </el-button>
        </RouterLink>
      </div>

      <nav class="mt-3 flex items-center gap-1" aria-label="我的组队视角">
        <button
          type="button"
          class="relative cursor-pointer px-3 py-1.5 text-body transition-colors duration-fast ease-standard"
          :class="role === 'owner' ? 'font-medium text-primary-strong' : 'text-ink-regular hover:text-link'"
          :aria-pressed="role === 'owner'"
          @click="role = 'owner'"
        >
          我发起的
          <span
            aria-hidden="true"
            class="inset-x-3 -bottom-px absolute h-0.5 rounded-full transition-opacity duration-fast ease-standard"
            :class="role === 'owner' ? 'bg-primary-strong opacity-100' : 'opacity-0'"
          />
        </button>
        <button
          type="button"
          class="relative cursor-pointer px-3 py-1.5 text-body transition-colors duration-fast ease-standard"
          :class="role === 'member' ? 'font-medium text-primary-strong' : 'text-ink-regular hover:text-link'"
          :aria-pressed="role === 'member'"
          @click="role = 'member'"
        >
          我申请的
          <span
            aria-hidden="true"
            class="inset-x-3 -bottom-px absolute h-0.5 rounded-full transition-opacity duration-fast ease-standard"
            :class="role === 'member' ? 'bg-primary-strong opacity-100' : 'opacity-0'"
          />
        </button>
      </nav>
    </el-card>

    <el-card shadow="never" :body-style="{ padding: 0 }">
      <el-skeleton v-if="loading" animated class="px-5 pt-4">
        <template #template>
          <div v-for="i in 3" :key="i" class="border-b-[0.5px] border-divider py-4 last:border-b-0">
            <el-skeleton-item variant="text" style="width: 42%" />
            <el-skeleton-item variant="text" class="mt-2" style="width: 88%" />
          </div>
        </template>
      </el-skeleton>

      <div v-else-if="loadError" class="p-5">
        <el-alert type="error" :closable="false" show-icon :title="loadError">
          <el-button size="small" @click="loadPage(page)">重新加载</el-button>
        </el-alert>
      </div>

      <div v-else-if="!teams.length" class="py-12 text-center">
        <el-empty :description="role === 'owner' ? '还没有发起过组队' : '还没有申请过组队'">
          <RouterLink :to="role === 'owner' ? '/teams/new' : '/teams'">
            <el-button type="primary">{{ role === 'owner' ? '发起组队' : '去组队广场' }}</el-button>
          </RouterLink>
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
                <el-tag v-if="role === 'member'" size="small" effect="plain">
                  {{ applicationLabel(t.myApplicationStatus) }}
                </el-tag>
                <el-tag
                  v-if="role === 'owner' && t.pendingCount > 0"
                  size="small"
                  type="warning"
                  effect="light"
                >
                  待审批 {{ t.pendingCount }}
                </el-tag>
              </div>

              <p v-if="t.target" class="mt-1 text-note text-ink-regular">目标：{{ t.target }}</p>

              <div class="mt-2 flex flex-wrap items-center gap-1.5 text-caption text-ink-meta">
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
