<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

/**
 * 文章详情（占位页）。
 * 路径参数 id 已经能拿到，负责「文章查询」的同学替换成 GET /api/articles/{id}。
 */
const route = useRoute()
const articleId = computed(() => route.params.id)
</script>

<template>
  <div class="page-container tb-page">
    <header>
      <p class="tb-page__eyebrow">文章详情</p>
      <h1 class="tb-page__title">文章 #{{ articleId }}</h1>
      <p class="tb-page__desc">正文、作者信息、发布时间与浏览量将在这里展示。</p>
    </header>

    <div class="tb-placeholder">
      <strong>占位页面</strong>
      <p>路由参数已正确解析，详情数据尚未接入（对应 Issue #3）。</p>
    </div>

    <RouterLink class="tb-btn" to="/articles">← 返回文章广场</RouterLink>
  </div>
</template>
