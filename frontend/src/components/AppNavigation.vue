<script setup>
import {
  PhBed, PhChartBar, PhDoorOpen, PhHouseLine,
  PhSignOut, PhStudent, PhUser, PhUsers, PhWrench, PhX
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
      <span class="brand-mark" aria-hidden="true"></span>
      <div class="brand-text"><strong>学生公寓</strong><span>管理系统</span></div>
      <a-button v-if="drawer" type="text" class="drawer-close" aria-label="关闭导航" @click="emit('close')">
        <template #icon><PhX :size="18" /></template>
      </a-button>
    </div>

    <div class="navigation-scroll">
      <div class="navigation-label">工作台</div>
      <a-menu :selected-keys="[current]" @menu-item-click="key => emit('navigate', key)">
        <a-menu-item v-if="user.admin" key="statistics" role="menuitem">
          <template #icon><PhChartBar :size="18" /></template>数据概览
        </a-menu-item>
        <a-menu-item v-if="!user.admin" key="profile" role="menuitem">
          <template #icon><PhUser :size="18" /></template>个人信息
        </a-menu-item>
        <a-menu-item key="repairs" role="menuitem">
          <template #icon><PhWrench :size="18" /></template>报修管理
        </a-menu-item>
        <a-menu-item key="housing" role="menuitem">
          <template #icon><PhDoorOpen :size="18" /></template>入住与退宿
        </a-menu-item>
      </a-menu>
      <template v-if="user.admin">
        <div class="navigation-label">基础信息</div>
        <a-menu :selected-keys="[current]" @menu-item-click="key => emit('navigate', key)">
          <a-menu-item key="students" role="menuitem">
            <template #icon><PhStudent :size="18" /></template>学生管理
          </a-menu-item>
          <a-menu-item key="dormitories" role="menuitem">
            <template #icon><PhHouseLine :size="18" /></template>宿舍管理
          </a-menu-item>
          <a-menu-item key="beds" role="menuitem">
            <template #icon><PhBed :size="18" /></template>床位管理
          </a-menu-item>
          <a-menu-item key="users" role="menuitem">
            <template #icon><PhUsers :size="18" /></template>账号管理
          </a-menu-item>
        </a-menu>
      </template>
    </div>

    <div class="navigation-footer">
      <a-avatar class="user-avatar" :size="32" :image-url="`https://api.dicebear.com/9.x/initials/svg?seed=${encodeURIComponent(user.username)}&backgroundColor=f2f3f5&textColor=1d2129`" />
      <div class="user-details"><strong>{{ user.username }}</strong><span>{{ user.admin ? '管理员' : '学生' }}</span></div>
      <a-button type="text" class="logout-button" aria-label="退出登录" title="退出登录" @click="emit('logout')">
        <template #icon><PhSignOut :size="18" /></template>
      </a-button>
    </div>
  </nav>
</template>
