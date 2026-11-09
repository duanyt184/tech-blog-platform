<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { http } from '../api/http'
import { useAuth } from '../composables/useAuth'
import { errorMessage } from '../utils/articles'
import { useTheme } from '../composables/useTheme'
const { theme } = useTheme()
const { user } = useAuth()
const form = reactive({ displayName: '', bio: '' }),
  loading = ref(true),
  busy = ref(false),
  error = ref(''),
  notice = ref(''),
  loaded = ref(false)
async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await http.get(`/profiles/${user.value!.id}`)
    form.displayName = data.displayName
    form.bio = data.bio
    loaded.value = true
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    loading.value = false
  }
}
onMounted(load)
async function save() {
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    await http.put('/my/profile', form)
    notice.value = '资料已保存，用户主页已更新。'
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <section class="work-page settings-page">
    <header class="work-heading">
      <div>
        <p class="eyebrow">YOUR SPACE</p>
        <h1>个人资料</h1>
        <p>让社区认识你，也让你的主页多一点个性。</p>
      </div>
      <RouterLink :to="`/u/${user?.id}`" class="tb-btn">查看主页 ↗</RouterLink>
    </header>
    <p v-if="loading" class="empty-state">正在加载资料…</p>
    <p v-if="error" class="error-message" role="alert">
      {{ error }} <button type="button" @click="load">重新加载</button>
    </p>
    <p v-if="notice" class="success-message" role="status">{{ notice }}</p>
    <form
      v-if="!loading && loaded"
      class="writing-form tb-card"
      @submit.prevent="save"
    >
      <p class="field-hint">账号：{{ user?.username }} · {{ user?.email }}</p>
      <fieldset :disabled="busy">
        <label
          >界面主题<select v-model="theme">
            <option value="system">跟随系统</option>
            <option value="light">浅色</option>
            <option value="dark">深色</option>
          </select></label
        ><label
          >公开昵称<input
            v-model="form.displayName"
            maxlength="80"
            placeholder="你希望大家如何称呼你" /></label
        ><label
          >个人简介<textarea
            v-model="form.bio"
            maxlength="500"
            rows="6"
            placeholder="你关注的技术、正在做的项目，或想分享的故事…"
          ></textarea>
        </label>
        <p class="field-hint">昵称与简介会公开显示在用户主页，邮箱不会公开。</p>
        <button class="tb-btn tb-btn--primary" type="submit">
          {{ busy ? '正在保存…' : '保存资料' }}
        </button>
      </fieldset>
    </form>
  </section>
</template>
