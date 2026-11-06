import { createRouter, createWebHistory } from 'vue-router'
import Page from './views/Page.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: Page },
    { path: '/dashboard', name: 'statistics', component: Page },
    { path: '/repairs', name: 'repairs', component: Page },
    { path: '/housing', name: 'housing', component: Page },
    { path: '/profile', name: 'profile', component: Page },
    { path: '/students', name: 'students', component: Page },
    { path: '/dormitories', name: 'dormitories', component: Page },
    { path: '/beds', name: 'beds', component: Page },
    { path: '/users', name: 'users', component: Page },
    { path: '/visitors', name: 'visitors', component: Page }
  ]
})
