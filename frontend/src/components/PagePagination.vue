<script setup lang="ts">
defineProps<{ page: number; pages: number; total: number; disabled?: boolean }>()
defineEmits<{ change: [page: number] }>()
</script>
<template>
  <nav class="pagination" aria-label="分页">
    <span>共 {{ total }} 篇<span v-if="pages"> · 第 {{ page + 1 }} / {{ pages }} 页</span></span>
    <button class="tb-btn" :disabled="disabled || page <= 0" @click="$emit('change', page - 1)">上一页</button>
    <button class="tb-btn" :disabled="disabled || page + 1 >= pages" @click="$emit('change', page + 1)">下一页</button>
  </nav>
</template>
