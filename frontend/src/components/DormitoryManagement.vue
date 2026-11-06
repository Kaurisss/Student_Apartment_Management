<script setup>
import { computed, inject, reactive, ref } from 'vue'
import { Message } from '@arco-design/web-vue'
import { PhPencilSimple, PhPlus, PhTrash } from '@phosphor-icons/vue'
import TableIconButton from './TableIconButton.vue'

const d = inject('desk')
const batch = reactive({building:'',startRoom:'101',endRoom:'120',roomType:'4人间',createBeds:true})
const addingBuilding = ref(false), saving = ref(false), batchForm = ref(null)
const selected = computed(() => d.buildings.find(group => group.building === d.selectedBuilding))
const columns = [
  {title:'房间号',dataIndex:'room_no'}, {title:'房间类型',dataIndex:'room_type'},
  {title:'床位总数',dataIndex:'beds'}, {title:'空闲床位',slotName:'free'},
  {title:'已占用',dataIndex:'occupied'}, {title:'操作',slotName:'actions',width:128}
]
const roomCount = computed(() => {
  const start=Number(batch.startRoom), end=Number(batch.endRoom)
  return /^\d{1,5}$/.test(batch.startRoom) && /^\d{1,5}$/.test(batch.endRoom) && start>0 && end>=start && end-start<200 ? end-start+1 : 0
})
function openBatch(newBuilding) {
  addingBuilding.value = newBuilding
  Object.assign(batch,{building:newBuilding?'':d.selectedBuilding,startRoom:newBuilding?'101':'',endRoom:newBuilding?'120':'',roomType:'4人间',createBeds:true})
  d.showRoomBatch = true
}
async function saveBatch() {
  if (await batchForm.value.validate()) return false
  if (!roomCount.value) { Message.warning('请填写有效房号范围，每次最多添加200间'); return false }
  if (addingBuilding.value && d.buildings.some(group => group.building === batch.building.trim())) { Message.warning('楼栋已存在，请在该楼栋内批量添加房间'); return false }
  saving.value = true
  try {
    const created = await d.request('/admin/dormitories/batch',{method:'POST',data:batch})
    d.selectedBuilding = batch.building.trim()
    await d.selectBuilding(d.selectedBuilding)
    Message.success(`已添加${created.rooms}间房间、${created.beds}个床位`)
    return true
  } catch (error) { Message.error(error.message); return false }
  finally { saving.value = false }
}
</script>

<template>
  <div class="page-header">
    <h1>宿舍管理</h1>
    <div class="header-actions">
      <a-button :type="d.buildings.length ? 'secondary' : 'primary'" @click="openBatch(true)"><template #icon><PhPlus :size="16" /></template>新增楼栋</a-button>
      <a-button v-if="d.selectedBuilding" type="primary" @click="d.openEditor()"><template #icon><PhPlus :size="16" /></template>添加房间</a-button>
    </div>
  </div>
  <div v-if="d.buildings.length" class="dormitory-layout">
    <nav class="building-list" aria-label="楼栋列表">
      <button v-for="group in d.buildings" :key="group.building" type="button" class="building-choice" :aria-current="group.building === d.selectedBuilding ? 'true' : undefined" @click="d.selectBuilding(group.building)">
        <strong>{{ group.building }}</strong>
        <span>{{ group.rooms }}间房间 / {{ group.beds }}个床位</span>
      </button>
    </nav>
    <section class="building-rooms" :aria-label="d.selectedBuilding + '房间'">
      <div class="building-toolbar">
        <div><h2>{{ d.selectedBuilding }}</h2><p>{{ selected?.rooms }}间房间，{{ Number(selected?.beds) - Number(selected?.occupied) }}个空闲床位</p></div>
        <a-button @click="openBatch(false)">批量添加房间</a-button>
      </div>
      <a-card class="section table-card" :bordered="false">
        <a-table :columns="columns" :data="d.result.rows" :pagination="d.pagination" :loading="d.busy" row-key="id" :scroll="{x:'max-content'}" @page-change="d.changePage">
          <template #free="{record}">{{ Number(record.beds) - Number(record.occupied) }}</template>
          <template #actions="{record}"><div class="actions"><TableIconButton label="编辑" @click="d.openEditor(record)"><PhPencilSimple /></TableIconButton><TableIconButton label="删除" danger @click="d.remove(record)"><PhTrash /></TableIconButton></div></template>
        </a-table>
      </a-card>
    </section>
  </div>
  <a-empty v-else-if="!d.busy" description="暂无楼栋" />
  <a-skeleton v-else animation />
  <a-modal v-model:visible="d.showRoomBatch" :title="addingBuilding ? '新增楼栋' : '批量添加房间'" ok-text="添加" cancel-text="取消" :ok-loading="saving" :on-before-ok="saveBatch" unmount-on-close>
    <a-form ref="batchForm" :model="batch" layout="vertical">
      <a-form-item field="building" label="楼栋名称" :rules="[{required:true,message:'请填写楼栋名称'}]">
        <a-input v-model="batch.building" :readonly="!addingBuilding" :max-length="50" :input-attrs="{'aria-label':'楼栋名称'}" placeholder="如：1号楼" />
      </a-form-item>
      <div class="room-range">
        <a-form-item field="startRoom" label="起始房号" :rules="[{required:true,message:'请填写起始房号'}]"><a-input v-model="batch.startRoom" :max-length="5" :input-attrs="{'aria-label':'起始房号',inputmode:'numeric'}" /></a-form-item>
        <a-form-item field="endRoom" label="结束房号" :rules="[{required:true,message:'请填写结束房号'}]"><a-input v-model="batch.endRoom" :max-length="5" :input-attrs="{'aria-label':'结束房号',inputmode:'numeric'}" /></a-form-item>
      </div>
      <a-form-item label="房间类型"><a-radio-group v-model="batch.roomType" :options="['4人间','6人间']" /></a-form-item>
      <a-form-item><a-checkbox v-model="batch.createBeds">同时创建床位</a-checkbox></a-form-item>
      <p class="batch-preview" aria-live="polite">{{ roomCount }}间房间，{{ batch.createBeds ? roomCount * (batch.roomType === '4人间' ? 4 : 6) : 0 }}个新床位</p>
    </a-form>
  </a-modal>
</template>
