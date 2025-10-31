<script setup lang="ts">
import { onMounted, ref, computed, watch } from 'vue'
import Card from 'primevue/card'
import ContributionHeatmap from '@/components/ContributionHeatmap.vue'
import AlbumCard from '@/components/AlbumCard.vue'
import CrontabCard from '@/components/CrontabCard.vue'
import { api } from '@/ApiInstance'
import type { Dynamic_Album } from '@/__generated/model/dynamic'
import Panel from 'primevue/panel'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import InputSwitch from 'primevue/inputswitch'
import Dropdown from 'primevue/dropdown'
import MultiSelect from 'primevue/multiselect'
import Message from 'primevue/message'
import { useToast } from 'primevue/usetoast'
import type { CrontabDto } from '@/__generated/model/dto'
import SplitButton from 'primevue/splitbutton';
import type { CrontabCreateInput } from '@/__generated/model/static'

type DataPoint = { timeStamp: number; count: number }

const labelText = ref('一年活跃度')
const weekStartNum = ref(1)
const rangeDaysNum = ref(365)
const endDateStr = ref(formatDateInput(new Date()))
const dataPoints = ref<DataPoint[]>([])
const rawTimelineMap = ref<Record<string, number>>({})
const optimizeHeatmap = ref(false)
const albums = ref<ReadonlyArray<Dynamic_Album>>([])
const tip = ref('')
let tipHideTimer: number | undefined

// Toast
const toast = useToast()

// ==== 计划任务：类型与状态 ====
type Crontab = CrontabDto['CrontabController/DEFAULT_CRONTAB']

const crontabs = ref<ReadonlyArray<Crontab>>([])
const loadingCrons = ref(false)
const showCronDialog = ref(false)
const isEditing = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const updatingRow = ref<number | null>(null)
const showDeleteId = ref<number | null>(null)
const showDeleteVisible = ref(false)
const deleting = ref(false)
const showExecuteId = ref<number | null>(null)
const showExecuteVisible = ref(false)
const executing = ref(false)

const defaultTz = (() => {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'
  } catch {
    return 'UTC'
  }
})()

const cronForm = ref<CrontabCreateInput>({
  name: '',
  description: '',
  enabled: true,
  config: {
    expression: '',
    timeZone: defaultTz,
    targetPath: '',
    downloadImages: true,
    downloadVideos: false,
    diffByTimeline: false,
    rewriteExifTime: false,
    rewriteExifTimeZone: defaultTz,
    skipExistingFile: true,
  },
  albumIds: [],
})

const formErrors = ref<Record<string, string>>({})
const timeZones = ref<string[]>([])
const albumOptions = computed(() =>
  (albums.value || [])
    .filter((a) => a.id !== undefined)
    .map((a) => ({ label: a.name ?? `ID ${a.id}`, value: a.id as string })), // 这个 as 从逻辑上是多余的，但是不加编译器会报错
)
function onDayClick(payload: {
  date: Date
  dateStr: string
  count: number
  level: 0 | 1 | 2 | 3 | 4
}) {
  tip.value = `${payload.dateStr} · ${payload.count}`
  if (tipHideTimer) window.clearTimeout(tipHideTimer)
  tipHideTimer = window.setTimeout(() => {
    tip.value = ''
  }, 2500)
}

function formatDateInput(d: Date) {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function parseDateToLocalTimestamp(dateStr: string) {
  const [y, m, d] = dateStr.split('-').map((n) => Number(n))
  const dt = new Date(y, (m || 1) - 1, d || 1)
  dt.setHours(0, 0, 0, 0)
  return dt.getTime()
}

async function fetchTimeline() {
  try {
    const end = new Date(endDateStr.value)
    end.setHours(0, 0, 0, 0)
    const start = new Date(
      end.getTime() - (Math.max(1, rangeDaysNum.value) - 1) * 24 * 3600 * 1000,
    )
    const startStr = formatDateInput(start)

    const albumIds = (albums.value || [])
      .map((a) => Number(a.id))
      .filter((id) => Number.isFinite(id)) as number[]

    const resp = await api.albumsController.fetchDateMap({
      albumIds: albumIds.length > 0 ? albumIds : undefined,
      start: startStr,
      end: endDateStr.value,
    })

    rawTimelineMap.value = resp as Record<string, number>
    rebuildHeatmapData()
  } catch (err) {
    console.error('获取时间线数据失败', err)
  }
}

function quantile(values: number[], q: number): number {
  if (!values || values.length === 0) return 0
  const arr = [...values].sort((a, b) => a - b)
  const pos = (arr.length - 1) * q
  const base = Math.floor(pos)
  const rest = pos - base
  if (arr[base + 1] !== undefined) {
    return arr[base] + rest * (arr[base + 1] - arr[base])
  }
  return arr[base]
}

function rebuildHeatmapData() {
  const entries = Object.entries(rawTimelineMap.value || {})
  if (entries.length === 0) {
    dataPoints.value = []
    return
  }

  // 仅考虑正数值用于分位数估计，避免零值影响上界判断
  const positiveValues = entries
    .map(([, v]) => (Number.isFinite(v) ? Number(v) : 0))
    .filter((v) => v > 0)

  let upper = 0
  if (optimizeHeatmap.value && positiveValues.length >= 5) {
    // 95分位作为上界，削弱极端大值影响
    upper = quantile(positiveValues, 0.95)
  }

  const points: DataPoint[] = entries
    .map(([dateStr, countRaw]) => {
      const c = typeof countRaw === 'number' ? countRaw : 0
      const count = upper > 0 ? Math.min(c, upper) : c
      return {
        timeStamp: parseDateToLocalTimestamp(dateStr),
        count,
      }
    })
    .sort((a, b) => a.timeStamp - b.timeStamp)

  dataPoints.value = points
}

// function refresh() {
//   generateRandomData()
// }

async function fetchAlbums() {
  try {
    albums.value = await api.albumsController.listAlbums()
    await fetchTimeline()
  } catch (err) {
    console.error('获取相册列表失败', err)
  }
}

async function fetchLatestAlbums() {
  try {
    toast.add({ severity: 'info', summary: '正在从远程更新相册列表', detail: "请暂时不要离开此页面，同步正在进行", life: 5000 })
    albums.value = await api.albumsController.refreshAlbums()
    toast.add({ severity: 'success', summary: '已更新', life: 1600 })
    await fetchTimeline()
  } catch (err) {
    toast.add({ severity: 'error', summary: '更新失败', detail: "请确保您已配置有效的 passToken 与 UserId。\n并确保此已完成相册服务二次验证", life: 10000 })
    console.error('获取最新相册列表失败', err)
  }
}

// ==== 计划任务：逻辑 ====
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
  loadingCrons.value = true
  try {
    crontabs.value = await api.crontabController.listCrontabs()
  } catch (err) {
    console.error('获取计划任务失败', err)
    toast.add({
      severity: 'error',
      summary: '获取失败',
      detail: '无法获取计划任务列表',
      life: 2000,
    })
  } finally {
    loadingCrons.value = false
  }
}

function openCreateCron() {
  isEditing.value = false
  editingId.value = null
  cronForm.value = {
    name: '',
    description: '',
    enabled: true,
    config: {
      expression: '',
      timeZone: defaultTz,
      targetPath: './download',
      downloadImages: true,
      downloadVideos: false,
      diffByTimeline: false,
      rewriteExifTime: false,
      rewriteExifTimeZone: defaultTz,
      skipExistingFile: true,
    },
    albumIds: [],
  }
  formErrors.value = {}
  showCronDialog.value = true
}

function openEditCron(item: Crontab) {
  isEditing.value = true
  editingId.value = item.id
  cronForm.value = {
    name: item.name,
    description: item.description,
    enabled: item.enabled,
    config: {
      expression: item.config.expression,
      timeZone: item.config.timeZone,
      targetPath: item.config.targetPath,
      downloadImages: item.config.downloadImages,
      downloadVideos: item.config.downloadVideos,
      diffByTimeline: item.config.diffByTimeline,
      rewriteExifTime: item.config.rewriteExifTime,
      rewriteExifTimeZone: item.config.rewriteExifTimeZone ?? item.config.timeZone,
      skipExistingFile: item.config.skipExistingFile ?? true,
    },
    albumIds: [...item.albumIds],
  }
  formErrors.value = {}
  showCronDialog.value = true
}

function validateCronForm(): boolean {
  const errors: Record<string, string> = {}
  if (!cronForm.value.name || cronForm.value.name.trim() === '') errors.name = '必填'
  if (!cronForm.value.config.expression || cronForm.value.config.expression.trim() === '')
    errors.expression = '必填'
  if (!cronForm.value.config.timeZone || cronForm.value.config.timeZone.trim() === '') errors.timeZone = '必选'
  if (!cronForm.value.config.targetPath || cronForm.value.config.targetPath.trim() === '') errors.targetPath = '必填'
  formErrors.value = errors
  return Object.keys(errors).length === 0
}

async function submitCron() {
  if (!validateCronForm()) return
  saving.value = true
  try {
    if (isEditing.value && editingId.value !== null) {
      await api.crontabController.updateCrontab({
        crontabId: editingId.value,
        body: cronForm.value,
      })
      toast.add({ severity: 'success', summary: '已更新', life: 1600 })
    } else {
      await api.crontabController.createCrontab({ body: cronForm.value })
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
    await api.crontabController.updateCrontab({
      crontabId: row.id,
      body: {
        name: row.name,
        description: row.description,
        enabled: !row.enabled,
        config: row.config,
        albumIds: row.albumIds,
      },
    })
    crontabs.value = crontabs.value.map((c) =>
      c.id === row.id ? { ...c, enabled: !row.enabled } : c,
    )
    toast.add({ severity: 'success', summary: '已更新', life: 1600 })
  } catch (err) {
    console.error('更新启用状态失败', err)
    toast.add({ severity: 'error', summary: '更新失败', life: 1800 })
  } finally {
    updatingRow.value = null
  }
}

function requestDelete(row: Crontab) {
  showDeleteId.value = row.id
  showDeleteVisible.value = true
}

function requestExecute(row: Crontab) {
  showExecuteId.value = row.id
  showExecuteVisible.value = true
}

async function confirmExecute() {
  if (showExecuteId.value === null) return
  executing.value = true
  try {
    await api.crontabController.executeCrontab({ crontabId: showExecuteId.value })
    toast.add({ severity: 'success', summary: '已触发', life: 2000 })

    showExecuteId.value = null
    showExecuteVisible.value = false
    fetchCrontabs()
  } catch (err) {
    console.error('立即执行触发失败', err)
    toast.add({ severity: 'error', summary: '触发失败', detail: err instanceof Error ? err.message : String(err), life: 2200 })
  } finally {
    executing.value = false
  }
}

async function confirmDelete() {
  if (showDeleteId.value === null) return
  deleting.value = true
  try {
    await api.crontabController.deleteCrontab({ crontabId: showDeleteId.value })
    toast.add({ severity: 'success', summary: '已删除', life: 1500 })
    showDeleteId.value = null
    showDeleteVisible.value = false
    await fetchCrontabs()
  } catch (err) {
    console.error('删除计划任务失败', err)
    toast.add({ severity: 'error', summary: '删除失败', life: 1800 })
  } finally {
    deleting.value = false
  }
}

onMounted(() => {
  // 读取本地设置，默认开启
  try {
    const saved = localStorage.getItem('app:optimizeHeatmap')
    if (saved === null) {
      optimizeHeatmap.value = true
    } else {
      optimizeHeatmap.value = !(saved === '0' || saved === 'false')
    }
  } catch {
    optimizeHeatmap.value = true
  }
  fetchAlbums()
  fetchCrontabs()
  buildTimeZones()
})

watch(optimizeHeatmap, () => {
  rebuildHeatmapData()
})

// const weekOptions = [
//   { value: 1, label: '周一' },
//   { value: 0, label: '周日' },
//   { value: 2, label: '周二' },
//   { value: 3, label: '周三' },
//   { value: 4, label: '周四' },
//   { value: 5, label: '周五' },
//   { value: 6, label: '周六' },
// ]

const albumsRefreshModel = ref([
  {
    label: '从远程更新整个相册列表',
    icon: 'pi pi-cloud-download',
    command: fetchLatestAlbums,
  },
])

</script>

<template>
  <div class="max-w-5xl mx-auto px-4 py-8">
    <Card class="overflow-hidden shadow-sm ring-1 ring-slate-200/60 mb-6">
      <template #content>
        <div class="w-full overflow-x-hidden">
          <ContributionHeatmap :data="dataPoints" :label="labelText" :week-start="weekStartNum"
            :range-days="rangeDaysNum" :end="endDateStr" @day-click="onDayClick" />
          <div v-if="tip"
            class="mt-3 inline-flex items-center rounded-md bg-slate-900/80 text-white text-xs px-2 py-1 shadow-md dark:bg-slate-100/10 dark:text-slate-100">
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
            <div v-if="!crontabs || crontabs.length === 0" class="text-xs text-slate-500 py-6">暂无计划任务</div>
            <div v-else class="grid grid-cols-1 gap-3">
              <CrontabCard v-for="item in crontabs" :key="item.id" :crontab="item" :album-options="albumOptions"
                :busy="updatingRow === item.id" @edit="openEditCron(item)" @delete="requestDelete(item)"
                @toggle="toggleEnabled(item)" @execute="requestExecute(item)" />
            </div>
          </div>
        </div>
      </template>
      >
    </Card>

    <Panel header="相册" toggleable>
      <template #icons>
        <SplitButton icon="pi pi-refresh" severity="secondary" outlined rounded @click="fetchAlbums"
          :model="albumsRefreshModel" />
      </template>

      <div class="space-y-2">
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-3">
          <AlbumCard v-for="a in albums" :key="a.id" :name="a.name" :asset-count="a.assetCount"
            :last-update-time="a.lastUpdateTime" />
          <div v-if="!albums || albums.length === 0" class="text-xs text-slate-500">暂无相册</div>
        </div>
      </div>
    </Panel>

    <!-- 创建/编辑 计划任务 -->
    <Dialog v-model:visible="showCronDialog" modal :header="isEditing ? '编辑计划任务' : '创建计划任务'"
      class="w-full sm:w-[520px]">
      <div class="space-y-4">
        <div class="space-y-2">
          <label class="block text-xs font-medium text-slate-500">名称</label>
          <InputText v-model="cronForm.name" placeholder="例如：每日同步" class="w-full" />
          <div v-if="formErrors.name" class="text-xs text-red-500">{{ formErrors.name }}</div>
        </div>

        <div class="space-y-2">
          <label class="block text-xs font-medium text-slate-500">描述</label>
          <Textarea v-model="cronForm.description" rows="2" autoResize placeholder="可选" class="w-full" />
        </div>

        <div class="grid grid-cols-2 gap-4">
          <div class="space-y-2">
            <label class="block text-xs font-medium text-slate-500">Cron 表达式</label>
            <InputText v-model="cronForm.config.expression" placeholder="0 0 23 * * ?" class="w-full" />
            <div v-if="formErrors.expression" class="text-xs text-red-500">
              {{ formErrors.expression }}
            </div>
            <div class="text-[10px] text-slate-400">支持标准 6/7 字段 Cron 表达式<br />例：0 0 23 * * ? 表示每天 23 点执行</div>
          </div>
          <div class="space-y-2">
            <label class="block text-xs font-medium text-slate-500">时区</label>
            <Dropdown v-model="cronForm.config.timeZone" :options="timeZones" placeholder="Asia/Shanghai" filter
              class="w-full" />
            <div v-if="formErrors.timeZone" class="text-xs text-red-500">
              {{ formErrors.timeZone }}
            </div>
          </div>
        </div>

        <div class="space-y-2">
          <label class="block text-xs font-medium text-slate-500">保存路径</label>
          <InputText v-model="cronForm.config.targetPath" placeholder="./download" class="w-full" />
          <div v-if="formErrors.targetPath" class="text-xs text-red-500">{{ formErrors.targetPath }}</div>
          <div class="text-[10px] text-slate-400">如在容器环境下运行，请确保已将此路径映射到宿主机。将在此路径下创建相册各自的文件夹。</div>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-3 gap-4 pt-1">
          <div class="flex items-center gap-2 text-xs text-slate-600">
            <InputSwitch v-model="cronForm.config.downloadImages" />
            <span>下载照片</span>
          </div>
          <div class="flex items-center gap-2 text-xs text-slate-600">
            <InputSwitch v-model="cronForm.config.downloadVideos" />
            <span>下载视频</span>
          </div>
        </div>


        <div class="space-y-2">
          <label class="block text-xs font-medium text-slate-500">关联相册</label>
          <MultiSelect v-model="cronForm.albumIds" :options="albumOptions" display="chip" optionLabel="label"
            optionValue="value" placeholder="选择相册" class="w-full" filter />
        </div>

        <Message severity="info" variant="simple" icon="pi pi-info-circle">
          <div class="text-[12px]">
            不同计划任务的<span class="font-semibold">下载记录</span><span class="font-semibold">相互独立</span>，<span
              class="font-semibold">互不影响</span>。即使是<span class="font-semibold">同一相册中的同一资产</span>，在不同计划任务中，其<span
              class="font-semibold">已下载状态</span>也会<span class="font-semibold">分别判断</span>。
          </div>
        </Message>

        <Panel header="高级配置" toggleable>
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div class="space-y-1">
              <div class="flex items-center gap-2 text-xs text-slate-600">
                <InputSwitch v-model="cronForm.config.diffByTimeline" />
                <span>按时间线比对差异</span>
              </div>
              <div class="text-[10px] text-slate-400">通过对比上一次同步的相册时间线，将相册资产的获取范围限定为存在变动的日期。</div>
            </div>
            <div class="space-y-1">
              <div class="flex items-center gap-2 text-xs text-slate-600">
                <InputSwitch v-model="cronForm.config.rewriteExifTime" />
                <span>填充 EXIF 时间</span>
              </div>
              <div class="text-[10px] text-slate-400">将资产在小米云服务的时间写入 EXIF 时间，仅在资产不存在 EXIF 时间时生效。</div>
            </div>
            <div class="space-y-1">
              <div class="flex items-center gap-2 text-xs text-slate-600">
                <InputSwitch v-model="cronForm.config.skipExistingFile" />
                <span>跳过已存在文件</span>
              </div>
              <div class="text-[10px] text-slate-400">若资产的目标文件路径已存在，将跳过下载。仅适用于保存路径中已有存量数据。</div>
            </div>
          </div>

          <div v-if="cronForm.config.rewriteExifTime" class="space-y-2 mt-3">
            <label class="block text-xs font-medium text-slate-500">EXIF 时区</label>
            <Dropdown v-model="cronForm.config.rewriteExifTimeZone" :options="timeZones" placeholder="Asia/Shanghai"
              filter class="w-full" />
            <div class="text-[10px] text-slate-400">用于写入 EXIF 的时区；仅在开启“填充 EXIF 时间”后生效。</div>
          </div>
        </Panel>

        <div class="flex items-center justify-between pt-1">
          <div class="flex items-center gap-2 text-xs text-slate-600">
            <InputSwitch v-model="cronForm.enabled" />
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
    <Dialog v-model:visible="showDeleteVisible" modal header="删除计划任务" class="w-full sm:w-[420px]">
      <div class="text-sm text-slate-700">确定要删除该计划任务吗？该操作不可恢复。</div>
      <template #footer>
        <div class="flex items-center justify-end gap-2 w-full">
          <Button label="取消" severity="secondary" text @click="
            () => {
              showDeleteId = null
              showDeleteVisible = false
            }
          " />
          <Button label="删除" severity="danger" :loading="deleting" @click="confirmDelete" />
        </div>
      </template>
    </Dialog>

    <!-- 立即执行确认 -->
    <Dialog v-model:visible="showExecuteVisible" modal header="立即执行" class="w-full sm:w-[420px]">
      <div class="text-sm text-slate-700">确定要立即触发该计划任务的执行吗？该操作较为耗时，将在后台执行。</div>
      <template #footer>
        <div class="flex items-center justify-end gap-2 w-full">
          <Button label="取消" severity="secondary" text @click="
            () => {
              showExecuteId = null
              showExecuteVisible = false
            }
          " />
          <Button label="执行" severity="warning" :loading="executing" @click="confirmExecute" />
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
