<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'

const { t } = useI18n()

const props = defineProps<{
  deleteVisible: boolean
  executeVisible: boolean
  executeExifVisible: boolean
  executeRewriteFsVisible: boolean
  clearHistoryVisible: boolean
  deleting: boolean
  executing: boolean
  executingExif: boolean
  executingRewriteFs: boolean
  clearingHistory: boolean
}>()

const emit = defineEmits<{
  (e: 'update:deleteVisible', value: boolean): void
  (e: 'update:executeVisible', value: boolean): void
  (e: 'update:executeExifVisible', value: boolean): void
  (e: 'update:executeRewriteFsVisible', value: boolean): void
  (e: 'update:clearHistoryVisible', value: boolean): void
  (e: 'closeDelete'): void
  (e: 'closeExecute'): void
  (e: 'closeExecuteExif'): void
  (e: 'closeExecuteRewriteFs'): void
  (e: 'closeClearHistory'): void
  (e: 'confirmDelete'): void
  (e: 'confirmExecute'): void
  (e: 'confirmExecuteExif'): void
  (e: 'confirmExecuteRewriteFs'): void
  (e: 'confirmClearHistory'): void
}>()

const deleteVisible = computed({
  get: () => props.deleteVisible,
  set: (value: boolean) => emit('update:deleteVisible', value),
})

const executeVisible = computed({
  get: () => props.executeVisible,
  set: (value: boolean) => emit('update:executeVisible', value),
})

const executeExifVisible = computed({
  get: () => props.executeExifVisible,
  set: (value: boolean) => emit('update:executeExifVisible', value),
})

const executeRewriteFsVisible = computed({
  get: () => props.executeRewriteFsVisible,
  set: (value: boolean) => emit('update:executeRewriteFsVisible', value),
})

const clearHistoryVisible = computed({
  get: () => props.clearHistoryVisible,
  set: (value: boolean) => emit('update:clearHistoryVisible', value),
})

function closeDelete() {
  deleteVisible.value = false
  emit('closeDelete')
}

function closeExecute() {
  executeVisible.value = false
  emit('closeExecute')
}

function closeExecuteExif() {
  executeExifVisible.value = false
  emit('closeExecuteExif')
}

function closeExecuteRewriteFs() {
  executeRewriteFsVisible.value = false
  emit('closeExecuteRewriteFs')
}

function closeClearHistory() {
  clearHistoryVisible.value = false
  emit('closeClearHistory')
}
</script>

<template>
  <!-- 删除确认 -->
  <Dialog
    v-model:visible="deleteVisible"
    modal
    :header="t('schedule.dialog.delete.header')"
    class="w-full sm:w-105"
  >
    <div class="text-sm text-slate-700 dark:text-slate-200">
      {{ t('schedule.dialog.delete.message') }}
    </div>
    <template #footer>
      <div class="flex items-center justify-end gap-2 w-full">
        <Button :label="t('common.action.cancel')" severity="secondary" text @click="closeDelete" />
        <Button
          :label="t('common.action.delete')"
          severity="danger"
          :loading="props.deleting"
          @click="emit('confirmDelete')"
        />
      </div>
    </template>
  </Dialog>

  <!-- 立即执行确认 -->
  <Dialog
    v-model:visible="executeVisible"
    modal
    :header="t('schedule.dialog.execute.header')"
    class="w-full sm:w-105"
  >
    <div class="text-sm text-slate-700 dark:text-slate-200">
      {{ t('schedule.dialog.execute.message') }}
    </div>
    <template #footer>
      <div class="flex items-center justify-end gap-2 w-full">
        <Button
          :label="t('common.action.cancel')"
          severity="secondary"
          text
          @click="closeExecute"
        />
        <Button
          :label="t('schedule.dialog.run')"
          severity="warning"
          :loading="props.executing"
          @click="emit('confirmExecute')"
        />
      </div>
    </template>
  </Dialog>

  <!-- 立即执行 EXIF 填充 -->
  <Dialog
    v-model:visible="executeExifVisible"
    modal
    :header="t('schedule.dialog.executeExif.header')"
    class="w-full sm:w-105"
  >
    <div class="text-sm text-slate-700 dark:text-slate-200">
      {{ t('schedule.dialog.executeExif.message') }}<br />{{
        t('schedule.dialog.appliesToAllFiles')
      }}
    </div>
    <template #footer>
      <div class="flex items-center justify-end gap-2 w-full">
        <Button
          :label="t('common.action.cancel')"
          severity="secondary"
          text
          @click="closeExecuteExif"
        />
        <Button
          :label="t('schedule.dialog.run')"
          severity="info"
          :loading="props.executingExif"
          @click="emit('confirmExecuteExif')"
        />
      </div>
    </template>
  </Dialog>

  <!-- 立即执行文件系统时间重写 -->
  <Dialog
    v-model:visible="executeRewriteFsVisible"
    modal
    :header="t('schedule.dialog.executeRewriteFs.header')"
    class="w-full sm:w-105"
  >
    <div class="text-sm text-slate-700 dark:text-slate-200">
      {{ t('schedule.dialog.executeRewriteFs.message') }}<br />{{
        t('schedule.dialog.appliesToAllFiles')
      }}
    </div>
    <template #footer>
      <div class="flex items-center justify-end gap-2 w-full">
        <Button
          :label="t('common.action.cancel')"
          severity="secondary"
          text
          @click="closeExecuteRewriteFs"
        />
        <Button
          :label="t('schedule.dialog.run')"
          severity="info"
          :loading="props.executingRewriteFs"
          @click="emit('confirmExecuteRewriteFs')"
        />
      </div>
    </template>
  </Dialog>

  <!-- 清理任务历史 -->
  <Dialog
    v-model:visible="clearHistoryVisible"
    modal
    :header="t('schedule.dialog.clearHistory.header')"
    class="w-full sm:w-105"
  >
    <div class="text-sm text-slate-700 dark:text-slate-200 space-y-2">
      <div>{{ t('schedule.dialog.clearHistory.line1') }}</div>
      <div>
        {{ t('schedule.dialog.clearHistory.line2') }}
      </div>
      <div>
        {{ t('schedule.dialog.clearHistory.line3a') }}
        <bold class="text-amber-700 dark:text-amber-400">{{
          t('schedule.dialog.clearHistory.line3b')
        }}</bold>
        {{ t('schedule.dialog.clearHistory.line3c') }}
      </div>
    </div>
    <template #footer>
      <div class="flex items-center justify-end gap-2 w-full">
        <Button
          :label="t('common.action.cancel')"
          severity="secondary"
          text
          @click="closeClearHistory"
        />
        <Button
          :label="t('schedule.dialog.clear')"
          severity="danger"
          :loading="props.clearingHistory"
          @click="emit('confirmClearHistory')"
        />
      </div>
    </template>
  </Dialog>
</template>
