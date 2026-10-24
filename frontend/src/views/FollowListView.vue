<script setup lang="ts">
/**
 * 粉丝 / 关注列表（F-SOC-002 补齐 / CR-130）：两条路由复用同一组件，按 `route.name` 切换视角。
 *
 * <p>三条口径由后端定死、这里只如实呈现：① 列表需登录（匿名由路由守卫拦到登录页带回跳）；
 * ② 条目已剔除已注销账号与本人已拉黑账号（服务端 SQL 侧过滤，`total` 同源）；
 * ③ 条目只给 `id / nickname / major / bio`，**不内嵌关注按钮**（方案 D4）——关注动作在主页完成，
 * 避免列表逐条携带 viewer 关系与 N+1 请求。
 */
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { listFollowers, listFollowing, type FollowUserVo } from '../api/follow'
import { getUserProfile } from '../api/user'
import SvgIcon from '../components/SvgIcon.vue'
import UserAvatar from '../components/UserAvatar.vue'

const route = useRoute()

const targetId = computed(() => Number(route.params.id))
/** 路由名决定视角：/u/:id/following → following，其余（含 /followers）→ followers */
const mode = computed<'followers' | 'following'>(() =>
  route.name === 'follow-following' ? 'following' : 'followers',
)
const modeLabel = computed(() => (mode.value === 'followers' ? '粉丝' : '关注'))
const modeHint = '按关注时间倒序；已注销账号与已被你拉黑的账号不在此列出'

const tabs = computed(() => [
  { to: `/u/${targetId.value}/followers`, label: '粉丝', active: mode.value === 'followers' },
  { to: `/u/${targetId.value}/following`, label: '关注', active: mode.value === 'following' },
])

const targetNickname = ref('')
const users = ref<FollowUserVo[]>([])
const total = ref(0)
const page = ref(1)
const size = 20
const loading = ref(false)
const loadError = ref('')

/** 路由切换（含粉丝 ↔ 关注）时的请求序号：后发请求覆盖先发，防止旧响应写回 */
let reqSeq = 0

async function loadPage(next: number) {
  const seq = ++reqSeq
  loading.value = true
  loadError.value = ''
  try {
    const res =
      mode.value === 'followers'
        ? await listFollowers(targetId.value, next, size)
        : await listFollowing(targetId.value, next, size)
    if (seq !== reqSeq) return
    users.value = res.items
    total.value = res.total
    page.value = res.page
  } catch (e) {
    if (seq !== reqSeq) return
    users.value = []
    total.value = 0
    loadError.value = e instanceof Error ? e.message : '列表加载失败'
  } finally {
    if (seq === reqSeq) loading.value = false
  }
}

/** 昵称只用于标题：拉不到（目标 404）不阻断列表加载——列表端点自己有 2007 错误态 */
async function loadTargetNickname() {
  try {
    const profile = await getUserProfile(targetId.value)
    targetNickname.value = profile.nickname
  } catch {
    targetNickname.value = ''
  }
}

watch(
  () => [route.name, route.params.id],
  () => {
    page.value = 1
    users.value = []
    total.value = 0
    void loadTargetNickname()
    void loadPage(1)
  },
  { immediate: true },
)

function onPageChange(next: number) {
  void loadPage(next)
}
</script>

<template>
  <!-- TRANSITION-SINGLE-ROOT：App.vue 以 <Transition mode="out-in"> 包路由组件，多根会卡死路由切换（CR-091） -->
  <div>
    <el-card shadow="never" class="mb-5">
      <h2 class="text-title-lg font-medium text-ink">
        <RouterLink v-if="targetNickname" :to="`/u/${targetId}`" class="hover:text-link">
          {{ targetNickname }}
        </RouterLink>
        <span v-else>用户主页</span>
        <span class="text-ink-meta">的{{ modeLabel }}</span>
      </h2>
      <p class="mt-1.5 text-note text-ink-meta">{{ modeHint }}</p>

      <!-- 双 Tab：沿用 CR-091 的文字型自有 Tab（主色 + 底部指示条），不复用 EP 组件 -->
      <nav class="mt-3 flex items-center gap-1" aria-label="粉丝与关注">
        <RouterLink
          v-for="tab in tabs"
          :key="tab.to"
          :to="tab.to"
          class="relative cursor-pointer px-3 py-1.5 text-body transition-colors duration-fast ease-standard"
          :class="tab.active ? 'font-medium text-primary' : 'text-ink-regular hover:text-link'"
          :aria-current="tab.active ? 'page' : undefined"
        >
          {{ tab.label }}
          <span
            aria-hidden="true"
            class="inset-x-3 -bottom-px absolute h-0.5 rounded-full transition-opacity duration-fast ease-standard"
            :class="tab.active ? 'bg-primary opacity-100' : 'opacity-0'"
          />
        </RouterLink>
      </nav>
    </el-card>

    <el-card shadow="never" :body-style="{ padding: 0 }">
      <el-skeleton v-if="loading" animated class="px-5 pt-4">
        <template #template>
          <div
            v-for="i in 4"
            :key="i"
            class="flex items-center gap-3 border-b-[0.5px] border-divider py-3 last:border-b-0"
          >
            <el-skeleton-item variant="circle" style="width: 40px; height: 40px" />
            <div class="min-w-0 flex-1">
              <el-skeleton-item variant="text" style="width: 32%" />
              <el-skeleton-item variant="text" class="mt-2" style="width: 54%" />
            </div>
          </div>
        </template>
      </el-skeleton>

      <div v-else-if="loadError" class="p-5">
        <el-alert type="error" :closable="false" show-icon :title="loadError">
          <el-button size="small" @click="onPageChange(page)">重新加载</el-button>
        </el-alert>
      </div>

      <div v-else-if="!users.length" class="py-10 text-center">
        <p class="text-body text-ink-regular">
          {{ mode === 'followers' ? '还没有粉丝' : '还没有关注任何人' }}
        </p>
        <RouterLink :to="`/u/${targetId}`">
          <el-button class="mt-4">回到主页</el-button>
        </RouterLink>
      </div>

      <template v-else>
        <ul>
          <li
            v-for="u in users"
            :key="u.id"
            class="border-b-[0.5px] border-divider last:border-b-0"
          >
            <RouterLink
              :to="`/u/${u.id}`"
              class="flex items-center gap-3 px-5 py-3 transition-colors duration-fast ease-standard hover:bg-bg-hover"
            >
              <UserAvatar :name="u.nickname" :size="40" class="flex-none" />
              <div class="min-w-0 flex-1">
                <p class="truncate text-title-sm font-medium text-ink">{{ u.nickname }}</p>
                <!-- major / bio 为 null 时服务端会整个删掉字段，判空用真值判断 -->
                <p v-if="u.major || u.bio" class="mt-0.5 truncate text-note text-ink-meta">
                  {{ [u.major, u.bio].filter(Boolean).join(' · ') }}
                </p>
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
      <RouterLink :to="`/u/${targetId}`" class="flex items-center gap-1 text-note text-link hover:underline">
        <SvgIcon name="arrow-left" :size="14" />
        返回主页
      </RouterLink>
    </div>
  </div>
</template>
