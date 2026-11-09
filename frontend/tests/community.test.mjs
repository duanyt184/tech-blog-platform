import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
import { parse, compileScript } from '@vue/compiler-sfc'
import { JSDOM } from 'jsdom'
const dom = new JSDOM('<!doctype html><html><body></body></html>', { url: 'https://blog.test' })
for (const key of ['window', 'document', 'HTMLElement', 'Element', 'SVGElement', 'Node']) globalThis[key] = dom.window[key]
const { createApp, h, nextTick, ref } = await import('vue')
const moduleUrl = source => 'data:text/javascript;base64,' + Buffer.from(source).toString('base64')
const user = ref(null), route = { fullPath: '/article/42' }, calls = []
let state, failWrite
const api = {
 async get(path) { calls.push(['get', path]); return { data: { ...state } } },
 async put(path) { calls.push(['put', path]); if (failWrite) throw new Error('网络暂不可用'); state = { count: 8, liked: true }; return { data: { ...state } } },
 async delete(path) { calls.push(['delete', path]); state = { count: 7, liked: false }; return { data: { ...state } } },
}
globalThis.communityFixture = { user, route, api, router: { async push(to) { calls.push(['route', to]) } } }
const mocks = new Map([
 ['../api/http', moduleUrl('export const http = globalThis.communityFixture.api')],
 ['../composables/useAuth', moduleUrl('export const useAuth = () => ({ user: globalThis.communityFixture.user })')],
 ['vue-router', moduleUrl('export const useRoute = () => globalThis.communityFixture.route; export const useRouter = () => globalThis.communityFixture.router')],
 ['../utils/articles', moduleUrl('export const errorMessage = e => e.message')],
])
const { descriptor } = parse(await readFile(new URL('../src/components/LikeButton.vue', import.meta.url), 'utf8'))
const compiled = compileScript(descriptor, { id: 'LikeButton', inlineTemplate: true })
const source = ts.transpileModule(compiled.content, { compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.ESNext } }).outputText
const resolved = source.replace(/from ['"]([^'"]+)['"]/g, (_, name) => `from '${mocks.get(name) || import.meta.resolve(name)}'`)
const Component = (await import(moduleUrl(resolved))).default
async function settle() { await new Promise(resolve => setImmediate(resolve)); await nextTick() }
async function mount(loggedIn = true) {
 state = { count: 7, liked: false }; calls.length = 0; failWrite = false; user.value = loggedIn ? { id: 3 } : null
 const root = document.createElement('div'); document.body.append(root)
 const app = createApp({ render: () => h(Component, { articleId: 42 }) }); app.mount(root)
 await settle()
 return { root, close() { app.unmount(); root.remove() } }
}
test('like control renders server count and persists like and unlike before updating UI', async () => {
 const f = await mount()
 try {
  const button = f.root.querySelector('button')
  assert.match(button.textContent, /点赞 · 7/)
  button.click(); await settle()
  assert.equal(button.getAttribute('aria-pressed'), 'true'); assert.match(button.textContent, /已点赞 · 8/)
  button.click(); await settle()
  assert.equal(button.getAttribute('aria-pressed'), 'false'); assert.match(button.textContent, /点赞 · 7/)
  assert.deepEqual(calls.map(c => c[0]), ['get', 'put', 'delete'])
 } finally { f.close() }
})
test('guest liking redirects to login with a return path and does not mutate data', async () => {
 const f = await mount(false)
 try {
  f.root.querySelector('button').click(); await settle()
  assert.deepEqual(calls, [['get', '/articles/42/likes'], ['route', { path: '/login', query: { redirect: '/article/42' } }]])
 } finally { f.close() }
})
test('failed like keeps the last confirmed state and presents an actionable error', async () => {
 const f = await mount()
 try {
  failWrite = true; const button = f.root.querySelector('button'); button.click(); await settle()
  assert.equal(button.getAttribute('aria-pressed'), 'false'); assert.match(button.textContent, /7/)
  assert.match(f.root.querySelector('[role="alert"]').textContent, /网络暂不可用/)
  assert.equal(button.disabled, false)
 } finally { f.close() }
})
