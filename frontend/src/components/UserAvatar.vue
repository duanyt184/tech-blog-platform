<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { avatarRevisions } from '../composables/useAvatar'
const props = defineProps<{ userId: number; username: string }>()
const failed = ref(false)
const src = computed(
  () =>
    `/api/profiles/${props.userId}/avatar?v=${avatarRevisions[props.userId] || '1'}`,
)
watch(src, () => {
  failed.value = false
})
</script>
<template>
  <img
    v-if="userId && !failed"
    class="avatar-image"
    :src="src"
    alt=""
    loading="lazy"
    @error="failed = true"
  />
  <span v-else>{{ username.slice(0, 1).toUpperCase() }}</span>
</template>
