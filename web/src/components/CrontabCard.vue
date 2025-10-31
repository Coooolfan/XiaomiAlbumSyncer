<script setup lang="ts">
import Card from 'primevue/card'
import Button from 'primevue/button'
import Chip from 'primevue/chip'
import InputSwitch from 'primevue/inputswitch'
import Tag from 'primevue/tag'
import type { CrontabDto } from '@/__generated/model/dto'
import { computed } from 'vue'

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
  return list.slice(0, 3)
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
          <InputSwitch :modelValue="crontab.enabled" :disabled="busy" @update:modelValue="() => emit('toggle')"
            class="mr-2 sm:mr-4" />
          <Button icon="pi pi-play" size="small" severity="warning" class="mr-1" @click="emit('execute')" />
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

          <div class="grid grid-cols-1 sm:grid-cols-3 gap-3">
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
            <Tag :severity="crontab.config?.downloadImages ? 'success' : 'secondary'"
              :value="crontab.config?.downloadImages ? '下载照片' : '不下载照片'" />
            <Tag :severity="crontab.config?.downloadVideos ? 'success' : 'secondary'"
              :value="crontab.config?.downloadVideos ? '下载视频' : '不下载视频'" />
            <Tag :severity="crontab.config?.rewriteExifTime ? 'success' : 'secondary'"
              :value="crontab.config?.rewriteExifTime ? '填充 EXIF' : '不填充 EXIF 时间'" />
            <Tag :severity="crontab.config?.diffByTimeline ? 'success' : 'secondary'"
              :value="crontab.config?.diffByTimeline ? '时间线比对差异' : '全量比对差异'" />
            <Tag :severity="crontab.config?.skipExistingFile ? 'success' : 'danger'"
              :value="crontab.config?.skipExistingFile ? '跳过已存在文件' : '覆盖已存在文件'" />
          </div>

          <div class="flex flex-wrap items-center gap-2 pt-1">
            <Chip v-for="id in crontab.albumIds" :key="id" :label="albumMap[id] || String(id)" class="text-xs" />
            <span v-if="!crontab.albumIds || crontab.albumIds.length === 0" class="text-xs text-slate-400">未关联相册</span>
          </div>


        </div>

        <div class="mt-4 md:mt-0 md:w-1/2">
          <div class="rounded-md bg-slate-50 dark:bg-slate-900/30 ring-1 ring-slate-200/60 p-3">
            <div class="text-xs font-medium text-slate-500 mb-2">最近执行(本地时间)</div>
            <div v-if="recentHistories.length === 0" class="text-xs text-slate-400">暂无历史</div>
            <ul v-else class="space-y-2">
              <li v-for="h in recentHistories" :key="h.id" class="flex items-center justify-between">
                <div class="flex items-center gap-2">
                  <span class="inline-block w-2 h-2 rounded-full"
                    :class="h.isCompleted ? 'bg-emerald-500' : 'bg-amber-500'" />
                  <span class="text-slate-600">{{ formatTime(h.startTime) }} → {{ formatTime(h.endTime) }}</span>
                </div>
                <Tag :severity="h.isCompleted ? 'success' : 'warning'" :value="h.isCompleted ? '完成' : '进行中'" />
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
  transition: transform 180ms ease, box-shadow 180ms ease;
}

:deep(.p-card:hover) {
  transform: translateY(-1px);
  box-shadow: 0 8px 30px -12px rgba(2, 6, 23, 0.2);
}
</style>
