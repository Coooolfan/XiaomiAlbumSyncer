<script setup lang="ts">
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import Button from 'primevue/button'
import { api } from '../ApiInstance'

const route = useRoute()
const loggingOut = ref(false)

const tabs = [
  { label: '计划', to: '/dashboard/schedule' },
  { label: '设置', to: '/dashboard/setting' },
]

function linkClasses(to: string) {
  const active = route.path === to || route.path.startsWith(to + '/')
  return [
    'px-3 py-1.5 text-sm rounded-md border transition-colors',
    active
      ? 'bg-blue-50 text-blue-600 border-blue-200'
      : 'text-slate-600 border-transparent hover:bg-slate-50',
  ].join(' ')
}

async function logout() {
  loggingOut.value = true
  try {
    await api.tokenController.logout()
  } catch {
    // 忽略错误，仍然跳转
  } finally {
    loggingOut.value = false
    window.location.href = '/'
  }
}
</script>
<template>
  <div class="min-h-screen">
    <!-- Topbar -->
    <header class="sticky top-0 z-10 backdrop-blur-3xl bg-white/70 border-b border-slate-200/60">
      <div class="max-w-7xl mx-auto px-4 py-3 flex items-center justify-between">
        <div class="flex items-center gap-4">
          <div class="flex items-center gap-2">
            <div
              class="h-8 w-8 rounded-lg bg-blue-600 text-white flex items-center justify-center font-bold"
            >
              X
            </div>
            <div class="font-semibold tracking-tight text-slate-700">Xiaomi Album Syncer</div>
          </div>

          <!-- Nav tabs -->
          <nav class="hidden sm:flex items-center gap-1">
            <RouterLink :to="tabs[0].to" :class="linkClasses(tabs[0].to)">{{
              tabs[0].label
            }}</RouterLink>
            <RouterLink :to="tabs[1].to" :class="linkClasses(tabs[1].to)">{{
              tabs[1].label
            }}</RouterLink>
          </nav>
        </div>

        <div class="flex items-center gap-3">
          <Button
            label="退出登录"
            severity="secondary"
            class="!py-2 !px-3"
            :loading="loggingOut"
            @click="logout"
          />
        </div>
      </div>
    </header>

    <!-- Content -->
    <main class="max-w-7xl mx-auto px-4 py-8">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
/* 细微的悬浮态与过渡，增强现代感 */
:deep(.p-button) {
  transition:
    transform 120ms ease,
    box-shadow 120ms ease;
}
:deep(.p-button:hover) {
  transform: translateY(-0.5px);
  box-shadow: 0 8px 30px -12px rgba(2, 6, 23, 0.2);
}
</style>
