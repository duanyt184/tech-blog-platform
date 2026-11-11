<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { categories, categoryColor } from '../utils/articles'
import AppIcon from './AppIcon.vue'
const props = defineProps<{ category: string; tags: string; suggestions: { name: string }[] }>()
const emit = defineEmits<{ 'update:category': [string]; 'update:tags': [string] }>()
const root = ref<HTMLElement | null>(null)
const categoryButton = ref<HTMLButtonElement | null>(null)
const categorySearch = ref<HTMLInputElement | null>(null)
const tagInput = ref<HTMLInputElement | null>(null)
const open = ref<'category' | 'tags' | null>(null)
const search = ref('')
const tagSearch = ref('')
const selectedTags = computed(() => props.tags.split(/[,，]/).map(t => t.trim()).filter(Boolean))
const filteredCategories = computed(() => categories.filter(c => c.name.toLowerCase().includes(search.value.toLowerCase())))
const filteredTags = computed(() => props.suggestions.filter(t => !selectedTags.value.includes(t.name) && t.name.includes(tagSearch.value.toLowerCase())))
const invalid = ref('')
async function toggleCategory() {
  open.value = open.value === 'category' ? null : 'category'
  search.value = ''
  await nextTick()
  if (open.value) categorySearch.value?.focus()
}
function selectCategory(name: string) {
  emit('update:category', name); open.value = null; categoryButton.value?.focus()
}
function addTag(value = tagSearch.value) {
  const tags = [...new Set([...selectedTags.value, ...value.split(/[,，]/).map(t => t.trim().toLowerCase()).filter(Boolean)])]
  if (tags.length > 5 || tags.some(t => t.length > 24)) { invalid.value = '最多 5 个标签，每个不超过 24 个字符'; return }
  emit('update:tags', tags.join(', ')); tagSearch.value = ''; invalid.value = ''
}
function removeTag(tag: string) { emit('update:tags', selectedTags.value.filter(t => t !== tag).join(', ')) }
function close(event: Event) {
  if (!root.value?.contains(event.target as Node)) { addTag(); open.value = null }
}
function navigate(event: KeyboardEvent) {
  if (!['ArrowDown', 'ArrowUp'].includes(event.key)) return
  event.preventDefault()
  const items = Array.from(root.value?.querySelectorAll<HTMLElement>('[role="option"]') || [])
  if (!items.length) return
  const index = items.indexOf(document.activeElement as HTMLElement)
  items[(index + (event.key === 'ArrowDown' ? 1 : -1) + items.length) % items.length]?.focus()
}
onMounted(() => document.addEventListener('pointerdown', close))
onBeforeUnmount(() => document.removeEventListener('pointerdown', close))
</script>
<template>
  <div ref="root" class="composer-taxonomy" @keydown="navigate" @keydown.esc.stop.prevent="open = null">
    <div class="composer-category taxonomy-control">
      <button ref="categoryButton" type="button" class="taxonomy-trigger" aria-label="Category" aria-haspopup="listbox" :aria-expanded="open === 'category'" @click="toggleCategory">
        <span v-if="category" class="category-square" :style="{ background: categoryColor(category) }"></span>
        <span :class="{ 'taxonomy-placeholder': !category }">{{ category || 'Category...' }}</span>
        <AppIcon name="chevron" :size="12" />
      </button>
      <div v-if="open === 'category'" class="taxonomy-menu">
        <input ref="categorySearch" v-model="search" aria-label="Search categories" placeholder="Search…" autocomplete="off" />
        <div role="listbox" aria-label="Categories">
          <button v-for="item in filteredCategories" :key="item.id" type="button" role="option" :aria-selected="category === item.name" @click="selectCategory(item.name)">
            <span class="category-square" :style="{ background: item.color }"></span>
            <span><strong>{{ item.name }}</strong><small>{{ item.description }}</small></span>
          </button>
          <p v-if="!filteredCategories.length">No categories found</p>
        </div>
      </div>
    </div>
    <div v-if="category || tags" class="composer-tags taxonomy-control">
      <div class="taxonomy-trigger taxonomy-tag-trigger" @click="tagInput?.focus()">
        <span v-for="tag in selectedTags" :key="tag" class="composer-tag-chip">{{ tag }}<button type="button" :aria-label="`Remove tag ${tag}`" @click.stop="removeTag(tag)"><AppIcon name="close" :size="12" /></button></span>
        <input ref="tagInput" v-model="tagSearch" aria-label="Tags" :placeholder="selectedTags.length ? '' : 'optional tags'" autocomplete="off" @focus="open = 'tags'" @blur="addTag()" @keydown.enter.prevent="addTag()" @keydown.,.prevent="addTag()" />
        <AppIcon name="chevron" :size="12" />
      </div>
      <div v-if="open === 'tags' && filteredTags.length" class="taxonomy-menu" role="listbox" aria-label="Suggested tags">
        <button v-for="tag in filteredTags" :key="tag.name" type="button" role="option" :aria-selected="false" @mousedown.prevent @click="addTag(tag.name)">{{ tag.name }}</button>
      </div>
      <p v-if="invalid" class="taxonomy-error" role="alert">{{ invalid }}</p>
    </div>
  </div>
</template>
