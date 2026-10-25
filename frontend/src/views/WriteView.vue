<script setup lang="ts">
import { useAuth } from '../composables/useAuth'

/**
 * 写文章（占位页）。
 * 该路由在 router 里标记了 meta.requiresAuth，未登录访问会先跳登录页，
 * 登录成功后自动回到这里——这就是认证模块为其他人提供的保护能力。
 */
const { user } = useAuth()
</script>

<template>
  <div class="page-container tb-page">
    <header>
      <p class="tb-page__eyebrow">创作</p>
      <h1 class="tb-page__title">写文章</h1>
      <p class="tb-page__desc">
        当前登录用户：<strong>{{ user?.username }}</strong>（{{ user?.email }}）。
        本页由「文章发布成员」实现（对应 Issue #4）。
      </p>
    </header>

    <div class="tb-placeholder">
      <strong>占位页面</strong>
      <p>
        说明：能进入本页就说明登录态与路由守卫已经生效；
        如果退出登录后再访问 /write，应被重定向到 /login?redirect=/write。
      </p>
    </div>
  </div>
</template>
