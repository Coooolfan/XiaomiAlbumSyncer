<script setup lang="ts">
import Button from 'primevue/button'
import CrontabRow from '@/components/CrontabRow.vue'
import type { CrontabDto } from '@/__generated/model/dto'
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

type Crontab = CrontabDto['CrontabController/DEFAULT_CRONTAB']
type CrontabHistory = Crontab['histories'][number]

const props = defineProps<{
  crontabs?: ReadonlyArray<Crontab>
  loading?: boolean
  albumOptions: ReadonlyArray<{ label: string; value: string; shadow?: boolean }>
  updatingRow?: number | null
}>()

const expandedIds = ref<Set<number>>(new Set())

function toggleExpand(id: number) {
  const next = new Set(expandedIds.value)
  if (next.has(id)) {
    next.delete(id)
  } else {
    next.add(id)
  }
  expandedIds.value = next
}

const emit = defineEmits<{
  (e: 'refresh'): void
  (e: 'create'): void
  (e: 'edit', item: Crontab): void
  (e: 'delete', item: Crontab): void
  (e: 'toggle', item: Crontab): void
  (e: 'execute', item: Crontab): void
  (e: 'executeExif', item: Crontab): void
  (e: 'executeRewriteFsTime', item: Crontab): void
  (e: 'clearHistory', item: Crontab): void
  (e: 'viewHistoryDetails', history: CrontabHistory): void
}>()
</script>

<template>
  <div
    class="overflow-hidden rounded-lg border border-slate-200/80 bg-white/70 backdrop-blur-sm dark:border-slate-800/80 dark:bg-slate-900/60"
  >
    <div
      v-if="props.loading"
      class="px-4 py-8 text-center text-xs text-slate-400 dark:text-slate-500"
    >
      {{ t('common.status.loading') }}
    </div>
    <div
      v-else-if="!props.crontabs || props.crontabs.length === 0"
      class="flex flex-col items-center gap-2 px-4 py-10"
    >
      <i class="pi pi-calendar text-xl text-slate-300 dark:text-slate-600" />
      <div class="text-xs text-slate-400 dark:text-slate-500">{{ t('schedule.list.empty') }}</div>
      <Button
        :label="t('schedule.newTask')"
        icon="pi pi-plus"
        size="small"
        text
        @click="emit('create')"
      />
    </div>
    <div v-else class="divide-y divide-slate-200/60 dark:divide-slate-800/60">
      <CrontabRow
        v-for="item in props.crontabs"
        :key="item.id"
        :crontab="item"
        :album-options="props.albumOptions"
        :busy="props.updatingRow === item.id"
        :expanded="expandedIds.has(item.id)"
        @toggle-expand="toggleExpand(item.id)"
        @edit="emit('edit', item)"
        @delete="emit('delete', item)"
        @toggle="emit('toggle', item)"
        @execute="emit('execute', item)"
        @execute-exif="emit('executeExif', item)"
        @execute-rewrite-fs-time="emit('executeRewriteFsTime', item)"
        @clear-history="emit('clearHistory', item)"
        @view-history-details="emit('viewHistoryDetails', $event)"
        @refresh="emit('refresh')"
      />
    </div>
  </div>
</template>
