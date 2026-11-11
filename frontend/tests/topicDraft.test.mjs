import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
import { JSDOM } from 'jsdom'
const dom = new JSDOM('<html><body></body></html>', { url: 'https://forum.test' })
for (const k of ['window', 'document', 'Element', 'HTMLElement', 'SVGElement', 'Node']) globalThis[k] = dom.window[k]
const { createApp, reactive, ref, h } = await import('vue')
const moduleUrl = s => 'data:text/javascript;base64,' + Buffer.from(s).toString('base64')
let calls, saveImpl
const api = { saveArticle: (...args) => saveImpl(...args) }
globalThis.draftFixture = api
const mocks = new Map([
  ['../api/articles', moduleUrl('export const saveArticle = (...args) => globalThis.draftFixture.saveArticle(...args)')],
  ['../utils/articles', moduleUrl('export const errorMessage = e => e.message')],
])
const source = ts.transpileModule(await readFile(new URL('../src/composables/useTopicDraft.ts', import.meta.url), 'utf8'), { compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.ESNext } }).outputText
const { useTopicDraft } = await import(moduleUrl(source.replace(/from ['"]([^'"]+)['"]/g, (_, n) => `from '${mocks.get(n) || import.meta.resolve(n)}'`)))
function mount({ key = 'topic-draft:7:new', published = false, reset = true } = {}) {
  if (reset) window.localStorage.clear()
  calls = []
  saveImpl = async (payload, id) => {
    calls.push({ payload, id })
    return { ...payload, id: id || 41, status: 'DRAFT', version: (payload.version || 0) + 1 }
  }
  const form = reactive({ title: '', content: '', summary: '', category: '', tags: '', contentFormat: 'MARKDOWN' })
  const article = ref(published ? { id: 90, version: 2, status: 'PUBLISHED' } : null)
  let draft
  const root = document.createElement('div'); document.body.append(root)
  const app = createApp({ setup() { draft = useTopicDraft(form, article, () => key); return () => h('div') } })
  app.mount(root)
  draft.initialize(draft.snapshot())
  return { form, article, draft, close() { app.unmount(); root.remove() } }
}
const turn = () => new Promise(r => setImmediate(r))
test('typing autosaves after debounce with no manual save action', async () => {
  const f = mount()
  try {
    f.form.title = 'A topic'; f.form.category = '软件工程'; f.form.content = 'Body'
    assert.equal(JSON.parse(window.localStorage.getItem('topic-draft:7:new')).form.content, 'Body')
    await new Promise(r => setTimeout(r, 850))
    assert.equal(calls.length, 1); assert.equal(f.draft.status.value, 'Saved')
    assert.equal(f.article.value.id, 41)
  } finally { f.close() }
})
test('body-only drafts survive remount and are isolated by account', async () => {
  const f = mount(); f.form.content = 'Unfinished body'
  await f.draft.flush(); assert.equal(calls.length, 0)
  assert.equal(f.draft.status.value, 'Draft saved on this device'); f.close()
  const restored = mount({ reset: false })
  assert.equal(restored.draft.read().form.content, 'Unfinished body'); restored.close()
  const other = mount({ key: 'topic-draft:8:new', reset: false })
  assert.equal(other.draft.read(), null); other.close()
})
test('edits during a pending save are serialized and use the new server version', async () => {
  const f = mount()
  try {
    let release
    saveImpl = (payload, id) => {
      calls.push({ payload, id })
      return new Promise(resolve => { release = () => resolve({ ...payload, id: 41, version: calls.length, status: 'DRAFT' }) })
    }
    f.form.title = 'Topic'; f.form.category = '软件工程'; f.form.content = 'First'
    const saving = f.draft.flush(); await turn()
    f.form.content = 'Newest'; release(); await turn()
    assert.equal(calls.length, 2); assert.equal(calls[1].id, 41)
    assert.equal(calls[1].payload.version, 1); assert.equal(calls[1].payload.content, 'Newest')
    release(); await saving
    assert.equal(f.draft.dirty.value, false)
  } finally { f.close() }
})
test('a network failure keeps recovery content and retry syncs without losing it', async () => {
  const f = mount()
  try {
    const success = saveImpl; saveImpl = async () => { throw new Error('Offline') }
    f.form.title = 'Topic'; f.form.category = '软件工程'; f.form.content = 'Keep me'
    await f.draft.flush()
    assert.match(f.draft.status.value, /sync failed/); assert.equal(f.draft.read().form.content, 'Keep me')
    saveImpl = success; await f.draft.flush()
    assert.equal(f.draft.status.value, 'Saved')
  } finally { f.close() }
})
test('published edits are only recovered locally and never automatically published', async () => {
  const f = mount({ published: true })
  try {
    f.form.title = 'Revised'; f.form.category = '软件工程'; f.form.content = 'Private edit'
    await f.draft.flush()
    assert.equal(calls.length, 0); assert.equal(f.draft.status.value, 'Changes saved on this device')
  } finally { f.close() }
})
test('publishing clears both new and numbered recovery entries and cancels pending debounce', async () => {
  const f = mount()
  try {
    f.form.title = 'Topic'; f.form.category = '软件工程'; await f.draft.flush()
    assert.ok(window.localStorage.getItem('topic-draft:7:41'))
    f.form.content = 'Final'; f.draft.clear(); await f.draft.flush()
    assert.equal(window.localStorage.getItem('topic-draft:7:new'), null)
    assert.equal(window.localStorage.getItem('topic-draft:7:41'), null)
    assert.equal(calls.length, 1)
  } finally { f.close() }
})
test('storage quota failures are not reported as successful saves', async () => {
  const f = mount(); const original = dom.window.Storage.prototype.setItem
  try {
    dom.window.Storage.prototype.setItem = () => { throw new Error('quota') }
    f.form.content = 'Unpersisted'
    assert.equal(f.draft.locallySaved.value, false)
    assert.match(f.draft.status.value, /could not be saved/)
  } finally { dom.window.Storage.prototype.setItem = original; f.close() }
})
test('invalid tags stay recoverable without sending invalid server writes', async () => {
  const f = mount()
  try {
    f.form.title = 'Topic'; f.form.category = '软件工程'; f.form.tags = 'a,b,c,d,e,f'
    await f.draft.flush(); assert.equal(calls.length, 0)
    assert.equal(f.draft.read().form.tags, 'a,b,c,d,e,f')
    f.form.tags = 'a,b'; await f.draft.flush(); assert.deepEqual(calls[0].payload.tags, ['a', 'b'])
  } finally { f.close() }
})
