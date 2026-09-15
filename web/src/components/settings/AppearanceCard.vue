<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import SelectButton from 'primevue/selectbutton'
import SettingSection from '@/components/settings/SettingSection.vue'
import { storeToRefs } from 'pinia'
import { usePreferencesStore, type ThemeMode } from '@/stores/preferences'
import type { AppLocale } from '@/i18n'

const { t } = useI18n()
const preferencesStore = usePreferencesStore()
const { themeMode, optimizeHeatmap, locale } = storeToRefs(preferencesStore)

const themeOptions = computed<Array<{ label: string; value: ThemeMode }>>(() => [
  { label: t('appearance.themeSystem'), value: 'system' },
  { label: t('appearance.themeLight'), value: 'light' },
  { label: t('appearance.themeDark'), value: 'dark' },
])

const languageOptions: Array<{ label: string; value: AppLocale }> = [
  { label: '中文', value: 'zh-CN' },
  { label: 'English', value: 'en-US' },
]

const heatOptions = computed<Array<{ label: string; value: boolean }>>(() => [
  { label: t('appearance.off'), value: false },
  { label: t('appearance.on'), value: true },
])
</script>

<template>
  <SettingSection :title="t('appearance.title')" :description="t('appearance.description')">
    <div class="divide-y divide-slate-100 dark:divide-slate-800/70">
      <div class="flex items-center justify-between gap-4 py-3 first:pt-0">
        <div class="min-w-0">
          <div class="text-sm text-slate-600 dark:text-slate-300">
            {{ t('appearance.theme') }}
          </div>
          <p class="mt-0.5 text-xs text-slate-400 dark:text-slate-500">
            {{ t('appearance.themeDesc') }}
          </p>
        </div>
        <div class="flex shrink-0 justify-end">
          <SelectButton
            v-model="themeMode"
            :options="themeOptions"
            optionLabel="label"
            optionValue="value"
            :allowEmpty="false"
          />
        </div>
      </div>

      <div class="flex items-center justify-between gap-4 py-3">
        <div class="min-w-0">
          <div class="text-sm text-slate-600 dark:text-slate-300">
            {{ t('appearance.language') }}
          </div>
          <p class="mt-0.5 text-xs text-slate-400 dark:text-slate-500">
            {{ t('appearance.languageDesc') }}
          </p>
        </div>
        <div class="flex shrink-0 justify-end">
          <SelectButton
            v-model="locale"
            :options="languageOptions"
            optionLabel="label"
            optionValue="value"
            :allowEmpty="false"
          />
        </div>
      </div>

      <div class="flex items-center justify-between gap-4 py-3 last:pb-0">
        <div class="min-w-0">
          <div class="text-sm text-slate-600 dark:text-slate-300">
            {{ t('appearance.heatmap') }}
          </div>
          <p class="mt-0.5 text-xs text-slate-400 dark:text-slate-500">
            {{ t('appearance.heatmapDesc') }}
          </p>
        </div>
        <div class="flex shrink-0 justify-end">
          <SelectButton
            v-model="optimizeHeatmap"
            :options="heatOptions"
            optionLabel="label"
            optionValue="value"
            :allowEmpty="false"
          />
        </div>
      </div>
    </div>
  </SettingSection>
</template>
