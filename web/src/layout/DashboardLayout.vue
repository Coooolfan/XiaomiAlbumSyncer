<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import Button from 'primevue/button'
import Chip from 'primevue/chip'
import { api } from '../ApiInstance'
import type { SystemInfoResponse } from '../__generated/model/static'

const route = useRoute()
const loggingOut = ref(false)
const systemInfo = ref<SystemInfoResponse | null>(null)

const tabs = [
  { label: '计划', to: '/dashboard/schedule' },
  { label: '设置', to: '/dashboard/setting' },
] as const

onMounted(async () => {
  try {
    systemInfo.value = await api.systemConfigController.getSystemInfo()
  } catch (error) {
    console.error('Failed to load system info:', error)
  }
})

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
  } catch (error) {
    console.error('Failed to log out:', error)
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
            <div class="h-8 w-8 flex items-center justify-center">
              <img src="/logo.avif" alt="Logo" class="w-full h-full object-contain" />
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
          <div v-if="systemInfo" class="hidden md:flex items-center gap-2 text-xs text-slate-500">
            <Chip :label="`v${systemInfo.appVersion}`" class="!text-xs !py-1 !px-2 bg-slate-100" />
            <span class="text-slate-300">|</span>
            <span v-if="systemInfo.nativeImage" class="font-medium text-emerald-600"> Native </span>
            <span v-else-if="systemInfo.aotRuntime" class="font-medium text-blue-600"> AOT </span>
            <span v-else class="font-medium text-amber-600"> JVM {{ systemInfo.jvmVersion }} </span>
            <span class="text-slate-300">|</span>
            <a href="https://github.com/coooolfan/xiaomialbumsyncer" target="_blank"
              ><i class="pi pi-github text-black"
            /></a>
          </div>

          <Button
            label="退出登录"
            severity="secondary"
            class="py-2! px-3!"
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

    <!-- Footer -->
    <footer class="my-2 text-center text-xs text-slate-500">
      <p>
        {{ new Date().getFullYear() }} Xiaomi Album Syncer ©
        <a href="https://github.com/coooolfan/xiaomialbumsyncer" target="_blank"
          >github.com/coooolfan/xiaomialbumsyncer</a
        >
      </p>
    </footer>
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
