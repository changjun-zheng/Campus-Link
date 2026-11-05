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
import { uploadPostImage } from '../api/postImages'
import { usePostImageHtml } from '../composables/usePostImageHtml'
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
/** 编辑页的匿名状态（CR-139）：发布后不可切换，只读展示 */
const editingAnonymous = ref(false)

// 预览分栏以工作区宽度为准；768px 的半栏不足以编辑代码。
const editorMedia = window.matchMedia('(min-width: 1024px)')
const splitPreview = ref(editorMedia.matches)
const onEditorResize = (event: MediaQueryListEvent) => { splitPreview.value = event.matches }
const { saveDraft, loadDraft, clearDraft } = useDraft()
const boards = ref<BoardVo[]>([])
const uploading = ref(false)
const fileEl = ref<HTMLInputElement | null>(null)
let uploadAbort: AbortController | undefined
let uploadGeneration = 0
let insertionEnd = 0
function chooseImage() {
  if (uploading.value || submitting.value) return
  insertionEnd = contentEl.value?.selectionEnd ?? form.contentMd.length
  fileEl.value?.click()
}
function cancelUpload() { uploadGeneration++; uploadAbort?.abort(); uploading.value = false }
async function uploadSelectedImage(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || uploading.value || submitting.value) return
  if (file.size > 2 * 1024 * 1024) { ElMessage.error('图片不能超过 2MiB'); return }
  const generation = ++uploadGeneration
  const body = form.contentMd
  const board = form.boardCode
  const id = editingId.value
  const controller = new AbortController()
  uploadAbort = controller
  uploading.value = true
  try {
    const image = await uploadPostImage(file, controller.signal)
    if (controller.signal.aborted || generation !== uploadGeneration || board !== form.boardCode
      || id !== editingId.value || body !== form.contentMd) return
    const end = Math.min(insertionEnd, body.length)
    const fragment = `\n![图片](${image.url})\n`
    form.contentMd = body.slice(0, end) + fragment + body.slice(end)
    await nextTick()
    contentEl.value?.focus()
    contentEl.value?.setSelectionRange(end + fragment.length, end + fragment.length)
  } catch (error) {
    if (!controller.signal.aborted) ElMessage.error(error instanceof Error ? error.message : '图片上传失败')
  } finally {
    if (generation === uploadGeneration) uploading.value = false
  }
}
const submitting = ref(false)
const contentEl = ref<HTMLTextAreaElement | null>(null)
/** 校验失败时的焦点落点（CR-173）：标题是原生 input，版块 / 标签是 EP 组件（暴露 focus()） */
const titleEl = ref<HTMLInputElement | null>(null)
const boardEl = ref<{ focus: () => void } | null>(null)
const form = reactive({
  boardCode: '',
  title: '',
  contentMd: '',
  tags: [] as string[],
  /** 匿名发布（F-FORUM-010 / CR-139）：仅发新帖时可勾选，发布后不可更改 */
  anonymous: false,
})

/**
 * 常用标签建议（CR-083 / CR-144）：el-select 可搜索可自建，上限 5 个由 multiple-limit 兜底。
 * 与 backend/scripts/data/D002__seed_tags.py 的种子集同源（两处各维护一份是刻意代价——
 * 标签是运营数据、建议是 UI 常量，联动的唯一约定是本注释）。
 */
const TAG_SUGGESTIONS = [
  // 课程名
  '数据结构', '操作系统', '计算机网络', '数据库原理', 'Java 程序设计',
  'Web 开发', '软件工程', '计算机组成原理', '编译原理', '人工智能',
  '离散数学', '线性代数',
  // 技术栈
  'Java', 'Spring Boot', 'MySQL', 'Redis', 'Vue', 'Python', 'C',
  'C++', 'JavaScript', 'TypeScript', 'Linux', 'Git', 'Docker',
  // 竞赛
  '蓝桥杯', '软件杯', 'ACM', '互联网+', '挑战杯', '数学建模',
  // 求职与通用
  '实习', '校招', '面经', '考研', '课程设计', '期末复习',
  '学习笔记', '前端', '算法', '大一', '大二', '大三', '大四',
]

/**
 * 面经结构化模板（CR-143 / CR-075 建议 N5）：面经版块发帖引导，正文为空时一键填入骨架。
 * 只做 Markdown 预填不做结构化表单——「引导填写」即达成建议目标，将来若做结构化解析
 * （CR-038 面经追问解禁时）按固定小节标题提取即可。
 */
const INTERVIEW_TEMPLATE = `## 基本信息

- 公司：
- 岗位：
- 轮次：一面 / 二面 / 三面 / HR 面
- 时间与形式：如 2026-10 · 线上

## 面试问题

1.
2.

## 心得与建议

`

const showTemplateHint = computed(
  () => !editing.value && form.boardCode === 'interview' && !form.contentMd.trim(),
)

function applyInterviewTemplate() {
  if (uploading.value || !showTemplateHint.value) return
  form.contentMd = INTERVIEW_TEMPLATE
  contentEl.value?.focus()
}

/* ─── 草稿箱状态 ─── */
const draftRestored = ref(false)
const draftTime = ref('')
const draftStatus = ref<'idle' | 'pending' | 'saved' | 'failed'>('idle')
const draftStatusText = computed(() => ({
  idle: '草稿仅保存在此浏览器',
  pending: '草稿待保存…',
  saved: '草稿已保存到此浏览器',
  failed: '草稿保存失败，请保留页面并复制正文',
})[draftStatus.value])
let draftTimer: ReturnType<typeof setTimeout> | null = null

function persistDraft(boardCode: string, title: string, contentMd: string) {
  const saved = saveDraft({ boardCode, title, contentMd, updatedAt: new Date().toISOString() })
  draftStatus.value = saved ? 'saved' : 'failed'
}

/* ─── 相似帖子推荐（F-FORUM-009）：标题≥6字时防抖查询，最多触发 2 次 ─── */
const SIMILAR_MIN_TITLE_LENGTH = 6  // 与后端 campuslink.ai.similar.min-title-length 同源
const MAX_SIMILAR_QUERIES = 2       // 前端最多触发 2 次（服务端未限流）

const similarPosts = ref<SimilarPostVo[]>([])
const queryCount = ref(0)
let debounceTimer: ReturnType<typeof setTimeout> | null = null
let reqSeq = 0  // 请求序号，用于乱序保护

/* ─── 实时预览（F-012 / CR-087 接线）：600ms 防抖调服务端渲染（ADR-005），移动端 Tab 切换 ─── */
const previewMode = ref(false)          // <1024px 编辑 / 预览切换；宽屏双栏并存
const previewHtml = ref('')
const renderedPreviewHtml = usePostImageHtml(previewHtml)
const previewLoading = ref(false)
const previewError = ref('')
let previewTimer: ReturnType<typeof setTimeout> | null = null
let previewSeq = 0

watch(() => form.contentMd, (md) => {
  if (previewTimer) clearTimeout(previewTimer)
  const seq = ++previewSeq
  previewError.value = ''
  if (!md.trim()) {
    previewHtml.value = ''
    previewLoading.value = false
    return
  }
  previewLoading.value = true
  previewTimer = setTimeout(async () => {
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
    persistDraft(form.boardCode, form.title, form.contentMd)
  }
}

onUnmounted(() => {
  cancelUpload()
  if (debounceTimer) clearTimeout(debounceTimer)
  if (previewTimer) clearTimeout(previewTimer)
  previewSeq++
  reqSeq++
  editorMedia.removeEventListener('change', onEditorResize)
  // flush 而非丢弃：2s 防抖窗口内导航走也落盘（编辑模式无草稿）
  if (draftTimer) {
    clearTimeout(draftTimer)
    draftTimer = null
    if (!editing.value && form.boardCode && (form.title.trim() || form.contentMd.trim())) {
      persistDraft(form.boardCode, form.title, form.contentMd)
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
    draftTimer = null
    if (!form.boardCode) return
    if (!form.title.trim() && !form.contentMd.trim()) {
      draftStatus.value = 'idle'
      return
    }
    draftStatus.value = 'pending'
    // 捕获调度时刻的快照，避免回调执行时 form 已被版块切换覆写
    const snapshot = { boardCode: form.boardCode, title: form.title, contentMd: form.contentMd }
    draftTimer = setTimeout(() => {
      persistDraft(snapshot.boardCode, snapshot.title, snapshot.contentMd)
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
      persistDraft(boardCode, form.title, form.contentMd)
    }
  }
}

/** 版块切换：先 flush 旧版块草稿，再尝试恢复新版块草稿（编辑模式版块只读，不参与） */
watch(() => form.boardCode, (newCode, prevCode) => {
  cancelUpload()
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
  if (uploading.value) return
  if (draftTimer) clearTimeout(draftTimer)
  draftTimer = null
  clearDraft(form.boardCode)
  form.title = ''
  form.contentMd = ''
  draftRestored.value = false
  draftTime.value = ''
  draftStatus.value = 'idle'
}

onMounted(async () => {
  editorMedia.addEventListener('change', onEditorResize)
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
      editingAnonymous.value = editable.anonymous
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
  if (uploading.value) return
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
  if (submitting.value || uploading.value) return
  if (!form.boardCode) {
    ElMessage.warning('请选择版块')
    boardEl.value?.focus()
    return
  }
  if (!form.title.trim()) {
    ElMessage.warning('请填写标题')
    titleEl.value?.focus()
    return
  }
  if (!form.contentMd.trim()) {
    ElMessage.warning('请填写正文')
    contentEl.value?.focus()
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
      anonymous: form.anonymous,
    })
    // 发布完成后取消待存任务，防止卸载 flush 把已发布内容重新存成草稿。
    if (draftTimer) clearTimeout(draftTimer)
    draftTimer = null
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
  <div class="cl-publish overflow-hidden rounded-lg border-[0.5px] border-line bg-card">
    <!-- 顶栏：版块选择 + 字数 + 发布（掘金式极简头） -->
    <div class="flex flex-wrap items-center gap-3 border-b-[0.5px] border-line px-5 py-3">
      <el-select
        v-if="!editing"
        ref="boardEl"
        v-model="form.boardCode"
        aria-label="选择版块"
        :disabled="uploading"
        placeholder="选择版块"
        class="cl-board-select"
      >
        <el-option v-for="b in boards" :key="b.code" :label="b.name" :value="b.code" />
      </el-select>
      <span v-else class="shrink-0 text-note text-ink-regular">版块：{{ editingBoardName }}</span>
      <span v-if="!editing" role="status" class="cl-draft-status text-caption text-ink-meta" :class="{ 'cl-draft-status--failed': draftStatus === 'failed' }">
        {{ draftStatusText }}
      </span>
      <el-button class="cl-publish-submit" type="primary" :loading="submitting" :disabled="uploading" @click="submit">
        {{ editing ? '保存修改' : '发布' }}
      </el-button>
    </div>

    <!-- 草稿恢复提示 -->
    <div v-if="!editing && draftRestored" class="mx-5 my-3 flex items-center justify-between gap-3 rounded-sm border-[0.5px] border-line bg-primary-soft px-3 py-2">
      <span class="text-note text-ink-regular">
        已恢复上次未发布的草稿（保存于 {{ formatDraftTime(draftTime) }}）
      </span>
      <button type="button" class="shrink-0 text-caption text-link hover:underline" @click="discardDraft">
        清除
      </button>
    </div>

    <div class="px-6 max-md:px-4">
      <!-- 标题（CR-201 批次 A）：20px + 一条细下划线，聚焦时提亮——比 26px 无边界大字更像编辑器 -->
      <div class="border-b-[0.5px] border-divider transition-colors duration-fast ease-standard focus-within:border-primary-strong">
        <input
          ref="titleEl"
          v-model="form.title"
          aria-label="帖子标题"
          maxlength="100"
          :placeholder="form.boardCode === 'interview' ? '如：XX 公司 Java 后端一面面经' : '输入标题（1~100 字）'"
          class="cl-editor-title block w-full border-0 bg-transparent py-5 text-[20px] leading-snug font-medium text-ink outline-none placeholder:text-ink-meta"
        />
      </div>

      <!-- 面经模板入口（CR-143）：仅面经版块 + 正文为空时出现，填入后自动隐藏 -->
      <div v-if="showTemplateHint" class="flex flex-wrap items-center gap-1.5 pb-4 text-note text-ink-meta">
        <span>第一次写面经？按固定小节组织更易读——</span>
        <button type="button" class="font-medium text-primary-strong hover:underline" @click="applyInterviewTemplate">
          填入面经模板（公司 / 岗位 / 轮次 / 面试问题）
        </button>
      </div>
      <!-- 标签（CR-083）：可搜索可自建，最多 5 个 -->
      <el-select
        v-model="form.tags"
        multiple
        filterable
        allow-create
        default-first-option
        :multiple-limit="5"
        aria-label="添加标签"
        placeholder="添加标签（0~5 个，回车自建）"
        class="mb-4 w-full"
      >
        <el-option v-for="t in TAG_SUGGESTIONS" :key="t" :label="t" :value="t" />
      </el-select>

      <!-- 匿名发布（F-FORUM-010 / CR-139）：发布后不可更改。编辑页只读展示当前身份 -->
      <el-checkbox v-if="!editing" v-model="form.anonymous" class="mb-4">
        <span class="block whitespace-normal">
          <span class="text-body text-ink">匿名发布</span>
          <span class="ml-1 text-caption text-ink-meta">列表与详情只显示「匿名用户」，发布后不可更改</span>
        </span>
      </el-checkbox>
      <p v-else class="mb-4 text-note text-ink-meta">
        发布身份：
        <span class="font-medium text-ink">{{ editingAnonymous ? '匿名发布' : '实名发布' }}</span>
        <span class="ml-1 text-caption">（发布后不可更改）</span>
      </p>
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

    <input ref="fileEl" type="file" accept="image/jpeg,image/png" aria-label="图片文件" hidden @change="uploadSelectedImage" />
    <p v-if="uploading" role="status" class="px-5 py-2 text-note text-ink-meta">图片上传中…正文编辑与提交已暂停</p>
    <!-- 工具条：通栏（CSDN 式），样式来自全局 .cl-md-tool -->
    <div class="flex flex-wrap items-center gap-1 border-y-[0.5px] border-line bg-code px-5 py-1.5" role="toolbar" aria-label="Markdown 工具条">
      <button type="button" class="cl-md-tool text-caption font-medium" title="一级标题" aria-label="# 一级标题" @click="wrap('# ', '', '标题')">#</button>
      <button type="button" class="cl-md-tool text-caption font-medium" title="二级标题" aria-label="H2 二级标题" @click="wrap('## ', '', '标题')">H2</button>
      <button type="button" class="cl-md-tool font-medium" title="加粗" aria-label="B 加粗" @click="wrap('**', '**', '加粗文字')">B</button>
      <button type="button" class="cl-md-tool italic" title="斜体" aria-label="I 斜体" @click="wrap('*', '*', '斜体文字')">I</button>
      <span class="mx-0.5 h-4 w-px bg-line" aria-hidden="true" />
      <button
        type="button"
        class="cl-md-tool font-mono text-caption"
        title="代码块"
        aria-label="</> 代码块"
        @click="wrap('```\n', '\n```', '代码')"
      >
        &lt;/&gt;
      </button>
      <button type="button" class="cl-md-tool text-caption font-medium" title="引用" aria-label="❝ 引用" @click="wrap('> ', '', '引用内容')">❝</button>
      <button type="button" class="cl-md-tool" title="无序列表" aria-label="无序列表" @click="wrap('- ', '', '列表项')">
        <SvgIcon name="list" :size="16" />
      </button>
      <button type="button" class="cl-md-tool" title="插入链接" aria-label="插入链接" @click="wrap('[', '](https://)', '链接文字')">
        <SvgIcon name="link" :size="16" />
      </button>
      <button
        type="button"
        class="cl-md-tool"
        :disabled="uploading || submitting"
        title="上传 JPEG / PNG 图片（不超过 2MiB）"
        aria-label="上传图片"
        @click="chooseImage"
      >
        <SvgIcon name="photo" :size="16" />
      </button>
      <span class="mx-0.5 h-4 w-px bg-line" aria-hidden="true" />
      <button
        v-if="!splitPreview"
        type="button"
        class="cl-md-tool cl-preview-toggle text-caption font-medium"
        :title="previewMode ? '返回编辑' : '预览正文'"
        :aria-pressed="previewMode"
        @click="previewMode = !previewMode"
      >
        {{ previewMode ? '返回编辑' : '预览正文' }}
      </button>
      <span class="ml-auto text-caption text-ink-meta">{{ form.title.length }}/100 · {{ form.contentMd.length }}/20000</span>
    </div>

    <div class="px-6 max-md:px-4">
      <div class="cl-editor-workspace" :class="{ 'cl-editor-workspace--split': splitPreview }">
        <section v-show="!previewMode || splitPreview" class="cl-editor-pane" aria-label="Markdown 编辑区">
          <p class="pt-4 text-caption text-ink-meta">Markdown 编辑</p>
          <textarea
            ref="contentEl"
            :readonly="uploading"
            v-model="form.contentMd"
            maxlength="20000"
            aria-label="正文（Markdown）"
            placeholder="正文支持 Markdown：标题 / 列表 / 代码块（```语言）/ 链接 / 引用。开始输入…"
            class="cl-editor-body min-h-[520px] w-full resize-y border-0 bg-transparent py-4 text-body leading-body text-ink outline-none placeholder:text-ink-meta max-md:min-h-[360px]"
          />
        </section>
        <!-- 预览面板：服务端渲染并净化的 HTML，直接 v-html（XSS 防线在 MarkdownRenderer） -->
        <div
          v-show="previewMode || splitPreview"
          class="cl-editor-pane cl-editor-preview min-h-[520px] py-4 max-md:min-h-[360px]"
          aria-label="正文预览"
          :aria-busy="previewLoading"
        >
          <p class="mb-4 text-caption text-ink-meta" role="status">{{ previewLoading ? '预览更新中…' : '正文预览' }}</p>
          <p v-if="!form.contentMd.trim()" class="text-note leading-body text-ink-meta">
            开始输入正文，此处将实时展示渲染效果…
          </p>
          <p v-else-if="previewLoading && !previewHtml" class="text-note text-ink-meta">渲染中…</p>
          <p v-else-if="previewError" class="text-note text-ink-meta">{{ previewError }}</p>
          <div v-else class="markdown-body" v-highlight v-html="renderedPreviewHtml" />
        </div>
      </div>
      <p class="pb-6 text-caption leading-body text-ink-meta">
        <template v-if="editing">可修改标题、正文和标签，版块与发布身份保持不变。保存后返回帖子详情。</template>
        <template v-else>预览与发布后的正文一致。草稿保存版块、标题和正文，仅限此浏览器。发布内容须符合社区规范。</template>
      </p>
    </div>
  </div>
</template>

<style scoped>
.cl-publish {
  max-width: var(--cl-editor);
  margin-inline: auto;
}

.cl-publish-submit {
  margin-left: auto;
}

.cl-board-select {
  --el-select-width: 180px;
  flex: 0 0 180px;
}

.cl-draft-status {
  margin-left: auto;
}

.cl-draft-status + .cl-publish-submit {
  margin-left: 0;
}

.cl-draft-status--failed {
  color: var(--cl-color-danger-strong);
}

.cl-editor-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--cl-space-5);
}

.cl-editor-workspace--split {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.cl-editor-pane {
  min-width: 0;
}

.cl-preview-toggle {
  width: auto;
  padding-inline: var(--cl-space-2);
  white-space: nowrap;
}

.cl-editor-workspace--split .cl-editor-preview {
  border-left: var(--cl-hairline) solid var(--cl-border-base);
  padding-left: var(--cl-space-5);
}

@media (max-width: 767px) {
  .cl-board-select {
    --el-select-width: 100%;
    flex: 1 1 0;
    min-width: 0;
  }

  .cl-draft-status {
    order: 1;
    flex-basis: 100%;
    margin-left: 0;
  }
}

/* 无边框编辑器（CR-171）：标题与正文用 outline-none 去掉了浏览器焦点环，键盘用户看不到落点。
   只补一个不占布局的内描边外轮廓，保持沉浸式外观。 */
.cl-editor-title:focus-visible,
.cl-editor-body:focus-visible {
  outline: 2px solid var(--cl-color-primary-strong);
  outline-offset: 2px;
  border-radius: var(--cl-radius-sm);
}
</style>
