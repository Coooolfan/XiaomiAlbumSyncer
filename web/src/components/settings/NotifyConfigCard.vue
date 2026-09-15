<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import Select from 'primevue/select'
import SelectButton from 'primevue/selectbutton'
import Tag from 'primevue/tag'
import type { NotifyConfig } from '@/__generated/model/static'
import { api } from '@/ApiInstance'
import {
  buildPresetBodyTemplate,
  buildPresetDailySummaryBodyTemplate,
  buildPresetPassTokenExpiredBodyTemplate,
  buildServerChan3Url,
  buildServerChanTurboUrl,
  createHeaderRow,
  detectPresetFromUrl,
  headerMapToRows,
  renderNotifyTemplate,
  rowsToHeaderMap,
  toNotifyDraft,
  type HeaderRow,
  type NotifyPresetMode,
} from '@/utils/notifyConfig'
import SettingSection from '@/components/settings/SettingSection.vue'
import CodeEditor from '@/components/CodeEditor.vue'
import { useToast } from 'primevue/usetoast'
import { useI18n } from 'vue-i18n'

const toast = useToast()
const { t } = useI18n()

const loading = ref(false)
const saving = ref(false)
const editing = ref(false)

const config = ref<NotifyConfig>({
  url: '',
  headers: {},
  body: '',
  dailySummaryBody: '',
  dailySummaryCron: '',
  dailySummaryTimeZone: '',
  passTokenExpiredBody: '',
})

const selectedChannel = ref<NotifyPresetMode>('serverchanTurbo')
const turboSendKey = ref('')
const server3SendKey = ref('')
const customUrl = ref('')
const bodyTemplate = ref('')
const dailySummaryBody = ref('')
const dailySummaryCron = ref('')
const dailySummaryTimeZone = ref('')
const passTokenExpiredBody = ref('')
const headerRows = ref<HeaderRow[]>([createHeaderRow()])
const timeZones = ref<string[]>([])
const presetHeaderKey = 'Content-Type'
const presetHeaderValue = 'application/json'
const presetHeaders: Readonly<Record<string, string>> = {
  [presetHeaderKey]: presetHeaderValue,
}

const channelOptions = computed<Array<{ label: string; value: NotifyPresetMode }>>(() => [
  { label: t('notify.channel.serverchanTurbo'), value: 'serverchanTurbo' },
  { label: t('notify.channel.serverchan3'), value: 'serverchan3' },
  { label: t('notify.channel.custom'), value: 'custom' },
])

const interpolationGroups = computed(() => [
  {
    key: 'task',
    title: t('notify.interpolation.taskTitle'),
    items: [
      { token: '${crontab.name}', description: t('notify.interpolation.crontabName') },
      { token: '${crontab.id}', description: t('notify.interpolation.crontabId') },
      { token: '${success}', description: t('notify.interpolation.success') },
      { token: '${total}', description: t('notify.interpolation.total') },
    ],
  },
  {
    key: 'daily',
    title: t('notify.interpolation.dailyTitle'),
    items: [
      { token: '${summary}', description: t('notify.interpolation.summary') },
      { token: '${date}', description: t('notify.interpolation.date') },
    ],
  },
  {
    key: 'passToken',
    title: t('notify.interpolation.passTokenTitle'),
    items: [
      { token: '${account.nickname}', description: t('notify.interpolation.accountNickname') },
      { token: '${account.userId}', description: t('notify.interpolation.accountUserId') },
      { token: '${account.id}', description: t('notify.interpolation.accountId') },
    ],
  },
])

const configured = computed(() => (config.value.url ?? '').trim() !== '')
const currentPreset = computed(() => detectPresetFromUrl(config.value.url ?? ''))
const currentChannel = computed(() => {
  if (!configured.value) return t('common.status.notConfigured')
  if (currentPreset.value.mode === 'serverchanTurbo') return t('notify.channel.serverchanTurbo')
  if (currentPreset.value.mode === 'serverchan3') return t('notify.channel.serverchan3')
  return t('notify.channel.custom')
})
const currentHeaderCount = computed(() => Object.keys(config.value.headers ?? {}).length)

const previewUrl = computed(() => {
  if (selectedChannel.value === 'custom') return customUrl.value.trim()
  if (selectedChannel.value === 'serverchanTurbo')
    return buildServerChanTurboUrl(turboSendKey.value)
  return buildServerChan3Url(server3SendKey.value)
})

const isPresetChannel = computed(() => selectedChannel.value !== 'custom')
const previewHeaders = computed(() =>
  Object.entries(isPresetChannel.value ? presetHeaders : rowsToHeaderMap(headerRows.value)),
)
const sampleDate = computed(() => {
  try {
    return new Intl.DateTimeFormat('en-CA', {
      timeZone: dailySummaryTimeZone.value || 'UTC',
    }).format(new Date())
  } catch {
    return new Date().toISOString().slice(0, 10)
  }
})
function renderPreview(template: string, values: Record<string, string>) {
  if (template.trim() === '') return t('common.status.empty')
  const rendered = renderNotifyTemplate(template, values)
  return tryFormatJson(rendered) ?? rendered
}

const dailySummaryComplete = computed(
  () =>
    dailySummaryBody.value.trim() !== '' &&
    dailySummaryCron.value.trim() !== '' &&
    dailySummaryTimeZone.value.trim() !== '',
)

const previewGroups = computed(() => [
  {
    key: 'task',
    title: t('notify.preview.taskTitle'),
    enabled: true,
    hint: '',
    body: renderPreview(bodyTemplate.value, {
      'crontab.name': t('notify.preview.sample.crontabName'),
      'crontab.id': '1',
      success: '128',
      total: '130',
    }),
  },
  {
    key: 'daily',
    title: t('notify.preview.dailyTitle'),
    enabled: dailySummaryComplete.value,
    hint: t('notify.preview.dailyIncomplete'),
    body: renderPreview(dailySummaryBody.value, {
      summary: t('notify.preview.sample.summary'),
      date: sampleDate.value,
    }),
  },
  {
    key: 'passToken',
    title: t('notify.preview.passTokenTitle'),
    enabled: passTokenExpiredBody.value.trim() !== '',
    hint: t('notify.preview.passTokenEmpty'),
    body: renderPreview(passTokenExpiredBody.value, {
      'account.nickname': t('notify.preview.sample.accountNickname'),
      'account.userId': '2266113523',
      'account.id': '1',
    }),
  },
])

watch(selectedChannel, (mode) => {
  if (mode !== 'custom' && bodyTemplate.value.trim() === '') {
    bodyTemplate.value = buildPresetBodyTemplate()
  }
})

function setEditorFromConfig(target?: NotifyConfig) {
  const draft = toNotifyDraft(target)
  const preset = detectPresetFromUrl(draft.url)

  selectedChannel.value = preset.mode

  if (preset.mode === 'serverchanTurbo') {
    turboSendKey.value = preset.sendKey
    server3SendKey.value = ''
    customUrl.value = ''
  } else if (preset.mode === 'serverchan3') {
    turboSendKey.value = ''
    server3SendKey.value = preset.sendKey
    customUrl.value = ''
  } else {
    turboSendKey.value = ''
    server3SendKey.value = ''
    customUrl.value = draft.url
  }

  if (selectedChannel.value !== 'custom' && draft.body.trim() === '') {
    draft.body = buildPresetBodyTemplate()
  }
  bodyTemplate.value = draft.body
  dailySummaryBody.value = target?.dailySummaryBody ?? ''
  passTokenExpiredBody.value = target?.passTokenExpiredBody ?? ''
  dailySummaryCron.value = target?.dailySummaryCron || '0 0 23 * * ?'
  dailySummaryTimeZone.value = target?.dailySummaryTimeZone || 'Asia/Shanghai'
  headerRows.value = headerMapToRows(draft.headers)
}

function openEditor() {
  setEditorFromConfig(config.value)
  editing.value = true
}

function addHeader() {
  headerRows.value.push(createHeaderRow(headerRows.value.length))
}

function removeHeader(id: string) {
  if (headerRows.value.length === 1) {
    headerRows.value = [createHeaderRow()]
    return
  }
  headerRows.value = headerRows.value.filter((item) => item.id !== id)
}

function applyDefaultBodyTemplate() {
  bodyTemplate.value = buildPresetBodyTemplate()
}

function formatDailySummaryBodyAsJson() {
  const formatted = tryFormatJson(dailySummaryBody.value)
  if (formatted === null) {
    toast.add({
      severity: 'warn',
      summary: t('common.toast.warn'),
      detail: t('notify.toast.invalidDailyJson'),
      life: 2600,
    })
    return
  }
  dailySummaryBody.value = formatted
}

function applyDefaultDailySummaryBodyTemplate() {
  dailySummaryBody.value = buildPresetDailySummaryBodyTemplate()
}

function formatPassTokenExpiredBodyAsJson() {
  const formatted = tryFormatJson(passTokenExpiredBody.value)
  if (formatted === null) {
    toast.add({
      severity: 'warn',
      summary: t('common.toast.warn'),
      detail: t('notify.toast.invalidPassTokenJson'),
      life: 2600,
    })
    return
  }
  passTokenExpiredBody.value = formatted
}

function applyDefaultPassTokenExpiredBodyTemplate() {
  passTokenExpiredBody.value = buildPresetPassTokenExpiredBodyTemplate()
}

function tryFormatJson(input: string): string | null {
  const trimmed = input.trim()
  if (trimmed === '') return ''
  try {
    const parsed = JSON.parse(input)
    return JSON.stringify(parsed, null, 2)
  } catch {
    return null
  }
}

function formatBodyAsJson() {
  const formatted = tryFormatJson(bodyTemplate.value)
  if (formatted === null) {
    toast.add({
      severity: 'warn',
      summary: t('common.toast.warn'),
      detail: t('notify.toast.invalidJson'),
      life: 2600,
    })
    return
  }
  bodyTemplate.value = formatted
}

function isValidHttpUrl(url: string): boolean {
  if (url.trim() === '') return true
  try {
    const parsed = new URL(url)
    return parsed.protocol === 'http:' || parsed.protocol === 'https:'
  } catch {
    return false
  }
}

function validateHeaders(): string | null {
  const keys = new Set<string>()
  for (const row of headerRows.value) {
    const key = row.key.trim()
    const hasValue = row.value.trim() !== ''
    if (key === '' && !hasValue) continue
    if (key === '' && hasValue) {
      return t('notify.errors.headerValueWithoutKey')
    }
    if (keys.has(key)) {
      return t('notify.errors.duplicateHeaderKey', { key })
    }
    keys.add(key)
  }
  return null
}

async function fetchNotifyConfig() {
  loading.value = true
  try {
    const result = await api.systemConfigController.getNotifyConfig()
    config.value = {
      url: result?.url ?? '',
      headers: { ...(result?.headers ?? {}) },
      body: result?.body ?? '',
      dailySummaryBody: result?.dailySummaryBody ?? '',
      dailySummaryCron: result?.dailySummaryCron ?? '',
      dailySummaryTimeZone: result?.dailySummaryTimeZone ?? '',
      passTokenExpiredBody: result?.passTokenExpiredBody ?? '',
    }
  } catch (error) {
    console.error('获取通知配置失败', error)
    toast.add({
      severity: 'error',
      summary: t('common.toast.fetchFailed'),
      detail: t('notify.toast.fetchFailedDetail'),
      life: 2600,
    })
  } finally {
    loading.value = false
  }
}

async function saveNotifyConfig() {
  const url = previewUrl.value
  const headers = isPresetChannel.value ? { ...presetHeaders } : rowsToHeaderMap(headerRows.value)

  if (selectedChannel.value === 'serverchanTurbo' && turboSendKey.value.trim() === '') {
    toast.add({
      severity: 'warn',
      summary: t('common.toast.warn'),
      detail: t('notify.toast.turboSendKeyRequired'),
      life: 2600,
    })
    return
  }

  if (selectedChannel.value === 'serverchan3' && server3SendKey.value.trim() === '') {
    toast.add({
      severity: 'warn',
      summary: t('common.toast.warn'),
      detail: t('notify.toast.server3SendKeyRequired'),
      life: 2600,
    })
    return
  }

  if (!isValidHttpUrl(url)) {
    toast.add({
      severity: 'warn',
      summary: t('common.toast.warn'),
      detail: t('notify.toast.invalidUrl'),
      life: 2600,
    })
    return
  }

  const headerError = isPresetChannel.value ? null : validateHeaders()
  if (headerError) {
    toast.add({
      severity: 'warn',
      summary: t('common.toast.warn'),
      detail: headerError,
      life: 2800,
    })
    return
  }

  try {
    saving.value = true
    await api.systemConfigController.updateNotifyConfig({
      body: {
        notifyConfig: {
          url,
          headers,
          body: bodyTemplate.value,
          dailySummaryBody: dailySummaryBody.value,
          dailySummaryCron: dailySummaryCron.value,
          dailySummaryTimeZone: dailySummaryTimeZone.value,
          passTokenExpiredBody: passTokenExpiredBody.value,
        },
      },
    })
    toast.add({
      severity: 'success',
      summary: t('common.toast.success'),
      detail: t('notify.toast.saved'),
      life: 2000,
    })
    editing.value = false
    await fetchNotifyConfig()
  } catch (error) {
    const detail =
      error instanceof Error ? error.message : String(error) || t('common.toast.saveFailed')
    toast.add({ severity: 'error', summary: t('common.toast.error'), detail, life: 3200 })
  } finally {
    saving.value = false
  }
}

function resolveDefaultTimeZone() {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'
  } catch {
    return 'UTC'
  }
}

function buildTimeZones() {
  const fallbackTimeZones = [
    'UTC',
    'Asia/Shanghai',
    'Asia/Tokyo',
    'Europe/Berlin',
    'America/New_York',
  ]
  try {
    const intl = Intl as unknown as { supportedValuesOf?: (key: 'timeZone') => string[] }
    const list = intl.supportedValuesOf?.('timeZone')
    timeZones.value = list && list.length > 0 ? list : fallbackTimeZones
  } catch {
    timeZones.value = fallbackTimeZones
  }
}

onMounted(() => {
  fetchNotifyConfig()
  buildTimeZones()
  if (!dailySummaryTimeZone.value) {
    dailySummaryTimeZone.value = resolveDefaultTimeZone()
  }
})
</script>

<template>
  <SettingSection :title="t('notify.section.title')" :description="t('notify.section.description')">
    <template #actions>
      <Button
        icon="pi pi-pencil"
        severity="secondary"
        text
        rounded
        size="small"
        v-tooltip.bottom="t('common.action.edit')"
        @click="openEditor"
      />
      <Button
        icon="pi pi-refresh"
        severity="secondary"
        text
        rounded
        size="small"
        :loading="loading"
        @click="fetchNotifyConfig"
      />
    </template>

    <div class="space-y-4">
      <div class="flex items-center justify-between">
        <div class="text-sm text-slate-600 dark:text-slate-300">{{ t('common.field.status') }}</div>
        <Tag
          :severity="configured ? 'success' : 'secondary'"
          :value="configured ? t('common.status.configured') : t('common.status.notConfigured')"
        />
      </div>

      <div class="grid gap-3 text-sm sm:grid-cols-2">
        <div class="space-y-1">
          <div class="text-xs text-slate-400 dark:text-slate-500">
            {{ t('notify.field.channel') }}
          </div>
          <div class="font-medium text-slate-700 dark:text-slate-200">{{ currentChannel }}</div>
        </div>
        <div class="space-y-1">
          <div class="text-xs text-slate-400 dark:text-slate-500">
            {{ t('notify.field.headerCount') }}
          </div>
          <div class="font-medium text-slate-700 dark:text-slate-200">
            {{ currentHeaderCount }}
          </div>
        </div>
      </div>

      <div class="space-y-1">
        <div class="text-xs text-slate-400 dark:text-slate-500">
          {{ t('notify.field.requestUrl') }}
        </div>
        <div
          class="truncate rounded border border-slate-200/70 px-2 py-1 font-mono text-xs dark:border-slate-700/70"
        >
          {{ configured ? config.url : t('notify.field.urlEmpty') }}
        </div>
      </div>

      <p class="text-xs text-slate-400 dark:text-slate-500">
        {{ t('notify.section.hint') }}
      </p>
    </div>
  </SettingSection>

  <Dialog
    v-model:visible="editing"
    modal
    :header="t('notify.dialog.title')"
    class="w-full sm:w-[1160px]"
    :dismissableMask="true"
  >
    <div class="flex flex-col items-start gap-8 lg:flex-row">
      <div class="w-full min-w-0 flex-1 space-y-4">
        <div class="space-y-2">
          <label class="block text-xs font-medium text-slate-500 dark:text-slate-400">{{
            t('notify.field.channel')
          }}</label>
          <SelectButton
            v-model="selectedChannel"
            :options="channelOptions"
            optionLabel="label"
            optionValue="value"
            :allowEmpty="false"
          />
        </div>

        <div v-if="selectedChannel !== 'custom'" class="space-y-3">
          <div class="space-y-2">
            <label class="block text-xs font-medium text-slate-500 dark:text-slate-400">
              {{
                selectedChannel === 'serverchanTurbo'
                  ? t('notify.sendKey.turboLabel')
                  : t('notify.sendKey.server3Label')
              }}
            </label>
            <InputText
              v-if="selectedChannel === 'serverchanTurbo'"
              v-model="turboSendKey"
              :placeholder="t('notify.sendKey.turboPlaceholder')"
              class="w-full"
            />
            <InputText
              v-else
              v-model="server3SendKey"
              :placeholder="t('notify.sendKey.server3Placeholder')"
              class="w-full"
            />
          </div>

          <div class="space-y-2">
            <label class="block text-xs font-medium text-slate-500 dark:text-slate-400">{{
              t('notify.dialog.autoGeneratedUrl')
            }}</label>
            <InputText
              :modelValue="previewUrl"
              readonly
              disabled
              class="w-full font-mono text-xs"
              :placeholder="t('notify.dialog.autoGeneratedUrlPlaceholder')"
            />
          </div>
        </div>

        <div v-else class="space-y-2">
          <label class="block text-xs font-medium text-slate-500 dark:text-slate-400"
            >Webhook URL</label
          >
          <InputText v-model="customUrl" placeholder="https://example.com/notify" class="w-full" />
        </div>

        <Message
          v-if="selectedChannel === 'custom'"
          severity="info"
          icon="pi pi-info-circle"
          variant="simple"
        >
          {{ t('notify.dialog.urlHint') }}
        </Message>

        <div class="space-y-2">
          <label class="block text-xs font-medium text-slate-500 dark:text-slate-400">{{
            t('notify.dialog.headers')
          }}</label>

          <div v-if="selectedChannel !== 'custom'" class="space-y-2">
            <div class="grid grid-cols-1 gap-2 p-2 sm:grid-cols-2">
              <InputText :modelValue="presetHeaderKey" readonly disabled class="text-xs" />
              <InputText :modelValue="presetHeaderValue" readonly disabled class="text-xs" />
            </div>
            <div class="text-[11px] text-slate-400 dark:text-slate-500">
              {{ t('notify.dialog.presetHeadersHint') }}
            </div>
          </div>

          <template v-else>
            <div class="space-y-2">
              <div
                v-for="item in headerRows"
                :key="item.id"
                class="grid grid-cols-1 gap-2 p-2 sm:grid-cols-[1fr_1fr_auto]"
              >
                <InputText v-model="item.key" placeholder="Header Key" class="text-xs" />
                <InputText v-model="item.value" placeholder="Header Value" class="text-xs" />
                <Button
                  icon="pi pi-trash"
                  text
                  rounded
                  severity="danger"
                  class="justify-self-end"
                  @click="removeHeader(item.id)"
                />
              </div>
            </div>
            <div class="flex justify-end pt-1">
              <Button
                icon="pi pi-plus"
                :label="t('notify.dialog.addHeader')"
                size="small"
                severity="secondary"
                text
                @click="addHeader"
              />
            </div>
            <div class="text-[11px] text-slate-400 dark:text-slate-500">
              {{ t('notify.dialog.headersHint') }}
            </div>
          </template>
        </div>

        <div class="space-y-2">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <label class="block text-xs font-medium text-slate-500 dark:text-slate-400">{{
              t('notify.dialog.bodyTemplate')
            }}</label>
            <div class="flex flex-wrap items-center gap-1">
              <Button
                :label="t('notify.dialog.formatJson')"
                severity="secondary"
                text
                size="small"
                @click="formatBodyAsJson"
              />
              <Button
                v-if="selectedChannel !== 'custom'"
                :label="t('notify.dialog.restoreDefault')"
                severity="secondary"
                text
                size="small"
                @click="applyDefaultBodyTemplate"
              />
            </div>
          </div>
          <CodeEditor
            v-model="bodyTemplate"
            min-height="11rem"
            :placeholder="t('notify.dialog.bodyTemplatePlaceholder')"
          />
        </div>

        <div class="space-y-3 border-t border-slate-100 pt-4 dark:border-slate-800">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <label class="block text-xs font-medium text-slate-500 dark:text-slate-400">{{
              t('notify.dialog.dailySummaryTemplate')
            }}</label>
            <div class="flex flex-wrap items-center gap-1">
              <Button
                :label="t('notify.dialog.formatJson')"
                severity="secondary"
                text
                size="small"
                @click="formatDailySummaryBodyAsJson"
              />
              <Button
                v-if="selectedChannel !== 'custom'"
                :label="t('notify.dialog.restoreDefault')"
                severity="secondary"
                text
                size="small"
                @click="applyDefaultDailySummaryBodyTemplate"
              />
            </div>
          </div>
          <CodeEditor
            v-model="dailySummaryBody"
            min-height="6rem"
            :placeholder="t('notify.dialog.dailySummaryPlaceholder')"
          />
          <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div class="space-y-1">
              <label class="block text-xs font-medium text-slate-500 dark:text-slate-400">{{
                t('notify.dialog.cronExpression')
              }}</label>
              <InputText
                v-model="dailySummaryCron"
                class="w-full font-mono text-xs"
                :placeholder="t('notify.dialog.cronPlaceholder')"
              />
            </div>
            <div class="space-y-1">
              <label class="block text-xs font-medium text-slate-500 dark:text-slate-400">{{
                t('notify.dialog.timeZone')
              }}</label>
              <Select
                v-model="dailySummaryTimeZone"
                :options="timeZones"
                placeholder="Asia/Shanghai"
                filter
                class="w-full"
              />
            </div>
          </div>
        </div>

        <div class="space-y-3 border-t border-slate-100 pt-4 dark:border-slate-800">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <label class="block text-xs font-medium text-slate-500 dark:text-slate-400">{{
              t('notify.dialog.passTokenExpiredTemplate')
            }}</label>
            <div class="flex flex-wrap items-center gap-1">
              <Button
                :label="t('notify.dialog.formatJson')"
                severity="secondary"
                text
                size="small"
                @click="formatPassTokenExpiredBodyAsJson"
              />
              <Button
                v-if="selectedChannel !== 'custom'"
                :label="t('notify.dialog.restoreDefault')"
                severity="secondary"
                text
                size="small"
                @click="applyDefaultPassTokenExpiredBodyTemplate"
              />
            </div>
          </div>
          <CodeEditor
            v-model="passTokenExpiredBody"
            min-height="6rem"
            :placeholder="t('notify.dialog.passTokenExpiredPlaceholder')"
          />
        </div>

        <div class="flex items-center justify-end gap-2 pt-2">
          <Button
            :label="t('common.action.cancel')"
            severity="secondary"
            text
            @click="editing = false"
          />
          <Button :label="t('common.action.save')" :loading="saving" @click="saveNotifyConfig" />
        </div>
      </div>

      <div
        class="w-full min-w-0 flex-1 self-stretch border-t border-slate-100 pt-6 dark:border-slate-800 lg:border-l lg:border-t-0 lg:pl-8 lg:pt-0"
      >
        <div class="space-y-5">
          <div v-for="group in interpolationGroups" :key="group.key" class="space-y-2">
            <h3 class="text-sm font-semibold text-slate-700 dark:text-slate-200">
              {{ group.title }}
            </h3>
            <div
              class="space-y-1 rounded border border-slate-200/70 p-3 text-xs dark:border-slate-700/70"
            >
              <div
                v-for="item in group.items"
                :key="item.token"
                class="flex items-start justify-between gap-3"
              >
                <span class="font-mono text-slate-700 dark:text-slate-200">{{ item.token }}</span>
                <span class="text-slate-500 dark:text-slate-400">{{ item.description }}</span>
              </div>
            </div>
          </div>

          <div
            v-for="preview in previewGroups"
            :key="preview.key"
            class="space-y-2 transition-opacity duration-200"
            :class="preview.enabled ? '' : 'pointer-events-none select-none opacity-40'"
          >
            <h3 class="text-sm font-semibold text-slate-700 dark:text-slate-200">
              {{ preview.title }}
              <span
                v-if="!preview.enabled"
                class="ml-1 text-xs font-normal text-slate-400 dark:text-slate-500"
                >{{ preview.hint }}</span
              >
            </h3>
            <div class="rounded border border-slate-200/70 p-3 text-xs dark:border-slate-700/70">
              <div class="space-y-1">
                <div class="text-slate-400 dark:text-slate-500">Method</div>
                <div class="font-mono text-slate-700 dark:text-slate-200">POST</div>
              </div>

              <div class="mt-3 space-y-1">
                <div class="text-slate-400 dark:text-slate-500">URL</div>
                <div class="break-all font-mono text-slate-700 dark:text-slate-200">
                  {{ previewUrl || t('notify.preview.emptyUrl') }}
                </div>
              </div>

              <div class="mt-3 space-y-1">
                <div class="text-slate-400 dark:text-slate-500">Headers</div>
                <div
                  v-if="previewHeaders.length === 0"
                  class="font-mono text-slate-400 dark:text-slate-500"
                >
                  {{ t('notify.preview.none') }}
                </div>
                <div v-for="[key, value] in previewHeaders" :key="key" class="font-mono">
                  <span class="text-slate-500 dark:text-slate-400">{{ key }}</span
                  >:
                  <span class="text-slate-700 dark:text-slate-200">{{
                    value || t('common.status.empty')
                  }}</span>
                </div>
              </div>

              <div class="mt-3 space-y-1">
                <div class="text-slate-400 dark:text-slate-500">Body</div>
                <pre
                  class="max-h-56 overflow-auto whitespace-pre-wrap rounded bg-slate-50 p-2 font-mono text-[11px] text-slate-700 dark:bg-slate-900/50 dark:text-slate-200"
                ><code>{{ preview.body }}</code></pre>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </Dialog>
</template>
