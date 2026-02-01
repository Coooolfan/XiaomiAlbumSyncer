<script setup lang="ts">
import Card from 'primevue/card'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import { computed, onMounted, ref } from 'vue'
import { api } from '@/ApiInstance'
import type { SyncStatusInfo, ChangeSummary } from '@/__generated/services/SyncController'

const props = defineProps<{
  crontabId: number
}>()

const emit = defineEmits<{
  (e: 'execute'): void
}>()

const syncStatus = ref<SyncStatusInfo | null>(null)
const changes = ref<ChangeSummary | null>(null)
const loading = ref(false)
const detecting = ref(false)

async function loadSyncStatus() {
  if (!props.crontabId) return
  loading.value = true
  try {
    syncStatus.value = await api.syncController.getSyncStatus({
      crontabId: props.crontabId,
    })
  } catch (e) {
    console.error('加载同步状态失败:', e)
  } finally {
    loading.value = false
  }
}

async function detectChanges() {
  if (!props.crontabId) return
  detecting.value = true
  try {
    changes.value = await api.syncController.detectChanges({
      crontabId: props.crontabId,
    })
  } catch (e) {
    console.error('检测变化失败:', e)
  } finally {
    detecting.value = false
  }
}

function formatTime(t?: string) {
  if (!t) return '-'
  try {
    const d = new Date(t)
    if (Number.isNaN(d.getTime())) return t
    return d.toLocaleString()
  } catch {
    return t
  }
}

const totalChanges = computed(() => {
  if (!changes.value) return 0
  return (
    changes.value.addedAssets.length +
    changes.value.deletedAssets.length +
    changes.value.updatedAssets.length
  )
})

onMounted(() => {
  loadSyncStatus()
})

defineExpose({
  refresh: loadSyncStatus,
})
</script>

<template>
  <Card class="overflow-hidden ring-1 ring-slate-200/60">
    <template #title>
      <div class="flex items-center justify-between pb-4">
        <div class="flex items-center gap-3">
          <i class="pi pi-sync text-blue-500" />
          <span class="font-medium text-slate-700 text-base">云端同步</span>
        </div>
        <div class="flex items-center gap-2">
          <Button
            icon="pi pi-search"
            size="small"
            label="检测变化"
            :loading="detecting"
            @click="detectChanges"
          />
          <Button
            icon="pi pi-sync"
            size="small"
            severity="success"
            label="执行同步"
            :disabled="syncStatus?.isRunning"
            @click="emit('execute')"
          />
        </div>
      </div>
    </template>

    <template #content>
      <div class="space-y-4">
        <!-- 同步状态 -->
        <div class="rounded-md bg-slate-50 dark:bg-slate-900/30 ring-1 ring-slate-200/60 p-3">
          <div class="text-xs font-medium text-slate-500 mb-2">同步状态</div>
          <div v-if="loading" class="text-xs text-slate-400">加载中...</div>
          <div v-else-if="syncStatus" class="space-y-2">
            <div class="flex items-center justify-between">
              <span class="text-sm text-slate-600">运行状态</span>
              <Tag
                :severity="syncStatus.isRunning ? 'info' : 'secondary'"
                :value="syncStatus.isRunning ? '运行中' : '空闲'"
              />
            </div>
            <div class="flex items-center justify-between">
              <span class="text-sm text-slate-600">上次同步时间</span>
              <span class="text-sm text-slate-700">{{ formatTime(syncStatus.lastSyncTime) }}</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-sm text-slate-600">上次同步结果</span>
              <Tag
                v-if="syncStatus.lastSyncResult"
                :severity="syncStatus.lastSyncResult === 'COMPLETED' ? 'success' : 'danger'"
                :value="syncStatus.lastSyncResult === 'COMPLETED' ? '成功' : '失败'"
              />
              <span v-else class="text-sm text-slate-400">-</span>
            </div>
          </div>
        </div>

        <!-- 变化检测结果 -->
        <div
          v-if="changes"
          class="rounded-md bg-blue-50 dark:bg-blue-900/20 ring-1 ring-blue-200/60 p-3"
        >
          <div class="text-xs font-medium text-blue-700 mb-2">检测到的变化</div>
          <div class="space-y-2">
            <div class="flex items-center justify-between">
              <span class="text-sm text-slate-600">新增照片</span>
              <span class="text-sm font-mono text-emerald-600">{{
                changes.addedAssets.length
              }}</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-sm text-slate-600">删除照片</span>
              <span class="text-sm font-mono text-red-600">{{ changes.deletedAssets.length }}</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-sm text-slate-600">修改照片</span>
              <span class="text-sm font-mono text-amber-600">{{
                changes.updatedAssets.length
              }}</span>
            </div>
            <div class="flex items-center justify-between pt-2 border-t border-blue-200">
              <span class="text-sm font-medium text-slate-700">总计变化</span>
              <span class="text-sm font-mono font-bold text-blue-700">{{ totalChanges }}</span>
            </div>
          </div>
        </div>
      </div>
    </template>
  </Card>
</template>
