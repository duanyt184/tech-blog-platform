<script setup lang="ts">
import type { Article } from '../types/article'
import { categoryColor, formatCount, relativeTime } from '../utils/articles'
defineProps<{ items: Article[]; compact?: boolean }>()
</script>
<template>
  <div v-if="compact" class="latest-column">
    <RouterLink
      v-for="a in items"
      :key="a.id"
      :to="`/t/${a.id}`"
      class="latest-topic"
      ><span
        class="avatar"
        :style="{ background: categoryColor(a.category) }"
        >{{ a.author.slice(0, 1).toUpperCase() }}</span
      >
      <div class="latest-topic-main">
        <h3>{{ a.title }}</h3>
        <div class="topic-taxonomy">
          <span
            class="category-square"
            :style="{ background: categoryColor(a.category) }"
          ></span
          >{{ a.category }}
          <span v-for="t in a.tags.slice(0, 2)" :key="t">{{ t }}</span>
        </div>
      </div>
      <div class="latest-topic-count">
        {{ a.replyCount }}<small>{{ relativeTime(a.updatedAt) }}</small>
      </div></RouterLink
    >
  </div>
  <table v-else class="topic-table">
    <thead>
      <tr>
        <th>Topic</th>
        <th class="poster-column"></th>
        <th>Replies</th>
        <th class="views-column">Views</th>
        <th>Activity</th>
      </tr>
    </thead>
    <tbody>
      <tr v-for="a in items" :key="a.id">
        <td class="topic-title-cell">
          <RouterLink :to="`/t/${a.id}`" class="topic-title">{{
            a.title
          }}</RouterLink>
          <div class="topic-taxonomy">
            <RouterLink
              :to="{ path: '/articles', query: { category: a.category } }"
              ><span
                class="category-square"
                :style="{ background: categoryColor(a.category) }"
              ></span
              >{{ a.category }}</RouterLink
            ><RouterLink
              v-for="t in a.tags"
              :key="t"
              :to="{ path: '/articles', query: { tag: t } }"
              >{{ t }}</RouterLink
            >
          </div>
        </td>
        <td class="poster-column">
          <RouterLink
            :to="`/u/${a.authorId}`"
            :title="a.author"
            class="avatar"
            :style="{ background: categoryColor(a.category) }"
            >{{ a.author.slice(0, 1).toUpperCase() }}</RouterLink
          >
        </td>
        <td class="numeric">{{ formatCount(a.replyCount || 0) }}</td>
        <td class="numeric views-column">{{ formatCount(a.viewCount) }}</td>
        <td class="numeric activity-column">
          <time :datetime="a.updatedAt" :title="a.updatedAt">{{
            relativeTime(a.updatedAt)
          }}</time>
        </td>
      </tr>
    </tbody>
  </table>
</template>
