<script setup lang="ts">
import { ref } from 'vue'
import { listNotifications, markAllRead, markNotificationRead, type NotificationVo } from '../api/notifications'
import { refreshUnreadCount } from '../composables/useUnreadNotifications'
import { formatRelativeTime } from '../utils/time'

const items = ref<NotificationVo[]>([])
const total = ref(0)
const page = ref(1)
const size = 20
const loading = ref(false)
const loadError = ref('')
/** null = 全部；true = 只看未读（后端 unread 参数三态） */
const onlyUnread = ref(false)
const marking = ref(false)

async function loadNotifications() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await listNotifications({
      unread: onlyUnread.value ? true : undefined,
      page: page.value,
      size,
    })
    items.value = res.list
    total.value = res.total
  } catch (e) {
    items.value = []
    total.value = 0
    loadError.value = e instanceof Error ? e.message : '通知加载失败'
  } finally {
    loading.value = false
  }
}

function onFilterChange() {
  page.value = 1
  loadNotifications()
}

function onPageChange(next: number) {
  page.value = next
  loadNotifications()
}

async function readAll() {
  marking.value = true
  try {
    await markAllRead()
    // 角标与本页状态都要跟上：unread-count 是另一条查询，不靠 30s 轮询自然收敛
    await Promise.all([refreshUnreadCount(), loadNotifications()])
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '操作失败')
  } finally {
    marking.value = false
  }
}

/**
 * 点击单条通知即视为已读（CR-078 功能 C）。
 *
 * <p><b>乐观更新</b>：先把本地这条置为已读（未读底色与小圆点立刻消失），再 fire-and-forget 通知服务端。
 * 刻意**不 await**：可跳转的行点完 RouterLink 就会导航，等响应只会让跳转变卡；失败也不回滚
 * （角标与列表下次刷新自然恢复真实状态），所以异常在这里吞掉、不弹 ElMessage——
 * 一条没标上的已读不值得打断用户阅读帖子。
 *
 * <p><b>角标必须显式刷新</b>：路由切换时 composable 也会补一次读，但那次读很可能跑在
 * POST /read 落库**之前**，于是角标仍旧计数、要等 30s 轮询才收敛，故放在标记成功之后再来一次。
 */
function onActivate(n: NotificationVo) {
  if (n.read) return
  n.read = true
  void markNotificationRead(n.id)
    .then(async () => {
      await refreshUnreadCount()
      // 「只看未读」视图下这条已不再符合筛选条件，重拉一次才不自相矛盾
      if (onlyUnread.value) await loadNotifications()
    })
    .catch(() => {
      // 静默：已读是提示不是主链路，见上文说明
    })
}

/** 动作短语：type 决定动词，targetType 决定对象是帖子还是楼层 */
function actionOf(n: NotificationVo): string {
  if (n.type === 'reply') return '回复了你的帖子'
  if (n.type === 'quote') return '引用了你的回复'
  if (n.type === 'accept') return '采纳了你的回复'
  if (n.type === 'favorite') return '收藏了你的帖子'
  if (n.type === 'content_removed') {
    return n.targetType === 'POST' ? '下架了你的帖子' : '下架了你的回复'
  }
  if (n.type === 'content_restored') {
    return n.targetType === 'POST' ? '恢复了你的帖子' : '恢复了你的回复'
  }
  return n.targetType === 'POST' ? '点赞了你的帖子' : '点赞了你的回复'
}

/** 内容处置通知的主体是平台，不展示管理员昵称（其余类型展示触发人） */
function actorOf(n: NotificationVo): string {
  return n.type === 'content_removed' || n.type === 'content_restored' ? '平台' : n.actorNickname
}

/** 楼层类通知带 floor=<replyId> 让详情页定位楼层；原帖已删（服务端不下发 postId）时不给跳转 */
function targetOf(n: NotificationVo) {
  if (!n.postId) return null
  return n.targetType === 'REPLY'
    ? { path: `/post/${n.postId}`, query: { floor: String(n.targetId) } }
    : { path: `/post/${n.postId}` }
}

loadNotifications()
</script>

<template>
  <!-- TRANSITION-SINGLE-ROOT：App.vue 以 <Transition mode="out-in"> 包路由组件，多根组件（fragment）在该模式下会随机卡死路由切换（CR-091 修复详情页同类问题） -->
  <div>
  <el-card shadow="never" class="mb-5">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h2 class="text-title-lg font-medium text-ink">通知中心</h2>
        <p class="mt-1.5 text-note text-ink-meta">回复、引用、采纳、点赞、收藏与内容处置的站内提醒，仅本人可见</p>
      </div>
      <div class="flex items-center gap-3">
        <el-radio-group v-model="onlyUnread" @change="onFilterChange">
          <el-radio-button :value="false">全部</el-radio-button>
          <el-radio-button :value="true">未读</el-radio-button>
        </el-radio-group>
        <el-button text type="primary" :loading="marking" @click="readAll">全部已读</el-button>
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
          <el-skeleton-item variant="text" class="mt-2" style="width: 72%" />
        </div>
      </template>
    </el-skeleton>

    <div v-else-if="loadError" class="p-5">
      <el-alert type="error" :closable="false" show-icon :title="loadError">
        <el-button size="small" @click="loadNotifications">重新加载</el-button>
      </el-alert>
    </div>

    <div v-else-if="!items.length" class="py-10 text-center">
      <p class="text-body text-ink-regular">{{ onlyUnread ? '没有未读通知' : '还没有收到通知' }}</p>
      <RouterLink to="/">
        <el-button type="primary" class="mt-4">去逛逛版块</el-button>
      </RouterLink>
    </div>

    <template v-else>
      <ul>
        <!--
          整行可点：链接的 ::after 铺满整个 li（stretched-link），于是行内任何位置都是一个**真链接**
          ——保留原生语义（Tab 可聚焦、Ctrl / 中键新标签页打开），不用把 li 改成 div+click 那种不可聚焦的假按钮。
          li 上的 @click 只管“已读”，导航交给链接自己完成（事件冒泡不阻止默认行为）。
        -->
        <li
          v-for="n in items"
          :key="n.id"
          class="relative flex items-start gap-2.5 border-b-[0.5px] border-divider px-5 py-3 transition-colors duration-fast ease-standard last:border-b-0"
          :class="n.read ? 'hover:bg-bg-hover' : 'cursor-pointer bg-primary-soft'"
          @click="onActivate(n)"
        >
          <!-- 未读用小圆点占位，保证已读 / 未读行首对齐（不为对齐再用 invisible 字符） -->
          <span
            class="mt-2 h-1.5 w-1.5 shrink-0 rounded-full"
            :class="n.read ? 'bg-transparent' : 'bg-primary'"
          />
          <div class="min-w-0 flex-1">
            <p class="text-body text-ink-regular">
              <span class="font-medium text-ink">{{ actorOf(n) }}</span>
              {{ actionOf(n) }}
              <template v-if="n.floorNo">#{{ n.floorNo }} 楼</template>
            </p>
            <!-- after:inset-0 把点击区拉到整行（定位祖先是 relative 的 li）；hover 因此也覆盖整行 -->
            <RouterLink
              v-if="targetOf(n)"
              :to="targetOf(n)!"
              class="mt-1 block truncate text-title-sm text-link hover:underline after:absolute after:inset-0 after:content-['']"
            >
              {{ n.postTitle }}
            </RouterLink>
            <p v-else class="mt-1 truncate text-title-sm text-ink-meta">{{ n.postTitle }}</p>
            <p class="mt-1 text-caption text-ink-meta">{{ formatRelativeTime(n.createdAt) }}</p>
          </div>
          <!--
            原帖已删 / 已下架的行没有链接可铺，整行对键盘用户就不可达了；给它一个真的按钮，
            否则这类通知只能靠“全部已读”清掉。已读后自动消失，不给列表添噪。
          -->
          <el-button
            v-if="!targetOf(n) && !n.read"
            text
            size="small"
            type="primary"
            class="ml-auto shrink-0 self-center"
            @click.stop="onActivate(n)"
          >
            标为已读
          </el-button>
        </li>
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
