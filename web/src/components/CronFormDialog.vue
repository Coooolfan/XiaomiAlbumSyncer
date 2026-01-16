<script setup lang="ts">
import { computed, onBeforeUnmount, ref, toRef, watch } from 'vue'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import ToggleSwitch from 'primevue/toggleswitch'
import Select from 'primevue/select'
import MultiSelect from 'primevue/multiselect'
import InputNumber from 'primevue/inputnumber'
import Panel from 'primevue/panel'
import Button from 'primevue/button'
import Message from 'primevue/message'
import ExpressionPathHelp from '@/components/ExpressionPathHelp.vue'
import CronHelp from '@/components/CronHelp.vue'
import type { LocalCronForm } from '@/utils/crontabForm'

const props = defineProps<{
  visible: boolean
  isEditing: boolean
  saving: boolean
  form: LocalCronForm
  formErrors: Record<string, string>
  timeZones: ReadonlyArray<string>
  accountOptions: ReadonlyArray<{ label: string; value: number }>
  formAlbumOptions: ReadonlyArray<{ label: string; value: number }>
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'submit'): void
}>()

const visibleProxy = computed({
  get: () => props.visible,
  set: (value: boolean) => emit('update:visible', value),
})

const form = toRef(props, 'form')
const formErrors = toRef(props, 'formErrors')
const accountOptions = computed(() => [...props.accountOptions])
const formAlbumOptions = computed(() => [...props.formAlbumOptions])
const timeZones = computed(() => [...props.timeZones])

const showExpressionHelp = ref(false)
const showCronHelp = ref(false)
let hideHelpTimer: number | undefined

function toggleCronHelp() {
  showCronHelp.value = !showCronHelp.value
  if (showCronHelp.value) showExpressionHelp.value = false
}

function toggleExpressionHelp() {
  showExpressionHelp.value = !showExpressionHelp.value
  if (showExpressionHelp.value) showCronHelp.value = false
}

function openCronHelp() {
  showCronHelp.value = true
  showExpressionHelp.value = false
}

function openExpressionHelp() {
  showExpressionHelp.value = true
  showCronHelp.value = false
}

function closeDialog() {
  visibleProxy.value = false
}

watch(
  () => props.visible,
  (val) => {
    if (!val) {
      if (hideHelpTimer) window.clearTimeout(hideHelpTimer)
      hideHelpTimer = window.setTimeout(() => {
        showExpressionHelp.value = false
        showCronHelp.value = false
      }, 300)
    }
  },
)

onBeforeUnmount(() => {
  if (hideHelpTimer) window.clearTimeout(hideHelpTimer)
})
</script>

<template>
  <Dialog
    v-model:visible="visibleProxy"
    modal
    :header="props.isEditing ? '编辑计划任务' : '创建计划任务'"
    :class="[
      'transition-all duration-300',
      showExpressionHelp || showCronHelp ? 'w-full sm:w-250' : 'w-full sm:w-130',
    ]"
    :dismissableMask="true"
  >
    <div class="flex gap-8 items-start">
      <div class="flex-1 space-y-4 min-w-0">
        <div class="space-y-2">
          <label class="block text-xs font-medium text-slate-500">名称</label>
          <InputText v-model="form.name" placeholder="例如：每日同步" class="w-full" />
          <div v-if="formErrors.name" class="text-xs text-red-500">{{ formErrors.name }}</div>
        </div>

        <div class="space-y-2">
          <label class="block text-xs font-medium text-slate-500">描述</label>
          <Textarea
            v-model="form.description"
            rows="2"
            autoResize
            placeholder="可选"
            class="w-full"
          />
        </div>

        <div class="space-y-2">
          <label class="block text-xs font-medium text-slate-500">归属账号</label>
          <Select
            v-model="form.accountId"
            :options="accountOptions"
            optionLabel="label"
            optionValue="value"
            placeholder="选择小米账号"
            class="w-full"
            :disabled="props.isEditing"
          />
          <div v-if="formErrors.accountId" class="text-xs text-red-500">
            {{ formErrors.accountId }}
          </div>
          <div v-if="props.isEditing" class="text-[10px] text-slate-400">
            计划任务创建后无法更改归属账号
          </div>
        </div>

        <div class="grid grid-cols-2 gap-4">
          <div class="space-y-2">
            <div class="flex items-center justify-between">
              <label class="block text-xs font-medium text-slate-500">Cron 表达式</label>
              <Button
                icon="pi pi-question-circle"
                variant="text"
                severity="secondary"
                size="small"
                class="w-6! h-6!"
                @click="toggleCronHelp"
              />
            </div>
            <InputText
              v-model="form.config.expression"
              placeholder="0 0 23 * * ?"
              class="w-full"
              @focus="openCronHelp"
            />

            <div v-if="formErrors.expression" class="text-xs text-red-500">
              {{ formErrors.expression }}
            </div>
            <div class="text-[10px] text-slate-400">
              支持标准 6/7 字段 Cron 表达式<br />例：0 0 23 * * ? 表示每天 23 点执行
            </div>
          </div>
          <div class="space-y-2">
            <label class="block text-xs font-medium text-slate-500 mb-4">时区</label>
            <Select
              v-model="form.config.timeZone"
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
          <InputText v-model="form.config.targetPath" placeholder="./download" class="w-full" />
          <div v-if="formErrors.targetPath" class="text-xs text-red-500">
            {{ formErrors.targetPath }}
          </div>
          <div class="text-[10px] text-slate-400">
            如在容器环境下运行，请确保已将此路径映射到宿主机。程序将在此路径下创建相册各自的文件夹。
          </div>
        </div>

        <div class="space-y-2">
          <div class="flex items-center justify-between">
            <label class="block text-xs font-medium text-slate-500">表达式路径 (高级)</label>
            <Button
              icon="pi pi-question-circle"
              variant="text"
              severity="secondary"
              rounded
              size="small"
              class="w-6! h-6!"
              @click="toggleExpressionHelp"
            />
          </div>
          <InputText
            v-model="form.config.expressionTargetPath"
            placeholder="./download/${album}/${download_YYYYMM}/${fileName}"
            class="w-full"
            @focus="openExpressionHelp"
          />
          <div class="text-[10px] text-slate-400">
            使用表达式自定义路径结构。此值有效将忽略上方的“保存路径”。
          </div>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-3 gap-4 pt-1">
          <div class="flex items-center gap-2 text-xs text-slate-600">
            <ToggleSwitch v-model="form.config.downloadImages" />
            <span>下载照片</span>
          </div>
          <div class="flex items-center gap-2 text-xs text-slate-600">
            <ToggleSwitch v-model="form.config.downloadVideos" />
            <span>下载视频</span>
          </div>
          <div class="flex items-center gap-2 text-xs text-slate-600">
            <ToggleSwitch v-model="form.config.downloadAudios" />
            <span>下载录音</span>
          </div>
        </div>

        <div class="space-y-2">
          <label class="block text-xs font-medium text-slate-500">关联相册</label>
          <MultiSelect
            v-model="form.albumIds"
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
                <ToggleSwitch v-model="form.config.diffByTimeline" />
                <span>按时间线比对差异</span>
              </div>
              <div class="text-[10px] text-slate-400">
                通过对比上一次同步的相册时间线，将相册资产的获取范围限定为存在变动的日期。
              </div>
            </div>
            <div class="space-y-1">
              <div class="flex items-center gap-2 text-xs text-slate-600">
                <ToggleSwitch v-model="form.config.rewriteExifTime" />
                <span>填充 EXIF 时间</span>
              </div>
              <div class="text-[10px] text-slate-400">
                将资产在小米云服务的时间写入 EXIF 时间，仅在资产不存在 EXIF 时间时生效。
              </div>
            </div>
            <div class="space-y-1">
              <div class="flex items-center gap-2 text-xs text-slate-600">
                <ToggleSwitch v-model="form.config.skipExistingFile" />
                <span>跳过已存在文件</span>
              </div>
              <div class="text-[10px] text-slate-400">
                若资产的目标文件路径已存在，将跳过下载。仅适用于保存路径中已有存量数据。
              </div>
            </div>
            <div class="space-y-1">
              <div class="flex items-center gap-2 text-xs text-slate-600">
                <ToggleSwitch v-model="form.config.rewriteFileSystemTime" />
                <span>重写文件时间</span>
              </div>
              <div class="text-[10px] text-slate-400">
                同步完成后，将资产的文件系统时间修改为对应的小米云服务上的时间。
              </div>
            </div>
            <div class="space-y-1">
              <div class="flex items-center gap-2 text-xs text-slate-600">
                <ToggleSwitch v-model="form.config.checkSha1" />
                <span>校验 SHA1</span>
              </div>
              <div class="text-[10px] text-slate-400">
                对下载的文件进行 SHA1 校验，失败则重新下载。<span class="font-bold"
                  >没必要开。</span
                >
              </div>
            </div>
          </div>

          <div v-if="form.config.rewriteExifTime" class="space-y-2 mt-3">
            <label class="block text-xs font-medium text-slate-500">EXIF 时区</label>
            <Select
              v-model="form.config.rewriteExifTimeZone"
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
                  v-model="form.config.fetchFromDbSize"
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
                  v-model="form.config.downloaders"
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
                  v-model="form.config.fileTimeWorkers"
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
                  v-model="form.config.verifiers"
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
                  v-model="form.config.exifProcessors"
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
            <ToggleSwitch v-model="form.enabled" />
            <span>启用</span>
          </div>
          <div class="flex items-center gap-2">
            <Button label="取消" severity="secondary" text @click="closeDialog" />
            <Button
              :label="props.isEditing ? '保存' : '创建'"
              :loading="props.saving"
              @click="emit('submit')"
            />
          </div>
        </div>
      </div>

      <div
        v-if="showExpressionHelp || showCronHelp"
        class="flex-1 border-l border-slate-100 pl-8 hidden lg:block self-stretch min-w-0"
      >
        <ExpressionPathHelp v-if="showExpressionHelp" />
        <CronHelp v-if="showCronHelp" />
      </div>
    </div>
  </Dialog>
</template>
