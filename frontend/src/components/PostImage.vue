<script setup lang="ts">
import { onScopeDispose, ref, watch } from 'vue'
import { useAuthStore } from '../stores/auth'
import { isPostImageUrl, readPostImage } from '../api/postImages'
const props = defineProps<{ src: string; alt?: string }>()
const auth = useAuthStore()
const resolved = ref('')
let objectUrl = ''
let cancel: AbortController | undefined
watch([() => props.src, () => auth.token], async ([src], _, onCleanup) => {
  cancel?.abort()
  resolved.value = ''
  if (objectUrl) URL.revokeObjectURL(objectUrl)
  objectUrl = ''
  const controller = new AbortController()
  cancel = controller
  onCleanup(() => controller.abort())
  if (!isPostImageUrl(src)) { resolved.value = src; return }
  try {
    const blob = await readPostImage(src, controller.signal)
    if (controller.signal.aborted) return
    objectUrl = URL.createObjectURL(blob)
    resolved.value = objectUrl
  } catch { /* An unavailable cover keeps its reserved layout without another request. */ }
}, { immediate: true })
onScopeDispose(() => { cancel?.abort(); if (objectUrl) URL.revokeObjectURL(objectUrl) })
</script>
<template>
  <img :src="resolved || undefined" :alt="alt || ''" />
</template>
