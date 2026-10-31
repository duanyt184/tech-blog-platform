<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElAlert, ElButton, ElForm, ElFormItem, ElInput } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import 'element-plus/es/components/base/style/css'
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/form/style/css'
import 'element-plus/es/components/input/style/css'
import { ApiError } from '../api/http'
import { createArticle } from '../api/articles'
import { useAuth } from '../composables/useAuth'

/**
 * 写文章页（Issue #4）。
 * 该路由在 router 里标记了 meta.requiresAuth，未登录会先被送去登录页。
 * 表单只收集标题与正文；真正的校验在后端（空内容会被 400 拒绝）。
 */
const router = useRouter()
const { user } = useAuth()

const formRef = ref<FormInstance>()
const form = reactive({ title: '', content: '' })
const submitting = ref(false)
const errorMessage = ref('')

const rules: FormRules = {
  title: [
    { required: true, message: '请输入标题', trigger: 'blur' },
    { max: 255, message: '标题不能超过 255 个字符', trigger: 'blur' },
  ],
  content: [
    { required: true, message: '请输入正文', trigger: 'blur' },
    { max: 20000, message: '正文不能超过 20000 个字符', trigger: 'blur' },
  ],
}

const contentLength = computed(() => form.content.length)

async function handleSubmit() {
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
    const article = await createArticle({ title: form.title.trim(), content: form.content.trim() })
    // 发布成功后跳到文章详情页（详情页由 Issue #3 实现，这里先确保 id 正确返回）
    await router.replace(`/article/${article.id}`)
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.firstFieldMessage : '发布失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="page-container tb-page">
    <header>
      <p class="tb-page__eyebrow">创作</p>
      <h1 class="tb-page__title">写文章</h1>
      <p class="tb-page__desc">
        以 <strong>{{ user?.username }}</strong> 的身份发布一篇纯文本文章，
        发布后会出现在文章广场。
      </p>
    </header>

    <div class="write-card">
      <ElAlert
        v-if="errorMessage"
        :title="errorMessage"
        type="error"
        show-icon
        :closable="false"
        class="write-alert"
      />

      <ElForm
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        size="large"
        @submit.prevent="handleSubmit"
      >
        <ElFormItem label="标题" prop="title">
          <ElInput
            v-model="form.title"
            placeholder="给文章起个标题"
            maxlength="255"
            show-word-limit
            clearable
          />
        </ElFormItem>

        <ElFormItem label="正文" prop="content">
          <ElInput
            v-model="form.content"
            type="textarea"
            :rows="12"
            placeholder="写点想分享的内容…（纯文本，换行会被保留）"
            maxlength="20000"
            resize="vertical"
          />
        </ElFormItem>

        <div class="write-footer">
          <span class="write-footer__hint">{{ contentLength }} / 20000 字</span>
          <ElButton type="primary" size="large" native-type="submit" :loading="submitting">
            {{ submitting ? '发布中…' : '发布' }}
          </ElButton>
        </div>
      </ElForm>
    </div>
  </div>
</template>

<style scoped>
.write-card {
  width: 100%;
  max-width: 760px;
  padding: 28px 28px 20px;
  border: 1px solid var(--tb-border, #e5e7eb);
  border-radius: var(--tb-radius, 14px);
  background: var(--tb-surface, #ffffff);
  box-shadow: var(--tb-shadow, 0 12px 30px rgba(15, 23, 42, 0.07));
}

.write-alert {
  margin-bottom: 16px;
}

.write-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 16px;
  margin-top: 4px;
}

.write-footer__hint {
  font-size: 13px;
  color: var(--tb-muted, #64748b);
}

@media (max-width: 480px) {
  .write-card {
    padding: 20px 16px 16px;
  }
}
</style>
