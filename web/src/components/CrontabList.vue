<script setup lang="ts">
import Card from 'primevue/card'
import Button from 'primevue/button'
import CrontabCard from '@/components/CrontabCard.vue'
import type { CrontabDto } from '@/__generated/model/dto'

type Crontab = CrontabDto['CrontabController/DEFAULT_CRONTAB']

const props = defineProps<{
  crontabs?: ReadonlyArray<Crontab>
  loading?: boolean
  albumOptions: ReadonlyArray<{ label: string; value: string }>
  updatingRow?: number | null
}>()

const emit = defineEmits<{
  (e: 'refresh'): void
  (e: 'create'): void
  (e: 'edit', item: Crontab): void
  (e: 'delete', item: Crontab): void
  (e: 'toggle', item: Crontab): void
  (e: 'execute', item: Crontab): void
  (e: 'executeExif', item: Crontab): void
  (e: 'executeRewriteFsTime', item: Crontab): void
}>()
</script>

<template>
  <Card header="计划任务" class="overflow-hidden shadow-sm ring-1 ring-slate-200/60 mb-6">
    <template #title>
      <div class="flex items-center justify-between">
        <div class="font-medium text-slate-600">计划任务</div>
        <div class="flex items-center gap-2">
          <Button icon="pi pi-refresh" severity="secondary" rounded text @click="emit('refresh')" />
          <Button icon="pi pi-plus" severity="success" rounded text @click="emit('create')" />
        </div>
      </div>
    </template>

    <template #content>
      <div class="space-y-3">
        <div v-if="props.loading" class="text-xs text-slate-500 py-6">加载中...</div>
        <div v-else>
          <div
            v-if="!props.crontabs || props.crontabs.length === 0"
            class="text-xs text-slate-500 py-6"
          >
            暂无计划任务
          </div>
          <div v-else class="grid grid-cols-1 gap-3">
            <CrontabCard
              v-for="item in props.crontabs"
              :key="item.id"
              :crontab="item"
              :album-options="props.albumOptions"
              :busy="props.updatingRow === item.id"
              @edit="emit('edit', item)"
              @delete="emit('delete', item)"
              @toggle="emit('toggle', item)"
              @execute="emit('execute', item)"
              @execute-exif="emit('executeExif', item)"
              @execute-rewrite-fs-time="emit('executeRewriteFsTime', item)"
              @refresh="emit('refresh')"
            />
          </div>
        </div>
      </div>
    </template>
  </Card>
</template>
