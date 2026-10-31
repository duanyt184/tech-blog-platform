<script setup lang="ts">
import type { ReviewEvent } from '../types/article'
import { formatTime } from '../utils/articles'
defineProps<{ events: ReviewEvent[] }>()
const labels = { SUBMITTED: '提交审核', WITHDRAWN: '撤回草稿', PUBLISHED: '审核通过', REJECTED: '退回修改' }
</script>
<template>
  <section v-if="events.length" class="review-history">
    <h2>投稿记录</h2>
    <ol>
      <li v-for="event in events" :key="event.id">
        <div><strong>{{ labels[event.action] }}</strong> · {{ event.actor }} <time>{{ formatTime(event.createdAt) }}</time></div>
        <p v-if="event.comment">{{ event.comment }}</p>
      </li>
    </ol>
  </section>
</template>
