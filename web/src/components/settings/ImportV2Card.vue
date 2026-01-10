<script setup lang="ts">
import { ref } from 'vue'
import Button from 'primevue/button'
import Card from 'primevue/card'
import Dialog from 'primevue/dialog'
import { useToast } from 'primevue/usetoast'
import { api } from '@/ApiInstance'

const importingFromV2 = ref(false)
const showImportConfirmVisible = ref(false)
const showImportBlockingVisible = ref(false)
const toast = useToast()

async function requestImportFromV2() {
  // 二次确认第一步：显示确认对话框
  showImportConfirmVisible.value = true
}

async function confirmImportFromV2() {
  // 点击确认后，先关闭确认对话框
  showImportConfirmVisible.value = false

  // 阻塞式调用：展示阻塞对话框与 loading
  showImportBlockingVisible.value = true
  importingFromV2.value = true
  try {
    await api.systemConfigController.importFromV2Db()
    toast.add({ severity: 'success', summary: '成功', detail: '导入完成', life: 2200 })
  } catch (e) {
    const raw = e instanceof Error ? e.message : String(e)
    const isNotEmpty = raw?.includes('Current database is not empty, import aborted')
    const detail = isNotEmpty ? '数据库非空，已中止导入' : raw || '导入失败'
    toast.add({
      severity: isNotEmpty ? 'warn' : 'error',
      summary: isNotEmpty ? '非空库' : '错误',
      detail,
      life: 3200,
    })
  } finally {
    importingFromV2.value = false
    showImportBlockingVisible.value = false
  }
}
</script>

<template>
  <Card
    class="overflow-hidden shadow-sm ring-1 ring-slate-200/60 mt-6"
    pt:footer:class="text-right"
  >
    <template #title>从 V2 导入数据</template>
    <template #content>
      <p class="text-sm text-slate-600">
        该操作会从旧版数据库导入数据，仅在全新空库环境下执行。导入过程可能较长，执行中将阻塞。
      </p>
    </template>
    <template #footer>
      <Button label="导入" severity="warning" @click="requestImportFromV2" />
    </template>
  </Card>

  <!-- 从 V2 导入 二次确认 -->
  <Dialog
    v-model:visible="showImportConfirmVisible"
    modal
    header="从 V2 导入数据"
    class="w-full sm:w-115"
  >
    <div class="text-sm text-slate-700">
      确定要从 V2 导入数据吗？
      <div class="mt-2 text-xs text-red-600">
        注意：仅在空库环境下执行，导入过程可能耗时，执行期间将阻塞。
      </div>
    </div>
    <template #footer>
      <div class="flex items-center justify-end gap-2 w-full">
        <Button
          label="取消"
          severity="secondary"
          text
          @click="
            () => {
              showImportConfirmVisible = false
            }
          "
        />
        <Button label="确定" severity="warning" @click="confirmImportFromV2" />
      </div>
    </template>
  </Dialog>

  <!-- 从 V2 导入 阻塞提示 -->
  <Dialog
    v-model:visible="showImportBlockingVisible"
    modal
    :closable="false"
    :dismissable-mask="false"
    header="正在导入"
    class="w-full sm:w-105"
  >
    <div class="flex items-center gap-3 text-sm text-slate-700">
      <i class="pi pi-spin pi-spinner text-slate-500"></i>
      <span>正在从 V2 导入数据，请勿关闭页面...</span>
    </div>
    <template #footer>
      <div class="flex items-center justify-end gap-2 w-full">
        <Button label="正在导入..." :loading="true" disabled />
      </div>
    </template>
  </Dialog>
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
