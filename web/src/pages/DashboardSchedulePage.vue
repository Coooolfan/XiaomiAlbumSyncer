<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
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
import { useToast } from 'primevue/usetoast'
import type { CrontabInput } from '@/__generated/model/static'
import type { CrontabDto } from '@/__generated/model/dto'
import SplitButton from 'primevue/splitbutton';
import type { Result } from '@/__generated/model/static'

type DataPoint = { timeStamp: number; count: number }

const labelText = ref('一年活跃度')
const weekStartNum = ref(1)
const rangeDaysNum = ref(365)
const endDateStr = ref(formatDateInput(new Date()))
const dataPoints = ref<DataPoint[]>([])
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

const cronForm = ref<CrontabInput>({
  name: '',
  description: '',
  enabled: true,
  config: {
    expression: '',
    timeZone: defaultTz,
    targetPath: '',
    downloadImages: true,
    downloadVideos: false,
    rewriteExifTime: false,
  },
  albumIds: [],
})

const formErrors = ref<Record<string, string>>({})
const timeZones = ref<string[]>([])
const albumOptions = computed(() =>
  (albums.value || [])
    .filter((a) => a.id !== undefined)
    .map((a) => ({ label: a.name ?? `ID ${a.id}`, value: a.id as number })),
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

function generateRandomData() {
  const end = new Date(endDateStr.value)
  end.setHours(0, 0, 0, 0)
  const start = new Date(end.getTime() - (Math.max(1, rangeDaysNum.value) - 1) * 24 * 3600 * 1000)
  const arr: DataPoint[] = []
  const cur = new Date(start)
  while (cur <= end) {
    if (Math.random() < 0.45) {
      arr.push({
        timeStamp: cur.getTime(),
        count: Math.floor(Math.random() * 10) + 1,
      })
    }
    cur.setDate(cur.getDate() + 1)
  }
  dataPoints.value = arr
}

function refresh() {
  generateRandomData()
}

async function fetchAlbums() {
  try {
    albums.value = await api.albumsController.listAlbums()
  } catch (err) {
    console.error('获取相册列表失败', err)
  }
}

async function fetchLatestAlbums() {
  try {
    toast.add({ severity: 'info', summary: '正在从远程更新相册列表',detail:"请暂时不要离开此页面，同步正在进行", life: 5000 })
    albums.value = await api.albumsController.refreshAlbumn()
    toast.add({ severity: 'success', summary: '已更新', life: 1600 })
  } catch (err) {
    toast.add({ severity: 'error', summary: '更新失败', detail: err instanceof Error ? err.message : String(err), life: 2200 })
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
      targetPath: '',
      downloadImages: true,
      downloadVideos: false,
      rewriteExifTime: false,
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
      rewriteExifTime: item.config.rewriteExifTime,
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
    const res: Result<number> = await api.crontabController.executeCrontab({ crontabId: showExecuteId.value })
    if (res && typeof res.code === 'number' && res.code === res.SUCCEED_CODE) {
      toast.add({ severity: 'success', summary: '已触发', detail: `执行ID: ${res.data}` , life: 1800 })
    } else {
      toast.add({ severity: 'warn', summary: '触发返回', detail: res?.description ?? '已发送请求', life: 2200 })
    }
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
  generateRandomData()
  fetchAlbums()
  fetchCrontabs()
  buildTimeZones()
})

const weekOptions = [
  { value: 1, label: '周一' },
  { value: 0, label: '周日' },
  { value: 2, label: '周二' },
  { value: 3, label: '周三' },
  { value: 4, label: '周四' },
  { value: 5, label: '周五' },
  { value: 6, label: '周六' },
]

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
            <div v-if="!crontabs || crontabs.length === 0" class="text-xs text-slate-500 py-6">暂无计划任务</div>
            <div v-else class="grid grid-cols-1 gap-3">
              <CrontabCard
                v-for="item in crontabs"
                :key="item.id"
                :crontab="item"
                :album-options="albumOptions"
                :busy="updatingRow === item.id"
                @edit="openEditCron(item)"
                @delete="requestDelete(item)"
                @toggle="toggleEnabled(item)"
                @execute="requestExecute(item)"
              />
            </div>
          </div>
        </div>
      </template>
      >
    </Card>

    <!-- <Card class="overflow-hidden shadow-sm ring-1 ring-slate-200/60 mb-6">
      <template #title>热力图数据 Mock 演示</template>
      <template #content>
        <div
          class="space-y-4 p-4 rounded-lg bg-white/60 dark:bg-slate-900/30 ring-1 ring-slate-200/60"
        >
          <div class="space-y-2">
            <label class="block text-xs font-medium text-slate-500">小标题 label</label>
            <input
              v-model="labelText"
              type="text"
              class="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-emerald-500/50 dark:bg-slate-900 dark:border-slate-700"
              placeholder="例如：一年活跃度"
            />
          </div>

          <div class="grid grid-cols-2 gap-4">
            <div class="space-y-2">
              <label class="block text-xs font-medium text-slate-500">周起始</label>
              <select
                v-model.number="weekStartNum"
                class="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-emerald-500/50 dark:bg-slate-900 dark:border-slate-700"
              >
                <option v-for="opt in weekOptions" :key="opt.value" :value="opt.value">
                  {{ opt.label }}
                </option>
              </select>
            </div>

            <div class="space-y-2">
              <label class="block text-xs font-medium text-slate-500">范围天数</label>
              <input
                v-model.number="rangeDaysNum"
                type="number"
                min="7"
                max="730"
                step="1"
                class="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-emerald-500/50 dark:bg-slate-900 dark:border-slate-700"
              />
            </div>
          </div>

          <div class="space-y-2">
            <label class="block text-xs font-medium text-slate-500">截止日期</label>
            <input
              v-model="endDateStr"
              type="date"
              class="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-emerald-500/50 dark:bg-slate-900 dark:border-slate-700"
            />
          </div>

          <div class="pt-2">
            <button
              type="button"
              class="inline-flex items-center gap-1 rounded-md bg-emerald-600 px-3 py-2 text-xs font-medium text-white hover:bg-emerald-700 active:bg-emerald-800"
              @click="refresh"
            >
              随机刷新数据
            </button>
          </div>
        </div>
      </template>
    </Card> -->

    <Panel header="相册" toggleable>
      <template #icons>
        <SplitButton icon="pi pi-refresh" severity="secondary" outlined rounded @click="fetchAlbums" :model="albumsRefreshModel"/>
      </template>

      <div class="space-y-2">
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-3">
          <AlbumCard
            v-for="a in albums"
            :key="a.id"
            :name="a.name"
            :asset-count="a.assetCount"
            :last-update-time="a.lastUpdateTime"
          />
          <div v-if="!albums || albums.length === 0" class="text-xs text-slate-500">暂无相册</div>
        </div>
      </div>
    </Panel>

    <!-- 创建/编辑 计划任务 -->
    <Dialog
      v-model:visible="showCronDialog"
      modal
      :header="isEditing ? '编辑计划任务' : '创建计划任务'"
      class="w-full sm:w-[520px]"
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

        <div class="grid grid-cols-2 gap-4">
          <div class="space-y-2">
            <label class="block text-xs font-medium text-slate-500">Cron 表达式</label>
            <InputText v-model="cronForm.config.expression" placeholder="0 0 23 * * ?" class="w-full" />
            <div v-if="formErrors.expression" class="text-xs text-red-500">
              {{ formErrors.expression }}
            </div>
            <div class="text-[10px] text-slate-400">支持标准 6/7 字段 Cron。例：0 0 23 * * ?</div>
          </div>
          <div class="space-y-2">
            <label class="block text-xs font-medium text-slate-500">时区</label>
            <Dropdown
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
          <InputText v-model="cronForm.config.targetPath" placeholder="例如：/Volumes/Photos/Xiaomi" class="w-full" />
          <div v-if="formErrors.targetPath" class="text-xs text-red-500">{{ formErrors.targetPath }}</div>
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
          <div class="flex items-center gap-2 text-xs text-slate-600">
            <InputSwitch v-model="cronForm.config.rewriteExifTime" />
            <span>重写 EXIF 时间</span>
          </div>
        </div>

        <div class="space-y-2">
          <label class="block text-xs font-medium text-slate-500">关联相册</label>
          <MultiSelect
            v-model="cronForm.albumIds"
            :options="albumOptions"
            display="chip"
            optionLabel="label"
            optionValue="value"
            placeholder="选择相册"
            class="w-full"
            filter
          />
        </div>

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
    <Dialog
      v-model:visible="showDeleteVisible"
      modal
      header="删除计划任务"
      class="w-full sm:w-[420px]"
    >
      <div class="text-sm text-slate-700">确定要删除该计划任务吗？该操作不可恢复。</div>
      <template #footer>
        <div class="flex items-center justify-end gap-2 w-full">
          <Button
            label="取消"
            severity="secondary"
            text
            @click="
              () => {
                showDeleteId = null
                showDeleteVisible = false
              }
            "
          />
          <Button label="删除" severity="danger" :loading="deleting" @click="confirmDelete" />
        </div>
      </template>
    </Dialog>

    <!-- 立即执行确认 -->
    <Dialog
      v-model:visible="showExecuteVisible"
      modal
      header="立即执行"
      class="w-full sm:w-[420px]"
    >
      <div class="text-sm text-slate-700">确定要立即触发该计划任务的执行吗？该操作较为耗时，将在后台执行。</div>
      <template #footer>
        <div class="flex items-center justify-end gap-2 w-full">
          <Button
            label="取消"
            severity="secondary"
            text
            @click="
              () => {
                showExecuteId = null
                showExecuteVisible = false
              }
            "
          />
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
