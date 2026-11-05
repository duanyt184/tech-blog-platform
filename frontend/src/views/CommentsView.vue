<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppIcon from '../components/AppIcon.vue'
import { getMyComments } from '../api/comments'
import { errorMessage, formatTime } from '../utils/articles'
import type { ArticleComment } from '../types/comment'

const tab = ref<'received' | 'written'>('received')
const received = ref<ArticleComment[]>([])
const written = ref<ArticleComment[]>([])
const loading = ref(true)
const error = ref('')

onMounted(async () => {
  try {
    const data = await getMyComments()
    received.value = data.received
    written.value = data.written
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    loading.value = false
  }
})

const current = computed(() => (tab.value === 'received' ? received.value : written.value))
</script>

<template>
  <div class="work-page">
    <header class="work-heading">
      <div>
        <p class="eyebrow">互动</p>
        <h1>评论</h1>
        <p>别人对你文章的评论，以及你自己发表过的评论。</p>
      </div>
    </header>

    <div class="status-tabs" role="tablist">
      <button
        type="button"
        role="tab"
        :aria-selected="tab === 'received'"
        :class="{ active: tab === 'received' }"
        @click="tab = 'received'"
      >
        收到的评论 <span class="tab-count">{{ received.length }}</span>
      </button>
      <button
        type="button"
        role="tab"
        :aria-selected="tab === 'written'"
        :class="{ active: tab === 'written' }"
        @click="tab = 'written'"
      >
        我的评论 <span class="tab-count">{{ written.length }}</span>
      </button>
    </div>

    <p v-if="loading" class="empty-state">正在加载评论…</p>
    <p v-else-if="error" class="error-message" role="alert">{{ error }}</p>
    <div v-else-if="current.length" class="comment-page-list">
      <RouterLink
        v-for="item in current"
        :key="item.id"
        class="comment-page-item"
        :to="{ path: `/article/${item.articleId}`, hash: `#comment-${item.id}` }"
        :title="`跳转到《${item.articleTitle}》的这条评论`"
      >
        <div class="comment-page-item__head">
          <span class="comment-page-item__title">{{ item.articleTitle }}</span>
          <time :datetime="item.createdAt">{{ formatTime(item.createdAt) }}</time>
        </div>
        <p class="comment-body">{{ item.content }}</p>
        <p class="comment-page-item__author">
          <template v-if="tab === 'received'">来自 <strong>{{ item.author }}</strong></template>
          <template v-else>查看评论位置 →</template>
        </p>
      </RouterLink>
    </div>
    <div v-else class="empty-state">
      <AppIcon name="comment" :size="32" />
      <h3>{{ tab === 'received' ? '还没有收到评论' : '你还没有发表过评论' }}</h3>
      <p>
        {{ tab === 'received'
          ? '发布文章后，读者的评论会出现在这里。'
          : '在文章详情页底部可以参与讨论。' }}
      </p>
      <RouterLink to="/articles" class="tb-btn">去逛逛文章广场</RouterLink>
    </div>
  </div>
</template>
