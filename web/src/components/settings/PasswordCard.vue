<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Dialog from 'primevue/dialog'
import SettingSection from '@/components/settings/SettingSection.vue'
import { useToast } from 'primevue/usetoast'
import { api } from '@/ApiInstance'

const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const updatingPassword = ref(false)
const showPasswordConfirmVisible = ref(false)
const isInsecureContext = ref(false)
const toast = useToast()
const { t } = useI18n()

onMounted(() => {
  try {
    isInsecureContext.value = typeof window !== 'undefined' && !window.isSecureContext
  } catch {
    isInsecureContext.value = false
  }
})

async function onUpdatePassword() {
  if (!oldPassword.value || !newPassword.value || !confirmPassword.value) {
    toast.add({
      severity: 'warn',
      summary: t('common.toast.warn'),
      detail: t('settings.password.fieldsRequired'),
      life: 2500,
    })
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    toast.add({
      severity: 'warn',
      summary: t('common.toast.warn'),
      detail: t('settings.password.mismatch'),
      life: 2500,
    })
    return
  }
  try {
    updatingPassword.value = true
    await api.systemConfigController.updatePassword({
      body: { oldPassword: oldPassword.value, password: newPassword.value },
    })
    toast.add({
      severity: 'success',
      summary: t('common.toast.success'),
      detail: t('settings.password.updated'),
      life: 2000,
    })
    oldPassword.value = ''
    newPassword.value = ''
    confirmPassword.value = ''
  } catch (e) {
    const detail = e instanceof Error ? e.message : String(e) || t('settings.password.updateFailed')
    toast.add({ severity: 'error', summary: t('common.toast.error'), detail, life: 3000 })
  } finally {
    updatingPassword.value = false
  }
}

function requestUpdatePassword() {
  showPasswordConfirmVisible.value = true
}

async function confirmUpdatePassword() {
  showPasswordConfirmVisible.value = false
  await onUpdatePassword()
}
</script>

<template>
  <SettingSection
    :title="t('nav.sections.password')"
    :description="t('settings.password.description')"
  >
    <InputText
      v-model="oldPassword"
      :type="'password'"
      :placeholder="t('settings.password.currentPlaceholder')"
      class="w-full"
    />
    <InputText
      v-model="newPassword"
      :type="'password'"
      :placeholder="t('settings.password.newPlaceholder')"
      class="w-full mt-2"
    />
    <InputText
      v-model="confirmPassword"
      :type="'password'"
      :placeholder="t('settings.password.confirmPlaceholder')"
      class="w-full mt-2"
    />
    <div
      v-if="isInsecureContext"
      class="mt-3 rounded-md bg-red-50 dark:bg-red-950/40 text-red-700 dark:text-red-300 text-xs px-3 py-2 ring-1 ring-red-200 dark:ring-red-900/70"
    >
      {{ t('settings.password.insecureWarning') }}
    </div>

    <template #footer>
      <Button
        :label="t('settings.password.updateButton')"
        size="small"
        :loading="updatingPassword"
        @click="requestUpdatePassword"
      />
    </template>
  </SettingSection>

  <!-- 更新密码 确认 -->
  <Dialog
    v-model:visible="showPasswordConfirmVisible"
    modal
    :header="t('settings.password.dialogHeader')"
    class="w-full sm:w-105"
  >
    <div class="text-sm text-slate-700 dark:text-slate-200">
      {{ t('settings.password.confirmMessage') }}
      <span v-if="isInsecureContext" class="text-red-600 dark:text-red-300 font-medium">
        {{ t('settings.password.insecureConfirmNote') }}
      </span>
    </div>
    <template #footer>
      <div class="flex items-center justify-end gap-2 w-full">
        <Button
          :label="t('common.action.cancel')"
          severity="secondary"
          text
          @click="
            () => {
              showPasswordConfirmVisible = false
            }
          "
        />
        <Button
          :label="t('common.action.confirm')"
          severity="warning"
          :loading="updatingPassword"
          @click="confirmUpdatePassword"
        />
      </div>
    </template>
  </Dialog>
</template>
