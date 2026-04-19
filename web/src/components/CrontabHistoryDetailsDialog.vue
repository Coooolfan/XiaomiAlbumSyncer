<script setup lang="ts">
import { computed, ref } from 'vue'
import Button from 'primevue/button'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import Dialog from 'primevue/dialog'
import Tag from 'primevue/tag'
import type { CrontabDto, CrontabHistoryDetailDto } from '@/__generated/model/dto'

type CrontabHistory = CrontabDto['CrontabController/DEFAULT_CRONTAB']['histories'][number]
type CrontabHistoryDetail =
  CrontabHistoryDetailDto['CrontabController/CRONTAB_HISTORY_DETAIL_FETCHER']

const props = withDefaults(
  defineProps<{
    visible: boolean
    history: CrontabHistory | null
    rows: ReadonlyArray<CrontabHistoryDetail>
    pageIndex: number
    pageSize: number
    totalRowCount: number
    loading?: boolean
    error?: string | null
  }>(),
  {
    loading: false,
    error: null,
  },
)

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'page', payload: { pageIndex: number; pageSize: number }): void
  (e: 'refresh'): void
}>()

const visibleProxy = computed({
  get: () => props.visible,
  set: (value: boolean) => emit('update:visible', value),
})

const maximized = ref(false)

const first = computed(() => props.pageIndex * props.pageSize)
const dialogStyle = computed(() =>
  maximized.value
    ? {
        width: '100vw',
        height: '100vh',
        maxHeight: '100vh',
      }
    : {
        width: '95vw',
        maxWidth: '72rem',
      },
)
const dialogPt = computed(() => ({
  root: {
    class: maximized.value ? '!m-0 flex h-screen max-h-screen flex-col' : 'flex flex-col',
  },
  content: {
    class: 'flex min-h-0 flex-1 flex-col overflow-hidden',
  },
}))

const historyStatus = computed(() => {
  const history = props.history
  if (!history) return { severity: 'secondary' as const, label: '未知' }
  if (history.isCompleted) return { severity: 'success' as const, label: '完成' }
  if (!history.endTime) return { severity: 'info' as const, label: '进行中' }
  return { severity: 'warn' as const, label: '终止' }
})

function formatTime(value?: string) {
  if (!value) return '-'
  try {
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return value
    return date.toLocaleString()
  } catch {
    return value
  }
}

function formatRange(history: CrontabHistory | null) {
  if (!history) return '-'
  return `${formatTime(history.startTime)} → ${formatTime(history.endTime || history.startTime)}`
}

function resolveErrorMessage(error: string | null | undefined) {
  return error?.trim() || '加载执行明细失败'
}

function resolveAssetId(detail: CrontabHistoryDetail) {
  return detail.asset?.id ?? '-'
}

function resolveAlbumName(detail: CrontabHistoryDetail) {
  return detail.asset?.album?.name?.trim() || '-'
}

function resolveAlbumId(detail: CrontabHistoryDetail) {
  return detail.asset?.album?.id ?? '-'
}

function resolveMessage(detail: CrontabHistoryDetail) {
  return detail.message?.trim() || '—'
}

function resolveStepStatus(value?: boolean) {
  return value
    ? { severity: 'success' as const, label: '完成' }
    : { severity: 'warn' as const, label: '未完成' }
}

function handlePage(event: { page?: number; rows?: number }) {
  emit('page', {
    pageIndex: event.page ?? 0,
    pageSize: event.rows ?? props.pageSize,
  })
}

const stepTagClass = '!whitespace-nowrap !text-xs'
</script>

<template>
  <Dialog
    v-model:visible="visibleProxy"
    modal
    maximizable
    :style="dialogStyle"
    :pt="dialogPt"
    header="执行明细"
    @maximize="maximized = true"
    @unmaximize="maximized = false"
  >
    <div class="flex min-h-0 flex-1 flex-col gap-4 pt-2">
      <div
        class="flex flex-col gap-3 rounded-lg bg-slate-50 dark:bg-slate-900/40 ring-1 ring-slate-200/70 dark:ring-slate-700/70 p-4"
      >
        <div class="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div class="space-y-2 min-w-0">
            <div class="flex items-center gap-2 flex-wrap">
              <span class="text-sm font-medium text-slate-700 dark:text-slate-200">
                {{ formatRange(history) }}
              </span>
              <Tag :severity="historyStatus.severity" :value="historyStatus.label" />
            </div>
            <div class="text-xs text-slate-500 dark:text-slate-400 flex flex-wrap gap-x-4 gap-y-1">
              <span>历史 ID：{{ history?.id ?? '-' }}</span>
              <span>总条数：{{ totalRowCount }}</span>
              <span>每页：{{ pageSize }}</span>
            </div>
          </div>

          <Button
            v-if="historyStatus.label === '进行中'"
            label="手动刷新"
            icon="pi pi-refresh"
            size="small"
            severity="secondary"
            :loading="loading"
            @click="emit('refresh')"
          />
        </div>

        <div
          v-if="error"
          class="rounded-md bg-red-50 dark:bg-red-950/40 text-red-700 dark:text-red-300 text-sm px-3 py-2 ring-1 ring-red-200 dark:ring-red-900/60"
        >
          {{ resolveErrorMessage(error) }}
        </div>
      </div>

      <div class="min-h-0 flex-1">
        <DataTable
          :value="rows"
          :loading="loading"
          :rows="pageSize"
          :first="first"
          :total-records="totalRowCount"
          paginator
          lazy
          scrollable
          scroll-height="flex"
          removable-sort
          size="small"
          class="h-full text-sm"
          @page="handlePage"
        >
          <template #empty>
            <div class="text-center text-slate-500 dark:text-slate-400 py-6">
              {{ error ? '明细加载失败，请稍后重试。' : '暂无执行明细。' }}
            </div>
          </template>

          <Column header="资产" style="min-width: 12rem">
            <template #body="{ data }">
              <span class="font-mono text-sm text-slate-700 dark:text-slate-200">
                {{ resolveAssetId(data) }}
              </span>
            </template>
          </Column>

          <Column header="相册" style="min-width: 10rem">
            <template #body="{ data }">
              <div class="flex flex-col gap-1">
                <span class="text-slate-700 dark:text-slate-200">
                  {{ resolveAlbumName(data) }}
                </span>
                <span class="font-mono text-xs text-slate-400 dark:text-slate-500">
                  {{ 'ID ' + resolveAlbumId(data) }}
                </span>
              </div>
            </template>
          </Column>

          <Column header="文件路径" style="min-width: 18rem">
            <template #body="{ data }">
              <span class="font-mono text-xs text-slate-600 dark:text-slate-300 break-all">
                {{ data.filePath || '-' }}
              </span>
            </template>
          </Column>

          <Column header="下载时间" style="min-width: 11rem">
            <template #body="{ data }">
              <span class="text-slate-600 dark:text-slate-300">
                {{ formatTime(data.downloadTime) }}
              </span>
            </template>
          </Column>

          <Column header="下载" style="width: 6rem">
            <template #body="{ data }">
              <Tag
                :class="stepTagClass"
                :severity="resolveStepStatus(data.downloadCompleted).severity"
                :value="resolveStepStatus(data.downloadCompleted).label"
              />
            </template>
          </Column>

          <Column header="SHA1" style="width: 6rem">
            <template #body="{ data }">
              <Tag
                :class="stepTagClass"
                :severity="resolveStepStatus(data.sha1Verified).severity"
                :value="resolveStepStatus(data.sha1Verified).label"
              />
            </template>
          </Column>

          <Column header="EXIF" style="width: 6rem">
            <template #body="{ data }">
              <Tag
                :class="stepTagClass"
                :severity="resolveStepStatus(data.exifFilled).severity"
                :value="resolveStepStatus(data.exifFilled).label"
              />
            </template>
          </Column>

          <Column header="文件时间" style="width: 7rem">
            <template #body="{ data }">
              <Tag
                :class="stepTagClass"
                :severity="resolveStepStatus(data.fsTimeUpdated).severity"
                :value="resolveStepStatus(data.fsTimeUpdated).label"
              />
            </template>
          </Column>

          <Column header="消息" style="min-width: 16rem">
            <template #body="{ data }">
              <div class="text-sm text-slate-700 dark:text-slate-200 break-words">
                {{ resolveMessage(data) }}
              </div>
            </template>
          </Column>
        </DataTable>
      </div>
    </div>
  </Dialog>
</template>
