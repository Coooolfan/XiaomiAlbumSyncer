import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api } from '@/ApiInstance'

export const useSystemConfigStore = defineStore('systemConfig', () => {
  const starNoticeDismissed = ref<boolean | null>(null)

  async function fetchSystemConfig() {
    const config = await api.systemConfigController.getSystemConfig()
    starNoticeDismissed.value = config.starNoticeDismissed
    return config
  }

  async function dismissStarNotice() {
    starNoticeDismissed.value = true
    await api.systemConfigController.dismissStarNotice()
  }

  function reset() {
    starNoticeDismissed.value = null
  }

  return {
    starNoticeDismissed,
    fetchSystemConfig,
    dismissStarNotice,
    reset,
  }
})
