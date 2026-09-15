<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import SettingSection from '@/components/settings/SettingSection.vue'
import { useToast } from 'primevue/usetoast'
import { api } from '@/ApiInstance'

const importingFromV2 = ref(false)
const showImportConfirmVisible = ref(false)
const showImportBlockingVisible = ref(false)
const toast = useToast()
const { t } = useI18n()

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
    toast.add({
      severity: 'success',
      summary: t('common.toast.success'),
      detail: t('settings.importV2.completed'),
      life: 2200,
    })
  } catch (e) {
    const raw = e instanceof Error ? e.message : String(e)
    const isNotEmpty = raw?.includes('Current database is not empty, import aborted')
    const detail = isNotEmpty
      ? t('settings.importV2.abortedNonEmpty')
      : raw || t('settings.importV2.failed')
    toast.add({
      severity: isNotEmpty ? 'warn' : 'error',
      summary: isNotEmpty ? t('settings.importV2.nonEmptySummary') : t('common.toast.error'),
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
  <SettingSection
    :title="t('settings.importV2.title')"
    :description="t('settings.importV2.description')"
  >
    <p class="text-sm text-slate-600 dark:text-slate-300">
      {{ t('settings.importV2.body') }}
    </p>
    <template #footer>
      <Button
        :label="t('settings.importV2.importButton')"
        size="small"
        severity="warning"
        @click="requestImportFromV2"
      />
    </template>
  </SettingSection>

  <!-- 从 V2 导入 二次确认 -->
  <Dialog
    v-model:visible="showImportConfirmVisible"
    modal
    :header="t('settings.importV2.title')"
    class="w-full sm:w-115"
  >
    <div class="text-sm text-slate-700 dark:text-slate-200">
      {{ t('settings.importV2.confirmMessage') }}
      <div class="mt-2 text-xs text-red-600 dark:text-red-300">
        {{ t('settings.importV2.confirmNote') }}
      </div>
    </div>
    <template #footer>
      <div class="flex items-center justify-end gap-2 w-full">
        <Button
          :label="t('common.action.cancel')"
          severity="secondary"
          text
          @click="
            () => {
              showImportConfirmVisible = false
            }
          "
        />
        <Button
          :label="t('common.action.confirm')"
          severity="warning"
          @click="confirmImportFromV2"
        />
      </div>
    </template>
  </Dialog>

  <!-- 从 V2 导入 阻塞提示 -->
  <Dialog
    v-model:visible="showImportBlockingVisible"
    modal
    :closable="false"
    :dismissable-mask="false"
    :header="t('settings.importV2.blockingHeader')"
    class="w-full sm:w-105"
  >
    <div class="flex items-center gap-3 text-sm text-slate-700 dark:text-slate-200">
      <i class="pi pi-spin pi-spinner text-slate-500 dark:text-slate-400"></i>
      <span>{{ t('settings.importV2.blockingMessage') }}</span>
    </div>
    <template #footer>
      <div class="flex items-center justify-end gap-2 w-full">
        <Button :label="t('settings.importV2.blockingButton')" :loading="true" disabled />
      </div>
    </template>
  </Dialog>
</template>
