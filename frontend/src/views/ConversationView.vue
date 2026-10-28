<script setup lang="ts">
/**
 * 私信聊天页（F-SOC-003 / CR-135）：`/messages/:peerId`。
 *
 * <p>消息接口是**最新在前**的倒序分页，本页每收到一页就反转成时间正序渲染；
 * 「加载更早」取下一页并 prepend，同时按滚动高度差补偿 scrollTop，避免视图跳动。
 *
 * <p>进入即已读：首屏加载完成后调 `POST /messages/{peerId}/read` 并刷新顶栏角标；
 * 发送成功把返回消息直接追加到气泡流（方案 D5：不靠轮询等它回来）。
 *
 * <p>拉黑语义由后端定死：历史消息仍可读，发送才 `403 / 4002`；页面对发送失败只如实提示错误文案。
 */
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getUserProfile, type UserProfileVo } from '../api/user'
import { listMessages, markConversationRead, sendMessage, type MessageVo } from '../api/messages'
import { refreshUnreadMessages } from '../composables/useUnreadMessages'
import { useAuthStore } from '../stores/auth'
import SvgIcon from '../components/SvgIcon.vue'
import UserAvatar from '../components/UserAvatar.vue'
import { formatTime } from '../utils/time'

const route = useRoute()
const auth = useAuthStore()

/** 路径参数不是正整数即视为无效会话（`/messages/abc` 与 `/messages/0` 不打后端） */
const peerId = computed(() => {
  const raw = Number(Array.isArray(route.params.peerId) ? route.params.peerId[0] : route.params.peerId)
  return Number.isInteger(raw) && raw > 0 ? raw : 0
})

const peer = ref<UserProfileVo | null>(null)
const peerNickname = computed(() => peer.value?.nickname || '已注销用户')

const messages = ref<MessageVo[]>([])
const total = ref(0)
const page = ref(1)
const size = 20
const loading = ref(false)
const loadingOlder = ref(false)
const loadError = ref('')
const draft = ref('')
const sending = ref(false)
const listRef = ref<HTMLElement | null>(null)

const invalidPeer = computed(() => peerId.value === 0)
const hasMore = computed(() => messages.value.length < total.value)

/** 请求序号守卫：快速切换 peer 时丢弃陈旧响应（含已读请求之后的角标刷新） */
let reqSeq = 0

function isMine(message: MessageVo): boolean {
  return message.senderId === auth.user?.id
}

function scrollToBottom() {
  const el = listRef.value
  if (el) el.scrollTop = el.scrollHeight
}

async function markRead(seq: number) {
  try {
    await markConversationRead(peerId.value)
    if (seq !== reqSeq) return
    await refreshUnreadMessages()
  } catch {
    // 静默：已读是提示不是主链路，下一次轮询自然恢复
  }
}

async function loadAll() {
  const seq = ++reqSeq
  messages.value = []
  total.value = 0
  page.value = 1
  loadError.value = ''
  peer.value = null
  draft.value = ''
  if (peerId.value === 0) {
    loading.value = false
    return
  }
  loading.value = true
  try {
    // 昵称失败不阻断消息主体：对方可能已注销，历史消息仍应可读
    const [res, profile] = await Promise.all([
      listMessages(peerId.value, 1, size),
      getUserProfile(peerId.value).catch(() => null),
    ])
    if (seq !== reqSeq) return
    messages.value = res.list.slice().reverse()
    total.value = res.total
    page.value = res.page
    peer.value = profile
    await nextTick()
    scrollToBottom()
    void markRead(seq)
  } catch (e) {
    if (seq !== reqSeq) return
    loadError.value = e instanceof Error ? e.message : '消息加载失败'
  } finally {
    if (seq === reqSeq) loading.value = false
  }
}

async function loadOlder() {
  if (loadingOlder.value || !hasMore.value || peerId.value === 0) return
  const seq = reqSeq
  const el = listRef.value
  const previousHeight = el?.scrollHeight ?? 0
  const previousTop = el?.scrollTop ?? 0
  loadingOlder.value = true
  try {
    const res = await listMessages(peerId.value, page.value + 1, size)
    if (seq !== reqSeq) return
    messages.value = [...res.list.slice().reverse(), ...messages.value]
    total.value = res.total
    page.value = res.page
    await nextTick()
    if (el) el.scrollTop = el.scrollHeight - previousHeight + previousTop
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '更早消息加载失败')
  } finally {
    loadingOlder.value = false
  }
}

async function send() {
  if (sending.value || peerId.value === 0) return
  const content = draft.value.trim()
  if (!content) return
  if (content.length > 1000) {
    ElMessage.warning('单条私信最多 1000 字')
    return
  }
  sending.value = true
  try {
    const message = await sendMessage(peerId.value, content)
    messages.value.push(message)
    total.value += 1
    draft.value = ''
    await nextTick()
    scrollToBottom()
    void refreshUnreadMessages()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '发送失败')
  } finally {
    sending.value = false
  }
}

watch(peerId, () => void loadAll(), { immediate: true })
onBeforeUnmount(() => {
  reqSeq += 1
})
</script>

<template>
  <!-- TRANSITION-SINGLE-ROOT：App.vue 以 <Transition mode="out-in"> 包路由组件，多根会卡死路由切换（CR-091） -->
  <div>
    <el-card v-if="invalidPeer" shadow="never">
      <div class="py-10 text-center">
        <p class="text-title-sm font-medium text-ink">会话不存在</p>
        <p class="mt-1.5 text-note text-ink-meta">链接有误，或对方账号不可用</p>
        <RouterLink to="/messages">
          <el-button type="primary" class="mt-4">回私信列表</el-button>
        </RouterLink>
      </div>
    </el-card>

    <template v-else>
      <el-card shadow="never" class="mb-4" :body-style="{ padding: '12px 20px' }">
        <div class="flex items-center gap-3">
          <RouterLink to="/messages" class="flex items-center gap-1 text-note text-link hover:underline">
            <SvgIcon name="arrow-left" :size="16" />
            私信
          </RouterLink>
          <span class="text-line" aria-hidden="true">/</span>
          <UserAvatar :name="peerNickname" :size="28" />
          <RouterLink
            v-if="peer"
            :to="`/u/${peer.id}`"
            class="min-w-0 truncate text-title-sm font-medium text-ink hover:text-link"
          >
            {{ peerNickname }}
          </RouterLink>
          <span v-else class="min-w-0 truncate text-title-sm font-medium text-ink">{{ peerNickname }}</span>
        </div>
      </el-card>

      <el-card shadow="never" :body-style="{ padding: 0 }">
        <div
          ref="listRef"
          class="max-h-[calc(100vh-320px)] min-h-[320px] overflow-y-auto px-5 py-4"
          aria-live="polite"
        >
          <el-skeleton v-if="loading" animated>
            <template #template>
              <div class="space-y-4">
                <el-skeleton-item variant="text" style="width: 42%" />
                <el-skeleton-item variant="text" class="ml-auto" style="width: 52%" />
                <el-skeleton-item variant="text" style="width: 36%" />
              </div>
            </template>
          </el-skeleton>

          <div v-else-if="loadError" class="py-4">
            <el-alert type="error" :closable="false" show-icon :title="loadError">
              <el-button size="small" @click="loadAll">重新加载</el-button>
            </el-alert>
          </div>

          <template v-else>
            <div v-if="hasMore" class="flex justify-center pb-3">
              <el-button size="small" text :loading="loadingOlder" @click="loadOlder">
                加载更早的消息
              </el-button>
            </div>

            <div v-if="!messages.length" class="py-12 text-center">
              <p class="text-body text-ink-regular">还没有消息</p>
              <p class="mt-1.5 text-note text-ink-meta">发第一条私信打个招呼吧</p>
            </div>

            <div v-else class="flex flex-col gap-4">
              <div
                v-for="m in messages"
                :key="m.id"
                class="flex flex-col"
                :class="isMine(m) ? 'items-end' : 'items-start'"
              >
                <div
                  class="max-w-[76%] whitespace-pre-wrap break-words rounded-lg px-3 py-2 text-body leading-body"
                  :class="isMine(m)
                    ? 'bg-primary-soft text-ink'
                    : 'border-[0.5px] border-line bg-page text-ink'"
                >
                  {{ m.content }}
                </div>
                <span class="mt-1 text-caption text-ink-meta">{{ formatTime(m.createdAt) }}</span>
              </div>
            </div>
          </template>
        </div>

        <div class="border-t-[0.5px] border-divider p-4">
          <el-input
            v-model="draft"
            type="textarea"
            :autosize="{ minRows: 1, maxRows: 4 }"
            maxlength="1000"
            show-word-limit
            resize="none"
            placeholder="输入私信，Enter 发送，Shift+Enter 换行"
            @keydown.enter.exact.prevent="send"
          />
          <div class="mt-3 flex items-center justify-between gap-3">
            <span class="text-caption text-ink-meta">纯文本，不渲染 Markdown</span>
            <el-button type="primary" size="small" :loading="sending" :disabled="!draft.trim()" @click="send">
              <SvgIcon name="send" :size="16" class="mr-1" />
              发送
            </el-button>
          </div>
        </div>
      </el-card>
    </template>
  </div>
</template>
