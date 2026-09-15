<script setup lang="ts">
import Button from 'primevue/button'
import Chip from 'primevue/chip'
import Menu from 'primevue/menu'
import Tag from 'primevue/tag'
import ToggleSwitch from 'primevue/toggleswitch'
import type { CrontabDto } from '@/__generated/model/dto'
import type { CrontabCurrentStats, CrontabHistoryGroup } from '@/__generated/model/static'
import { computed, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { api } from '@/ApiInstance'

const { t, locale } = useI18n()

type Crontab = CrontabDto['CrontabController/DEFAULT_CRONTAB']
type CrontabHistory = Crontab['histories'][number]

const props = defineProps<{
  crontab: Crontab
  albumOptions: ReadonlyArray<{ label: string; value: string; shadow?: boolean }>
  busy?: boolean
  expanded?: boolean
}>()

const emit = defineEmits<{
  (e: 'edit'): void
  (e: 'delete'): void
  (e: 'toggle'): void
  (e: 'execute'): void
  (e: 'executeExif'): void
  (e: 'executeRewriteFsTime'): void
  (e: 'clearHistory'): void
  (e: 'viewHistoryDetails', history: CrontabHistory): void
  (e: 'refresh'): void
  (e: 'toggleExpand'): void
}>()

const albumMap = computed<Record<string, { label: string; shadow: boolean }>>(() => {
  const map: Record<string, { label: string; shadow: boolean }> = {}
  for (const opt of props.albumOptions || []) {
    map[opt.value] = { label: opt.label, shadow: opt.shadow === true }
  }
  return map
})

function isShadowAlbum(id: number) {
  return albumMap.value[String(id)]?.shadow === true
}

function formatTime(time?: string) {
  if (!time) return '-'
  try {
    const d = new Date(time)
    if (Number.isNaN(d.getTime())) return time
    return d.toLocaleString(locale.value)
  } catch {
    return time
  }
}

function formatRelative(time?: string) {
  if (!time) return ''
  const d = new Date(time)
  if (Number.isNaN(d.getTime())) return ''
  const diffMs = Date.now() - d.getTime()
  const min = Math.floor(diffMs / 60000)
  const hour = Math.floor(min / 60)
  const day = Math.floor(hour / 24)
  if (min < 1) return t('schedule.row.justNow')
  if (min < 60) return t('schedule.row.minutesAgo', { n: min }, min)
  if (hour < 24) return t('schedule.row.hoursAgo', { n: hour }, hour)
  if (day < 30) return t('schedule.row.daysAgo', { n: day }, day)
  return d.toLocaleDateString(locale.value)
}

const recentHistories = computed(() => {
  const list = [...(props.crontab.histories || [])]
  list.sort((a, b) => (a.startTime < b.startTime ? 1 : -1))
  return list.slice(0, 5)
})

const lastHistory = computed(() => recentHistories.value[0])

const HISTORY_PAGE_SIZE = 6
const historyGroups = ref<ReadonlyArray<CrontabHistoryGroup>>([])
const historyPageCache = new Map<number, ReadonlyArray<CrontabHistoryGroup>>()
const historyGroupsTotal = ref(0)
const historyPageIndex = ref(0)
const historyLoading = ref(false)
const historyLoaded = ref(false)
const historyTotalPages = computed(() =>
  Math.max(1, Math.ceil(historyGroupsTotal.value / HISTORY_PAGE_SIZE)),
)

async function loadHistoryGroups() {
  const cached = historyPageCache.get(historyPageIndex.value)
  if (cached) {
    historyGroups.value = cached
    return
  }
  historyLoading.value = true
  try {
    const page = await api.crontabController.listCrontabHistoryGroups({
      crontabId: props.crontab.id,
      pageIndex: historyPageIndex.value,
      pageSize: HISTORY_PAGE_SIZE,
    })
    historyGroups.value = page.rows
    historyGroupsTotal.value = page.totalRowCount
    historyPageCache.set(historyPageIndex.value, page.rows)
    historyLoaded.value = true
  } catch {
    historyGroups.value = []
    historyGroupsTotal.value = 0
  } finally {
    historyLoading.value = false
  }
}

function gotoHistoryPage(delta: number) {
  const next = historyPageIndex.value + delta
  if (next < 0 || next >= historyTotalPages.value) return
  historyPageIndex.value = next
  loadHistoryGroups()
}

watch(
  () => props.expanded,
  (expanded) => {
    if (expanded && !historyLoaded.value) loadHistoryGroups()
  },
)

function openHistoryGroup(g: CrontabHistoryGroup) {
  if (g.runCount !== 1) return
  emit('viewHistoryDetails', {
    id: g.historyId,
    startTime: g.startTime,
    endTime: g.endTime,
    fetchedAllAssets: true,
    isCompleted: g.endTime != null,
    detailsCount: g.detailsCount,
  })
}

const lastRunStatus = computed(() => {
  const h = lastHistory.value
  if (!h) return null
  if (h.isCompleted)
    return {
      label: t('schedule.status.completed'),
      class: 'text-emerald-500',
      dot: 'bg-emerald-500',
    }
  if (!h.endTime)
    return { label: t('schedule.status.running'), class: 'text-blue-500', dot: 'bg-blue-500' }
  return { label: t('schedule.status.terminated'), class: 'text-amber-500', dot: 'bg-amber-500' }
})

const enabledOptionTags = computed(() => {
  const c = props.crontab.config
  if (!c) return [] as string[]
  const tags: string[] = []
  if (c.downloadImages) tags.push(t('schedule.options.images'))
  if (c.downloadVideos) tags.push(t('schedule.options.videos'))
  if (c.downloadAudios) tags.push(t('schedule.options.audios'))
  if (c.notify) tags.push(t('schedule.options.notify'))
  if (c.rewriteExifTime) tags.push(t('schedule.options.exif'))
  if (c.diffByTimeline) tags.push(t('schedule.options.diffByTimeline'))
  if (c.skipExistingFile) tags.push(t('schedule.options.skipExisting'))
  if (c.rewriteFileSystemTime) tags.push(t('schedule.options.rewriteFsTime'))
  if (c.checkSha1) tags.push(t('schedule.options.sha1'))
  return tags
})

const moreMenu = ref<InstanceType<typeof Menu> | null>(null)
const moreMenuOpen = ref(false)
const moreMenuItems = computed(() => {
  const items: Array<{ label: string; icon: string; command: () => void }> = []
  if (props.crontab.config?.rewriteExifTime) {
    items.push({
      label: t('schedule.row.menu.fillExif'),
      icon: 'pi pi-clock',
      command: () => emit('executeExif'),
    })
  }
  if (props.crontab.config?.rewriteFileSystemTime) {
    items.push({
      label: t('schedule.row.menu.rewriteFsTime'),
      icon: 'pi pi-history',
      command: () => emit('executeRewriteFsTime'),
    })
  }
  items.push({
    label: t('schedule.row.menu.clearHistory'),
    icon: 'pi pi-trash',
    command: () => emit('clearHistory'),
  })
  return items
})

function openMoreMenu(event: MouseEvent) {
  moreMenu.value?.toggle(event)
}

const currentStats = ref<CrontabCurrentStats | null>(null)
const polling = ref(false)
const lastFetchTime = ref(0)
const now = ref(Date.now())
let pollTimer: number | undefined
let nowTimer: number | undefined

async function fetchStats() {
  if (!props.crontab.id) return
  try {
    currentStats.value = await api.crontabController.getCrontabCurrentStats({
      crontabId: props.crontab.id,
    })
    if (!currentStats.value.ts) {
      emit('refresh')
    }
    lastFetchTime.value = Date.now()
  } catch (e: unknown) {
    console.debug('Failed to fetch stats', e)
    const msg = e instanceof Error ? e.message : String(e)
    if (msg.includes('没有正在运行')) {
      stopPolling()
      emit('refresh')
    }
  }
}

function startPolling() {
  if (polling.value) return
  polling.value = true
  fetchStats()
  pollTimer = window.setInterval(fetchStats, 1000)
  nowTimer = window.setInterval(() => {
    now.value = Date.now()
  }, 200)
}

function stopPolling() {
  polling.value = false
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = undefined
  }
  if (nowTimer) {
    clearInterval(nowTimer)
    nowTimer = undefined
  }
  currentStats.value = null
  lastFetchTime.value = 0
}

function getPercent(val?: number) {
  if (val === undefined || !currentStats.value?.assetCount) return 0
  return Math.min(100, Math.round((val / currentStats.value.assetCount) * 100))
}

const downloadPercent = computed(() => getPercent(currentStats.value?.downloadCompletedCount))

watch(
  () => props.crontab.running,
  (v) => {
    if (v) {
      startPolling()
    } else {
      stopPolling()
    }
  },
  { immediate: true },
)

onUnmounted(() => {
  stopPolling()
})
</script>

<template>
  <div class="group/row">
    <!-- Collapsed row -->
    <div
      class="flex h-11 cursor-pointer items-center gap-3 px-4 transition-colors hover:bg-slate-100/70 dark:hover:bg-slate-800/50"
      :class="expanded ? 'bg-slate-100/60 dark:bg-slate-800/40' : ''"
      @click="emit('toggleExpand')"
    >
      <!-- status toggle -->
      <button
        type="button"
        class="shrink-0 rounded-full p-0.5 transition-transform hover:scale-110 disabled:opacity-50"
        :disabled="busy"
        :title="
          crontab.enabled
            ? t('schedule.row.toggleHintEnabled')
            : t('schedule.row.toggleHintDisabled')
        "
        @click.stop="emit('toggle')"
      >
        <span
          v-if="crontab.running"
          class="block h-3.5 w-3.5 rounded-full border-2 border-blue-500 border-t-transparent animate-spin"
        />
        <span
          v-else-if="crontab.enabled"
          class="block h-3.5 w-3.5 rounded-full bg-emerald-500 ring-2 ring-emerald-500/25"
        />
        <span
          v-else
          class="block h-3.5 w-3.5 rounded-full border-[1.5px] border-slate-300 dark:border-slate-600"
        />
      </button>

      <!-- name + description -->
      <div class="flex min-w-0 flex-1 items-baseline gap-2">
        <span class="truncate text-[13px] font-medium text-slate-800 dark:text-slate-100">
          {{ crontab.name }}
        </span>
        <span
          v-if="crontab.description"
          class="hidden truncate text-xs text-slate-400 dark:text-slate-500 xl:inline"
        >
          {{ crontab.description }}
        </span>
      </div>

      <!-- running progress (inline) -->
      <div
        v-if="crontab.running && currentStats?.assetCount"
        class="hidden w-28 shrink-0 items-center gap-2 lg:flex"
      >
        <div class="h-1 flex-1 rounded-full bg-slate-200 dark:bg-slate-700">
          <div
            class="h-1 rounded-full bg-blue-500 transition-all duration-500"
            :style="{ width: downloadPercent + '%' }"
          />
        </div>
        <span class="font-mono text-[10px] text-slate-400">{{ downloadPercent }}%</span>
      </div>
      <span
        v-else-if="crontab.running"
        class="hidden shrink-0 text-[11px] text-blue-500 lg:inline"
        >{{ t('schedule.row.running') }}</span
      >

      <!-- hover actions -->
      <div
        class="flex shrink-0 items-center gap-0.5 opacity-0 transition-opacity delay-200 group-hover/row:opacity-100 group-hover/row:delay-0 focus-within:opacity-100 focus-within:delay-0"
        :class="expanded || moreMenuOpen ? 'opacity-100 delay-0' : ''"
      >
        <Button
          icon="pi pi-play-circle"
          severity="secondary"
          text
          rounded
          size="small"
          v-tooltip.bottom="t('schedule.row.executeNow')"
          @click.stop="emit('execute')"
        />
        <Button
          icon="pi pi-ellipsis-h"
          severity="secondary"
          text
          rounded
          size="small"
          v-tooltip.bottom="t('schedule.row.moreActions')"
          @click.stop="openMoreMenu"
        />
        <Button
          icon="pi pi-pencil"
          severity="secondary"
          text
          rounded
          size="small"
          v-tooltip.bottom="t('common.action.edit')"
          @click.stop="emit('edit')"
        />
        <Button
          icon="pi pi-trash"
          severity="danger"
          text
          rounded
          size="small"
          v-tooltip.bottom="t('common.action.delete')"
          @click.stop="emit('delete')"
        />
      </div>

      <!-- cron -->
      <span
        class="hidden shrink-0 font-mono text-[11px] text-slate-400 dark:text-slate-500 lg:inline"
      >
        {{ crontab.config?.expression }}
      </span>

      <!-- account -->
      <span
        class="hidden w-20 shrink-0 truncate text-[11px] text-slate-400 dark:text-slate-500 2xl:inline"
      >
        {{ crontab.account?.nickname || '-' }}
      </span>

      <!-- last run -->
      <span
        class="hidden shrink-0 items-center gap-1.5 text-[11px] sm:flex"
        :class="lastRunStatus ? lastRunStatus.class : 'text-slate-300 dark:text-slate-600'"
      >
        <template v-if="lastRunStatus">
          <span class="h-1.5 w-1.5 rounded-full" :class="lastRunStatus.dot" />
          {{ formatRelative(lastHistory?.endTime || lastHistory?.startTime) }}
        </template>
        <template v-else>{{ t('schedule.row.neverRun') }}</template>
      </span>

      <i
        class="pi shrink-0 text-[10px] text-slate-400 transition-transform duration-200"
        :class="expanded ? 'pi-chevron-up' : 'pi-chevron-down'"
      />
    </div>

    <!-- Expanded detail -->
    <div
      v-if="expanded"
      class="border-t border-slate-200/60 bg-slate-50/50 px-4 py-4 dark:border-slate-800/60 dark:bg-slate-900/30"
    >
      <div class="grid gap-5 lg:grid-cols-2">
        <!-- left: config -->
        <div class="min-w-0 space-y-3">
          <div v-if="crontab.description" class="text-[13px] text-slate-500 dark:text-slate-400">
            {{ crontab.description }}
          </div>

          <dl class="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1.5 text-[13px]">
            <dt class="text-slate-400 dark:text-slate-500">{{ t('schedule.row.account') }}</dt>
            <dd class="truncate text-slate-700 dark:text-slate-200">
              {{ crontab.account?.nickname || '-' }}
            </dd>
            <dt class="text-slate-400 dark:text-slate-500">{{ t('schedule.row.schedule') }}</dt>
            <dd class="font-mono text-xs text-slate-700 dark:text-slate-200">
              {{ crontab.config?.expression }}
              <span class="ml-1 font-sans text-slate-400 dark:text-slate-500"
                >({{ crontab.config?.timeZone }})</span
              >
            </dd>
            <dt class="text-slate-400 dark:text-slate-500">{{ t('schedule.row.targetPath') }}</dt>
            <dd class="truncate font-mono text-xs text-slate-700 dark:text-slate-200">
              {{ crontab.config?.targetPath || '-' }}
            </dd>
            <template v-if="crontab.config?.expressionTargetPath">
              <dt class="text-slate-400 dark:text-slate-500">
                {{ t('schedule.row.expressionPath') }}
              </dt>
              <dd class="truncate font-mono text-xs text-slate-700 dark:text-slate-200">
                {{ crontab.config.expressionTargetPath }}
              </dd>
            </template>
            <dt class="text-slate-400 dark:text-slate-500">{{ t('common.field.status') }}</dt>
            <dd class="flex items-center gap-2">
              <ToggleSwitch
                :modelValue="crontab.enabled"
                :disabled="busy"
                @update:modelValue="() => emit('toggle')"
              />
              <span
                class="text-xs"
                :class="
                  crontab.enabled
                    ? 'text-emerald-600 dark:text-emerald-400'
                    : 'text-slate-400 dark:text-slate-500'
                "
              >
                {{ crontab.enabled ? t('schedule.row.enabled') : t('schedule.row.disabled') }}
              </span>
            </dd>
          </dl>

          <div class="flex flex-wrap items-center gap-1.5">
            <span
              v-for="tag in enabledOptionTags"
              :key="tag"
              class="rounded border border-slate-200/80 px-1.5 py-0.5 text-[11px] text-slate-500 dark:border-slate-700/80 dark:text-slate-400"
            >
              {{ tag }}
            </span>
            <span
              v-if="enabledOptionTags.length === 0"
              class="text-[11px] text-slate-400 dark:text-slate-500"
              >{{ t('schedule.row.noOptions') }}</span
            >
          </div>

          <div class="flex flex-wrap items-center gap-1.5">
            <Chip
              v-for="id in crontab.albumIds"
              :key="id"
              :label="albumMap[String(id)]?.label || String(id)"
              class="!h-6 !px-2 text-[11px]"
              v-tooltip="isShadowAlbum(id) ? t('schedule.row.shadowAlbumTip') : undefined"
              :class="
                isShadowAlbum(id)
                  ? '!bg-red-50 !text-red-700 !line-through ring-1 ring-red-200 dark:!bg-red-950/30 dark:!text-red-300 dark:ring-red-900/70'
                  : '!bg-slate-200/60 dark:!bg-slate-700/60'
              "
            />
            <span
              v-if="!crontab.albumIds || crontab.albumIds.length === 0"
              class="text-[11px] text-slate-400 dark:text-slate-500"
              >{{ t('schedule.row.noAlbums') }}</span
            >
          </div>
        </div>

        <!-- right: stats + history -->
        <div class="min-w-0 space-y-3">
          <div
            v-if="crontab.running"
            class="rounded-md border border-blue-200/60 bg-blue-50/60 p-3 dark:border-blue-900/50 dark:bg-blue-950/20"
          >
            <div class="mb-2 flex items-center justify-between">
              <div class="flex items-center gap-2">
                <i class="pi pi-spin pi-spinner text-xs text-blue-500" />
                <span class="text-xs font-medium text-blue-700 dark:text-blue-300">{{
                  t('schedule.row.executing')
                }}</span>
              </div>
              <div class="flex flex-col items-end">
                <span
                  class="font-mono text-[10px] text-blue-600/60 dark:text-blue-300/70"
                  :title="t('schedule.row.statsFetchedAt')"
                  >{{
                    currentStats?.ts ? new Date(currentStats.ts).toLocaleTimeString(locale) : ''
                  }}</span
                >
                <span v-if="lastFetchTime" class="font-mono text-[9px] text-blue-400">
                  {{
                    t('schedule.row.statsAge', {
                      n: ((now - lastFetchTime) / 1000).toFixed(1),
                    })
                  }}
                </span>
              </div>
            </div>

            <div v-if="currentStats" class="space-y-2">
              <div
                class="flex items-center justify-between border-b border-blue-100 pb-1 text-xs dark:border-blue-900/60"
              >
                <span class="text-slate-500 dark:text-slate-400">{{
                  t('schedule.row.totalAssets')
                }}</span>
                <span class="font-mono font-bold text-blue-700 dark:text-blue-300">{{
                  currentStats.assetCount ?? '-'
                }}</span>
              </div>

              <div v-if="currentStats.downloadCompletedCount !== undefined" class="text-xs">
                <div class="mb-1 flex items-center justify-between">
                  <span class="text-slate-500 dark:text-slate-400">{{
                    t('schedule.row.downloadProgress')
                  }}</span>
                  <span class="font-mono text-slate-700 dark:text-slate-200"
                    >{{ currentStats.downloadCompletedCount }}
                    <span v-if="currentStats.assetCount" class="text-slate-400 dark:text-slate-500"
                      >/ {{ currentStats.assetCount }}</span
                    ></span
                  >
                </div>
                <div
                  v-if="currentStats.assetCount"
                  class="h-1 w-full rounded-full bg-blue-200 dark:bg-blue-900"
                >
                  <div
                    class="h-1 rounded-full bg-blue-500 transition-all duration-500"
                    :style="{ width: downloadPercent + '%' }"
                  />
                </div>
              </div>

              <div
                v-if="currentStats.sha1VerifiedCount !== undefined && crontab.config?.checkSha1"
                class="text-xs"
              >
                <div class="mb-1 flex items-center justify-between">
                  <span class="text-slate-500 dark:text-slate-400">{{
                    t('schedule.options.sha1')
                  }}</span>
                  <span class="font-mono text-slate-700 dark:text-slate-200">{{
                    currentStats.sha1VerifiedCount
                  }}</span>
                </div>
                <div
                  v-if="currentStats.assetCount"
                  class="h-1 w-full rounded-full bg-blue-200 dark:bg-blue-900"
                >
                  <div
                    class="h-1 rounded-full bg-purple-500 transition-all duration-500"
                    :style="{ width: getPercent(currentStats.sha1VerifiedCount) + '%' }"
                  />
                </div>
              </div>

              <div
                v-if="currentStats.exifFilledCount !== undefined && crontab.config?.rewriteExifTime"
                class="text-xs"
              >
                <div class="mb-1 flex items-center justify-between">
                  <span class="text-slate-500 dark:text-slate-400">{{
                    t('schedule.row.exifProgress')
                  }}</span>
                  <span class="font-mono text-slate-700 dark:text-slate-200">{{
                    currentStats.exifFilledCount
                  }}</span>
                </div>
                <div
                  v-if="currentStats.assetCount"
                  class="h-1 w-full rounded-full bg-blue-200 dark:bg-blue-900"
                >
                  <div
                    class="h-1 rounded-full bg-amber-500 transition-all duration-500"
                    :style="{ width: getPercent(currentStats.exifFilledCount) + '%' }"
                  />
                </div>
              </div>

              <div
                v-if="
                  currentStats.fsTimeUpdatedCount !== undefined &&
                  crontab.config?.rewriteFileSystemTime
                "
                class="text-xs"
              >
                <div class="mb-1 flex items-center justify-between">
                  <span class="text-slate-500 dark:text-slate-400">{{
                    t('schedule.row.fsTimeProgress')
                  }}</span>
                  <span class="font-mono text-slate-700 dark:text-slate-200">{{
                    currentStats.fsTimeUpdatedCount
                  }}</span>
                </div>
                <div
                  v-if="currentStats.assetCount"
                  class="h-1 w-full rounded-full bg-blue-200 dark:bg-blue-900"
                >
                  <div
                    class="h-1 rounded-full bg-emerald-500 transition-all duration-500"
                    :style="{ width: getPercent(currentStats.fsTimeUpdatedCount) + '%' }"
                  />
                </div>
              </div>
            </div>
            <div v-else class="py-1 text-xs text-slate-400 dark:text-slate-500">
              {{ t('schedule.row.fetchingRemote') }}
            </div>
          </div>

          <div>
            <div class="mb-1.5 flex items-center justify-between">
              <span
                class="text-[11px] font-medium uppercase tracking-wider text-slate-400 dark:text-slate-500"
              >
                {{ t('schedule.row.recentRuns') }}
              </span>
              <div
                v-if="historyGroupsTotal > HISTORY_PAGE_SIZE"
                class="flex items-center mr-2 gap-0.5 text-[9px] leading-none text-slate-400 dark:text-slate-500"
              >
                <button
                  type="button"
                  class="transition-colors hover:text-slate-600 disabled:opacity-40 dark:hover:text-slate-200"
                  :disabled="historyPageIndex === 0 || historyLoading"
                  @click="gotoHistoryPage(-1)"
                >
                  <i class="pi pi-angle-left text-[7px]" />
                </button>
                <span class="tabular-nums">{{ historyPageIndex + 1 }}/{{ historyTotalPages }}</span>
                <button
                  type="button"
                  class="transition-colors hover:text-slate-600 disabled:opacity-40 dark:hover:text-slate-200"
                  :disabled="historyPageIndex + 1 >= historyTotalPages || historyLoading"
                  @click="gotoHistoryPage(1)"
                >
                  <i class="pi pi-angle-right text-[7px]" />
                </button>
              </div>
            </div>
            <div
              v-if="historyLoading && !historyLoaded"
              class="text-xs text-slate-400 dark:text-slate-500"
            >
              {{ t('common.status.loading') }}
            </div>
            <div
              v-else-if="historyGroups.length === 0"
              class="text-xs text-slate-400 dark:text-slate-500"
            >
              {{ t('schedule.row.noHistory') }}
            </div>
            <div v-else class="space-y-0.5">
              <button
                v-for="g in historyGroups"
                :key="g.historyId + '-' + g.runCount"
                type="button"
                class="flex w-full items-center justify-between rounded-md px-2 py-1 text-left transition-colors"
                :class="
                  g.runCount === 1
                    ? 'hover:bg-slate-200/60 dark:hover:bg-slate-800/70'
                    : 'cursor-default'
                "
                @click="openHistoryGroup(g)"
              >
                <div class="flex min-w-0 items-center gap-2">
                  <span
                    class="h-1.5 w-1.5 shrink-0 rounded-full"
                    :class="g.endTime ? 'bg-emerald-500' : 'bg-blue-500'"
                  />
                  <span class="truncate text-xs text-slate-600 dark:text-slate-300">
                    {{ formatTime(g.startTime) }} → {{ formatTime(g.endTime) }}
                  </span>
                </div>
                <div class="flex shrink-0 items-center gap-2">
                  <span class="text-[11px] text-slate-400 dark:text-slate-500">
                    {{
                      g.runCount > 1
                        ? t('schedule.row.checks', { n: g.runCount }, g.runCount)
                        : t('schedule.row.assets', { n: g.detailsCount }, g.detailsCount)
                    }}
                  </span>
                  <Tag
                    :severity="g.runCount > 1 ? 'secondary' : g.endTime ? 'success' : 'info'"
                    :value="
                      g.endTime ? t('schedule.status.completed') : t('schedule.status.running')
                    "
                    class="!h-5 !px-1.5 !text-[10px]"
                  />
                </div>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <Menu
      ref="moreMenu"
      :model="moreMenuItems"
      popup
      @show="moreMenuOpen = true"
      @hide="moreMenuOpen = false"
    />
  </div>
</template>
