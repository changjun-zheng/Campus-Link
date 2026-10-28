<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { searchPosts, type PostSummaryVo } from '../api/forum'
import { BOARDS } from '../constants/boards'
import PostListItem from '../components/PostListItem.vue'
import SvgIcon from '../components/SvgIcon.vue'

const route = useRoute()
const router = useRouter()

/** 后端 ngram 分词下限：单字不受理（1001），前端同口径先拦一次，省一次往返 */
const KEYWORD_MIN_CHARS = 2

const keyword = ref(String(route.query.keyword || ''))
const boardCode = ref(String(route.query.boardCode || ''))
const days = ref<number | ''>(route.query.days ? Number(route.query.days) : '')
const page = ref(Number(route.query.page) || 1)
const size = 20

const posts = ref<PostSummaryVo[]>([])
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
/** 已执行过搜索（区分"还没搜"与"搜了没结果"两种空态） */
const searched = ref(false)
const inputRef = ref<{ focus: () => void } | null>(null)

const timeWindows = [
  { label: '全部时间', value: '' as number | '' },
  { label: '近 7 天', value: 7 },
  { label: '近 30 天', value: 30 },
  { label: '近 90 天', value: 90 },
]

const effectiveKeyword = computed(() => keyword.value.trim())

async function runSearch(nextPage = 1) {
  const kw = effectiveKeyword.value
  if (kw.length < KEYWORD_MIN_CHARS) {
    posts.value = []
    total.value = 0
    searched.value = false
    loadError.value = `关键词至少 ${KEYWORD_MIN_CHARS} 个字`
    return
  }
  loadError.value = ''
  loading.value = true
  page.value = nextPage
  syncQuery(kw)
  try {
    const res = await searchPosts(kw, {
      boardCode: boardCode.value || undefined,
      days: days.value === '' ? undefined : Number(days.value),
      page: nextPage,
      size,
    })
    posts.value = res.list
    total.value = res.total
    searched.value = true
  } catch (e) {
    posts.value = []
    total.value = 0
    searched.value = true
    loadError.value = e instanceof Error ? e.message : '搜索失败'
  } finally {
    loading.value = false
  }
}

/** 搜索条件写回 URL：可分享、可后退；只改 query 不换路由 */
function syncQuery(kw: string) {
  const query: Record<string, string> = { keyword: kw }
  if (boardCode.value) query.boardCode = boardCode.value
  if (days.value !== '') query.days = String(days.value)
  if (page.value > 1) query.page = String(page.value)
  router.replace({ path: '/search', query })
}

function onSubmit() {
  runSearch(1)
}

watch([boardCode, days], () => {
  if (effectiveKeyword.value.length >= KEYWORD_MIN_CHARS) {
    runSearch(1)
  }
})

onMounted(async () => {
  if (effectiveKeyword.value.length >= KEYWORD_MIN_CHARS) {
    await runSearch(page.value)
  } else {
    await nextTick()
    inputRef.value?.focus()
  }
})
</script>

<template>
  <!-- TRANSITION-SINGLE-ROOT：App.vue 以 <Transition mode="out-in"> 包路由组件，多根组件（fragment）在该模式下会随机卡死路由切换（CR-091 修复详情页同类问题） -->
  <div>
  <nav class="mb-3 flex items-center gap-1.5 text-note" aria-label="面包屑">
    <RouterLink to="/">首页</RouterLink>
    <span class="text-ink-meta">/</span>
    <span class="text-ink-regular">搜索</span>
  </nav>

  <el-card shadow="never" class="mb-5">
    <h2 class="text-title-lg font-medium text-ink">站内搜索</h2>
    <p class="mt-1.5 text-note text-ink-meta">按标题、正文与标签检索，至少 2 个字；结果按相关度排序</p>
    <div class="mt-4 flex flex-col gap-3 md:flex-row md:items-center">
      <el-input
        ref="inputRef"
        v-model="keyword"
        class="md:flex-1"
        placeholder="搜索帖子标题 / 正文 / 标签（至少 2 个字）"
        maxlength="50"
        clearable
        @keyup.enter="onSubmit"
      >
        <template #prefix>
          <SvgIcon name="search" :size="16" />
        </template>
      </el-input>
      <div class="flex shrink-0 items-center gap-2">
        <el-select v-model="boardCode" class="w-[130px]" placeholder="全部版块">
          <el-option label="全部版块" value="" />
          <el-option v-for="b in BOARDS" :key="b.code" :label="b.name" :value="b.code" />
        </el-select>
        <el-select v-model="days" class="w-[120px]" placeholder="全部时间">
          <el-option v-for="w in timeWindows" :key="String(w.value)" :label="w.label" :value="w.value" />
        </el-select>
        <el-button type="primary" @click="onSubmit">搜索</el-button>
      </div>
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
          <div class="mt-2 flex items-center gap-2">
            <el-skeleton-item variant="text" style="width: 64px" />
            <el-skeleton-item variant="text" style="width: 64px" />
          </div>
        </div>
      </template>
    </el-skeleton>

    <div v-else-if="loadError && !searched" class="p-5">
      <el-alert type="warning" :closable="false" show-icon :title="loadError" />
    </div>

    <div v-else-if="loadError" class="p-5">
      <el-alert type="error" :closable="false" show-icon :title="loadError">
        <el-button size="small" @click="onSubmit">重新搜索</el-button>
      </el-alert>
    </div>

    <div v-else-if="!searched" class="py-10 text-center">
      <p class="text-body text-ink-regular">输入关键词搜索帖子标题、正文与标签</p>
    </div>

    <div v-else-if="!posts.length" class="py-10 text-center">
      <p class="text-body text-ink-regular">没有找到与「{{ effectiveKeyword }}」相关的帖子</p>
      <RouterLink to="/publish">
        <el-button type="primary" class="mt-4">去提问</el-button>
      </RouterLink>
    </div>

    <template v-else>
      <div class="border-b-[0.5px] border-divider px-5 py-2.5 text-note text-ink-meta">
        共 {{ total }} 条结果
      </div>
      <ul>
        <PostListItem v-for="p in posts" :key="p.id" :post="p" show-board :highlight="effectiveKeyword" />
      </ul>
      <div v-if="total > size" class="flex justify-center px-5 py-4">
        <el-pagination
          layout="prev, pager, next"
          :total="total"
          :page-size="size"
          :current-page="page"
          @current-change="runSearch"
        />
      </div>
    </template>
  </el-card>
  </div>
</template>
