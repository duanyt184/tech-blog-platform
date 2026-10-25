<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { http } from '../api/http'
import { useAuth } from '../composables/useAuth'

/**
 * 首页（占位版）：保留「链路自检」卡片，方便每次启动后确认
 * 浏览器 → Vite 代理 → Spring Boot → MySQL 是否整条通。
 * 正式首页的设计由负责初始化的同学继续完善。
 */
const { isAuthenticated } = useAuth()

type CheckState = 'checking' | 'up' | 'down'
const backendState = ref<CheckState>('checking')
const databaseState = ref<CheckState>('checking')

async function checkHealth() {
  backendState.value = 'checking'
  databaseState.value = 'checking'
  try {
    const { data } = await http.get<{ status: string; database: string }>('/health')
    backendState.value = data.status === 'UP' ? 'up' : 'down'
    databaseState.value = data.database === 'UP' ? 'up' : 'down'
  } catch {
    // 后端没起来时 /api/health 连不上，整条链路都算不可用
    backendState.value = 'down'
    databaseState.value = 'down'
  }
}

const stateText: Record<CheckState, string> = {
  checking: '检查中…',
  up: '正常',
  down: '不可用',
}

const demoArticles = [
  { id: 1, title: 'Vue 3 + TypeScript 项目从零到一', summary: '组合式 API、类型推导与目录约定的实践记录。', category: '前端' },
  { id: 2, title: 'Spring Boot 分层的边界在哪里', summary: 'Controller / Service / Repository 各该做什么，不该做什么。', category: '后端' },
  { id: 3, title: 'Flyway 让数据库变更可追溯', summary: '为什么不要手改已经执行过的迁移脚本。', category: '数据库' },
]

onMounted(checkHealth)
</script>

<template>
  <div class="page-container home">
    <section class="hero">
      <p class="tb-page__eyebrow">码上 · 技术分享社区</p>
      <h1 class="hero__title">让每一次技术分享<br />都值得被看见</h1>
      <p class="hero__desc">
        面向高校学生与技术爱好者的博客与投稿协作平台：写作、投稿、审核、发布，
        把踩过的坑变成别人的捷径。
      </p>
      <div class="hero__actions">
        <RouterLink class="tb-btn tb-btn--primary" :to="isAuthenticated ? '/write' : '/login?redirect=/write'">
          开始写作
        </RouterLink>
        <RouterLink class="tb-btn" to="/articles">浏览文章广场</RouterLink>
        <RouterLink v-if="!isAuthenticated" class="tb-btn tb-btn--ghost" to="/register">注册账号</RouterLink>
      </div>
    </section>

    <div class="home__grid">
      <section class="tb-card">
        <header class="section-head">
          <h2>最新文章</h2>
          <span class="tb-tag">Demo 演示数据</span>
        </header>
        <ul class="article-list">
          <li v-for="article in demoArticles" :key="article.id" class="article-item">
            <RouterLink :to="`/article/${article.id}`" class="article-item__link">
              <h3>{{ article.title }}</h3>
              <p>{{ article.summary }}</p>
            </RouterLink>
            <span class="tb-tag">{{ article.category }}</span>
          </li>
        </ul>
        <p class="section-note">
          以上为占位内容，真实列表由负责「文章查询」的同学接入数据库。
        </p>
      </section>

      <aside class="tb-card status-card">
        <header class="section-head">
          <h2>系统状态</h2>
          <button type="button" class="tb-btn tb-btn--ghost" @click="checkHealth">重新检查</button>
        </header>
        <ul class="status-list">
          <li>
            <span>后端接口</span>
            <span class="status" :class="`status--${backendState}`">{{ stateText[backendState] }}</span>
          </li>
          <li>
            <span>数据库连接</span>
            <span class="status" :class="`status--${databaseState}`">{{ stateText[databaseState] }}</span>
          </li>
        </ul>
        <p class="section-note">
          {{
            backendState === 'up' && databaseState === 'up'
              ? '完整链路运行正常：浏览器 → Vite 代理 → Spring Boot → MySQL。'
              : '链路不通时，先确认 MySQL 服务与后端进程是否都在运行。'
          }}
        </p>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.home {
  display: flex;
  flex-direction: column;
  gap: 28px;
}

.hero {
  padding: 40px 36px;
  border-radius: 20px;
  border: 1px solid var(--tb-border);
  background: linear-gradient(135deg, #eff6ff 0%, #ffffff 55%, #f8fafc 100%);
}

.hero__title {
  font-size: 36px;
  letter-spacing: -0.01em;
}

.hero__desc {
  margin-top: 14px;
  max-width: 56ch;
  color: var(--tb-muted);
  font-size: 15px;
}

.hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 24px;
}

.home__grid {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.section-head h2 {
  font-size: 18px;
}

.section-note {
  margin-top: 16px;
  font-size: 13px;
  color: var(--tb-muted);
}

.article-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.article-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid var(--tb-border);
}

.article-item:last-child {
  border-bottom: none;
}

.article-item__link {
  flex: 1;
}

.article-item__link h3 {
  font-size: 16px;
}

.article-item__link p {
  margin-top: 4px;
  font-size: 13px;
  color: var(--tb-muted);
}

.status-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  font-size: 14px;
}

.status-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-radius: var(--tb-radius-sm);
  background: var(--tb-bg);
}

.status {
  font-weight: 600;
}

.status--up {
  color: var(--tb-success);
}

.status--down {
  color: var(--tb-danger);
}

.status--checking {
  color: var(--tb-muted);
}

@media (max-width: 900px) {
  .home__grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .hero {
    padding: 28px 22px;
  }

  .hero__title {
    font-size: 26px;
  }
}
</style>
