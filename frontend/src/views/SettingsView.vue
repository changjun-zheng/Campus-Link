<script setup lang="ts">
/**
 * 设置页（F-ACC-007d）：基线 §5.1「我的（帖子 / 收藏 / 设置 / 注销）」那一格第一次落成结构。
 *
 * <p>三区块按增量 PRD §4.2 的骨架来，但**后端不存在的行一律不占位**：通知偏好（007f 门槛未齐）、
 * 头像（007h）、求职意向（007g）本期都不进本页——"点了没反应"就是文档在撒谎。
 * 注销只有入口、没有可提交的目标（F-ACC-003 零实现），故做成**禁用按钮 + 说明**，
 * 连带 PRD 的"手输注销二字二次确认"一起不做：确认框后面接一个必然失败的请求，比没有按钮更糟。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { deactivateAccount, updateProfile } from '../api/auth'
import { listMyFavorites, listMyPosts, listMyReplies } from '../api/forum'
import type { IconName } from '../assets/icons'
import { useAuthStore } from '../stores/auth'
import SvgIcon from '../components/SvgIcon.vue'
import UserAvatar from '../components/UserAvatar.vue'

const router = useRouter()
const auth = useAuthStore()

/** 与后端 FieldRules 同源的长度（改一侧必须改另一侧，否则前端放过、后端 1001） */
const NICKNAME_MIN = 2
const NICKNAME_MAX = 32
const MAJOR_MAX = 64
const BIO_MAX = 200

const form = reactive({ nickname: '', major: '', bio: '' })
const saving = ref(false)
const saveError = ref('')

const user = computed(() => auth.user)
const dirty = computed(() => {
  const u = user.value
  if (!u) {
    return false
  }
  return (
    form.nickname.trim() !== (u.nickname ?? '') ||
    form.major.trim() !== (u.major ?? '') ||
    form.bio.trim() !== (u.bio ?? '')
  )
})
const nicknameValid = computed(() => {
  const v = form.nickname.trim()
  return v.length >= NICKNAME_MIN && v.length <= NICKNAME_MAX
})

/** 变更字段名清单：让用户在提交前看见"这次会改哪几项"，而不是把三个输入框当一个整体 */
const changedFields = computed(() => {
  const u = user.value
  if (!u) {
    return []
  }
  const names: string[] = []
  if (form.nickname.trim() !== (u.nickname ?? '')) names.push('昵称')
  if (form.major.trim() !== (u.major ?? '')) names.push('专业')
  if (form.bio.trim() !== (u.bio ?? '')) names.push('签名')
  return names
})

function fillFromUser() {
  const u = user.value
  form.nickname = u?.nickname ?? ''
  form.major = u?.major ?? ''
  form.bio = u?.bio ?? ''
}

onMounted(async () => {
  fillFromUser()
  loadCounts()
  try {
    // 令牌可能在别处改过资料或已失效，进页先拉一次全量回显，避免表单落在过期值上
    await auth.fetchMe()
    fillFromUser()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '账号信息加载失败')
  }
})

async function save() {
  saveError.value = ''
  if (!nicknameValid.value) {
    saveError.value = `昵称需 ${NICKNAME_MIN}~${NICKNAME_MAX} 个字符，且不能只有空格`
    return
  }
  saving.value = true
  try {
    const updated = await updateProfile({
      nickname: form.nickname.trim(),
      major: form.major.trim(),
      bio: form.bio.trim(),
    })
    auth.setUser(updated)
    fillFromUser()
    ElMessage.success('资料已保存')
    loadCounts()
  } catch (e) {
    // 后端消息直接透出：429/2008 的"资料修改过于频繁"与 1001 的字符集判据都在那里
    saveError.value = e instanceof Error ? e.message : '保存失败'
  } finally {
    saving.value = false
  }
}

/** 三个「我的内容」计数的探测请求：只要 total，故 size=1；任一失败即留空，不阻塞本页其它区块 */
const counts = reactive<{ posts: number | null; replies: number | null; favorites: number | null }>({
  posts: null,
  replies: null,
  favorites: null,
})
const contentEntries: { to: string; icon: IconName; label: string; hint: string; key: keyof typeof counts }[] = [
  { to: '/me/posts', icon: 'article', label: '我的帖子', hint: '含被平台下架的条目', key: 'posts' },
  { to: '/me/replies', icon: 'message-2', label: '我的回帖', hint: '父帖不可见的不出现', key: 'replies' },
  { to: '/favorites', icon: 'bookmark', label: '我的收藏', hint: '按收藏时间倒序', key: 'favorites' },
]
async function loadCounts() {
  await Promise.allSettled([
    listMyPosts(1, 1).then((res) => (counts.posts = res.total)),
    listMyReplies(1, 1).then((res) => (counts.replies = res.total)),
    listMyFavorites(1, 1).then((res) => (counts.favorites = res.total)),
  ])
}

function logout() {
  auth.logout()
  router.push('/')
}

/** 注销（F-ACC-003 / CR-082）：手输「注销」二次确认 → deactivate → 冷静期提示 → 退出登录 */
const deactivating = ref(false)

async function onDeactivate() {
  try {
    await ElMessageBox.prompt('此操作进入 7 天冷静期，期满后账号将匿名化且无法恢复。请输入「注销」以确认：', '注销账号', {
      confirmButtonText: '确认注销',
      cancelButtonText: '取消',
      type: 'warning',
      inputPattern: /^注销$/,
      inputErrorMessage: '请输入「注销」二字以确认',
    })
  } catch {
    return
  }
  deactivating.value = true
  try {
    await deactivateAccount()
    auth.logout()
    ElMessage.success('账号已进入 7 天冷静期；期间再次登录即自动撤销注销')
    router.push('/')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '注销失败')
  } finally {
    deactivating.value = false
  }
}
</script>

<template>
  <div class="mx-auto w-full max-w-reading">
    <el-card shadow="never" class="mb-4">
      <div class="flex items-center gap-3">
        <UserAvatar :src="user?.avatarUrl" :name="user?.nickname" :size="44" />
        <div class="min-w-0">
          <h1 class="truncate text-title-lg font-medium text-ink">设置</h1>
          <p class="mt-0.5 text-note text-ink-meta">本页内容仅本人可见</p>
        </div>
      </div>
    </el-card>

    <!-- 区块 1：我的资料 -->
    <el-card shadow="never" class="mb-4">
      <template #header>
        <span class="font-medium">我的资料</span>
      </template>

      <el-form label-position="top" @submit.prevent>
        <el-form-item>
          <template #label>
            <span>昵称</span>
            <span class="ml-1 text-caption text-ink-meta">必填，{{ NICKNAME_MIN }}~{{ NICKNAME_MAX }} 字</span>
          </template>
          <el-input
            v-model="form.nickname"
            :maxlength="NICKNAME_MAX"
            show-word-limit
            placeholder="回帖与帖子列表上展示的名字"
          />
        </el-form-item>

        <el-form-item>
          <template #label>
            <span>专业</span>
            <span class="ml-1 text-caption text-ink-meta">选填，≤{{ MAJOR_MAX }} 字</span>
          </template>
          <el-input v-model="form.major" :maxlength="MAJOR_MAX" show-word-limit placeholder="如：软件工程" />
        </el-form-item>

        <el-form-item>
          <template #label>
            <span>个性签名</span>
            <span class="ml-1 text-caption text-ink-meta">选填，≤{{ BIO_MAX }} 字纯文本</span>
          </template>
          <el-input
            v-model="form.bio"
            type="textarea"
            :rows="3"
            :maxlength="BIO_MAX"
            show-word-limit
            resize="none"
            placeholder="一句话介绍自己"
          />
        </el-form-item>
      </el-form>

      <!-- 学校 / 年级 / 学号 / 邮箱在这里没有输入框，也不该有：学校是部署事实、年级由学号推导、
           学号与邮箱属敏感列（本期连读都不出）。「届」同样不展示——它要由学号前两位推导，
           而学号从不下发到前端，客户端算不出，硬展示就等于把未核实假设写死在页面上。 -->
      <div class="mt-1 flex flex-wrap items-center gap-2 text-note text-ink-regular">
        <el-tag v-if="user?.verified" type="success" effect="light" size="small">学籍已认证</el-tag>
        <el-tag v-else type="info" effect="light" size="small">未认证</el-tag>
        <span>角色：{{ user?.role || '—' }}</span>
      </div>

      <p class="mt-3 text-caption leading-body text-ink-meta">
        昵称 / 专业 / 签名不得包含尖括号与控制字符（服务端校验是唯一防线，本站尚未接入 CSP）；
        清空专业或签名即不再展示，昵称不能清空。资料修改限每小时 10 次。
      </p>

      <el-alert v-if="saveError" class="mt-3" type="error" :closable="false" show-icon :title="saveError" />

      <div class="mt-4 flex flex-wrap items-center justify-between gap-3">
        <span class="text-note text-ink-meta">
          <template v-if="changedFields.length">将修改：{{ changedFields.join('、') }}</template>
          <template v-else>没有待保存的修改</template>
        </span>
        <div class="flex gap-2">
          <el-button :disabled="saving || !dirty" @click="fillFromUser">还原</el-button>
          <el-button
            type="primary"
            :loading="saving"
            :disabled="!dirty || !nicknameValid"
            @click="save"
          >
            保存
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 区块 2：我的内容 -->
    <el-card shadow="never" class="mb-4" :body-style="{ padding: 0 }">
      <template #header>
        <span class="font-medium">我的内容</span>
      </template>
      <ul>
        <li v-for="entry in contentEntries" :key="entry.to">
          <RouterLink
            :to="entry.to"
            class="flex items-center gap-3 border-b-[0.5px] border-divider px-5 py-3 transition-colors duration-fast ease-standard hover:bg-bg-hover last:border-b-0"
          >
            <SvgIcon :name="entry.icon" :size="18" class="flex-none text-ink-meta" />
            <span class="min-w-0 flex-1">
              <span class="block text-body text-ink">{{ entry.label }}</span>
              <span class="mt-0.5 block text-caption text-ink-meta">{{ entry.hint }}</span>
            </span>
            <span class="flex-none text-note text-ink-meta">{{ counts[entry.key] ?? '—' }}</span>
            <SvgIcon name="chevron-right" :size="14" class="flex-none text-ink-meta" />
          </RouterLink>
        </li>
      </ul>
    </el-card>

    <!-- 区块 3：账号 -->
    <el-card shadow="never">
      <template #header>
        <span class="font-medium">账号</span>
      </template>

      <div class="flex flex-wrap items-center gap-3">
        <span class="text-body text-ink-regular">账号状态</span>
        <el-tag v-if="user?.status === 'BANNED'" type="danger" size="small">已封禁</el-tag>
        <el-tag v-else-if="user?.status === 'DEACTIVATED'" type="warning" size="small">注销冷静期</el-tag>
        <el-tag v-else type="success" effect="light" size="small">正常</el-tag>
      </div>

      <!-- 邮箱 / 手机号 / 学号不进本页（增量 PRD N6：换绑要两段式确认且牵动唯一索引），
           登录凭据是"邮箱 + 验证码"，本站没有密码字段，故也没有改密码入口 -->
      <p class="mt-3 text-caption leading-body text-ink-meta">
        登录方式是邮箱 + 验证码，系统不保存密码，因此没有"修改密码"。邮箱换绑、手机号、学号本期均不开放修改。
      </p>

      <div class="mt-4 flex flex-wrap items-center gap-2">
        <el-button @click="logout">退出登录</el-button>
        <el-button type="danger" plain :loading="deactivating" @click="onDeactivate">注销账号</el-button>
      </div>
      <p class="mt-2 text-caption leading-body text-ink-meta">
        注销进入 7 天冷静期：期内再次登录即自动撤销；期满后昵称改为「已注销用户」、
        邮箱 / 学号等实名信息匿名化，且该账号无法再登录。
      </p>
    </el-card>
  </div>
</template>
