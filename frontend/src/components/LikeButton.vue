<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { http } from '../api/http'
import { useAuth } from '../composables/useAuth'
import { errorMessage } from '../utils/articles'
const props = defineProps<{ articleId: number }>()
const emit = defineEmits<{ changed: [count: number] }>()
const { user } = useAuth()
const route = useRoute(),
  router = useRouter()
const state = ref({ count: 0, liked: false }),
  busy = ref(false),
  error = ref('')
async function load() {
  busy.value = true
  try {
    state.value = (await http.get(`/articles/${props.articleId}/likes`)).data
    error.value = ''
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    busy.value = false
  }
}
onMounted(load)
async function toggle() {
  if (busy.value) return
  if (!user.value) {
    await router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  busy.value = true
  error.value = ''
  try {
    state.value = (
      await http[state.value.liked ? 'delete' : 'put'](
        `/articles/${props.articleId}/likes`,
      )
    ).data
    emit('changed', state.value.count)
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <div>
    <button
      class="tb-btn"
      :class="{ 'is-bookmarked': state.liked }"
      :disabled="busy"
      :aria-pressed="state.liked"
      @click="toggle"
    >
      ♡ {{ state.liked ? '已点赞' : '点赞' }} · {{ state.count }}
    </button>
    <p v-if="error" class="error-message" role="alert">
      {{ error }} <button type="button" @click="load">重试</button>
    </p>
  </div>
</template>
