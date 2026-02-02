<script setup lang="ts">
import Card from 'primevue/card'
import Button from 'primevue/button'
import { computed, onMounted, ref } from 'vue'
import { api } from '@/ApiInstance'
import type { CloudSpaceInfo } from '@/__generated/model/static'

const props = defineProps<{
  accountId: number
}>()

const spaceInfo = ref<CloudSpaceInfo | null>(null)
const loading = ref(false)

async function loadSpaceInfo() {
  if (!props.accountId) return
  loading.value = true
  try {
    spaceInfo.value = await api.cloudController.getCloudSpace({
      accountId: props.accountId,
    })
  } catch (e) {
    console.error('加载云端空间失败:', e)
  } finally {
    loading.value = false
  }
}

function formatSize(bytes: number) {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + ' ' + sizes[i]
}

// 计算分段数据，用于显示进度条
const segmentData = computed(() => {
  if (!spaceInfo.value) return []

  const segments: Array<{
    key: string
    label: string
    size: number
    percent: number
    color: string
  }> = []

  const total = spaceInfo.value.totalQuota
  const usedDetail = spaceInfo.value.usedDetail || {}

  // 定义颜色映射
  const colorMap: Record<string, string> = {
    GalleryImage: 'bg-blue-500',
    Recorder: 'bg-purple-500',
    Creation: 'bg-pink-500',
    AppList: 'bg-amber-500',
    Drive: 'bg-emerald-500',
  }

  // 按大小排序，从大到小
  const sortedEntries = Object.entries(usedDetail).sort(
    (a, b) => (b[1] as { size: number }).size - (a[1] as { size: number }).size,
  )

  sortedEntries.forEach(([key, item]) => {
    const usageItem = item as { size: number; text: string }
    if (usageItem.size > 0) {
      segments.push({
        key,
        label: usageItem.text,
        size: usageItem.size,
        percent: (usageItem.size / total) * 100,
        color: colorMap[key] || 'bg-slate-500',
      })
    }
  })

  return segments
})

onMounted(() => {
  loadSpaceInfo()
})

defineExpose({
  refresh: loadSpaceInfo,
})
</script>

<template>
  <Card class="overflow-hidden ring-1 ring-slate-200/60">
    <template #title>
      <div class="flex items-center justify-between pb-4">
        <div class="flex items-center gap-3">
          <i class="pi pi-cloud text-sky-500" />
          <span class="font-medium text-slate-700 text-base">云端空间</span>
        </div>
        <Button icon="pi pi-refresh" size="small" :loading="loading" @click="loadSpaceInfo" />
      </div>
    </template>

    <template #content>
      <div v-if="loading && !spaceInfo" class="text-center py-8 text-slate-400">
        <i class="pi pi-spin pi-spinner text-2xl" />
      </div>

      <div v-else-if="spaceInfo" class="space-y-4">
        <!-- 使用率进度条 - 详细分段式 -->
        <div>
          <div class="flex items-center justify-between mb-2">
            <span class="text-sm text-slate-600">空间使用率</span>
            <span
              class="text-lg font-bold font-mono"
              :class="{
                'text-emerald-600': spaceInfo.usagePercent < 75,
                'text-amber-600': spaceInfo.usagePercent >= 75 && spaceInfo.usagePercent < 90,
                'text-red-600': spaceInfo.usagePercent >= 90,
              }"
            >
              {{ spaceInfo.usagePercent }}%
            </span>
          </div>

          <!-- 详细分段式进度条 -->
          <div class="w-full bg-slate-200 dark:bg-slate-700 rounded-full h-4 overflow-hidden flex">
            <div
              v-for="segment in segmentData"
              :key="segment.key"
              class="h-4 transition-all duration-500 flex items-center justify-center"
              :class="segment.color"
              :style="{ width: segment.percent + '%' }"
              :title="`${segment.label}: ${formatSize(segment.size)}`"
            >
              <span
                v-if="segment.percent > 8"
                class="text-[10px] text-white font-medium px-1 truncate"
              >
                {{ segment.label }}
              </span>
            </div>
          </div>

          <!-- 详细图例说明 -->
          <div class="flex items-center justify-between mt-2 text-xs flex-wrap gap-y-2">
            <div class="flex items-center gap-3 flex-wrap">
              <div
                v-for="segment in segmentData"
                :key="segment.key"
                class="flex items-center gap-1"
              >
                <div class="w-3 h-3 rounded" :class="segment.color"></div>
                <span class="text-slate-600"
                  >{{ segment.label }} {{ formatSize(segment.size) }}</span
                >
              </div>
              <div class="flex items-center gap-1">
                <div class="w-3 h-3 rounded bg-slate-200 dark:bg-slate-700"></div>
                <span class="text-slate-600"
                  >剩余 {{ formatSize(spaceInfo.totalQuota - spaceInfo.used) }}</span
                >
              </div>
            </div>
            <div class="text-slate-600 font-medium">
              总空间 {{ formatSize(spaceInfo.totalQuota) }}
            </div>
          </div>
        </div>
      </div>

      <div v-else class="text-center py-8 text-slate-400">
        <i class="pi pi-cloud-download text-4xl mb-2" />
        <p class="text-sm">无法加载云端空间信息</p>
      </div>
    </template>
  </Card>
</template>
