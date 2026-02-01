import { createRouter, createWebHashHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'auth',
    component: () => import('../pages/AuthPage.vue'),
    meta: { guestOnly: true, title: '登录 / 注册' },
  },
  {
    path: '/dashboard',
    component: () => import('../layout/DashboardLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '', name: 'dashboard', redirect: { name: 'dashboard-setting' } },
      {
        path: 'setting',
        name: 'dashboard-setting',
        component: () => import('../pages/DashboardSettingPage.vue'),
        meta: { requiresAuth: true, title: '设置' },
      },
      {
        path: 'schedule',
        name: 'dashboard-schedule',
        component: () => import('../pages/DashboardSchedulePage.vue'),
        meta: { requiresAuth: true, title: '计划' },
      },
      {
        path: 'sync-archive',
        name: 'dashboard-sync-archive',
        component: () => import('../pages/DashboardSyncArchivePage.vue'),
        meta: { requiresAuth: true, title: '同步与归档' },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/',
  },
]

const router = createRouter({
  history: createWebHashHistory(import.meta.env.BASE_URL),
  routes,
})

router.afterEach((to) => {
  const title = to.meta?.title
  if (title) document.title = String(title)
})

export default router
