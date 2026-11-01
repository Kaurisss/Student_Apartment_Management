<script setup>
import {
  PhBed, PhBuildings, PhChartBar, PhDoorOpen, PhHouseLine,
  PhSignOut, PhStudent, PhUsers, PhWrench, PhX
} from '@phosphor-icons/vue'

defineProps({
  user: { type: Object, required: true },
  current: { type: String, required: true },
  drawer: { type: Boolean, default: false }
})
const emit = defineEmits(['navigate', 'logout', 'close'])
</script>

<template>
  <nav class="navigation" aria-label="主导航">
    <div class="navigation-brand">
      <span class="brand-mark"><PhBuildings :size="24" weight="duotone" /></span>
      <div class="brand-text"><strong>学生公寓</strong><span>宿舍管理系统</span></div>
      <a-button v-if="drawer" type="text" class="drawer-close" aria-label="关闭导航" @click="emit('close')">
        <template #icon><PhX :size="19" /></template>
      </a-button>
    </div>

    <div class="navigation-label">工作台</div>
    <a-menu mode="inline" :selected-keys="[current]" @click="({key}) => emit('navigate', key)">
      <a-menu-item v-if="user.admin" key="statistics"><template #icon><PhChartBar :size="19" /></template>数据概览</a-menu-item>
      <a-menu-item key="repairs"><template #icon><PhWrench :size="19" /></template>报修管理</a-menu-item>
      <a-menu-item key="housing"><template #icon><PhDoorOpen :size="19" /></template>入住与退宿</a-menu-item>
      <a-menu-item-group v-if="user.admin" title="基础信息">
        <a-menu-item key="students"><template #icon><PhStudent :size="19" /></template>学生管理</a-menu-item>
        <a-menu-item key="dormitories"><template #icon><PhHouseLine :size="19" /></template>宿舍管理</a-menu-item>
        <a-menu-item key="beds"><template #icon><PhBed :size="19" /></template>床位管理</a-menu-item>
        <a-menu-item key="users"><template #icon><PhUsers :size="19" /></template>账号管理</a-menu-item>
      </a-menu-item-group>
    </a-menu>

    <div class="navigation-footer">
      <a-avatar class="user-avatar" :size="38">{{ user.username.charAt(0).toUpperCase() }}</a-avatar>
      <div class="user-details"><strong>{{ user.username }}</strong><span>{{ user.admin ? '管理员' : '学生' }}</span></div>
      <a-button type="text" class="logout-button" aria-label="退出登录" title="退出登录" @click="emit('logout')">
        <template #icon><PhSignOut :size="19" /></template>
      </a-button>
    </div>
  </nav>
</template>
