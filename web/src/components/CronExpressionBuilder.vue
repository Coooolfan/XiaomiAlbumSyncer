<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import SelectButton from 'primevue/selectbutton'
import Select from 'primevue/select'
import InputNumber from 'primevue/inputnumber'
import Message from 'primevue/message'
import {
  CRON_FREQUENCIES,
  HOUR_STEPS,
  MINUTE_STEPS,
  WEEKDAYS,
  buildCron,
  createDefaultCronBuilderState,
  parseCron,
  type Weekday,
} from '@/utils/cronBuilder'

const expression = defineModel<string>({ required: true })

const { t } = useI18n()

const parsed = parseCron(expression.value)
const state = reactive(parsed ?? createDefaultCronBuilderState())
const unsupported = ref(parsed === null)

watch(
  state,
  () => {
    expression.value = buildCron(state)
    unsupported.value = false
  },
  { deep: true },
)

const frequencyOptions = computed(() =>
  CRON_FREQUENCIES.map((value) => ({ label: t(`cronform.builder.freq.${value}`), value })),
)

const minuteStepOptions = MINUTE_STEPS.map((value) => ({ label: String(value), value }))
const hourStepOptions = HOUR_STEPS.map((value) => ({ label: String(value), value }))

const weekdayOptions = computed(() =>
  WEEKDAYS.map((value) => ({ label: t(`cronform.builder.weekday.${value}`), value })),
)

const dayOfMonthOptions = computed(() => [
  ...Array.from({ length: 31 }, (_, index) => ({
    label: String(index + 1),
    value: (index + 1) as number | 'L',
  })),
  { label: t('cronform.builder.lastDay'), value: 'L' as number | 'L' },
])

const showMonthDayHint = computed(
  () => typeof state.dayOfMonth === 'number' && state.dayOfMonth >= 29,
)

function setNumberField(field: 'hour' | 'minute', value: number | null | undefined) {
  if (typeof value === 'number' && Number.isFinite(value)) state[field] = value
}

function setWeekdays(value: Weekday[] | undefined) {
  if (!value || value.length === 0) return
  state.weekdays = WEEKDAYS.filter((day) => value.includes(day))
}

const timeText = computed(
  () => `${String(state.hour).padStart(2, '0')}:${String(state.minute).padStart(2, '0')}`,
)

const summary = computed(() => {
  switch (state.frequency) {
    case 'minutes':
      return t('cronform.builder.summary.minutes', { n: state.minuteStep })
    case 'hours':
      return state.hourStep === 1
        ? t('cronform.builder.summary.hourly', { minute: state.minute })
        : t('cronform.builder.summary.hours', { n: state.hourStep, minute: state.minute })
    case 'daily':
      return t('cronform.builder.summary.daily', { time: timeText.value })
    case 'weekly': {
      const days = WEEKDAYS.filter((day) => state.weekdays.includes(day))
        .map((day) => t(`cronform.builder.weekday.${day}`))
        .join(t('cronform.builder.weekdaySeparator'))
      return t('cronform.builder.summary.weekly', { days, time: timeText.value })
    }
    case 'monthly':
      return state.dayOfMonth === 'L'
        ? t('cronform.builder.summary.monthlyLast', { time: timeText.value })
        : t('cronform.builder.summary.monthly', { day: state.dayOfMonth, time: timeText.value })
    default:
      return ''
  }
})
</script>

<template>
  <div class="space-y-3">
    <Message v-if="unsupported" severity="warn" variant="simple" icon="pi pi-exclamation-triangle">
      <div class="text-[13px]">{{ t('cronform.builder.unsupported') }}</div>
    </Message>

    <SelectButton
      :model-value="state.frequency"
      :options="frequencyOptions"
      optionLabel="label"
      optionValue="value"
      :allowEmpty="false"
      size="small"
      class="w-full flex-wrap [&_.p-togglebutton]:flex-1"
      @update:model-value="(value) => value && (state.frequency = value)"
    />

    <template v-if="state.frequency === 'minutes'">
      <div class="flex items-center gap-2 text-[13px] text-slate-600 dark:text-slate-300">
        <span>{{ t('cronform.builder.every') }}</span>
        <Select
          v-model="state.minuteStep"
          :options="minuteStepOptions"
          optionLabel="label"
          optionValue="value"
          size="small"
          class="w-24"
        />
        <span>{{ t('cronform.builder.unitMinute') }}</span>
      </div>
      <Message
        v-if="state.minuteStep <= 10"
        severity="warn"
        variant="simple"
        icon="pi pi-exclamation-triangle"
      >
        <div class="text-[13px]">{{ t('cronform.builder.frequentHint') }}</div>
      </Message>
    </template>

    <div
      v-else-if="state.frequency === 'hours'"
      class="flex flex-wrap items-center gap-2 text-[13px] text-slate-600 dark:text-slate-300"
    >
      <span>{{ t('cronform.builder.every') }}</span>
      <Select
        v-model="state.hourStep"
        :options="hourStepOptions"
        optionLabel="label"
        optionValue="value"
        size="small"
        class="w-24"
      />
      <span>{{ t('cronform.builder.unitHour') }}</span>
      <span>{{ t('cronform.builder.atMinute') }}</span>
      <InputNumber
        :model-value="state.minute"
        :min="0"
        :max="59"
        :useGrouping="false"
        :allowEmpty="false"
        size="small"
        inputClass="w-14 text-center"
        @update:model-value="(value) => setNumberField('minute', value)"
      />
      <span v-if="t('cronform.builder.atMinuteSuffix')">{{
        t('cronform.builder.atMinuteSuffix')
      }}</span>
    </div>

    <template v-else>
      <div class="flex items-center gap-2 text-[13px] text-slate-600 dark:text-slate-300">
        <span>{{ t('cronform.builder.time') }}</span>
        <InputNumber
          :model-value="state.hour"
          :min="0"
          :max="23"
          :useGrouping="false"
          :allowEmpty="false"
          size="small"
          inputClass="w-14 text-center"
          @update:model-value="(value) => setNumberField('hour', value)"
        />
        <span>:</span>
        <InputNumber
          :model-value="state.minute"
          :min="0"
          :max="59"
          :useGrouping="false"
          :allowEmpty="false"
          size="small"
          inputClass="w-14 text-center"
          @update:model-value="(value) => setNumberField('minute', value)"
        />
      </div>

      <div v-if="state.frequency === 'weekly'" class="space-y-2">
        <label class="block text-[13px] font-medium text-slate-500 dark:text-slate-400">{{
          t('cronform.builder.weekdays')
        }}</label>
        <SelectButton
          :model-value="state.weekdays"
          :options="weekdayOptions"
          optionLabel="label"
          optionValue="value"
          multiple
          :allowEmpty="false"
          size="small"
          class="w-full flex-wrap [&_.p-togglebutton]:flex-1"
          @update:model-value="setWeekdays"
        />
      </div>

      <div v-if="state.frequency === 'monthly'" class="space-y-2">
        <div class="flex items-center gap-2 text-[13px] text-slate-600 dark:text-slate-300">
          <span>{{ t('cronform.builder.dayOfMonth') }}</span>
          <Select
            v-model="state.dayOfMonth"
            :options="dayOfMonthOptions"
            optionLabel="label"
            optionValue="value"
            size="small"
            class="w-32"
          />
        </div>
        <div v-if="showMonthDayHint" class="text-[13px] text-slate-500 dark:text-slate-400">
          {{ t('cronform.builder.monthDayHint') }}
        </div>
      </div>
    </template>

    <div class="rounded-lg bg-slate-50 px-3 py-2 space-y-1 dark:bg-slate-800/60">
      <div v-if="!unsupported" class="text-[13px] text-slate-600 dark:text-slate-300">
        {{ summary }}
      </div>
      <div class="font-mono text-xs text-slate-500 dark:text-slate-400">{{ expression }}</div>
    </div>
  </div>
</template>
