<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Skeleton from 'primevue/skeleton'
import SettingSection from '@/components/settings/SettingSection.vue'
import { useToast } from 'primevue/usetoast'
import { api } from '@/ApiInstance'

const exifToolPath = ref('')
const loadingConfig = ref(false)
const savingConfig = ref(false)
const initialized = ref(false)
const toast = useToast()
const { t } = useI18n()

async function fetchSystemConfig() {
  loadingConfig.value = true
  try {
    const cfg = await api.systemConfigController.getSystemConfig()
    exifToolPath.value = cfg?.exifToolPath ?? ''
  } catch (e) {
    console.error('获取系统配置失败', e)
    toast.add({
      severity: 'error',
      summary: t('common.toast.fetchFailed'),
      detail: t('settings.system.fetchFailedDetail'),
      life: 2200,
    })
  } finally {
    loadingConfig.value = false
    initialized.value = true
  }
}

async function onUpdateSystemConfig() {
  if (!exifToolPath.value || exifToolPath.value.trim() === '') {
    toast.add({
      severity: 'warn',
      summary: t('common.toast.warn'),
      detail: t('settings.system.pathRequired'),
      life: 2500,
    })
    return
  }
  try {
    savingConfig.value = true
    await api.systemConfigController.updateSystemConfig({
      body: { exifToolPath: exifToolPath.value },
    })

    toast.add({
      severity: 'success',
      summary: t('common.toast.success'),
      detail: t('settings.system.saved'),
      life: 2000,
    })
  } catch (e) {
    const detail = e instanceof Error ? e.message : String(e) || t('common.toast.saveFailed')
    toast.add({ severity: 'error', summary: t('common.toast.error'), detail, life: 3000 })
  } finally {
    savingConfig.value = false
  }
}

onMounted(() => {
  fetchSystemConfig()
})
</script>

<template>
  <SettingSection :title="t('nav.sections.system')" :description="t('settings.system.description')">
    <template #actions>
      <Button
        icon="pi pi-refresh"
        severity="secondary"
        rounded
        text
        size="small"
        @click="fetchSystemConfig"
      />
    </template>

    <div class="space-y-2">
      <span class="text-sm text-slate-600 dark:text-slate-300 font-medium">{{
        t('settings.system.exifToolPathLabel')
      }}</span>
      <Skeleton v-if="!initialized" height="2.75rem" class="w-full" />
      <InputText
        v-else
        v-model="exifToolPath"
        :disabled="loadingConfig"
        :placeholder="t('settings.system.exifToolPathPlaceholder')"
        class="w-full"
      />
      <p class="text-xs text-slate-400 dark:text-slate-500">
        {{ t('settings.system.exifToolPathHint') }}
      </p>
    </div>

    <template #footer>
      <Button
        :label="t('common.action.save')"
        size="small"
        :loading="savingConfig"
        @click="onUpdateSystemConfig"
      />
    </template>
  </SettingSection>
</template>
