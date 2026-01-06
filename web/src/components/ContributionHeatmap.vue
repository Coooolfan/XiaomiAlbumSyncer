<script setup lang="ts">
import { computed, toRefs, ref, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'

type DataPoint = {
  timeStamp: number
  count: number
}

const props = withDefaults(
  defineProps<{
    data: DataPoint[]
    label?: string
    // 周起始日: 0 = 周日 ... 6 = 周六. 默认周一.
    weekStart?: number
    // 显示范围. 如果省略start，则为end - (rangeDays - 1)
    start?: string | number | Date
    end?: string | number | Date
    // 仅在省略start时使用. 默认365天.
    rangeDays?: number
    // 视觉尺寸
    cellSize?: number
    gap?: number
    // 响应式尺寸
    responsive?: boolean
    minCellSize?: number
    maxCellSize?: number
    minGap?: number
    maxGap?: number
    // 配色方案: 'emerald' | 'blue' | 'rose' | 'slate'
    scheme?: 'emerald' | 'blue' | 'rose' | 'slate'
  }>(),
  {
    weekStart: 1,
    rangeDays: 365,
    cellSize: 12,
    gap: 3,
    responsive: true,
    minCellSize: 10,
    maxCellSize: 16,
    minGap: 2,
    maxGap: 4,
    scheme: 'emerald',
  },
)

const {
  data,
  weekStart,
  start,
  end,
  rangeDays,
  cellSize,
  gap,
  scheme,
  responsive,
  minCellSize,
  maxCellSize,
  minGap,
  maxGap,
} = toRefs(props)

const emit = defineEmits<{
  (
    e: 'day-click',
    payload: {
      date: Date
      dateStr: string
      count: number
      level: 0 | 1 | 2 | 3 | 4
    },
  ): void
}>()

function toDate(input?: string | number | Date): Date | undefined {
  if (input === undefined) return undefined
  const d = new Date(input)
  return isNaN(d.getTime()) ? undefined : d
}

const endDate = computed(() => {
  const e = toDate(end?.value)
  const now = new Date()
  const d = e ?? now
  d.setHours(0, 0, 0, 0)
  return d
})
const startDate = computed(() => {
  const s = toDate(start?.value)
  const d = s
    ? new Date(s)
    : new Date(endDate.value.getTime() - (Math.max(1, rangeDays.value) - 1) * 24 * 3600 * 1000)
  d.setHours(0, 0, 0, 0)
  return d
})

function alignToWeekStart(d: Date, ws: number): Date {
  const out = new Date(d)
  const day = out.getDay()
  const diff = (day - ws + 7) % 7
  out.setDate(out.getDate() - diff)
  out.setHours(0, 0, 0, 0)
  return out
}
function alignToWeekEnd(d: Date, ws: number): Date {
  const out = new Date(d)
  const weekEnd = (ws + 6) % 7
  const day = out.getDay()
  const diff = (weekEnd - day + 7) % 7
  out.setDate(out.getDate() + diff)
  out.setHours(0, 0, 0, 0)
  return out
}

function keyOf(date: Date): string {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

const gridStart = computed(() => alignToWeekStart(startDate.value, weekStart.value))
const gridEnd = computed(() => alignToWeekEnd(endDate.value, weekStart.value))

const allDays = computed(() => {
  const out: Date[] = []
  const cur = new Date(gridStart.value)
  while (cur <= gridEnd.value) {
    out.push(new Date(cur))
    cur.setDate(cur.getDate() + 1)
  }
  return out
})

const mapCounts = computed(() => {
  const m = new Map<string, number>()
  for (const item of data.value ?? []) {
    if (!item || typeof item.timeStamp !== 'number') continue
    const d = new Date(item.timeStamp)
    if (isNaN(d.getTime())) continue
    d.setHours(0, 0, 0, 0)
    const k = keyOf(d)
    m.set(k, (m.get(k) ?? 0) + (Number.isFinite(item.count) ? item.count : 0))
  }
  return m
})

const maxCount = computed(() => {
  let max = 0
  const s = startDate.value.getTime()
  const e = endDate.value.getTime()
  mapCounts.value.forEach((c, k) => {
    const d = new Date(k).getTime()
    if (d >= s && d <= e) {
      if (c > max) max = c
    }
  })
  return max
})

function levelForCount(count: number, max: number): 0 | 1 | 2 | 3 | 4 {
  if (!max || max <= 0 || !Number.isFinite(count) || count <= 0) return 0
  const n = count / max
  if (n >= 0.8) return 4
  if (n >= 0.6) return 3
  if (n >= 0.35) return 2
  return 1
}

const weeks = computed(() => {
  // 构建周作为最多7天的列，但隐藏超出范围的日期（无占位符）
  const result: {
    days: { date: Date; dateStr: string; count: number; level: 0 | 1 | 2 | 3 | 4 }[]
    monthLabel?: string
  }[] = []
  const labeledMonths = new Set<string>()

  const s = startDate.value.getTime()
  const e = endDate.value.getTime()
  const weekEndDay = (weekStart.value + 6) % 7
  let week: {
    days: { date: Date; dateStr: string; count: number; level: 0 | 1 | 2 | 3 | 4 }[]
    monthLabel?: string
  } = { days: [] }

  const max = maxCount.value

  for (const d of allDays.value) {
    const k = keyOf(d)
    const inRange = d.getTime() >= s && d.getTime() <= e
    if (inRange) {
      const count = mapCounts.value.get(k) ?? 0
      const level = levelForCount(count, max)
      week.days.push({ date: d, dateStr: k, count, level })
    }

    const isWeekEnd = d.getDay() === weekEndDay
    if (isWeekEnd) {
      if (week.days.length > 0) {
        // 月份标签逻辑，每月去重
        const labelFirst = week.days[0]!.date
        const firstKey = `${labelFirst.getFullYear()}-${labelFirst.getMonth()}`
        const firstOfMonthInWeek = week.days.find((dd) => dd.date.getDate() === 1)
        if (firstOfMonthInWeek) {
          const key = `${firstOfMonthInWeek.date.getFullYear()}-${firstOfMonthInWeek.date.getMonth()}`
          if (!labeledMonths.has(key)) {
            week.monthLabel = monthShort(firstOfMonthInWeek.date)
            labeledMonths.add(key)
          }
        } else {
          const prev = result[result.length - 1]?.days?.[0]?.date
          if (!prev || labelFirst.getMonth() !== prev.getMonth()) {
            if (labelFirst.getDate() <= 7) {
              if (!labeledMonths.has(firstKey)) {
                week.monthLabel = monthShort(labelFirst)
                labeledMonths.add(firstKey)
              }
            }
          }
        }
        result.push(week)
      }
      week = { days: [] }
    }
  }

  // 如果有任何范围内的日期，推送尾随的部分周
  if (week.days.length > 0) {
    const labelFirst = week.days[0]!.date
    const firstKey = `${labelFirst.getFullYear()}-${labelFirst.getMonth()}`
    const firstOfMonthInWeek = week.days.find((dd) => dd.date.getDate() === 1)
    if (firstOfMonthInWeek) {
      const key = `${firstOfMonthInWeek.date.getFullYear()}-${firstOfMonthInWeek.date.getMonth()}`
      if (!labeledMonths.has(key)) {
        week.monthLabel = monthShort(firstOfMonthInWeek.date)
        labeledMonths.add(key)
      }
    } else {
      const prev = result[result.length - 1]?.days?.[0]?.date
      if (!prev || labelFirst.getMonth() !== prev.getMonth()) {
        if (labelFirst.getDate() <= 7) {
          if (!labeledMonths.has(firstKey)) {
            week.monthLabel = monthShort(labelFirst)
            labeledMonths.add(firstKey)
          }
        }
      }
    }
    result.push(week)
  }

  return result
})

const containerEl = ref<HTMLElement | null>(null)
const measuredCell = ref(Math.max(8, cellSize.value))
const measuredGap = ref(Math.max(1, gap.value))
let ro: ResizeObserver | null = null

const weeksCount = computed(() => weeks.value.length)

function clamp(n: number, min: number, max: number) {
  return Math.min(max, Math.max(min, n))
}

function recomputeLayout() {
  const cols = weeksCount.value || 1
  const width = containerEl.value?.clientWidth ?? 0
  if (!responsive.value || width <= 0 || cols <= 0) {
    measuredCell.value = Math.max(8, cellSize.value)
    measuredGap.value = Math.max(1, gap.value)
    return
  }
  const minC = Math.max(4, minCellSize.value ?? 10)
  const maxC = Math.max(minC, maxCellSize.value ?? 16)
  const minG = Math.max(1, minGap.value ?? 2)
  const maxG = Math.max(minG, maxGap.value ?? 4)

  // 第一遍：尝试用最小间隙填充
  let cell = Math.floor((width - (cols - 1) * minG) / cols)
  if (cell < minC) {
    // 允许缩小到minC以下以避免空间紧张时溢出
    cell = Math.max(4, cell)
    measuredCell.value = cell
    measuredGap.value = minG
    return
  }
  cell = clamp(cell, minC, maxC)

  // 计算间隙以填充宽度
  let gapPx = cols > 1 ? (width - cell * cols) / (cols - 1) : 0
  gapPx = clamp(Math.round(gapPx), minG, maxG)

  // 使用钳位间隙重新调整单元格
  cell = clamp(Math.floor((width - gapPx * (cols - 1)) / cols), 4, maxC)

  measuredCell.value = cell
  measuredGap.value = gapPx
}

onMounted(() => {
  ro = new ResizeObserver(() => {
    recomputeLayout()
  })
  if (containerEl.value) ro.observe(containerEl.value)
  recomputeLayout()
})

onBeforeUnmount(() => {
  ro?.disconnect()
  ro = null
})

watch(
  [weeksCount, cellSize, gap, responsive, minCellSize, maxCellSize, minGap, maxGap],
  async () => {
    await nextTick()
    recomputeLayout()
  },
)

function monthShort(d: Date): string {
  return String(d.getMonth() + 1).padStart(2, '0')
}

const rangeText = computed(() => {
  const s = startDate.value
  const e = endDate.value
  const fmt = (d: Date) =>
    d.toLocaleDateString(undefined, { year: '2-digit', month: '2-digit', day: '2-digit' })
  return `${fmt(s)} - ${fmt(e)}`
})

const rootStyle = computed(() => {
  return {
    '--cell': `${Math.max(4, measuredCell.value)}px`,
    '--gap': `${Math.max(1, measuredGap.value)}px`,
  } as Record<string, string>
})

const colorPalette = computed(() => {
  // 从浅到深，5个级别
  // 0是"空"状态，仍由CSS变量控制主题；这里我们提供一个微妙的回退。
  switch (scheme.value) {
    case 'blue':
      return ['var(--heatmap-empty)', '#e6f0ff', '#c7dcff', '#8fb8ff', '#3b82f6']
    case 'rose':
      return ['var(--heatmap-empty)', '#ffe6ea', '#ffc7d1', '#ff8fa8', '#e11d48']
    case 'slate':
      return ['var(--heatmap-empty)', '#e9eef4', '#cfd8e3', '#97a6ba', '#475569']
    case 'emerald':
    default:
      return ['var(--heatmap-empty)', '#e6f4ea', '#c9ecd7', '#8fdbb7', '#10b981']
  }
})

function cellStyle(level: number) {
  const idx = Math.max(0, Math.min(4, Math.floor(level)))
  const bg = colorPalette.value[idx]
  return {
    width: 'var(--cell)',
    height: 'var(--cell)',
    borderRadius: '3px',
    backgroundColor: bg,

    transition: 'transform 120ms ease, background-color 120ms ease, box-shadow 120ms ease',
  } as Record<string, string>
}

function onDayClick(day: { date: Date; dateStr: string; count: number; level: 0 | 1 | 2 | 3 | 4 }) {
  emit('day-click', { date: day.date, dateStr: day.dateStr, count: day.count, level: day.level })
}
function tooltipText(day: { dateStr: string; count: number }) {
  return `${day.count} on ${day.dateStr}`
}
</script>

<template>
  <div class="inline-block w-full" :style="rootStyle" ref="containerEl">
    <div class="flex items-center justify-between mb-2">
      <div v-if="label" class="text-sm font-medium text-slate-700 dark:text-slate-200">
        {{ label }}
      </div>
      <div class="text-[10px] text-slate-400 dark:text-slate-500">
        {{ rangeText }}
      </div>
    </div>

    <!-- Month labels -->
    <div
      class="grid mb-1 select-none"
      :style="{
        gridTemplateColumns: `repeat(${weeks.length}, var(--cell))`,
        columnGap: 'var(--gap)',
      }"
    >
      <div
        v-for="(week, wi) in weeks"
        :key="`m-${wi}`"
        class="text-[10px] text-slate-400 dark:text-slate-500"
        :style="{ width: 'var(--cell)' }"
      >
        <span v-if="week.monthLabel">{{ week.monthLabel }}</span>
      </div>
    </div>

    <!-- Heatmap -->
    <div
      class="flex"
      :style="{ columnGap: 'var(--gap)' }"
      role="grid"
      aria-label="contribution heatmap"
    >
      <div
        v-for="(week, wi) in weeks"
        :key="`w-${wi}`"
        class="flex flex-col"
        :style="{ rowGap: 'var(--gap)' }"
        role="rowgroup"
      >
        <button
          v-for="(day, di) in week.days"
          :key="`d-${wi}-${di}`"
          class="cell focus:outline-none focus:ring-2 focus:ring-emerald-500/60"
          role="gridcell"
          :aria-label="`${day.dateStr}: ${day.count} count`"
          :title="tooltipText(day)"
          :style="cellStyle(day.level)"
          @click="onDayClick(day)"
        />
      </div>
    </div>

    <!-- Legend -->
    <div
      class="flex items-center gap-2 mt-3 text-[10px] text-slate-400 dark:text-slate-500 select-none"
    >
      <span>Less</span>
      <div class="flex items-center gap-1">
        <div
          v-for="l in [0, 1, 2, 3, 4]"
          :key="`legend-${l}`"
          class="cell"
          :style="cellStyle(l)"
        ></div>
      </div>
      <span>More</span>
    </div>
  </div>
</template>

<style scoped>
.cell:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 18px -10px rgba(2, 6, 23, 0.3);
}

/* Theming vars */
:host,
:root,
.inline-block {
  --heatmap-empty: rgba(15, 23, 42, 0.06);
  --heatmap-cell-border: rgba(15, 23, 42, 0.08);
}
@media (prefers-color-scheme: dark) {
  :host,
  :root,
  .inline-block {
    --heatmap-empty: rgba(148, 163, 184, 0.12);
    --heatmap-cell-border: rgba(148, 163, 184, 0.18);
  }
}
</style>
