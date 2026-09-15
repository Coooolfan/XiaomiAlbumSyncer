import { computed, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { usePreferencesStore } from '@/stores/preferences'

const darkMediaQuery =
  typeof window !== 'undefined' ? window.matchMedia('(prefers-color-scheme: dark)') : null

const systemDark = ref(darkMediaQuery?.matches ?? false)

darkMediaQuery?.addEventListener('change', (event) => {
  systemDark.value = event.matches
})

export function useTheme() {
  const { themeMode } = storeToRefs(usePreferencesStore())

  const isDark = computed(
    () => themeMode.value === 'dark' || (themeMode.value === 'system' && systemDark.value),
  )

  return { themeMode, isDark }
}
