export const CRON_FREQUENCIES = ['minutes', 'hours', 'daily', 'weekly', 'monthly'] as const
export type CronFrequency = (typeof CRON_FREQUENCIES)[number]

export const WEEKDAYS = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'] as const
export type Weekday = (typeof WEEKDAYS)[number]

export const MINUTE_STEPS = [5, 10, 15, 20, 30] as const
export const HOUR_STEPS = [1, 2, 3, 4, 6, 8, 12] as const

export interface CronBuilderState {
  frequency: CronFrequency
  minuteStep: number
  hourStep: number
  minute: number
  hour: number
  weekdays: Weekday[]
  dayOfMonth: number | 'L'
}

export function createDefaultCronBuilderState(): CronBuilderState {
  return {
    frequency: 'daily',
    minuteStep: 30,
    hourStep: 1,
    minute: 0,
    hour: 23,
    weekdays: ['MON'],
    dayOfMonth: 1,
  }
}

export function buildCron(state: CronBuilderState): string {
  switch (state.frequency) {
    case 'minutes':
      return `0 */${state.minuteStep} * * * ?`
    case 'hours':
      return state.hourStep === 1
        ? `0 ${state.minute} * * * ?`
        : `0 ${state.minute} */${state.hourStep} * * ?`
    case 'daily':
      return `0 ${state.minute} ${state.hour} * * ?`
    case 'weekly': {
      const days = WEEKDAYS.filter((day) => state.weekdays.includes(day))
      return `0 ${state.minute} ${state.hour} ? * ${days.length ? days.join(',') : 'MON'}`
    }
    case 'monthly':
      return `0 ${state.minute} ${state.hour} ${state.dayOfMonth} * ?`
  }
}

const NUMBER_FIELD = /^\d{1,2}$/
const STEP_FIELD = /^(?:\*|0)\/(\d{1,2})$/

function parseNumberField(field: string, min: number, max: number): number | null {
  if (!NUMBER_FIELD.test(field)) return null
  const value = Number(field)
  return value >= min && value <= max ? value : null
}

export function parseCron(expression: string): CronBuilderState | null {
  const fields = expression.trim().split(/\s+/)
  if (fields.length === 7) {
    if (fields[6] !== '*') return null
    fields.pop()
  }
  if (fields.length !== 6) return null

  const [second, minute, hour, dayOfMonth, month, dayOfWeek] = fields
  if (second !== '0' || month !== '*') return null

  const state = createDefaultCronBuilderState()

  if (dayOfMonth === '*' && dayOfWeek === '?') {
    const minuteStep = STEP_FIELD.exec(minute ?? '')
    if (minuteStep && hour === '*') {
      const step = Number(minuteStep[1])
      if (!(MINUTE_STEPS as readonly number[]).includes(step)) return null
      state.frequency = 'minutes'
      state.minuteStep = step
      return state
    }
  }

  const minuteValue = parseNumberField(minute ?? '', 0, 59)
  if (minuteValue === null) return null
  state.minute = minuteValue

  if (dayOfMonth === '*' && dayOfWeek === '?') {
    if (hour === '*') {
      state.frequency = 'hours'
      state.hourStep = 1
      return state
    }
    const hourStep = STEP_FIELD.exec(hour ?? '')
    if (hourStep) {
      const step = Number(hourStep[1])
      if (!(HOUR_STEPS as readonly number[]).includes(step)) return null
      state.frequency = 'hours'
      state.hourStep = step
      return state
    }
    const hourValue = parseNumberField(hour ?? '', 0, 23)
    if (hourValue === null) return null
    state.frequency = 'daily'
    state.hour = hourValue
    return state
  }

  if (dayOfMonth === '?' && dayOfWeek === '*') {
    const hourValue = parseNumberField(hour ?? '', 0, 23)
    if (hourValue === null) return null
    state.frequency = 'daily'
    state.hour = hourValue
    return state
  }

  if (dayOfMonth === '?' && dayOfWeek !== '?' && dayOfWeek !== '*') {
    const hourValue = parseNumberField(hour ?? '', 0, 23)
    if (hourValue === null) return null
    const days = (dayOfWeek ?? '').split(',').map((day) => day.toUpperCase())
    if (!days.length || days.some((day) => !(WEEKDAYS as readonly string[]).includes(day))) {
      return null
    }
    state.frequency = 'weekly'
    state.hour = hourValue
    state.weekdays = WEEKDAYS.filter((day) => days.includes(day))
    return state
  }

  if (dayOfMonth !== '*' && dayOfMonth !== '?' && dayOfWeek === '?') {
    const hourValue = parseNumberField(hour ?? '', 0, 23)
    if (hourValue === null) return null
    state.frequency = 'monthly'
    state.hour = hourValue
    if (dayOfMonth === 'L') {
      state.dayOfMonth = 'L'
      return state
    }
    const dayValue = parseNumberField(dayOfMonth, 1, 31)
    if (dayValue === null) return null
    state.dayOfMonth = dayValue
    return state
  }

  return null
}
