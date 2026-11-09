/** Run only against the isolated forum test service documented in docs/TESTING.md. */
import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import { chromium } from 'playwright-core'
const base = process.env.FORUM_TEST_URL
if (
  !/^http:\/\/(localhost|127\.0\.0\.1):\d+$/.test(base || '') ||
  !process.env.FORUM_TEST_DB_SOCKET?.startsWith('/tmp/') ||
  !/^forum_[a-z0-9_]*test[a-z0-9_]*$/.test(process.env.FORUM_TEST_DB_NAME || '')
)
  throw new Error(
    'Use an isolated loopback forum_*test* database and /tmp socket.',
  )
const output = '../docs/screenshots/personalization'
await mkdir(output, { recursive: true })
const browser = await chromium.launch({
  executablePath: process.env.CHROME_PATH || '/usr/bin/google-chrome-stable',
  headless: true,
  args: ['--no-sandbox', '--no-proxy-server'],
})
const context = await browser.newContext({
  viewport: { width: 1440, height: 1100 },
  reducedMotion: 'no-preference',
  colorScheme: 'light',
})
const second = await browser.newContext()
const page = await context.newPage(),
  errors = [],
  checks = []
page.setDefaultTimeout(12000)
page.on('pageerror', (e) => errors.push(e.message))
const password = 'Forum-test-only-123!',
  username = 'nav_' + Date.now().toString(36)
async function api(client, method, path, data) {
  const csrf = await (await client.get(base + '/api/auth/csrf')).json()
  const r = await client.fetch(base + '/api' + path, {
    method,
    data,
    headers: { [csrf.headerName]: csrf.token },
  })
  assert.ok(r.ok(), `${method} ${path}: ${r.status()} ${await r.text()}`)
  return r.status() === 204 ? null : r.json()
}
async function signup(client, name) {
  await api(client, 'POST', '/auth/register', {
    username: name,
    email: name + '@example.test',
    password,
    confirmPassword: password,
  })
  return api(client, 'POST', '/auth/login', { identifier: name, password })
}
async function editor(kind) {
  await page
    .getByRole('button', { name: `Edit ${kind} navigation`, exact: true })
    .click()
  const modal = page.getByRole('dialog')
  await modal.waitFor()
  return modal
}
async function save(modal) {
  await modal.getByRole('button', { name: 'Save', exact: true }).click()
  await modal.waitFor({ state: 'hidden' })
}
async function snap(name) {
  await page.screenshot({ path: `${output}/${name}.png` })
}
try {
  await signup(page.request, username)
  await signup(second.request, username + '_b')
  const user = await (await page.request.get(base + '/api/auth/me')).json()
  await page.goto(base + '/categories')
  await page.locator('.category-entry').nth(12).waitFor()
  assert.equal(await page.title(), '码上 · 西农CS论坛')
  assert.match(
    await page.locator('#site-favicon').getAttribute('href'),
    /favicon\.svg\?v=mashang-3/,
  )
  const favicon = await page.request.get(base + '/favicon.svg?v=mashang-3')
  assert.ok(
    (await favicon.text()).includes(
      await page.locator('.brand-mark path').getAttribute('d'),
    ),
  )
  checks.push('Browser title and versioned favicon match the site brand')
  const motion = await page.evaluate(async () => {
    const state = () => {
      const w = document.querySelector('.workspace'),
        h = document.querySelector('.header-inner'),
        m = document.querySelector('.app-main')
      return {
        width: w.getBoundingClientRect().width,
        headerWidth: h.getBoundingClientRect().width,
        mainX: m.getBoundingClientRect().x,
      }
    }
    const samples = [{ t: 0, ...state() }],
      animations = []
    const start = performance.now()
    document.querySelector('[aria-label="Toggle navigation"]').click()
    for (let i = 0; i < 30; i++) {
      await new Promise(requestAnimationFrame)
      for (const a of document.getAnimations())
        if (
          a.effect.target.matches('.workspace,.header-inner') &&
          !animations.some(
            (x) =>
              x.property === a.transitionProperty &&
              x.target === a.effect.target.className,
          )
        )
          animations.push({
            target: a.effect.target.className,
            property: a.transitionProperty,
            ...a.effect.getTiming(),
          })
      samples.push({ t: performance.now() - start, ...state() })
    }
    return { samples, animations }
  })
  assert.equal(motion.animations.length, 3)
  assert.ok(
    motion.animations.every(
      (a) => a.duration === 250 && a.easing === 'ease-in-out',
    ),
  )
  assert.ok(Math.abs(motion.samples[0].mainX - 354.5) < 1)
  assert.ok(Math.abs(motion.samples.at(-1).mainX - 175) < 1)
  assert.ok(motion.samples.some((s) => s.width > 1111 && s.width < 1408))
  assert.equal(await page.locator('.site-sidebar').getAttribute('inert'), '')
  await snap('sidebar-closed')
  await page
    .getByRole('button', { name: 'Toggle navigation', exact: true })
    .click()
  await page.waitForFunction(
    () =>
      Math.abs(
        document.querySelector('.app-main').getBoundingClientRect().x - 354.5,
      ) < 1,
  )
  await page
    .locator('.site-sidebar')
    .getByRole('link', { name: 'Topics', exact: true })
    .click()
  await page.waitForURL('**/latest')
  assert.equal(
    await page
      .getByRole('button', { name: 'Toggle navigation', exact: true })
      .getAttribute('aria-expanded'),
    'true',
  )
  await page.evaluate(async () => {
    const b = document.querySelector('[aria-label="Toggle navigation"]')
    b.click()
    await new Promise((r) => setTimeout(r, 60))
    b.click()
  })
  await page.waitForFunction(
    () =>
      Math.abs(
        document.querySelector('.app-main').getBoundingClientRect().x - 354.5,
      ) < 1,
  )
  checks.push(
    '250ms ease-in-out sidebar and header motion matches measured reference; navigation stays open and rapid reversal works',
  )
  let modal = await editor('categories')
  await modal.getByRole('button', { name: 'Deselect all', exact: true }).click()
  await modal.getByRole('checkbox', { name: '数学基础', exact: true }).check()
  await modal.getByRole('checkbox', { name: 'Web开发', exact: true }).check()
  await modal.getByLabel('Filter selections').selectOption('selected')
  assert.equal(await modal.getByRole('checkbox').count(), 2)
  await modal.getByLabel('Filter selections').selectOption('unselected')
  assert.equal(await modal.getByRole('checkbox').count(), 11)
  await modal.getByLabel('Filter selections').selectOption('all')
  await modal.getByLabel('Search categories').fill('数学')
  assert.equal(await modal.getByRole('checkbox').count(), 2)
  await modal.getByLabel('Search categories').fill('')
  await snap('edit-categories')
  await save(modal)
  assert.equal(
    await page.locator('.site-sidebar a[href*="category="]').count(),
    2,
  )
  await page.reload()
  await page.waitForFunction(
    () =>
      document.querySelectorAll('.site-sidebar a[href*="category="]').length ===
      2,
  )
  modal = await editor('categories')
  await modal.getByRole('button', { name: 'Reset to defaults' }).click()
  await modal.getByRole('button', { name: 'Close navigation editor' }).click()
  assert.equal(
    await page.locator('.site-sidebar a[href*="category="]').count(),
    2,
  )
  modal = await editor('categories')
  await modal.getByRole('button', { name: 'Deselect all', exact: true }).click()
  await save(modal)
  await page.reload()
  await page
    .getByRole('button', { name: 'Edit categories navigation', exact: true })
    .waitFor()
  await page.waitForFunction(
    () =>
      document.querySelectorAll('.site-sidebar a[href*="category="]').length ===
      0,
  )
  modal = await editor('categories')
  await modal.getByRole('button', { name: 'Reset to defaults' }).click()
  await save(modal)
  assert.equal(
    await page.locator('.site-sidebar a[href*="category="]').count(),
    13,
  )
  modal = await editor('categories')
  await modal.getByRole('button', { name: 'Deselect all', exact: true }).click()
  await modal.getByRole('checkbox', { name: '数学基础', exact: true }).check()
  await modal.getByRole('checkbox', { name: 'Web开发', exact: true }).check()
  await save(modal)
  modal = await editor('tags')
  await modal.getByRole('button', { name: 'Deselect all', exact: true }).click()
  await modal.getByRole('checkbox', { name: 'Python', exact: true }).check()
  await modal.getByRole('checkbox', { name: '学习笔记', exact: true }).check()
  await snap('edit-tags')
  await save(modal)
  await page.reload()
  await page.waitForFunction(
    () =>
      document.querySelectorAll('.site-sidebar a[href*="tag="]').length === 2,
  )
  const preference = await (
    await page.request.get(base + '/api/my/navigation')
  ).json()
  assert.equal(preference.categoryIds.length, 2)
  assert.deepEqual(preference.tagNames, ['Python', '学习笔记'])
  assert.deepEqual(
    await (await second.request.get(base + '/api/my/navigation')).json(),
    { categoryIds: null, tagNames: null },
  )
  modal = await editor('tags')
  await page.keyboard.press('Escape')
  await modal.waitFor({ state: 'hidden' })
  assert.equal(
    await page
      .getByRole('button', { name: 'Toggle navigation', exact: true })
      .getAttribute('aria-expanded'),
    'true',
  )
  checks.push(
    'Category/tag filtering, deselect, cancel, reset, empty selection, save, reload, Escape and per-user isolation',
  )
  await page.goto(base + '/settings')
  const png = Buffer.from(
    await page.evaluate(() => {
      const c = document.createElement('canvas')
      c.width = 80
      c.height = 60
      const g = c.getContext('2d')
      g.fillStyle = '#26251e'
      g.fillRect(0, 0, 80, 60)
      g.fillStyle = '#f7f7f4'
      g.fillRect(15, 15, 50, 30)
      return c.toDataURL('image/png').split(',')[1]
    }),
    'base64',
  )
  await page
    .getByLabel('选择头像图片', { exact: true })
    .setInputFiles({ name: 'avatar.png', mimeType: 'image/png', buffer: png })
  await page.getByAltText('待保存的头像预览').waitFor()
  await page.getByRole('button', { name: '保存头像', exact: true }).click()
  await page
    .getByText('头像已保存，帖子与个人主页已同步更新。', { exact: true })
    .waitFor()
  await page.waitForFunction(
    () =>
      document.querySelector('.account-menu .avatar-image')?.naturalWidth ===
      256,
  )
  await snap('avatar-settings')
  assert.equal(
    (
      await page.request.get(base + `/api/profiles/${user.id}/avatar`)
    ).headers()['content-type'],
    'image/png',
  )
  await page.reload()
  await page.waitForFunction(
    () => document.querySelector('.settings-avatar img')?.naturalWidth === 256,
  )
  await page.getByRole('button', { name: '恢复默认头像', exact: true }).click()
  await page.getByText('已恢复默认头像。', { exact: true }).waitFor()
  assert.equal(
    (
      await page.request.get(base + `/api/profiles/${user.id}/avatar`)
    ).headers()['content-type'],
    'image/svg+xml',
  )
  await page
    .getByLabel('选择头像图片', { exact: true })
    .setInputFiles({
      name: 'bad.svg',
      mimeType: 'image/svg+xml',
      buffer: Buffer.from('<svg/>'),
    })
  await page
    .getByText('请选择不超过 2 MB 的 PNG 或 JPEG 图片', { exact: true })
    .waitFor()
  await page
    .getByLabel('选择头像图片', { exact: true })
    .setInputFiles({ name: 'avatar.png', mimeType: 'image/png', buffer: png })
  await page.getByRole('button', { name: '保存头像', exact: true }).click()
  await page
    .getByText('头像已保存，帖子与个人主页已同步更新。', { exact: true })
    .waitFor()
  await page.goto(base + `/u/${user.id}`)
  await page.waitForFunction(
    () => document.querySelector('.profile-avatar img')?.naturalWidth === 256,
  )
  const draft = await api(page.request, 'POST', '/my/articles', {
    title: 'Avatar persistence verification',
    summary: '',
    content: 'A real topic verifies the uploaded author avatar.',
    contentFormat: 'MARKDOWN',
    category: 'Web开发',
    tags: ['Python'],
  })
  await api(page.request, 'POST', `/my/articles/${draft.id}/publish`, {
    version: draft.version,
  })
  await api(page.request, 'POST', `/articles/${draft.id}/comments`, {
    content: 'The same uploaded avatar appears on this reply.',
  })
  await page.goto(base + `/t/${draft.id}`)
  await page.waitForFunction(() => {
    const images = [
      ...document.querySelectorAll('.forum-post .post-avatar img'),
    ]
    return (
      images.length === 2 && images.every((image) => image.naturalWidth === 256)
    )
  })
  await snap('avatar-topic')
  checks.push(
    'Real avatar upload, preview, normalized image, header/profile/topic/reply sync, reload, default restore and invalid file rejection',
  )
  await page.getByRole('button', { name: 'Toggle color theme' }).click()
  assert.match(
    await page.locator('#site-favicon').getAttribute('href'),
    /favicon-dark\.svg/,
  )
  modal = await editor('categories')
  await snap('edit-categories-dark')
  await page.keyboard.press('Escape')
  await page.getByRole('button', { name: 'Toggle color theme' }).click()
  await page.setViewportSize({ width: 390, height: 844 })
  await page.reload()
  await page
    .getByRole('button', { name: 'Toggle navigation', exact: true })
    .click()
  modal = await editor('categories')
  assert.ok(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  )
  await snap('edit-categories-mobile')
  await page.keyboard.press('Escape')
  await page
    .getByRole('button', { name: '关闭导航', exact: true })
    .click({ position: { x: 350, y: 100 } })
  await page.waitForFunction(
    () =>
      getComputedStyle(document.querySelector('.site-sidebar')).visibility ===
      'hidden',
  )
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page
    .getByRole('button', { name: 'Toggle navigation', exact: true })
    .click()
  assert.equal(
    await page
      .locator('.site-sidebar')
      .evaluate((el) => getComputedStyle(el).transitionDuration),
    '0s',
  )
  checks.push('Dark favicon, desktop/mobile dialogs and reduced-motion support')
  assert.deepEqual(errors, [])
  await writeFile(
    output + '/result.json',
    JSON.stringify(
      {
        checks,
        userId: user.id,
        topicId: draft.id,
        username,
        preferences: preference,
        motion,
        runtimeErrors: errors,
      },
      null,
      2,
    ) + '\n',
  )
  console.log('PASS', checks.join('\nPASS '))
} catch (e) {
  await snap('failure')
  throw e
} finally {
  await browser.close()
}
