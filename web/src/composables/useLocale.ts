import { watch } from 'vue'
import { usePrimeVue } from 'primevue/config'
import { storeToRefs } from 'pinia'
import { usePreferencesStore } from '@/stores/preferences'
import { i18n, primevueLocales } from '@/i18n'
import router from '@/router'

export function useLocale() {
  const { locale } = storeToRefs(usePreferencesStore())
  const primevue = usePrimeVue()

  watch(
    locale,
    (val) => {
      i18n.global.locale.value = val
      try {
        document.documentElement.lang = val
      } catch {}
      primevue.config.locale = {
        ...(primevue.config.locale ?? {}),
        ...primevueLocales[val],
      } as typeof primevue.config.locale
      const titleKey = router.currentRoute.value.meta?.titleKey
      if (typeof titleKey === 'string') {
        document.title = i18n.global.t(titleKey)
      }
    },
    { immediate: true },
  )
}
