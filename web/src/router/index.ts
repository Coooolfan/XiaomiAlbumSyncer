import { createRouter, createWebHistory } from 'vue-router'
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
    name: 'dashboard',
    component: () => import('../layout/DashboardLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '', redirect: '/dashboard/setting' },
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
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/',
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

router.afterEach((to) => {
  const title = to.meta?.title
  if (title) document.title = String(title)
})

export default router
