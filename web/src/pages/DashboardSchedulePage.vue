<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import Card from 'primevue/card'
import ContributionHeatmap from '@/components/ContributionHeatmap.vue'
import AlbumPanel from '@/components/AlbumPanel.vue'
import CrontabCard from '@/components/CrontabCard.vue'
import Panel from 'primevue/panel'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import ToggleSwitch from 'primevue/toggleswitch'
import Select from 'primevue/select'
import MultiSelect from 'primevue/multiselect'
import InputNumber from 'primevue/inputnumber'
import Message from 'primevue/message'
import { useToast } from 'primevue/usetoast'
import type { CrontabDto } from '@/__generated/model/dto'
import { storeToRefs } from 'pinia'
import { useAccountsStore } from '@/stores/accounts'
import { useAlbumsStore } from '@/stores/albums'
import { useCrontabsStore } from '@/stores/crontabs'
import { usePreferencesStore } from '@/stores/preferences'
import { useHeatmapTimeline } from '@/composables/useHeatmapTimeline'
import { useActionDialog } from '@/composables/useActionDialog'
import { createEmptyCronForm, mapCrontabToForm, type LocalCronForm } from '@/utils/crontabForm'

type Crontab = CrontabDto['CrontabController/DEFAULT_CRONTAB']

const accountsStore = useAccountsStore()
const albumsStore = useAlbumsStore()
const crontabsStore = useCrontabsStore()
const preferencesStore = usePreferencesStore()

const { accounts } = storeToRefs(accountsStore)
const { albums } = storeToRefs(albumsStore)
const { crontabs, loading: loadingCrons } = storeToRefs(crontabsStore)
const { optimizeHeatmap } = storeToRefs(preferencesStore)

const toast = useToast()

const albumIds = computed(() => albums.value.map((a) => a.id))
const {
  labelText,
  weekStartNum,
  rangeDaysNum,
  endDateStr,
  dataPoints,
  tip,
  onDayClick,
  fetchTimeline,
  scheduleFetch,
} = useHeatmapTimeline({
  albumIds: () => albumIds.value,
  optimizeHeatmap: () => optimizeHeatmap.value,
})

const showCronDialog = ref(false)
const isEditing = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const updatingRow = ref<number | null>(null)

const deleteDialog = useActionDialog()
const executeDialog = useActionDialog()
const executeExifDialog = useActionDialog()
const executeRewriteFsDialog = useActionDialog()

const { targetId: showDeleteId, visible: showDeleteVisible, loading: deleting } = deleteDialog
const { targetId: showExecuteId, visible: showExecuteVisible, loading: executing } = executeDialog
const { targetId: showExecuteExifId, visible: showExecuteExifVisible, loading: executingExif } =
  executeExifDialog
const {
  targetId: showExecuteRewriteFsId,
  visible: showExecuteRewriteFsVisible,
  loading: executingRewriteFs,
} = executeRewriteFsDialog

const defaultTz = (() => {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'
  } catch {
    return 'UTC'
  }
})()

const cronForm = ref<LocalCronForm>(createEmptyCronForm(defaultTz, 0))
const formErrors = ref<Record<string, string>>({})
const timeZones = ref<string[]>([])

const accountOptions = computed(() =>
  accounts.value.map((a) => ({ label: a.nickname || a.userId, value: a.id })),
)

const allAlbumOptions = computed(() =>
  (albums.value || []).map((a) => ({
    label: a.name ?? `ID ${a.id}`,
    value: String(a.id),
  })),
)

const formAlbumOptions = computed(() => {
  if (!cronForm.value.accountId) return []
  return (albums.value || [])
    .filter((a) => a.account.id === cronForm.value.accountId)
    .map((a) => ({ label: a.name ?? `ID ${a.id}`, value: a.id }))
})

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

async function fetchCrontabs() {
  try {
    await crontabsStore.fetchCrontabs({ force: true })
  } catch (err) {
    console.error('获取计划任务失败', err)
    toast.add({
      severity: 'error',
      summary: '获取失败',
      detail: '无法获取计划任务列表',
      life: 2000,
    })
  }
}

function openCreateCron() {
  isEditing.value = false
  editingId.value = null
  const defaultAccountId = accounts.value[0]?.id ?? 0
  cronForm.value = createEmptyCronForm(defaultTz, defaultAccountId)
  formErrors.value = {}
  showCronDialog.value = true
}

function openEditCron(item: Crontab) {
  isEditing.value = true
  editingId.value = item.id
  cronForm.value = mapCrontabToForm(item, defaultTz)
  formErrors.value = {}
  showCronDialog.value = true
}

function validateCronForm(): boolean {
  const errors: Record<string, string> = {}
  if (!cronForm.value.name || cronForm.value.name.trim() === '') errors.name = '必填'
  if (!cronForm.value.config.expression || cronForm.value.config.expression.trim() === '') {
    errors.expression = '必填'
  } else {
    const crontabExpression = cronForm.value.config.expression.split(' ')
    if (crontabExpression.length < 6) {
      errors.expression = '看起来这不是一个有效的表达式'
    } else {
      if (crontabExpression[0] === '*') {
        errors.expression = '每秒运行一次似乎有点太高频了'
      } else if (crontabExpression[1] === '*') {
        errors.expression = '每分钟运行一次似乎有点太高频了'
      }
    }
  }
  if (!cronForm.value.config.timeZone || cronForm.value.config.timeZone.trim() === '')
    errors.timeZone = '必选'
  if (!cronForm.value.config.targetPath || cronForm.value.config.targetPath.trim() === '')
    errors.targetPath = '必填'
  if (!cronForm.value.accountId) errors.accountId = '必选'

  formErrors.value = errors
  return Object.keys(errors).length === 0
}

async function submitCron() {
  if (!validateCronForm()) return
  saving.value = true
  try {
    if (isEditing.value && editingId.value !== null) {
      await crontabsStore.updateCrontab(editingId.value, {
        name: cronForm.value.name,
        description: cronForm.value.description,
        enabled: cronForm.value.enabled,
        config: cronForm.value.config,
        albumIds: cronForm.value.albumIds,
      })
      toast.add({ severity: 'success', summary: '已更新', life: 1600 })
    } else {
      await crontabsStore.createCrontab(cronForm.value)
      toast.add({ severity: 'success', summary: '已创建', life: 1600 })
    }
    showCronDialog.value = false
    await fetchCrontabs()
  } catch (err) {
    console.error('保存计划任务失败', err)
    toast.add({ severity: 'error', summary: '保存失败', detail: '请稍后重试', life: 2200 })
  } finally {
    saving.value = false
  }
}

async function toggleEnabled(row: Crontab) {
  updatingRow.value = row.id
  try {
    await crontabsStore.updateCrontab(row.id, {
      name: row.name,
      description: row.description,
      enabled: !row.enabled,
      config: row.config,
      albumIds: row.albumIds,
    })
    toast.add({ severity: 'success', summary: '已更新', life: 1600 })
  } catch (err) {
    console.error('更新启用状态失败', err)
    toast.add({ severity: 'error', summary: '更新失败', life: 1800 })
  } finally {
    updatingRow.value = null
  }
}

function requestDelete(row: Crontab) {
  deleteDialog.open(row.id)
}

function requestExecute(row: Crontab) {
  executeDialog.open(row.id)
}

function requestExecuteExif(row: Crontab) {
  executeExifDialog.open(row.id)
}

function requestExecuteRewriteFs(row: Crontab) {
  executeRewriteFsDialog.open(row.id)
}

async function confirmExecute() {
  if (showExecuteId.value === null) return
  executing.value = true
  try {
    await crontabsStore.executeCrontab(showExecuteId.value)
    toast.add({ severity: 'success', summary: '已触发', life: 2000 })
    executeDialog.close()
    fetchCrontabs()
  } catch (err) {
    console.error('立即执行触发失败', err)
    toast.add({
      severity: 'error',
      summary: '触发失败',
      detail: err instanceof Error ? err.message : String(err),
      life: 2200,
    })
  } finally {
    executing.value = false
  }
}

async function confirmExecuteExif() {
  if (showExecuteExifId.value === null) return
  executingExif.value = true
  try {
    await crontabsStore.executeCrontabExifTime(showExecuteExifId.value)
    toast.add({ severity: 'success', summary: '已触发 EXIF 填充', life: 2000 })
    executeExifDialog.close()
    fetchCrontabs()
  } catch (err) {
    console.error('立即执行 EXIF 填充失败', err)
    toast.add({
      severity: 'error',
      summary: '触发失败',
      detail: err instanceof Error ? err.message : String(err),
      life: 2200,
    })
  } finally {
    executingExif.value = false
  }
}

async function confirmExecuteRewriteFs() {
  if (showExecuteRewriteFsId.value === null) return
  executingRewriteFs.value = true
  try {
    await crontabsStore.executeCrontabRewriteFileSystemTime(showExecuteRewriteFsId.value)
    toast.add({ severity: 'success', summary: '已触发文件时间重写', life: 2000 })
    executeRewriteFsDialog.close()
    fetchCrontabs()
  } catch (err) {
    console.error('立即执行文件系统时间重写失败', err)
    toast.add({
      severity: 'error',
      summary: '触发失败',
      detail: err instanceof Error ? err.message : String(err),
      life: 2200,
    })
  } finally {
    executingRewriteFs.value = false
  }
}

async function confirmDelete() {
  if (showDeleteId.value === null) return
  deleting.value = true
  try {
    await crontabsStore.deleteCrontab(showDeleteId.value)
    toast.add({ severity: 'success', summary: '已删除', life: 1500 })
    deleteDialog.close()
    await fetchCrontabs()
  } catch (err) {
    console.error('删除计划任务失败', err)
    toast.add({ severity: 'error', summary: '删除失败', life: 1800 })
  } finally {
    deleting.value = false
  }
}

onMounted(async () => {
  await Promise.all([
    accountsStore.fetchAccounts(),
    albumsStore.fetchAlbums(),
    crontabsStore.fetchCrontabs(),
  ])
  await fetchTimeline().catch((err) => {
    console.error('获取时间线数据失败', err)
  })
  buildTimeZones()
})

watch(albums, () => {
  scheduleFetch()
})

watch(
  () => cronForm.value.accountId,
  () => {
    if (!isEditing.value) {
      cronForm.value.albumIds = []
    }
  },
)
</script>

<template>
  <div class="max-w-5xl mx-auto px-4 py-8">
    <Card class="overflow-hidden shadow-sm ring-1 ring-slate-200/60 mb-6">
      <template #content>
        <div class="w-full overflow-x-hidden">
          <ContributionHeatmap
            :data="dataPoints"
            :label="labelText"
            :week-start="weekStartNum"
            :range-days="rangeDaysNum"
            :end="endDateStr"
            @day-click="onDayClick"
          />
          <div
            v-if="tip"
            class="mt-3 inline-flex items-center rounded-md bg-slate-900/80 text-white text-xs px-2 py-1 shadow-md dark:bg-slate-100/10 dark:text-slate-100"
          >
            {{ tip }}
          </div>
        </div>
      </template>
    </Card>

    <Card header="计划任务" class="overflow-hidden shadow-sm ring-1 ring-slate-200/60 mb-6">
      <template #title>
        <div class="flex items-center justify-between">
          <div class="font-medium text-slate-600">计划任务</div>
          <div class="flex items-center gap-2">
            <Button icon="pi pi-refresh" severity="secondary" rounded text @click="fetchCrontabs" />
            <Button icon="pi pi-plus" severity="success" rounded text @click="openCreateCron" />
          </div>
        </div>
      </template>

      <template #content>
        <div class="space-y-3">
          <div v-if="loadingCrons" class="text-xs text-slate-500 py-6">加载中...</div>
          <div v-else>
            <div v-if="!crontabs || crontabs.length === 0" class="text-xs text-slate-500 py-6">
              暂无计划任务
            </div>
            <div v-else class="grid grid-cols-1 gap-3">
              <CrontabCard
                v-for="item in crontabs"
                :key="item.id"
                :crontab="item"
                :album-options="allAlbumOptions"
                :busy="updatingRow === item.id"
                @edit="openEditCron(item)"
                @delete="requestDelete(item)"
                @toggle="toggleEnabled(item)"
                @execute="requestExecute(item)"
                @execute-exif="requestExecuteExif(item)"
                @execute-rewrite-fs-time="requestExecuteRewriteFs(item)"
                @refresh="fetchCrontabs"
              />
            </div>
          </div>
        </div>
      </template>
    </Card>

    <AlbumPanel />

    <!-- 创建/编辑 计划任务 -->
    <Dialog
      v-model:visible="showCronDialog"
      modal
      :header="isEditing ? '编辑计划任务' : '创建计划任务'"
      class="w-full sm:w-130"
    >
      <div class="space-y-4">
        <div class="space-y-2">
          <label class="block text-xs font-medium text-slate-500">名称</label>
          <InputText v-model="cronForm.name" placeholder="例如：每日同步" class="w-full" />
          <div v-if="formErrors.name" class="text-xs text-red-500">{{ formErrors.name }}</div>
        </div>

        <div class="space-y-2">
          <label class="block text-xs font-medium text-slate-500">描述</label>
          <Textarea
            v-model="cronForm.description"
            rows="2"
            autoResize
            placeholder="可选"
            class="w-full"
          />
        </div>

        <div class="space-y-2">
          <label class="block text-xs font-medium text-slate-500">归属账号</label>
          <Select
            v-model="cronForm.accountId"
            :options="accountOptions"
            optionLabel="label"
            optionValue="value"
            placeholder="选择小米账号"
            class="w-full"
            :disabled="isEditing"
          />
          <div v-if="formErrors.accountId" class="text-xs text-red-500">
            {{ formErrors.accountId }}
          </div>
          <div v-if="isEditing" class="text-[10px] text-slate-400">
            计划任务创建后无法更改归属账号
          </div>
        </div>

        <div class="grid grid-cols-2 gap-4">
          <div class="space-y-2">
            <label class="block text-xs font-medium text-slate-500">Cron 表达式</label>
            <InputText
              v-model="cronForm.config.expression"
              placeholder="0 0 23 * * ?"
              class="w-full"
            />
            <div v-if="formErrors.expression" class="text-xs text-red-500">
              {{ formErrors.expression }}
            </div>
            <div class="text-[10px] text-slate-400">
              支持标准 6/7 字段 Cron 表达式<br />例：0 0 23 * * ? 表示每天 23 点执行
            </div>
          </div>
          <div class="space-y-2">
            <label class="block text-xs font-medium text-slate-500">时区</label>
            <Select
              v-model="cronForm.config.timeZone"
              :options="timeZones"
              placeholder="Asia/Shanghai"
              filter
              class="w-full"
            />
            <div v-if="formErrors.timeZone" class="text-xs text-red-500">
              {{ formErrors.timeZone }}
            </div>
          </div>
        </div>

        <div class="space-y-2">
          <label class="block text-xs font-medium text-slate-500">保存路径</label>
          <InputText v-model="cronForm.config.targetPath" placeholder="./download" class="w-full" />
          <div v-if="formErrors.targetPath" class="text-xs text-red-500">
            {{ formErrors.targetPath }}
          </div>
          <div class="text-[10px] text-slate-400">
            如在容器环境下运行，请确保已将此路径映射到宿主机。程序将在此路径下创建相册各自的文件夹。
          </div>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-3 gap-4 pt-1">
          <div class="flex items-center gap-2 text-xs text-slate-600">
            <ToggleSwitch v-model="cronForm.config.downloadImages" />
            <span>下载照片</span>
          </div>
          <div class="flex items-center gap-2 text-xs text-slate-600">
            <ToggleSwitch v-model="cronForm.config.downloadVideos" />
            <span>下载视频</span>
          </div>
          <div class="flex items-center gap-2 text-xs text-slate-600">
            <ToggleSwitch v-model="cronForm.config.downloadAudios" />
            <span>下载录音</span>
          </div>
        </div>

        <div class="space-y-2">
          <label class="block text-xs font-medium text-slate-500">关联相册</label>
          <MultiSelect
            v-model="cronForm.albumIds"
            :options="formAlbumOptions"
            display="chip"
            optionLabel="label"
            optionValue="value"
            placeholder="选择相册"
            class="w-full"
            filter
          />
        </div>

        <Message severity="info" variant="simple" icon="pi pi-info-circle">
          <div class="text-[12px]">
            不同计划任务的<span class="font-semibold">下载记录</span
            ><span class="font-semibold">相互独立</span>，<span class="font-semibold">互不影响</span
            >。即使是<span class="font-semibold">同一相册中的同一资产</span
            >，在不同计划任务中，其<span class="font-semibold">已下载状态</span>也会<span
              class="font-semibold"
              >分别判断</span
            >。
          </div>
        </Message>

        <Panel header="高级配置" toggleable>
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div class="space-y-1">
              <div class="flex items-center gap-2 text-xs text-slate-600">
                <ToggleSwitch v-model="cronForm.config.diffByTimeline" />
                <span>按时间线比对差异</span>
              </div>
              <div class="text-[10px] text-slate-400">
                通过对比上一次同步的相册时间线，将相册资产的获取范围限定为存在变动的日期。
              </div>
            </div>
            <div class="space-y-1">
              <div class="flex items-center gap-2 text-xs text-slate-600">
                <ToggleSwitch v-model="cronForm.config.rewriteExifTime" />
                <span>填充 EXIF 时间</span>
              </div>
              <div class="text-[10px] text-slate-400">
                将资产在小米云服务的时间写入 EXIF 时间，仅在资产不存在 EXIF 时间时生效。
              </div>
            </div>
            <div class="space-y-1">
              <div class="flex items-center gap-2 text-xs text-slate-600">
                <ToggleSwitch v-model="cronForm.config.skipExistingFile" />
                <span>跳过已存在文件</span>
              </div>
              <div class="text-[10px] text-slate-400">
                若资产的目标文件路径已存在，将跳过下载。仅适用于保存路径中已有存量数据。
              </div>
            </div>
            <div class="space-y-1">
              <div class="flex items-center gap-2 text-xs text-slate-600">
                <ToggleSwitch v-model="cronForm.config.rewriteFileSystemTime" />
                <span>重写文件时间</span>
              </div>
              <div class="text-[10px] text-slate-400">
                同步完成后，将资产的文件系统时间修改为对应的小米云服务上的时间。
              </div>
            </div>
            <div class="space-y-1">
              <div class="flex items-center gap-2 text-xs text-slate-600">
                <ToggleSwitch v-model="cronForm.config.checkSha1" />
                <span>校验 SHA1</span>
              </div>
              <div class="text-[10px] text-slate-400">
                对下载的文件进行 SHA1 校验，失败则重新下载。<span class="font-bold"
                  >没必要开。</span
                >
              </div>
            </div>
          </div>

          <div v-if="cronForm.config.rewriteExifTime" class="space-y-2 mt-3">
            <label class="block text-xs font-medium text-slate-500">EXIF 时区</label>
            <Select
              v-model="cronForm.config.rewriteExifTimeZone"
              :options="timeZones"
              placeholder="Asia/Shanghai"
              filter
              class="w-full"
            />
            <div class="text-[10px] text-slate-400">
              用于写入 EXIF 的时区；仅在开启“填充 EXIF 时间”后生效。
            </div>
          </div>

          <Panel header="并发与性能" toggleable collapsed class="mt-4">
            <div class="text-[10px] text-slate-400 mb-4">
              除非你明确知道改动这些值的后果，否则不要改动
            </div>

            <div class="grid grid-cols-2 sm:grid-cols-3 gap-4">
              <div class="space-y-2">
                <label class="block text-xs font-medium text-slate-500">数据库读批大小</label>
                <InputNumber
                  v-model="cronForm.config.fetchFromDbSize"
                  :min="1"
                  :max="20"
                  showButtons
                  buttonLayout="horizontal"
                  inputClass="w-12 text-center"
                  class="w-full"
                >
                  <template>
                    <span class="pi pi-plus" />
                  </template>
                  <template>
                    <span class="pi pi-minus" />
                  </template>
                </InputNumber>
                <div class="text-[10px] text-slate-400">每次从数据库拉取的资产数量</div>
                <div v-if="formErrors.concurrency" class="text-xs text-red-500">
                  {{ formErrors.concurrency }}
                </div>
              </div>

              <div class="space-y-2">
                <label class="block text-xs font-medium text-slate-500">资产下载</label>
                <InputNumber
                  v-model="cronForm.config.downloaders"
                  :min="1"
                  :max="50"
                  showButtons
                  buttonLayout="horizontal"
                  inputClass="w-12 text-center"
                  class="w-full"
                >
                  <template>
                    <span class="pi pi-plus" />
                  </template>
                  <template>
                    <span class="pi pi-minus" />
                  </template>
                </InputNumber>
                <div class="text-[10px] text-slate-400">同时下载的资产数</div>
              </div>

              <div class="space-y-2">
                <label class="block text-xs font-medium text-slate-500">文件系统时间重写</label>
                <InputNumber
                  v-model="cronForm.config.fileTimeWorkers"
                  :min="1"
                  :max="50"
                  showButtons
                  buttonLayout="horizontal"
                  inputClass="w-12 text-center"
                  class="w-full"
                >
                  <template>
                    <span class="pi pi-plus" />
                  </template>
                  <template>
                    <span class="pi pi-minus" />
                  </template>
                </InputNumber>
                <div class="text-[10px] text-slate-400">文件系统时间重写并发数</div>
              </div>

              <div class="space-y-2">
                <label class="block text-xs font-medium text-slate-500">文件校验</label>
                <InputNumber
                  v-model="cronForm.config.verifiers"
                  :min="1"
                  :max="50"
                  showButtons
                  buttonLayout="horizontal"
                  inputClass="w-12 text-center"
                  class="w-full"
                >
                  <template>
                    <span class="pi pi-plus" />
                  </template>
                  <template>
                    <span class="pi pi-minus" />
                  </template>
                </InputNumber>
                <div class="text-[10px] text-slate-400">SHA1 校验检查并发数</div>
              </div>

              <div class="space-y-2">
                <label class="block text-xs font-medium text-slate-500">EXIF 填充</label>
                <InputNumber
                  v-model="cronForm.config.exifProcessors"
                  :min="1"
                  :max="50"
                  showButtons
                  buttonLayout="horizontal"
                  inputClass="w-12 text-center"
                  class="w-full"
                >
                  <template>
                    <span class="pi pi-plus" />
                  </template>
                  <template>
                    <span class="pi pi-minus" />
                  </template>
                </InputNumber>
                <div class="text-[10px] text-slate-400">EXIF 信息填充并发数</div>
              </div>
            </div>
          </Panel>
        </Panel>

        <div class="flex items-center justify-between pt-1">
          <div class="flex items-center gap-2 text-xs text-slate-600">
            <ToggleSwitch v-model="cronForm.enabled" />
            <span>启用</span>
          </div>
          <div class="flex items-center gap-2">
            <Button label="取消" severity="secondary" text @click="showCronDialog = false" />
            <Button :label="isEditing ? '保存' : '创建'" :loading="saving" @click="submitCron" />
          </div>
        </div>
      </div>
    </Dialog>

    <!-- 删除确认 -->
    <Dialog v-model:visible="showDeleteVisible" modal header="删除计划任务" class="w-full sm:w-105">
      <div class="text-sm text-slate-700">确定要删除该计划任务吗？该操作不可恢复。</div>
      <template #footer>
        <div class="flex items-center justify-end gap-2 w-full">
          <Button label="取消" severity="secondary" text @click="deleteDialog.close()" />
          <Button label="删除" severity="danger" :loading="deleting" @click="confirmDelete" />
        </div>
      </template>
    </Dialog>

    <!-- 立即执行确认 -->
    <Dialog v-model:visible="showExecuteVisible" modal header="立即执行" class="w-full sm:w-105">
      <div class="text-sm text-slate-700">
        确定要立即触发该计划任务的执行吗？该操作较为耗时，将在后台执行。
      </div>
      <template #footer>
        <div class="flex items-center justify-end gap-2 w-full">
          <Button label="取消" severity="secondary" text @click="executeDialog.close()" />
          <Button label="执行" severity="warning" :loading="executing" @click="confirmExecute" />
        </div>
      </template>
    </Dialog>

    <!-- 立即执行 EXIF 填充 -->
    <Dialog
      v-model:visible="showExecuteExifVisible"
      modal
      header="立即执行 EXIF 填充"
      class="w-full sm:w-105"
    >
      <div class="text-sm text-slate-700">
        确定要手动触发该计划任务的 EXIF
        填充操作吗？该操作较为耗时，将在后台执行。可观察程序日志查看进度。<br />会对此计划任务所有下载过的文件执行此操作。
      </div>
      <template #footer>
        <div class="flex items-center justify-end gap-2 w-full">
          <Button label="取消" severity="secondary" text @click="executeExifDialog.close()" />
          <Button
            label="执行"
            severity="info"
            :loading="executingExif"
            @click="confirmExecuteExif"
          />
        </div>
      </template>
    </Dialog>

    <!-- 立即执行文件系统时间重写 -->
    <Dialog
      v-model:visible="showExecuteRewriteFsVisible"
      modal
      header="立即重写文件系统时间"
      class="w-full sm:w-105"
    >
      <div class="text-sm text-slate-700">
        确定要对该计划任务已下载的文件执行文件系统时间重写吗？该操作较为耗时，将在后台执行。可观察程序日志查看进度。<br />会对此计划任务所有下载过的文件执行此操作。
      </div>
      <template #footer>
        <div class="flex items-center justify-end gap-2 w-full">
          <Button
            label="取消"
            severity="secondary"
            text
            @click="executeRewriteFsDialog.close()"
          />
          <Button
            label="执行"
            severity="info"
            :loading="executingRewriteFs"
            @click="confirmExecuteRewriteFs"
          />
        </div>
      </template>
    </Dialog>
  </div>
</template>

<style scoped>
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
