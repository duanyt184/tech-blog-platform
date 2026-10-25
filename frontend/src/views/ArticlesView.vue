<script setup lang="ts">
/**
 * 文章广场（占位页）。
 * 由负责「文章查询」的同学替换为真实的数据库列表：从 ArticleRepository 查询 PUBLISHED 文章，
 * 新增 GET /api/articles 接口后在这里调用。
 */
</script>

<template>
  <div class="page-container tb-page">
    <header>
      <p class="tb-page__eyebrow">文章广场</p>
      <h1 class="tb-page__title">所有技术分享</h1>
      <p class="tb-page__desc">
        这里将展示所有已发布文章，支持按分类与关键词筛选。
      </p>
    </header>

    <div class="tb-placeholder">
      <strong>占位页面</strong>
      <p>
        当前路由与布局已生效，但列表数据尚未接入数据库。
        本页由「文章查询成员」实现（对应 Issue #3）。
      </p>
    </div>
  </div>
</template>
