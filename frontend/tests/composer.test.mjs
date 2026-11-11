import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
import { parse, compileScript } from '@vue/compiler-sfc'
import { JSDOM } from 'jsdom'
const dom = new JSDOM('<html><body></body></html>', { url: 'https://forum.test' })
for (const k of ['window', 'document', 'Document', 'Element', 'HTMLElement', 'SVGElement', 'Node', 'history', 'location']) globalThis[k] = dom.window[k]
globalThis.requestAnimationFrame = fn => setTimeout(fn, 0)
globalThis.cancelAnimationFrame = id => clearTimeout(id)
window.matchMedia = () => ({ matches: true })
const { createApp, h, nextTick, reactive, ref } = await import('vue')
const { createRouter, createMemoryHistory, useRoute } = await import('vue-router')
const moduleUrl = s => 'data:text/javascript;base64,' + Buffer.from(s).toString('base64')
const mocks = new Map()
const stub = moduleUrl('export default { render: () => null }')
const fixture = { user: ref({ id: 7 }), categories: reactive([{ id: 1, name: '软件工程', description: 'Software engineering', color: '#f60' }]), server: null, saves: 0, deletes: 0, failDelete: false }
globalThis.composerFixture = fixture
const api = moduleUrl(`
const f = globalThis.composerFixture;
export async function getArticle() { return structuredClone(f.server) }
export async function saveArticle(payload, id) { f.saves++; f.server = { ...payload, id: id || 41, version: (payload.version || 0) + 1, status: f.server?.status || 'DRAFT' }; return structuredClone(f.server) }
export async function deleteArticle(article) { if (f.failDelete) throw new Error('Delete failed'); f.deletes++; f.server = null }
export async function publishTopic(article) { f.server = { ...article, status: 'PUBLISHED' }; return structuredClone(f.server) }
`)
const utils = moduleUrl(`export const categories = globalThis.composerFixture.categories; export const loadCategories = async () => {}; export const errorMessage = e => e.message; export const categoryColor = () => '#f60'`)
const auth = moduleUrl('export const useAuth = () => ({ user: globalThis.composerFixture.user })')
const background = { setup() { const route = useRoute(); return () => h('p', { id: 'background-route' }, route.fullPath) } }
globalThis.composerBackground = background
const bgUrl = moduleUrl('export default globalThis.composerBackground')
for (const p of ['../api/articles']) mocks.set(p, api)
for (const p of ['../utils/articles']) mocks.set(p, utils)
mocks.set('../composables/useAuth', auth)
mocks.set('../api/http', moduleUrl('export const http = { get: async () => ({ data: [{ name: "testing" }] }) }'))
mocks.set('./composables/useTheme', moduleUrl('export const useTheme = () => {}'))
mocks.set('./components/AppHeader.vue', stub); mocks.set('./components/AppSidebar.vue', stub)
mocks.set('./views/ArticlesView.vue', bgUrl)
function resolve(source) {
  return source.replace(/from ['"]([^'"]+)['"]/g, (_, n) => `from '${mocks.get(n) || import.meta.resolve(n)}'`).replace(/import\(['"]([^'"]+)['"]\)/g, (_, n) => `import('${mocks.get(n) || import.meta.resolve(n)}')`)
}
function transpile(source) { return ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.ESNext } }).outputText }
async function component(path) {
  const { descriptor } = parse(await readFile(new URL('../src/' + path, import.meta.url), 'utf8'))
  const url = moduleUrl(resolve(transpile(compileScript(descriptor, { id: path, inlineTemplate: true }).content)))
  return { url, component: (await import(url)).default }
}
const draftUrl = moduleUrl(resolve(transpile(await readFile(new URL('../src/composables/useTopicDraft.ts', import.meta.url), 'utf8'))))
mocks.set('../composables/useTopicDraft', draftUrl)
const icon = await component('components/AppIcon.vue')
mocks.set('./AppIcon.vue', icon.url); mocks.set('../components/AppIcon.vue', icon.url)
mocks.set('./ArticleBody.vue', stub)
const editor = await component('components/MarkdownEditor.vue'); mocks.set('../components/MarkdownEditor.vue', editor.url)
const taxonomy = await component('components/TopicTaxonomy.vue'); mocks.set('../components/TopicTaxonomy.vue', taxonomy.url)
const forum = await component('components/ForumPage.vue'); mocks.set('./components/ForumPage.vue', forum.url)
const write = await component('views/WriteView.vue')
const appComponent = await component('App.vue')
async function settle() { for (let i = 0; i < 4; i++) { await new Promise(r => setImmediate(r)); await nextTick() } }
function input(el, value) { assert.ok(el); assert.equal(el.disabled, false); el.value = value; el.dispatchEvent(new window.Event('input', { bubbles: true })) }
async function mount(start = '/latest?category=software', server = null) {
  window.localStorage.clear(); fixture.server = server; fixture.saves = 0; fixture.deletes = 0; fixture.failDelete = false
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/latest', component: background }, { path: '/write/:id?', name: 'write', component: write.component }, { path: '/t/:id', component: background },
  ] })
  await router.push(start); await router.isReady()
  const root = document.createElement('div'); document.body.append(root)
  const app = createApp(appComponent.component); app.use(router); app.mount(root); await settle()
  return { root, router, close() { app.unmount(); root.remove() } }
}
test('composer overlays the existing route and restores it with filters intact', async () => {
  const f = await mount()
  try {
    await f.router.push('/write'); await settle()
    assert.equal(f.root.querySelector('#background-route').textContent, '/latest?category=software')
    assert.ok(f.root.querySelector('.topic-composer.is-visible'))
    assert.ok(f.root.querySelector('.editor-actions [type=submit]'))
    assert.ok(f.root.querySelector('.markdown-editor .composer-format-toolbar'))
    assert.doesNotMatch(f.root.textContent, /Save Draft|Unsaved changes/)
    await f.router.push('/latest?category=software'); await settle()
    assert.equal(f.root.querySelector('.topic-composer'), null)
    assert.equal(f.root.querySelector('#background-route').textContent, '/latest?category=software')
  } finally { f.close() }
})
test('searchable category, tag chips, autosave on close, and new-topic recovery work together', async () => {
  const f = await mount('/write')
  try {
    input(f.root.querySelector('.topic-title-input'), 'Recovered title')
    f.root.querySelector('[aria-label="Category"]').click(); await settle()
    input(f.root.querySelector('[aria-label="Search categories"]'), '软件'); await settle()
    f.root.querySelector('[role="option"]').click(); await settle()
    input(f.root.querySelector('.body-editor'), 'Recovered body')
    input(f.root.querySelector('[aria-label="Tags"]'), 'testing')
    f.root.querySelector('[aria-label="Tags"]').dispatchEvent(new window.FocusEvent('blur')); await settle()
    assert.match(f.root.querySelector('.composer-tag-chip').textContent, /testing/)
    await f.router.push('/latest'); await settle()
    assert.equal(fixture.saves, 1); assert.equal(fixture.server.content, 'Recovered body')
    await f.router.push('/write'); await settle()
    assert.equal(f.root.querySelector('.topic-title-input').value, 'Recovered title')
    assert.equal(f.root.querySelector('.body-editor').value, 'Recovered body')
    assert.match(f.root.querySelector('.draft-status').textContent, /Saved/)
    f.root.querySelector('[aria-label="Minimize editor"]').click(); await settle()
    assert.equal(f.root.querySelector('.composer-content').style.display, 'none')
    await f.router.push('/write'); await settle()
    assert.notEqual(f.root.querySelector('.composer-content').style.display, 'none')
    assert.equal(f.root.querySelector('.body-editor').value, 'Recovered body')
  } finally { f.close() }
})
test('create topic flushes pending autosave, publishes once, and clears recovery', async () => {
  const f = await mount('/write')
  try {
    input(f.root.querySelector('.topic-title-input'), 'Publish me')
    f.root.querySelector('[aria-label="Category"]').click(); await settle()
    f.root.querySelector('[role="option"]').click(); await settle()
    input(f.root.querySelector('.body-editor'), 'Final body')
    f.root.querySelector('form').dispatchEvent(new window.Event('submit', { bubbles: true, cancelable: true })); await settle()
    assert.equal(f.router.currentRoute.value.path, '/t/41')
    assert.equal(fixture.server.status, 'PUBLISHED')
    assert.equal(fixture.server.content, 'Final body')
    assert.equal(window.localStorage.getItem('topic-draft:7:new'), null)
    await f.router.push('/write'); await settle()
    assert.equal(f.root.querySelector('.topic-title-input').value, '')
  } finally { f.close() }
})

test('opening waits for a painted collapsed panel and closing cancels queued opening frames', async () => {
  const raf = globalThis.requestAnimationFrame
  const cancel = globalThis.cancelAnimationFrame
  const matchMedia = window.matchMedia
  const frames = new Map()
  let frameId = 0
  globalThis.requestAnimationFrame = fn => { frames.set(++frameId, fn); return frameId }
  globalThis.cancelAnimationFrame = id => frames.delete(id)
  window.matchMedia = () => ({ matches: false })
  const frame = async () => {
    const callbacks = [...frames.values()]; frames.clear()
    callbacks.forEach(fn => fn()); await nextTick()
  }
  let f
  try {
    f = await mount('/write')
    assert.equal(f.root.querySelector('.topic-composer').classList.contains('is-visible'), false)
    await frame()
    assert.equal(f.root.querySelector('.topic-composer').classList.contains('is-visible'), false)
    await frame()
    assert.ok(f.root.querySelector('.topic-composer.is-visible'))
    await f.router.push('/latest'); await settle()
    await f.router.push('/write'); await settle()
    assert.equal(frames.size, 1)
    await f.router.push('/latest'); await settle()
    assert.equal(frames.size, 0)
    assert.equal(f.root.querySelector('.topic-composer'), null)
  } finally {
    f?.close()
    globalThis.requestAnimationFrame = raf
    globalThis.cancelAnimationFrame = cancel
    window.matchMedia = matchMedia
  }
})

async function selectCategory(f) {
  f.root.querySelector('[aria-label="Category"]').click(); await settle()
  f.root.querySelector('[role="option"]').click(); await settle()
}
test('reference initial state gates editing on category and preview controls preserve content', async () => {
  const f = await mount('/write')
  try {
    assert.equal(f.root.querySelector('.composer-heading h1').textContent.trim(), 'Create topic')
    assert.equal(f.root.querySelector('.topic-title-input').placeholder, 'Title')
    assert.equal(f.root.querySelector('.body-editor').placeholder, 'Select a category before typing here.')
    assert.equal(f.root.querySelector('.body-editor').disabled, true)
    assert.ok([...f.root.querySelectorAll('.composer-format-toolbar button')].every(b => b.disabled))
    assert.equal(f.root.querySelector('[aria-label="Tags"]'), null)
    await selectCategory(f)
    assert.equal(f.root.querySelector('.body-editor').disabled, false)
    input(f.root.querySelector('.body-editor'), 'Keep **Markdown** intact')
    f.root.querySelector('[aria-label="Hide preview"]').click(); await settle()
    assert.equal(f.root.querySelector('.editor-preview'), null)
    f.root.querySelector('[aria-label="Show preview"]').click(); await settle()
    assert.ok(f.root.querySelector('.editor-preview'))
    assert.equal(f.root.querySelector('.body-editor').value, 'Keep **Markdown** intact')
    f.root.querySelector('[aria-label="Plain text"]').click(); await settle()
    assert.equal(f.root.querySelector('.body-editor').value, 'Keep **Markdown** intact')
    f.root.querySelector('[aria-label="Markdown"]').click(); await settle()
    assert.equal(f.root.querySelector('.body-editor').value, 'Keep **Markdown** intact')
  } finally { f.close() }
})
test('Discard asks before deleting, cancels safely, and clears server and local drafts on confirmation', async () => {
  const f = await mount('/write')
  try {
    input(f.root.querySelector('.topic-title-input'), 'Discard me')
    await selectCategory(f)
    input(f.root.querySelector('.body-editor'), 'A saved draft')
    const button = text => [...f.root.querySelectorAll('button')].find(b => b.textContent.trim() === text)
    button('Discard').click(); await settle()
    assert.ok(f.root.querySelector('[role="alertdialog"]'))
    assert.equal(fixture.deletes, 0)
    button('Keep editing').click(); await settle()
    assert.equal(f.root.querySelector('.body-editor').value, 'A saved draft')
    button('Discard').click(); await settle()
    button('Discard draft').click(); await settle()
    assert.equal(fixture.deletes, 1)
    assert.equal(fixture.server, null)
    assert.equal(window.localStorage.getItem('topic-draft:7:new'), null)
    assert.equal(f.router.currentRoute.value.path, '/latest')
    await f.router.push('/write'); await settle()
    assert.equal(f.root.querySelector('.topic-title-input').value, '')
  } finally { f.close() }
})
test('failed discard retains recoverable content', async () => {
  const f = await mount('/write')
  try {
    input(f.root.querySelector('.topic-title-input'), 'Keep on failure')
    await selectCategory(f)
    input(f.root.querySelector('.body-editor'), 'Do not lose this')
    fixture.failDelete = true
    const button = text => [...f.root.querySelectorAll('button')].find(b => b.textContent.trim() === text)
    button('Discard').click(); await settle()
    button('Discard draft').click(); await settle()
    assert.equal(f.router.currentRoute.value.path, '/write')
    assert.match(f.root.querySelector('[role="alert"]').textContent, /Delete failed/)
    assert.equal(JSON.parse(window.localStorage.getItem('topic-draft:7:new')).form.content, 'Do not lose this')
  } finally { f.close() }
})

test('discarding an edit never deletes a published topic', async () => {
  const f = await mount('/write/41', { id: 41, version: 2, status: 'PUBLISHED', title: 'Published', category: '软件工程', content: 'Public content', tags: [], contentFormat: 'MARKDOWN' })
  try {
    input(f.root.querySelector('.body-editor'), 'Private changes')
    const button = text => [...f.root.querySelectorAll('button')].find(b => b.textContent.trim() === text)
    button('Discard').click(); await settle()
    assert.equal(document.activeElement.textContent.trim(), 'Keep editing')
    button('Discard draft').click(); await settle()
    assert.equal(fixture.deletes, 0)
    assert.equal(fixture.saves, 0)
    assert.equal(fixture.server.content, 'Public content')
    assert.equal(window.localStorage.getItem('topic-draft:7:41'), null)
  } finally { f.close() }
})
test('toolbar media insertion rejects unsafe URLs and adds image Markdown without submitting', async () => {
  const f = await mount('/write')
  try {
    await selectCategory(f)
    f.root.querySelector('[aria-label="Insert GIF from URL"]').click(); await settle()
    input(f.root.querySelector('.composer-media-menu input'), 'javascript:alert(1)')
    f.root.querySelector('.composer-media-menu button').click(); await settle()
    assert.match(f.root.querySelector('.composer-media-menu [role="alert"]').textContent, /https/)
    assert.equal(f.root.querySelector('.body-editor').value, '')
    input(f.root.querySelector('.composer-media-menu input'), 'https://example.test/demo.gif')
    f.root.querySelector('.composer-media-menu button').click(); await settle()
    assert.equal(f.root.querySelector('.body-editor').value, '![GIF](<https://example.test/demo.gif>)')
    assert.equal(f.router.currentRoute.value.path, '/write')
    assert.equal(fixture.saves, 0)
  } finally { f.close() }
})
