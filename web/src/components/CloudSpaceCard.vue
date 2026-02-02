<script setup lang="ts">
import Card from 'primevue/card'
import Button from 'primevue/button'
import { onMounted, ref } from 'vue'
import { api } from '@/ApiInstance'
import type { CloudSpaceInfo } from '@/__generated/services/CloudController'

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

function getUsageColor(percent: number) {
  if (percent >= 90) return 'bg-red-500'
  if (percent >= 75) return 'bg-amber-500'
  return 'bg-emerald-500'
}

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
        <!-- 使用率进度条 -->
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
          <div class="w-full bg-slate-200 dark:bg-slate-700 rounded-full h-3">
            <div
              class="h-3 rounded-full transition-all duration-500"
              :class="getUsageColor(spaceInfo.usagePercent)"
              :style="{ width: spaceInfo.usagePercent + '%' }"
            ></div>
          </div>
        </div>

        <!-- 空间详情 -->
        <div class="rounded-md bg-slate-50 dark:bg-slate-900/30 ring-1 ring-slate-200/60 p-3">
          <div class="space-y-2">
            <div class="flex items-center justify-between">
              <span class="text-sm text-slate-600">总容量</span>
              <span class="text-sm font-mono text-slate-700">{{
                formatSize(spaceInfo.totalQuota)
              }}</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-sm text-slate-600">已使用</span>
              <span class="text-sm font-mono text-slate-700">{{ formatSize(spaceInfo.used) }}</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-sm text-slate-600">相册占用</span>
              <span class="text-sm font-mono text-blue-600">{{
                formatSize(spaceInfo.galleryUsed)
              }}</span>
            </div>
            <div class="flex items-center justify-between pt-2 border-t border-slate-200">
              <span class="text-sm font-medium text-slate-700">剩余空间</span>
              <span class="text-sm font-mono font-bold text-emerald-600">{{
                formatSize(spaceInfo.totalQuota - spaceInfo.used)
              }}</span>
            </div>
          </div>
        </div>

        <!-- 空间警告 -->
        <div
          v-if="spaceInfo.usagePercent >= 90"
          class="rounded-md bg-red-50 dark:bg-red-900/20 ring-1 ring-red-200/60 p-3"
        >
          <div class="flex items-start gap-2">
            <i class="pi pi-exclamation-triangle text-red-500 mt-0.5" />
            <div class="flex-1">
              <p class="text-sm font-medium text-red-700">空间不足警告</p>
              <p class="text-xs text-red-600 mt-1">
                云端空间使用率已达 {{ spaceInfo.usagePercent }}%，建议尽快执行归档操作释放空间。
              </p>
            </div>
          </div>
        </div>
        <div
          v-else-if="spaceInfo.usagePercent >= 75"
          class="rounded-md bg-amber-50 dark:bg-amber-900/20 ring-1 ring-amber-200/60 p-3"
        >
          <div class="flex items-start gap-2">
            <i class="pi pi-info-circle text-amber-500 mt-0.5" />
            <div class="flex-1">
              <p class="text-sm font-medium text-amber-700">空间提示</p>
              <p class="text-xs text-amber-600 mt-1">
                云端空间使用率已达 {{ spaceInfo.usagePercent }}%，可以考虑执行归档操作。
              </p>
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
