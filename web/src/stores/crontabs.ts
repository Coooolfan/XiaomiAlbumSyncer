import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api } from '@/ApiInstance'
import type { CrontabDto } from '@/__generated/model/dto'
import type { CrontabCreateInput, CrontabUpdateInput } from '@/__generated/model/static'

type Crontab = CrontabDto['CrontabController/DEFAULT_CRONTAB']

type FetchOptions = { force?: boolean }

export const useCrontabsStore = defineStore('crontabs', () => {
  const crontabs = ref<ReadonlyArray<Crontab>>([])
  const loading = ref(false)
  const loaded = ref(false)
  const error = ref<unknown | null>(null)

  async function fetchCrontabs(options: FetchOptions = {}) {
    if (loaded.value && !options.force) return crontabs.value
    loading.value = true
    error.value = null
    try {
      const list = await api.crontabController.listCrontabs()
      crontabs.value = list
      loaded.value = true
      return list
    } catch (err) {
      error.value = err
      throw err
    } finally {
      loading.value = false
    }
  }

  async function refreshCrontabs() {
    return fetchCrontabs({ force: true })
  }

  async function createCrontab(body: CrontabCreateInput) {
    const created = await api.crontabController.createCrontab({ body })
    await refreshCrontabs()
    return created
  }

  async function updateCrontab(crontabId: number, body: CrontabUpdateInput) {
    const updated = await api.crontabController.updateCrontab({ crontabId, body })
    crontabs.value = crontabs.value.map((c) => (c.id === crontabId ? updated : c))
    return updated
  }

  async function deleteCrontab(crontabId: number) {
    await api.crontabController.deleteCrontab({ crontabId })
    crontabs.value = crontabs.value.filter((c) => c.id !== crontabId)
  }

  async function executeCrontab(crontabId: number) {
    return api.crontabController.executeCrontab({ crontabId })
  }

  async function executeCrontabExifTime(crontabId: number) {
    return api.crontabController.executeCrontabExifTime({ crontabId })
  }

  async function executeCrontabRewriteFileSystemTime(crontabId: number) {
    return api.crontabController.executeCrontabRewriteFileSystemTime({ crontabId })
  }

  function reset() {
    crontabs.value = []
    loaded.value = false
    error.value = null
  }

  return {
    crontabs,
    loading,
    loaded,
    error,
    fetchCrontabs,
    refreshCrontabs,
    createCrontab,
    updateCrontab,
    deleteCrontab,
    executeCrontab,
    executeCrontabExifTime,
    executeCrontabRewriteFileSystemTime,
    reset,
  }
})
