<script setup lang="ts">
import { useAuth } from '../composables/useAuth'

/**
 * 我的文章（占位页）。
 * 后续由「个人文章成员」实现：按当前登录用户 id 查询，服务端必须校验数据归属，
 * 不能只靠前端隐藏按钮（对应 Issue #5）。
 */
const { user } = useAuth()
</script>

<template>
  <div class="page-container tb-page">
    <header>
      <p class="tb-page__eyebrow">个人中心</p>
      <h1 class="tb-page__title">我的文章</h1>
      <p class="tb-page__desc">
        {{ user?.username }} 的草稿、待审核、已发布与已退回文章都会在这里。
      </p>
    </header>

    <div class="tb-placeholder">
      <strong>占位页面</strong>
      <p>需要登录才能访问，列表功能对应 Issue #5。</p>
    </div>
  </div>
</template>
