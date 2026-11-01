import { test, expect } from '@playwright/test'

async function mockApi(page, { admin = true, empty = false } = {}) {
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    let body
    if (path === '/api/csrf') body = { token: 'test-token', headerName: 'X-CSRF-TOKEN' }
    else if (path === '/api/session') body = { username: admin ? 'admin' : 'student01', admin }
    else if (path === '/api/admin/statistics') body = {
      beds: { occupied: empty ? 0 : 1, total: empty ? 0 : 2 },
      repairs: { done: 0, total: empty ? 0 : 1 },
      dorms: [{ building: '1号楼', room_no: '301', occupied: empty ? 0 : 1, total: empty ? 0 : 2 }],
      repairStates: empty ? [] : [{ status: 'WAITING', total: 1 }]
    }
    else if (path === '/api/repairs') body = {
      rows: [{ id: 1, student_no: 'S2026001', student_name: '示例学生', description: '宿舍灯具无法正常开启', status: 'WAITING', created_at: '2026-01-01T10:00:00', evaluation: null }],
      page: 1, pages: 1, total: 1
    }
    else if (path === '/api/housing') body = { rows: [], page: 1, pages: 1, total: 0 }
    else return route.fulfill({ status: 404 })
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
  })
}

async function expectNoPageOverflow(page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
}

async function expectHorizontallyScrollable(table) {
  const dimensions = await table.evaluate(el => ({ width: el.clientWidth, content: el.scrollWidth }))
  expect(dimensions.content).toBeGreaterThan(dimensions.width)
  await table.evaluate(el => { el.scrollLeft = 100 })
  expect(await table.evaluate(el => el.scrollLeft)).toBeGreaterThan(0)
}

test('desktop shows the light navigation and dashboard metrics', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  await mockApi(page)
  await page.goto('/')
  await expect(page.getByRole('heading', { name: '数据概览' })).toBeVisible()
  await expect(page.locator('.sider')).toBeVisible()
  await expect(page.getByRole('button', { name: '打开导航' })).toHaveCount(0)
  await expect(page.locator('.metric-value').first()).toHaveText('50.0%')
  await expect(page.locator('.metric-card .ant-progress')).toHaveCount(2)
  await expect(page.locator('.table-rate .ant-progress')).toHaveCount(1)
  await expect(page.locator('.state-tile')).toHaveCount(4)
  expect(await page.locator('.sider').evaluate(el => getComputedStyle(el).backgroundColor)).toBe('rgb(255, 255, 255)')
  await expectNoPageOverflow(page)
})

test('mobile drawer switches pages and tables scroll without squeezing', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 })
  await mockApi(page)
  await page.goto('/')
  const title = page.getByRole('heading', { name: '数据概览' })
  await expect(title).toBeVisible()
  await expect(page.locator('.sider')).toHaveCount(0)
  await expect(page.locator('.ant-layout-sider-zero-width-trigger')).toHaveCount(0)
  const bounds = await title.boundingBox()
  expect(bounds.x).toBeGreaterThanOrEqual(0)
  expect(bounds.x + bounds.width).toBeLessThanOrEqual(375)
  expect(bounds.y).toBeGreaterThanOrEqual(60)
  await expectHorizontallyScrollable(page.locator('.dorm-card .ant-table-content'))
  expect(await page.locator('.dorm-card th').first().evaluate(el => getComputedStyle(el).whiteSpace)).toBe('nowrap')
  await expectNoPageOverflow(page)
  await page.getByRole('button', { name: '打开导航' }).click()
  await expect(page.getByRole('button', { name: '关闭导航' })).toBeVisible()
  await page.getByRole('menuitem', { name: '报修管理' }).click()
  await expect(page.getByRole('heading', { name: '报修管理' })).toBeVisible()
  await expect(page.locator('.ant-drawer-open')).toHaveCount(0)
  await expectHorizontallyScrollable(page.locator('.table-card .ant-table-content'))
  await expectNoPageOverflow(page)
  await page.setViewportSize({ width: 767, height: 900 })
  await expect(page.getByRole('button', { name: '打开导航' })).toBeVisible()
  await page.setViewportSize({ width: 768, height: 900 })
  await expect(page.locator('.sider')).toBeVisible()
  await expect(page.getByRole('button', { name: '打开导航' })).toHaveCount(0)
})

test('student drawer hides admin navigation on a narrow screen', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 720 })
  await mockApi(page, { admin: false })
  await page.goto('/')
  await expect(page.getByRole('heading', { name: '我的报修' })).toBeVisible()
  await page.getByRole('button', { name: '打开导航' }).click()
  await expect(page.getByRole('menuitem', { name: '学生管理' })).toHaveCount(0)
  await expect(page.getByRole('menuitem', { name: '数据概览' })).toHaveCount(0)
  await page.getByRole('menuitem', { name: '入住与退宿' }).click()
  await expect(page.getByRole('heading', { name: '我的入住' })).toBeVisible()
  await expectNoPageOverflow(page)
})

test('empty statistics render zero percentages and four status counts', async ({ page }) => {
  await page.setViewportSize({ width: 768, height: 900 })
  await mockApi(page, { empty: true })
  await page.goto('/')
  await expect(page.locator('.metric-value')).toHaveText(['0.0%', '0.0%'])
  await expect(page.locator('.state-count')).toHaveCount(4)
  await expectNoPageOverflow(page)
})
