<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { listFollowingPosts, listPosts, type PostSort, type PostSummaryVo } from '../api/forum'
import { BOARDS } from '../constants/boards'
import { useAuthStore } from '../stores/auth'
import PostListItem from '../components/PostListItem.vue'
import SvgIcon from '../components/SvgIcon.vue'

const auth = useAuthStore()

/** 排序（F-FORUM-003 + CR-074）：latest = 最新，hot = 热榜（后端 sort=hot），following = 关注 Feed（仅登录可见） */
const sort = ref<PostSort | 'following'>('latest')
const posts = ref<PostSummaryVo[]>([])
const total = ref(0)
const page = ref(1)
const size = 10
const loading = ref(false)
const loadError = ref('')

/** 右栏热榜 Top5（CR-074 数据源，sort=hot） */
const hotTop5 = ref<PostSummaryVo[]>([])

async function loadHotTop() {
  try {
    const res = await listPosts(1, 5, undefined, 'hot')
    hotTop5.value = res.list
  } catch {
    hotTop5.value = []
  }
}

async function loadList() {
  loading.value = true
  loadError.value = ''
  try {
    const res =
      sort.value === 'following'
        ? await listFollowingPosts(page.value, size)
        : await listPosts(page.value, size, undefined, sort.value)
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
  loadList()
}

watch(sort, () => {
  page.value = 1
  loadList()
})

onMounted(() => {
  loadList()
  loadHotTop()
})

const myEntries = [
  { label: '我的收藏', to: '/favorites', icon: 'bookmark' },
  { label: '我的帖子', to: '/me/posts', icon: 'article' },
]
</script>

<template>
  <!-- 三栏布局（参考 CodeNav / 掘金）：左版块导航 · 中信息流 · 右热榜 -->
  <div class="flex items-start gap-5">
    <!-- ─── 左栏：版块导航（lg 以上显示） ─── -->
    <aside class="sticky top-4 hidden w-[200px] flex-none lg:block">
      <el-card shadow="never" :body-style="{ padding: '8px' }">
        <nav aria-label="版块导航">
          <RouterLink
            v-for="b in BOARDS"
            :key="b.code"
            :to="`/board/${b.code}`"
            class="flex items-center gap-2.5 rounded-md px-3 py-2.5 text-body text-ink-regular transition-colors duration-fast ease-standard hover:bg-bg-hover"
          >
            <span class="inline-flex h-7 w-7 flex-none items-center justify-center rounded-sm bg-primary-soft text-primary">
              <SvgIcon :name="b.icon" :size="15" />
            </span>
            {{ b.name }}
          </RouterLink>
        </nav>
        <el-divider class="my-2" />
        <nav aria-label="我的入口">
          <RouterLink
            v-for="m in myEntries"
            :key="m.to"
            :to="m.to"
            class="flex items-center gap-2.5 rounded-md px-3 py-2.5 text-body text-ink-regular transition-colors duration-fast ease-standard hover:bg-bg-hover"
          >
            <span class="inline-flex h-7 w-7 flex-none items-center justify-center rounded-sm bg-code text-ink-meta">
              <SvgIcon :name="m.icon" :size="15" />
            </span>
            {{ m.label }}
          </RouterLink>
        </nav>
      </el-card>
    </aside>

    <!-- ─── 中栏：信息流 ─── -->
    <main class="min-w-0 flex-1">
      <el-card shadow="never" :body-style="{ padding: 0 }">
        <template #header>
          <div class="flex items-center justify-between">
            <span class="font-medium">
              {{ sort === 'hot' ? '全站热门' : sort === 'following' ? '我的关注' : '全站最新' }}
            </span>
            <el-radio-group v-model="sort" size="small" aria-label="帖子排序">
              <el-radio-button value="latest">最新</el-radio-button>
              <el-radio-button value="hot">热门</el-radio-button>
              <el-radio-button v-if="auth.isLoggedIn" value="following">关注</el-radio-button>
            </el-radio-group>
          </div>
        </template>

        <el-skeleton v-if="loading" animated class="px-5 pt-4">
          <template #template>
            <div v-for="i in 5" :key="i" class="border-b-[0.5px] border-divider py-3 last:border-b-0">
              <el-skeleton-item variant="text" style="width: 56%" />
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
            <el-button size="small" @click="loadList">重新加载</el-button>
          </el-alert>
        </div>

        <div v-else-if="!posts.length" class="py-10 text-center">
          <p class="text-body text-ink-regular">
            {{ sort === 'following' ? '还没有关注任何作者，去帖子详情页点「＋ 关注」吧' : '还没有帖子，来发第一帖吧' }}
          </p>
          <RouterLink to="/publish">
            <el-button type="primary" class="mt-4">去发第一帖</el-button>
          </RouterLink>
        </div>

        <template v-else>
          <ul>
            <PostListItem v-for="p in posts" :key="p.id" :post="p" show-board />
          </ul>
          <div class="flex justify-center px-5 py-4">
            <el-pagination
              v-if="total > size"
              layout="prev, pager, next"
              :total="total"
              :page-size="size"
              :current-page="page"
              @current-change="onPageChange"
            />
          </div>
        </template>
      </el-card>
    </main>

    <!-- ─── 右栏：热榜 + 站点卡（xl 以上显示） ─── -->
    <aside class="sticky top-4 hidden w-[300px] flex-none xl:block">
      <el-card shadow="never" :body-style="{ padding: '16px' }">
        <template #header>
          <div class="flex items-center justify-between">
            <span class="font-medium">全站热榜</span>
            <RouterLink to="/board/qna" class="text-caption text-link hover:underline">更多</RouterLink>
          </div>
        </template>
        <ol v-if="hotTop5.length">
          <li
            v-for="(p, i) in hotTop5"
            :key="p.id"
            class="flex items-start gap-2 border-b-[0.5px] border-divider py-2 last:border-b-0"
          >
            <span
              class="w-5 flex-none text-center text-title-sm font-medium"
              :class="i < 3 ? 'text-primary' : 'text-ink-meta'"
            >
              {{ i + 1 }}
            </span>
            <RouterLink :to="`/post/${p.id}`" class="min-w-0 flex-1 truncate text-note text-ink-regular hover:text-link">
              {{ p.title }}
            </RouterLink>
          </li>
        </ol>
        <p v-else class="text-note text-ink-meta">热榜计算中，稍后再来看</p>
      </el-card>

      <el-card shadow="never" class="mt-4" :body-style="{ padding: '16px' }">
        <h3 class="text-title-sm font-medium text-ink">关于 Campus-Link</h3>
        <p class="mt-2 text-note leading-body text-ink-regular">
          重庆工程学院计算机专业学生的垂直交流论坛——问有所答、学有同伴、求职有路。
        </p>
        <RouterLink to="/publish">
          <el-button type="primary" size="small" class="mt-3">我要提问</el-button>
        </RouterLink>
      </el-card>
    </aside>
  </div>
</template>
