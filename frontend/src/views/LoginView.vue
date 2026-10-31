<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { IconName } from '../assets/icons'
import { useAuthStore } from '../stores/auth'
import { sendCaptcha, verifyStudent } from '../api/auth'
import { useCountdown } from '../composables/useCountdown'
import SvgIcon from '../components/SvgIcon.vue'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const tab = ref<'login' | 'register'>('login')

/** 左侧品牌区事实（CR-161：原为与标题重复的三个卖点，改为可核验事实）；icon 显式标注类型，否则推成 string 传不进 SvgIcon */
const values: { icon: IconName; title: string }[] = [
  { icon: 'school', title: '仅限本校学生' },
  { icon: 'circle-check', title: '学籍核验注册' },
  { icon: 'list', title: '6 个固定版块' },
]

const loginForm = reactive({ account: '', code: '' })
/** 校验失败时把焦点送回字段（CR-173）：只弹 toast 不移动焦点，键盘 / 读屏用户不知道落在哪 */
const accountRef = ref<{ focus: () => void } | null>(null)
const codeRef = ref<{ focus: () => void } | null>(null)
const reg = reactive({
  step: 1 as 1 | 2,
  ticket: '',
  studentId: '',
  name: '',
  contact: '',
  code: '',
  nickname: '',
})

/** 联系方式输入提示（F-011 / CR-093）：邮箱与手机号二合一 */
const contactLabel = '邮箱 / 手机号'

const loginCaptcha = useCountdown()
const regCaptcha = useCountdown()

function errorMessage(e: unknown, fallback: string) {
  return e instanceof Error ? e.message : fallback
}

async function doLogin() {
  const account = loginForm.account.trim()
  if (!account) {
    ElMessage.warning('请输入邮箱或手机号')
    accountRef.value?.focus()
    return
  }
  if (!/^\d{6}$/.test(loginForm.code.trim())) {
    ElMessage.warning('请输入 6 位验证码')
    codeRef.value?.focus()
    return
  }
  try {
    await auth.login(account, loginForm.code.trim())
    ElMessage.success('登录成功')
    router.push(String(route.query.redirect || '/'))
  } catch (e) {
    ElMessage.error(errorMessage(e, '登录失败'))
  }
}

async function doVerify() {
  try {
    const res = await verifyStudent(reg.studentId, reg.name)
    reg.ticket = res.ticket
    reg.step = 2
    ElMessage.success('学籍核验通过，请继续完成注册')
  } catch (e) {
    // 后端对学号不存在 / 姓名不匹配 / 已注册统一返回同一提示（防名册枚举）
    ElMessage.error(errorMessage(e, '学籍信息校验未通过'))
  }
}

async function doRegister() {
  try {
    await auth.register({ ticket: reg.ticket, contact: reg.contact, code: reg.code, nickname: reg.nickname })
    ElMessage.success('注册成功')
    router.push('/')
  } catch (e) {
    ElMessage.error(errorMessage(e, '注册失败'))
  }
}
</script>

<template>
  <div
    class="mx-auto flex w-full max-w-[960px] flex-col gap-5 py-8 md:flex-row md:items-stretch md:gap-9 md:py-20"
  >
    <!-- 品牌区（CR-052：桌面左栏，窄屏折叠为顶部品牌条；CR-161：去渐变改纯色浅底，
         收敛为「渐变仅首页 hero 一处」） -->
    <section
      class="bg-primary-soft flex flex-col justify-center rounded-lg border-[0.5px] border-line p-6 md:w-[360px] md:flex-none md:p-10"
    >
      <div class="flex items-center gap-2.5">
        <img src="/logo.svg" alt="Campus-Link" class="h-9 w-auto" width="36" height="36" />
        <span class="text-title font-medium text-ink">Campus-Link</span>
      </div>
      <h2 class="mt-5 text-title-lg font-medium leading-tight text-ink">
        问有所答<br />学有同伴 · 求职有路
      </h2>
      <p class="mt-3 text-note leading-body text-ink-regular">
        重庆工程学院计算机专业学生的垂直交流论坛，学籍核验注册，仅本校同学可见。
      </p>
      <ul class="mt-6 flex flex-col gap-3.5">
        <li v-for="v in values" :key="v.title" class="flex items-start gap-2.5 text-note text-ink-regular">
          <SvgIcon :name="v.icon" :size="16" class="mt-0.5 text-primary-strong" />
          <span class="font-medium text-ink">{{ v.title }}</span>
        </li>
      </ul>
    </section>

    <!-- 表单区（逻辑与字段保持原样，仅换外壳与标题文案） -->
    <section class="min-w-0 flex-1">
      <el-card shadow="never" class="h-full">
        <h1 class="mb-3 text-title-lg font-medium text-ink">
          {{ tab === 'login' ? '欢迎回来' : '加入 Campus-Link' }}
        </h1>

        <el-tabs v-model="tab">
          <el-tab-pane label="登录" name="login">
            <el-form label-position="top" @submit.prevent>
              <el-form-item :label="contactLabel">
                <el-input ref="accountRef" v-model="loginForm.account" placeholder="注册时使用的邮箱或手机号" />
              </el-form-item>
              <el-form-item label="验证码">
                <div class="flex w-full gap-2">
                  <el-input ref="codeRef" v-model="loginForm.code" placeholder="6 位验证码" maxlength="6" />
                  <el-button
                    :disabled="loginCaptcha.remaining.value > 0 || loginCaptcha.sending.value"
                    @click="loginCaptcha.run(() => sendCaptcha(loginForm.account))"
                  >
                    {{ loginCaptcha.remaining.value > 0 ? `${loginCaptcha.remaining.value}s` : '发送验证码' }}
                  </el-button>
                </div>
              </el-form-item>
              <el-button type="primary" class="w-full" @click="doLogin">登录</el-button>
            </el-form>
            <p class="mt-4 text-center text-body text-ink-regular">
              还没有账号？
              <button type="button" class="text-link hover:underline" @click="tab = 'register'">去注册</button>
            </p>
          </el-tab-pane>

          <el-tab-pane label="注册" name="register">
            <el-steps :active="reg.step - 1" finish-status="success" class="mb-4">
              <el-step title="学籍核验" />
              <el-step title="邮箱 / 手机号验证" />
            </el-steps>

            <el-form v-if="reg.step === 1" label-position="top" @submit.prevent>
              <el-form-item label="学号">
                <el-input v-model="reg.studentId" placeholder="请输入 9 位数字学号" maxlength="9" />
                <p class="mt-1 w-full text-caption text-ink-meta">将与教务学籍名册比对核验（仅本校）</p>
              </el-form-item>
              <el-form-item label="姓名">
                <el-input v-model="reg.name" placeholder="请输入真实姓名" />
                <p class="mt-1 w-full text-caption text-ink-meta">
                  中文名（2~16 汉字，可含 ·）或外文名，须与学籍信息一致
                </p>
              </el-form-item>
              <el-button type="primary" class="w-full" @click="doVerify">下一步：邮箱 / 手机号验证</el-button>
            </el-form>

            <el-form v-else label-position="top" @submit.prevent>
              <el-form-item :label="contactLabel">
                <el-input v-model="reg.contact" placeholder="邮箱或手机号，用于登录，仅本人可见" />
                <p class="mt-1 w-full text-caption text-ink-meta">
                  支持邮箱或 11 位手机号；验证码将发送到对应渠道
                </p>
              </el-form-item>
              <el-form-item label="验证码">
                <div class="flex w-full gap-2">
                  <el-input v-model="reg.code" placeholder="6 位验证码" maxlength="6" />
                  <el-button
                    :disabled="regCaptcha.remaining.value > 0 || regCaptcha.sending.value"
                    @click="regCaptcha.run(() => sendCaptcha(reg.contact))"
                  >
                    {{ regCaptcha.remaining.value > 0 ? `${regCaptcha.remaining.value}s` : '发送验证码' }}
                  </el-button>
                </div>
              </el-form-item>
              <el-form-item label="昵称">
                <el-input v-model="reg.nickname" placeholder="社区展示昵称（2~32 字符）" maxlength="32" />
              </el-form-item>
              <el-button type="primary" class="w-full" @click="doRegister">完成注册</el-button>
              <p class="mt-2 w-full text-caption text-ink-meta">核验票据 5 分钟内有效，过期请返回上一步重新核验。</p>
            </el-form>

            <p class="mt-4 text-center text-body text-ink-regular">
              已有账号？
              <button type="button" class="text-link hover:underline" @click="tab = 'login'">直接登录</button>
            </p>
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </section>
  </div>
</template>
