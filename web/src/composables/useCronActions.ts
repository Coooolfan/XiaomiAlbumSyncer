import { ref, type Ref } from 'vue'
import { i18n } from '@/i18n'
import { useActionDialog } from '@/composables/useActionDialog'
import type { CrontabDto } from '@/__generated/model/dto'
import type { CrontabCreateInput, CrontabUpdateInput } from '@/__generated/model/static'
import type { LocalCronForm } from '@/utils/crontabForm'

type Crontab = CrontabDto['CrontabController/DEFAULT_CRONTAB']

type ToastLike = {
  add: (options: { severity: string; summary: string; detail?: string; life?: number }) => void
}

type CrontabsStore = {
  createCrontab: (body: CrontabCreateInput) => Promise<unknown>
  updateCrontab: (crontabId: number, body: CrontabUpdateInput) => Promise<unknown>
  deleteCrontab: (crontabId: number) => Promise<void>
  executeCrontab: (crontabId: number) => Promise<unknown>
  executeCrontabExifTime: (crontabId: number) => Promise<unknown>
  executeCrontabRewriteFileSystemTime: (crontabId: number) => Promise<unknown>
  clearCrontabHistory: (crontabId: number) => Promise<unknown>
}

type UseCronActionsOptions = {
  crontabsStore: CrontabsStore
  toast: ToastLike
  fetchCrontabs: () => Promise<void>
  cronForm: Ref<LocalCronForm>
  isEditing: Ref<boolean>
  editingId: Ref<number | null>
  showCronDialog: Ref<boolean>
  validateCronForm: () => boolean
}

export function useCronActions(options: UseCronActionsOptions) {
  const {
    crontabsStore,
    toast,
    fetchCrontabs,
    cronForm,
    isEditing,
    editingId,
    showCronDialog,
    validateCronForm,
  } = options

  const saving = ref(false)
  const updatingRow = ref<number | null>(null)

  const deleteDialog = useActionDialog()
  const executeDialog = useActionDialog()
  const executeExifDialog = useActionDialog()
  const executeRewriteFsDialog = useActionDialog()
  const clearHistoryDialog = useActionDialog()

  function requestDelete(row: Crontab) {
    deleteDialog.open(row.id)
  }

  function requestExecute(row: Crontab) {
    executeDialog.open(row.id)
  }

  function requestExecuteExif(row: Crontab) {
    executeExifDialog.open(row.id)
  }

  function requestExecuteRewriteFs(row: Crontab) {
    executeRewriteFsDialog.open(row.id)
  }

  function requestClearHistory(row: Crontab) {
    clearHistoryDialog.open(row.id)
  }

  async function submitCron() {
    if (!validateCronForm()) return
    saving.value = true
    try {
      if (isEditing.value && editingId.value !== null) {
        await crontabsStore.updateCrontab(editingId.value, {
          name: cronForm.value.name,
          description: cronForm.value.description,
          enabled: cronForm.value.enabled,
          config: cronForm.value.config,
          albumIds: cronForm.value.albumIds,
        })
        toast.add({
          severity: 'success',
          summary: i18n.global.t('schedule.toast.updated'),
          life: 1600,
        })
      } else {
        await crontabsStore.createCrontab(cronForm.value)
        toast.add({
          severity: 'success',
          summary: i18n.global.t('schedule.toast.created'),
          life: 1600,
        })
      }
      showCronDialog.value = false
      await fetchCrontabs()
    } catch (err) {
      console.error('保存计划任务失败', err)
      toast.add({
        severity: 'error',
        summary: i18n.global.t('common.toast.saveFailed'),
        detail: i18n.global.t('schedule.toast.retryLater'),
        life: 2200,
      })
    } finally {
      saving.value = false
    }
  }

  async function toggleEnabled(row: Crontab) {
    updatingRow.value = row.id
    try {
      await crontabsStore.updateCrontab(row.id, {
        name: row.name,
        description: row.description,
        enabled: !row.enabled,
        config: row.config,
        albumIds: row.albumIds,
      })
      toast.add({
        severity: 'success',
        summary: i18n.global.t('schedule.toast.updated'),
        life: 1600,
      })
    } catch (err) {
      console.error('更新启用状态失败', err)
      toast.add({
        severity: 'error',
        summary: i18n.global.t('schedule.toast.updateFailed'),
        life: 1800,
      })
    } finally {
      updatingRow.value = null
    }
  }

  async function confirmDelete() {
    if (deleteDialog.targetId.value === null) return
    deleteDialog.loading.value = true
    try {
      await crontabsStore.deleteCrontab(deleteDialog.targetId.value)
      toast.add({
        severity: 'success',
        summary: i18n.global.t('schedule.toast.deleted'),
        life: 1500,
      })
      deleteDialog.close()
      await fetchCrontabs()
    } catch (err) {
      console.error('删除计划任务失败', err)
      toast.add({
        severity: 'error',
        summary: i18n.global.t('schedule.toast.deleteFailed'),
        life: 1800,
      })
    } finally {
      deleteDialog.loading.value = false
    }
  }

  async function confirmExecute() {
    if (executeDialog.targetId.value === null) return
    executeDialog.loading.value = true
    try {
      await crontabsStore.executeCrontab(executeDialog.targetId.value)
      toast.add({
        severity: 'success',
        summary: i18n.global.t('schedule.toast.triggered'),
        life: 2000,
      })
      executeDialog.close()
      await fetchCrontabs()
    } catch (err) {
      console.error('立即执行触发失败', err)
      toast.add({
        severity: 'error',
        summary: i18n.global.t('schedule.toast.triggerFailed'),
        detail: err instanceof Error ? err.message : String(err),
        life: 2200,
      })
    } finally {
      executeDialog.loading.value = false
    }
  }

  async function confirmExecuteExif() {
    if (executeExifDialog.targetId.value === null) return
    executeExifDialog.loading.value = true
    try {
      await crontabsStore.executeCrontabExifTime(executeExifDialog.targetId.value)
      toast.add({
        severity: 'success',
        summary: i18n.global.t('schedule.toast.exifTriggered'),
        life: 2000,
      })
      executeExifDialog.close()
      await fetchCrontabs()
    } catch (err) {
      console.error('立即执行 EXIF 填充失败', err)
      toast.add({
        severity: 'error',
        summary: i18n.global.t('schedule.toast.triggerFailed'),
        detail: err instanceof Error ? err.message : String(err),
        life: 2200,
      })
    } finally {
      executeExifDialog.loading.value = false
    }
  }

  async function confirmExecuteRewriteFs() {
    if (executeRewriteFsDialog.targetId.value === null) return
    executeRewriteFsDialog.loading.value = true
    try {
      await crontabsStore.executeCrontabRewriteFileSystemTime(executeRewriteFsDialog.targetId.value)
      toast.add({
        severity: 'success',
        summary: i18n.global.t('schedule.toast.fsRewriteTriggered'),
        life: 2000,
      })
      executeRewriteFsDialog.close()
      await fetchCrontabs()
    } catch (err) {
      console.error('立即执行文件系统时间重写失败', err)
      toast.add({
        severity: 'error',
        summary: i18n.global.t('schedule.toast.triggerFailed'),
        detail: err instanceof Error ? err.message : String(err),
        life: 2200,
      })
    } finally {
      executeRewriteFsDialog.loading.value = false
    }
  }

  async function confirmClearHistory() {
    if (clearHistoryDialog.targetId.value === null) return
    clearHistoryDialog.loading.value = true
    try {
      await crontabsStore.clearCrontabHistory(clearHistoryDialog.targetId.value)
      toast.add({
        severity: 'success',
        summary: i18n.global.t('schedule.toast.historyCleared'),
        life: 1800,
      })
      clearHistoryDialog.close()
      await fetchCrontabs()
    } catch (err) {
      console.error('清理任务历史失败', err)
      toast.add({
        severity: 'error',
        summary: i18n.global.t('schedule.toast.clearFailed'),
        detail: err instanceof Error ? err.message : String(err),
        life: 2200,
      })
    } finally {
      clearHistoryDialog.loading.value = false
    }
  }

  return {
    saving,
    updatingRow,
    deleteDialog,
    executeDialog,
    executeExifDialog,
    executeRewriteFsDialog,
    clearHistoryDialog,
    requestDelete,
    requestExecute,
    requestExecuteExif,
    requestExecuteRewriteFs,
    requestClearHistory,
    submitCron,
    toggleEnabled,
    confirmDelete,
    confirmExecute,
    confirmExecuteExif,
    confirmExecuteRewriteFs,
    confirmClearHistory,
  }
}
