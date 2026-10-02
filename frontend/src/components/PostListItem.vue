<script setup lang="ts">
/**
 * 帖子列表项统一组件（CR-052，闭环 ui-guideline §5.1 的 P0 挂账）
 * 首页 / 版块 / 搜索 / 收藏四处列表共用，消除重复结构。
 * - 标题是真实 RouterLink（保留 href：可中键新开 / 右键复制 / 键盘聚焦），行 hover 通栏底色；
 * - 内部版块链接独立可点（@click.stop 阻止冒泡到标题的场景不存在，二者为并列元素）；
 * - 分隔线与内边距在此统一，父卡片 body padding 置 0。
 *
 * CR-085：卡片重排为 CSDN / 掘金式——作者行（头像 + 昵称 + 时间）提到标题上方，
 * 标签 chips 与互动计数（点赞 / 回复 / 阅读）沉到摘要下方，信息层级与参考站一致。
 */
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { togglePostLike, type PostSummaryVo } from '../api/forum'
import { boardNameOf } from '../constants/boards'
import { useAuthStore } from '../stores/auth'
import { formatRelativeTime } from '../utils/time'
import SvgIcon from './SvgIcon.vue'
import UserAvatar from './UserAvatar.vue'

const props = withDefaults(
  defineProps<{
    post: PostSummaryVo
    /** 是否显示版块入口（版块页不显示自己；首页 / 搜索 / 收藏显示） */
    showBoard?: boolean
    /** 是否显示摘要（默认显示，两行截断） */
    showSummary?: boolean
  }>(),
  { showBoard: false, showSummary: true },
)

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const liking = ref(false)

/** 未登录时不发请求（CR-080）：后端 4001 的文案是错误提示、不是「去登录」的引导 */
function requireLoginOrRedirect(): boolean {
  if (auth.isLoggedIn) return true
  router.push({ path: '/login', query: { redirect: route.fullPath } })
  return false
}

/** 就地点赞：以响应的 {active, count} 回填这一行（与详情页同一份口径），不整表重拉 */
async function onLike() {
  if (liking.value || !requireLoginOrRedirect()) return
  liking.value = true
  try {
    const res = await togglePostLike(props.post.id)
    props.post.likeCount = res.count
    props.post.likedByMe = res.active
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '点赞失败')
  } finally {
    liking.value = false
  }
}
</script>

<template>
  <li
    class="border-b-[0.5px] border-divider px-5 py-3 transition-colors duration-fast ease-standard last:border-b-0 hover:bg-bg-hover"
  >
    <div class="flex gap-3">
      <div class="min-w-0 flex-1">
        <!-- 作者行（CSDN / 掘金式）：头像 + 昵称 + 时间在标题上方（CR-085） -->
        <div class="flex items-center gap-1.5 text-caption text-ink-meta">
          <UserAvatar :name="post.authorNickname" :size="20" />
          <span class="text-ink-regular">{{ post.authorNickname }}</span>
          <span aria-hidden="true">·</span>
          <span>{{ formatRelativeTime(post.createdAt) }}</span>
        </div>

        <div class="mt-1 flex items-center gap-2">
          <el-tag v-if="post.accepted" type="success" effect="light" size="small" class="flex-none">
            已采纳
          </el-tag>
          <RouterLink
            :to="`/post/${post.id}`"
            class="min-w-0 flex-1 truncate text-title-sm font-medium text-ink hover:text-link"
          >
            {{ post.title }}
          </RouterLink>
          <span
            class="flex flex-none items-center gap-0.5 rounded-sm bg-code px-1.5 py-px text-caption text-ink-meta"
            :title="`${post.replyCount} 条回复`"
          >
            <SvgIcon name="message-2" :size="12" />
            {{ post.replyCount }}
          </span>
        </div>

        <p
          v-if="showSummary && post.summary"
          class="mt-1 line-clamp-2 text-note leading-body text-ink-regular"
        >
          {{ post.summary }}
        </p>

        <div class="mt-1.5 flex flex-wrap items-center gap-1.5 text-caption text-ink-meta">
          <template v-if="showBoard">
            <RouterLink :to="`/board/${post.boardCode}`" class="text-link hover:underline">
              {{ post.boardName || boardNameOf(post.boardCode) }}
            </RouterLink>
            <span aria-hidden="true">·</span>
          </template>
          <!--
            就地点赞（CR-080）：用真 button 而非 span+click——键盘可聚焦、aria-pressed 可读出状态。
            按钮不继承字体，故显式 font-inherit + text-caption，与同行 span 同尺寸同色阶。
          -->
          <button
            type="button"
            class="inline-flex cursor-pointer items-center gap-0.5 border-0 bg-transparent p-0 font-inherit text-caption transition-colors duration-fast ease-standard"
            :class="post.likedByMe ? 'font-medium text-primary' : 'text-ink-meta hover:text-link'"
            :aria-pressed="post.likedByMe"
            :aria-label="post.likedByMe ? '取消点赞' : '点赞'"
            :title="post.likedByMe ? '取消点赞' : '点赞'"
            :disabled="liking"
            @click="onLike"
          >
            <SvgIcon name="thumb-up" :size="12" />
            {{ post.likeCount }}
          </button>
          <span aria-hidden="true">·</span>
          <span class="inline-flex items-center gap-0.5" :title="`${post.replyCount} 条回复`">
            <SvgIcon name="message-2" :size="12" />
            {{ post.replyCount }}
          </span>
          <span aria-hidden="true">·</span>
          <span class="inline-flex items-center gap-0.5" title="阅读数">
            <SvgIcon name="eye" :size="12" />
            {{ post.viewCount }}
          </span>
          <template v-if="post.tags?.length">
            <span aria-hidden="true">·</span>
            <el-tag v-for="t in post.tags.slice(0, 3)" :key="t" size="small" effect="plain" class="flex-none">
              {{ t }}
            </el-tag>
          </template>
        </div>
      </div>
      <!-- 封面缩略图（CR-074）：无图帖不占位；仅 https 外链（服务端提取时已限定） -->
      <img
        v-if="post.coverUrl"
        :src="post.coverUrl"
        alt=""
        loading="lazy"
        class="h-16 w-24 flex-none rounded-md border-[0.5px] border-line object-cover max-md:hidden"
      />
    </div>
  </li>
</template>
