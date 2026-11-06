<script setup>
import { inject, ref } from 'vue'
import { PhArrowUUpLeft, PhBed, PhCheck, PhClipboardText, PhDoorOpen, PhEye, PhInfo, PhPencilSimple, PhPlus, PhSignIn, PhSignOut, PhStar, PhTrash, PhUserMinus, PhXCircle } from '@phosphor-icons/vue'
import DormitoryManagement from '../components/DormitoryManagement.vue'
import TableIconButton from '../components/TableIconButton.vue'

const d = inject('desk')
const viewOpen = ref(false)
const viewKind = ref('')
const viewRecord = ref(null)
function openView(kind, record) { viewKind.value = kind; viewRecord.value = record; viewOpen.value = true }
</script>

<template>
  <div v-if="d.current !== 'dormitories'" class="page-header">
    <h1>{{ d.title }}</h1>
    <div class="header-actions">
      <a-tag v-if="d.current === 'housing' && !d.admin && d.activeHousing?.active" color="orangered">{{ d.statusLabels[d.activeHousing.status] }}</a-tag>
      <a-button v-if="d.current !== 'statistics' && d.current !== 'profile' && !(d.admin && (d.current === 'repairs' || d.current === 'housing'))" type="primary" :disabled="d.current === 'housing' && !d.admin && d.activeHousing?.active" @click="d.openEditor()">
        <template #icon><PhPlus :size="16" /></template>{{ d.current === 'repairs' ? '提交报修' : d.current === 'housing' ? '入住申请' : '新增' }}
      </a-button>
    </div>
  </div>
  <DormitoryManagement v-if="d.current === 'dormitories'" />
  <div v-if="d.current === 'repairs'" class="filters">
    <a-input v-model="d.repairFilter.keyword" allow-clear placeholder="学生、学号或故障" @change="d.applyFilters" @press-enter="d.applyFilters" @clear="d.applyFilters" />
    <a-input v-model="d.repairFilter.dorm" allow-clear placeholder="楼栋或宿舍" @change="d.applyFilters" @press-enter="d.applyFilters" @clear="d.applyFilters" />
    <a-select v-model="d.repairFilter.status" allow-clear placeholder="全部状态" @change="d.applyFilters">
      <a-option value="WAITING">待受理</a-option>
      <a-option value="IN_PROGRESS">处理中</a-option>
      <a-option value="COMPLETED">已完成</a-option>
      <a-option value="CONFIRMED">已评价</a-option>
    </a-select>
  </div>
  <a-card v-if="d.current === 'profile'" class="section profile-card" :bordered="false">
    <div class="profile-head">
      <span class="profile-avatar">{{ (d.profile.username || '?').charAt(0).toUpperCase() }}</span>
      <div class="profile-id"><strong>{{ d.profile.username || '—' }}</strong><a-tag>学生</a-tag></div>
    </div>
    <a-form :model="d.profile" layout="vertical" class="profile-form" @submit="d.saveProfile">
      <a-form-item label="用户名"><a-input :model-value="d.profile.username" disabled /></a-form-item>
      <a-form-item field="student_no">
        <template #label>
          <span class="field-label-help">学号
            <a-tooltip content="学号由管理员维护，如需变更请联系管理员。" :trigger="['hover', 'focus', 'click']">
              <button type="button" class="field-help" aria-label="学号修改说明"><PhInfo :size="16" aria-hidden="true" /></button>
            </a-tooltip>
          </span>
        </template>
        <a-input v-model="d.profile.student_no" :max-length="30" disabled />
      </a-form-item>
      <a-form-item field="name" label="姓名" :rules="[{ required:true, message:'请填写姓名' }]"><a-input v-model="d.profile.name" :max-length="50" /></a-form-item>
      <a-form-item field="class_name" label="班级"><a-input v-model="d.profile.class_name" :max-length="50" /></a-form-item>
      <a-form-item field="phone" label="联系电话"><a-input v-model="d.profile.phone" :max-length="30" /></a-form-item>
      <a-form-item field="password" label="新密码"><a-input-password v-model="d.profile.password" :max-length="100" placeholder="留空则不修改" /></a-form-item>
      <a-form-item v-if="d.profile.password" field="oldPassword" label="原密码" :rules="[{ required:true, message:'请填写原密码' }]"><a-input-password v-model="d.profile.oldPassword" :max-length="100" placeholder="验证原密码后才能修改" /></a-form-item>
      <a-button type="primary" html-type="submit" :loading="d.saving">保存</a-button>
    </a-form>
  </a-card>
  <template v-if="d.current === 'statistics'">
    <a-skeleton v-if="!d.stats" animation />
    <template v-else-if="d.signedIn && !d.admin">
      <div class="student-overview">
          <a-card title="我的住宿" class="metric-card student-summary" :bordered="false">
            <div class="student-summary-content">
            <div class="student-bed">{{ d.stats.housing?.bed_label || '暂无床位' }}</div>
            <div class="metric-detail">{{ d.stats.housing ? d.stats.housing.checkout_requested_at ? '退宿申请待办理' : d.statusLabels[d.stats.housing.status] : '暂无有效入住记录' }}</div>
            </div>
            <a-button class="student-summary-action" @click="d.navigate('housing')">{{ d.stats.housing ? '查看入住记录' : '申请入住' }}</a-button>
          </a-card>
          <a-card title="我的报修" class="metric-card student-summary" :bordered="false">
            <div class="student-repair-counts">
              <div><span>待处理</span><strong>{{ d.stats.repairs.pending }}</strong></div>
              <div><span>待评价</span><strong>{{ d.stats.repairs.awaiting_evaluation }}</strong></div>
              <div><span>全部报修</span><strong>{{ d.stats.repairs.total }}</strong></div>
            </div>
            <a-button class="student-summary-action" @click="d.navigate('repairs')">查看我的报修</a-button>
          </a-card>
      </div>
      <a-card title="我的报修进度" class="section state-card flat-card" :bordered="false">
        <div class="state-strip">
          <div v-for="item in d.repairStates" :key="item.status" class="state-tile">
            <div class="state-label">{{ d.statusLabels[item.status] }}</div>
            <div class="state-count">{{ item.total }}<span>单</span></div>
          </div>
        </div>
      </a-card>
    </template>
    <template v-else>
      <a-row :gutter="[24, 24]" class="metrics">
        <a-col :xs="24" :sm="12">
          <a-card class="metric-card" :bordered="false">
            <div class="metric-label">入住率</div>
            <div class="metric-value">{{ d.rate(d.stats.beds.occupied, d.stats.beds.total) }}</div>
            <a-progress :percent="d.percentage(d.stats.beds.occupied, d.stats.beds.total) / 100" :show-text="false" :stroke-width="4" :animation="false" color="#1D2129" track-color="#E5E6EB" />
            <div class="metric-detail">已占用 <strong>{{ d.stats.beds.occupied }}</strong> / {{ d.stats.beds.total }} 个床位（已分配未入住也计入）</div>
          </a-card>
        </a-col>
        <a-col :xs="24" :sm="12">
          <a-card class="metric-card" :bordered="false">
            <div class="metric-label">报修处理率</div>
            <div class="metric-value">{{ d.rate(d.stats.repairs.done, d.stats.repairs.total) }}</div>
            <a-progress :percent="d.percentage(d.stats.repairs.done, d.stats.repairs.total) / 100" :show-text="false" :stroke-width="4" :animation="false" color="#1D2129" track-color="#E5E6EB" />
            <div class="metric-detail">已完成及已评价 <strong>{{ d.stats.repairs.done }}</strong> / {{ d.stats.repairs.total }} 单</div>
          </a-card>
        </a-col>
      </a-row>
      <a-card v-if="d.admin" title="各宿舍床位" class="section dorm-card flat-card" :bordered="false">
        <a-table :columns="d.dormColumns" :data="d.dormRows" :pagination="d.dormPagination" :bordered="false" row-key="key" :scroll="{ x:'max-content' }" size="medium">
          <template #rate="{ record }">
            <div class="table-rate">
              <a-progress :percent="d.percentage(record.occupied, record.total) / 100" :show-text="false" :stroke-width="4" :animation="false" color="#1D2129" track-color="#E5E6EB" />
              <span>{{ d.rate(record.occupied, record.total) }}</span>
            </div>
          </template>
        </a-table>
      </a-card>
      <a-card v-if="d.admin" title="报修状态" class="section state-card flat-card" :bordered="false">
        <div class="state-strip">
          <div v-for="item in d.repairStates" :key="item.status" class="state-tile">
            <div class="state-label">{{ d.statusLabels[item.status] }}</div>
            <div class="state-count">{{ item.total }}<span>单</span></div>
          </div>
        </div>
      </a-card>
    </template>
  </template>
  <a-card v-else-if="d.current !== 'profile' && d.current !== 'dormitories'" class="section table-card" :bordered="false">
    <a-table :columns="d.columns" :data="d.result.rows" :pagination="d.pagination" :loading="d.busy" row-key="id" :scroll="{ x:'max-content' }" size="medium" @page-change="d.changePage">
      <template #cell="{ column, record }">
        <template v-if="column.key === 'student'">{{ record.student_no }} {{ record.student_name }}</template>
        <template v-else-if="column.key === 'dorm'">{{ record.dorm_label || '—' }}</template>
        <template v-else-if="column.key === 'status'"><a-tag :color="d.tagColor(record.status)">{{ d.statusLabels[record.status] }}</a-tag></template>
        <template v-else-if="['created_at', 'check_in_at', 'checkout_requested_at', 'check_out_at', 'arrived_at', 'left_at'].includes(column.key)">{{ d.date(record[column.dataIndex]) }}</template>
        <template v-else-if="column.key === 'actions'"><div class="actions">
          <template v-if="d.adminKeys.includes(d.current)">
            <TableIconButton label="编辑" @click="d.openEditor(record)"><PhPencilSimple /></TableIconButton>
            <TableIconButton label="删除" danger @click="d.remove(record)"><PhTrash /></TableIconButton>
          </template>
          <template v-else-if="d.current === 'repairs'">
            <TableIconButton label="查看" @click="openView('repair', record)"><PhEye /></TableIconButton>
            <TableIconButton v-if="d.admin && record.status === 'WAITING'" label="受理" @click="d.action('repairs', record.id, 'accept')"><PhClipboardText /></TableIconButton>
            <template v-if="record.status === 'WAITING'">
              <TableIconButton label="编辑" @click="d.openEditor(record)"><PhPencilSimple /></TableIconButton>
              <TableIconButton label="删除" danger @click="d.remove(record)"><PhTrash /></TableIconButton>
            </template>
            <TableIconButton v-if="d.admin && record.status === 'IN_PROGRESS'" label="标记完成" @click="d.openComplete(record.id)"><PhCheck /></TableIconButton>
            <TableIconButton v-if="!d.admin && record.status === 'COMPLETED'" label="确认评价" @click="d.openEvaluation(record.id)"><PhStar /></TableIconButton>
          </template>
          <template v-else>
            <TableIconButton v-if="record.bed_label" label="查看分配" @click="openView('housing', record)"><PhEye /></TableIconButton>
            <template v-if="record.status === 'PENDING'">
              <TableIconButton label="删除" danger @click="d.remove(record)"><PhTrash /></TableIconButton>
              <TableIconButton v-if="d.admin" label="分配床位" @click="d.openAssign(record)"><PhBed /></TableIconButton>
            </template>
            <TableIconButton v-if="d.admin && record.status === 'ASSIGNED'" label="办理入住" @click="d.action('housing', record.id, 'checkin')"><PhSignIn /></TableIconButton>
            <TableIconButton v-if="d.admin && record.status === 'ASSIGNED'" label="取消分配" danger @click="d.confirmAction('housing', record.id, 'unassign', '取消床位分配', '取消后该床位将释放，申请退回待审核状态。')"><PhXCircle /></TableIconButton>
            <TableIconButton v-if="!d.admin && record.status === 'CHECKED_IN' && !record.checkout_requested_at" label="申请退宿" @click="d.action('housing', record.id, 'request-checkout')"><PhDoorOpen /></TableIconButton>
            <TableIconButton v-if="!d.admin && record.status === 'CHECKED_IN' && record.checkout_requested_at" label="撤销退宿申请" @click="d.action('housing', record.id, 'cancel-checkout')"><PhArrowUUpLeft /></TableIconButton>
            <TableIconButton v-if="d.admin && record.status === 'CHECKED_IN' && record.checkout_requested_at" label="办理退宿" @click="d.action('housing', record.id, 'checkout')"><PhSignOut /></TableIconButton>
            <TableIconButton v-if="d.admin && record.status === 'CHECKED_IN' && !record.checkout_requested_at" label="强制退宿" danger @click="d.confirmAction('housing', record.id, 'force-checkout', '强制退宿', '该操作无需学生申请，确认后将立即释放床位。')"><PhUserMinus /></TableIconButton>
          </template>
        </div></template>
        <template v-else-if="record[column.key + '_label']">{{ record[column.key + '_label'] }}</template>
        <template v-else-if="column.key === 'description' || column.key === 'evaluation'"><span class="wrap-text">{{ record[column.dataIndex] || '—' }}</span></template>
        <template v-else-if="column.key === 'bed_label'">{{ record[column.dataIndex] || '—' }}</template>
        <template v-else>{{ record[column.dataIndex] ?? '—' }}</template>
      </template>
    </a-table>
  </a-card>
  <a-modal v-model:visible="viewOpen" :title="viewKind === 'repair' ? '报修详情' : '床位分配'" :footer="false">
    <dl v-if="viewRecord" class="detail">
      <template v-if="viewKind === 'repair'">
        <div><dt>报修宿舍</dt><dd>{{ viewRecord.location || '未记录' }}</dd></div>
        <div><dt>故障描述</dt><dd>{{ viewRecord.description || '—' }}</dd></div>
        <div><dt>完成说明</dt><dd>{{ viewRecord.completion_note || '—' }}</dd></div>
        <div><dt>状态</dt><dd>{{ d.statusLabels[viewRecord.status] }}</dd></div>
        <div><dt>提交时间</dt><dd>{{ d.date(viewRecord.created_at) }}</dd></div>
        <div><dt>评价</dt><dd>{{ viewRecord.evaluation || '—' }}</dd></div>
        <div><dt>满意度</dt><dd>{{ viewRecord.satisfaction === 'SATISFIED' ? '满意' : viewRecord.satisfaction === 'DISSATISFIED' ? '不满意' : '—' }}</dd></div>
      </template>
      <template v-else>
        <div><dt>床位</dt><dd>{{ viewRecord.bed_label || '尚未分配' }}</dd></div>
        <div><dt>状态</dt><dd>{{ d.statusLabels[viewRecord.status] }}</dd></div>
        <div><dt>入住时间</dt><dd>{{ d.date(viewRecord.check_in_at) }}</dd></div>
        <div><dt>退宿申请</dt><dd>{{ d.date(viewRecord.checkout_requested_at) }}</dd></div>
        <div><dt>退宿时间</dt><dd>{{ d.date(viewRecord.check_out_at) }}</dd></div>
      </template>
    </dl>
  </a-modal>
</template>
