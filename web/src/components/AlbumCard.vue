<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { RECORDING_ALBUM_REMOTE_KEY } from '@/utils/album'

type Props = {
  name?: string
  remoteKey?: string
  assetCount?: number | null
  lastUpdateTime?: string | null
  shadow?: boolean
}

const props = defineProps<Props>()

const { t } = useI18n()

const isRecording = computed(() => props.remoteKey === RECORDING_ALBUM_REMOTE_KEY)
const displayName = computed(() => props.name ?? t('album.card.unnamed'))
const displayCount = computed(() => {
  if (props.assetCount == null) return t('album.card.countUnknown')
  const n = props.assetCount
  return t('album.card.assetCount', { n }, n)
})

const displayRelativeUpdate = computed(() => {
  if (!props.lastUpdateTime) return ''
  const d = new Date(props.lastUpdateTime)
  if (isNaN(d.getTime())) return ''
  const diffMs = Date.now() - d.getTime()
  const sec = Math.floor(diffMs / 1000)
  const min = Math.floor(sec / 60)
  const hour = Math.floor(min / 60)
  const day = Math.floor(hour / 24)
  const month = Math.floor(day / 30)
  const year = Math.floor(day / 365)
  if (sec < 60) return t('album.card.justNow')
  if (min < 60) return t('album.card.minutesAgo', { n: min }, min)
  if (hour < 24) return t('album.card.hoursAgo', { n: hour }, hour)
  if (day < 30) return t('album.card.daysAgo', { n: day }, day)
  if (month < 12) return t('album.card.monthsAgo', { n: month }, month)
  return t('album.card.yearsAgo', { n: year }, year)
})
</script>

<template>
  <div
    class="min-w-0 rounded-md border border-slate-200/70 px-3 py-2.5 transition-colors hover:bg-slate-100/60 dark:border-slate-700/60 dark:hover:bg-slate-800/40"
    :class="props.shadow ? 'opacity-50' : ''"
    v-tooltip="props.shadow ? t('album.card.orphanTip') : undefined"
  >
    <div class="flex min-w-0 items-center gap-2">
      <i
        class="pi shrink-0 text-[14px]"
        :class="[
          isRecording ? 'pi-microphone' : 'pi-images',
          props.shadow
            ? 'text-slate-300 dark:text-slate-600'
            : 'text-slate-500 dark:text-slate-400',
        ]"
      />
      <span
        class="min-w-0 flex-1 truncate text-[14px]"
        :class="
          props.shadow
            ? 'text-slate-500 line-through dark:text-slate-400'
            : 'text-slate-700 dark:text-slate-200'
        "
      >
        {{ displayName }}
      </span>
    </div>
    <div
      class="mt-2 flex items-center justify-between gap-2 text-[13px] text-slate-500 dark:text-slate-400"
    >
      <span class="truncate">{{ displayCount }}</span>
      <span v-if="displayRelativeUpdate" class="shrink-0">{{ displayRelativeUpdate }}</span>
    </div>
  </div>
</template>
