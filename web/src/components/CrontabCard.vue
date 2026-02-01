<script setup lang="ts">
import Card from 'primevue/card'
import Button from 'primevue/button'
import Chip from 'primevue/chip'
import ToggleSwitch from 'primevue/toggleswitch'
import Tag from 'primevue/tag'
import SplitButton from 'primevue/splitbutton'
import type { CrontabDto } from '@/__generated/model/dto'
import type { CrontabCurrentStats } from '@/__generated/model/static'
import { computed, onUnmounted, ref, watch } from 'vue'
import { api } from '@/ApiInstance'

type Crontab = CrontabDto['CrontabController/DEFAULT_CRONTAB']

const props = defineProps<{
  crontab: Crontab
  albumOptions: ReadonlyArray<{ label: string; value: string }>
  busy?: boolean
}>()

const emit = defineEmits<{
  (e: 'edit'): void
  (e: 'delete'): void
  (e: 'toggle'): void
  (e: 'execute'): void
  (e: 'executeExif'): void
  (e: 'executeRewriteFsTime'): void
  (e: 'refresh'): void
}>()

const albumMap = computed<Record<string, string>>(() => {
  const map: Record<string, string> = {}
  for (const opt of props.albumOptions || []) {
    map[opt.value] = opt.label
  }
  return map
})

function formatTime(t?: string) {
  if (!t) return '-'
  try {
    const d = new Date(t)
    if (Number.isNaN(d.getTime())) return t
    return d.toLocaleString()
  } catch {
    return t
  }
}

const recentHistories = computed(() => {
  const list = [...(props.crontab.histories || [])]
  list.sort((a, b) => (a.startTime < b.startTime ? 1 : -1))
  return list.slice(0, 5)
})

const manualActionOptions = computed(() => {
  const actions: Array<{ label: string; icon: string; command: () => void }> = []
  if (props.crontab.running) {
    // If running, maybe disable execute?
    // But user might want to force execute? Let's leave it.
  }
  if (props.crontab.config?.rewriteExifTime) {
    actions.push({
      label: '填充 EXIF 时间',
      icon: 'pi pi-clock',
      command: () => emit('executeExif'),
    })
  }
  if (props.crontab.config?.rewriteFileSystemTime) {
    actions.push({
      label: '重写文件系统时间',
      icon: 'pi pi-history',
      command: () => emit('executeRewriteFsTime'),
    })
  }
  return actions
})

const currentStats = ref<CrontabCurrentStats | null>(null)
const polling = ref(false)
const lastFetchTime = ref(0)
const now = ref(Date.now())
let pollTimer: number | undefined
let nowTimer: number | undefined

async function fetchStats() {
  if (!props.crontab.id) return
  try {
    currentStats.value = await api.crontabController.getCrontabCurrentStats({
      crontabId: props.crontab.id,
    })
    // 只有在没有统计信息且超过5秒没有刷新时才触发刷新，避免频繁刷新
    if (!currentStats.value.ts && Date.now() - lastFetchTime.value > 5000) {
      emit('refresh')
    }
    lastFetchTime.value = Date.now()
  } catch (e: unknown) {
    console.debug('Failed to fetch stats', e)
    const msg = e instanceof Error ? e.message : String(e)
    if (msg.includes('没有正在运行')) {
      stopPolling()
      // 任务完成时才刷新一次
      emit('refresh')
    }
  }
}

function startPolling() {
  if (polling.value) return
  polling.value = true
  fetchStats()
  // 降低轮询频率：从1秒改为3秒，减少服务器压力和页面闪烁
  pollTimer = window.setInterval(fetchStats, 3000)
  // 降低时间更新频率：从200ms改为1秒
  nowTimer = window.setInterval(() => {
    now.value = Date.now()
  }, 1000)
}

function stopPolling() {
  polling.value = false
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = undefined
  }
  if (nowTimer) {
    clearInterval(nowTimer)
    nowTimer = undefined
  }
  currentStats.value = null
  lastFetchTime.value = 0
}

function getPercent(val?: number) {
  if (val === undefined || !currentStats.value?.assetCount) return 0
  return Math.min(100, Math.round((val / currentStats.value.assetCount) * 100))
}

watch(
  () => props.crontab.running,
  (v) => {
    if (v) {
      startPolling()
    } else {
      stopPolling()
    }
  },
  { immediate: true },
)

onUnmounted(() => {
  stopPolling()
})
</script>

<template>
  <Card class="overflow-hidden ring-1 ring-slate-200/60">
    <template #title>
      <div class="flex items-center justify-between pb-4">
        <div class="flex items-center gap-3">
          <span class="font-medium text-slate-700 text-base">{{ crontab.name }}</span>
        </div>
        <div class="flex items-center gap-2 text-xs text-slate-600">
          <span class="hidden sm:inline">启用</span>
          <ToggleSwitch
            :modelValue="crontab.enabled"
            :disabled="busy"
            @update:modelValue="() => emit('toggle')"
            class="mr-2 sm:mr-4"
          />
          <SplitButton
            v-if="manualActionOptions.length > 0"
            size="small"
            severity="warning"
            class="mr-1"
            label="立即执行"
            icon="pi pi-play"
            :model="manualActionOptions"
            @click="emit('execute')"
          />
          <Button
            v-else
            icon="pi pi-play"
            size="small"
            severity="warning"
            class="mr-1"
            @click="emit('execute')"
          />
          <Button icon="pi pi-pencil" size="small" @click="emit('edit')" />
          <Button icon="pi pi-trash" size="small" severity="danger" @click="emit('delete')" />
        </div>
      </div>
    </template>

    <template #content>
      <div class="text-sm md:flex md:items-start md:gap-6">
        <div class="flex-1 space-y-3">
          <div v-if="crontab.description" class="text-slate-500">
            {{ crontab.description }}
          </div>

          <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div class="flex items-center gap-2">
              <i class="pi pi-user text-slate-400" />
              <span class="text-slate-600">{{ crontab.account?.nickname || '-' }}</span>
            </div>
            <div class="flex items-center gap-2">
              <i class="pi pi-clock text-slate-400" />
              <span class="text-slate-600">{{ crontab.config?.expression }}</span>
            </div>
            <div class="flex items-center gap-2">
              <i class="pi pi-globe text-slate-400" />
              <span class="text-slate-600">{{ crontab.config?.timeZone }}</span>
            </div>
            <div class="flex items-center gap-2 sm:col-span-1 col-span-1">
              <i class="pi pi-folder text-slate-400" />
              <span class="text-slate-600 truncate">{{ crontab.config?.targetPath || '-' }}</span>
            </div>
          </div>

          <div class="flex items-center gap-2 flex-wrap">
            <Tag
              :severity="crontab.config?.downloadImages ? 'success' : 'secondary'"
              :value="crontab.config?.downloadImages ? '下载照片' : '不下载照片'"
            />
            <Tag
              :severity="crontab.config?.downloadVideos ? 'success' : 'secondary'"
              :value="crontab.config?.downloadVideos ? '下载视频' : '不下载视频'"
            />
            <Tag
              :severity="crontab.config?.downloadAudios ? 'success' : 'secondary'"
              :value="crontab.config?.downloadAudios ? '下载录音' : '不下载录音'"
            />
            <Tag
              :severity="crontab.config?.rewriteExifTime ? 'success' : 'secondary'"
              :value="crontab.config?.rewriteExifTime ? '填充 EXIF' : '不填充 EXIF 时间'"
            />
            <Tag
              :severity="crontab.config?.diffByTimeline ? 'success' : 'secondary'"
              :value="crontab.config?.diffByTimeline ? '时间线比对差异' : '全量比对差异'"
            />
            <Tag
              :severity="crontab.config?.skipExistingFile ? 'success' : 'danger'"
              :value="crontab.config?.skipExistingFile ? '跳过已存在文件' : '覆盖已存在文件'"
            />
            <Tag
              :severity="crontab.config?.rewriteFileSystemTime ? 'success' : 'secondary'"
              :value="
                crontab.config?.rewriteFileSystemTime ? '重写文件系统时间' : '不重写文件系统时间'
              "
            />
            <Tag
              :severity="crontab.config?.checkSha1 ? 'success' : 'secondary'"
              :value="crontab.config?.checkSha1 ? '校验 SHA1' : '不校验 SHA1'"
            />
          </div>

          <div class="flex flex-wrap items-center gap-2 pt-1">
            <Chip
              v-for="id in crontab.albumIds"
              :key="id"
              :label="albumMap[id] || String(id)"
              class="text-xs"
            />
            <span
              v-if="!crontab.albumIds || crontab.albumIds.length === 0"
              class="text-xs text-slate-400"
              >无关联相册</span
            >
          </div>
        </div>

        <div class="mt-4 md:mt-0 md:w-1/2">
          <div
            v-if="crontab.running"
            class="mb-3 rounded-md bg-blue-50 dark:bg-blue-900/20 ring-1 ring-blue-200/60 p-3"
          >
            <div class="flex items-center justify-between mb-2">
              <div class="flex items-center gap-2">
                <i class="pi pi-spin pi-spinner text-blue-500"></i>
                <span class="text-xs font-medium text-blue-700 dark:text-blue-300"
                  >正在执行中...</span
                >
              </div>
              <div class="flex flex-col items-end">
                <span class="text-[10px] text-blue-600/60 font-mono" title="数据获取时间">{{
                  currentStats?.ts ? new Date(currentStats.ts).toLocaleTimeString() : ''
                }}</span>
                <span v-if="lastFetchTime" class="text-[9px] text-blue-400 font-mono">
                  {{ ((now - lastFetchTime) / 1000).toFixed(1) }}s ago
                </span>
              </div>
            </div>

            <div v-if="currentStats" class="space-y-2">
              <div
                class="text-xs flex justify-between items-center pb-1 border-b border-blue-100 dark:border-blue-800"
              >
                <span class="text-slate-500">总计资产</span>
                <span class="font-mono text-blue-700 font-bold">{{
                  currentStats.assetCount ?? '-'
                }}</span>
              </div>

              <div v-if="currentStats.downloadCompletedCount !== undefined" class="text-xs">
                <div class="flex justify-between items-center mb-1">
                  <span class="text-slate-500">下载进度</span>
                  <span class="font-mono text-slate-700"
                    >{{ currentStats.downloadCompletedCount }}
                    <span class="text-slate-400" v-if="currentStats.assetCount"
                      >/ {{ currentStats.assetCount }}</span
                    ></span
                  >
                </div>
                <div
                  class="w-full bg-blue-200 dark:bg-blue-900 rounded-full h-1.5"
                  v-if="currentStats.assetCount"
                >
                  <div
                    class="bg-blue-500 h-1.5 rounded-full transition-all duration-500"
                    :style="{ width: getPercent(currentStats.downloadCompletedCount) + '%' }"
                  ></div>
                </div>
              </div>

              <div
                v-if="currentStats.sha1VerifiedCount !== undefined && crontab.config?.checkSha1"
                class="text-xs"
              >
                <div class="flex justify-between items-center mb-1">
                  <span class="text-slate-500">SHA1 校验</span>
                  <span class="font-mono text-slate-700">{{ currentStats.sha1VerifiedCount }}</span>
                </div>
                <div
                  class="w-full bg-blue-200 dark:bg-blue-900 rounded-full h-1.5"
                  v-if="currentStats.assetCount"
                >
                  <div
                    class="bg-purple-500 h-1.5 rounded-full transition-all duration-500"
                    :style="{ width: getPercent(currentStats.sha1VerifiedCount) + '%' }"
                  ></div>
                </div>
              </div>

              <div
                v-if="currentStats.exifFilledCount !== undefined && crontab.config?.rewriteExifTime"
                class="text-xs"
              >
                <div class="flex justify-between items-center mb-1">
                  <span class="text-slate-500">EXIF 填充</span>
                  <span class="font-mono text-slate-700">{{ currentStats.exifFilledCount }}</span>
                </div>
                <div
                  class="w-full bg-blue-200 dark:bg-blue-900 rounded-full h-1.5"
                  v-if="currentStats.assetCount"
                >
                  <div
                    class="bg-amber-500 h-1.5 rounded-full transition-all duration-500"
                    :style="{ width: getPercent(currentStats.exifFilledCount) + '%' }"
                  ></div>
                </div>
              </div>

              <div
                v-if="
                  currentStats.fsTimeUpdatedCount !== undefined &&
                  crontab.config?.rewriteFileSystemTime
                "
                class="text-xs"
              >
                <div class="flex justify-between items-center mb-1">
                  <span class="text-slate-500">时间重写</span>
                  <span class="font-mono text-slate-700">{{
                    currentStats.fsTimeUpdatedCount
                  }}</span>
                </div>
                <div
                  class="w-full bg-blue-200 dark:bg-blue-900 rounded-full h-1.5"
                  v-if="currentStats.assetCount"
                >
                  <div
                    class="bg-emerald-500 h-1.5 rounded-full transition-all duration-500"
                    :style="{ width: getPercent(currentStats.fsTimeUpdatedCount) + '%' }"
                  ></div>
                </div>
              </div>

              <div v-else class="text-xs text-slate-400 pl-6 py-2">
                正在获取远程数据和资产差异检查...
              </div>
            </div>
          </div>

          <div class="rounded-md bg-slate-50 dark:bg-slate-900/30 ring-1 ring-slate-200/60 p-3">
            <div class="text-xs font-medium text-slate-500 mb-2">最近执行(本地时间)</div>
            <div v-if="recentHistories.length === 0" class="text-xs text-slate-400">暂无历史</div>
            <ul v-else class="space-y-2">
              <li
                v-for="(h, index) in recentHistories"
                :key="h.id"
                class="flex items-center justify-between"
              >
                <div class="flex items-center gap-2">
                  <span
                    class="inline-block w-2 h-2 rounded-full"
                    :class="h.isCompleted ? 'bg-emerald-500' : 'bg-amber-500'"
                  />
                  <span class="text-slate-600"
                    >{{ formatTime(h.startTime) }} → {{ formatTime(h.endTime) }}</span
                  >
                </div>
                <div class="flex items-center gap-2">
                  <span class="text-xs text-slate-400" v-if="h.detailsCount !== undefined">
                    {{ h.detailsCount }} 个资产
                  </span>
                  <Tag
                    v-if="index === 0 && crontab.running"
                    :severity="h.isCompleted ? 'success' : 'info'"
                    :value="h.isCompleted ? '完成' : '进行中'"
                  />
                  <Tag
                    v-else
                    :severity="h.isCompleted ? 'success' : 'warn'"
                    :value="h.isCompleted ? '完成' : '终止'"
                  />
                </div>
              </li>
            </ul>
          </div>
        </div>
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
