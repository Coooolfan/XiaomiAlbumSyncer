<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import Button from 'primevue/button'

const { t } = useI18n()

const emit = defineEmits<{
  (e: 'close'): void
}>()
</script>

<template>
  <div
    class="flex flex-wrap items-center gap-3 rounded-lg border border-slate-200/80 bg-white/70 px-4 py-3 backdrop-blur-sm dark:border-slate-800/80 dark:bg-slate-900/60"
  >
    <i class="pi pi-star shrink-0 text-amber-500" />
    <span class="min-w-0 flex-1 text-sm text-slate-600 dark:text-slate-300">
      {{ t('schedule.starNotice.message') }}
    </span>
    <div class="flex shrink-0 items-center gap-1.5">
      <Button
        as="a"
        href="https://github.com/coooolfan/xiaomialbumsyncer"
        target="_blank"
        rel="noopener noreferrer"
        icon="pi pi-github"
        :label="t('schedule.starNotice.goToGithub')"
        size="small"
      />
      <Button
        icon="pi pi-times"
        text
        rounded
        severity="secondary"
        :aria-label="t('schedule.starNotice.dismiss')"
        v-tooltip.bottom="t('schedule.starNotice.dismiss')"
        @click="emit('close')"
      />
    </div>
  </div>
</template>
