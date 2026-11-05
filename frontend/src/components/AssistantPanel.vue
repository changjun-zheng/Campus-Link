<script setup lang="ts">
/**
 * 帖子选中文本问 AI 的私密面板（CR-200）。
 *
 * <p>展示边界：流式阶段只显示原始文本；`done` 帧的 `html` 是服务端
 * `MarkdownRenderer.renderForAssistant` 的净化产物，前端只对它 `v-html`，
 * 不在浏览器里二次解析 Markdown。
 */
import { computed, nextTick, onUpdated, ref, watch } from 'vue'
import SvgIcon from './SvgIcon.vue'
import type { AssistantMessage } from '../composables/useAssistant'
import { vHighlight } from '../utils/highlight'
import { injectCopyButtons } from '../utils/copyCode'

const props = defineProps<{
  selectedText: string
  messages: AssistantMessage[]
  streaming: boolean
  remainingToday: number | null
  dailyLimit: number
  quickPrompts: string[]
  maxQuestionChars: number
  ended: boolean
  quotaExhausted: boolean
}>()

const visible = defineModel<boolean>('visible', { required: true })
const input = defineModel<string>('input', { required: true })
const privacyVisible = defineModel<boolean>('privacyVisible', { required: true })

const emit = defineEmits<{
  acceptPrivacy: []
  declinePrivacy: []
  send: [question?: string]
  stop: []
  retry: []
  restart: []
  copy: [message: AssistantMessage]
}>()

const messagesRef = ref<HTMLElement | null>(null)
const inputRef = ref<{ focus: () => void } | null>(null)

const selectedLength = computed(() => props.selectedText.length)
const canSend = computed(
  () =>
    input.value.trim().length > 0 &&
    !props.streaming &&
    !props.ended &&
    !props.quotaExhausted,
)

/** 只有最后一条回答失败时才显示「重试」，避免在历史消息里出现多个重试入口 */
const retryId = computed(() => {
  const last = props.messages[props.messages.length - 1]
  return last?.role === 'assistant' && last.error ? last.id : null
})

function onKeydown(event: Event | KeyboardEvent) {
  if (!(event instanceof KeyboardEvent)) return
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing) return
  event.preventDefault()
  if (canSend.value) emit('send')
}

function scrollToBottom() {
  const el = messagesRef.value
  if (!el) return
  el.scrollTop = el.scrollHeight
}

const lastContentLength = computed(() => {
  const last = props.messages[props.messages.length - 1]
  return last ? last.content.length : 0
})

watch(
  [() => props.messages.length, lastContentLength],
  () => {
    scrollToBottom()
  },
  { flush: 'post' },
)

// `onUpdated` 是 v-html 已落 DOM 之后最可靠的时机；`injectCopyButtons` 幂等，
// 流式期间重复调用只做一次 querySelectorAll，不会重复包 wrapper。
onUpdated(() => {
  injectCopyButtons(messagesRef.value)
})

watch(visible, async (open) => {
  if (!open) return
  await nextTick()
  scrollToBottom()
  if (!privacyVisible.value) inputRef.value?.focus()
})
</script>

<template>
  <Teleport to="body">
    <div v-if="visible" class="cl-assistant-backdrop" @click="visible = false" />
    <aside
      v-if="visible"
      class="cl-assistant-panel"
      role="dialog"
      aria-modal="false"
      aria-label="AI 助手"
    >
      <header class="cl-assistant-head">
        <div class="flex min-w-0 items-center gap-2">
          <span class="cl-assistant-mark">
            <SvgIcon name="sparkles" :size="16" />
          </span>
          <span class="text-title-sm font-medium text-ink">问 AI</span>
          <el-tag size="small" effect="plain">仅你可见</el-tag>
        </div>
        <div class="flex shrink-0 items-center gap-2">
          <span v-if="remainingToday !== null" class="text-caption tabular-nums text-ink-meta">
            今日剩余 {{ remainingToday }} 次
          </span>
          <el-button link size="small" @click="visible = false">关闭</el-button>
        </div>
      </header>

      <div class="cl-assistant-selection">
        <div class="cl-assistant-selection__label">选中内容 · {{ selectedLength }} 字</div>
        <p class="cl-assistant-selection__text">{{ selectedText }}</p>
      </div>

      <div ref="messagesRef" class="cl-assistant-messages">
        <div v-if="!messages.length" class="cl-assistant-empty">
          <p class="text-body text-ink-regular">就选中的内容提问，回答只有你能看到。</p>
          <div v-if="quickPrompts.length" class="cl-assistant-quick">
            <button
              v-for="prompt in quickPrompts"
              :key="prompt"
              type="button"
              class="cl-assistant-quick__item"
              @click="emit('send', prompt)"
            >
              {{ prompt }}
            </button>
          </div>
        </div>

        <div
          v-for="message in messages"
          :key="message.id"
          class="cl-assistant-row"
          :class="message.role === 'user' ? 'is-user' : 'is-assistant'"
        >
          <template v-if="message.role === 'user'">
            <div class="cl-assistant-user">{{ message.content }}</div>
          </template>
          <template v-else>
            <div class="cl-assistant-answer">
              <div
                v-if="message.html"
                v-highlight
                class="markdown-body cl-assistant-markdown"
                v-html="message.html"
              />
              <div v-else class="cl-assistant-stream">
                {{ message.content }}<span v-if="message.streaming" class="cl-assistant-cursor" />
              </div>

              <p v-if="message.stopped" class="cl-assistant-note">
                已停止生成；本轮不会保存，后续追问基于已保存的上下文。
              </p>
              <p v-if="message.blocked" class="cl-assistant-note">
                该回答未通过内容安全检查，已替换为安全提示。
              </p>
              <div v-if="message.error" class="cl-assistant-error">
                <span class="min-w-0">{{ message.error }}</span>
                <el-button
                  v-if="message.id === retryId"
                  link
                  size="small"
                  @click="emit('retry')"
                >
                  重试
                </el-button>
              </div>

              <div v-if="message.html" class="cl-assistant-actions">
                <el-button link size="small" @click="emit('copy', message)">复制回答</el-button>
              </div>
            </div>
          </template>
        </div>

        <div v-if="ended" class="cl-assistant-ended">
          <span>会话已结束（过期或轮次用尽），请重新开始。</span>
          <el-button link size="small" type="primary" @click="emit('restart')">重新开始</el-button>
        </div>
        <p v-else-if="quotaExhausted" class="cl-assistant-ended">
          今日提问次数已用完，明天再来。
        </p>
      </div>

      <div class="cl-assistant-compose">
        <el-input
          ref="inputRef"
          v-model="input"
          type="textarea"
          :autosize="{ minRows: 2, maxRows: 5 }"
          resize="none"
          :maxlength="maxQuestionChars || undefined"
          show-word-limit
          :disabled="ended || quotaExhausted"
          placeholder="就选中内容提问…（Enter 发送，Shift+Enter 换行）"
          @keydown="onKeydown"
        />
        <div class="cl-assistant-compose__actions">
          <el-button v-if="streaming" @click="emit('stop')">停止</el-button>
          <el-button v-else type="primary" :disabled="!canSend" @click="emit('send')">发送</el-button>
        </div>
      </div>

      <p class="cl-assistant-privacy">
        选中内容与问题会发送给 DeepSeek 生成回答；回答仅你可见，不会写入楼层或公开。
      </p>
    </aside>

    <el-dialog
      v-model="privacyVisible"
      title="使用 AI 助手前"
      width="460px"
      :show-close="false"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      append-to-body
    >
      <div class="space-y-2 text-body leading-relaxed text-ink-regular">
        <p>
          你选中的正文和提出的问题会发送给第三方模型服务商
          <strong class="font-medium text-ink">DeepSeek</strong>，用于生成这次回答。
        </p>
        <ul class="list-disc space-y-1 pl-5">
          <li>回答只展示在当前页面，仅你可见；不会写入楼层、搜索或热榜。</li>
          <li>请不要选中包含身份证号、手机号、密码等敏感信息的内容。</li>
          <li>AI 可能生成不准确的内容，请结合课程资料自行判断。</li>
          <li>每人每天最多 {{ dailyLimit }} 次提问，按北京时间自然日重置。</li>
        </ul>
      </div>
      <template #footer>
        <el-button @click="emit('declinePrivacy')">暂不使用</el-button>
        <el-button type="primary" @click="emit('acceptPrivacy')">同意并继续</el-button>
      </template>
    </el-dialog>
  </Teleport>
</template>

<style scoped>
.cl-assistant-backdrop {
  display: none;
}

.cl-assistant-panel {
  position: fixed;
  top: 76px;
  right: 24px;
  bottom: 20px;
  z-index: 900;
  display: flex;
  width: 420px;
  max-width: calc(100vw - 48px);
  flex-direction: column;
  overflow: hidden;
  border: 0.5px solid var(--cl-border-base);
  border-radius: var(--cl-radius-lg);
  background: var(--cl-bg-card);
  box-shadow: var(--el-box-shadow);
}

.cl-assistant-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border-bottom: 0.5px solid var(--cl-border-divider);
}

.cl-assistant-mark {
  display: inline-grid;
  width: 28px;
  height: 28px;
  flex: none;
  place-items: center;
  border-radius: var(--cl-radius-sm);
  background: var(--cl-color-primary-soft);
  color: var(--cl-color-primary-strong);
}

.cl-assistant-selection {
  margin: 12px 14px 0;
  padding: 8px 10px;
  border: 0.5px solid var(--cl-border-base);
  border-radius: var(--cl-radius-sm);
  background: var(--cl-bg-code);
}

.cl-assistant-selection__label {
  margin-bottom: 4px;
  color: var(--cl-text-meta);
  font-size: var(--cl-font-caption);
}

.cl-assistant-selection__text {
  display: block;
  max-height: 76px;
  overflow: auto;
  color: var(--cl-text-regular);
  font-size: var(--cl-font-sm);
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.cl-assistant-messages {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  padding: 14px;
  overscroll-behavior: contain;
  scrollbar-width: thin;
  scrollbar-color: var(--cl-border-base) transparent;
}

.cl-assistant-empty {
  padding: 8px 2px;
}

.cl-assistant-quick {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.cl-assistant-quick__item {
  padding: 6px 10px;
  border: 0.5px solid var(--cl-border-base);
  border-radius: var(--cl-radius-sm);
  background: var(--cl-bg-card);
  color: var(--cl-text-regular);
  font-size: var(--cl-font-caption);
  line-height: 1.2;
  cursor: pointer;
  transition:
    color var(--cl-duration-fast) var(--cl-ease-standard),
    border-color var(--cl-duration-fast) var(--cl-ease-standard),
    background-color var(--cl-duration-fast) var(--cl-ease-standard);
}

.cl-assistant-quick__item:hover {
  border-color: color-mix(in srgb, var(--cl-color-primary) 42%, var(--cl-border-base));
  background: var(--cl-color-primary-soft);
  color: var(--cl-color-primary-strong);
}

.cl-assistant-quick__item:focus-visible {
  outline: none;
  box-shadow: var(--cl-focus-ring);
}

.cl-assistant-row + .cl-assistant-row {
  margin-top: 12px;
}

.cl-assistant-row.is-user {
  display: flex;
  justify-content: flex-end;
}

.cl-assistant-user {
  max-width: 86%;
  padding: 8px 12px;
  border-radius: var(--cl-radius-md);
  background: var(--cl-color-primary-soft);
  color: var(--cl-text-primary);
  font-size: var(--cl-font-body);
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.cl-assistant-answer {
  min-width: 0;
}

.cl-assistant-stream {
  color: var(--cl-text-primary);
  font-size: var(--cl-font-body);
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.cl-assistant-cursor {
  display: inline-block;
  width: 2px;
  height: 1em;
  margin-left: 2px;
  vertical-align: text-bottom;
  background: var(--cl-color-primary-strong);
  animation: cl-assistant-blink 1s step-end infinite;
}

@keyframes cl-assistant-blink {
  50% {
    opacity: 0;
  }
}

.cl-assistant-note {
  margin-top: 6px;
  color: var(--cl-text-meta);
  font-size: var(--cl-font-caption);
  line-height: 1.5;
}

.cl-assistant-error {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  margin-top: 8px;
  padding: 8px 10px;
  border-radius: var(--cl-radius-sm);
  background: color-mix(in srgb, var(--cl-color-danger) 8%, #fff);
  color: var(--cl-color-danger-strong);
  font-size: var(--cl-font-caption);
  line-height: 1.5;
}

.cl-assistant-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 2px;
}

.cl-assistant-ended {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 12px;
  padding: 8px 10px;
  border: 0.5px solid var(--cl-border-base);
  border-radius: var(--cl-radius-sm);
  background: var(--cl-bg-code);
  color: var(--cl-text-meta);
  font-size: var(--cl-font-caption);
  line-height: 1.5;
}

.cl-assistant-compose {
  padding: 10px 14px 6px;
  border-top: 0.5px solid var(--cl-border-divider);
}

.cl-assistant-compose__actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}

.cl-assistant-privacy {
  padding: 0 14px 10px;
  color: var(--cl-text-meta);
  font-size: var(--cl-font-caption);
  line-height: 1.5;
}

/* Markdown 回答继承全局 .markdown-body；这里只补面板内的空间与代码块复制按钮样式 */
.cl-assistant-markdown {
  font-size: var(--cl-font-body);
  line-height: 1.7;
}

.cl-assistant-markdown :deep(.cl-pre-wrap) {
  margin: 0.8em 0;
}

.cl-assistant-markdown :deep(.cl-pre-wrap > pre) {
  margin: 0;
}

.cl-assistant-markdown :deep(.cl-copy-btn) {
  position: absolute;
  top: 6px;
  right: 8px;
  z-index: 2;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 24px;
  padding: 0 7px;
  border: 0.5px solid var(--cl-border-base);
  border-radius: 4px;
  background: color-mix(in srgb, #fff 86%, transparent);
  color: var(--cl-text-regular);
  font-size: 11px;
  line-height: 1;
  cursor: pointer;
}

.cl-assistant-markdown :deep(.cl-copy-btn__icon) {
  position: relative;
  display: inline-flex;
  width: 11px;
  height: 11px;
  flex: 0 0 11px;
}

.cl-assistant-markdown :deep(.cl-copy-btn__icon > svg) {
  position: absolute;
  inset: 0;
  transition: opacity var(--cl-duration-fast) var(--cl-ease-standard);
}

.cl-assistant-markdown :deep(.cl-copy-btn__check) {
  opacity: 0;
}

.cl-assistant-markdown :deep(.cl-copy-btn.is-success) {
  border-color: color-mix(in srgb, var(--cl-color-success) 48%, var(--cl-border-base));
  color: var(--cl-color-success-strong);
}

.cl-assistant-markdown :deep(.cl-copy-btn.is-success .cl-copy-btn__icon > svg:first-child) {
  opacity: 0;
}

.cl-assistant-markdown :deep(.cl-copy-btn.is-success .cl-copy-btn__check) {
  opacity: 1;
}

.cl-assistant-markdown :deep(.cl-copy-btn.is-error) {
  border-color: color-mix(in srgb, var(--cl-color-danger) 48%, var(--cl-border-base));
  color: var(--cl-color-danger-strong);
}

@media (max-width: 767px) {
  .cl-assistant-backdrop {
    position: fixed;
    inset: 0;
    z-index: 890;
    display: block;
    background: rgb(15 23 42 / 30%);
  }

  .cl-assistant-panel {
    top: auto;
    right: 0;
    bottom: 0;
    left: 0;
    width: auto;
    max-width: none;
    height: 82svh;
    max-height: 82svh;
    border-bottom: 0;
    border-radius: var(--cl-radius-lg) var(--cl-radius-lg) 0 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .cl-assistant-cursor {
    animation: none;
  }
}
</style>
