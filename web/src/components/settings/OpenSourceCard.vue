<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import Tag from 'primevue/tag'
import SettingSection from '@/components/settings/SettingSection.vue'
import { api } from '@/ApiInstance'
import type { SystemInfoResponse } from '@/__generated/model/static'

const { t } = useI18n()
const systemInfo = ref<SystemInfoResponse | null>(null)
const repoUrl = 'https://github.com/coooolfan/xiaomialbumsyncer'

onMounted(async () => {
  try {
    systemInfo.value = await api.systemConfigController.getSystemInfo()
  } catch (error) {
    console.error('Failed to load system info:', error)
  }
})

const docsTag = computed(() => {
  const version = systemInfo.value?.appVersion?.trim()
  if (!version || version === 'dev') return 'main'
  return version
})

const docsBaseUrl = computed(() => `${repoUrl}/blob/${docsTag.value}`)

const frontendCore = computed(() => [
  { name: 'Vue 3', desc: t('settings.openSource.desc.frontendFramework'), license: 'MIT' },
  { name: 'Vue Router', desc: t('settings.openSource.desc.routing'), license: 'MIT' },
  { name: 'Pinia', desc: t('settings.openSource.desc.stateManagement'), license: 'MIT' },
])

const uiStack = computed(() => [
  { name: 'PrimeVue', desc: t('settings.openSource.desc.uiComponents'), license: 'MIT' },
  { name: 'PrimeIcons', desc: t('settings.openSource.desc.icons'), license: 'MIT' },
  { name: 'PrimeUIX Themes', desc: t('settings.openSource.desc.themes'), license: 'MIT' },
  { name: 'Tailwind CSS', desc: t('settings.openSource.desc.utilityCss'), license: 'MIT' },
])

const tooling = computed(() => [
  { name: 'Vite (rolldown)', desc: t('settings.openSource.desc.bundler'), license: 'MIT' },
  { name: 'TypeScript', desc: t('settings.openSource.desc.typeSystem'), license: 'Apache-2.0' },
  { name: 'ESLint', desc: t('settings.openSource.desc.linting'), license: 'MIT' },
  { name: 'Prettier', desc: t('settings.openSource.desc.formatting'), license: 'MIT' },
])

const features = computed(() => [
  { name: 'SimpleWebAuthn', desc: t('settings.openSource.desc.passkeys'), license: 'MIT' },
  { name: 'OGL', desc: t('settings.openSource.desc.webgl'), license: 'Unlicense' },
])

const backendCore = computed(() => [
  { name: 'Solon', desc: t('settings.openSource.desc.backendFramework'), license: 'Apache-2.0' },
  { name: 'Sa-Token', desc: t('settings.openSource.desc.auth'), license: 'Apache-2.0' },
  { name: 'Jimmer', desc: t('settings.openSource.desc.orm'), license: 'Apache-2.0' },
  { name: 'Flyway', desc: t('settings.openSource.desc.dbMigrations'), license: 'Apache-2.0' },
  { name: 'OkHttp', desc: t('settings.openSource.desc.httpClient'), license: 'Apache-2.0' },
])
</script>

<template>
  <SettingSection
    :title="t('nav.sections.about')"
    :description="t('settings.openSource.description')"
  >
    <template #actions>
      <Tag value="GPL-3.0" severity="info" />
    </template>
    <p class="text-xs text-slate-500 dark:text-slate-400">
      {{ t('settings.openSource.intro') }}
    </p>

    <div class="mt-4 grid grid-cols-1 sm:grid-cols-2 gap-4">
      <div class="space-y-2">
        <div class="text-sm font-medium text-slate-700 dark:text-slate-200">
          {{ t('settings.openSource.frontendCore') }}
        </div>
        <ul class="text-xs text-slate-500 dark:text-slate-400 list-disc pl-4 space-y-1">
          <li v-for="item in frontendCore" :key="item.name">
            <span class="text-slate-700 dark:text-slate-200">{{ item.name }}</span> ·
            {{ item.desc }} ·
            <span class="text-slate-400 dark:text-slate-500">{{ item.license }}</span>
          </li>
        </ul>
      </div>

      <div class="space-y-2">
        <div class="text-sm font-medium text-slate-700 dark:text-slate-200">
          {{ t('settings.openSource.uiStack') }}
        </div>
        <ul class="text-xs text-slate-500 dark:text-slate-400 list-disc pl-4 space-y-1">
          <li v-for="item in uiStack" :key="item.name">
            <span class="text-slate-700 dark:text-slate-200">{{ item.name }}</span> ·
            {{ item.desc }} ·
            <span class="text-slate-400 dark:text-slate-500">{{ item.license }}</span>
          </li>
        </ul>
      </div>

      <div class="space-y-2">
        <div class="text-sm font-medium text-slate-700 dark:text-slate-200">
          {{ t('settings.openSource.tooling') }}
        </div>
        <ul class="text-xs text-slate-500 dark:text-slate-400 list-disc pl-4 space-y-1">
          <li v-for="item in tooling" :key="item.name">
            <span class="text-slate-700 dark:text-slate-200">{{ item.name }}</span> ·
            {{ item.desc }} ·
            <span class="text-slate-400 dark:text-slate-500">{{ item.license }}</span>
          </li>
        </ul>
      </div>

      <div class="space-y-2">
        <div class="text-sm font-medium text-slate-700 dark:text-slate-200">
          {{ t('settings.openSource.features') }}
        </div>
        <ul class="text-xs text-slate-500 dark:text-slate-400 list-disc pl-4 space-y-1">
          <li v-for="item in features" :key="item.name">
            <span class="text-slate-700 dark:text-slate-200">{{ item.name }}</span> ·
            {{ item.desc }} ·
            <span class="text-slate-400 dark:text-slate-500">{{ item.license }}</span>
          </li>
        </ul>
      </div>
    </div>

    <div class="mt-4 pt-4 border-t border-slate-200/60 dark:border-slate-700/60">
      <div class="text-sm font-medium text-slate-700 dark:text-slate-200">
        {{ t('settings.openSource.backendCore') }}
      </div>
      <ul class="mt-2 text-xs text-slate-500 dark:text-slate-400 list-disc pl-4 space-y-1">
        <li v-for="item in backendCore" :key="item.name">
          <span class="text-slate-700 dark:text-slate-200">{{ item.name }}</span> ·
          {{ item.desc }} ·
          <span class="text-slate-400 dark:text-slate-500">{{ item.license }}</span>
        </li>
      </ul>
      <p class="text-xs text-slate-400 dark:text-slate-500 mt-3">
        {{ t('settings.openSource.depsListLead')
        }}<a
          class="text-blue-600 hover:underline"
          :href="`${docsBaseUrl}/web/README/DEPENDENCIES.md`"
          target="_blank"
          >web/README/DEPENDENCIES.md</a
        >{{ t('settings.openSource.depsListAnd')
        }}<a
          class="text-blue-600 hover:underline"
          :href="`${docsBaseUrl}/server/README/DEPENDENCIES.md`"
          target="_blank"
          >server/README/DEPENDENCIES.md</a
        >{{ t('settings.openSource.depsListTail') }}
      </p>
    </div>
  </SettingSection>
</template>
