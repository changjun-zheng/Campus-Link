<script setup lang="ts">
/**
 * 举报对话框（F-SAFE-002 / CR-084；CR-174 拆为异步组件）
 * 从 PostDetailView 拆出后，el-dialog / el-select / el-form-item 只在首次点「举报」时加载，
 * 不再进入详情页首包。
 */
import { reactive, ref } from 'vue'
import { submitReport } from '../api/admin'

const props = defineProps<{
  targetType: 'POST' | 'REPLY'
  targetId: number
}>()
const visible = defineModel<boolean>({ required: true })
const emit = defineEmits<{ submitted: [type: 'POST' | 'REPLY'] }>()

const REPORT_REASONS = [
  { value: 'ILLEGAL', label: '违法违规' },
  { value: 'HARASS', label: '骚扰广告' },
  { value: 'COPYRIGHT', label: '侵权' },
  { value: 'SPAM', label: '低质灌水' },
  { value: 'OTHER', label: '其他' },
]

const form = reactive({ reason: 'SPAM', detail: '' })
const submitting = ref(false)

async function submit() {
  submitting.value = true
  try {
    await submitReport({
      targetType: props.targetType,
      targetId: props.targetId,
      reason: form.reason,
      detail: form.detail.trim() || undefined,
    })
    ElMessage.success('举报已提交，运营将尽快处理')
    visible.value = false
    emit('submitted', props.targetType)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '举报提交失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" title="提交举报" width="440px">
    <el-form label-position="top">
      <el-form-item label="举报理由">
        <el-select v-model="form.reason" aria-label="举报理由" class="w-full">
          <el-option v-for="r in REPORT_REASONS" :key="r.value" :label="r.label" :value="r.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="补充说明（可选）">
        <el-input
          v-model="form.detail"
          type="textarea"
          :rows="3"
          maxlength="1024"
          show-word-limit
          placeholder="补充问题描述（可选）"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="danger" :loading="submitting" @click="submit">提交举报</el-button>
    </template>
  </el-dialog>
</template>
