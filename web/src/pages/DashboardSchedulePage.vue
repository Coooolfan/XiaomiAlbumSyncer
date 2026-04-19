<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import Card from 'primevue/card'
import ContributionHeatmap from '@/components/ContributionHeatmap.vue'
import AlbumPanel from '@/components/AlbumPanel.vue'
import CrontabList from '@/components/CrontabList.vue'
import CronFormDialog from '@/components/CronFormDialog.vue'
import ExecutionDialogs from '@/components/ExecutionDialogs.vue'
import CrontabHistoryDetailsDialog from '@/components/CrontabHistoryDetailsDialog.vue'
import { useToast } from 'primevue/usetoast'
import { storeToRefs } from 'pinia'
import { useAccountsStore } from '@/stores/accounts'
import { useAlbumsStore } from '@/stores/albums'
import { useCrontabsStore } from '@/stores/crontabs'
import { usePreferencesStore } from '@/stores/preferences'
import { useHeatmapTimeline } from '@/composables/useHeatmapTimeline'
import { useCronForm } from '@/composables/useCronForm'
import { useCronActions } from '@/composables/useCronActions'
import { api } from '@/ApiInstance'
import type { CrontabDto, CrontabHistoryDetailDto } from '@/__generated/model/dto'

const accountsStore = useAccountsStore()
const albumsStore = useAlbumsStore()
const crontabsStore = useCrontabsStore()
const preferencesStore = usePreferencesStore()

type CrontabHistory = CrontabDto['CrontabController/DEFAULT_CRONTAB']['histories'][number]
type CrontabHistoryDetail =
  CrontabHistoryDetailDto['CrontabController/CRONTAB_HISTORY_DETAIL_FETCHER']

const { accounts } = storeToRefs(accountsStore)
const { albums, loaded: albumsLoaded } = storeToRefs(albumsStore)
const { crontabs, loading: loadingCrons } = storeToRefs(crontabsStore)
const { optimizeHeatmap } = storeToRefs(preferencesStore)

const toast = useToast()

const albumIds = computed(() => albums.value.map((a) => a.id))
const {
  labelText,
  weekStartNum,
  rangeDaysNum,
  endDateStr,
  dataPoints,
  tip,
  onDayClick,
  fetchTimeline,
  scheduleFetch,
} = useHeatmapTimeline({
  albumIds: () => albumIds.value,
  optimizeHeatmap: () => optimizeHeatmap.value,
})

const accountOptions = computed(() =>
  accounts.value.map((a) => ({ label: a.nickname || a.userId, value: a.id })),
)

const allAlbumOptions = computed(() =>
  (albums.value || []).map((a) => ({
    label: a.name ?? `ID ${a.id}`,
    value: String(a.id),
    shadow: a.shadow,
  })),
)

const {
  showCronDialog,
  isEditing,
  editingId,
  cronForm,
  formErrors,
  timeZones,
  targetPathMountWarning,
  openCreateCron,
  openEditCron,
  validateCronForm,
  buildTimeZones,
} = useCronForm(() => accounts.value[0]?.id ?? 0)

const formAlbumOptions = computed(() => {
  if (!cronForm.value.accountId) return []
  return (albums.value || [])
    .filter((a) => a.account.id === cronForm.value.accountId && !a.shadow)
    .map((a) => ({ label: a.name ?? `ID ${a.id}`, value: a.id }))
})

watch(
  [showCronDialog, albumsLoaded, formAlbumOptions],
  ([visible, loaded]) => {
    if (!visible || !loaded) return

    const availableIds = new Set(formAlbumOptions.value.map((option) => option.value))
    const filteredAlbumIds = cronForm.value.albumIds.filter((id) => availableIds.has(id))

    if (filteredAlbumIds.length !== cronForm.value.albumIds.length) {
      cronForm.value.albumIds = filteredAlbumIds
    }
  },
  { immediate: true },
)

async function fetchCrontabs() {
  try {
    await crontabsStore.fetchCrontabs({ force: true })
  } catch (err) {
    console.error('获取计划任务失败', err)
    toast.add({
      severity: 'error',
      summary: '获取失败',
      detail: '无法获取计划任务列表',
      life: 2000,
    })
  }
}

const {
  saving,
  updatingRow,
  deleteDialog,
  executeDialog,
  executeExifDialog,
  executeRewriteFsDialog,
  requestDelete,
  requestExecute,
  requestExecuteExif,
  requestExecuteRewriteFs,
  submitCron,
  toggleEnabled,
  confirmDelete,
  confirmExecute,
  confirmExecuteExif,
  confirmExecuteRewriteFs,
} = useCronActions({
  crontabsStore,
  toast,
  fetchCrontabs,
  cronForm,
  isEditing,
  editingId,
  showCronDialog,
  validateCronForm,
})

const { visible: showDeleteVisible, loading: deleting } = deleteDialog
const { visible: showExecuteVisible, loading: executing } = executeDialog
const { visible: showExecuteExifVisible, loading: executingExif } = executeExifDialog
const { visible: showExecuteRewriteFsVisible, loading: executingRewriteFs } = executeRewriteFsDialog

const showHistoryDetailsDialog = ref(false)
const selectedHistory = ref<CrontabHistory | null>(null)
const historyDetailRows = ref<ReadonlyArray<CrontabHistoryDetail>>([])
const historyDetailsPageIndex = ref(0)
const historyDetailsPageSize = ref(10)
const historyDetailsTotalRowCount = ref(0)
const historyDetailsLoading = ref(false)
const historyDetailsError = ref<string | null>(null)

function getErrorMessage(error: unknown) {
  if (error instanceof Error) return error.message
  if (typeof error === 'string') return error
  if (error && typeof error === 'object' && 'message' in error) {
    const message = (error as { message?: unknown }).message
    if (typeof message === 'string' && message.trim()) return message
  }
  try {
    return JSON.stringify(error)
  } catch {
    return '无法加载执行明细'
  }
}

async function loadHistoryDetails() {
  const historyId = selectedHistory.value?.id
  if (historyId === null || historyId === undefined) {
    historyDetailRows.value = []
    historyDetailsTotalRowCount.value = 0
    historyDetailsError.value = '历史记录不存在，无法加载执行明细'
    return
  }

  historyDetailsLoading.value = true
  historyDetailsError.value = null
  try {
    const page = await api.crontabController.listCrontabHistoryDetails({
      id: historyId,
      pageIndex: historyDetailsPageIndex.value,
      pageSize: historyDetailsPageSize.value,
    })
    historyDetailRows.value = page.rows
    historyDetailsTotalRowCount.value = page.totalRowCount
  } catch (error) {
    historyDetailRows.value = []
    historyDetailsTotalRowCount.value = 0
    historyDetailsError.value = getErrorMessage(error)
  } finally {
    historyDetailsLoading.value = false
  }
}

async function openHistoryDetails(history: CrontabHistory) {
  selectedHistory.value = history
  historyDetailsPageIndex.value = 0
  historyDetailsError.value = null
  historyDetailRows.value = []
  historyDetailsTotalRowCount.value = 0
  showHistoryDetailsDialog.value = true
  await loadHistoryDetails()
}

async function handleHistoryDetailsPage(payload: { pageIndex: number; pageSize: number }) {
  historyDetailsPageIndex.value = payload.pageIndex
  historyDetailsPageSize.value = payload.pageSize
  await loadHistoryDetails()
}

async function refreshHistoryDetails() {
  await loadHistoryDetails()
}

onMounted(async () => {
  await Promise.all([
    accountsStore.fetchAccounts(),
    albumsStore.fetchAlbums(),
    crontabsStore.fetchCrontabs(),
  ])
  await fetchTimeline().catch((err) => {
    console.error('获取时间线数据失败', err)
  })
  buildTimeZones()
})

watch(albums, () => {
  scheduleFetch()
})

watch(showHistoryDetailsDialog, (visible) => {
  if (visible) return
  selectedHistory.value = null
  historyDetailRows.value = []
  historyDetailsPageIndex.value = 0
  historyDetailsError.value = null
  historyDetailsTotalRowCount.value = 0
})
</script>

<template>
  <div class="max-w-5xl mx-auto px-4 py-8">
    <Card class="overflow-hidden shadow-sm ring-1 ring-slate-200/60 dark:ring-slate-700/60 mb-6">
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

    <CrontabList
      :crontabs="crontabs"
      :loading="loadingCrons"
      :album-options="allAlbumOptions"
      :updating-row="updatingRow"
      @refresh="fetchCrontabs"
      @create="openCreateCron"
      @edit="openEditCron"
      @delete="requestDelete"
      @toggle="toggleEnabled"
      @execute="requestExecute"
      @execute-exif="requestExecuteExif"
      @execute-rewrite-fs-time="requestExecuteRewriteFs"
      @view-history-details="openHistoryDetails"
    />

    <AlbumPanel />

    <CronFormDialog
      v-model:visible="showCronDialog"
      :is-editing="isEditing"
      :saving="saving"
      :form="cronForm"
      :form-errors="formErrors"
      :time-zones="timeZones"
      :account-options="accountOptions"
      :form-album-options="formAlbumOptions"
      :target-path-mount-warning="targetPathMountWarning"
      @submit="submitCron"
    />

    <ExecutionDialogs
      v-model:delete-visible="showDeleteVisible"
      v-model:execute-visible="showExecuteVisible"
      v-model:execute-exif-visible="showExecuteExifVisible"
      v-model:execute-rewrite-fs-visible="showExecuteRewriteFsVisible"
      :deleting="deleting"
      :executing="executing"
      :executing-exif="executingExif"
      :executing-rewrite-fs="executingRewriteFs"
      @close-delete="deleteDialog.close()"
      @close-execute="executeDialog.close()"
      @close-execute-exif="executeExifDialog.close()"
      @close-execute-rewrite-fs="executeRewriteFsDialog.close()"
      @confirm-delete="confirmDelete"
      @confirm-execute="confirmExecute"
      @confirm-execute-exif="confirmExecuteExif"
      @confirm-execute-rewrite-fs="confirmExecuteRewriteFs"
    />

    <CrontabHistoryDetailsDialog
      v-model:visible="showHistoryDetailsDialog"
      :history="selectedHistory"
      :rows="historyDetailRows"
      :page-index="historyDetailsPageIndex"
      :page-size="historyDetailsPageSize"
      :total-row-count="historyDetailsTotalRowCount"
      :loading="historyDetailsLoading"
      :error="historyDetailsError"
      @page="handleHistoryDetailsPage"
      @refresh="refreshHistoryDetails"
    />
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
