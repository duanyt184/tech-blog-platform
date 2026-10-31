import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
import { parse, compileScript } from '@vue/compiler-sfc'
import { JSDOM } from 'jsdom'

const dom = new JSDOM('<!doctype html><html><body></body></html>', { url: 'https://blog.test' })
for (const key of ['window', 'document', 'HTMLElement', 'Element', 'SVGElement', 'Node']) globalThis[key] = dom.window[key]
const { createApp, h, reactive, nextTick } = await import('vue')
const toModule = source => 'data:text/javascript;base64,' + Buffer.from(source).toString('base64')
const transpile = source => ts.transpileModule(source, {
  compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.ESNext },
}).outputText
const modules = new Map()
function resolveImports(source) {
  return source.replace(/from ['"]([^'"]+)['"]/g, (_, name) => `from '${modules.get(name) || import.meta.resolve(name)}'`)
}
const parserUrl = toModule(resolveImports(transpile(await readFile(new URL('../src/utils/articleBody.ts', import.meta.url), 'utf8'))))
modules.set('../utils/articleBody', parserUrl)
const { renderArticleBody } = await import(parserUrl)
async function component(name) {
  const { descriptor } = parse(await readFile(new URL(`../src/components/${name}.vue`, import.meta.url), 'utf8'))
  const compiled = compileScript(descriptor, { id: name, inlineTemplate: true })
  const url = toModule(resolveImports(transpile(compiled.content)))
  modules.set(`./${name}.vue`, url)
  return (await import(url)).default
}
const ArticleBody = await component('ArticleBody')
const MarkdownEditor = await component('MarkdownEditor')
function rendered(content, format = 'MARKDOWN') {
  const result = renderArticleBody(content, format)
  const root = document.createElement('div')
  root.innerHTML = result.html
  return { ...result, root }
}
function mount(Component, props, events = {}) {
  const root = document.createElement('div')
  document.body.append(root)
  const state = reactive(props)
  const app = createApp({ render: () => h(Component, { ...state, ...events }) })
  app.mount(root)
  return { root, state, close: () => { app.unmount(); root.remove() } }
}

test('plain text preserves whitespace and literal Markdown, LaTeX and HTML', () => {
  const content = '  # 标题\n\n\t**原文** $E=mc^2$\n<script>alert(1)</script> & <img src=x onerror=alert(1)>'
  const { root, headings } = rendered(content, 'PLAIN_TEXT')
  assert.equal(root.textContent, content)
  assert.equal(root.children.length, 0)
  assert.deepEqual(headings, [])
})

test('Markdown renders GFM, safe links, stable unique headings and code highlighting', () => {
  const content = '# 开始\r\n第一行\r\n第二行\r\n\r\n## 开始\r\n\n**粗体** *斜体* ~~删除~~ [跳转](#开始) [外链](https://example.test)\n\n> 引用\n\n- [x] 完成\n\n| 列 | 值 |\n| --- | --- |\n| A | B |\n\n```js\nconst x = 1\n```'
  const { root, headings } = rendered(content)
  assert.deepEqual(headings.map(item => item.id), ['md-开始', 'md-开始-1'])
  assert.deepEqual(rendered(content).headings, headings)
  for (const selector of ['strong', 'em', 'del', 'blockquote', '.markdown-table table', 'pre code .hljs-keyword']) assert.ok(root.querySelector(selector), selector)
  assert.equal(root.querySelector('input').disabled, true)
  assert.equal(root.querySelector('a').getAttribute('href'), '#md-开始')
  assert.equal(root.querySelector('a[target="_blank"]').rel, 'nofollow ugc noopener noreferrer')
})

test('LaTeX inline and display math preserve KaTeX layout and accessible MathML', () => {
  const { root } = rendered('中文紧邻$x^2 + y^2$也能渲染。\n\n$$\n\\frac{1}{2} + \\sum_{i=1}^{n} i + \\sqrt{a^2}\n$$\n\n$$\n\\begin{pmatrix}1 & 2 \\\\ 3 & 4\\end{pmatrix}\n$$')
  assert.equal(root.querySelectorAll('.katex').length, 3)
  assert.equal(root.querySelectorAll('.katex-display').length, 2)
  assert.equal(root.querySelectorAll('math').length, 3)
  assert.ok(root.querySelector('.katex [style]'))
  assert.ok(root.querySelector('.katex svg path'))
  assert.match(root.querySelector('annotation').textContent, /x\^2/)
})

test('invalid and unfinished formulas remain readable without breaking the article', () => {
  const { root } = rendered('前文\n\n$$\n\\frac{1}{\n$$\n\n后文 $unfinished')
  assert.ok(root.querySelector('.katex-error'))
  assert.match(root.textContent, /前文/)
  assert.match(root.textContent, /后文 \$unfinished/)
  assert.equal(rendered('').html, '')
})

test('headings containing math have one readable formula in the table of contents', () => {
  const { headings, root } = rendered('## 公式 $x^2$')
  assert.equal(headings[0].text, '公式 x^2')
  assert.ok(root.querySelector('h2 .katex'))
})

test('code fences, inline code and escaped dollars are not parsed as math', () => {
  const { root } = rendered('`$x^2$` 与 \\$5\n\n```tex\n$$\\frac{1}{2}$$\n```\n\n```mermaid\nflowchart LR\n  A --> B\n```\n\n```html\n<img src=x onerror=alert(1)>')
  assert.equal(root.querySelectorAll('.katex, svg, img, figure').length, 0)
  assert.equal(root.querySelectorAll('pre').length, 3)
  assert.match(root.textContent, /flowchart LR/)
  assert.match(root.textContent, /\$5/)
  assert.match(root.textContent, /<img src=x onerror=alert\(1\)>/)
})

test('untrusted HTML, URLs and LaTeX cannot introduce scripts or styles', () => {
  const content = '<script>alert(1)</script>\n\n<img src="javascript:alert(1)" onerror="alert(1)" style="position:fixed" class="katex">\n\n<iframe src="https://example.test"></iframe>\n\n<a href="javascript:alert(1)" onclick="alert(1)" id="md-heading">恶意链接</a>\n\n<span data-md-slot="fake" style="display:none">原文</span>\n\n$\\href{javascript:alert(1)}{click}$ $\\htmlStyle{position:fixed}{x}$ $\\includegraphics{https://example.test/a.png}$'
  const { root } = rendered(content)
  assert.equal(root.querySelectorAll('script, iframe, [onclick], [onerror], [data-md-slot], a[href^="javascript:"]').length, 0)
  assert.equal(root.querySelector('img').hasAttribute('src'), false)
  assert.equal(root.querySelector('img').hasAttribute('style'), false)
  assert.equal(root.querySelector('img').hasAttribute('class'), false)
  assert.equal(root.querySelectorAll('[style*="position"], [style*="display:none"]').length, 0)
  assert.equal(root.querySelectorAll('img').length, 1)
})

test('article body reacts to source and format changes with the same renderer', async () => {
  const fixture = mount(ArticleBody, { content: '# 标题\n\n$x^2$', contentFormat: 'PLAIN_TEXT' })
  try {
    assert.ok(fixture.root.querySelector('.plain-text-body'))
    assert.equal(fixture.root.querySelector('h1'), null)
    fixture.state.contentFormat = 'MARKDOWN'
    await nextTick()
    assert.ok(fixture.root.querySelector('h1'))
    assert.ok(fixture.root.querySelector('.katex'))
    fixture.state.content = '新正文'
    await nextTick()
    assert.equal(fixture.root.textContent.trim(), '新正文')
  } finally { fixture.close() }
})

test('editor switches format without changing source and previews both formats', async () => {
  const content = '# 原文\n\n$x^2$'
  let fixture
  fixture = mount(MarkdownEditor, { modelValue: content, contentFormat: 'PLAIN_TEXT' }, {
    'onUpdate:modelValue': value => { fixture.state.modelValue = value },
    'onUpdate:contentFormat': value => { fixture.state.contentFormat = value },
  })
  const click = label => [...fixture.root.querySelectorAll('button')].find(button => button.textContent === label).click()
  try {
    assert.equal(fixture.root.querySelector('.markdown-tools'), null)
    click('分屏')
    await nextTick()
    assert.equal(fixture.root.querySelector('.plain-text-body').textContent, content)
    const select = fixture.root.querySelector('select')
    select.value = 'MARKDOWN'
    select.dispatchEvent(new dom.window.Event('change', { bubbles: true }))
    await nextTick()
    assert.equal(fixture.state.modelValue, content)
    assert.ok(fixture.root.querySelector('.editor-preview .katex'))
    assert.ok(fixture.root.querySelector('.markdown-tools'))
    assert.equal([...fixture.root.querySelectorAll('button')].some(button => button.textContent === '流程图'), false)
    const input = fixture.root.querySelector('textarea')
    input.setSelectionRange(0, 0)
    click('行内公式')
    await nextTick()
    assert.ok(fixture.state.modelValue.startsWith('$E = mc^2$'))
    click('预览')
    await nextTick()
    assert.equal(fixture.root.querySelectorAll('.editor-preview .katex').length, 2)
    const updated = fixture.state.modelValue
    select.value = 'PLAIN_TEXT'
    select.dispatchEvent(new dom.window.Event('change', { bubbles: true }))
    await nextTick()
    assert.equal(fixture.root.querySelector('.plain-text-body').textContent, updated)
    assert.equal(fixture.state.modelValue, updated)
  } finally { fixture.close() }
})

test('plain text shortcuts and locked editor cannot add Markdown or change format', async () => {
  let changes = 0
  const fixture = mount(MarkdownEditor, { modelValue: '原文', contentFormat: 'PLAIN_TEXT' }, {
    'onUpdate:modelValue': () => changes++, 'onUpdate:contentFormat': () => changes++,
  })
  try {
    const input = fixture.root.querySelector('textarea')
    const shortcut = new dom.window.KeyboardEvent('keydown', { key: 'b', ctrlKey: true, cancelable: true })
    input.dispatchEvent(shortcut)
    assert.equal(shortcut.defaultPrevented, false)
    assert.equal(changes, 0)
    fixture.state.contentFormat = 'MARKDOWN'
    fixture.state.disabled = true
    await nextTick()
    assert.equal(input.disabled, true)
    assert.equal(fixture.root.querySelector('select').disabled, true)
    assert.ok([...fixture.root.querySelectorAll('.markdown-tools button')].every(button => button.disabled))
    input.dispatchEvent(new dom.window.KeyboardEvent('keydown', { key: 'b', ctrlKey: true }))
    fixture.root.querySelector('select').dispatchEvent(new dom.window.Event('change'))
    assert.equal(changes, 0)
  } finally { fixture.close() }
})
