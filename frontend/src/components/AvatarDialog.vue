<script setup lang="ts">
/**
 * 更换头像弹窗（CR-191 / F-ACC-007h）：预置 8 宫格 + 本地上传。
 *
 * <p>预设路径由后端 `GET /users/avatar-presets` 下发，前端不硬编码（两处列表必然漂移）；
 * 选中即提交，成功后关闭弹窗并由父组件 `auth.setUser(updated)` 全站生效。
 *
 * <p>本地只做体验性预缩放（最长边 1024、转 JPEG），**服务端才是防线**：格式 / 2MB / 4096px
 * 与重编码仍在后端校验，前端的类型与体积提示不替代它。
 */
import { onBeforeUnmount, ref, watch } from 'vue'
import { fetchAvatarPresets, selectAvatarPreset, uploadAvatar } from '../api/auth'
import type { UserInfo } from '../types'
import UserAvatar from './UserAvatar.vue'

const props = defineProps<{
  modelValue: boolean
  nickname?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  updated: [user: UserInfo]
}>()

const activeTab = ref<'preset' | 'upload'>('preset')
const presets = ref<string[]>([])
const loadingPresets = ref(false)
const saving = ref(false)
const error = ref('')

const fileInput = ref<HTMLInputElement | null>(null)
const previewUrl = ref('')
const pendingFile = ref<File | null>(null)
let objectUrl: string | null = null

watch(
  () => props.modelValue,
  (open) => {
    if (!open) {
      resetUpload()
      return
    }
    activeTab.value = 'preset'
    error.value = ''
    if (presets.value.length === 0) {
      void loadPresets()
    }
  },
  // immediate：父组件用 v-if 条件挂载本弹窗，首挂时 modelValue 已是 true——
  // 不带 immediate 的 watch 不会触发，预设列表永远不加载（浏览器验收发现的缺陷）
  { immediate: true },
)

onBeforeUnmount(resetUpload)

async function loadPresets() {
  loadingPresets.value = true
  try {
    presets.value = await fetchAvatarPresets()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '头像列表加载失败'
  } finally {
    loadingPresets.value = false
  }
}

async function applyPreset(url: string) {
  if (saving.value) return
  saving.value = true
  error.value = ''
  try {
    const updated = await selectAvatarPreset(url)
    emit('updated', updated)
    ElMessage.success('头像已更新')
    close()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '头像更新失败'
  } finally {
    saving.value = false
  }
}

function pickFile() {
  fileInput.value?.click()
}

async function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  error.value = ''
  if (!/^image\/(jpeg|png)$/.test(file.type)) {
    error.value = '只支持 JPEG / PNG 图片'
    return
  }
  if (file.size > 2 * 1024 * 1024) {
    error.value = '图片不能超过 2MB'
    return
  }
  resetUpload()
  pendingFile.value = await prepareFile(file)
  objectUrl = URL.createObjectURL(pendingFile.value)
  previewUrl.value = objectUrl
}

async function saveUpload() {
  if (!pendingFile.value || saving.value) return
  saving.value = true
  error.value = ''
  try {
    const updated = await uploadAvatar(pendingFile.value)
    emit('updated', updated)
    ElMessage.success('头像已更新')
    close()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '头像上传失败'
  } finally {
    saving.value = false
  }
}

function close() {
  emit('update:modelValue', false)
}

function resetUpload() {
  pendingFile.value = null
  previewUrl.value = ''
  if (objectUrl) {
    URL.revokeObjectURL(objectUrl)
    objectUrl = null
  }
}

/** 体验性预缩放：最长边 1024 + 转 JPEG；canvas 不可用时回退原文件（服务端仍会校验） */
async function prepareFile(file: File): Promise<File> {
  try {
    const bitmap = await createImageBitmap(file)
    const maxSide = 1024
    const scale = Math.min(1, maxSide / Math.max(bitmap.width, bitmap.height))
    if (scale === 1 && file.size <= 512 * 1024) {
      bitmap.close()
      return file
    }
    const canvas = document.createElement('canvas')
    canvas.width = Math.round(bitmap.width * scale)
    canvas.height = Math.round(bitmap.height * scale)
    const context = canvas.getContext('2d')
    if (!context) {
      bitmap.close()
      return file
    }
    context.fillStyle = '#ffffff'
    context.fillRect(0, 0, canvas.width, canvas.height)
    context.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
    bitmap.close()
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.9))
    return blob ? new File([blob], 'avatar.jpg', { type: 'image/jpeg' }) : file
  } catch {
    return file
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="更换头像"
    width="480px"
    append-to-body
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-tabs v-model="activeTab">
      <el-tab-pane label="选择预设" name="preset">
        <div v-if="loadingPresets" class="py-6 text-center text-note text-ink-meta">加载中…</div>
        <div v-else class="grid grid-cols-4 gap-3 py-1">
          <button
            v-for="url in presets"
            :key="url"
            type="button"
            class="flex items-center justify-center rounded-lg p-2 transition-colors duration-fast ease-standard hover:bg-bg-hover focus-visible:outline focus-visible:outline-2 focus-visible:outline-primary-strong disabled:opacity-60"
            :disabled="saving"
            :aria-label="`选择预设头像 ${url}`"
            @click="applyPreset(url)"
          >
            <img :src="url" alt="" class="h-16 w-16 rounded-full object-cover" />
          </button>
        </div>
        <p class="mt-2 text-caption leading-body text-ink-meta">点击即生效，全站头像同步更新。</p>
      </el-tab-pane>

      <el-tab-pane label="上传图片" name="upload">
        <div class="flex flex-col items-center gap-3 py-2">
          <UserAvatar :src="previewUrl" :name="nickname" :size="96" />
          <input
            ref="fileInput"
            type="file"
            accept="image/jpeg,image/png"
            class="hidden"
            @change="onFileChange"
          />
          <el-button :disabled="saving" @click="pickFile">
            {{ pendingFile ? '重新选择' : '选择本地图片' }}
          </el-button>
          <p class="text-caption leading-body text-ink-meta">
            JPEG / PNG，≤2MB、≤4096×4096；提交后服务端居中裁方并压缩为 512×512。
          </p>
          <el-button type="primary" :loading="saving" :disabled="!pendingFile" @click="saveUpload">
            保存头像
          </el-button>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-alert v-if="error" class="mt-2" type="error" :closable="false" show-icon :title="error" />
  </el-dialog>
</template>
