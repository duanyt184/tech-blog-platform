<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { http } from '../api/http'
import { errorMessage, type Category } from '../utils/articles'
import AppIcon from './AppIcon.vue'
export interface NavigationPreferences {
  categoryIds: number[] | null
  tagNames: string[] | null
}
const props = defineProps<{
  kind: 'categories' | 'tags'
  categories: Category[]
  tags: { name: string; count?: number }[]
  preferences: NavigationPreferences
  defaultCategoryIds: number[]
  defaultTagNames: string[]
}>()
const emit = defineEmits<{ close: []; saved: [value: NavigationPreferences] }>()
const dialog = ref<HTMLDialogElement | null>(null)
const query = ref(''),
  filter = ref('all'),
  busy = ref(false),
  error = ref('')
const defaults = computed(() =>
  props.kind === 'categories'
    ? props.defaultCategoryIds.map(String)
    : props.defaultTagNames,
)
const items = computed(() =>
  props.kind === 'categories'
    ? props.categories.map((c) => ({
        key: String(c.id),
        name: c.name,
        description: c.description,
        color: c.color,
        child: !!c.parentId,
        count: undefined as number | undefined,
      }))
    : props.tags.map((t) => ({
        key: t.name,
        name: t.name,
        count: t.count,
        description: '',
        color: '',
        child: false,
      })),
)
const current =
  props.kind === 'categories'
    ? props.preferences.categoryIds?.map(String)
    : props.preferences.tagNames
const selected = ref(
  new Set(
    (current || defaults.value).filter((key) =>
      items.value.some((item) => item.key === key),
    ),
  ),
)
const defaultRequested = ref(false)
const filtered = computed(() =>
  items.value.filter((item) => {
    const matches = `${item.name} ${item.description}`
      .toLowerCase()
      .includes(query.value.trim().toLowerCase())
    return (
      matches &&
      (filter.value === 'all' ||
        (filter.value === 'selected'
          ? selected.value.has(item.key)
          : !selected.value.has(item.key)))
    )
  }),
)
function toggle(key: string) {
  defaultRequested.value = false
  if (selected.value.has(key)) selected.value.delete(key)
  else selected.value.add(key)
}
function deselectAll() {
  selected.value.clear()
  defaultRequested.value = false
}
function resetDefaults() {
  selected.value = new Set(defaults.value)
  defaultRequested.value = true
}
function close() {
  if (!busy.value) emit('close')
}
async function save() {
  busy.value = true
  error.value = ''
  try {
    const value = { ...props.preferences }
    if (props.kind === 'categories')
      value.categoryIds = defaultRequested.value
        ? null
        : [...selected.value].map(Number)
    else value.tagNames = defaultRequested.value ? null : [...selected.value]
    const { data } = await http.put<NavigationPreferences>(
      '/my/navigation',
      value,
    )
    emit('saved', data)
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    busy.value = false
  }
}
onMounted(() => dialog.value?.showModal())
</script>
<template>
  <Teleport to="body">
    <dialog
      ref="dialog"
      class="navigation-editor"
      :aria-labelledby="`navigation-editor-${kind}`"
      @cancel.prevent="close"
      @keydown.esc.stop
      @click="$event.target === dialog && close()"
    >
      <header class="navigation-editor-heading">
        <div>
          <h2 :id="`navigation-editor-${kind}`">Edit {{ kind }} navigation</h2>
          <p>
            <button
              type="button"
              class="navigation-link-button"
              :disabled="busy"
              @click="deselectAll"
            >
              Deselect all
            </button>
            {{
              kind === 'categories'
                ? 'categories shown in your sidebar.'
                : 'tags shown in your sidebar.'
            }}
          </p>
        </div>
        <button
          class="icon-button"
          type="button"
          aria-label="Close navigation editor"
          :disabled="busy"
          @click="close"
        >
          <AppIcon name="close" :size="23" />
        </button>
      </header>
      <form @submit.prevent="save">
        <div class="navigation-editor-fields" :aria-busy="busy">
          <div class="navigation-editor-filter">
            <label
              ><AppIcon name="search" :size="17" /><input
                v-model="query"
                :disabled="busy"
                :aria-label="`Search ${kind}`"
                :placeholder="`Search ${kind}`"
                autofocus /></label
            ><select
              v-model="filter"
              :disabled="busy"
              aria-label="Filter selections"
            >
              <option value="all">All</option>
              <option value="selected">Selected</option>
              <option value="unselected">Unselected</option>
            </select>
          </div>
          <div
            class="navigation-editor-options"
            :class="{ 'tag-options': kind === 'tags' }"
          >
            <label
              v-for="item in filtered"
              :key="item.key"
              class="navigation-option"
              :class="{ 'child-category': item.child }"
              :style="{ '--category-color': item.color }"
            >
              <input
                type="checkbox"
                :disabled="busy"
                :checked="selected.has(item.key)"
                :aria-label="item.name"
                @change="toggle(item.key)"
              />
              <span
                ><strong
                  ><span
                    v-if="item.color"
                    class="category-square"
                    :style="{ background: item.color }"
                  ></span
                  >{{ item.name
                  }}<span
                    v-if="item.count !== undefined"
                    class="navigation-tag-count"
                  >
                    ({{ item.count }})</span
                  ></strong
                ><span v-if="item.description" class="navigation-description">{{
                  item.description
                }}</span></span
              >
            </label>
            <p v-if="!filtered.length" class="empty-state">
              No {{ kind }} found.
            </p>
          </div>
          <p v-if="error" class="error-message" role="alert">{{ error }}</p>
          <footer class="navigation-editor-footer">
            <button
              class="tb-btn tb-btn--primary"
              type="submit"
              :disabled="busy"
            >
              {{ busy ? 'Saving…' : 'Save' }}</button
            ><button
              type="button"
              class="navigation-link-button"
              :disabled="busy"
              @click="resetDefaults"
            >
              ↶ Reset to defaults
            </button>
          </footer>
        </div>
      </form>
    </dialog>
  </Teleport>
</template>
