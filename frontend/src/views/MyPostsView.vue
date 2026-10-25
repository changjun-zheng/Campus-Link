<script setup lang="ts">
/**
 * 我的帖子（F-ACC-007b）：本人发帖时间倒序。
 *
 * <p>两件事由后端定死、前端只负责如实呈现：**自己删除的帖不出现**（墓碑过滤），
 * **被平台下架的帖仍出现并带 `status=REMOVED`**（作者要能答出"少了哪一帖"）。
 * 后者**不渲染成链接**——下架帖的详情是 `404 / 3001`，"列出来了却点不进"是本仓抓过两次的缺陷模式
 * （CR-065 D-1 / CR-066 §2 6b），不能在前端再造第三次。
 */
import { ref } from 'vue'
import { listMyPosts, type MyPostSummaryVo } from '../api/forum'
import { boardNameOf } from '../constants/boards'
import { formatRelativeTime } from '../utils/time'
import SvgIcon from '../components/SvgIcon.vue'

const posts = ref<MyPostSummaryVo[]>([])
const total = ref(0)
const page = ref(1)
const size = 20
const loading = ref(false)
const loadError = ref('')

async function loadPosts() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await listMyPosts(page.value, size)
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

loadPosts()
</script>

<template>
  <!-- TRANSITION-SINGLE-ROOT：App.vue 以 <Transition mode="out-in"> 包路由组件，多根组件（fragment）在该模式下会随机卡死路由切换（CR-091 修复详情页同类问题） -->
  <div>
  <el-card shadow="never" class="mb-5">
    <h2 class="text-title-lg font-medium text-ink">我的帖子</h2>
    <p class="mt-1.5 text-note text-ink-meta">按发布时间倒序，仅本人可见；被平台下架的帖子会标出但打不开</p>
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
        </div>
      </template>
    </el-skeleton>

    <div v-else-if="loadError" class="p-5">
      <el-alert type="error" :closable="false" show-icon :title="loadError">
        <el-button size="small" @click="loadPosts">重新加载</el-button>
      </el-alert>
    </div>

    <div v-else-if="!posts.length" class="py-10 text-center">
      <p class="text-body text-ink-regular">还没有发过帖子</p>
      <RouterLink to="/publish">
        <el-button type="primary" class="mt-4">去发帖</el-button>
      </RouterLink>
    </div>

    <template v-else>
      <ul>
        <li
          v-for="p in posts"
          :key="p.id"
          class="border-b-[0.5px] border-divider px-5 py-3 last:border-b-0"
        >
          <div class="flex items-center gap-2">
            <!-- 匿名标记（CR-139）：本人可见，详情 / 列表对外仍显示「匿名用户」 -->
            <el-tag v-if="p.anonymous" type="info" effect="plain" size="small" class="flex-none">
              匿名
            </el-tag>
            <el-tag v-if="p.status === 'REMOVED'" type="danger" effect="light" size="small" class="flex-none">
              已下架
            </el-tag>
            <RouterLink
              v-if="p.status !== 'REMOVED'"
              :to="`/post/${p.id}`"
              class="min-w-0 flex-1 truncate text-title-sm font-medium text-ink hover:text-link"
            >
              {{ p.title }}
            </RouterLink>
            <!-- 下架条目是纯文本：不给 href，键盘与中键也点不开 -->
            <span v-else class="min-w-0 flex-1 truncate text-title-sm font-medium text-ink-meta">
              {{ p.title }}
            </span>
          </div>

          <p v-if="p.summary" class="mt-1 line-clamp-2 text-note leading-body text-ink-regular">
            {{ p.summary }}
          </p>

          <div class="mt-1.5 flex flex-wrap items-center gap-1.5 text-caption text-ink-meta">
            <RouterLink :to="`/board/${p.boardCode}`" class="text-link hover:underline">
              {{ boardNameOf(p.boardCode) }}
            </RouterLink>
            <span aria-hidden="true">·</span>
            <span>{{ formatRelativeTime(p.createdAt) }}</span>
            <span v-if="p.status === 'REMOVED'" aria-hidden="true">·</span>
            <span v-if="p.status === 'REMOVED'">由平台处置，作者不可自助恢复</span>
          </div>
        </li>
      </ul>
      <div v-if="total > size" class="flex justify-center px-5 py-4">
        <el-pagination
          layout="prev, pager, next, total"
          :total="total"
          :page-size="size"
          :current-page="page"
          @current-change="onPageChange"
        />
      </div>
    </template>
  </el-card>

  <div class="mt-4 flex justify-center">
    <RouterLink to="/settings" class="flex items-center gap-1 text-note text-link hover:underline">
      <SvgIcon name="arrow-left" :size="14" />
      返回设置
    </RouterLink>
  </div>
  </div>
</template>
