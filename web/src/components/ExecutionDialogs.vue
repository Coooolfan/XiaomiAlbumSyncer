<script setup lang="ts">
import { computed } from 'vue'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'

const props = defineProps<{
  deleteVisible: boolean
  executeVisible: boolean
  executeExifVisible: boolean
  executeRewriteFsVisible: boolean
  deleting: boolean
  executing: boolean
  executingExif: boolean
  executingRewriteFs: boolean
}>()

const emit = defineEmits<{
  (e: 'update:deleteVisible', value: boolean): void
  (e: 'update:executeVisible', value: boolean): void
  (e: 'update:executeExifVisible', value: boolean): void
  (e: 'update:executeRewriteFsVisible', value: boolean): void
  (e: 'closeDelete'): void
  (e: 'closeExecute'): void
  (e: 'closeExecuteExif'): void
  (e: 'closeExecuteRewriteFs'): void
  (e: 'confirmDelete'): void
  (e: 'confirmExecute'): void
  (e: 'confirmExecuteExif'): void
  (e: 'confirmExecuteRewriteFs'): void
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
</script>

<template>
  <!-- 删除确认 -->
  <Dialog v-model:visible="deleteVisible" modal header="删除计划任务" class="w-full sm:w-105">
    <div class="text-sm text-slate-700 dark:text-slate-200">
      确定要删除该计划任务吗？该操作不可恢复。
    </div>
    <template #footer>
      <div class="flex items-center justify-end gap-2 w-full">
        <Button label="取消" severity="secondary" text @click="closeDelete" />
        <Button
          label="删除"
          severity="danger"
          :loading="props.deleting"
          @click="emit('confirmDelete')"
        />
      </div>
    </template>
  </Dialog>

  <!-- 立即执行确认 -->
  <Dialog v-model:visible="executeVisible" modal header="立即执行" class="w-full sm:w-105">
    <div class="text-sm text-slate-700 dark:text-slate-200">
      确定要立即触发该计划任务的执行吗？该操作较为耗时，将在后台执行。
    </div>
    <template #footer>
      <div class="flex items-center justify-end gap-2 w-full">
        <Button label="取消" severity="secondary" text @click="closeExecute" />
        <Button
          label="执行"
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
    header="立即执行 EXIF 填充"
    class="w-full sm:w-105"
  >
    <div class="text-sm text-slate-700 dark:text-slate-200">
      确定要手动触发该计划任务的 EXIF
      填充操作吗？该操作较为耗时，将在后台执行。可观察程序日志查看进度。<br />会对此计划任务所有下载过的文件执行此操作。
    </div>
    <template #footer>
      <div class="flex items-center justify-end gap-2 w-full">
        <Button label="取消" severity="secondary" text @click="closeExecuteExif" />
        <Button
          label="执行"
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
    header="立即重写文件系统时间"
    class="w-full sm:w-105"
  >
    <div class="text-sm text-slate-700 dark:text-slate-200">
      确定要对该计划任务已下载的文件执行文件系统时间重写吗？该操作较为耗时，将在后台执行。可观察程序日志查看进度。<br />会对此计划任务所有下载过的文件执行此操作。
    </div>
    <template #footer>
      <div class="flex items-center justify-end gap-2 w-full">
        <Button label="取消" severity="secondary" text @click="closeExecuteRewriteFs" />
        <Button
          label="执行"
          severity="info"
          :loading="props.executingRewriteFs"
          @click="emit('confirmExecuteRewriteFs')"
        />
      </div>
    </template>
  </Dialog>
</template>
