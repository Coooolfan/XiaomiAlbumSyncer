<script setup lang="ts">
import { computed } from 'vue'

type Props = {
  name?: string
  assetCount?: number
  lastUpdateTime?: string
}

const props = defineProps<Props>()

const displayName = computed(() => props.name ?? '未命名相册')
const displayCount = computed(() => {
  if (props.assetCount === 0 && props.name === '录音') {
    return '下载前不可见'
  }
  return (props.assetCount ?? 0) + ' 个项目'
})
const displayLastUpdate = computed(() => {
  if (!props.lastUpdateTime) return ''

  try {
    const d = new Date(props.lastUpdateTime)
    if (isNaN(d.getTime())) return props.lastUpdateTime
    const y = d.getFullYear()
    const m = String(d.getMonth() + 1).padStart(2, '0')
    const day = String(d.getDate()).padStart(2, '0')
    const hh = String(d.getHours()).padStart(2, '0')
    const mm = String(d.getMinutes()).padStart(2, '0')
    return `${y}-${m}-${day} ${hh}:${mm}`
  } catch {
    return props.lastUpdateTime
  }
})

const displayRelativeUpdate = computed(() => {
  if (!props.lastUpdateTime) return ''
  const d = new Date(props.lastUpdateTime)
  const now = new Date()
  const diffMs = now.getTime() - d.getTime()
  if (isNaN(diffMs)) return ''
  const sec = Math.floor(diffMs / 1000)
  const min = Math.floor(sec / 60)
  const hour = Math.floor(min / 60)
  const day = Math.floor(hour / 24)
  const month = Math.floor(day / 30)
  const year = Math.floor(day / 365)
  if (sec < 60) return '刚刚'
  if (min < 60) return `${min} 分钟前`
  if (hour < 24) return `${hour} 小时前`
  if (day < 30) return `${day} 天前`
  if (month < 12) return `${month} 个月前`
  return `${year} 年前`
})
</script>

<template>
  <div
    class="group relative w-full max-w-sm rounded-xl border border-slate-200/80 bg-white/70 px-4 py-3 shadow-sm backdrop-blur transition-all hover:bg-white hover:shadow-md dark:border-slate-700/60 dark:bg-slate-900/40 dark:hover:bg-slate-900/55"
  >
    <div class="min-w-0">
      <div class="truncate text-sm font-semibold text-slate-900 dark:text-slate-100">
        {{ displayName }}
      </div>
      <div class="mt-1 flex items-center justify-between">
        <div class="text-xs text-slate-600 dark:text-slate-300">{{ displayCount }}</div>
        <div v-if="displayLastUpdate" class="text-[11px] text-slate-400 dark:text-slate-500">
          上次更新于 {{ displayRelativeUpdate }}
        </div>
      </div>
      <div v-if="displayLastUpdate" class="mt-1 text-[11px] text-slate-400 dark:text-slate-500">
        {{ displayLastUpdate }}
      </div>
    </div>
  </div>
</template>

<style scoped></style>
