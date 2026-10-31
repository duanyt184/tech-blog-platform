import { Marked } from 'marked'
import DOMPurify from 'dompurify'
import hljs from 'highlight.js/lib/common'
import markedKatex from 'marked-katex-extension'
import type { ContentFormat } from '../types/article'

export interface ArticleHeading { id: string; text: string; depth: number }
export interface RenderedArticle { html: string; headings: ArticleHeading[] }

const escapeHtml = (text: string) => text.replace(/[&<>"']/g, char => ({
  '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
})[char]!)

/** Source remains unchanged in storage. Only this sanitized result may enter v-html. */
export function renderArticleBody(content: string, format: ContentFormat = 'MARKDOWN'): RenderedArticle {
  if (format === 'PLAIN_TEXT') return { html: escapeHtml(content), headings: [] }
  const slots = new Map<string, string>()
  // getRandomValues also works on local HTTP deployments without a secure context.
  const prefix = `md-slot-${Array.from(crypto.getRandomValues(new Uint32Array(4)), value => value.toString(16)).join('-')}-`
  function trustedSlot(html: string, block: boolean) {
    const key = `${prefix}${slots.size}`
    slots.set(key, html)
    const tag = block ? 'div' : 'span'
    return `<${tag} data-md-slot="${key}"></${tag}>`
  }
  const math = markedKatex({
    nonStandard: true, throwOnError: false, trust: false, strict: 'ignore',
    maxExpand: 1000, maxSize: 20, output: 'htmlAndMathml',
  })
  // Only generated math may supply layout styles; raw HTML is sanitized separately.
  for (const extension of math.extensions || []) {
    if ('renderer' in extension && extension.renderer) {
      const renderer = extension.renderer
      extension.renderer = function (token) {
        const html = renderer.call(this, token)
        return typeof html === 'string' ? trustedSlot(html, token.type === 'blockKatex') : html
      }
    }
  }
  const parser = new Marked({ gfm: true, breaks: false, async: false }, math, {
    renderer: {
      code({ text, lang }) {
        const language = (lang || '').trim().split(/\s+/)[0]!.toLowerCase()
        const highlighted = text.length <= 20000 && hljs.getLanguage(language)
          ? hljs.highlight(text, { language, ignoreIllegals: true }).value : escapeHtml(text)
        return trustedSlot(`<div class="code-block"><div class="code-caption">${escapeHtml(language || '代码')}</div><pre tabindex="0"><code class="hljs">${highlighted}</code></pre></div>`, true)
      },
    },
  })
  const fragment = DOMPurify.sanitize(parser.parse(content) as string, {
    USE_PROFILES: { html: true }, RETURN_DOM_FRAGMENT: true,
    FORBID_TAGS: ['style', 'form', 'button', 'textarea', 'select', 'iframe', 'object', 'embed'],
    FORBID_ATTR: ['style', 'class', 'id', 'name'],
    ALLOW_DATA_ATTR: false, ADD_ATTR: ['data-md-slot'],
  })
  for (const slot of fragment.querySelectorAll('[data-md-slot]')) {
    const html = slots.get(slot.getAttribute('data-md-slot') || '')
    if (html !== undefined) {
      slot.replaceWith(DOMPurify.sanitize(html, {
        RETURN_DOM_FRAGMENT: true, USE_PROFILES: { html: true, mathMl: true, svg: true },
        FORBID_TAGS: ['style', 'script', 'foreignObject'],
        ADD_TAGS: ['annotation'], ADD_ATTR: ['encoding'],
        ALLOW_DATA_ATTR: false,
      }))
    } else slot.removeAttribute('data-md-slot')
  }
  for (const input of fragment.querySelectorAll('input')) {
    if (input.type !== 'checkbox') input.remove()
    else { input.disabled = true; input.tabIndex = -1; input.closest('li')?.classList.add('task-list-item') }
  }
  const headings: ArticleHeading[] = []
  const ids = new Set<string>()
  for (const element of fragment.querySelectorAll('h1,h2,h3,h4,h5,h6')) {
    // KaTeX supplies both visual HTML and MathML; avoid repeating a formula in the TOC.
    const label = element.cloneNode(true) as HTMLElement
    for (const formula of label.querySelectorAll('.katex')) {
      formula.replaceWith(document.createTextNode(formula.querySelector('annotation')?.textContent || '公式'))
    }
    const text = label.textContent?.trim() || '未命名章节'
    const slug = text.toLowerCase().replace(/[^\p{L}\p{N}\s_-]/gu, '').trim().replace(/\s+/g, '-') || 'section'
    let id = `md-${slug}`
    for (let suffix = 1; ids.has(id); suffix++) id = `md-${slug}-${suffix}`
    ids.add(id)
    element.id = id
    element.setAttribute('tabindex', '-1')
    headings.push({ id, text, depth: Number(element.tagName.slice(1)) })
  }
  for (const anchor of fragment.querySelectorAll('a[href]')) {
    const href = anchor.getAttribute('href')!
    if (href.startsWith('#')) {
      try {
        const target = decodeURIComponent(href.slice(1))
        if (ids.has(`md-${target}`)) anchor.setAttribute('href', `#md-${target}`)
      } catch { /* Invalid escapes remain inert fragment links. */ }
    }
    anchor.setAttribute('rel', 'nofollow ugc noopener noreferrer')
    if (/^https?:\/\//i.test(href)) anchor.setAttribute('target', '_blank')
  }
  for (const image of fragment.querySelectorAll('img')) {
    image.loading = 'lazy'
    image.setAttribute('referrerpolicy', 'no-referrer')
    if (!/^(https?:\/\/|\/(?!\/)|\.\.?\/)/i.test(image.getAttribute('src') || '')) image.removeAttribute('src')
    image.removeAttribute('srcset')
  }
  for (const table of fragment.querySelectorAll('table')) {
    const wrapper = document.createElement('div')
    wrapper.className = 'markdown-table'
    wrapper.tabIndex = 0
    wrapper.setAttribute('role', 'region')
    wrapper.setAttribute('aria-label', '表格，可横向滚动')
    table.replaceWith(wrapper)
    wrapper.append(table)
  }
  const container = document.createElement('div')
  container.append(fragment)
  return { html: container.innerHTML, headings }
}
