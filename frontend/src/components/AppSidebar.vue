<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import SidebarNavigationEditor, {
  type NavigationPreferences,
} from './SidebarNavigationEditor.vue'
import { useRoute } from 'vue-router'
import { categories, loadCategories } from '../utils/articles'
import { http } from '../api/http'
import { useAuth } from '../composables/useAuth'
import { useTheme } from '../composables/useTheme'
import AppIcon from './AppIcon.vue'
defineProps<{ open: boolean }>()
const route = useRoute(),
  { user, isEditor } = useAuth(),
  { toggle } = useTheme()
const tags = ref<{ name: string; count?: number }[]>([])
const preferences = ref<NavigationPreferences>({
  categoryIds: null,
  tagNames: null,
})
const editing = ref<'categories' | 'tags' | null>(null)
const preferencesReady = ref(false)
const navigationError = ref('')
let preferenceRequest = 0
async function loadPreferences() {
  const request = ++preferenceRequest
  const id = user.value?.id
  preferencesReady.value = false
  navigationError.value = ''
  preferences.value = { categoryIds: null, tagNames: null }
  editing.value = null
  if (!id) return
  try {
    const { data } = await http.get<NavigationPreferences>('/my/navigation')
    if (request === preferenceRequest) {
      preferences.value = data
      preferencesReady.value = true
    }
  } catch {
    if (request === preferenceRequest)
      navigationError.value = 'Unable to load navigation preferences.'
  }
}
watch(() => user.value?.id, loadPreferences, { immediate: true })
const defaultCategoryIds = computed(() =>
  categories.filter((c) => !c.parentId).map((c) => c.id),
)
const defaultTagNames = computed(() =>
  tags.value.slice(0, 10).map((t) => t.name),
)
const visibleCategories = computed(() =>
  categories.filter((c) =>
    (preferences.value.categoryIds || defaultCategoryIds.value).includes(c.id),
  ),
)
const visibleTags = computed(() =>
  (preferences.value.tagNames || defaultTagNames.value).flatMap(
    (name) => tags.value.find((t) => t.name === name) || [],
  ),
)
function savedNavigation(value: NavigationPreferences) {
  preferences.value = value
  editing.value = null
}

const preferredTags = [
  'Python',
  'Java',
  'TypeScript',
  'SQL',
  '算法题',
  '学习笔记',
  '项目实践',
  '概念解析',
  '求助答疑',
  '资源分享',
]
onMounted(async () => {
  try {
    await loadCategories()
    tags.value = (await http.get('/community/tags')).data
    const rank = (name: string) => {
      const index = preferredTags.findIndex(
        (tag) => tag.toLowerCase() === name.toLowerCase(),
      )
      return index < 0 ? preferredTags.length : index
    }
    tags.value.sort((a, b) => rank(a.name) - rank(b.name))
  } catch {
    /* Pages show fetch errors and allow retry. */
  }
})
</script>
<template>
  <aside
    id="site-sidebar"
    class="site-sidebar"
    :class="{ 'is-open': open }"
    :inert="!open"
    :aria-hidden="!open"
    aria-label="Main navigation"
  >
    <nav>
      <p v-if="navigationError" class="sidebar-error" role="alert">
        {{ navigationError }}
        <button type="button" @click="loadPreferences">Retry</button>
      </p>
      <RouterLink
        to="/"
        class="sidebar-link"
        :class="{ selected: route.path === '/' }"
        ><AppIcon name="home" :size="16" />Home</RouterLink
      >
      <RouterLink
        to="/latest"
        class="sidebar-link"
        :class="{
          selected: ['/latest', '/articles', '/top'].includes(route.path),
        }"
        ><AppIcon name="book" :size="16" />Topics</RouterLink
      >
      <RouterLink to="/my-articles" class="sidebar-link" active-class="selected"
        ><AppIcon name="comment" :size="16" />My Posts</RouterLink
      >
      <RouterLink to="/drafts" class="sidebar-link" active-class="selected"
        ><AppIcon name="file" :size="16" />My Drafts</RouterLink
      >
      <RouterLink to="/bookmarks" class="sidebar-link" active-class="selected"
        ><AppIcon name="bookmark" :size="16" />Bookmarks</RouterLink
      >
      <RouterLink to="/guidelines" class="sidebar-link" active-class="selected"
        ><AppIcon name="check" :size="16" />Guidelines</RouterLink
      >
      <template v-if="user"
        ><RouterLink :to="`/u/${user.id}?tab=activity`" class="sidebar-link"
          ><AppIcon name="clock" :size="16" />My activity</RouterLink
        ><RouterLink to="/settings" class="sidebar-link" active-class="selected"
          ><AppIcon name="edit" :size="16" />Preferences</RouterLink
        ></template
      >
      <div class="nav-divider"></div>
      <p class="nav-section-label">
        ⌄ <span>Categories</span
        ><button
          v-if="user"
          type="button"
          class="icon-button section-edit"
          aria-label="Edit categories navigation"
          title="Edit categories navigation"
          :disabled="!preferencesReady"
          @click="editing = 'categories'"
        >
          <AppIcon name="edit" :size="15" />
        </button>
      </p>
      <RouterLink
        v-for="c in visibleCategories"
        :key="c.id"
        :to="{ path: '/articles', query: { category: c.name } }"
        class="sidebar-link"
        :class="{ selected: route.query.category === c.name }"
        ><span class="category-square" :style="{ background: c.color }"></span
        >{{ c.name }}</RouterLink
      >
      <RouterLink
        to="/categories"
        class="sidebar-link all-link"
        active-class="selected"
        ><AppIcon name="list" :size="16" />All categories</RouterLink
      >
      <div class="nav-divider"></div>
      <p class="nav-section-label">
        ⌄ <span>Tags</span
        ><button
          v-if="user"
          type="button"
          class="icon-button section-edit"
          aria-label="Edit tags navigation"
          title="Edit tags navigation"
          :disabled="!preferencesReady"
          @click="editing = 'tags'"
        >
          <AppIcon name="edit" :size="15" />
        </button>
      </p>
      <RouterLink
        v-for="t in visibleTags"
        :key="t.name"
        :to="{ path: '/articles', query: { tag: t.name } }"
        class="sidebar-link"
        ><AppIcon name="tag" :size="16" />{{ t.name }}</RouterLink
      >
      <RouterLink to="/tags" class="sidebar-link" active-class="selected"
        ><AppIcon name="list" :size="16" />All tags</RouterLink
      >
      <template v-if="isEditor"
        ><div class="nav-divider"></div>
        <RouterLink to="/review" class="sidebar-link" active-class="selected"
          ><AppIcon name="flag" :size="16" />Review queue</RouterLink
        ></template
      ><RouterLink
        v-if="user?.role === 'ADMIN'"
        to="/admin"
        class="sidebar-link"
        active-class="selected"
        ><AppIcon name="check" :size="16" />Admin</RouterLink
      >
    </nav>
    <div class="sidebar-bottom">
      <button
        class="icon-button"
        aria-label="Toggle color theme"
        title="Toggle light / dark theme"
        @click="toggle"
      >
        <AppIcon name="sun" :size="17" />
      </button>
    </div>
    <SidebarNavigationEditor
      v-if="editing"
      :kind="editing"
      :categories="categories"
      :tags="tags"
      :preferences="preferences"
      :default-category-ids="defaultCategoryIds"
      :default-tag-names="defaultTagNames"
      @close="editing = null"
      @saved="savedNavigation"
    />
  </aside>
</template>
