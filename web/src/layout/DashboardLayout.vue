<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { api } from '../ApiInstance'
import type { SystemInfoResponse } from '@/__generated/model/static'
import {
  DEFAULT_SETTING_SECTION,
  SETTING_SECTION_GROUPS,
  SETTING_SECTION_KEYS,
} from '@/utils/settingSections'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const loggingOut = ref(false)
const systemInfo = ref<SystemInfoResponse | null>(null)

const navItems = computed(() => [
  { label: t('nav.schedule'), to: '/dashboard/schedule', icon: 'pi pi-calendar' },
  { label: t('nav.settings'), to: '/dashboard/setting', icon: 'pi pi-cog' },
])

const isSettingsRoute = computed(() => isActive('/dashboard/setting'))

const activeSettingSection = computed(() => {
  const key = String(route.query.section ?? '')
  return SETTING_SECTION_KEYS.has(key) ? key : DEFAULT_SETTING_SECTION
})

function selectSettingSection(key: string) {
  if (key !== activeSettingSection.value) {
    router.replace({ query: { ...route.query, section: key } })
  }
}

onMounted(async () => {
  try {
    systemInfo.value = await api.systemConfigController.getSystemInfo()
  } catch (error) {
    console.error('Failed to load system info:', error)
  }
})

function isActive(to: string) {
  return route.path === to || route.path.startsWith(to + '/')
}

function navItemClasses(to: string) {
  const active = isActive(to)
  return [
    'group flex items-center gap-2.5 rounded-md px-2.5 py-1.5 text-[13px] transition-colors',
    active
      ? 'bg-slate-200/70 text-slate-900 dark:bg-slate-700/60 dark:text-slate-100 font-medium'
      : 'text-slate-500 dark:text-slate-400 hover:bg-slate-200/50 hover:text-slate-800 dark:hover:bg-slate-800/60 dark:hover:text-slate-200',
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

const versionTag = computed(() => {
  const version = systemInfo.value?.appVersion
  if (!version) return ''
  return version === 'dev' ? version : `v${version}`
})

const runtimeLabel = computed(() => {
  if (!systemInfo.value) return ''
  if (systemInfo.value.nativeImage) return 'Native'
  if (systemInfo.value.aotRuntime) return 'AOT'
  return `JVM ${systemInfo.value.jvmVersion}`
})
</script>

<template>
  <div class="min-h-screen md:flex">
    <!-- Sidebar (desktop) -->
    <aside
      class="hidden md:flex md:w-60 md:shrink-0 md:flex-col md:sticky md:top-0 md:h-screen border-r border-slate-200/80 dark:border-slate-800/80 bg-white/60 dark:bg-slate-900/50 backdrop-blur-xl"
    >
      <div class="flex items-center gap-2.5 px-4 pt-5 pb-4">
        <div class="h-6 w-6 shrink-0">
          <img src="/logo.avif" alt="Logo" class="h-full w-full object-contain" />
        </div>
        <div
          class="truncate text-[13px] font-semibold tracking-tight text-slate-800 dark:text-slate-100"
        >
          Xiaomi Album Syncer
        </div>
      </div>

      <nav class="flex-1 overflow-y-auto px-3 pb-4">
        <div
          class="px-2 pb-1.5 text-[11px] font-medium uppercase tracking-wider text-slate-400 dark:text-slate-500"
        >
          {{ t('nav.console') }}
        </div>
        <div class="space-y-0.5">
          <template v-for="item in navItems" :key="item.to">
            <RouterLink :to="item.to" :class="navItemClasses(item.to)">
              <i :class="[item.icon, 'text-[13px]']" />
              <span class="truncate">{{ item.label }}</span>
            </RouterLink>

            <!-- 设置页子目录 -->
            <div
              v-if="item.to === '/dashboard/setting' && isSettingsRoute"
              class="mb-2 ml-3 space-y-2.5 border-l border-slate-200/70 pl-2 dark:border-slate-700/60"
            >
              <div v-for="group in SETTING_SECTION_GROUPS" :key="group.labelKey">
                <div
                  class="px-2 pb-1 pt-1.5 text-[10px] font-medium uppercase tracking-wider text-slate-400 dark:text-slate-500"
                >
                  {{ t(group.labelKey) }}
                </div>
                <div class="space-y-0.5">
                  <button
                    v-for="section in group.items"
                    :key="section.key"
                    type="button"
                    class="flex w-full items-center gap-2 rounded-md px-2 py-1 text-xs transition-colors"
                    :class="
                      section.key === activeSettingSection
                        ? 'bg-slate-200/70 text-slate-900 dark:bg-slate-700/60 dark:text-slate-100 font-medium'
                        : 'text-slate-500 dark:text-slate-400 hover:bg-slate-200/50 hover:text-slate-800 dark:hover:bg-slate-800/60 dark:hover:text-slate-200'
                    "
                    @click="selectSettingSection(section.key)"
                  >
                    <i :class="[section.icon, 'text-[11px]']" />
                    <span class="truncate">{{ t(section.labelKey) }}</span>
                  </button>
                </div>
              </div>
            </div>
          </template>
        </div>
      </nav>

      <div class="px-3 pb-2">
        <button
          type="button"
          class="flex w-full items-center gap-2.5 rounded-md px-2.5 py-1.5 text-[13px] text-slate-500 dark:text-slate-400 transition-colors hover:bg-slate-200/50 hover:text-slate-800 dark:hover:bg-slate-800/60 dark:hover:text-slate-200 disabled:opacity-50"
          :disabled="loggingOut"
          @click="logout"
        >
          <i :class="loggingOut ? 'pi pi-spin pi-spinner' : 'pi pi-sign-out'" class="text-[13px]" />
          <span>{{ t('nav.logout') }}</span>
        </button>
      </div>

      <div class="border-t border-slate-200/80 dark:border-slate-800/80 px-3 py-3">
        <div
          v-if="systemInfo"
          class="flex items-center gap-2 px-2 text-[11px] text-slate-400 dark:text-slate-500"
        >
          <span class="font-mono">{{ versionTag }}</span>
          <span class="text-slate-300 dark:text-slate-600">·</span>
          <span>{{ runtimeLabel }}</span>
          <a
            href="https://github.com/coooolfan/xiaomialbumsyncer"
            target="_blank"
            class="ml-auto text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 transition-colors"
            aria-label="GitHub"
          >
            <i class="pi pi-github text-[13px]" />
          </a>
        </div>
      </div>
    </aside>

    <!-- Mobile top bar -->
    <div class="flex min-h-screen flex-1 flex-col">
      <header
        class="md:hidden sticky top-0 z-10 border-b border-slate-200/80 dark:border-slate-800/80 bg-white/80 dark:bg-slate-900/80 backdrop-blur-xl"
      >
        <div class="flex items-center justify-between px-4 py-2.5">
          <div class="flex items-center gap-2">
            <img src="/logo.avif" alt="Logo" class="h-5 w-5 object-contain" />
            <span class="text-[13px] font-semibold text-slate-800 dark:text-slate-100">XAS</span>
          </div>
          <nav class="flex items-center gap-1">
            <RouterLink
              v-for="item in navItems"
              :key="item.to"
              :to="item.to"
              class="rounded-md px-2.5 py-1 text-[13px] transition-colors"
              :class="
                isActive(item.to)
                  ? 'bg-slate-200/70 text-slate-900 dark:bg-slate-700/60 dark:text-slate-100 font-medium'
                  : 'text-slate-500 dark:text-slate-400 hover:text-slate-800 dark:hover:text-slate-200'
              "
            >
              {{ item.label }}
            </RouterLink>
            <button
              type="button"
              class="ml-1 rounded-md p-1.5 text-slate-500 dark:text-slate-400 hover:bg-slate-200/50 dark:hover:bg-slate-800/60 transition-colors"
              :disabled="loggingOut"
              :aria-label="t('nav.logout')"
              @click="logout"
            >
              <i
                :class="loggingOut ? 'pi pi-spin pi-spinner' : 'pi pi-sign-out'"
                class="text-[13px]"
              />
            </button>
          </nav>
        </div>
      </header>

      <main class="flex-1 min-w-0">
        <router-view />
      </main>
    </div>
  </div>
</template>
