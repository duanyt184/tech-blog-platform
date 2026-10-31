<script setup lang="ts">
import { computed } from 'vue'
import { renderArticleBody, type RenderedArticle } from '../utils/articleBody'
import type { ContentFormat } from '../types/article'
const props = withDefaults(defineProps<{ content: string; contentFormat?: ContentFormat; rendered?: RenderedArticle }>(), { contentFormat: 'MARKDOWN' })
const body = computed(() => props.rendered || renderArticleBody(props.content, props.contentFormat))
</script>
<template>
  <div class="article-body" :class="contentFormat === 'PLAIN_TEXT' ? 'plain-text-body' : 'markdown-body'" v-html="body.html"></div>
</template>
