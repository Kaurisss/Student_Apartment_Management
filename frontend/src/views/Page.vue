<script setup>
import { inject, ref } from 'vue'
import { PhPlus } from '@phosphor-icons/vue'

const d = inject('desk')
const viewOpen = ref(false)
const viewKind = ref('')
const viewRecord = ref(null)
function openView(kind, record) { viewKind.value = kind; viewRecord.value = record; viewOpen.value = true }
</script>

<template>
  <div class="page-header">
    <h1>{{ d.title }}</h1>
    <div class="header-actions">
      <a-tag v-if="d.current === 'housing' && !d.admin && d.activeHousing?.active" color="orangered">{{ d.statusLabels[d.activeHousing.status] }}</a-tag>
      <a-button v-if="d.current !== 'statistics' && d.current !== 'profile' && !(d.admin && (d.current === 'repairs' || d.current === 'housing'))" type="primary" :disabled="d.current === 'housing' && !d.admin && d.activeHousing?.active" @click="d.openEditor()">
        <template #icon><PhPlus :size="16" /></template>{{ d.current === 'repairs' ? '提交报修' : d.current === 'housing' ? '入住申请' : '新增' }}
      </a-button>
    </div>
  </div>
  <div v-if="d.current === 'repairs'" class="filters">
    <a-input v-model="d.repairFilter.keyword" allow-clear placeholder="学生、学号或故障" @change="d.applyFilters" @press-enter="d.applyFilters" @clear="d.applyFilters" />
    <a-input v-model="d.repairFilter.dorm" allow-clear placeholder="楼栋或宿舍" @change="d.applyFilters" @press-enter="d.applyFilters" @clear="d.applyFilters" />
    <a-select v-model="d.repairFilter.status" allow-clear placeholder="全部状态" @change="d.applyFilters">
      <a-option value="WAITING">待受理</a-option>
      <a-option value="IN_PROGRESS">处理中</a-option>
      <a-option value="COMPLETED">已完成</a-option>
      <a-option value="CONFIRMED">已确认</a-option>
    </a-select>
  </div>
  <a-card v-if="d.current === 'profile'" class="section profile-card" :bordered="false">
    <div class="profile-head">
      <span class="profile-avatar">{{ (d.profile.username || '?').charAt(0).toUpperCase() }}</span>
      <div class="profile-id"><strong>{{ d.profile.username || '—' }}</strong><a-tag>学生</a-tag></div>
    </div>
    <a-form :model="d.profile" layout="vertical" class="profile-form" @submit="d.saveProfile">
      <a-form-item label="用户名"><a-input :model-value="d.profile.username" disabled /></a-form-item>
      <a-form-item field="student_no" label="学号"><a-input v-model="d.profile.student_no" :max-length="30" disabled /><span class="field-hint">学号由管理员维护，如需变更请联系管理员。</span></a-form-item>
      <a-form-item field="name" label="姓名" :rules="[{ required:true, message:'请填写姓名' }]"><a-input v-model="d.profile.name" :max-length="50" /></a-form-item>
      <a-form-item field="password" label="新密码"><a-input-password v-model="d.profile.password" :max-length="100" placeholder="留空则不修改" /></a-form-item>
      <a-form-item v-if="d.profile.password" field="oldPassword" label="原密码" :rules="[{ required:true, message:'请填写原密码' }]"><a-input-password v-model="d.profile.oldPassword" :max-length="100" placeholder="验证原密码后才能修改" /></a-form-item>
      <a-button type="primary" html-type="submit" :loading="d.saving">保存</a-button>
    </a-form>
  </a-card>
  <template v-if="d.current === 'statistics'">
    <a-skeleton v-if="!d.stats" animation />
    <template v-else>
      <a-row :gutter="[24, 24]" class="metrics">
        <a-col :xs="24" :sm="12">
          <a-card class="metric-card" :bordered="false">
            <div class="metric-label">床位占用率</div>
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
            <div class="metric-detail">已完成或确认 <strong>{{ d.stats.repairs.done }}</strong> / {{ d.stats.repairs.total }} 单</div>
          </a-card>
        </a-col>
      </a-row>
      <a-card title="各宿舍床位" class="section dorm-card flat-card" :bordered="false">
        <a-table :columns="d.dormColumns" :data="d.dormRows" :pagination="d.dormPagination" :bordered="false" row-key="key" :scroll="{ x:'max-content' }" size="medium">
          <template #rate="{ record }">
            <div class="table-rate">
              <a-progress :percent="d.percentage(record.occupied, record.total) / 100" :show-text="false" :stroke-width="4" :animation="false" color="#1D2129" track-color="#E5E6EB" />
              <span>{{ d.rate(record.occupied, record.total) }}</span>
            </div>
          </template>
        </a-table>
      </a-card>
      <a-card title="报修状态" class="section state-card flat-card" :bordered="false">
        <div class="state-strip">
          <div v-for="item in d.repairStates" :key="item.status" class="state-tile">
            <div class="state-label">{{ d.statusLabels[item.status] }}</div>
            <div class="state-count">{{ item.total }}<span>单</span></div>
          </div>
        </div>
      </a-card>
    </template>
  </template>
  <a-card v-else-if="d.current !== 'profile'" class="section table-card" :bordered="false">
    <a-table :columns="d.columns" :data="d.result.rows" :pagination="d.pagination" :loading="d.busy" row-key="id" :scroll="{ x:'max-content' }" size="medium" @page-change="d.changePage">
      <template #cell="{ column, record }">
        <template v-if="column.key === 'student'">{{ record.student_no }} {{ record.student_name }}</template>
        <template v-else-if="column.key === 'dorm'">{{ record.dorm_label || '—' }}</template>
        <template v-else-if="column.key === 'status'"><a-tag :color="d.tagColor(record.status)">{{ d.statusLabels[record.status] }}</a-tag></template>
        <template v-else-if="['created_at', 'check_in_at', 'checkout_requested_at', 'check_out_at'].includes(column.key)">{{ d.date(record[column.dataIndex]) }}</template>
        <template v-else-if="column.key === 'actions'"><div class="actions">
          <template v-if="d.adminKeys.includes(d.current)">
            <button type="button" @click="d.openEditor(record)">编辑</button>
            <button type="button" class="danger" @click="d.remove(record)">删除</button>
          </template>
          <template v-else-if="d.current === 'repairs'">
            <button type="button" @click="openView('repair', record)">查看</button>
            <a-button v-if="d.admin && record.status === 'WAITING'" type="primary" size="mini" @click="d.action('repairs', record.id, 'accept')">受理</a-button>
            <template v-if="record.status === 'WAITING'">
              <button type="button" @click="d.openEditor(record)">编辑</button>
              <button type="button" class="danger" @click="d.remove(record)">删除</button>
            </template>
            <button v-if="d.admin && record.status === 'IN_PROGRESS'" type="button" @click="d.action('repairs', record.id, 'complete')">标记完成</button>
            <button v-if="!d.admin && record.status === 'COMPLETED'" type="button" @click="d.openEvaluation(record.id)">确认评价</button>
          </template>
          <template v-else>
            <button v-if="record.bed_label" type="button" @click="openView('housing', record)">查看分配</button>
            <template v-if="record.status === 'PENDING'">
              <button type="button" class="danger" @click="d.remove(record)">删除</button>
              <button v-if="d.admin" type="button" @click="d.openAssign(record)">分配床位</button>
            </template>
            <button v-if="d.admin && record.status === 'ASSIGNED'" type="button" @click="d.action('housing', record.id, 'checkin')">办理入住</button>
            <button v-if="d.admin && record.status === 'ASSIGNED'" type="button" class="danger" @click="d.confirmAction('housing', record.id, 'unassign', '取消床位分配', '取消后该床位将释放，申请退回待审核状态。')">取消分配</button>
            <button v-if="!d.admin && record.status === 'CHECKED_IN' && !record.checkout_requested_at" type="button" @click="d.action('housing', record.id, 'request-checkout')">申请退宿</button>
            <button v-if="!d.admin && record.status === 'CHECKED_IN' && record.checkout_requested_at" type="button" @click="d.action('housing', record.id, 'cancel-checkout')">撤销退宿申请</button>
            <button v-if="d.admin && record.status === 'CHECKED_IN' && record.checkout_requested_at" type="button" @click="d.action('housing', record.id, 'checkout')">办理退宿</button>
            <button v-if="d.admin && record.status === 'CHECKED_IN' && !record.checkout_requested_at" type="button" class="danger" @click="d.confirmAction('housing', record.id, 'force-checkout', '强制退宿', '该操作无需学生申请，确认后将立即释放床位。')">强制退宿</button>
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
        <div><dt>故障描述</dt><dd>{{ viewRecord.description || '—' }}</dd></div>
        <div><dt>状态</dt><dd>{{ d.statusLabels[viewRecord.status] }}</dd></div>
        <div><dt>提交时间</dt><dd>{{ d.date(viewRecord.created_at) }}</dd></div>
        <div><dt>评价</dt><dd>{{ viewRecord.evaluation || '—' }}</dd></div>
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
