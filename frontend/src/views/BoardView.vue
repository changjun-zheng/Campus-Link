<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import type { IconName } from '../assets/icons'
import { listBoards, listPosts, type BoardVo, type PostSummaryVo } from '../api/forum'
import { BOARD_MAP, type BoardCode } from '../constants/boards'
import PostListItem from '../components/PostListItem.vue'
import SvgIcon from '../components/SvgIcon.vue'

const route = useRoute()
const code = computed(() => String(route.params.code || ''))
/** 版块图标取本地固定 6 版块常量（接口 BoardVo 不含图标），非法 code 兜底问号气泡 */
const boardIcon = computed<IconName>(
  () => BOARD_MAP[code.value as BoardCode]?.icon ?? 'message-circle',
)

const board = ref<BoardVo | undefined>()
const posts = ref<PostSummaryVo[]>([])
const total = ref(0)
const page = ref(1)
const size = 20
const loading = ref(false)
const loadError = ref('')

async function loadBoards() {
  try {
    const list = await listBoards()
    board.value = list.find((b) => b.code === code.value)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '版块信息加载失败')
  }
}

async function loadPosts() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await listPosts(page.value, size, code.value)
    posts.value = res.list
    total.value = res.total
  } catch (e) {
    posts.value = []
    total.value = 0
    loadError.value = e instanceof Error ? e.message : '帖子加载失败'
  } finally {
    loading.value = false
  }
}

function onPageChange(next: number) {
  page.value = next
  loadPosts()
}

watch(
  code,
  () => {
    page.value = 1
    loadBoards()
    loadPosts()
  },
  { immediate: true },
)
</script>

<template>
  <!-- TRANSITION-SINGLE-ROOT：App.vue 以 <Transition mode="out-in"> 包路由组件，多根组件（fragment）在该模式下会随机卡死路由切换（CR-091 修复详情页同类问题） -->
  <div>
  <nav class="mb-3 flex items-center gap-1.5 text-note" aria-label="面包屑">
    <RouterLink to="/">首页</RouterLink>
    <span class="text-ink-meta">/</span>
    <span class="text-ink-regular">{{ board?.name || code }}</span>
  </nav>

  <el-card shadow="never" class="mb-4">
    <div class="flex items-center justify-between gap-3">
      <div class="flex min-w-0 items-center gap-3.5">
        <span :class="['cl-board-icon', `cl-board-icon--${code}`]">
          <SvgIcon :name="boardIcon" :size="22" />
        </span>
        <div class="min-w-0">
          <h2 class="mb-0.5 text-title-lg font-medium text-ink">{{ board?.name || code }}</h2>
          <p class="text-note text-ink-meta">{{ board?.description || '' }}</p>
        </div>
      </div>
      <RouterLink :to="`/publish?board=${code}`" class="flex-none">
        <el-button type="primary">发帖</el-button>
      </RouterLink>
    </div>
  </el-card>

  <el-card shadow="never" :body-style="{ padding: 0 }">
    <el-skeleton v-if="loading" animated class="px-5 pt-4">
      <template #template>
        <div
          v-for="i in 4"
          :key="i"
          class="border-b-[0.5px] border-divider py-3 last:border-b-0"
        >
          <el-skeleton-item variant="text" style="width: 46%" />
          <el-skeleton-item variant="text" class="mt-2" style="width: 100%" />
          <el-skeleton-item variant="text" class="mt-1" style="width: 72%" />
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
        <el-button size="small" @click="loadPosts">重新加载</el-button>
      </el-alert>
    </div>

    <div v-else-if="!posts.length" class="py-10 text-center">
      <p class="text-body text-ink-regular">本版块还没有帖子，来发第一帖吧</p>
      <RouterLink :to="`/publish?board=${code}`">
        <el-button type="primary" class="mt-4">去发第一帖</el-button>
      </RouterLink>
    </div>

    <template v-else>
      <ul>
        <PostListItem v-for="p in posts" :key="p.id" :post="p" />
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
    </template>
  </el-card>
  </div>
</template>
