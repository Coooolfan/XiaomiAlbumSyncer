import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

type BgMode = 'lightRays' | 'silk'

const BG_KEY = 'app:bgMode'
const HEAT_KEY = 'app:optimizeHeatmap'

function hasStorage(): boolean {
  return typeof window !== 'undefined' && typeof window.localStorage !== 'undefined'
}

export const usePreferencesStore = defineStore('preferences', () => {
  const backgroundMode = ref<BgMode>('lightRays')
  const optimizeHeatmap = ref(true)
  const loaded = ref(false)

  function loadFromStorage() {
    if (!hasStorage()) {
      loaded.value = true
      return
    }
    try {
      const bgSaved = window.localStorage.getItem(BG_KEY) as BgMode | null
      if (bgSaved === 'silk' || bgSaved === 'lightRays') {
        backgroundMode.value = bgSaved
      }
    } catch {}

    try {
      const heatSaved = window.localStorage.getItem(HEAT_KEY)
      if (heatSaved === null) {
        optimizeHeatmap.value = true
      } else {
        optimizeHeatmap.value = !(heatSaved === '0' || heatSaved === 'false')
      }
    } catch {}

    loaded.value = true
  }

  function setBackgroundMode(mode: BgMode) {
    backgroundMode.value = mode
  }

  function toggleBackground() {
    backgroundMode.value = backgroundMode.value === 'lightRays' ? 'silk' : 'lightRays'
  }

  function setOptimizeHeatmap(value: boolean) {
    optimizeHeatmap.value = value
  }

  if (!loaded.value) {
    loadFromStorage()
  }

  watch(
    backgroundMode,
    (val) => {
      if (!loaded.value || !hasStorage()) return
      try {
        window.localStorage.setItem(BG_KEY, val)
      } catch {}
    },
    { immediate: false },
  )

  watch(
    optimizeHeatmap,
    (val) => {
      if (!loaded.value || !hasStorage()) return
      try {
        window.localStorage.setItem(HEAT_KEY, val ? '1' : '0')
      } catch {}
    },
    { immediate: false },
  )

  return {
    backgroundMode,
    optimizeHeatmap,
    loaded,
    loadFromStorage,
    setBackgroundMode,
    toggleBackground,
    setOptimizeHeatmap,
  }
})
