<script setup lang="ts">
import { computed, defineAsyncComponent, ref, watch, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  DEFAULT_SETTING_SECTION,
  SETTING_SECTION_GROUPS,
  SETTING_SECTION_ITEMS,
  SETTING_SECTION_KEYS,
} from '@/utils/settingSections'
import SystemConfigCard from '@/components/settings/SystemConfigCard.vue'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const sectionComponents: Record<string, Component> = {
  system: SystemConfigCard,
  appearance: defineAsyncComponent(() => import('@/components/settings/AppearanceCard.vue')),
  notify: defineAsyncComponent(() => import('@/components/settings/NotifyConfigCard.vue')),
  accounts: defineAsyncComponent(() => import('@/components/settings/PassTokenCard.vue')),
  password: defineAsyncComponent(() => import('@/components/settings/PasswordCard.vue')),
  passkey: defineAsyncComponent(() => import('@/components/settings/PasskeyCard.vue')),
  mcp: defineAsyncComponent(() => import('@/components/settings/McpTokenCard.vue')),
  import: defineAsyncComponent(() => import('@/components/settings/ImportV2Card.vue')),
  about: defineAsyncComponent(() => import('@/components/settings/OpenSourceCard.vue')),
}

const activeKey = ref<string>(
  SETTING_SECTION_KEYS.has(String(route.query.section))
    ? String(route.query.section)
    : DEFAULT_SETTING_SECTION,
)

const activeComponent = computed(() => sectionComponents[activeKey.value] ?? SystemConfigCard)

const activeSection = computed(() => {
  for (const group of SETTING_SECTION_GROUPS) {
    const item = group.items.find((i) => i.key === activeKey.value)
    if (item) return { item, groupLabelKey: group.labelKey }
  }
  return { item: SETTING_SECTION_ITEMS[0], groupLabelKey: SETTING_SECTION_GROUPS[0].labelKey }
})

function select(key: string) {
  activeKey.value = key
}

watch(activeKey, (key) => {
  if (route.query.section !== key) {
    router.replace({ query: { ...route.query, section: key } })
  }
})

watch(
  () => route.query.section,
  (section) => {
    const key = String(section ?? '')
    if (SETTING_SECTION_KEYS.has(key) && key !== activeKey.value) {
      activeKey.value = key
    }
  },
)
</script>

<template>
  <div class="mx-auto max-w-4xl px-4 py-6 sm:px-6">
    <div class="min-w-0">
      <h1 class="truncate text-lg font-semibold tracking-tight text-slate-800 dark:text-slate-100">
        {{ t(activeSection.item.labelKey) }}
      </h1>
      <p class="mt-0.5 text-xs text-slate-400 dark:text-slate-500">
        {{ t('nav.settings') }} · {{ t(activeSection.groupLabelKey) }}
      </p>
    </div>

    <!-- Mobile section picker -->
    <div class="mt-5 w-full overflow-x-auto md:hidden">
      <div class="flex gap-1">
        <button
          v-for="item in SETTING_SECTION_ITEMS"
          :key="item.key"
          type="button"
          class="shrink-0 rounded-md px-2.5 py-1.5 text-[13px] transition-colors"
          :class="
            item.key === activeKey
              ? 'bg-slate-200/70 text-slate-900 dark:bg-slate-700/60 dark:text-slate-100 font-medium'
              : 'text-slate-500 dark:text-slate-400'
          "
          @click="select(item.key)"
        >
          {{ t(item.labelKey) }}
        </button>
      </div>
    </div>

    <!-- Active section -->
    <div
      class="mt-5 min-w-0 rounded-lg border border-slate-200/80 bg-white/70 p-5 backdrop-blur-sm dark:border-slate-800/80 dark:bg-slate-900/60"
    >
      <component :is="activeComponent" :key="activeKey" />
    </div>
  </div>
</template>
