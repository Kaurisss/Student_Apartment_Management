import { test, expect } from '@playwright/test'

async function mockDormitories(page) {
  const groups=[{building:'1号楼',rooms:2,beds:8,occupied:1},{building:'2号楼',rooms:1,beds:6,occupied:0}]
  const rows={
    '1号楼':[{id:1,building:'1号楼',room_no:'301',room_type:'4人间',beds:4,occupied:1},{id:2,building:'1号楼',room_no:'302',room_type:'4人间',beds:4,occupied:0}],
    '2号楼':[{id:3,building:'2号楼',room_no:'201',room_type:'6人间',beds:6,occupied:0}]
  }
  const fields=[{name:'building',label:'楼栋',type:'text',max:50,required:true},{name:'room_no',label:'房间号',type:'text',max:20,required:true},{name:'room_type',label:'房间类型',type:'select',required:true,options:{'4人间':'4人间','6人间':'6人间'}}]
  await page.route('**/api/**',async route => {
    const url=new URL(route.request().url()),path=url.pathname
    let body
    if (path==='/api/csrf') body={token:'token',headerName:'X-CSRF-TOKEN'}
    else if (path==='/api/session') body={username:'admin',admin:true}
    else if (path==='/api/admin/dormitories/buildings') body=groups
    else if (path==='/api/admin/dormitories/rooms') {
      const data=rows[url.searchParams.get('building')] || []
      body={rows:data,page:1,pages:1,total:data.length}
    }
    else if (path==='/api/admin/dormitories/form') body={title:'宿舍',fields}
    else return route.fulfill({status:404})
    return route.fulfill({json:body})
  })
}

for (const width of [1440,375,320]) {
  test(`rooms are grouped by building at ${width}px`,async ({page}) => {
    await page.setViewportSize({width,height:900})
    await mockDormitories(page)
    await page.goto('/dormitories')
    const navigation=page.getByRole('navigation',{name:'楼栋列表'})
    await expect(navigation.getByRole('button')).toHaveCount(2)
    await expect(page.getByRole('cell',{name:'301',exact:true})).toBeVisible()
    await navigation.getByRole('button',{name:/2号楼/}).click()
    await expect(page.getByRole('cell',{name:'201',exact:true})).toBeVisible()
    await expect(page.getByRole('cell',{name:'301',exact:true})).toHaveCount(0)
    await page.getByRole('button',{name:'添加房间',exact:true}).click()
    await expect(page.getByLabel('楼栋',{exact:true})).toHaveValue('2号楼')
    expect(await page.evaluate(() => document.documentElement.scrollWidth<=innerWidth)).toBe(true)
  })
}

test('batch room entry previews capacity and submits the selected building',async ({page}) => {
  await mockDormitories(page)
  let submitted
  await page.route('**/api/admin/dormitories/batch',async route => {
    submitted=route.request().postDataJSON()
    await route.fulfill({json:{rooms:3,beds:18}})
  })
  await page.goto('/dormitories')
  await page.getByRole('button',{name:'批量添加房间',exact:true}).click()
  await expect(page.getByLabel('楼栋名称',{exact:true})).toHaveValue('1号楼')
  await page.getByLabel('起始房号',{exact:true}).fill('401')
  await page.getByLabel('结束房号',{exact:true}).fill('403')
  await page.locator('.arco-modal:visible').getByText('6人间',{exact:true}).click()
  await expect(page.getByText('3间房间，18个新床位',{exact:true})).toBeVisible()
  await page.getByRole('button',{name:'添加',exact:true}).click()
  await expect(page.getByText('已添加3间房间、18个床位',{exact:true})).toBeVisible()
  expect(submitted).toEqual({building:'1号楼',startRoom:'401',endRoom:'403',roomType:'6人间',createBeds:true})
})

test('new building entry rejects an existing building without posting',async ({page}) => {
  await mockDormitories(page)
  let posts=0
  page.on('request',request => {if (request.method()==='POST') posts++})
  await page.goto('/dormitories')
  await page.getByRole('button',{name:'新增楼栋',exact:true}).click()
  await page.getByLabel('楼栋名称',{exact:true}).fill('1号楼')
  await page.getByRole('button',{name:'添加',exact:true}).click()
  await expect(page.getByText('楼栋已存在，请在该楼栋内批量添加房间',{exact:true})).toBeVisible()
  expect(posts).toBe(0)
})
