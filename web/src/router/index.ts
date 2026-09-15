import { createRouter, createWebHashHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { i18n } from '@/i18n'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'auth',
    component: () => import('../pages/AuthPage.vue'),
    meta: { guestOnly: true, titleKey: 'meta.title.auth' },
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
        meta: { requiresAuth: true, titleKey: 'meta.title.settings' },
      },
      {
        path: 'schedule',
        name: 'dashboard-schedule',
        component: () => import('../pages/DashboardSchedulePage.vue'),
        meta: { requiresAuth: true, titleKey: 'meta.title.schedule' },
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
  const titleKey = to.meta?.titleKey
  if (typeof titleKey === 'string') document.title = i18n.global.t(titleKey)
})

export default router
