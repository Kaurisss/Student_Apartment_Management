<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { PhBuildings, PhChartBar, PhWrench, PhDoorOpen, PhStudent, PhHouseLine, PhBed, PhUsers, PhSignOut, PhPlus } from '@phosphor-icons/vue'

const user = ref(null), current = ref('statistics'), busy = ref(false), saving = ref(false)
const csrf = ref({}), login = reactive({ username: '', password: '' })
const page = ref(1), result = ref({ rows: [], total: 0 }), meta = ref({ title: '', fields: [] }), stats = ref(null)
const students = ref([]), beds = ref([])
const showEditor = ref(false), editId = ref(null), form = reactive({}), formRef = ref(null)
const showEvaluate = ref(false), evaluateId = ref(null), evaluation = ref('')
const showAssign = ref(false), assignId = ref(null), selectedBed = ref(undefined)
const adminKeys = ['students', 'dormitories', 'beds', 'users']
const labels = { statistics:'数据概览', repairs:'报修管理', housing:'入住与退宿', students:'学生管理', dormitories:'宿舍管理', beds:'床位管理', users:'账号管理' }
const statusLabels = { WAITING:'待受理', IN_PROGRESS:'处理中', COMPLETED:'已完成', CONFIRMED:'已确认', PENDING:'待审核', ASSIGNED:'已分配', CHECKED_IN:'已入住', CHECKED_OUT:'已退宿', FREE:'空闲', OCCUPIED:'已占用' }
const admin = computed(() => !!user.value?.admin)
const title = computed(() => current.value === 'repairs' && !admin.value ? '我的报修' : current.value === 'housing' && !admin.value ? '我的入住' : labels[current.value])
const pagination = computed(() => ({ current:page.value, pageSize:10, total:result.value.total, showSizeChanger:false, showTotal: total => `共 ${total} 条` }))
const studentOptions = computed(() => students.value.map(s => ({ value:String(s.id), label:s.label })))
const bedOptions = computed(() => beds.value.map(b => ({ value:String(b.id), label:b.label })))
const rules = computed(() => {
  const r = {}
  if (adminKeys.includes(current.value)) for (const f of meta.value.fields || []) {
    const required = f.required || (f.name === 'password' && !editId.value)
    r[f.name] = [{ required, message:`请填写${f.label}`, trigger: f.type === 'select' ? 'change' : 'blur' }]
    if(f.name === 'password') r[f.name].push({ min:6, max:100, message:'密码长度须为6至100个字符', trigger:'blur' })
  }
  if(current.value === 'repairs') {
    r.description = [{ required:true, whitespace:true, message:'请输入故障描述', trigger:'blur' }]
    if(admin.value && !editId.value) r.studentId = [{ required:true, message:'请选择学生', trigger:'change' }]
  }
  if(current.value === 'housing' && admin.value) r.studentId = [{ required:true, message:'请选择学生', trigger:'change' }]
  return r
})
const columns = computed(() => {
  if (adminKeys.includes(current.value)) {
    const fields = (meta.value.fields || []).filter(f => f.name !== 'password').map(f => ({title:f.label, key:f.name, dataIndex:f.name}))
    return [{title:'编号',dataIndex:'id',key:'id'}, ...fields, ...(current.value === 'beds' ? [{title:'状态',dataIndex:'status',key:'status'}] : []), {title:'操作',key:'actions',width:150}]
  }
  if(current.value === 'repairs') return [
    {title:'编号',dataIndex:'id',key:'id'}, ...(admin.value?[{title:'学生',key:'student'}]:[]),
    {title:'故障描述',dataIndex:'description',key:'description',width:250}, {title:'提交时间',dataIndex:'created_at',key:'created_at'},
    {title:'状态',dataIndex:'status',key:'status'}, {title:'评价',dataIndex:'evaluation',key:'evaluation',width:200}, {title:'操作',key:'actions',width:220}
  ]
  return [{title:'编号',dataIndex:'id',key:'id'}, ...(admin.value?[{title:'学生',key:'student'}]:[]),
    {title:'床位',dataIndex:'bed_label',key:'bed_label'}, {title:'状态',dataIndex:'status',key:'status'},
    {title:'入住时间',dataIndex:'check_in_at',key:'check_in_at'}, {title:'退宿申请',dataIndex:'checkout_requested_at',key:'checkout_requested_at'},
    {title:'退宿时间',dataIndex:'check_out_at',key:'check_out_at'}, {title:'操作',key:'actions',width:180}]
})
const dormColumns = [{title:'楼栋',dataIndex:'building'}, {title:'宿舍',dataIndex:'room_no'}, {title:'已占用',dataIndex:'occupied'}, {title:'床位总数',dataIndex:'total'}, {title:'入住率',key:'rate'}]
const repairStateColumns = [{title:'状态',key:'status'}, {title:'数量',dataIndex:'total'}]
function rate(n, total) { return `${total ? (Number(n) * 100 / Number(total)).toFixed(1) : '0.0'}%` }
function date(value) { return value ? String(value).replace('T', ' ').slice(0,16) : '—' }
async function token() { const r = await fetch('/api/csrf', {credentials:'same-origin'}); if(!r.ok) throw Error('无法获取安全令牌'); csrf.value = await r.json() }
async function request(url, { method='GET', data, formData=false }={}) {
  const headers = {}
  if(method !== 'GET') headers[csrf.value.headerName || 'X-CSRF-TOKEN'] = csrf.value.token || ''
  if(data !== undefined) headers['Content-Type'] = formData ? 'application/x-www-form-urlencoded' : 'application/json'
  const response = await fetch('/api' + url, { method, headers, credentials:'same-origin', body:data === undefined ? undefined : formData ? new URLSearchParams(data) : JSON.stringify(data) })
  if(!response.ok) { let error; try { error = await response.json() } catch { /* 403 等空响应 */ } throw new Error(error?.message || (response.status === 401 ? '请先登录' : response.status === 403 ? '没有操作权限' : '请求失败，请重试')) }
  if(response.status === 204 || response.headers.get('content-length') === '0') return null
  const body = await response.text(); return body ? JSON.parse(body) : null
}
async function initialize() { try { await token(); user.value = await request('/session'); current.value = admin.value?'statistics':'repairs'; await load() } catch { user.value = null } }
async function doLogin() {
  saving.value=true
  try { await request('/login',{method:'POST',data:login,formData:true}); await token(); user.value=await request('/session'); current.value=admin.value?'statistics':'repairs'; page.value=1; login.password=''; await load() }
  catch(e) { message.error(e.message); await token() } finally { saving.value=false }
}
async function logout() { try { await request('/logout',{method:'POST'}); user.value=null; await token() } catch(e) { message.error(e.message) } }
async function load() {
  busy.value=true
  try {
    if(current.value === 'statistics') { stats.value = await request('/admin/statistics'); return }
    const resource = adminKeys.includes(current.value) ? '/admin/'+current.value : '/'+current.value
    const data = await request(resource+'?page='+page.value)
    if(adminKeys.includes(current.value)) { meta.value=data.meta; result.value=data.data } else result.value=data
    page.value=result.value.page
  } catch(e) { message.error(e.message) } finally { busy.value=false }
}
async function navigate(key) { current.value=key; page.value=1; await load() }
function changePage(p) { page.value=p.current; load() }
function resetForm() { for(const key of Object.keys(form)) delete form[key]; editId.value=null }
async function openEditor(record=null) {
  resetForm(); editId.value=record?.id || null
  try {
    if(adminKeys.includes(current.value)) {
      if(record) Object.assign(form, await request('/admin/'+current.value+'/'+record.id))
      for(const f of meta.value.fields) if(form[f.name] != null) form[f.name]=String(form[f.name])
    } else if(current.value === 'repairs') {
      if(record) Object.assign(form, await request('/repairs/'+record.id))
      if(admin.value && !record) students.value = await request('/admin/options/students')
    } else if(current.value === 'housing' && admin.value) students.value = await request('/admin/options/students')
    showEditor.value=true
  } catch(e) { message.error(e.message) }
}
async function saveEditor() {
  try { await formRef.value?.validate() } catch { return }
  saving.value=true
  try {
    if(adminKeys.includes(current.value)) {
      const body={}; for(const f of meta.value.fields) body[f.name]=String(form[f.name] ?? '')
      await request('/admin/'+current.value+(editId.value?'/'+editId.value:''),{method:editId.value?'PUT':'POST',data:body})
    } else if(current.value==='repairs') {
      await request('/repairs'+(editId.value?'/'+editId.value:''),{method:editId.value?'PUT':'POST',data:{description:form.description,studentId:form.studentId}})
    } else await request('/housing',{method:'POST',data:{studentId:form.studentId}})
    showEditor.value=false; message.success('保存成功'); await load()
  } catch(e) { message.error(e.message) } finally { saving.value=false }
}
function remove(record) {
  Modal.confirm({title:'确定删除这条记录吗？', okText:'删除', cancelText:'取消', okType:'danger', async onOk() {
    try { const resource=adminKeys.includes(current.value)?'/admin/'+current.value:'/'+current.value; await request(resource+'/'+record.id,{method:'DELETE'}); message.success('删除成功'); await load() } catch(e) { message.error(e.message) }
  }})
}
async function action(type,id,action,data={}) {
  try { await request('/'+type+'/'+id+'/'+action,{method:'POST',data}); message.success('操作成功'); await load(); return true } catch(e) { message.error(e.message); return false }
}
function openEvaluation(id) { evaluateId.value=id; evaluation.value=''; showEvaluate.value=true }
async function submitEvaluation() {
  if(!evaluation.value.trim()) return message.warning('请填写评价')
  saving.value=true; if(await action('repairs',evaluateId.value,'confirm',{evaluation:evaluation.value})) showEvaluate.value=false; saving.value=false
}
async function openAssign(id) { try { beds.value=await request('/admin/options/free-beds'); selectedBed.value=undefined; assignId.value=id; showAssign.value=true } catch(e) { message.error(e.message) } }
async function submitAssign() {
  if(!selectedBed.value) return message.warning('请选择空床位')
  saving.value=true; if(await action('housing',assignId.value,'assign',{bedId:selectedBed.value})) showAssign.value=false; saving.value=false
}
onMounted(initialize)
</script>

<template>
  <div v-if="!user" class="login-wrap">
    <div class="login-card"><div class="login-icon"><PhBuildings :size="26" weight="duotone" /></div><p class="eyebrow">DORMITORY MANAGEMENT</p><h1>学生公寓管理系统</h1><p class="login-subtitle">登录以管理宿舍事务</p>
      <a-form :model="login" layout="vertical" @finish="doLogin">
        <a-form-item label="用户名" name="username" :rules="[{required:true,message:'请输入用户名'}]"><a-input v-model:value="login.username" size="large" maxlength="50" autocomplete="username" /></a-form-item>
        <a-form-item label="密码" name="password" :rules="[{required:true,message:'请输入密码'}]"><a-input-password v-model:value="login.password" size="large" maxlength="100" autocomplete="current-password" /></a-form-item>
        <a-button type="primary" size="large" html-type="submit" block :loading="saving">登录</a-button>
      </a-form>
    </div>
  </div>
  <a-layout v-else class="app-layout">
    <a-layout-sider class="sider" :width="228" breakpoint="lg" collapsed-width="0">
      <div class="brand">宿舍管理<small>学生公寓</small></div><div class="menu-caption">工作台</div>
      <a-menu theme="dark" mode="inline" :selected-keys="[current]" @click="({key}) => navigate(key)">
        <a-menu-item v-if="admin" key="statistics"><template #icon><PhChartBar :size="18" /></template>数据概览</a-menu-item><a-menu-item key="repairs"><template #icon><PhWrench :size="18" /></template>报修管理</a-menu-item><a-menu-item key="housing"><template #icon><PhDoorOpen :size="18" /></template>入住与退宿</a-menu-item>
        <a-menu-item-group v-if="admin" title="基础信息"><a-menu-item key="students"><template #icon><PhStudent :size="18" /></template>学生管理</a-menu-item><a-menu-item key="dormitories"><template #icon><PhHouseLine :size="18" /></template>宿舍管理</a-menu-item><a-menu-item key="beds"><template #icon><PhBed :size="18" /></template>床位管理</a-menu-item><a-menu-item key="users"><template #icon><PhUsers :size="18" /></template>账号管理</a-menu-item></a-menu-item-group>
      </a-menu>
      <div class="sider-footer"><strong>{{ user.username }}</strong><small>{{ admin ? '管理员' : '学生' }}</small><a-button type="link" @click="logout"><template #icon><PhSignOut :size="16" /></template>退出登录</a-button></div>
    </a-layout-sider>
    <a-layout-content class="content">
      <div class="page-header"><div><p class="eyebrow">{{ adminKeys.includes(current) ? '基础信息' : '工作台' }}</p><h1>{{ title }}</h1></div>
        <a-button v-if="current !== 'statistics'" type="primary" @click="openEditor()"><template #icon><PhPlus :size="16" /></template>{{ current === 'repairs' ? '提交报修' : current === 'housing' ? '入住申请' : '新增' }}</a-button>
      </div>
      <template v-if="current === 'statistics' && stats">
        <div class="metrics"><a-card><div class="metric-label">床位入住率</div><div class="metric-value">{{ rate(stats.beds.occupied,stats.beds.total) }}</div><div class="muted">已占用 {{ stats.beds.occupied }} / {{ stats.beds.total }} 个床位</div></a-card><a-card><div class="metric-label">报修处理率</div><div class="metric-value">{{ rate(stats.repairs.done,stats.repairs.total) }}</div><div class="muted">已完成或确认 {{ stats.repairs.done }} / {{ stats.repairs.total }} 单</div></a-card></div>
        <a-card title="各宿舍床位" class="section"><a-table :columns="dormColumns" :data-source="stats.dorms" :pagination="{pageSize:10,showSizeChanger:false}" :row-key="r => r.building + r.room_no" size="middle"><template #bodyCell="{column,record}"><span v-if="column.key === 'rate'">{{ rate(record.occupied,record.total) }}</span></template></a-table></a-card>
        <a-card title="报修状态" class="section"><a-table :columns="repairStateColumns" :data-source="stats.repairStates" :pagination="false" row-key="status" size="middle"><template #bodyCell="{column,record}"><span v-if="column.key === 'status'">{{ statusLabels[record.status] }}</span></template></a-table></a-card>
      </template>
      <a-card v-else class="section table-card"><a-table :columns="columns" :data-source="result.rows" :pagination="pagination" :loading="busy" row-key="id" :scroll="{x:'max-content'}" @change="changePage">
        <template #bodyCell="{column,record,text}">
          <template v-if="column.key === 'student'">{{ record.student_no }} {{ record.student_name }}</template>
          <template v-else-if="column.key === 'status'"><a-tag :color="['CONFIRMED','CHECKED_IN','FREE'].includes(record.status) ? 'green' : ['IN_PROGRESS','ASSIGNED'].includes(record.status) ? 'blue' : undefined">{{ statusLabels[record.status] }}</a-tag></template>
          <template v-else-if="['created_at','check_in_at','checkout_requested_at','check_out_at'].includes(column.key)">{{ date(text) }}</template>
          <template v-else-if="column.key === 'actions'"><div class="actions">
            <template v-if="adminKeys.includes(current)"><a @click="openEditor(record)">编辑</a><a class="danger" @click="remove(record)">删除</a></template>
            <template v-else-if="current === 'repairs'"><template v-if="record.status === 'WAITING'"><a @click="openEditor(record)">编辑</a><a class="danger" @click="remove(record)">删除</a><a v-if="admin" @click="action('repairs',record.id,'accept')">受理</a></template><a v-if="admin && record.status === 'IN_PROGRESS'" @click="action('repairs',record.id,'complete')">标记完成</a><a v-if="!admin && record.status === 'COMPLETED'" @click="openEvaluation(record.id)">确认评价</a></template>
            <template v-else><template v-if="record.status === 'PENDING'"><a class="danger" @click="remove(record)">删除</a><a v-if="admin" @click="openAssign(record.id)">分配床位</a></template><a v-if="admin && record.status === 'ASSIGNED'" @click="action('housing',record.id,'checkin')">办理入住</a><a v-if="!admin && record.status === 'CHECKED_IN' && !record.checkout_requested_at" @click="action('housing',record.id,'request-checkout')">申请退宿</a><a v-if="admin && record.status === 'CHECKED_IN' && record.checkout_requested_at" @click="action('housing',record.id,'checkout')">办理退宿</a></template>
          </div></template>
          <template v-else-if="record[column.key + '_label']">{{ record[column.key + '_label'] }}</template>
          <template v-else-if="column.key === 'description' || column.key === 'evaluation'"><span class="wrap-text">{{ text || '—' }}</span></template>
          <template v-else-if="column.key === 'bed_label'">{{ text || '—' }}</template>
          <template v-else>{{ text ?? '—' }}</template>
        </template>
      </a-table></a-card>
    </a-layout-content>
  </a-layout>
  <a-modal v-model:open="showEditor" :title="(editId ? '编辑' : current === 'repairs' ? '提交' : current === 'housing' ? '提交' : '新增') + (adminKeys.includes(current) ? meta.title : current === 'repairs' ? '报修' : '入住申请')" ok-text="保存" cancel-text="取消" :confirm-loading="saving" destroy-on-close @ok="saveEditor">
    <a-form ref="formRef" :model="form" :rules="rules" layout="vertical" class="modal-form">
      <template v-if="adminKeys.includes(current)"><a-form-item v-for="field in meta.fields" :key="field.name" :label="field.label" :name="field.name"><a-select v-if="field.type === 'select'" v-model:value="form[field.name]" placeholder="请选择" :options="Object.entries(field.options).map(([value,label]) => ({value,label}))"/><a-input-password v-else-if="field.type === 'password'" v-model:value="form.password" :maxlength="field.max" :placeholder="editId ? '留空则不修改密码' : ''"/><a-input v-else v-model:value="form[field.name]" :maxlength="field.max" /></a-form-item></template>
      <template v-else><a-form-item v-if="admin && !editId" label="学生" name="studentId"><a-select v-model:value="form.studentId" placeholder="请选择学生" :options="studentOptions" show-search :filter-option="(input, option) => option.label.toLowerCase().includes(input.toLowerCase())" /></a-form-item><a-form-item v-if="current === 'repairs'" label="故障描述" name="description"><a-textarea v-model:value="form.description" :rows="4" :maxlength="500" show-count /></a-form-item></template>
    </a-form>
  </a-modal>
  <a-modal v-model:open="showEvaluate" title="确认并评价" ok-text="确认评价" cancel-text="取消" :confirm-loading="saving" @ok="submitEvaluation"><a-textarea v-model:value="evaluation" :rows="4" :maxlength="500" show-count placeholder="填写评价" /></a-modal>
  <a-modal v-model:open="showAssign" title="分配床位" ok-text="分配" cancel-text="取消" :confirm-loading="saving" @ok="submitAssign"><a-select v-model:value="selectedBed" style="width:100%" placeholder="请选择空床位" :options="bedOptions" /></a-modal>
</template>
