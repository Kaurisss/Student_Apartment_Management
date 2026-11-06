<script setup>
import { ref, reactive, computed, provide, watch, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Message, Modal } from '@arco-design/web-vue'
import zhCN from '@arco-design/web-vue/es/locale/lang/zh-cn'
import { PhArrowLeft, PhList } from '@phosphor-icons/vue'
import AppNavigation from './components/AppNavigation.vue'

const route = useRoute()
const router = useRouter()

const isMobile = ref(false)
const drawerOpen = ref(false)
let mobileQuery
function syncMobile() {
  isMobile.value = mobileQuery.matches
  if (!isMobile.value) drawerOpen.value = false
}
const user = ref(null), busy = ref(false), saving = ref(false)
const current = computed(() => route.name || 'home')
const csrf = ref({}), login = reactive({ username: '', password: '' })
const registering = ref(false), registration = reactive({ username:'', password:'', name:'', student_no:'', class_name:'', phone:'' })
const dormitories = ref([]), satisfaction = ref('')
const buildings = ref([]), selectedBuilding = ref(''), showRoomBatch = ref(false)
const showComplete = ref(false), completeId = ref(null), completionNote = ref('')
const repairFilter = reactive({ keyword: '', dorm: '', status: '' })
let routeReady = false
let refreshTimer, refreshing = false, loadVersion = 0
const page = ref(1), result = ref({ rows: [], total: 0 }), meta = ref({ title: '', fields: [] }), stats = ref(null)
const beds = ref([]), activeHousing = ref(null)
const showEditor = ref(false), editId = ref(null), form = reactive({}), formRef = ref(null)
const editorMeta = ref({ title: '', fields: [] })
const showEvaluate = ref(false), evaluateId = ref(null), evaluation = ref('')
const showAssign = ref(false), assignId = ref(null), selectedBed = ref(undefined)
const adminKeys = ['students', 'dormitories', 'beds', 'users', 'visitors']
const profile = reactive({ username:'', student_no:'', name:'', class_name:'', phone:'', password:'', oldPassword:'' })
const labels = { statistics:'数据概览', profile:'个人信息', repairs:'报修管理', housing:'入住与退宿', students:'学生管理', dormitories:'宿舍管理', beds:'床位管理', users:'账号管理', visitors:'访客登记' }
const statusLabels = { WAITING:'待受理', IN_PROGRESS:'处理中', COMPLETED:'已完成', CONFIRMED:'已评价', PENDING:'待审核', ASSIGNED:'已分配', CHECKED_IN:'已入住', CHECKED_OUT:'已退宿', FREE:'空闲', OCCUPIED:'已占用' }
const admin = computed(() => !!user.value?.admin)
const title = computed(() => current.value === 'repairs' && !admin.value ? '我的报修' : current.value === 'housing' && !admin.value ? '我的入住' : labels[current.value] || '')
const pagination = computed(() => ({ current:page.value, pageSize:10, total:result.value.total, showTotal:true, showPageSize:false }))
const bedOptions = computed(() => beds.value.map(b => ({ value:String(b.id), label:b.label })))
const editingSelf = computed(() => current.value === 'users' && !!editId.value && form.username === user.value?.username)
const noStudentAccounts = computed(() => current.value === 'students' && !editId.value && (editorMeta.value.fields || []).some(f => f.name === 'user_id' && Object.keys(f.options || {}).length === 0))
const editorTitle = computed(() => (editId.value ? '编辑' : current.value === 'repairs' || current.value === 'housing' ? '提交' : '新增') + (current.value === 'dormitories' ? '房间' : adminKeys.includes(current.value) ? editorMeta.value.title : current.value === 'repairs' ? '报修' : '入住申请'))
const dormRows = computed(() => (stats.value?.dorms || []).map(item => ({ ...item, key: item.building + item.room_no })))
const rules = computed(() => {
  const r = {}
  if (adminKeys.includes(current.value)) for (const f of editorMeta.value.fields || []) {
    const required = f.required || (f.name === 'password' && !editId.value)
    r[f.name] = [{ required, message:`请填写${f.label}` }]
    if (f.name === 'password' && (!editId.value || form.password)) r[f.name].push({ minLength:6, maxLength:100, message:'密码长度须为6至100个字符' })
  }
  if (editingSelf.value && form.password) r.oldPassword = [{ required:true, message:'请填写原密码' }]
  if (current.value === 'repairs') {
    r.description = [{ validator:(value, callback) => { if (!String(value ?? '').trim()) callback('请输入故障描述') } }]
    r.dormitory_id = [{ required:true, message:'请选择报修宿舍' }]
  }
  if (current.value === 'housing') r.bedId = [{ required:true, message:'请选择空闲床位' }]
  return r
})
const columns = computed(() => {
  const cell = column => ({ ...column, slotName:'cell' })
  if (adminKeys.includes(current.value)) {
    const fields = (meta.value.fields || []).filter(f => f.name !== 'password').map(f => cell({ title:f.label, key:f.name, dataIndex:f.name }))
    return [cell({ title:'编号', dataIndex:'id', key:'id' }), ...fields, ...(current.value === 'beds' ? [cell({ title:'状态', dataIndex:'status', key:'status' })] : []), cell({ title:'操作', key:'actions', width:112 })]
  }
  if (current.value === 'repairs') return [
    cell({ title:'编号', dataIndex:'id', key:'id' }), ...(admin.value ? [cell({ title:'学生', key:'student' })] : []),
    cell({ title:'所在宿舍', dataIndex:'dorm_label', key:'dorm', width:120 }),
    cell({ title:'故障描述', dataIndex:'description', key:'description', width:250 }), cell({ title:'提交时间', dataIndex:'created_at', key:'created_at' }),
    cell({ title:'状态', dataIndex:'status', key:'status' }), cell({ title:'评价', dataIndex:'evaluation', key:'evaluation', width:200 }), cell({ title:'操作', key:'actions', width:184 })
  ]
  return [cell({ title:'编号', dataIndex:'id', key:'id' }), ...(admin.value ? [cell({ title:'学生', key:'student' })] : []),
    cell({ title:'床位', dataIndex:'bed_label', key:'bed_label' }), cell({ title:'状态', dataIndex:'status', key:'status' }),
    cell({ title:'入住时间', dataIndex:'check_in_at', key:'check_in_at' }), cell({ title:'退宿申请', dataIndex:'checkout_requested_at', key:'checkout_requested_at' }),
    cell({ title:'退宿时间', dataIndex:'check_out_at', key:'check_out_at' }), cell({ title:'操作', key:'actions', width:144 })]
})
const dormColumns = [
  { title:'楼栋', dataIndex:'building' }, { title:'宿舍', dataIndex:'room_no', width:88 },
  { title:'已占用', dataIndex:'occupied', width:88 }, { title:'床位总数', dataIndex:'total', width:96 },
  { title:'占用率', dataIndex:'rate', slotName:'rate', width:160 }
]
const dormPagination = { pageSize:10, showPageSize:false, showTotal:true }
const repairStates = computed(() => ['WAITING', 'IN_PROGRESS', 'COMPLETED', 'CONFIRMED'].map(status => ({
  status, total: stats.value?.repairStates?.find(item => item.status === status)?.total ?? 0
})))
function percentage(n, total) { return total ? Math.min(100, Math.max(0, Number((Number(n) * 100 / Number(total)).toFixed(1)))) : 0 }
function rate(n, total) { return `${percentage(n, total).toFixed(1)}%` }
function date(value) { return value ? String(value).replace('T', ' ').slice(0, 16) : '—' }
function filterOption(input, option) { return String(option.label).toLowerCase().includes(input.toLowerCase()) }
function tagColor(status) { return ['CONFIRMED', 'CHECKED_IN', 'FREE'].includes(status) ? 'green' : ['WAITING', 'PENDING'].includes(status) ? 'orangered' : 'gray' }
async function token() { const r = await fetch('/api/csrf', { credentials:'same-origin' }); if (!r.ok) throw Error('无法获取安全令牌'); csrf.value = await r.json() }
async function request(url, { method = 'GET', data, formData = false } = {}) {
  const headers = {}
  if (method !== 'GET') headers[csrf.value.headerName || 'X-CSRF-TOKEN'] = csrf.value.token || ''
  if (data !== undefined) headers['Content-Type'] = formData ? 'application/x-www-form-urlencoded' : 'application/json'
  const response = await fetch('/api' + url, { method, headers, credentials:'same-origin', body:data === undefined ? undefined : formData ? new URLSearchParams(data) : JSON.stringify(data) })
  if (!response.ok) {
    let error; try { error = await response.json() } catch { /* 403 等空响应 */ }
    if (response.status === 401 && user.value) { stats.value = null; user.value = null; activeHousing.value = null; token().catch(() => {}) }
    throw new Error(error?.message || (response.status === 401 ? '请先登录' : response.status === 403 ? '没有操作权限' : '请求失败，请重试'))
  }
  if (response.status === 204 || response.headers.get('content-length') === '0') return null
  const body = await response.text(); return body ? JSON.parse(body) : null
}
function homeName() { return admin.value ? 'statistics' : 'repairs' }
function routeAllowed(name) {
  if (!labels[name]) return false
  if (name === 'profile') return !admin.value
  return admin.value || !adminKeys.includes(name)
}
function blocked() { return !!user.value && !user.value.admin && user.value.studentLinked === false }
async function initialize() {
  try {
    await token(); user.value = await request('/session')
    if (blocked()) { routeReady = true; return }
    if (!routeAllowed(route.name)) await router.replace({ name: homeName() })
    routeReady = true; await load()
  } catch {
    user.value = null
    routeReady = true
    if (route.name === 'statistics') await load()
  }
}
async function register({ errors }) {
  if (errors) return
  saving.value = true
  try {
    await request('/register', { method:'POST', data:registration })
    login.username = registration.username; login.password = ''
    for (const key of Object.keys(registration)) registration[key] = ''
    registering.value = false; Message.success('注册成功，请登录')
  } catch (e) { Message.error(e.message) } finally { saving.value = false }
}
async function showPublicStatistics() { await router.push({ name:'statistics' }); await load() }
function submitLogin({ errors }) { if (!errors) doLogin() }
async function doLogin() {
  saving.value = true
  try {
    await request('/login', { method:'POST', data:login, formData:true }); await token(); user.value = await request('/session')
    page.value = 1; login.password = ''; routeReady = true
    if (blocked()) return
    if (route.name !== homeName()) await router.replace({ name: homeName() })
    else await load()
  }
  catch (e) { Message.error(e.message); await token() } finally { saving.value = false }
}
async function logout() {
  try {
    await request('/logout', { method:'POST' })
    drawerOpen.value = false; stats.value = null; user.value = null; activeHousing.value = null
    await token()
    if (current.value === 'statistics') await load()
  } catch (e) { Message.error(e.message) }
}
function refreshCurrent() {
  if (!routeReady || document.hidden || busy.value || refreshing || saving.value || blocked() || current.value === 'profile'
    || showEditor.value || showEvaluate.value || showComplete.value || showAssign.value || showRoomBatch.value
    || (!user.value && current.value !== 'statistics')) return
  load({ background:true })
}
async function load({ background = false } = {}) {
  const version = ++loadVersion, view = current.value, session = user.value
  const isCurrent = () => version === loadVersion && view === current.value && session === user.value
  if (background) refreshing = true
  else busy.value = true
  try {
    if (!labels[view]) return
    if (view === 'statistics') {
      const data = await request(!session ? '/statistics' : admin.value ? '/admin/statistics' : '/my/statistics')
      if (isCurrent()) stats.value = data
      return
    }
    if (view === 'profile') {
      const data = await request('/profile')
      if (!isCurrent()) return
      profile.username = data.username; profile.student_no = data.student_no; profile.name = data.name; profile.password = ''; profile.oldPassword = ''
      profile.class_name = data.class_name; profile.phone = data.phone
      return
    }
    if (view === 'repairs') {
      const query = new URLSearchParams({ page: page.value })
      if (repairFilter.keyword.trim()) query.set('keyword', repairFilter.keyword.trim())
      if (repairFilter.dorm.trim()) query.set('dorm', repairFilter.dorm.trim())
      if (repairFilter.status) query.set('status', repairFilter.status)
      const data = await request('/repairs?' + query)
      if (isCurrent()) { result.value = data; page.value = data.page }
      return
    }
    if (view === 'dormitories') {
      const groups = await request('/admin/dormitories/buildings')
      if (!isCurrent()) return
      buildings.value = groups
      if (!groups.some(group => group.building === selectedBuilding.value)) selectedBuilding.value = groups[0]?.building || ''
      const building = selectedBuilding.value
      if (!building) { result.value = {rows:[],total:0}; page.value = 1; return }
      const data = await request('/admin/dormitories/rooms?' + new URLSearchParams({building,page:page.value}))
      if (isCurrent() && building === selectedBuilding.value) { result.value = data; page.value = data.page }
      return
    }
    const resource = adminKeys.includes(view) ? '/admin/' + view : '/' + view
    const data = await request(resource + '?page=' + page.value)
    if (!isCurrent()) return
    if (adminKeys.includes(view)) { meta.value = data.meta; result.value = data.data } else result.value = data
    page.value = result.value.page
    if (view === 'housing' && !admin.value) {
      const active = await request('/housing/active')
      if (isCurrent()) activeHousing.value = active
    }
  } catch (e) { if (!background && isCurrent()) Message.error(e.message) }
  finally {
    if (background) refreshing = false
    else if (version === loadVersion) busy.value = false
  }
}
async function saveProfile({ errors }) {
  if (errors) return
  if (profile.password && (profile.password.length < 6 || profile.password.length > 100)) return Message.warning('密码长度须为6至100个字符')
  if (profile.password && !profile.oldPassword) return Message.warning('修改密码请填写原密码')
  saving.value = true
  try {
    const changing = !!profile.password
    await request('/profile', { method:'PUT', data:{ name:profile.name, class_name:profile.class_name, phone:profile.phone, password:profile.password, oldPassword:profile.oldPassword } })
    profile.password = ''; profile.oldPassword = ''
    Message.success(changing ? '密码已修改，其他设备已退出登录' : '保存成功')
  } catch (e) { Message.error(e.message) } finally { saving.value = false }
}
async function navigate(key) {
  drawerOpen.value = false
  if (route.name !== key) await router.push({ name: key })
}
function changePage(next) { page.value = next; load() }
function applyFilters() { page.value = 1; load() }
function selectBuilding(building) { selectedBuilding.value = building; page.value = 1; return load() }
function resetForm() { for (const key of Object.keys(form)) delete form[key]; editId.value = null }
async function openEditor(record = null) {
  resetForm(); editId.value = record?.id || null
  try {
    if (adminKeys.includes(current.value)) {
      editorMeta.value = await request('/admin/' + current.value + '/form' + (record ? '?editingId=' + record.id : ''))
      if (record) Object.assign(form, await request('/admin/' + current.value + '/' + record.id))
      if (current.value === 'dormitories' && !record) { form.room_type = '4人间'; form.building = selectedBuilding.value }
      for (const f of editorMeta.value.fields) if (f.type === 'datetime' && form[f.name]) form[f.name] = String(form[f.name]).replace(' ', 'T').slice(0,19)
      for (const f of editorMeta.value.fields) if (form[f.name] != null) form[f.name] = String(form[f.name])
    } else if (current.value === 'repairs') {
      dormitories.value = await request('/dormitories')
      if (record) Object.assign(form, await request('/repairs/' + record.id))
      if (form.dormitory_id != null) form.dormitory_id = String(form.dormitory_id)
    } else if (current.value === 'housing') {
      beds.value = await request('/beds/free')
    }
    showEditor.value = true
  } catch (e) { Message.error(e.message) }
}
async function saveEditor() {
  const errors = await formRef.value?.validate()
  if (errors) return false
  saving.value = true
  try {
    if (adminKeys.includes(current.value)) {
      const body = {}; for (const f of editorMeta.value.fields) body[f.name] = String(form[f.name] ?? '')
      if (editingSelf.value && body.password) body.oldPassword = String(form.oldPassword ?? '')
      await request('/admin/' + current.value + (editId.value ? '/' + editId.value : ''), { method:editId.value ? 'PUT' : 'POST', data:body })
      if (current.value === 'dormitories') selectedBuilding.value = body.building.trim()
    } else if (current.value === 'repairs') {
      await request('/repairs' + (editId.value ? '/' + editId.value : ''), { method:editId.value ? 'PUT' : 'POST', data:{ description:form.description, dormitory_id:form.dormitory_id } })
    } else await request('/housing', { method:'POST', data:{ bedId:form.bedId } })
    Message.success('保存成功'); await load(); return true
  } catch (e) { Message.error(e.message); return false } finally { saving.value = false }
}
function remove(record) {
  Modal.confirm({ title:'确定删除这条记录吗？', content:'', okText:'删除', cancelText:'取消', okButtonProps:{ status:'danger' }, async onOk() {
    try { const resource = adminKeys.includes(current.value) ? '/admin/' + current.value : '/' + current.value; await request(resource + '/' + record.id, { method:'DELETE' }); Message.success('删除成功'); await load() } catch (e) { Message.error(e.message) }
  } })
}
async function action(type, id, name, data = {}) {
  try { await request('/' + type + '/' + id + '/' + name, { method:'POST', data }); Message.success('操作成功'); await load(); return true } catch (e) { Message.error(e.message); return false }
}
function confirmAction(type, id, name, title, content) {
  Modal.confirm({ title, content, okText:'确认', cancelText:'取消', okButtonProps:{ status:'danger' }, async onOk() { await action(type, id, name) } })
}
function goCreateAccount() { showEditor.value = false; router.push({ name:'users' }) }
function openEvaluation(id) { evaluateId.value = id; evaluation.value = ''; satisfaction.value = ''; showEvaluate.value = true }
function openComplete(id) { completeId.value = id; completionNote.value = ''; showComplete.value = true }
async function submitComplete() {
  if (!completionNote.value.trim()) { Message.warning('请填写完成说明'); return false }
  saving.value = true
  try { return await action('repairs', completeId.value, 'complete', { completion_note:completionNote.value }) }
  finally { saving.value = false }
}
async function submitEvaluation() {
  if (!satisfaction.value) { Message.warning('请选择满意或不满意'); return false }
  if (!evaluation.value.trim()) { Message.warning('请填写评价'); return false }
  saving.value = true
  try { if (await action('repairs', evaluateId.value, 'confirm', { evaluation:evaluation.value, satisfaction:satisfaction.value })) return true; return false }
  finally { saving.value = false }
}
async function openAssign(record) {
  try {
    beds.value = await request('/admin/options/free-beds')
    selectedBed.value = beds.value.some(b => Number(b.id) === Number(record.bed_id)) ? String(record.bed_id) : undefined
    assignId.value = record.id; showAssign.value = true
  } catch (e) { Message.error(e.message) }
}
async function submitAssign() {
  if (!selectedBed.value) { Message.warning('请选择空床位'); return false }
  saving.value = true
  try { if (await action('housing', assignId.value, 'assign', { bedId:selectedBed.value })) return true; return false }
  finally { saving.value = false }
}
watch(() => route.name, (name, previous) => {
  if (!routeReady || !previous || name === previous) return
  page.value = 1
  load()
})
provide('desk', reactive({
  title, current, admin, signedIn:computed(() => !!user.value), adminKeys, stats, columns, result, pagination, busy, saving, profile, activeHousing,
  dormColumns, dormRows, dormPagination, repairStates, statusLabels, repairFilter, buildings, selectedBuilding, showRoomBatch,
  request, load, selectBuilding,
  openEditor, navigate, changePage, applyFilters, saveProfile, rate, percentage, date, tagColor, remove, action, confirmAction, openEvaluation, openComplete, openAssign
}))
onMounted(() => {
  mobileQuery = window.matchMedia('(max-width: 767px)')
  syncMobile()
  mobileQuery.addEventListener('change', syncMobile)
  refreshTimer = window.setInterval(refreshCurrent, 30000)
  document.addEventListener('visibilitychange', refreshCurrent)
  window.addEventListener('focus', refreshCurrent)
  initialize()
})
onUnmounted(() => {
  mobileQuery?.removeEventListener('change', syncMobile)
  window.clearInterval(refreshTimer)
  document.removeEventListener('visibilitychange', refreshCurrent)
  window.removeEventListener('focus', refreshCurrent)
})
</script>

<template>
  <a-config-provider :locale="zhCN">
    <main v-if="!user && current === 'statistics'" class="content public-statistics">
      <a-button @click="router.push('/')"><template #icon><PhArrowLeft :size="16" aria-hidden="true" /></template>返回登录</a-button>
      <router-view />
    </main>
    <div v-else-if="!user" class="login-wrap">
      <section class="login-aside">
        <div class="login-aside-inner">
          <h1>学生公寓管理系统</h1>
          <div class="login-rule" aria-hidden="true"></div>
          <p class="login-aside-copy">床位分配、报修处理、入住与退宿。</p>
        </div>
      </section>
      <main class="login-main">
        <div class="login-form">
          <h2>{{ registering ? '学生注册' : '登录' }}</h2>
          <a-form v-if="registering" :model="registration" layout="vertical" @submit="register">
            <a-form-item v-for="field in [{key:'username',label:'用户名',max:50},{key:'password',label:'密码',max:100},{key:'name',label:'姓名',max:50},{key:'student_no',label:'学号',max:30},{key:'class_name',label:'班级',max:50},{key:'phone',label:'联系电话',max:30}]" :key="field.key" :field="field.key" :label="field.label" :rules="[{required:true,message:'请填写' + field.label}]">
              <a-input-password v-if="field.key === 'password'" v-model="registration.password" :input-attrs="{'aria-label':field.label,autocomplete:'new-password'}" :max-length="field.max" />
              <a-input v-else v-model="registration[field.key]" :input-attrs="{'aria-label':field.label}" :max-length="field.max" />
            </a-form-item>
            <a-button type="primary" html-type="submit" long :loading="saving">注册</a-button>
          </a-form>
          <a-form v-else :model="login" layout="vertical" @submit="submitLogin">
            <a-form-item field="username" label="用户名" :rules="[{ required:true, message:'请输入用户名' }]">
              <a-input v-model="login.username" size="large" :max-length="50" autocomplete="username" />
            </a-form-item>
            <a-form-item field="password" label="密码" :rules="[{ required:true, message:'请输入密码' }]">
              <a-input-password v-model="login.password" size="large" :max-length="100" autocomplete="current-password" />
            </a-form-item>
            <a-button type="primary" size="large" html-type="submit" long :loading="saving">登录</a-button>
          </a-form>
          <div class="auth-actions">
            <a-button type="text" @click="registering = !registering">{{ registering ? '返回登录' : '学生注册' }}</a-button>
            <a-button type="text" @click="showPublicStatistics">查看统计</a-button>
          </div>
        </div>
      </main>
    </div>
    <div v-else-if="blocked()" class="blocked-wrap">
      <a-card class="blocked-card" :bordered="false">
        <h2>账号尚未关联学生信息</h2>
        <p>请联系管理员在「学生管理」中为本账号建立学生档案后再使用。</p>
        <a-button type="primary" @click="logout">退出登录</a-button>
      </a-card>
    </div>
    <a-layout v-else class="app-layout">
      <a-layout-sider v-if="!isMobile" class="sider" :width="228">
        <AppNavigation :user="user" :current="current" @navigate="navigate" @logout="logout" />
      </a-layout-sider>
      <a-layout class="main-layout">
        <a-layout-header v-if="isMobile" class="mobile-header">
          <a-button type="text" class="menu-toggle" aria-label="打开导航" @click="drawerOpen = true">
            <template #icon><PhList :size="22" /></template>
          </a-button>
          <span class="mobile-brand">学生公寓</span>
          <a-avatar class="user-avatar" :size="32" :image-url="`https://api.dicebear.com/9.x/initials/svg?seed=${encodeURIComponent(user.username)}&backgroundColor=f2f3f5&textColor=1d2129`" />
        </a-layout-header>
        <a-drawer v-if="isMobile && drawerOpen" :visible="true" placement="left" :width="280" :header="false" :footer="false"
          :body-style="{ padding: 0, height: '100%' }" @cancel="drawerOpen = false">
          <AppNavigation :user="user" :current="current" drawer @navigate="navigate" @logout="logout" @close="drawerOpen = false" />
        </a-drawer>
        <a-layout-content class="content">
          <router-view />
        </a-layout-content>
      </a-layout>
    </a-layout>
    <a-modal v-model:visible="showEditor" :title="editorTitle" ok-text="保存" cancel-text="取消" :ok-loading="saving" :ok-button-props="{ disabled: noStudentAccounts }" unmount-on-close :on-before-ok="saveEditor">
      <div v-if="noStudentAccounts" class="form-guide">
        <span>暂无可用的学生账号，请先在「账号管理」创建 STUDENT 账号。</span>
        <a-button size="mini" @click="goCreateAccount">前往账号管理</a-button>
      </div>
      <a-form ref="formRef" :model="form" :rules="rules" layout="vertical" class="modal-form">
        <template v-if="adminKeys.includes(current)">
          <a-form-item v-for="field in editorMeta.fields" :key="field.name" :field="field.name" :label="field.label">
            <a-select v-if="field.type === 'select'" v-model="form[field.name]" placeholder="请选择" :options="Object.entries(field.options).map(([value, label]) => ({ value, label }))" allow-search :filter-option="filterOption" />
            <a-input-password v-else-if="field.type === 'password'" v-model="form.password" :max-length="field.max" :placeholder="editId ? '留空则不修改密码' : ''" />
            <a-input v-else-if="field.type === 'datetime'" v-model="form[field.name]" type="datetime-local" :input-attrs="{'aria-label':field.label}" />
            <a-input v-else v-model="form[field.name]" :max-length="field.max" :input-attrs="{'aria-label':field.label}" />
          </a-form-item>
          <a-form-item v-if="editingSelf && form.password" field="oldPassword" label="原密码">
            <a-input-password v-model="form.oldPassword" :max-length="100" placeholder="修改本人密码需验证原密码" />
          </a-form-item>
        </template>
        <template v-else>
          <a-form-item v-if="current === 'housing'" field="bedId" label="床位">
            <a-select v-model="form.bedId" placeholder="请选择空闲床位" :options="bedOptions" allow-search :filter-option="filterOption" />
          </a-form-item>
          <a-form-item v-if="current === 'repairs'" field="description" label="故障描述">
            <a-textarea v-model="form.description" :auto-size="{ minRows:4, maxRows:8 }" :max-length="500" show-word-limit />
          </a-form-item>
          <a-form-item v-if="current === 'repairs'" field="dormitory_id" label="报修宿舍">
            <a-select v-model="form.dormitory_id" :options="dormitories.map(d => ({value:String(d.id),label:d.label}))" placeholder="请选择报修宿舍" allow-search :filter-option="filterOption" />
          </a-form-item>
        </template>
      </a-form>
    </a-modal>
    <a-modal v-model:visible="showEvaluate" title="确认并评价" ok-text="确认评价" cancel-text="取消" :ok-loading="saving" :on-before-ok="submitEvaluation">
      <a-form layout="vertical">
        <a-form-item label="满意度"><a-radio-group v-model="satisfaction" :options="[{value:'SATISFIED',label:'满意'},{value:'DISSATISFIED',label:'不满意'}]" /></a-form-item>
        <a-form-item label="评语"><a-textarea v-model="evaluation" :textarea-attrs="{'aria-label':'评语'}" :auto-size="{ minRows:4, maxRows:8 }" :max-length="500" show-word-limit placeholder="填写评价" /></a-form-item>
      </a-form>
    </a-modal>
    <a-modal v-model:visible="showComplete" title="完成报修" ok-text="标记完成" cancel-text="取消" :ok-loading="saving" :on-before-ok="submitComplete">
      <a-form layout="vertical"><a-form-item label="完成说明"><a-textarea v-model="completionNote" :textarea-attrs="{'aria-label':'完成说明'}" :max-length="500" show-word-limit :auto-size="{minRows:4,maxRows:8}" /></a-form-item></a-form>
    </a-modal>
    <a-modal v-model:visible="showAssign" title="分配床位" ok-text="分配" cancel-text="取消" :ok-loading="saving" :on-before-ok="submitAssign">
      <a-select v-model="selectedBed" placeholder="请选择空床位" :options="bedOptions" />
    </a-modal>
  </a-config-provider>
</template>
