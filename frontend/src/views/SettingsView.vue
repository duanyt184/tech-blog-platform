<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import UserAvatar from '../components/UserAvatar.vue'
import { refreshAvatar } from '../composables/useAvatar'
import { http } from '../api/http'
import { useAuth } from '../composables/useAuth'
import { errorMessage } from '../utils/articles'
import { useTheme } from '../composables/useTheme'
const { theme } = useTheme()
const { user } = useAuth()
const avatarFile = ref<File | null>(null)
const avatarPreview = ref('')
const avatarBusy = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)
function clearAvatarSelection() {
  if (avatarPreview.value) URL.revokeObjectURL(avatarPreview.value)
  avatarPreview.value = ''
  avatarFile.value = null
  if (fileInput.value) fileInput.value.value = ''
}
onBeforeUnmount(clearAvatarSelection)
function chooseAvatar(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  clearAvatarSelection()
  if (!file) return
  error.value = ''
  notice.value = ''
  if (
    !['image/png', 'image/jpeg'].includes(file.type) ||
    file.size > 2 * 1024 * 1024
  ) {
    error.value = '请选择不超过 2 MB 的 PNG 或 JPEG 图片'
    return
  }
  avatarFile.value = file
  avatarPreview.value = URL.createObjectURL(file)
}
async function saveAvatar(remove = false) {
  if (!user.value || avatarBusy.value || (!remove && !avatarFile.value)) return
  avatarBusy.value = true
  error.value = ''
  try {
    if (remove) {
      await http.delete('/my/avatar')
      refreshAvatar(user.value.id)
    } else {
      const data = new FormData()
      data.append('file', avatarFile.value!)
      const response = await http.post('/my/avatar', data, {
        headers: { 'Content-Type': undefined },
      })
      refreshAvatar(user.value.id, response.data.revision)
    }
    clearAvatarSelection()
    notice.value = remove
      ? '已恢复默认头像。'
      : '头像已保存，帖子与个人主页已同步更新。'
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    avatarBusy.value = false
  }
}
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
      {{ error }}
      <button v-if="!loaded" type="button" @click="load">重新加载</button>
    </p>
    <p v-if="notice" class="success-message" role="status">{{ notice }}</p>
    <section v-if="user" class="avatar-settings tb-card" aria-label="头像设置">
      <div class="avatar settings-avatar">
        <img
          v-if="avatarPreview"
          :src="avatarPreview"
          alt="待保存的头像预览"
          class="avatar-image"
        /><UserAvatar v-else :user-id="user.id" :username="user.username" />
      </div>
      <div>
        <h2>头像</h2>
        <p class="field-hint">
          PNG 或 JPEG，最大 2 MB、4096 × 4096 像素。图片会居中裁剪为正方形。
        </p>
        <input
          ref="fileInput"
          type="file"
          accept="image/png,image/jpeg"
          class="sr-only"
          aria-label="选择头像图片"
          :disabled="avatarBusy"
          @change="chooseAvatar"
        />
        <div class="avatar-settings-actions">
          <button
            type="button"
            class="tb-btn"
            :disabled="avatarBusy"
            @click="fileInput?.click()"
          >
            更换头像
          </button>
          <template v-if="avatarFile"
            ><button
              type="button"
              class="tb-btn tb-btn--primary"
              :disabled="avatarBusy"
              @click="saveAvatar(false)"
            >
              {{ avatarBusy ? '上传中…' : '保存头像' }}</button
            ><button
              type="button"
              class="tb-btn"
              :disabled="avatarBusy"
              @click="clearAvatarSelection"
            >
              取消
            </button></template
          >
          <button
            v-else
            type="button"
            class="composer-text-button"
            :disabled="avatarBusy"
            @click="saveAvatar(true)"
          >
            恢复默认头像
          </button>
        </div>
      </div>
    </section>
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
