<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import ArticleBody from './ArticleBody.vue'
import type { ContentFormat } from '../types/article'
const props = defineProps<{ modelValue: string; contentFormat: ContentFormat; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: string]; 'update:contentFormat': [value: ContentFormat] }>()
const editor = ref<HTMLTextAreaElement | null>(null)
const mode = ref<'edit' | 'split' | 'preview'>('edit')
const previewContent = ref(props.modelValue)
const feedback = ref('')
let timer: ReturnType<typeof setTimeout> | undefined
watch(() => props.modelValue, value => {
  clearTimeout(timer)
  timer = setTimeout(() => { previewContent.value = value }, 300)
})
onBeforeUnmount(() => clearTimeout(timer))
const modes = [{ id: 'edit', label: '编辑' }, { id: 'split', label: '分屏' }, { id: 'preview', label: '预览' }] as const
const tools = [
  { label: '标题', before: '\n\n## ', after: '\n', sample: '小节标题' },
  { label: '粗体', before: '**', after: '**', sample: '重点内容', shortcut: 'b' },
  { label: '斜体', before: '*', after: '*', sample: '强调内容', shortcut: 'i' },
  { label: '删除线', before: '~~', after: '~~', sample: '删除内容' },
  { label: '链接', before: '[', after: '](https://example.com)', sample: '链接文字', shortcut: 'k' },
  { label: '图片', before: '![', after: '](https://example.com/image.png)', sample: '图片说明' },
  { label: '引用', before: '\n\n> ', after: '\n', sample: '引用内容' },
  { label: '列表', before: '\n\n- ', after: '\n- 下一项\n', sample: '列表项' },
  { label: '待办', before: '\n\n- [ ] ', after: '\n', sample: '待完成事项' },
  { label: '代码', before: '\n\n```javascript\n', after: '\n```\n', sample: 'console.log("Hello")' },
  { label: '表格', before: '\n\n| 列名 | 说明 |\n| --- | --- |\n| ', after: ' | 内容 |\n', sample: '项目' },
  { label: '行内公式', before: '$', after: '$', sample: 'E = mc^2' },
  { label: '独立公式', before: '\n\n$$\n', after: '\n$$\n', sample: '\\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}' },
]
async function insert(tool: typeof tools[number]) {
  if (props.disabled || props.contentFormat !== 'MARKDOWN' || !editor.value) return
  const input = editor.value
  const start = input.selectionStart
  const end = input.selectionEnd
  const selected = props.modelValue.slice(start, end) || tool.sample
  const replacement = tool.before + selected + tool.after
  const value = props.modelValue.slice(0, start) + replacement + props.modelValue.slice(end)
  if (value.length > 100000) { feedback.value = '正文已达到 100000 字符限制，无法插入。'; return }
  feedback.value = ''
  emit('update:modelValue', value)
  await nextTick()
  input.focus()
  input.setSelectionRange(start + tool.before.length, start + tool.before.length + selected.length)
}
function shortcut(event: KeyboardEvent) {
  if (props.disabled || props.contentFormat !== 'MARKDOWN') return
  if ((!event.ctrlKey && !event.metaKey) || event.altKey || event.shiftKey || event.isComposing) return
  const tool = tools.find(item => item.shortcut === event.key.toLowerCase())
  if (tool) { event.preventDefault(); void insert(tool) }
}
function changeMode(value: typeof mode.value) {
  clearTimeout(timer)
  previewContent.value = props.modelValue
  mode.value = value
}
function changeFormat(event: Event) {
  if (props.disabled) return
  clearTimeout(timer)
  previewContent.value = props.modelValue
  emit('update:contentFormat', (event.target as HTMLSelectElement).value as ContentFormat)
}
</script>
<template>
  <section class="markdown-editor" aria-label="文章正文编辑器">
    <div class="editor-toolbar">
      <label class="content-format-control">正文格式
        <select :value="contentFormat" :disabled="disabled" @change="changeFormat">
          <option value="PLAIN_TEXT">纯文本</option>
          <option value="MARKDOWN">Markdown</option>
        </select>
      </label>
      <div class="editor-modes" role="group" aria-label="编辑视图">
        <button v-for="item in modes" :key="item.id" type="button" class="tb-btn" :aria-pressed="mode === item.id" @click="changeMode(item.id)">{{ item.label }}</button>
      </div>
    </div>
    <p class="field-hint">{{ contentFormat === 'PLAIN_TEXT' ? '纯文本保留空格和换行，标题、公式等符号按原文显示。' : '支持 Markdown 排版和 LaTeX 数学公式。' }} 切换格式保留正文，只改变显示方式。</p>
    <div v-if="contentFormat === 'MARKDOWN' && mode !== 'preview'" class="markdown-tools" role="group" aria-label="插入 Markdown 格式">
      <button v-for="tool in tools" :key="tool.label" type="button" :disabled="disabled" :title="tool.shortcut ? `${tool.label}（Ctrl / ⌘ + ${tool.shortcut.toUpperCase()}）` : `插入${tool.label}`" @mousedown.prevent @click="insert(tool)">{{ tool.label }}</button>
    </div>
    <div class="markdown-editor-panes" :class="{ 'is-split': mode === 'split' }">
      <textarea v-show="mode !== 'preview'" ref="editor" :value="modelValue" @input="emit('update:modelValue', ($event.target as HTMLTextAreaElement).value)" @keydown="shortcut" class="body-editor" :disabled="disabled" maxlength="100000" rows="22" :aria-label="contentFormat === 'MARKDOWN' ? 'Markdown 正文' : '纯文本正文'" :placeholder="contentFormat === 'MARKDOWN' ? '用 Markdown 记录实践：标题、列表、表格、代码和 LaTeX 公式…' : '在这里记录你的技术实践…'" spellcheck="false"></textarea>
      <div v-if="mode !== 'edit'" class="editor-preview" aria-label="正文预览">
        <ArticleBody v-if="previewContent" :content="previewContent" :content-format="contentFormat" />
        <p v-else class="field-hint">填写正文后可以预览。</p>
      </div>
    </div>
    <p v-if="feedback" class="error-message" role="alert">{{ feedback }}</p>
    <details v-if="contentFormat === 'MARKDOWN'" class="markdown-help">
      <summary>Markdown 语法提示</summary>
      <p><code># 标题</code> · <code>**粗体**</code> · <code>*斜体*</code> · <code>~~删除线~~</code> · <code>`行内代码`</code></p>
      <p><code>[链接文字](网址)</code> · <code>![图片说明](图片网址)</code> · <code>- [ ] 待办事项</code></p>
      <p>代码块用三反引号包围，并在第一行写语言名。暂不渲染 Mermaid 图表，其内容按代码块显示。</p>
      <p>行内公式：<code>$E = mc^2$</code>；独立公式用两行 <code>$$</code> 包围。显示美元符号时写 <code>\$</code>。</p>
      <p>公式使用 KaTeX 支持的 LaTeX 数学语法，不支持完整 LaTeX 文档或任意宏包；语法错误会在预览中标示。</p>
      <p>普通换行会合并为同一段；空一行分段，行末两个空格可强制换行。图片支持网址，暂不支持本地上传。</p>
    </details>
  </section>
</template>
