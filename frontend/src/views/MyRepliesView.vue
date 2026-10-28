<script setup lang="ts">
/**
 * 我的回帖（F-ACC-007c）：本人楼层时间倒序，条目带父帖定位。
 *
 * <p>**父帖不可见的楼层后端已在 SQL 侧整条过滤**（父帖自删或被下架都不出现），
 * 所以这里的父帖链接**结构上不可能点开才发现 404**——这是它比"先列出再报错"更贵的地方，
 * 也是本仓第三次踩同一个缺陷模式的预防措施。
 * `status` 是**楼层自身**被平台下架的状态，仍出现、仍标出，但父帖链接保持可点。
 */
import { ref } from 'vue'
import { listMyReplies, type MyReplySummaryVo } from '../api/forum'
import { formatRelativeTime } from '../utils/time'
import SvgIcon from '../components/SvgIcon.vue'

const replies = ref<MyReplySummaryVo[]>([])
const total = ref(0)
const page = ref(1)
const size = 20
const loading = ref(false)
const loadError = ref('')

async function loadReplies() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await listMyReplies(page.value, size)
    replies.value = res.list
    total.value = res.total
  } catch (e) {
    replies.value = []
    total.value = 0
    loadError.value = e instanceof Error ? e.message : '回帖加载失败'
  } finally {
    loading.value = false
  }
}

function onPageChange(next: number) {
  page.value = next
  loadReplies()
}

loadReplies()
</script>

<template>
  <!-- TRANSITION-SINGLE-ROOT：App.vue 以 <Transition mode="out-in"> 包路由组件，多根组件（fragment）在该模式下会随机卡死路由切换（CR-091 修复详情页同类问题） -->
  <div>
  <el-card shadow="never" class="mb-5">
    <h2 class="text-title-lg font-medium text-ink">我的回帖</h2>
    <p class="mt-1.5 text-note text-ink-meta">按回帖时间倒序，仅本人可见；所在帖子已消失的回帖不出现</p>
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
        <el-button size="small" @click="loadReplies">重新加载</el-button>
      </el-alert>
    </div>

    <div v-else-if="!replies.length" class="py-10 text-center">
      <p class="text-body text-ink-regular">还没有回过帖</p>
      <RouterLink to="/">
        <el-button type="primary" class="mt-4">去逛逛版块</el-button>
      </RouterLink>
    </div>

    <template v-else>
      <ul>
        <li
          v-for="r in replies"
          :key="r.id"
          class="border-b-[0.5px] border-divider px-5 py-3 last:border-b-0"
        >
          <div class="flex items-center gap-2">
            <el-tag v-if="r.status === 'REMOVED'" type="danger" effect="light" size="small" class="flex-none">
              已下架
            </el-tag>
            <span class="flex-none text-caption text-ink-meta">第 {{ r.floorNo }} 楼</span>
            <RouterLink
              :to="`/post/${r.postId}`"
              class="min-w-0 flex-1 truncate text-body text-ink hover:text-link"
            >
              {{ r.postTitle }}
            </RouterLink>
          </div>

          <p v-if="r.summary" class="mt-1 line-clamp-2 text-note leading-body text-ink-regular">
            {{ r.summary }}
          </p>

          <p class="mt-1.5 text-caption text-ink-meta">{{ formatRelativeTime(r.createdAt) }}</p>
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
      <SvgIcon name="arrow-left" :size="16" />
      返回设置
    </RouterLink>
  </div>
  </div>
</template>
