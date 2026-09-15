<script setup lang="ts">
import { computed, onBeforeUnmount, ref, toRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
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
  targetPathMountWarning: boolean
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'submit'): void
}>()

const { t } = useI18n()

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
    :header="props.isEditing ? t('cronform.title.edit') : t('cronform.title.create')"
    :class="[
      'transition-all duration-300',
      showExpressionHelp || showCronHelp ? 'w-full sm:w-250' : 'w-full sm:w-130',
    ]"
    :dismissableMask="true"
  >
    <div class="flex gap-8 items-start">
      <div class="flex-1 space-y-4 min-w-0">
        <div class="space-y-2">
          <label class="block text-xs font-medium text-slate-500 dark:text-slate-400">{{
            t('common.field.name')
          }}</label>
          <InputText
            v-model="form.name"
            :placeholder="t('cronform.field.namePlaceholder')"
            class="w-full"
          />
          <div v-if="formErrors.name" class="text-xs text-red-500">{{ formErrors.name }}</div>
        </div>

        <div class="space-y-2">
          <label class="block text-xs font-medium text-slate-500 dark:text-slate-400">{{
            t('common.field.description')
          }}</label>
          <Textarea
            v-model="form.description"
            rows="2"
            autoResize
            :placeholder="t('common.field.optional')"
            class="w-full"
          />
        </div>

        <div class="space-y-2">
          <label class="block text-xs font-medium text-slate-500 dark:text-slate-400">{{
            t('cronform.field.account')
          }}</label>
          <Select
            v-model="form.accountId"
            :options="accountOptions"
            optionLabel="label"
            optionValue="value"
            :placeholder="t('cronform.field.accountPlaceholder')"
            class="w-full"
            :disabled="props.isEditing"
          />
          <div v-if="formErrors.accountId" class="text-xs text-red-500">
            {{ formErrors.accountId }}
          </div>
          <div v-if="props.isEditing" class="text-[10px] text-slate-400 dark:text-slate-500">
            {{ t('cronform.field.accountLockedHint') }}
          </div>
        </div>

        <div class="grid grid-cols-2 gap-4">
          <div class="space-y-2">
            <div class="flex items-center justify-between">
              <label class="block text-xs font-medium text-slate-500 dark:text-slate-400">{{
                t('cronform.field.expression')
              }}</label>
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
            <div class="text-[10px] text-slate-400 dark:text-slate-500">
              {{ t('cronform.field.expressionHint') }}<br />{{
                t('cronform.field.expressionExample')
              }}
            </div>
          </div>
          <div class="space-y-2">
            <label class="block text-xs font-medium text-slate-500 dark:text-slate-400 mb-4">{{
              t('cronform.field.timeZone')
            }}</label>
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
          <label class="block text-xs font-medium text-slate-500 dark:text-slate-400">{{
            t('cronform.field.targetPath')
          }}</label>
          <InputText v-model="form.config.targetPath" placeholder="/app/download" class="w-full" />
          <div v-if="formErrors.targetPath" class="text-xs text-red-500">
            {{ formErrors.targetPath }}
          </div>
          <div class="text-[10px] text-slate-400 dark:text-slate-500">
            {{ t('cronform.field.targetPathHint') }}
          </div>
          <Message
            v-if="props.targetPathMountWarning"
            severity="warn"
            variant="simple"
            icon="pi pi-exclamation-triangle"
          >
            <div class="text-[11px]">
              {{ t('cronform.field.targetPathMountWarning') }}
            </div>
          </Message>
        </div>

        <div class="space-y-2">
          <div class="flex items-center justify-between">
            <label class="block text-xs font-medium text-slate-500 dark:text-slate-400">{{
              t('cronform.field.expressionTargetPath')
            }}</label>
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
            placeholder="/app/download/${album}/${download_YYYYMM}/${fileName}"
            class="w-full"
            @focus="openExpressionHelp"
          />
          <div class="text-[10px] text-slate-400 dark:text-slate-500">
            {{ t('cronform.field.expressionTargetPathHint') }}
          </div>
        </div>

        <div class="grid grid-cols-2 gap-4 pt-1 pb-2">
          <div class="flex items-center gap-2 text-sm text-slate-600 dark:text-slate-300">
            <ToggleSwitch v-model="form.config.downloadImages" />
            <span>{{ t('cronform.toggle.downloadImages') }}</span>
          </div>
          <div class="flex items-center gap-2 text-sm text-slate-600 dark:text-slate-300">
            <ToggleSwitch v-model="form.config.downloadVideos" />
            <span>{{ t('cronform.toggle.downloadVideos') }}</span>
          </div>
          <div class="flex items-center gap-2 text-sm text-slate-600 dark:text-slate-300">
            <ToggleSwitch v-model="form.config.downloadAudios" />
            <span>{{ t('cronform.toggle.downloadAudios') }}</span>
          </div>
          <div class="flex items-center gap-2 text-sm text-slate-600 dark:text-slate-300">
            <ToggleSwitch v-model="form.config.notify" />
            <span>{{ t('cronform.toggle.notify') }}</span>
          </div>
        </div>

        <div class="space-y-2">
          <label class="block text-xs font-medium text-slate-500 dark:text-slate-400">{{
            t('cronform.field.albums')
          }}</label>
          <MultiSelect
            v-model="form.albumIds"
            :options="formAlbumOptions"
            display="chip"
            optionLabel="label"
            optionValue="value"
            :placeholder="t('cronform.field.albumsPlaceholder')"
            class="w-full"
            filter
          />
        </div>

        <Message severity="info" variant="simple" icon="pi pi-info-circle">
          <i18n-t keypath="cronform.notice.text" tag="div" class="text-[12px]">
            <template #records>
              <span class="font-semibold">{{ t('cronform.notice.records') }}</span>
            </template>
            <template #independent>
              <span class="font-semibold">{{ t('cronform.notice.independent') }}</span>
            </template>
            <template #impact>
              <span class="font-semibold">{{ t('cronform.notice.impact') }}</span>
            </template>
            <template #sameAsset>
              <span class="font-semibold">{{ t('cronform.notice.sameAsset') }}</span>
            </template>
            <template #status>
              <span class="font-semibold">{{ t('cronform.notice.status') }}</span>
            </template>
            <template #judged>
              <span class="font-semibold">{{ t('cronform.notice.judged') }}</span>
            </template>
          </i18n-t>
        </Message>

        <Panel :header="t('cronform.advanced.title')" toggleable>
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div class="space-y-1">
              <div class="flex items-center gap-2 text-xs text-slate-600 dark:text-slate-300">
                <ToggleSwitch v-model="form.config.diffByTimeline" />
                <span>{{ t('cronform.advanced.diffByTimeline') }}</span>
              </div>
              <div class="text-[10px] text-slate-400 dark:text-slate-500">
                {{ t('cronform.advanced.diffByTimelineHint') }}
              </div>
            </div>
            <div class="space-y-1">
              <div class="flex items-center gap-2 text-xs text-slate-600 dark:text-slate-300">
                <ToggleSwitch v-model="form.config.rewriteExifTime" />
                <span>{{ t('cronform.advanced.rewriteExifTime') }}</span>
              </div>
              <div class="text-[10px] text-slate-400 dark:text-slate-500">
                {{ t('cronform.advanced.rewriteExifTimeHint') }}
              </div>
            </div>
            <div class="space-y-1">
              <div class="flex items-center gap-2 text-xs text-slate-600 dark:text-slate-300">
                <ToggleSwitch v-model="form.config.skipExistingFile" />
                <span>{{ t('cronform.advanced.skipExistingFile') }}</span>
              </div>
              <div class="text-[10px] text-slate-400 dark:text-slate-500">
                {{ t('cronform.advanced.skipExistingFileHint') }}
              </div>
            </div>
            <div class="space-y-1">
              <div class="flex items-center gap-2 text-xs text-slate-600 dark:text-slate-300">
                <ToggleSwitch v-model="form.config.rewriteFileSystemTime" />
                <span>{{ t('cronform.advanced.rewriteFileSystemTime') }}</span>
              </div>
              <div class="text-[10px] text-slate-400 dark:text-slate-500">
                {{ t('cronform.advanced.rewriteFileSystemTimeHint') }}
              </div>
            </div>
            <div class="space-y-1">
              <div class="flex items-center gap-2 text-xs text-slate-600 dark:text-slate-300">
                <ToggleSwitch v-model="form.config.checkSha1" />
                <span>{{ t('cronform.advanced.checkSha1') }}</span>
              </div>
              <div class="text-[10px] text-slate-400 dark:text-slate-500">
                {{ t('cronform.advanced.checkSha1Hint')
                }}<span class="font-bold">{{ t('cronform.advanced.checkSha1Warning') }}</span>
              </div>
            </div>
          </div>

          <div v-if="form.config.rewriteExifTime" class="space-y-2 mt-3">
            <label class="block text-xs font-medium text-slate-500 dark:text-slate-400">{{
              t('cronform.advanced.exifTimeZone')
            }}</label>
            <Select
              v-model="form.config.rewriteExifTimeZone"
              :options="timeZones"
              placeholder="Asia/Shanghai"
              filter
              class="w-full"
            />
            <div class="text-[10px] text-slate-400 dark:text-slate-500">
              {{ t('cronform.advanced.exifTimeZoneHint') }}
            </div>
          </div>

          <Panel :header="t('cronform.concurrency.title')" toggleable collapsed class="mt-4">
            <div class="text-[10px] text-slate-400 dark:text-slate-500 mb-4">
              {{ t('cronform.concurrency.warning') }}
            </div>

            <div class="grid grid-cols-2 sm:grid-cols-3 gap-4">
              <div class="space-y-2">
                <label class="block text-xs font-medium text-slate-500 dark:text-slate-400">{{
                  t('cronform.concurrency.fetchFromDbSize')
                }}</label>
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
                <div class="text-[10px] text-slate-400 dark:text-slate-500">
                  {{ t('cronform.concurrency.fetchFromDbSizeHint') }}
                </div>
                <div v-if="formErrors.concurrency" class="text-xs text-red-500">
                  {{ formErrors.concurrency }}
                </div>
              </div>

              <div class="space-y-2">
                <label class="block text-xs font-medium text-slate-500 dark:text-slate-400">{{
                  t('cronform.concurrency.downloaders')
                }}</label>
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
                <div class="text-[10px] text-slate-400 dark:text-slate-500">
                  {{ t('cronform.concurrency.downloadersHint') }}
                </div>
              </div>

              <div class="space-y-2">
                <label class="block text-xs font-medium text-slate-500 dark:text-slate-400">{{
                  t('cronform.concurrency.fileTimeWorkers')
                }}</label>
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
                <div class="text-[10px] text-slate-400 dark:text-slate-500">
                  {{ t('cronform.concurrency.fileTimeWorkersHint') }}
                </div>
              </div>

              <div class="space-y-2">
                <label class="block text-xs font-medium text-slate-500 dark:text-slate-400">{{
                  t('cronform.concurrency.verifiers')
                }}</label>
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
                <div class="text-[10px] text-slate-400 dark:text-slate-500">
                  {{ t('cronform.concurrency.verifiersHint') }}
                </div>
              </div>

              <div class="space-y-2">
                <label class="block text-xs font-medium text-slate-500 dark:text-slate-400">{{
                  t('cronform.concurrency.exifProcessors')
                }}</label>
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
                <div class="text-[10px] text-slate-400 dark:text-slate-500">
                  {{ t('cronform.concurrency.exifProcessorsHint') }}
                </div>
              </div>
            </div>
          </Panel>
        </Panel>

        <div class="flex items-center justify-between pt-1">
          <div class="flex items-center gap-2 text-xs text-slate-600 dark:text-slate-300">
            <ToggleSwitch v-model="form.enabled" />
            <span>{{ t('common.status.enabled') }}</span>
          </div>
          <div class="flex items-center gap-2">
            <Button
              :label="t('common.action.cancel')"
              severity="secondary"
              text
              @click="closeDialog"
            />
            <Button
              :label="props.isEditing ? t('common.action.save') : t('common.action.create')"
              :loading="props.saving"
              @click="emit('submit')"
            />
          </div>
        </div>
      </div>

      <div
        v-if="showExpressionHelp || showCronHelp"
        class="flex-1 border-l border-slate-100 dark:border-slate-800 pl-8 hidden lg:block self-stretch min-w-0"
      >
        <ExpressionPathHelp v-if="showExpressionHelp" />
        <CronHelp v-if="showCronHelp" />
      </div>
    </div>
  </Dialog>
</template>
