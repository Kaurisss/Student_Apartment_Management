import { test, expect } from '@playwright/test'

async function mockApi(page, { admin = true, empty = false, studentLinked = true, housingActive = false, noStudentAccounts = false } = {}) {
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    let body
    const studentMeta = {
      title: '学生', table: 'student', columns: ['id', 'user_id', 'student_no', 'name'],
      fields: [
        { name: 'user_id', label: '学生账号', type: 'select', max: 0, required: true, options: noStudentAccounts ? {} : { 1: 'student01' } },
        { name: 'student_no', label: '学号', type: 'text', max: 30, required: true, options: {} },
        { name: 'name', label: '姓名', type: 'text', max: 50, required: true, options: {} }
      ]
    }
    if (path === '/api/csrf') body = { token: 'test-token', headerName: 'X-CSRF-TOKEN' }
    else if (path === '/api/session') body = { username: admin ? 'admin' : 'student01', admin, studentLinked: admin || studentLinked }
    else if (path === '/api/admin/students/form') body = studentMeta
    else if (path === '/api/admin/students') body = { meta: studentMeta, data: { rows: [], page: 1, pages: 1, total: 0 } }
    else if (path === '/api/statistics' || path === '/api/admin/statistics') body = {
      beds: { occupied: empty ? 0 : 1, total: empty ? 0 : 2 },
      repairs: { done: 0, total: empty ? 0 : 1 },
      dorms: [{ building: '1号楼', room_no: '301', occupied: empty ? 0 : 1, total: empty ? 0 : 2 }],
      repairStates: empty ? [] : [{ status: 'WAITING', total: 1 }]
    }
    else if (path === '/api/my/statistics') body = {
      housing:{status:'CHECKED_IN',bed_label:'1号楼 301 / 01号床',checkout_requested_at:null},
      repairs:{total:3,pending:1,awaiting_evaluation:1},
      repairStates:[{status:'WAITING',total:1},{status:'COMPLETED',total:1},{status:'CONFIRMED',total:1}]
    }
    else if (path === '/api/repairs') body = {
      rows: [{ id: 1, student_no: 'S2026001', student_name: '示例学生', description: '宿舍灯具无法正常开启', status: 'WAITING', created_at: '2026-01-01T10:00:00', evaluation: null }],
      page: 1, pages: 1, total: 1
    }
    else if (path === '/api/housing') body = { rows: [], page: 1, pages: 1, total: 0 }
    else if (path === '/api/beds/free') body = [{ id: 2, label: '1号楼 001 / 01号床' }]
    else if (path === '/api/housing/active') body = { active: housingActive, status: housingActive ? 'PENDING' : null }
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
  await expect(page.locator('.metric-card .arco-progress')).toHaveCount(2)
  await expect(page.locator('.table-rate .arco-progress')).toHaveCount(1)
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
  await expect(page.locator('.arco-layout-sider-trigger')).toHaveCount(0)
  const bounds = await title.boundingBox()
  expect(bounds.x).toBeGreaterThanOrEqual(0)
  expect(bounds.x + bounds.width).toBeLessThanOrEqual(375)
  expect(bounds.y).toBeGreaterThanOrEqual(60)
  await expectHorizontallyScrollable(page.locator('.dorm-card .arco-scrollbar-container'))
  expect(await page.locator('.dorm-card th').first().evaluate(el => getComputedStyle(el).whiteSpace)).toBe('nowrap')
  await expectNoPageOverflow(page)
  await page.getByRole('button', { name: '打开导航' }).click()
  await expect(page.getByRole('button', { name: '关闭导航' })).toBeVisible()
  await page.getByRole('menuitem', { name: '报修管理' }).click()
  await expect(page.getByRole('heading', { name: '报修管理' })).toBeVisible()
  await expect(page.locator('.arco-drawer')).toHaveCount(0)
  await expectHorizontallyScrollable(page.locator('.table-card .arco-scrollbar-container'))
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
  await expect(page.getByRole('menuitem', { name: '数据概览' })).toBeVisible()
  await page.getByRole('menuitem', { name: '入住与退宿' }).click()
  await expect(page.getByRole('heading', { name: '我的入住' })).toBeVisible()
  await expectNoPageOverflow(page)
})

test('student overview shows personal housing and repair progress', async ({ page }) => {
  await page.setViewportSize({width:375,height:812})
  await mockApi(page, {admin:false})
  const requests=[]
  page.on('request',request => { if (request.url().includes('/api/')) requests.push(new URL(request.url()).pathname) })
  await page.goto('/dashboard')
  await expect(page.getByText('我的住宿',{exact:true})).toBeVisible()
  await expect(page.getByText('1号楼 301 / 01号床',{exact:true})).toBeVisible()
  await expect(page.getByText('待评价',{exact:true})).toBeVisible()
  await expect(page.getByText('各宿舍床位',{exact:true})).toHaveCount(0)
  await expect(page.getByText('入住率',{exact:true})).toHaveCount(0)
  expect(requests).toContain('/api/my/statistics')
  expect(requests).not.toContain('/api/admin/statistics')
  expect(requests).not.toContain('/api/statistics')
  await expectNoPageOverflow(page)
  await page.getByRole('button',{name:'查看我的报修'}).click()
  await expect(page.getByRole('heading',{name:'我的报修'})).toBeVisible()
  await page.setViewportSize({width:1440,height:900})
  await page.getByRole('menuitem',{name:'数据概览'}).click()
  await page.route('**/api/logout',route => route.fulfill({status:204}))
  await page.getByRole('button',{name:'退出登录'}).click()
  await expect(page.getByRole('button',{name:'返回登录',exact:true})).toBeVisible()
  await expect(page.locator('.metric-value')).toHaveCount(2)
  await expect(page.getByText('各宿舍床位',{exact:true})).toHaveCount(0)
})

test('student summary cards share edges and action baselines', async ({ page }) => {
  await mockApi(page,{admin:false})
  await page.goto('/dashboard')
  await expect(page.locator('.student-summary')).toHaveCount(2)
  for (const width of [1440,768,375,320]) {
    await page.setViewportSize({width,height:900})
    const cards=await page.locator('.student-summary').evaluateAll(elements => elements.map(el => {
      const card=el.getBoundingClientRect(), action=el.querySelector('.student-summary-action').getBoundingClientRect()
      return {top:card.top,bottom:card.bottom,actionTop:action.top,actionLeft:action.left-card.left}
    }))
    if (width>=768) {
      expect(Math.abs(cards[0].top-cards[1].top)).toBeLessThan(1)
      expect(Math.abs(cards[0].bottom-cards[1].bottom)).toBeLessThan(1)
      expect(Math.abs(cards[0].actionTop-cards[1].actionTop)).toBeLessThan(1)
    }
    expect(Math.abs(cards[0].actionLeft-cards[1].actionLeft)).toBeLessThan(1)
    await expectNoPageOverflow(page)
  }
})

test('polling updates statistics without flicker or overlapping requests and resumes when visible', async ({ page }) => {
  await page.clock.install()
  await mockApi(page,{admin:false})
  let calls=0, delayed
  const body={housing:null,repairs:{total:9,pending:2,awaiting_evaluation:1},repairStates:[]}
  await page.route('**/api/my/statistics',async route => {
    calls++
    if (calls===2) { delayed=route; return }
    await route.fulfill({json:{...body,repairs:{...body.repairs,total:calls===1?0:9}}})
  })
  await page.goto('/dashboard')
  await expect(page.locator('.student-repair-counts strong').last()).toHaveText('0')
  await page.clock.fastForward(30000)
  await expect.poll(() => calls).toBe(2)
  await expect(page.locator('.student-summary')).toHaveCount(2)
  await expect(page.locator('.arco-skeleton')).toHaveCount(0)
  await page.clock.fastForward(30000)
  expect(calls).toBe(2)
  await delayed.fulfill({json:body})
  await expect(page.locator('.student-repair-counts strong')).toHaveText(['2','1','9'])
  await page.evaluate(() => { Object.defineProperty(document,'hidden',{configurable:true,get:()=>true}); document.dispatchEvent(new Event('visibilitychange')) })
  await page.clock.fastForward(30000)
  expect(calls).toBe(2)
  await page.evaluate(() => { Object.defineProperty(document,'hidden',{configurable:true,get:()=>false}); document.dispatchEvent(new Event('visibilitychange')) })
  await expect.poll(() => calls).toBe(3)
})

test('automatic refresh pauses while a repair form is open', async ({ page }) => {
  await page.clock.install()
  await mockApi(page,{admin:false})
  await page.route('**/api/dormitories',route => route.fulfill({json:[{id:1,label:'1号楼 301'}]}))
  let calls=0
  page.on('request',request => { if (new URL(request.url()).pathname==='/api/repairs') calls++ })
  await page.goto('/repairs')
  await page.getByRole('button',{name:'提交报修',exact:true}).click()
  await page.locator('.arco-modal:visible textarea').fill('尚未提交的报修内容')
  const before=calls
  await page.clock.fastForward(30000)
  expect(calls).toBe(before)
  await expect(page.locator('.arco-modal:visible textarea')).toHaveValue('尚未提交的报修内容')
  await page.getByRole('button',{name:'取消',exact:true}).click()
  await page.clock.fastForward(30000)
  await expect.poll(() => calls).toBeGreaterThan(before)
})

test('empty statistics render zero percentages and four status counts', async ({ page }) => {
  await page.setViewportSize({ width: 768, height: 900 })
  await mockApi(page, { empty: true })
  await page.goto('/')
  await expect(page.locator('.metric-value')).toHaveText(['0.0%', '0.0%'])
  await expect(page.locator('.state-count')).toHaveCount(4)
  await expectNoPageOverflow(page)
})

test('student without a linked record sees the blocking guidance', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 })
  await mockApi(page, { admin: false, studentLinked: false })
  await page.goto('/')
  await expect(page.getByRole('heading', { name: '账号尚未关联学生信息' })).toBeVisible()
  await expect(page.getByRole('button', { name: '退出登录' })).toBeVisible()
  await expect(page.locator('.sider')).toHaveCount(0)
  await expectNoPageOverflow(page)
})

test('student with an active housing record cannot submit another application', async ({ page }) => {
  await page.setViewportSize({ width: 768, height: 900 })
  await mockApi(page, { admin: false, housingActive: true })
  await page.goto('/housing')
  await expect(page.getByRole('heading', { name: '我的入住' })).toBeVisible()
  await expect(page.getByRole('button', { name: '入住申请' })).toBeDisabled()
  await expect(page.getByText('待审核', { exact: true })).toBeVisible()
  await expectNoPageOverflow(page)
})

test('admin only reviews repairs and housing without submission buttons', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  await mockApi(page)
  await page.goto('/repairs')
  await expect(page.getByRole('heading', { name: '报修管理' })).toBeVisible()
  await expect(page.getByRole('button', { name: '提交报修' })).toHaveCount(0)
  await page.goto('/housing')
  await expect(page.getByRole('heading', { name: '入住与退宿' })).toBeVisible()
  await expect(page.getByRole('button', { name: '入住申请' })).toHaveCount(0)
})

test('student keeps repair and housing submission buttons', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  await mockApi(page, { admin: false })
  await page.goto('/repairs')
  await expect(page.getByRole('heading', { name: '我的报修' })).toBeVisible()
  await expect(page.getByRole('button', { name: '提交报修' })).toBeVisible()
  await page.goto('/housing')
  await expect(page.getByRole('heading', { name: '我的入住' })).toBeVisible()
  await expect(page.getByRole('button', { name: '入住申请' })).toBeVisible()
})

test('student chooses a free bed when applying for housing', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  await mockApi(page, { admin: false })
  let posted = ''
  page.on('request', request => {
    if (request.url().endsWith('/api/housing') && request.method() === 'POST') posted = request.postData() || ''
  })
  await page.goto('/housing')
  await page.getByRole('button', { name: '入住申请' }).click()
  await expect(page.getByText('提交入住申请')).toBeVisible()
  await page.getByPlaceholder('请选择空闲床位').click()
  await page.getByRole('listitem').filter({ hasText: '1号楼 001 / 01号床' }).click()
  await page.getByRole('button', { name: '保存' }).click()
  await expect(page.getByText('保存成功')).toBeVisible()
  await expect(page.getByText('提交入住申请')).toBeHidden()
  expect(posted).toContain('"bedId":"2"')
})

test('student form guides to account management when no account is available', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  await mockApi(page, { noStudentAccounts: true })
  await page.goto('/students')
  await expect(page.getByRole('heading', { name: '学生管理' })).toBeVisible()
  await page.getByRole('button', { name: '新增' }).click()
  await expect(page.getByText('暂无可用的学生账号，请先在「账号管理」创建 STUDENT 账号。')).toBeVisible()
  await expect(page.getByRole('button', { name: '前往账号管理' })).toBeVisible()
  await expect(page.getByRole('button', { name: '保存' })).toBeDisabled()
})
