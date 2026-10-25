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
const { registerAccount } = useAuth()

const formRef = ref<FormInstance>()
const form = reactive({ username: '', email: '', password: '', confirmPassword: '' })
const submitting = ref(false)
const errorMessage = ref('')

// 规则与后端 RegisterRequest 的校验保持一致，避免「前端能过、后端 400」的体验割裂
const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]{3,20}$/, message: '用户名需为 3-20 位字母、数字或下划线', trigger: 'blur' },
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 64, message: '密码长度需为 8-64 个字符', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.password) {
          callback(new Error('两次输入的密码不一致'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
}

async function handleSubmit() {
  // 回车会同时触发 submit 事件，这里防重复提交
  if (submitting.value || !formRef.value) {
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  submitting.value = true
  errorMessage.value = ''
  try {
    // 注册成功后立刻用同一份凭据登录（详见 useAuth.registerAccount）
    await registerAccount({
      username: form.username.trim(),
      email: form.email.trim(),
      password: form.password,
      confirmPassword: form.confirmPassword,
    })
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.replace(redirect)
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.firstFieldMessage : '注册失败，请稍后重试'
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
        <h1 class="auth-card__title">创建账号</h1>
        <p class="auth-card__subtitle">注册后即可投稿，把踩过的坑写成别人的捷径</p>
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
        <ElFormItem label="用户名" prop="username">
          <ElInput v-model="form.username" placeholder="3-20 位字母、数字或下划线" autocomplete="username" clearable />
        </ElFormItem>
        <ElFormItem label="邮箱" prop="email">
          <ElInput v-model="form.email" placeholder="用于接收投稿审核通知" autocomplete="email" clearable />
        </ElFormItem>
        <ElFormItem label="密码" prop="password">
          <ElInput
            v-model="form.password"
            type="password"
            placeholder="至少 8 位"
            autocomplete="new-password"
            show-password
          />
        </ElFormItem>
        <ElFormItem label="确认密码" prop="confirmPassword">
          <ElInput
            v-model="form.confirmPassword"
            type="password"
            placeholder="请再次输入密码"
            autocomplete="new-password"
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
          注册并登录
        </ElButton>
      </ElForm>

      <p class="auth-card__foot">
        已有账号？
        <RouterLink :to="{ path: '/login', query: route.query }">直接登录</RouterLink>
      </p>
    </div>
  </section>
</template>
