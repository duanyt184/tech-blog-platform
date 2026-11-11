/** Run only against a disposable forum instance: see docs/TESTING.md. */
import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import { execFileSync } from 'node:child_process'
import { chromium } from 'playwright-core'
const base = process.env.FORUM_TEST_URL
const socket = process.env.FORUM_TEST_DB_SOCKET
const db = process.env.FORUM_TEST_DB_NAME
if (
  !base ||
  !/^http:\/\/(127\.0\.0\.1|localhost):\d+$/.test(base) ||
  !socket?.startsWith('/tmp/') ||
  !/^forum_[a-z0-9_]*test[a-z0-9_]*$/.test(db || '')
)
  throw new Error(
    'Set a loopback FORUM_TEST_URL and a disposable /tmp/ FORUM_TEST_DB_SOCKET and forum_*test* database.',
  )
const screenshots = process.env.FORUM_SCREENSHOTS || '../docs/screenshots/local'
await mkdir(screenshots, { recursive: true })
const browser = await chromium.launch({
  executablePath: process.env.CHROME_PATH || '/usr/bin/google-chrome-stable',
  headless: true,
  args: ['--no-sandbox', '--no-proxy-server'],
})
const context = await browser.newContext({
  viewport: { width: 1440, height: 1100 },
  locale: 'en-US',
  colorScheme: 'light',
})
context.setDefaultTimeout(12000)
context.setDefaultNavigationTimeout(20000)
const page = await context.newPage(),
  errors = [],
  checks = []
page.on('pageerror', (error) => errors.push(error.message))
page.on('dialog', (dialog) => dialog.accept())
const stamp = Date.now().toString(36),
  password = 'Forum-test-only-123!'
const names = {
  author: 'alex_' + stamp,
  admin: 'mod_' + stamp,
  reader: 'sam_' + stamp,
}
async function api(client, method, path, data) {
  const csrf = await (await client.get(base + '/api/auth/csrf')).json()
  const response = await client.fetch(base + '/api' + path, {
    method,
    data,
    headers: { [csrf.headerName]: csrf.token },
  })
  if (!response.ok())
    throw new Error(
      `${method} ${path}: ${response.status()} ${await response.text()}`,
    )
  return response.status() === 204 || !(await response.text())
    ? null
    : response.json()
}
async function signup(client, name) {
  await api(client, 'POST', '/auth/register', {
    username: name,
    email: name + '@example.test',
    password,
    confirmPassword: password,
  })
  await api(client, 'POST', '/auth/login', { identifier: name, password })
  return (await client.get(base + '/api/auth/me')).json()
}
async function snap(name) {
  await page.evaluate(() => window.scrollTo(0, 0))
  await page.screenshot({
    path: screenshots + '/' + name + '.png',
    fullPage: false,
  })
}
async function visit(
  path,
  waitSelector = 'main h1, main h2, main table, main .category-entry',
) {
  await page.goto(base + path)
  await page.locator(waitSelector).first().waitFor()
  await page
    .locator('main .empty-state')
    .filter({ hasText: /Loading|正在加载/ })
    .waitFor({ state: 'hidden' })
    .catch(() => {})
  await page.evaluate(() => document.fonts.ready)
}
try {
  // Register in the real UI; browser cookie sessions are then reused by fixture requests.
  await page.goto(base + '/register')
  await page.getByLabel('用户名', { exact: true }).fill(names.author)
  await page
    .getByLabel('邮箱', { exact: true })
    .fill(names.author + '@example.test')
  await page.getByLabel('密码', { exact: true }).fill(password)
  await page.getByLabel('确认密码', { exact: true }).fill(password)
  await page.getByRole('button', { name: '注册并登录' }).click()
  await page.waitForURL(base + '/')
  const author = await (await page.request.get(base + '/api/auth/me')).json()
  assert.equal(author.username, names.author)
  checks.push('UI registration and authenticated session')
  const adminContext = await browser.newContext(),
    readerContext = await browser.newContext()
  const admin = await signup(adminContext.request, names.admin),
    reader = await signup(readerContext.request, names.reader)
  execFileSync('mariadb', [
    '--socket=' + socket,
    '-u',
    'root',
    db,
    '-e',
    `UPDATE users SET role='ADMIN' WHERE id=${Number(admin.id)};`,
  ])
  const fixtures = [
    [
      'Welcome to the community — introduce yourself',
      '软件工程',
      ['community'],
    ],
    [
      'A small guide to a calmer development workflow',
      '编程入门',
      ['workflow', 'productivity'],
    ],
    ['Share what you are building this week', 'Web开发', ['projects']],
    [
      'How do you keep context focused in a large repository?',
      '软件工程',
      ['context', 'workflow'],
    ],
    [
      'Tips for writing clear, reproducible bug reports',
      '编程入门',
      ['debugging'],
    ],
    [
      'Terminal output stops updating after a long command',
      '操作系统',
      ['terminal', 'linux'],
    ],
    ['What belongs in a project rules file?', '编程入门', ['rules']],
    [
      'A keyboard shortcut for switching between conversations',
      '软件工程',
      ['keyboard'],
    ],
    ['Notes from our first community study session', '数学基础', ['community']],
    [
      'Reviewing a database migration before release',
      '软件工程',
      ['database', 'review'],
    ],
    [
      'How we test a feature from the browser to the database',
      '编程入门',
      ['testing'],
    ],
    [
      'A lightweight command line tool for project notes',
      'Web开发',
      ['cli', 'projects'],
    ],
    [
      'Keeping discussions helpful and easy to find',
      '计算机系统基础',
      ['community'],
    ],
    ['A clearer way to group related conversations', '软件工程', ['workflow']],
  ]
  for (let i = 0; i < fixtures.length; i++) {
    const [title, category, tags] = fixtures[i]
    const client = i % 2 ? page.request : readerContext.request
    const topic = await api(client, 'POST', '/my/articles', {
      title,
      category,
      tags,
      summary: 'A community conversation about practical development.',
      content: `## ${title}\n\nI have been exploring this with a small project and would love to hear how others approach it.\n\n- Start with a clear goal and a minimal example.\n- Keep changes easy to review.\n- Share what worked and what you would change.\n\nWhat has been useful in your own workflow?`,
      contentFormat: 'MARKDOWN',
    })
    await api(client, 'POST', `/my/articles/${topic.id}/publish`, {
      version: topic.version,
    })
    if (i < 4) {
      await api(page.request, 'POST', `/articles/${topic.id}/comments`, {
        content:
          'Thanks for sharing. A small, focused example has helped me too.',
      })
      await api(readerContext.request, 'PUT', `/articles/${topic.id}/likes`)
      await api(page.request, 'PUT', `/my/bookmarks/${topic.id}`)
    }
  }
  checks.push('Persistent fixture creation via authenticated API')
  await visit('/write')
  await page
    .getByLabel('Topic title', { exact: true })
    .fill('Browser verification ' + stamp)
  await page.getByRole('button', { name: 'Category', exact: true }).click()
  await page.getByRole('option', { name: /软件工程/ }).click()
  await page.getByLabel('Tags', { exact: true }).fill('testing, workflow')
  await page
    .getByLabel('Markdown 正文', { exact: true })
    .fill(
      '## Browser-tested discussion\n\nA **real** topic stored in MariaDB.\n\n```js\nconsole.log("hello community")\n```',
    )
  await page.locator('.editor-preview h2').waitFor()
  await snap('editor')
  assert.equal(
    await page
      .locator('.topic-composer')
      .evaluate((el) => getComputedStyle(el).position),
    'fixed',
  )
  const draftBody = await page
    .getByLabel('Markdown 正文', { exact: true })
    .inputValue()
  await page
    .getByRole('button', { name: 'Minimize editor', exact: true })
    .click()
  assert.equal(
    await page.getByLabel('Markdown 正文', { exact: true }).isVisible(),
    false,
  )
  await page
    .getByRole('button', { name: 'Restore editor', exact: true })
    .click()
  assert.equal(
    await page.getByLabel('Markdown 正文', { exact: true }).inputValue(),
    draftBody,
  )
  await page.getByRole('separator', { name: 'Resize editor' }).dblclick()
  await page.waitForFunction(() => document.querySelector('.topic-composer').clientHeight >= innerHeight - 52)
  await page.locator('.composer-heading').dblclick()
  await page.waitForFunction(() => !document.querySelector('.topic-composer').classList.contains('is-expanded'))
  await page.getByRole('button', { name: 'Hide preview', exact: true }).click()
  assert.equal(await page.locator('.editor-preview').count(), 0)
  await page.getByRole('button', { name: 'Show preview', exact: true }).click()
  checks.push('Docked composer minimizes and expands without losing text')
  assert.equal(await page.getByRole('button', { name: 'Save Draft', exact: true }).count(), 0)
  await page.locator('.draft-status').filter({ hasText: /^Saved$/ }).waitFor()
  const topicId = Number(await page.locator('.topic-composer').getAttribute('data-draft-id'))
  await page.reload()
  await page.getByLabel('Topic title', { exact: true }).waitFor()
  assert.equal(
    await page.getByLabel('Topic title', { exact: true }).inputValue(),
    'Browser verification ' + stamp,
  )
  checks.push('Draft saved and survives page reload')
  const anonymous = await browser.newContext()
  assert.equal(
    (await anonymous.request.get(base + `/api/articles/${topicId}`)).status(),
    404,
  )
  await anonymous.close()
  checks.push('Draft is not publicly readable')
  await page.getByRole('button', { name: 'Create Topic', exact: true }).click()
  await page.waitForURL(base + `/t/${topicId}`)
  await page.locator('.topic-header h1').waitFor()
  checks.push('UI direct publication without approval')
  await page.getByRole('link', { name: 'Edit', exact: true }).click()
  await page
    .getByLabel('Topic title', { exact: true })
    .fill('Browser verification updated ' + stamp)
  await page.getByRole('button', { name: 'Save changes', exact: true }).click()
  await page.waitForURL(base + `/t/${topicId}`)
  await page
    .getByRole('heading', { name: 'Browser verification updated ' + stamp })
    .waitFor()
  checks.push('Published topic edited directly')
  await page.getByRole('button', { name: '♡ 点赞 · 0', exact: true }).click()
  await page
    .getByRole('button', { name: '♡ 已点赞 · 1', exact: true })
    .waitFor()
  await page.getByRole('button', { name: '♧ Bookmark', exact: true }).click()
  await page.getByRole('button', { name: '▣ Saved', exact: true }).waitFor()
  checks.push('Topic like and bookmark persist')
  await page
    .getByLabel('Reply content', { exact: true })
    .fill('My **browser reply**')
  await page
    .locator('#reply-composer')
    .getByRole('button', { name: '↶ Reply', exact: true })
    .click()
  await page.locator('.forum-replies .forum-post').first().waitFor()
  await page
    .locator('.forum-replies .forum-post')
    .first()
    .getByRole('button', { name: 'Edit', exact: true })
    .click()
  await page
    .getByLabel('Reply content', { exact: true })
    .fill('My **edited browser reply**')
  await page.getByRole('button', { name: 'Save edit', exact: true }).click()
  await page
    .getByText('edited browser reply', { exact: false })
    .first()
    .waitFor()
  await page
    .locator('.forum-replies .forum-post')
    .first()
    .getByRole('button', { name: '♡ 0', exact: true })
    .click()
  await page
    .locator('.forum-replies .forum-post')
    .first()
    .getByRole('button', { name: '♡ 1', exact: true })
    .waitFor()
  await page
    .locator('.forum-replies .forum-post')
    .first()
    .getByRole('button', { name: '❞ Quote', exact: true })
    .click()
  assert.match(await page.getByLabel('Reply content').inputValue(), /^> /)
  await page.getByLabel('Reply content').fill('')
  checks.push('Reply, reply edit, reply like and quotation')
  await snap('topic')
  await page.reload()
  await page.getByRole('button', { name: '▣ Saved', exact: true }).waitFor()
  await page
    .getByRole('button', { name: '♡ 已点赞 · 1', exact: true })
    .waitFor()
  checks.push('Interactions survive reload')
  await api(readerContext.request, 'POST', `/articles/${topicId}/reports`, {
    reason: 'Review this test topic',
  })
  await api(adminContext.request, 'POST', '/admin/rules', {
    title: 'Browser QA detection',
    description: 'Test-only rule',
    pattern: 'qa-trigger-' + stamp,
    enabled: true,
  })
  const flagged = await api(page.request, 'POST', '/my/articles', {
    title: 'Automatic review test',
    category: '软件工程',
    tags: ['testing'],
    content: 'qa-trigger-' + stamp,
    contentFormat: 'MARKDOWN',
  })
  await api(page.request, 'POST', `/my/articles/${flagged.id}/publish`, {
    version: flagged.version,
  })
  checks.push('User report and automatic moderation enqueue')
  for (const [name, path] of [
    ['home', '/'],
    ['categories', '/categories'],
    ['latest', '/latest'],
    ['top', '/top'],
    ['tags', '/tags'],
    ['search', '/search?q=Browser&author=' + names.author],
    ['profile', `/u/${author.id}`],
    ['activity', `/u/${author.id}?tab=activity`],
    ['drafts', '/drafts'],
    ['my-posts', '/my-articles'],
    ['bookmarks', '/bookmarks'],
    ['settings', '/settings'],
    ['guidelines', '/guidelines'],
  ]) {
    await visit(path)
    await snap(name)
    assert.ok(
      !(await page.locator('main').innerText()).includes('操作失败'),
      'No API error on ' + name,
    )
  }
  checks.push('All public and personal page routes render')
  await visit('/')
  await page
    .getByRole('button', { name: 'Toggle color theme', exact: true })
    .click()
  assert.equal(await page.locator('html').getAttribute('data-theme'), 'dark')
  await snap('home-dark')
  await page.reload()
  await page.locator('.category-entry').first().waitFor()
  assert.equal(await page.locator('html').getAttribute('data-theme'), 'dark')
  await page
    .getByRole('button', { name: 'Toggle color theme', exact: true })
    .click()
  checks.push('Dark theme and persisted preference')
  const adminPage = await adminContext.newPage()
  adminPage.on('pageerror', (e) => errors.push(e.message))
  await adminPage.setViewportSize({ width: 1440, height: 1100 })
  await adminPage.goto(base + '/review')
  await adminPage
    .getByRole('heading', { name: 'Review queue', exact: true })
    .waitFor()
  await adminPage.locator('.moderation-card').first().waitFor()
  await adminPage.screenshot({ path: screenshots + '/review.png' })
  let queue = await (
    await adminContext.request.get(base + '/api/editor/moderation')
  ).json()
  const report = queue.find((r) => r.article_id === topicId)
  assert.ok(report)
  await api(
    adminContext.request,
    'POST',
    `/editor/moderation/${report.id}/resolve`,
    { action: 'DISMISS', note: 'Verified in browser test' },
  )
  checks.push('Moderator decision persisted')
  for (const tab of [
    'users',
    'articles',
    'comments',
    'categories',
    'tags',
    'rules',
  ]) {
    await adminPage.goto(base + '/admin?tab=' + tab)
    await adminPage.locator('main h1').waitFor()
    await adminPage
      .locator('main .empty-state')
      .filter({ hasText: '正在加载' })
      .waitFor({ state: 'hidden' })
      .catch(() => {})
    await adminPage.screenshot({ path: screenshots + '/admin-' + tab + '.png' })
    assert.ok(
      !(await adminPage.locator('main').innerText()).includes('操作失败'),
    )
  }
  checks.push('All six administration modules render')
  // Mobile checks use a fresh anonymous context to test the closed navigation state.
  const mobile = await browser.newContext({
    viewport: { width: 390, height: 844 },
    isMobile: true,
    deviceScaleFactor: 1,
    locale: 'en-US',
  })
  const mobilePage = await mobile.newPage()
  mobilePage.on('pageerror', (e) => errors.push(e.message))
  for (const [name, path] of [
    ['home', '/'],
    ['latest', '/latest'],
    ['topic', `/t/${topicId}`],
    ['search', '/search'],
    ['tags', '/tags'],
    ['profile', `/u/${author.id}`],
    ['login', '/login'],
    ['register', '/register'],
  ]) {
    await mobilePage.goto(base + path)
    await mobilePage.locator('main h1, main h2, main table').first().waitFor()
    await mobilePage.waitForTimeout(150)
    const overflow = await mobilePage.evaluate(
      () => document.documentElement.scrollWidth > innerWidth + 1,
    )
    assert.equal(
      overflow,
      false,
      'No horizontal page overflow on mobile ' + name,
    )
    await mobilePage.screenshot({
      path: screenshots + '/' + name + '-mobile.png',
    })
  }
  await mobilePage.goto(base + '/')
  await mobilePage.getByRole('button', { name: 'Toggle navigation' }).click()
  await mobilePage.getByRole('link', { name: 'All tags', exact: true }).click()
  await mobilePage.waitForURL(base + '/tags')
  checks.push('Mobile routes fit viewport and navigation works')
  assert.deepEqual(errors, [], 'No browser exceptions')
  checks.push('No browser runtime exceptions')
  await writeFile(
    screenshots + '/result.json',
    JSON.stringify(
      { checks, topicId, authorId: author.id, base, screenshots },
      null,
      2,
    ),
  )
  console.log(checks.map((c) => 'PASS ' + c).join('\n'))
  await readerContext.close()
  await adminContext.close()
  await mobile.close()
} catch (e) {
  await page.screenshot({ path: screenshots + '/failure.png' })
  console.error('Browser errors:', errors)
  throw e
} finally {
  await browser.close()
}
