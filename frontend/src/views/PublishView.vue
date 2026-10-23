<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  getEditablePost,
  getSimilarPosts,
  listBoards,
  publishPost,
  updatePost,
  type BoardVo,
  type SimilarPostVo,
} from '../api/forum'
import { previewMarkdown } from '../api/markdown'
import { useNarrowScreen } from '../composables/useNarrowScreen'
import { useDraft } from '../composables/useDraft'
import { vHighlight } from '../utils/highlight'
import SvgIcon from '../components/SvgIcon.vue'

const route = useRoute()
const router = useRouter()

/**
 * 编辑模式（F-FORUM-007 / CR-128）：路由 `/post/:id/edit` 复用本页。
 * 版块只读（后端也不接受 boardCode）、不读写草稿、不查相似帖——那些都是"发新帖"的语义。
 */
const editingId = computed(() => (route.params.id ? Number(route.params.id) : null))
const editing = computed(() => editingId.value !== null)
const editingBoardName = ref('')

const narrow = useNarrowScreen()
const { saveDraft, loadDraft, clearDraft } = useDraft()
const boards = ref<BoardVo[]>([])
const submitting = ref(false)
const contentEl = ref<HTMLTextAreaElement | null>(null)
const form = reactive({
  boardCode: '',
  title: '',
  contentMd: '',
  tags: [] as string[],
})

/** 常用标签建议（CR-083）：el-select 可搜索可自建，上限 5 个由 multiple-limit 兜底 */
const TAG_SUGGESTIONS = ['Java', 'Spring Boot', 'Redis', 'MySQL', '前端', 'Vue', '算法', '考研', 'ACM', '实习', '面经']

/* ─── 草稿箱状态 ─── */
const draftRestored = ref(false)
const draftTime = ref('')
let draftTimer: ReturnType<typeof setTimeout> | null = null

/* ─── 相似帖子推荐（F-FORUM-009）：标题≥6字时防抖查询，最多触发 2 次 ─── */
const SIMILAR_MIN_TITLE_LENGTH = 6  // 与后端 campuslink.ai.similar.min-title-length 同源
const MAX_SIMILAR_QUERIES = 2       // 前端最多触发 2 次（服务端未限流）

const similarPosts = ref<SimilarPostVo[]>([])
const queryCount = ref(0)
let debounceTimer: ReturnType<typeof setTimeout> | null = null
let reqSeq = 0  // 请求序号，用于乱序保护

/* ─── 实时预览（F-012 / CR-087 接线）：600ms 防抖调服务端渲染（ADR-005），移动端 Tab 切换 ─── */
const previewMode = ref(false)          // 移动端编辑 / 预览切换；桌面端双栏并存
const previewHtml = ref('')
const previewLoading = ref(false)
const previewError = ref('')
let previewTimer: ReturnType<typeof setTimeout> | null = null
let previewSeq = 0

watch(() => form.contentMd, (md) => {
  if (previewTimer) clearTimeout(previewTimer)
  if (!md.trim()) {
    previewHtml.value = ''
    return
  }
  previewTimer = setTimeout(async () => {
    const seq = ++previewSeq
    previewLoading.value = true
    try {
      const res = await previewMarkdown(md)
      if (seq === previewSeq) {
        previewHtml.value = res.html
        previewError.value = ''
      }
    } catch {
      if (seq === previewSeq) {
        previewError.value = '预览加载失败'
      }
    } finally {
      if (seq === previewSeq) previewLoading.value = false
    }
  }, 600)
})

watch(() => form.title, (newTitle) => {
  if (editing.value) return
  if (debounceTimer) clearTimeout(debounceTimer)
  const trimmed = newTitle.trim()
  if (trimmed.length < SIMILAR_MIN_TITLE_LENGTH) {
    // 标题不成立时才清空
    similarPosts.value = []
    return
  }
  if (queryCount.value >= MAX_SIMILAR_QUERIES) return  // 配额耗尽：停止查询，保留上次结果
  debounceTimer = setTimeout(async () => {
    const seq = ++reqSeq
    queryCount.value++  // 发起即计数
    try {
      const data = await getSimilarPosts(trimmed)
      if (seq === reqSeq) {  // 只接受最新响应
        similarPosts.value = data
      }
    } catch {
      if (seq === reqSeq) {
        similarPosts.value = []
      }
    }
  }, 800)
})

/** beforeunload 兜底：页面关闭 / 刷新时同步写盘（localStorage.setItem 是同步的；编辑模式不写草稿） */
function handleBeforeUnload() {
  if (editing.value) return
  if (form.boardCode && (form.title.trim() || form.contentMd.trim())) {
    saveDraft({
      boardCode: form.boardCode,
      title: form.title,
      contentMd: form.contentMd,
      updatedAt: new Date().toISOString(),
    })
  }
}

onUnmounted(() => {
  if (debounceTimer) clearTimeout(debounceTimer)
  // flush 而非丢弃：2s 防抖窗口内导航走也落盘（编辑模式无草稿）
  if (draftTimer) {
    clearTimeout(draftTimer)
    draftTimer = null
    if (!editing.value && form.boardCode && (form.title.trim() || form.contentMd.trim())) {
      saveDraft({
        boardCode: form.boardCode,
        title: form.title,
        contentMd: form.contentMd,
        updatedAt: new Date().toISOString(),
      })
    }
  }
  window.removeEventListener('beforeunload', handleBeforeUnload)
})

/* ─── 草稿自动保存（2s 防抖）：调度时捕获快照，回调使用快照而非实时 form（编辑模式不写草稿） ─── */
watch(
  () => [form.title, form.contentMd, form.boardCode],
  () => {
    if (editing.value) return
    if (draftTimer) clearTimeout(draftTimer)
    if (!form.boardCode) return
    if (!form.title.trim() && !form.contentMd.trim()) return
    // 捕获调度时刻的快照，避免回调执行时 form 已被版块切换覆写
    const snapshot = { boardCode: form.boardCode, title: form.title, contentMd: form.contentMd }
    draftTimer = setTimeout(() => {
      saveDraft({ ...snapshot, updatedAt: new Date().toISOString() })
      draftTimer = null
    }, 2000)
  },
)

/** 尝试恢复指定版块的草稿；有内容才恢复并显示提示 */
function tryRestoreDraft(boardCode: string) {
  const draft = loadDraft(boardCode)
  if (draft && (draft.title.trim() || draft.contentMd.trim())) {
    form.title = draft.title
    form.contentMd = draft.contentMd
    draftRestored.value = true
    draftTime.value = draft.updatedAt
  } else {
    draftRestored.value = false
    draftTime.value = ''
  }
}

/** 切换版块前先 flush 旧版块待存内容，防止 clearTimeout 导致永久丢失 */
function flushPendingDraft(boardCode: string) {
  if (draftTimer) {
    clearTimeout(draftTimer)
    draftTimer = null
    // 立刻按旧 boardCode 保存当前编辑器中的内容
    if (form.title.trim() || form.contentMd.trim()) {
      saveDraft({ boardCode, title: form.title, contentMd: form.contentMd, updatedAt: new Date().toISOString() })
    }
  }
}

/** 版块切换：先 flush 旧版块草稿，再尝试恢复新版块草稿（编辑模式版块只读，不参与） */
watch(() => form.boardCode, (newCode, prevCode) => {
  if (editing.value) return
  if (prevCode) flushPendingDraft(prevCode)
  if (!newCode) return
  tryRestoreDraft(newCode)
})

/** 相对时间格式化：分钟 / 小时 / 天 */
function formatDraftTime(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime()
  const minutes = Math.floor(diff / 60_000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}小时前`
  const days = Math.floor(hours / 24)
  if (days === 1) return '昨天'
  if (days < 30) return `${days}天前`
  return new Date(iso).toLocaleDateString()
}

/** 清除草稿 + 清空表单 + 隐藏提示条 */
function discardDraft() {
  clearDraft(form.boardCode)
  form.title = ''
  form.contentMd = ''
  draftRestored.value = false
  draftTime.value = ''
}

onMounted(async () => {
  window.addEventListener('beforeunload', handleBeforeUnload)
  try {
    boards.value = await listBoards()
    if (editingId.value) {
      // 编辑模式：一次请求取回原文（仅作者可取；非作者 403、不可见 3001 都回到详情页）
      const editable = await getEditablePost(editingId.value)
      editingBoardName.value = editable.boardName
      form.boardCode = editable.boardCode
      form.title = editable.title
      form.contentMd = editable.contentMd
      form.tags = editable.tags
      return
    }
    const fromQuery = String(route.query.board || '')
    form.boardCode = boards.value.some((b) => b.code === fromQuery) ? fromQuery : boards.value[0]?.code || ''
    // 首次加载：尝试恢复当前版块草稿（watch boardCode 不会在此触发因为初始化赋值）
    tryRestoreDraft(form.boardCode)
  } catch (e) {
    if (editingId.value) {
      ElMessage.error(e instanceof Error ? e.message : '帖子加载失败')
      router.replace(`/post/${editingId.value}`)
    } else {
      ElMessage.error(e instanceof Error ? e.message : '版块加载失败')
    }
  }
})

/** 在光标处包裹 / 插入 Markdown 片段，并恢复焦点与选区 */
function wrap(before: string, after = '', placeholder = '') {
  const el = contentEl.value
  if (!el) {
    return
  }
  const { selectionStart: start, selectionEnd: end } = el
  const selected = form.contentMd.slice(start, end) || placeholder
  form.contentMd = form.contentMd.slice(0, start) + before + selected + after + form.contentMd.slice(end)
  nextTick(() => {
    el.focus()
    const caret = start + before.length + selected.length
    el.setSelectionRange(caret, caret)
  })
}

async function submit() {
  if (!form.boardCode) {
    ElMessage.warning('请选择版块')
    return
  }
  if (!form.title.trim()) {
    ElMessage.warning('请填写标题')
    return
  }
  if (!form.contentMd.trim()) {
    ElMessage.warning('请填写正文')
    return
  }
  if (form.tags.length > 5) {
    ElMessage.warning('标签最多 5 个')
    return
  }
  submitting.value = true
  try {
    if (editingId.value) {
      await updatePost(editingId.value, {
        title: form.title.trim(),
        contentMd: form.contentMd,
        tags: form.tags,
      })
      ElMessage.success('修改已保存')
      router.push(`/post/${editingId.value}`)
      return
    }
    const res = await publishPost({
      boardCode: form.boardCode,
      title: form.title.trim(),
      contentMd: form.contentMd,
      tags: form.tags,
    })
    clearDraft(form.boardCode)
    draftRestored.value = false
    ElMessage.success('发布成功')
    router.push(`/post/${res.id}`)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '发布失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <!-- 沉浸式编辑器（CR-073，参考 CSDN / 掘金）：单栏大 sheet，标题超大无边框、工具条通栏、正文大行距 -->
  <div class="overflow-hidden rounded-lg border-[0.5px] border-line bg-card">
    <!-- 顶栏：版块选择 + 字数 + 发布（掘金式极简头） -->
    <div class="flex items-center gap-3 border-b-[0.5px] border-line px-5 py-3">
      <el-select
        v-if="!editing"
        v-model="form.boardCode"
        placeholder="选择版块"
        class="w-[180px] max-md:w-full max-md:flex-1"
      >
        <el-option v-for="b in boards" :key="b.code" :label="b.name" :value="b.code" />
      </el-select>
      <span v-else class="shrink-0 text-note text-ink-regular">版块：{{ editingBoardName }}</span>
      <span class="ml-auto hidden shrink-0 text-caption text-ink-meta md:inline" aria-live="polite">
        标题 {{ form.title.length }}/100 · 正文 {{ form.contentMd.length }}/20000
      </span>
      <el-button type="primary" :loading="submitting" @click="submit">
        {{ editing ? '保存修改' : '发 布' }}
      </el-button>
    </div>

    <!-- 草稿恢复提示 -->
    <div v-if="draftRestored" class="mx-5 mb-3 flex items-center justify-between rounded-lg border border-amber-200 bg-amber-50 px-3 py-2">
      <span class="text-sm text-amber-700">
        已恢复上次未发布的草稿（保存于 {{ formatDraftTime(draftTime) }}）
      </span>
      <button type="button" class="text-xs text-amber-500 hover:text-amber-700" @click="discardDraft">
        清除
      </button>
    </div>

    <div class="mx-auto max-w-reading px-6 max-md:px-4">
      <!-- 标题：超大无边框输入（CSDN / 掘金式） -->
      <input
        v-model="form.title"
        maxlength="100"
        placeholder="输入标题（1~100 字）"
        class="block w-full border-0 bg-transparent py-6 text-[26px] leading-snug font-medium text-ink outline-none placeholder:text-ink-meta max-md:text-[22px]"
      />
      <!-- 标签（CR-083）：可搜索可自建，最多 5 个 -->
      <el-select
        v-model="form.tags"
        multiple
        filterable
        allow-create
        default-first-option
        :multiple-limit="5"
        placeholder="添加标签（0~5 个，回车自建）"
        class="mb-4 w-full"
      >
        <el-option v-for="t in TAG_SUGGESTIONS" :key="t" :label="t" :value="t" />
      </el-select>
    </div>

    <!-- 相似帖子推荐（F-FORUM-009；编辑模式不查——改的是已有帖，不是找重复提问） -->
    <div v-if="!editing && similarPosts.length" class="mx-5 mb-3 rounded-lg border border-blue-200 bg-blue-50 p-3">
      <div class="mb-2 flex items-center justify-between">
        <span class="text-sm font-medium text-blue-700">发现相似问题，也许已有答案</span>
        <button type="button" class="text-xs text-gray-400 hover:text-gray-600" @click="similarPosts = []">忽略</button>
      </div>
      <a
        v-for="post in similarPosts"
        :key="post.id"
        :href="'/post/' + post.id"
        target="_blank"
        rel="noopener"
        class="block truncate py-1.5 text-sm text-gray-700 hover:text-blue-600"
      >
        <span v-if="post.accepted" class="mr-1 font-medium text-green-600">[已采纳]</span>
        {{ post.title }}
        <span class="ml-2 text-xs text-gray-400">{{ post.replyCount }} 回复</span>
      </a>
    </div>

    <!-- 工具条：通栏（CSDN 式），样式来自全局 .cl-md-tool -->
    <div class="flex flex-wrap items-center gap-1 border-y-[0.5px] border-line bg-code px-5 py-1.5" role="toolbar" aria-label="Markdown 工具条">
      <button type="button" class="cl-md-tool text-caption font-medium" title="一级标题" aria-label="一级标题" @click="wrap('# ', '', '标题')">#</button>
      <button type="button" class="cl-md-tool text-caption font-medium" title="二级标题" aria-label="二级标题" @click="wrap('## ', '', '标题')">H2</button>
      <button type="button" class="cl-md-tool font-medium" title="加粗" aria-label="加粗" @click="wrap('**', '**', '加粗文字')">B</button>
      <button type="button" class="cl-md-tool italic" title="斜体" aria-label="斜体" @click="wrap('*', '*', '斜体文字')">I</button>
      <span class="mx-0.5 h-4 w-px bg-line" aria-hidden="true" />
      <button
        type="button"
        class="cl-md-tool font-mono text-caption"
        title="代码块"
        aria-label="代码块"
        @click="wrap('```\n', '\n```', '代码')"
      >
        &lt;/&gt;
      </button>
      <button type="button" class="cl-md-tool text-caption font-medium" title="引用" aria-label="引用" @click="wrap('> ', '', '引用内容')">❝</button>
      <button type="button" class="cl-md-tool" title="无序列表" aria-label="无序列表" @click="wrap('- ', '', '列表项')">
        <SvgIcon name="list" :size="15" />
      </button>
      <button type="button" class="cl-md-tool" title="插入链接" aria-label="插入链接" @click="wrap('[', '](https://)', '链接文字')">
        <SvgIcon name="link" :size="15" />
      </button>
      <button
        type="button"
        class="cl-md-tool"
        disabled
        title="图片上传即将开放（暂无上传接口）"
        aria-label="图片（暂未开放）"
      >
        <SvgIcon name="photo" :size="15" />
      </button>
      <span class="mx-0.5 h-4 w-px bg-line" aria-hidden="true" />
      <button
        type="button"
        class="cl-md-tool text-caption font-medium"
        :title="narrow ? (previewMode ? '返回编辑' : '预览') : '桌面端右侧实时预览'"
        :aria-pressed="narrow ? previewMode : undefined"
        @click="narrow ? (previewMode = !previewMode) : undefined"
      >
        {{ narrow ? (previewMode ? '编辑' : '预览') : '预览' }}
      </button>
      <span class="ml-auto text-caption text-ink-meta">{{ form.title.length }}/100 · {{ form.contentMd.length }}/20000</span>
    </div>

    <div class="mx-auto max-w-reading px-6 max-md:px-4">
      <!-- 编辑 / 预览：桌面端双栏（md+ 并排），窄屏 Tab 切换（CR-091 / F-012 接线） -->
      <div class="flex items-start gap-6 max-md:flex-col">
        <textarea
          ref="contentEl"
          v-show="!previewMode || !narrow"
          v-model="form.contentMd"
          maxlength="20000"
          placeholder="正文支持 Markdown：标题 / 列表 / 代码块（```语言）/ 链接 / 引用。开始输入…"
          class="min-h-[50vh] w-full resize-none border-0 bg-transparent py-5 text-body leading-[1.9] text-ink outline-none placeholder:text-ink-meta max-md:min-h-[36vh] md:w-1/2 md:flex-none"
        />
        <!-- 预览面板：服务端渲染并净化的 HTML，直接 v-html（XSS 防线在 MarkdownRenderer） -->
        <div
          v-show="previewMode || !narrow"
          class="min-h-[50vh] w-full py-5 max-md:min-h-[36vh] md:w-1/2 md:border-l-[0.5px] md:border-line md:pl-6"
        >
          <p v-if="!form.contentMd.trim()" class="text-note leading-body text-ink-meta">
            开始输入正文，此处将实时展示渲染效果…
          </p>
          <p v-else-if="previewLoading && !previewHtml" class="text-note text-ink-meta">渲染中…</p>
          <p v-else-if="previewError" class="text-note text-ink-meta">{{ previewError }}</p>
          <div v-else class="markdown-body" v-highlight v-html="previewHtml" />
        </div>
      </div>
      <p class="pb-6 text-caption leading-body text-ink-meta">
        <template v-if="editing">编辑只改标题 / 正文 / 标签，版块不可改 · 预览与保存后的正文同源</template>
        <template v-else>发布内容须符合社区规范，违规内容将被下架处理 · 预览按 ADR-005 在服务端渲染并做安全净化，与发布后的正文同源</template>
      </p>
    </div>
  </div>
</template>
