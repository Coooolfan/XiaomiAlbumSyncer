<script setup lang="ts">
import Card from 'primevue/card'
import SelectButton from 'primevue/selectbutton'
import { storeToRefs } from 'pinia'
import { usePreferencesStore } from '@/stores/preferences'

type BgMode = 'lightRays' | 'silk'

const preferencesStore = usePreferencesStore()
const { backgroundMode, optimizeHeatmap } = storeToRefs(preferencesStore)

const bgOptions: Array<{ label: string; value: BgMode }> = [
  { label: '光束', value: 'lightRays' },
  { label: '丝绸', value: 'silk' },
]

const heatOptions: Array<{ label: string; value: boolean }> = [
  { label: '关闭', value: false },
  { label: '开启', value: true },
]
</script>

<template>
  <Card class="overflow-hidden shadow-sm ring-1 ring-slate-200/60 dark:ring-slate-700/60 mb-6">
    <template #title>外观</template>
    <template #content>
      <div class="flex items-center justify-between">
        <span class="text-sm text-slate-600 dark:text-slate-300">背景</span>
        <div class="min-w-40">
          <SelectButton
            v-model="backgroundMode"
            :options="bgOptions"
            optionLabel="label"
            optionValue="value"
          />
        </div>
      </div>
      <p class="text-xs text-slate-400 dark:text-slate-500 mt-3">
        选择背景效果（单选），偏好将被本地保存。
      </p>

      <div class="mt-4 pt-4 border-t border-slate-200/60 dark:border-slate-700/60">
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2">
            <span class="text-sm text-slate-600 dark:text-slate-300">热力图优化展示</span>
          </div>
          <div class="min-w-40">
            <SelectButton
              v-model="optimizeHeatmap"
              :options="heatOptions"
              optionLabel="label"
              optionValue="value"
            />
          </div>
        </div>
        <p class="text-xs text-slate-400 dark:text-slate-500 mt-3">
          开启后，颜色深度将基于近似上界（95%分位）映射，弱化极端离群值影响；偏好将被本地保存。
        </p>
      </div>
    </template>
  </Card>
</template>

<style scoped>
:deep(.p-card) {
  transition:
    transform 180ms ease,
    box-shadow 180ms ease;
}
:deep(.p-card:hover) {
  transform: translateY(-1px);
  box-shadow: 0 8px 30px -12px rgba(2, 6, 23, 0.2);
}
</style>
