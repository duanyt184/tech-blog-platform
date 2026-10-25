<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElAlert, ElButton, ElForm, ElFormItem, ElInput } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import 'element-plus/es/components/base/style/css'
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/form/style/css'
import 'element-plus/es/components/input/style/css'
import '../styles/auth.css'
import { ApiError } from '../api/http'
import { useAuth } from '../composables/useAuth'

const router = useRouter()
const route = useRoute()
const { loginWithPassword } = useAuth()

const formRef = ref<FormInstance>()
const form = reactive({ identifier: '', password: '' })
const submitting = ref(false)
const errorMessage = ref('')

const rules: FormRules = {
  identifier: [{ required: true, message: '请输入用户名或邮箱', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleSubmit() {
  // 回车会同时触发 submit 事件，这里防重复提交
  if (submitting.value || !formRef.value) {
    return
  }
  // 前端校验只是为了少发一次请求，真正的校验仍在后端
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  submitting.value = true
  errorMessage.value = ''
  try {
    await loginWithPassword({ identifier: form.identifier.trim(), password: form.password })
    // 登录前想去的页面（例如 /write）会带在 redirect 上，登录后送回去
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.replace(redirect)
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.firstFieldMessage : '登录失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="auth-page">
    <div class="auth-card">
      <header class="auth-card__head">
        <p class="auth-card__eyebrow">码上 · 技术分享社区</p>
        <h1 class="auth-card__title">欢迎回来</h1>
        <p class="auth-card__subtitle">登录后可以投稿、管理自己的文章</p>
      </header>

      <ElAlert
        v-if="errorMessage"
        :title="errorMessage"
        type="error"
        show-icon
        :closable="false"
        class="auth-alert"
      />

      <ElForm
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        size="large"
        @submit.prevent="handleSubmit"
      >
        <ElFormItem label="用户名或邮箱" prop="identifier">
          <ElInput
            v-model="form.identifier"
            placeholder="请输入用户名或邮箱"
            autocomplete="username"
            clearable
          />
        </ElFormItem>
        <ElFormItem label="密码" prop="password">
          <ElInput
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            autocomplete="current-password"
            show-password
          />
        </ElFormItem>

        <ElButton
          type="primary"
          class="auth-submit"
          size="large"
          native-type="submit"
          :loading="submitting"
        >
          登录
        </ElButton>
      </ElForm>

      <p class="auth-card__foot">
        还没有账号？
        <RouterLink :to="{ path: '/register', query: route.query }">立即注册</RouterLink>
      </p>
    </div>
  </section>
</template>
