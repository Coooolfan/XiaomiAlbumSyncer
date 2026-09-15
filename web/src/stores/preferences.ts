import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import { detectLocale, type AppLocale } from '@/i18n'

export type ThemeMode = 'system' | 'light' | 'dark'

const THEME_KEY = 'app:themeMode'
const LEGACY_BG_KEY = 'app:bgMode'
const HEAT_KEY = 'app:optimizeHeatmap'
const LOCALE_KEY = 'app:locale'

function hasStorage(): boolean {
  return typeof window !== 'undefined' && typeof window.localStorage !== 'undefined'
}

export const usePreferencesStore = defineStore('preferences', () => {
  const themeMode = ref<ThemeMode>('system')
  const optimizeHeatmap = ref(true)
  const locale = ref<AppLocale>(detectLocale())
  const loaded = ref(false)

  function loadFromStorage() {
    if (!hasStorage()) {
      loaded.value = true
      return
    }
    try {
      window.localStorage.removeItem(LEGACY_BG_KEY)
      const themeSaved = window.localStorage.getItem(THEME_KEY) as ThemeMode | null
      if (themeSaved === 'system' || themeSaved === 'light' || themeSaved === 'dark') {
        themeMode.value = themeSaved
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

    try {
      const localeSaved = window.localStorage.getItem(LOCALE_KEY)
      if (localeSaved === 'zh-CN' || localeSaved === 'en-US') {
        locale.value = localeSaved
      }
    } catch {}

    loaded.value = true
  }

  function setThemeMode(mode: ThemeMode) {
    themeMode.value = mode
  }

  function setOptimizeHeatmap(value: boolean) {
    optimizeHeatmap.value = value
  }

  function setLocale(value: AppLocale) {
    locale.value = value
  }

  if (!loaded.value) {
    loadFromStorage()
  }

  watch(
    themeMode,
    (val) => {
      if (!loaded.value || !hasStorage()) return
      try {
        window.localStorage.setItem(THEME_KEY, val)
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

  watch(
    locale,
    (val) => {
      if (!loaded.value || !hasStorage()) return
      try {
        window.localStorage.setItem(LOCALE_KEY, val)
      } catch {}
    },
    { immediate: false },
  )

  return {
    themeMode,
    optimizeHeatmap,
    locale,
    loaded,
    loadFromStorage,
    setThemeMode,
    setOptimizeHeatmap,
    setLocale,
  }
})
