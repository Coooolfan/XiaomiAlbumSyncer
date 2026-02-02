import { ref, type Ref } from 'vue'
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

  async function submitCron() {
    if (!validateCronForm()) return
    saving.value = true
    try {
      // 在保存前自动同步 enableArchive 和 archiveMode 字段
      const configToSave = { ...cronForm.value.config }
      
      // enableSync 始终为 true
      configToSave.enableSync = true
      
      // 根据 archiveMode 自动设置 enableArchive
      if (configToSave.archiveMode !== 'DISABLED') {
        configToSave.enableArchive = true
      } else {
        configToSave.enableArchive = false
      }
      
      if (isEditing.value && editingId.value !== null) {
        await crontabsStore.updateCrontab(editingId.value, {
          name: cronForm.value.name,
          description: cronForm.value.description,
          enabled: cronForm.value.enabled,
          config: configToSave,
          albumIds: cronForm.value.albumIds,
        })
        toast.add({ severity: 'success', summary: '已更新', life: 1600 })
      } else {
        await crontabsStore.createCrontab({
          ...cronForm.value,
          config: configToSave,
        })
        toast.add({ severity: 'success', summary: '已创建', life: 1600 })
      }
      showCronDialog.value = false
      await fetchCrontabs()
    } catch (err) {
      console.error('保存计划任务失败', err)
      toast.add({ severity: 'error', summary: '保存失败', detail: '请稍后重试', life: 2200 })
    } finally {
      saving.value = false
    }
  }

  async function toggleEnabled(row: Crontab) {
    updatingRow.value = row.id
    try {
      // 在保存前自动同步 enableArchive 和 archiveMode 字段
      const configToSave = { ...row.config }
      
      // enableSync 始终为 true
      configToSave.enableSync = true
      
      // 根据 archiveMode 自动设置 enableArchive
      if (configToSave.archiveMode !== 'DISABLED') {
        configToSave.enableArchive = true
      } else {
        configToSave.enableArchive = false
      }
      
      await crontabsStore.updateCrontab(row.id, {
        name: row.name,
        description: row.description,
        enabled: !row.enabled,
        config: configToSave,
        albumIds: row.albumIds,
      })
      toast.add({ severity: 'success', summary: '已更新', life: 1600 })
    } catch (err) {
      console.error('更新启用状态失败', err)
      toast.add({ severity: 'error', summary: '更新失败', life: 1800 })
    } finally {
      updatingRow.value = null
    }
  }

  async function confirmDelete() {
    if (deleteDialog.targetId.value === null) return
    deleteDialog.loading.value = true
    try {
      await crontabsStore.deleteCrontab(deleteDialog.targetId.value)
      toast.add({ severity: 'success', summary: '已删除', life: 1500 })
      deleteDialog.close()
      await fetchCrontabs()
    } catch (err) {
      console.error('删除计划任务失败', err)
      toast.add({ severity: 'error', summary: '删除失败', life: 1800 })
    } finally {
      deleteDialog.loading.value = false
    }
  }

  async function confirmExecute() {
    if (executeDialog.targetId.value === null) return
    executeDialog.loading.value = true
    try {
      await crontabsStore.executeCrontab(executeDialog.targetId.value)
      toast.add({ severity: 'success', summary: '已触发', life: 2000 })
      executeDialog.close()
      await fetchCrontabs()
    } catch (err) {
      console.error('立即执行触发失败', err)
      toast.add({
        severity: 'error',
        summary: '触发失败',
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
      toast.add({ severity: 'success', summary: '已触发 EXIF 填充', life: 2000 })
      executeExifDialog.close()
      await fetchCrontabs()
    } catch (err) {
      console.error('立即执行 EXIF 填充失败', err)
      toast.add({
        severity: 'error',
        summary: '触发失败',
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
      toast.add({ severity: 'success', summary: '已触发文件时间重写', life: 2000 })
      executeRewriteFsDialog.close()
      await fetchCrontabs()
    } catch (err) {
      console.error('立即执行文件系统时间重写失败', err)
      toast.add({
        severity: 'error',
        summary: '触发失败',
        detail: err instanceof Error ? err.message : String(err),
        life: 2200,
      })
    } finally {
      executeRewriteFsDialog.loading.value = false
    }
  }

  return {
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
  }
}
