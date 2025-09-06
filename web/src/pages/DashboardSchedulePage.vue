<script setup lang="ts">
import { ref, onMounted } from 'vue'
import Card from 'primevue/card'
import ContributionHeatmap from '@/components/ContributionHeatmap.vue'

type DataPoint = { timeStamp: number; count: number }

const labelText = ref('一年活跃度')
const weekStartNum = ref(1)
const rangeDaysNum = ref(365)
const endDateStr = ref(formatDateInput(new Date()))
const dataPoints = ref<DataPoint[]>([])
const tip = ref('')
let tipHideTimer: number | undefined
function onDayClick(payload: {
  date: Date
  dateStr: string
  count: number
  level: 0 | 1 | 2 | 3 | 4
}) {
  tip.value = `${payload.dateStr} · ${payload.count}`
  if (tipHideTimer) window.clearTimeout(tipHideTimer)
  tipHideTimer = window.setTimeout(() => {
    tip.value = ''
  }, 2500)
}

function formatDateInput(d: Date) {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function generateRandomData() {
  const end = new Date(endDateStr.value)
  end.setHours(0, 0, 0, 0)
  const start = new Date(end.getTime() - (Math.max(1, rangeDaysNum.value) - 1) * 24 * 3600 * 1000)
  const arr: DataPoint[] = []
  const cur = new Date(start)
  while (cur <= end) {
    if (Math.random() < 0.45) {
      arr.push({
        timeStamp: cur.getTime(),
        count: Math.floor(Math.random() * 10) + 1,
      })
    }
    cur.setDate(cur.getDate() + 1)
  }
  dataPoints.value = arr
}

function refresh() {
  generateRandomData()
}

onMounted(() => {
  generateRandomData()
})

const weekOptions = [
  { value: 1, label: '周一' },
  { value: 0, label: '周日' },
  { value: 2, label: '周二' },
  { value: 3, label: '周三' },
  { value: 4, label: '周四' },
  { value: 5, label: '周五' },
  { value: 6, label: '周六' },
]
</script>

<template>
  <div class="max-w-5xl mx-auto px-4 py-8">
    <Card class="overflow-hidden shadow-sm ring-1 ring-slate-200/60 mb-6">
      <template #content>
        <div class="w-full overflow-x-hidden">
          <ContributionHeatmap
            :data="dataPoints"
            :label="labelText"
            :week-start="weekStartNum"
            :range-days="rangeDaysNum"
            :end="endDateStr"
            @day-click="onDayClick"
          />
          <div
            v-if="tip"
            class="mt-3 inline-flex items-center rounded-md bg-slate-900/80 text-white text-xs px-2 py-1 shadow-md dark:bg-slate-100/10 dark:text-slate-100"
          >
            {{ tip }}
          </div>
        </div>
      </template>
    </Card>

    <Card class="overflow-hidden shadow-sm ring-1 ring-slate-200/60">
      <template #title>计划 · 热力图演示</template>
      <template #content>
        <div
          class="space-y-4 p-4 rounded-lg bg-white/60 dark:bg-slate-900/30 ring-1 ring-slate-200/60"
        >
          <div class="space-y-2">
            <label class="block text-xs font-medium text-slate-500">小标题 label</label>
            <input
              v-model="labelText"
              type="text"
              class="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-emerald-500/50 dark:bg-slate-900 dark:border-slate-700"
              placeholder="例如：一年活跃度"
            />
          </div>

          <div class="grid grid-cols-2 gap-4">
            <div class="space-y-2">
              <label class="block text-xs font-medium text-slate-500">周起始</label>
              <select
                v-model.number="weekStartNum"
                class="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-emerald-500/50 dark:bg-slate-900 dark:border-slate-700"
              >
                <option v-for="opt in weekOptions" :key="opt.value" :value="opt.value">
                  {{ opt.label }}
                </option>
              </select>
            </div>

            <div class="space-y-2">
              <label class="block text-xs font-medium text-slate-500">范围天数</label>
              <input
                v-model.number="rangeDaysNum"
                type="number"
                min="7"
                max="730"
                step="1"
                class="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-emerald-500/50 dark:bg-slate-900 dark:border-slate-700"
              />
            </div>
          </div>

          <div class="space-y-2">
            <label class="block text-xs font-medium text-slate-500">截止日期</label>
            <input
              v-model="endDateStr"
              type="date"
              class="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-emerald-500/50 dark:bg-slate-900 dark:border-slate-700"
            />
          </div>

          <div class="pt-2">
            <button
              type="button"
              class="inline-flex items-center gap-1 rounded-md bg-emerald-600 px-3 py-2 text-xs font-medium text-white hover:bg-emerald-700 active:bg-emerald-800"
              @click="refresh"
            >
              随机刷新数据
            </button>
          </div>
        </div>
      </template>
    </Card>
  </div>
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
