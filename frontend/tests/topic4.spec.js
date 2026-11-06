import { test, expect } from '@playwright/test'

test('admin registers a visitor with arrival and departure times', async ({ page }) => {
  let submitted
  const fields = [
    {name:'name',label:'访客姓名',type:'text',max:50,required:true},
    {name:'student_id',label:'被访学生',type:'select',required:true,options:{1:'S001 张同学'}},
    {name:'arrived_at',label:'来访时间',type:'datetime',required:true},
    {name:'left_at',label:'离开时间',type:'datetime',required:false}
  ]
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (route.request().method() === 'POST') {
      submitted = route.request().postDataJSON()
      return route.fulfill({status:204})
    }
    const body = path === '/api/csrf' ? {token:'token',headerName:'X-CSRF-TOKEN'}
      : path === '/api/session' ? {username:'admin',admin:true}
      : path.endsWith('/form') ? {title:'访客',fields}
      : {meta:{title:'访客',fields},data:{rows:[],page:1,pages:1,total:0}}
    return route.fulfill({json:body})
  })
  await page.goto('/visitors')
  await page.getByRole('button', {name:'新增',exact:true}).click()
  await page.getByLabel('访客姓名', {exact:true}).fill('家长')
  await page.getByPlaceholder('请选择', {exact:true}).click()
  await page.getByRole('listitem').filter({hasText:'S001 张同学'}).click()
  await page.getByLabel('来访时间', {exact:true}).fill('2026-11-06T10:00')
  await page.getByLabel('离开时间', {exact:true}).fill('2026-11-06T11:00')
  await page.getByRole('button', {name:'保存',exact:true}).click()
  await expect(page.getByText('保存成功')).toBeVisible()
  expect(submitted).toMatchObject({name:'家长',student_id:'1',arrived_at:'2026-11-06T10:00',left_at:'2026-11-06T11:00'})
})

test('student submits repair with a selected dormitory', async ({ page }) => {
  let submitted
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (route.request().method() === 'POST') {
      submitted = route.request().postDataJSON()
      return route.fulfill({status:204})
    }
    const body = path === '/api/csrf' ? {token:'token',headerName:'X-CSRF-TOKEN'}
      : path === '/api/session' ? {username:'student',admin:false,studentLinked:true}
      : path === '/api/dormitories' ? [{id:1,label:'1号楼 301'}]
      : {rows:[],page:1,pages:1,total:0}
    return route.fulfill({json:body})
  })
  await page.goto('/repairs')
  await page.getByRole('button', {name:'提交报修',exact:true}).click()
  await page.locator('.arco-modal:visible textarea').fill('灯坏了')
  await page.getByPlaceholder('请选择报修宿舍').click()
  await page.getByRole('listitem').filter({hasText:'1号楼 301'}).click()
  await page.getByRole('button', {name:'保存',exact:true}).click()
  await expect(page.getByText('保存成功')).toBeVisible()
  expect(submitted).toMatchObject({description:'灯坏了',dormitory_id:'1'})
})

test('anonymous visitors can register and view public statistics', async ({ page }) => {
  let registration
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/session') return route.fulfill({ status:401 })
    if (path === '/api/register') {
      registration = route.request().postDataJSON()
      return route.fulfill({ status:204 })
    }
    const body = path === '/api/csrf' ? { token:'token', headerName:'X-CSRF-TOKEN' }
      : { beds:{total:4,occupied:1}, repairs:{total:2,done:1}, dorms:[], repairStates:[] }
    return route.fulfill({ json:body })
  })
  await page.setViewportSize({ width:375,height:900 })
  await page.goto('/')
  await page.getByRole('button', { name:'学生注册', exact:true }).click()
  for (const [label,value] of Object.entries({ 用户名:'newstudent', 密码:'123456', 姓名:'新同学', 学号:'S001', 班级:'计应2401', 联系电话:'13800138000' })) {
    await page.getByLabel(label, { exact:true }).fill(value)
  }
  await page.getByRole('button', { name:'注册', exact:true }).click()
  await expect(page.getByText('注册成功，请登录')).toBeVisible()
  expect(registration.class_name).toBe('计应2401')
  expect(registration.role).toBeUndefined()
  await page.getByRole('button', { name:'查看统计' }).click()
  await expect(page.locator('.metric-value')).toHaveText(['25.0%', '50.0%'])
  await page.reload()
  await expect(page.getByRole('heading', { name:'数据概览' })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.getByRole('button', { name:'返回登录' }).click()
  await expect(page.getByRole('heading', { name:'登录', exact:true })).toBeVisible()
})

for (const admin of [true,false]) {
  test(`${admin ? 'admin completes' : 'student evaluates'} a repair with required details`, async ({ page }) => {
    let submitted
    let record = { id:1, student_name:'学生', student_no:'S001', description:'灯坏了', dormitory_id:1, location:'1号楼 301', dorm_label:'1号楼 301', status:admin ? 'IN_PROGRESS' : 'COMPLETED', completion_note:admin ? null : '已更换灯具' }
    await page.route('**/api/**', async route => {
      const path = new URL(route.request().url()).pathname
      if (route.request().method() === 'POST') {
        submitted = route.request().postDataJSON()
        record = { ...record,...submitted,status:admin ? 'COMPLETED' : 'CONFIRMED' }
        return route.fulfill({ status:204 })
      }
      const body = path === '/api/csrf' ? { token:'token',headerName:'X-CSRF-TOKEN' }
        : path === '/api/session' ? { username:'tester',admin,studentLinked:true }
        : { rows:[record],total:1,page:1,pages:1 }
      return route.fulfill({ json:body })
    })
    await page.goto('/repairs')
    await page.getByRole('button', { name:admin ? '标记完成' : '确认评价',exact:true }).click()
    const dialog = page.locator('.arco-modal').filter({ has:page.locator('.arco-modal-title', { hasText:admin ? '完成报修' : '确认并评价' }) })
    await dialog.getByRole('button', { name:admin ? '标记完成' : '确认评价',exact:true }).click()
    await expect(page.getByText(admin ? '请填写完成说明' : '请选择满意或不满意')).toBeVisible()
    if (admin) await dialog.getByLabel('完成说明').fill('已更换灯具')
    else {
      await dialog.getByText('满意', { exact:true }).click()
      await dialog.getByLabel('评语').fill('修好了，谢谢')
    }
    await dialog.getByRole('button', { name:admin ? '标记完成' : '确认评价',exact:true }).click()
    await expect(dialog).toBeHidden()
    expect(admin ? submitted.completion_note : submitted.satisfaction).toBe(admin ? '已更换灯具' : 'SATISFIED')
    await page.getByRole('button', { name:'查看',exact:true }).click()
    await expect(page.locator('.arco-modal:visible').getByText('已更换灯具', { exact:true })).toBeVisible()
  })
}
