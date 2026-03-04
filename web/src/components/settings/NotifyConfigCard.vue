<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import Button from 'primevue/button'
import Card from 'primevue/card'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import SelectButton from 'primevue/selectbutton'
import Tag from 'primevue/tag'
import Textarea from 'primevue/textarea'
import type { NotifyConfig } from '@/__generated/model/static'
import { api } from '@/ApiInstance'
import {
  buildPresetBodyTemplate,
  buildServerChan3Url,
  buildServerChanTurboUrl,
  createHeaderRow,
  detectPresetFromUrl,
  headerMapToRows,
  rowsToHeaderMap,
  toNotifyDraft,
  type HeaderRow,
  type NotifyPresetMode,
} from '@/utils/notifyConfig'
import { useToast } from 'primevue/usetoast'

const toast = useToast()

const loading = ref(false)
const saving = ref(false)
const editing = ref(false)

const config = ref<NotifyConfig>({
  url: '',
  headers: {},
  body: '',
})

const selectedChannel = ref<NotifyPresetMode>('serverchanTurbo')
const turboSendKey = ref('')
const server3SendKey = ref('')
const customUrl = ref('')
const bodyTemplate = ref('')
const headerRows = ref<HeaderRow[]>([createHeaderRow()])
const presetHeaderKey = 'Content-Type'
const presetHeaderValue = 'application/json'
const presetHeaders: Readonly<Record<string, string>> = {
  [presetHeaderKey]: presetHeaderValue,
}

const channelOptions: Array<{ label: string; value: NotifyPresetMode }> = [
  { label: 'Server酱 Turbo', value: 'serverchanTurbo' },
  { label: 'Server酱 ³', value: 'serverchan3' },
  { label: '自定义 WebHook', value: 'custom' },
]

const interpolationItems = [
  { token: '${crontab.name}', description: '计划任务名称' },
  { token: '${crontab.id}', description: '计划任务 ID' },
  { token: '${success}', description: '本次同步成功数量' },
  { token: '${total}', description: '本次同步总数量' },
]

const configured = computed(() => (config.value.url ?? '').trim() !== '')
const currentPreset = computed(() => detectPresetFromUrl(config.value.url ?? ''))
const currentChannel = computed(() => {
  if (!configured.value) return '未配置'
  if (currentPreset.value.mode === 'serverchanTurbo') return 'Server酱 Turbo'
  if (currentPreset.value.mode === 'serverchan3') return 'Server酱 ³'
  return '自定义 WebHook'
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
const previewBody = computed(() => {
  if (bodyTemplate.value.trim() === '') return '(空)'
  const formatted = tryFormatJson(bodyTemplate.value)
  return formatted ?? bodyTemplate.value
})

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
      summary: '提示',
      detail: '当前请求体不是有效 JSON，无法格式化',
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
      return '存在请求头 value 已填写但 key 为空的行，请修正后保存'
    }
    if (keys.has(key)) {
      return `请求头 key "${key}" 重复，请合并后再保存`
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
    }
  } catch (error) {
    console.error('获取通知配置失败', error)
    toast.add({
      severity: 'error',
      summary: '获取失败',
      detail: '无法获取通知配置',
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
      summary: '提示',
      detail: '请填写 Server酱 Turbo SendKey',
      life: 2600,
    })
    return
  }

  if (selectedChannel.value === 'serverchan3' && server3SendKey.value.trim() === '') {
    toast.add({
      severity: 'warn',
      summary: '提示',
      detail: '请填写 Server酱 ³ SendKey',
      life: 2600,
    })
    return
  }

  if (!isValidHttpUrl(url)) {
    toast.add({
      severity: 'warn',
      summary: '提示',
      detail: '通知 URL 格式无效，请输入 http(s) 地址',
      life: 2600,
    })
    return
  }

  const headerError = isPresetChannel.value ? null : validateHeaders()
  if (headerError) {
    toast.add({ severity: 'warn', summary: '提示', detail: headerError, life: 2800 })
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
        },
      },
    })
    toast.add({ severity: 'success', summary: '成功', detail: '通知配置已保存', life: 2000 })
    editing.value = false
    await fetchNotifyConfig()
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error) || '保存失败'
    toast.add({ severity: 'error', summary: '错误', detail, life: 3200 })
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  fetchNotifyConfig()
})
</script>

<template>
  <Card class="overflow-hidden shadow-sm ring-1 ring-slate-200/60 dark:ring-slate-700/60 mb-6">
    <template #title>
      <div class="flex items-center justify-between gap-2">
        <span>通知配置</span>
        <div class="flex items-center gap-1">
          <Button icon="pi pi-pencil" severity="secondary" text rounded @click="openEditor" />
          <Button
            icon="pi pi-refresh"
            severity="secondary"
            text
            rounded
            :loading="loading"
            @click="fetchNotifyConfig"
          />
        </div>
      </div>
    </template>

    <template #content>
      <div class="space-y-4">
        <div class="flex items-center justify-between">
          <div class="text-sm text-slate-600 dark:text-slate-300">状态</div>
          <Tag
            :severity="configured ? 'success' : 'secondary'"
            :value="configured ? '已配置' : '未配置'"
          />
        </div>

        <div class="grid gap-3 text-sm sm:grid-cols-2">
          <div class="space-y-1">
            <div class="text-xs text-slate-400 dark:text-slate-500">通知渠道</div>
            <div class="font-medium text-slate-700 dark:text-slate-200">{{ currentChannel }}</div>
          </div>
          <div class="space-y-1">
            <div class="text-xs text-slate-400 dark:text-slate-500">请求头数量</div>
            <div class="font-medium text-slate-700 dark:text-slate-200">
              {{ currentHeaderCount }}
            </div>
          </div>
        </div>

        <div class="space-y-1">
          <div class="text-xs text-slate-400 dark:text-slate-500">请求 URL</div>
          <div
            class="truncate rounded border border-slate-200/70 px-2 py-1 font-mono text-xs dark:border-slate-700/70"
          >
            {{ configured ? config.url : '未配置（URL 为空）' }}
          </div>
        </div>

        <p class="text-xs text-slate-400 dark:text-slate-500">
          URL 留空表示关闭通知发送；支持 Server酱 Turbo、Server酱 ³ 和自定义 WebHook。
        </p>
      </div>
    </template>
  </Card>

  <Dialog
    v-model:visible="editing"
    modal
    header="编辑通知配置"
    class="w-full sm:w-[1160px]"
    :dismissableMask="true"
  >
    <div class="flex flex-col items-start gap-8 lg:flex-row">
      <div class="w-full min-w-0 flex-1 space-y-4">
        <div class="space-y-2">
          <label class="block text-xs font-medium text-slate-500 dark:text-slate-400"
            >通知渠道</label
          >
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
                  ? 'Server酱 Turbo SendKey'
                  : 'Server酱 ³ SendKey'
              }}
            </label>
            <InputText
              v-if="selectedChannel === 'serverchanTurbo'"
              v-model="turboSendKey"
              placeholder="输入 Turbo SendKey（通常以 sctp 开头）"
              class="w-full"
            />
            <InputText
              v-else
              v-model="server3SendKey"
              placeholder="输入 Server酱 ³ SendKey"
              class="w-full"
            />
          </div>

          <div class="space-y-2">
            <label class="block text-xs font-medium text-slate-500 dark:text-slate-400"
              >自动生成 URL</label
            >
            <InputText
              :modelValue="previewUrl"
              readonly
              disabled
              class="w-full font-mono text-xs"
              placeholder="根据 SendKey 自动生成"
            />
          </div>
        </div>

        <div v-else class="space-y-2">
          <label class="block text-xs font-medium text-slate-500 dark:text-slate-400"
            >Webhook URL</label
          >
          <InputText v-model="customUrl" placeholder="https://example.com/notify" class="w-full" />
        </div>

        <Message severity="info" icon="pi pi-info-circle" variant="simple">
          URL 为空则关闭通知，填写时请使用 http(s)。
        </Message>

        <div class="space-y-2">
          <label class="block text-xs font-medium text-slate-500 dark:text-slate-400">请求头</label>

          <div v-if="selectedChannel !== 'custom'" class="space-y-2">
            <div class="grid grid-cols-1 gap-2 p-2 sm:grid-cols-2">
              <InputText :modelValue="presetHeaderKey" readonly disabled class="text-xs" />
              <InputText :modelValue="presetHeaderValue" readonly disabled class="text-xs" />
            </div>
            <div class="text-[11px] text-slate-400 dark:text-slate-500">
              Server酱预设固定使用 Content-Type: application/json，且不可修改。
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
                label="添加请求头"
                size="small"
                severity="secondary"
                text
                @click="addHeader"
              />
            </div>
            <div class="text-[11px] text-slate-400 dark:text-slate-500">
              支持多个请求头；`value` 非空时 `key` 必填，且 key 不能重复。
            </div>
          </template>
        </div>

        <div class="space-y-2">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <label class="block text-xs font-medium text-slate-500 dark:text-slate-400"
              >请求体模板</label
            >
            <div class="flex flex-wrap items-center gap-1">
              <Button
                label="格式化 JSON"
                severity="secondary"
                text
                size="small"
                @click="formatBodyAsJson"
              />
              <Button
                v-if="selectedChannel !== 'custom'"
                label="恢复默认模板"
                severity="secondary"
                text
                size="small"
                @click="applyDefaultBodyTemplate"
              />
            </div>
          </div>
          <Textarea
            v-model="bodyTemplate"
            rows="8"
            autoResize
            class="w-full text-xs body-default-mono"
            placeholder="通知请求体模板"
          />
        </div>

        <div class="flex items-center justify-end gap-2 pt-2">
          <Button label="取消" severity="secondary" text @click="editing = false" />
          <Button label="保存" :loading="saving" @click="saveNotifyConfig" />
        </div>
      </div>

      <div
        class="w-full min-w-0 flex-1 self-stretch border-t border-slate-100 pt-6 dark:border-slate-800 lg:border-l lg:border-t-0 lg:pl-8 lg:pt-0"
      >
        <div class="space-y-5">
          <div class="space-y-2">
            <h3 class="text-sm font-semibold text-slate-700 dark:text-slate-200">插值项说明</h3>
            <div
              class="space-y-1 rounded border border-slate-200/70 p-3 text-xs dark:border-slate-700/70"
            >
              <div
                v-for="item in interpolationItems"
                :key="item.token"
                class="flex items-start justify-between gap-3"
              >
                <span class="font-mono text-slate-700 dark:text-slate-200">{{ item.token }}</span>
                <span class="text-slate-500 dark:text-slate-400">{{ item.description }}</span>
              </div>
            </div>
          </div>

          <div class="space-y-2">
            <h3 class="text-sm font-semibold text-slate-700 dark:text-slate-200">请求预览</h3>
            <div class="rounded border border-slate-200/70 p-3 text-xs dark:border-slate-700/70">
              <div class="space-y-1">
                <div class="text-slate-400 dark:text-slate-500">Method</div>
                <div class="font-mono text-slate-700 dark:text-slate-200">POST</div>
              </div>

              <div class="mt-3 space-y-1">
                <div class="text-slate-400 dark:text-slate-500">URL</div>
                <div class="break-all font-mono text-slate-700 dark:text-slate-200">
                  {{ previewUrl || '(空 URL，表示关闭通知)' }}
                </div>
              </div>

              <div class="mt-3 space-y-1">
                <div class="text-slate-400 dark:text-slate-500">Headers</div>
                <div
                  v-if="previewHeaders.length === 0"
                  class="font-mono text-slate-400 dark:text-slate-500"
                >
                  (无)
                </div>
                <div v-for="[key, value] in previewHeaders" :key="key" class="font-mono">
                  <span class="text-slate-500 dark:text-slate-400">{{ key }}</span
                  >:
                  <span class="text-slate-700 dark:text-slate-200">{{ value || '(空)' }}</span>
                </div>
              </div>

              <div class="mt-3 space-y-1">
                <div class="text-slate-400 dark:text-slate-500">Body</div>
                <pre
                  class="max-h-56 overflow-auto whitespace-pre-wrap rounded bg-slate-50 p-2 font-mono text-[11px] text-slate-700 dark:bg-slate-900/50 dark:text-slate-200"
                ><code>{{ previewBody }}</code></pre>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </Dialog>
</template>

<style scoped>
.body-default-mono {
  font-family: monospace;
}

:deep(.p-card) {
  transition:
    transform 180ms ease,
    box-shadow 180ms ease;
}
:deep(.p-card:hover) {
  transform: translateY(-1px);
  box-shadow: 0 8px 30px -12px rgba(2, 6, 23, 0.2);
}
</style>
