<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { http } from '../api/http'
import { errorMessage } from '../utils/articles'
const props = defineProps<{ kind: 'tags' | 'rules' }>()
const items = ref<any[]>([]),
  editing = ref<number>(),
  form = reactive({
    name: '',
    description: '',
    title: '',
    pattern: '',
    enabled: true,
    groupName: 'Other Tags',
  }),
  error = ref(''),
  notice = ref(''),
  busy = ref(false)
async function load() {
  try {
    items.value = (
      await http.get(props.kind === 'tags' ? '/community/tags' : '/admin/rules')
    ).data
  } catch (e) {
    error.value = errorMessage(e)
  }
}
function reset() {
  editing.value = undefined
  Object.assign(form, {
    name: '',
    description: '',
    title: '',
    pattern: '',
    enabled: true,
    groupName: 'Other Tags',
  })
}
watch(
  () => props.kind,
  () => {
    reset()
    error.value = ''
    notice.value = ''
    void load()
  },
  { immediate: true },
)
function edit(item: any) {
  editing.value = item.id
  Object.assign(form, item)
}
async function save() {
  busy.value = true
  error.value = ''
  try {
    const url = `/admin/${props.kind}${editing.value ? '/' + editing.value : ''}`
    await http[editing.value ? 'put' : 'post'](url, form)
    reset()
    await load()
    notice.value = 'Saved.'
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    busy.value = false
  }
}
async function remove(item: any) {
  if (
    !window.confirm(
      props.kind === 'tags'
        ? 'Remove this tag from all topics?'
        : 'Delete this community rule?',
    )
  )
    return
  busy.value = true
  try {
    await http.delete(
      `/admin/${props.kind}/${encodeURIComponent(props.kind === 'tags' ? item.name : item.id)}`,
    )
    await load()
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <p v-if="error" class="error-message" role="alert">{{ error }}</p>
  <p v-if="notice" class="success-message" role="status">{{ notice }}</p>
  <div class="category-admin">
    <form class="writing-form tb-card" @submit.prevent="save">
      <h2>
        {{ editing ? 'Edit' : 'Add' }} {{ kind === 'tags' ? 'tag' : 'rule' }}
      </h2>
      <fieldset :disabled="busy">
        <label v-if="kind === 'tags'"
          >Name<input
            v-model="form.name"
            required
            maxlength="24"
            pattern="[\p{L}\p{N}_\-]+" /></label
        ><label v-else
          >Title<input v-model="form.title" required maxlength="100" /></label
        ><label v-if="kind === 'tags'"
          >Group<input
            v-model="form.groupName"
            maxlength="80"
            placeholder="Other Tags" /></label
        ><label
          >Description<textarea
            v-model="form.description"
            :maxlength="kind === 'tags' ? 200 : 2000"
            rows="4"
          ></textarea></label
        ><template v-if="kind === 'rules'"
          ><label
            >Detection phrases<input
              v-model="form.pattern"
              maxlength="200"
              placeholder="Comma-separated phrases"
          /></label>
          <p class="field-hint">
            Matching is case-insensitive. Leave empty for a guideline without
            automatic detection.
          </p>
          <label class="checkbox-label"
            ><input v-model="form.enabled" type="checkbox" />Enabled</label
          ></template
        >
        <div class="row-actions">
          <button class="tb-btn tb-btn--primary">Save</button
          ><button type="button" class="tb-btn" @click="reset">Reset</button>
        </div>
      </fieldset>
    </form>
    <div>
      <article
        v-for="item in items"
        :key="item.id || item.name"
        class="management-row"
      >
        <div>
          <h2>{{ item.name || item.title }}</h2>
          <p>{{ item.description }}</p>
          <small v-if="kind === 'rules'"
            >{{ item.enabled ? 'Enabled' : 'Disabled' }} ·
            {{ item.pattern || 'Guideline only' }}</small
          ><small v-else>{{ item.count }} topics</small>
        </div>
        <div class="row-actions">
          <button class="tb-btn" :disabled="busy" @click="edit(item)">
            Edit</button
          ><button
            class="tb-btn danger-button"
            :disabled="busy"
            @click="remove(item)"
          >
            Delete
          </button>
        </div>
      </article>
    </div>
  </div>
</template>
