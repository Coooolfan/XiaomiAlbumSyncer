import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { api } from '@/ApiInstance'

export type DataPoint = { timeStamp: number; count: number }

type DayClickPayload = {
  date: Date
  dateStr: string
  count: number
  level: 0 | 1 | 2 | 3 | 4
}

type UseHeatmapTimelineOptions = {
  albumIds: () => number[]
  optimizeHeatmap: () => boolean
}

function formatDateInput(d: Date) {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function parseDateToLocalTimestamp(dateStr: string) {
  const parts = dateStr.split('-')
  const y = Number(parts[0] ?? '1970')
  const m = Number(parts[1] ?? '1')
  const d = Number(parts[2] ?? '1')
  const dt = new Date(y || 1970, (m || 1) - 1, d || 1)
  dt.setHours(0, 0, 0, 0)
  return dt.getTime()
}

function quantile(values: number[], q: number): number {
  if (!values || values.length === 0) return 0
  const arr = [...values].sort((a, b) => a - b)
  const pos = (arr.length - 1) * q
  const base = Math.floor(pos)
  const rest = pos - base
  const v0 = arr[base] ?? 0
  const v1 = arr[base + 1]
  if (v1 !== undefined) {
    return v0 + rest * (v1 - v0)
  }
  return v0
}

export function useHeatmapTimeline({ albumIds, optimizeHeatmap }: UseHeatmapTimelineOptions) {
  const labelText = ref('一年活跃度')
  const weekStartNum = ref(1)
  const rangeDaysNum = ref(365)
  const endDateStr = ref(formatDateInput(new Date()))
  const rawTimelineMap = ref<Record<string, number>>({})

  const tip = ref('')
  let tipHideTimer: number | undefined

  const dataPoints = computed<DataPoint[]>(() => {
    const entries = Object.entries(rawTimelineMap.value || {})
    if (entries.length === 0) return []

    const positiveValues = entries
      .map(([, v]) => (Number.isFinite(v) ? Number(v) : 0))
      .filter((v) => v > 0)

    let upper = 0
    if (optimizeHeatmap() && positiveValues.length >= 5) {
      upper = quantile(positiveValues, 0.95)
    }

    return entries
      .map(([dateStr, countRaw]) => {
        const count = upper > 0 ? Math.min(countRaw, upper) : countRaw
        return {
          timeStamp: parseDateToLocalTimestamp(dateStr),
          count,
        }
      })
      .sort((a, b) => a.timeStamp - b.timeStamp)
  })

  function onDayClick(payload: DayClickPayload) {
    tip.value = `${payload.dateStr} · ${payload.count}`
    if (tipHideTimer) window.clearTimeout(tipHideTimer)
    tipHideTimer = window.setTimeout(() => {
      tip.value = ''
    }, 2500)
  }

  async function fetchTimeline() {
    const ids = albumIds()
    const end = new Date(endDateStr.value)
    end.setHours(0, 0, 0, 0)
    const start = new Date(end.getTime() - (Math.max(1, rangeDaysNum.value) - 1) * 24 * 3600 * 1000)
    const startStr = formatDateInput(start)

    const resp = await api.albumsController.fetchDateMap({
      albumIds: ids.length > 0 ? ids : undefined,
      start: startStr,
      end: endDateStr.value,
    })

    rawTimelineMap.value = resp as Record<string, number>
  }

  let fetchTimer: number | undefined
  function scheduleFetch() {
    if (fetchTimer) window.clearTimeout(fetchTimer)
    fetchTimer = window.setTimeout(() => {
      fetchTimeline().catch((err) => {
        console.error('获取时间线数据失败', err)
      })
    }, 150)
  }

  watch([rangeDaysNum, endDateStr], scheduleFetch)

  onBeforeUnmount(() => {
    if (fetchTimer) window.clearTimeout(fetchTimer)
    if (tipHideTimer) window.clearTimeout(tipHideTimer)
  })

  return {
    labelText,
    weekStartNum,
    rangeDaysNum,
    endDateStr,
    dataPoints,
    tip,
    onDayClick,
    fetchTimeline,
    scheduleFetch,
  }
}
