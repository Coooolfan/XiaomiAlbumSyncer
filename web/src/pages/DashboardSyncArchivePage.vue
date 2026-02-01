<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useToast } from 'primevue/usetoast'
import { storeToRefs } from 'pinia'
import { useAccountsStore } from '@/stores/accounts'
import { useCrontabsStore } from '@/stores/crontabs'
import SyncStatusCard from '@/components/SyncStatusCard.vue'
import ArchivePreviewCard from '@/components/ArchivePreviewCard.vue'
import CloudSpaceCard from '@/components/CloudSpaceCard.vue'
import Select from 'primevue/select'
import { api } from '@/ApiInstance'

const accountsStore = useAccountsStore()
const crontabsStore = useCrontabsStore()

const { accounts } = storeToRefs(accountsStore)
const { crontabs } = storeToRefs(crontabsStore)

const toast = useToast()

const selectedAccountId = ref<number | null>(null)
const selectedCrontabId = ref<number | null>(null)

const syncStatusRef = ref<InstanceType<typeof SyncStatusCard> | null>(null)
const archivePreviewRef = ref<InstanceType<typeof ArchivePreviewCard> | null>(null)
const cloudSpaceRef = ref<InstanceType<typeof CloudSpaceCard> | null>(null)

const accountOptions = computed(() =>
  accounts.value.map((a) => ({ label: a.nickname || a.userId, value: a.id })),
)

const crontabOptions = computed(() => {
  if (!selectedAccountId.value) return []
  return crontabs.value
    .filter((c) => c.accountId === selectedAccountId.value)
    .map((c) => ({ label: c.name, value: c.id }))
})

async function executeSync() {
  if (!selectedCrontabId.value) return
  try {
    const result = await api.syncController.executeSync({
      crontabId: selectedCrontabId.value,
    })
    toast.add({
      severity: 'success',
      summary: '同步已启动',
      detail: `同步记录ID: ${result.syncRecordId}`,
      life: 3000,
    })
    // 刷新状态
    setTimeout(() => {
      syncStatusRef.value?.refresh()
    }, 1000)
  } catch (e) {
    console.error('执行同步失败:', e)
    toast.add({
      severity: 'error',
      summary: '同步失败',
      detail: e instanceof Error ? e.message : String(e),
      life: 5000,
    })
  }
}

async function executeArchive(confirmed: boolean) {
  if (!selectedCrontabId.value || !confirmed) return
  try {
    const result = await api.archiveController.executeArchive({
      crontabId: selectedCrontabId.value,
      body: { confirmed: true },
    })
    toast.add({
      severity: 'success',
      summary: '归档已启动',
      detail: `归档记录ID: ${result.archiveRecordId}`,
      life: 3000,
    })
    // 刷新状态
    setTimeout(() => {
      archivePreviewRef.value?.refresh()
      cloudSpaceRef.value?.refresh()
    }, 1000)
  } catch (e) {
    console.error('执行归档失败:', e)
    toast.add({
      severity: 'error',
      summary: '归档失败',
      detail: e instanceof Error ? e.message : String(e),
      life: 5000,
    })
  }
}

onMounted(async () => {
  await accountsStore.fetchAccounts()
  await crontabsStore.fetchCrontabs()
  
  // 自动选择第一个账号和定时任务
  if (accounts.value.length > 0) {
    selectedAccountId.value = accounts.value[0].id
    const accountCrontabs = crontabs.value.filter(
      (c) => c.accountId === selectedAccountId.value,
    )
    if (accountCrontabs.length > 0) {
      selectedCrontabId.value = accountCrontabs[0].id
    }
  }
})
</script>

<template>
  <div class="p-4 md:p-6 space-y-6">
    <!-- 页面标题 -->
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-2xl font-bold text-slate-800">同步与归档管理</h1>
        <p class="text-sm text-slate-500 mt-1">管理云端同步和智能归档功能</p>
      </div>
    </div>

    <!-- 选择器 -->
    <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
      <div>
        <label class="block text-sm font-medium text-slate-700 mb-2">选择账号</label>
        <Select
          v-model="selectedAccountId"
          :options="accountOptions"
          optionLabel="label"
          optionValue="value"
          placeholder="请选择账号"
          class="w-full"
        />
      </div>
      <div>
        <label class="block text-sm font-medium text-slate-700 mb-2">选择定时任务</label>
        <Select
          v-model="selectedCrontabId"
          :options="crontabOptions"
          optionLabel="label"
          optionValue="value"
          placeholder="请选择定时任务"
          :disabled="!selectedAccountId"
          class="w-full"
        />
      </div>
    </div>

    <!-- 功能卡片 -->
    <div v-if="selectedAccountId && selectedCrontabId" class="grid grid-cols-1 lg:grid-cols-2 gap-6">
      <!-- 左列 -->
      <div class="space-y-6">
        <!-- 云端空间 -->
        <CloudSpaceCard
          ref="cloudSpaceRef"
          :accountId="selectedAccountId"
        />

        <!-- 同步状态 -->
        <SyncStatusCard
          ref="syncStatusRef"
          :crontabId="selectedCrontabId"
          @execute="executeSync"
        />
      </div>

      <!-- 右列 -->
      <div class="space-y-6">
        <!-- 归档预览 -->
        <ArchivePreviewCard
          ref="archivePreviewRef"
          :crontabId="selectedCrontabId"
          @execute="executeArchive"
        />
      </div>
    </div>

    <!-- 未选择提示 -->
    <div
      v-else
      class="text-center py-16 text-slate-400"
    >
      <i class="pi pi-info-circle text-6xl mb-4" />
      <p class="text-lg">请先选择账号和定时任务</p>
    </div>
  </div>
</template>
