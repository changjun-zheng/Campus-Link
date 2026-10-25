<script setup lang="ts">
/**
 * 发起组队（F-TEAM-001 / CR-137）：方向为固定枚举，人数 2~20（含发起人），联系方式仅成员可见。
 *
 * <p>简介走服务端 Markdown 渲染（与发帖同一条 ADR-005 链路）；预览按钮手动触发，不做实时防抖，
 * 避免把发帖页那套重量级编辑器状态搬进一个轻表单。
 */
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { createTeam, TEAM_DIRECTIONS, type TeamDirectionCode } from '../api/teams'
import { previewMarkdown } from '../api/markdown'
import SvgIcon from '../components/SvgIcon.vue'

const router = useRouter()

const form = reactive({
  title: '',
  descriptionMd: '',
  direction: '' as TeamDirectionCode | '',
  target: '',
  capacity: 4,
  contact: '',
  deadline: '',
})

const submitting = ref(false)
const previewLoading = ref(false)
const previewHtml = ref('')
const previewError = ref('')
const showPreview = ref(false)

function validate(): string {
  if (form.title.trim().length < 2) return '标题至少 2 个字'
  if (form.descriptionMd.trim().length < 1) return '请填写组队简介'
  if (!form.direction) return '请选择方向'
  if (form.capacity < 2 || form.capacity > 20) return '人数须在 2~20 之间'
  if (form.contact.trim().length < 5) return '联系方式至少 5 个字符'
  if (form.contact.includes('<') || form.contact.includes('>')) return '联系方式不能包含尖括号'
  return ''
}

async function onPreview() {
  if (!form.descriptionMd.trim()) {
    previewError.value = '请先填写组队简介'
    return
  }
  previewLoading.value = true
  previewError.value = ''
  try {
    const res = await previewMarkdown(form.descriptionMd)
    previewHtml.value = res.html
    showPreview.value = true
  } catch (e) {
    previewError.value = e instanceof Error ? e.message : '预览加载失败'
  } finally {
    previewLoading.value = false
  }
}

async function onSubmit() {
  const message = validate()
  if (message) {
    ElMessage.error(message)
    return
  }
  submitting.value = true
  try {
    const created = await createTeam({
      title: form.title.trim(),
      descriptionMd: form.descriptionMd.trim(),
      direction: form.direction as TeamDirectionCode,
      target: form.target.trim() || undefined,
      capacity: form.capacity,
      contact: form.contact.trim(),
      deadline: form.deadline || undefined,
    })
    ElMessage.success('组队已发布')
    await router.push(`/teams/${created.id}`)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '发布失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <!-- TRANSITION-SINGLE-ROOT -->
  <div>
    <el-card shadow="never" class="mb-5">
      <h2 class="text-title-lg font-medium text-ink">发起组队</h2>
      <p class="mt-1.5 text-note text-ink-meta">
        发布后其他同学可申请加入，由你审批；满员自动停止招募，也可随时关闭（关闭后不可重开）。
      </p>
    </el-card>

    <el-card shadow="never">
      <el-form label-position="top">
        <el-form-item label="组队标题">
          <el-input v-model="form.title" maxlength="60" show-word-limit placeholder="例如：蓝桥杯算法组队，冲省一" />
        </el-form-item>

        <div class="grid gap-4 md:grid-cols-2">
          <el-form-item label="方向">
            <el-select v-model="form.direction" placeholder="请选择方向" class="w-full">
              <el-option v-for="d in TEAM_DIRECTIONS" :key="d.code" :label="d.label" :value="d.code" />
            </el-select>
          </el-form-item>

          <el-form-item label="目标赛事 / 项目（选填）">
            <el-input v-model="form.target" maxlength="64" placeholder="例如：蓝桥杯、ACM 校赛" />
          </el-form-item>

          <el-form-item label="总人数（含你自己）">
            <el-input-number v-model="form.capacity" :min="2" :max="20" />
          </el-form-item>

          <el-form-item label="招募截止日（选填）">
            <el-date-picker
              v-model="form.deadline"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="仅用于展示"
              class="w-full"
            />
          </el-form-item>
        </div>

        <el-form-item label="联系方式">
          <el-input v-model="form.contact" maxlength="100" placeholder="例如：微信 / QQ / 邮箱" />
          <p class="mt-1 text-caption text-ink-meta">仅发起人与已加入成员可见，不会出现在广场列表。</p>
        </el-form-item>

        <el-form-item label="组队简介（支持 Markdown）">
          <el-input
            v-model="form.descriptionMd"
            type="textarea"
            :rows="8"
            maxlength="2000"
            show-word-limit
            placeholder="想找什么样的队友、准备做什么、时间安排……"
          />
        </el-form-item>

        <div class="mb-4 flex items-center gap-2">
          <el-button :loading="previewLoading" @click="onPreview">
            <SvgIcon name="eye" :size="13" class="mr-1" />
            预览简介
          </el-button>
          <el-text v-if="previewError" type="danger" size="small">{{ previewError }}</el-text>
        </div>

        <el-card v-if="showPreview" shadow="never" class="mb-4 bg-page">
          <!-- 服务端 MarkdownRenderer + jsoup 白名单 -->
          <div class="markdown-body" v-html="previewHtml" />
        </el-card>

        <div class="flex items-center gap-2">
          <el-button type="primary" :loading="submitting" @click="onSubmit">发布组队</el-button>
          <el-button @click="router.back()">取消</el-button>
        </div>
      </el-form>
    </el-card>
  </div>
</template>
