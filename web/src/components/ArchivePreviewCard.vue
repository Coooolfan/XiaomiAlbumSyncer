<script setup lang="ts">
import Card from 'primevue/card'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import Dialog from 'primevue/dialog'
import { computed, ref } from 'vue'
import { api } from '@/ApiInstance'
import type { ArchivePlan } from '@/__generated/services/ArchiveController'

const props = defineProps<{
  crontabId: number
}>()

const emit = defineEmits<{
  (e: 'execute', confirmed: boolean): void
}>()

const archivePlan = ref<ArchivePlan | null>(null)
const loading = ref(false)
const showConfirmDialog = ref(false)

async function previewArchive() {
  if (!props.crontabId) return
  loading.value = true
  try {
    archivePlan.value = await api.archiveController.previewArchive({
      crontabId: props.crontabId,
    })
  } catch (e) {
    console.error('预览归档失败:', e)
  } finally {
    loading.value = false
  }
}

function formatSize(bytes: number) {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + ' ' + sizes[i]
}

function formatDate(dateStr?: string) {
  if (!dateStr) return '-'
  try {
    const d = new Date(dateStr)
    if (Number.isNaN(d.getTime())) return dateStr
    return d.toLocaleDateString()
  } catch {
    return dateStr
  }
}

function confirmExecute() {
  showConfirmDialog.value = true
}

function executeArchive() {
  showConfirmDialog.value = false
  emit('execute', true)
  archivePlan.value = null
}

const hasAssets = computed(() => {
  return archivePlan.value && archivePlan.value.assetsToArchive.length > 0
})

defineExpose({
  refresh: previewArchive,
})
</script>

<template>
  <Card class="overflow-hidden ring-1 ring-slate-200/60">
    <template #title>
      <div class="flex items-center justify-between pb-4">
        <div class="flex items-center gap-3">
          <i class="pi pi-inbox text-purple-500" />
          <span class="font-medium text-slate-700 text-base">智能归档</span>
        </div>
        <div class="flex items-center gap-2">
          <Button
            icon="pi pi-eye"
            size="small"
            label="预览归档"
            :loading="loading"
            @click="previewArchive"
          />
          <Button
            v-if="hasAssets"
            icon="pi pi-check"
            size="small"
            severity="warning"
            label="执行归档"
            @click="confirmExecute"
          />
        </div>
      </div>
    </template>

    <template #content>
      <div v-if="!archivePlan" class="text-center py-8 text-slate-400">
        <i class="pi pi-inbox text-4xl mb-2" />
        <p class="text-sm">点击"预览归档"查看归档计划</p>
      </div>

      <div v-else class="space-y-4">
        <!-- 归档摘要 -->
        <div class="rounded-md bg-purple-50 dark:bg-purple-900/20 ring-1 ring-purple-200/60 p-3">
          <div class="text-xs font-medium text-purple-700 mb-2">归档摘要</div>
          <div class="space-y-2">
            <div v-if="archivePlan.archiveBeforeDate" class="flex items-center justify-between">
              <span class="text-sm text-slate-600">归档截止日期</span>
              <span class="text-sm font-mono text-slate-700">{{
                formatDate(archivePlan.archiveBeforeDate)
              }}</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-sm text-slate-600">待归档照片数</span>
              <span class="text-sm font-mono text-purple-700 font-bold">{{
                archivePlan.assetsToArchive.length
              }}</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-sm text-slate-600">预计释放空间</span>
              <span class="text-sm font-mono text-emerald-600 font-bold">{{
                formatSize(archivePlan.estimatedFreedSpace)
              }}</span>
            </div>
          </div>
        </div>

        <!-- 待归档照片列表 -->
        <div
          v-if="archivePlan.assetsToArchive.length > 0"
          class="rounded-md bg-slate-50 dark:bg-slate-900/30 ring-1 ring-slate-200/60 p-3"
        >
          <div class="text-xs font-medium text-slate-500 mb-2">
            待归档照片 (显示前 10 个)
          </div>
          <div class="space-y-1 max-h-60 overflow-y-auto">
            <div
              v-for="asset in archivePlan.assetsToArchive.slice(0, 10)"
              :key="asset.id"
              class="flex items-center justify-between text-xs py-1"
            >
              <span class="text-slate-600 truncate flex-1">{{ asset.fileName }}</span>
              <div class="flex items-center gap-2 ml-2">
                <span class="text-slate-400">{{ formatSize(asset.size ?? 0) }}</span>
                <span class="text-slate-400">{{ formatDate(asset.dateTaken ?? '') }}</span>
              </div>
            </div>
          </div>
          <div
            v-if="archivePlan.assetsToArchive.length > 10"
            class="text-xs text-slate-400 mt-2 text-center"
          >
            还有 {{ archivePlan.assetsToArchive.length - 10 }} 个照片未显示
          </div>
        </div>

        <div v-else class="text-center py-4 text-slate-400">
          <i class="pi pi-check-circle text-2xl mb-2" />
          <p class="text-sm">没有需要归档的照片</p>
        </div>
      </div>
    </template>
  </Card>

  <!-- 确认对话框 -->
  <Dialog
    v-model:visible="showConfirmDialog"
    modal
    header="确认执行归档"
    :style="{ width: '30rem' }"
  >
    <div class="space-y-4">
      <p class="text-slate-600">
        即将归档 <span class="font-bold text-purple-600">{{ archivePlan?.assetsToArchive.length }}</span> 个照片，
        预计释放 <span class="font-bold text-emerald-600">{{ formatSize(archivePlan?.estimatedFreedSpace || 0) }}</span> 空间。
      </p>
      <p class="text-sm text-amber-600">
        <i class="pi pi-exclamation-triangle mr-2" />
        归档操作将把照片移动到 backup 文件夹，并可能删除云端照片。此操作不可撤销，请确认。
      </p>
    </div>
    <template #footer>
      <Button label="取消" severity="secondary" @click="showConfirmDialog = false" />
      <Button label="确认执行" severity="warning" @click="executeArchive" />
    </template>
  </Dialog>
</template>
