<script setup lang="ts">
/**
 * 私信会话列表（F-SOC-003 / CR-135）：`/messages`。
 *
 * <p>口径由后端定死：需登录；按最后一条消息时间倒序；条目含对方昵称 / 头像、最后消息前 50 字与未读数。
 * 已注销对方仍在列表里（历史消息可读），昵称由服务端回落「已注销用户」。
 */
import { ref } from 'vue'
import { listConversations, type ConversationVo } from '../api/messages'
import SvgIcon from '../components/SvgIcon.vue'
import UserAvatar from '../components/UserAvatar.vue'
import { formatRelativeTime } from '../utils/time'

const items = ref<ConversationVo[]>([])
const total = ref(0)
const page = ref(1)
const size = 20
const loading = ref(false)
const loadError = ref('')

async function load(next = 1) {
  loading.value = true
  loadError.value = ''
  try {
    const res = await listConversations(next, size)
    items.value = res.list
    total.value = res.total
    page.value = res.page
  } catch (e) {
    items.value = []
    total.value = 0
    loadError.value = e instanceof Error ? e.message : '会话加载失败'
  } finally {
    loading.value = false
  }
}

function onPageChange(next: number) {
  void load(next)
}

load(1)
</script>

<template>
  <!-- TRANSITION-SINGLE-ROOT：App.vue 以 <Transition mode="out-in"> 包路由组件，多根会卡死路由切换（CR-091） -->
  <div>
    <el-card shadow="never" class="mb-5">
      <h2 class="text-title-lg font-medium text-ink">私信</h2>
      <p class="mt-1.5 text-note text-ink-meta">与同学一对一交流，仅本人可见；按最后一条消息时间倒序</p>
    </el-card>

    <el-card shadow="never" :body-style="{ padding: 0 }">
      <el-skeleton v-if="loading" animated class="px-5 pt-4">
        <template #template>
          <div v-for="i in 4" :key="i" class="flex items-center gap-3 border-b-[0.5px] border-divider py-3 last:border-b-0">
            <el-skeleton-item variant="circle" style="width: 40px; height: 40px" />
            <div class="min-w-0 flex-1">
              <el-skeleton-item variant="text" style="width: 28%" />
              <el-skeleton-item variant="text" class="mt-2" style="width: 72%" />
            </div>
          </div>
        </template>
      </el-skeleton>

      <div v-else-if="loadError" class="p-5">
        <el-alert type="error" :closable="false" show-icon :title="loadError">
          <el-button size="small" @click="load(1)">重新加载</el-button>
        </el-alert>
      </div>

      <div v-else-if="!items.length" class="py-12 text-center">
        <p class="text-body text-ink-regular">还没有私信</p>
        <p class="mt-1.5 text-note text-ink-meta">在他人主页点「私信」即可开始一对一交流</p>
      </div>

      <template v-else>
        <ul>
          <li v-for="c in items" :key="c.peerId" class="border-b-[0.5px] border-divider last:border-b-0">
            <RouterLink
              :to="`/messages/${c.peerId}`"
              class="flex items-center gap-3 px-5 py-3 transition-colors duration-fast ease-standard hover:bg-bg-hover"
            >
              <UserAvatar :src="c.peerAvatarUrl" :name="c.peerNickname" :size="40" />
              <div class="min-w-0 flex-1">
                <div class="flex items-center justify-between gap-3">
                  <span class="min-w-0 truncate text-title-sm font-medium text-ink">{{ c.peerNickname }}</span>
                  <span class="flex-none text-caption text-ink-meta">{{ formatRelativeTime(c.lastMessageAt) }}</span>
                </div>
                <div class="mt-0.5 flex items-center justify-between gap-3">
                  <span class="min-w-0 truncate text-note text-ink-regular">
                    {{ c.lastMessagePreview || '（无消息摘要）' }}
                  </span>
                  <span
                    v-if="c.unreadCount > 0"
                    class="min-w-[18px] flex-none rounded-full bg-primary px-1.5 text-center text-caption text-white tabular-nums"
                    :aria-label="`${c.unreadCount} 条未读`"
                  >
                    {{ c.unreadCount > 99 ? '99+' : c.unreadCount }}
                  </span>
                </div>
              </div>
            </RouterLink>
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
      <RouterLink to="/" class="flex items-center gap-1 text-note text-link hover:underline">
        <SvgIcon name="arrow-left" :size="14" />
        回首页
      </RouterLink>
    </div>
  </div>
</template>
