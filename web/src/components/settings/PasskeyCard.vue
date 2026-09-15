<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Dialog from 'primevue/dialog'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import SettingSection from '@/components/settings/SettingSection.vue'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import { api } from '@/ApiInstance'
import { isWebAuthnSupported, registerPasskey } from '@/utils/passkey'
import type { PasskeyCredentialInfo } from '@/utils/passkey'

const { t, locale } = useI18n()
const toast = useToast()
const confirm = useConfirm()

const credentials = ref<PasskeyCredentialInfo[]>([])
const loading = ref(false)
const webAuthnSupported = ref(false)
const isInsecureContext = ref(false)

const showRegisterDialog = ref(false)
const registerPassword = ref('')
const registerName = ref('')
const registering = ref(false)

const showRenameDialog = ref(false)
const renameCredentialId = ref('')
const renameName = ref('')
const renaming = ref(false)

onMounted(async () => {
  webAuthnSupported.value = isWebAuthnSupported()
  try {
    isInsecureContext.value = typeof window !== 'undefined' && !window.isSecureContext
  } catch {
    isInsecureContext.value = false
  }
  await loadCredentials()
})

async function loadCredentials() {
  loading.value = true
  try {
    credentials.value = (await api.passkeyController.listCredentials()) as PasskeyCredentialInfo[]
  } catch (e) {
    const detail = e instanceof Error ? e.message : String(e)
    toast.add({ severity: 'error', summary: t('common.toast.fetchFailed'), detail, life: 3000 })
  } finally {
    loading.value = false
  }
}

function formatDate(timestamp: number | null | undefined): string {
  if (!timestamp) return '-'
  return new Date(timestamp).toLocaleString(locale.value, {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function openRegisterDialog() {
  registerPassword.value = ''
  registerName.value = ''
  showRegisterDialog.value = true
}

async function doRegister() {
  if (!registerPassword.value) {
    toast.add({
      severity: 'warn',
      summary: t('common.toast.warn'),
      detail: t('tokens.passkey.enterPassword'),
      life: 2500,
    })
    return
  }
  if (!registerName.value.trim()) {
    toast.add({
      severity: 'warn',
      summary: t('common.toast.warn'),
      detail: t('tokens.passkey.enterName'),
      life: 2500,
    })
    return
  }

  registering.value = true
  try {
    await registerPasskey(registerPassword.value, registerName.value.trim())
    toast.add({
      severity: 'success',
      summary: t('common.toast.success'),
      detail: t('tokens.passkey.registerSuccess'),
      life: 2000,
    })
    showRegisterDialog.value = false
    await loadCredentials()
  } catch (e) {
    console.error('Register passkey error:', e)
    const msg = e instanceof Error ? e.message : String(e)
    if (msg.includes('Auth failed')) {
      toast.add({
        severity: 'error',
        summary: t('common.toast.error'),
        detail: t('tokens.passkey.wrongPassword'),
        life: 3000,
      })
    } else if (msg.includes('The operation either timed out or was not allowed')) {
      toast.add({
        severity: 'warn',
        summary: t('tokens.passkey.cancelled'),
        detail: t('tokens.passkey.registerCancelled'),
        life: 3000,
      })
    } else {
      toast.add({
        severity: 'error',
        summary: t('tokens.passkey.registerFailed'),
        detail: msg,
        life: 3000,
      })
    }
  } finally {
    registering.value = false
  }
}

function openRenameDialog(cred: PasskeyCredentialInfo) {
  renameCredentialId.value = cred.id
  renameName.value = cred.name
  showRenameDialog.value = true
}

async function doRename() {
  if (!renameName.value.trim()) {
    toast.add({
      severity: 'warn',
      summary: t('common.toast.warn'),
      detail: t('tokens.passkey.nameRequired'),
      life: 2500,
    })
    return
  }

  renaming.value = true
  try {
    await api.passkeyController.updateCredentialName({
      credentialId: renameCredentialId.value,
      body: { name: renameName.value.trim() },
    })
    toast.add({
      severity: 'success',
      summary: t('common.toast.success'),
      detail: t('tokens.passkey.nameUpdated'),
      life: 2000,
    })
    showRenameDialog.value = false
    await loadCredentials()
  } catch (e) {
    const detail = e instanceof Error ? e.message : String(e)
    toast.add({
      severity: 'error',
      summary: t('tokens.passkey.updateFailed'),
      detail,
      life: 3000,
    })
  } finally {
    renaming.value = false
  }
}

function confirmDelete(cred: PasskeyCredentialInfo) {
  confirm.require({
    message: t('tokens.passkey.confirmDelete', { name: cred.name }),
    header: t('tokens.passkey.confirmDeleteTitle'),
    icon: 'pi pi-exclamation-triangle',
    rejectLabel: t('common.action.cancel'),
    acceptLabel: t('common.action.delete'),
    rejectProps: { severity: 'secondary', text: true },
    acceptProps: { severity: 'danger' },
    accept: () => doDelete(cred.id),
  })
}

async function doDelete(credentialId: string) {
  try {
    await api.passkeyController.deleteCredential({ credentialId })
    toast.add({
      severity: 'success',
      summary: t('common.toast.success'),
      detail: t('tokens.passkey.deleted'),
      life: 2000,
    })
    await loadCredentials()
  } catch (e) {
    const detail = e instanceof Error ? e.message : String(e)
    toast.add({
      severity: 'error',
      summary: t('tokens.passkey.deleteFailed'),
      detail,
      life: 3000,
    })
  }
}
</script>

<template>
  <SettingSection :title="t('tokens.passkey.title')" :description="t('tokens.passkey.description')">
    <!-- WebAuthn 不支持警告 -->
    <div
      v-if="!webAuthnSupported"
      class="mb-4 rounded-md bg-amber-50 dark:bg-amber-950/35 text-amber-700 dark:text-amber-300 text-sm px-3 py-2 ring-1 ring-amber-200 dark:ring-amber-900/60"
    >
      <i class="pi pi-exclamation-triangle mr-2"></i>
      {{ t('tokens.passkey.unsupported') }}
    </div>

    <!-- 不安全上下文警告 -->
    <div
      v-if="isInsecureContext"
      class="mb-4 rounded-md bg-red-50 dark:bg-red-950/40 text-red-700 dark:text-red-300 text-xs px-3 py-2 ring-1 ring-red-200 dark:ring-red-900/70"
    >
      <i class="pi pi-shield mr-2"></i>
      {{ t('tokens.passkey.insecureContext') }}
    </div>

    <!-- Passkey 列表 -->
    <DataTable
      :value="credentials"
      :loading="loading"
      class="text-sm"
      stripedRows
      :pt="{
        table: { class: 'min-w-full' },
        column: { bodyCell: { class: 'py-2' } },
      }"
    >
      <template #empty>
        <div class="text-center text-slate-500 dark:text-slate-400 py-4">
          {{ t('tokens.passkey.empty') }}
        </div>
      </template>

      <Column field="name" :header="t('common.field.name')" class="font-medium" />
      <Column :header="t('tokens.table.createdAt')">
        <template #body="{ data }">
          {{ formatDate(data.createdAt) }}
        </template>
      </Column>
      <Column :header="t('tokens.passkey.colLastUsed')">
        <template #body="{ data }">
          {{ formatDate(data.lastUsedAt) }}
        </template>
      </Column>
      <Column :header="t('tokens.table.actions')" style="width: 120px">
        <template #body="{ data }">
          <div class="flex gap-1">
            <Button
              icon="pi pi-pencil"
              severity="secondary"
              text
              rounded
              size="small"
              v-tooltip.top="t('tokens.passkey.rename')"
              @click="openRenameDialog(data)"
            />
            <Button
              icon="pi pi-trash"
              severity="danger"
              text
              rounded
              size="small"
              v-tooltip.top="t('common.action.delete')"
              @click="confirmDelete(data)"
            />
          </div>
        </template>
      </Column>
    </DataTable>

    <template #footer>
      <Button
        :label="t('tokens.passkey.registerTitle')"
        icon="pi pi-plus"
        size="small"
        :disabled="!webAuthnSupported"
        @click="openRegisterDialog"
      />
    </template>
  </SettingSection>

  <!-- 注册对话框 -->
  <Dialog
    v-model:visible="showRegisterDialog"
    modal
    :header="t('tokens.passkey.registerTitle')"
    class="w-full sm:w-105"
  >
    <div class="space-y-4">
      <p class="text-sm text-slate-600 dark:text-slate-300">
        {{ t('tokens.passkey.registerDesc') }}
      </p>

      <div>
        <label class="text-sm font-medium text-slate-700 dark:text-slate-200 block mb-1">{{
          t('tokens.passkey.currentPassword')
        }}</label>
        <InputText
          v-model="registerPassword"
          type="password"
          :placeholder="t('tokens.passkey.passwordPlaceholder')"
          class="w-full"
          @keyup.enter="doRegister"
        />
      </div>

      <div>
        <label class="text-sm font-medium text-slate-700 dark:text-slate-200 block mb-1">{{
          t('tokens.passkey.nameLabel')
        }}</label>
        <InputText
          v-model="registerName"
          :placeholder="t('tokens.passkey.namePlaceholder')"
          class="w-full"
          @keyup.enter="doRegister"
        />
        <p class="text-xs text-slate-500 dark:text-slate-400 mt-1">
          {{ t('tokens.passkey.nameHint') }}
        </p>
      </div>

      <div
        v-if="isInsecureContext"
        class="rounded-md bg-red-50 dark:bg-red-950/40 text-red-700 dark:text-red-300 text-xs px-3 py-2 ring-1 ring-red-200 dark:ring-red-900/70"
      >
        {{ t('tokens.passkey.insecurePassword') }}
      </div>
    </div>

    <template #footer>
      <div class="flex items-center justify-end gap-2 w-full">
        <Button
          :label="t('common.action.cancel')"
          severity="secondary"
          text
          @click="showRegisterDialog = false"
        />
        <Button
          :label="t('tokens.passkey.registerAction')"
          :loading="registering"
          @click="doRegister"
        />
      </div>
    </template>
  </Dialog>

  <!-- 重命名对话框 -->
  <Dialog
    v-model:visible="showRenameDialog"
    modal
    :header="t('tokens.passkey.renameTitle')"
    class="w-full sm:w-96"
  >
    <div>
      <label class="text-sm font-medium text-slate-700 dark:text-slate-200 block mb-1">{{
        t('tokens.passkey.newName')
      }}</label>
      <InputText
        v-model="renameName"
        :placeholder="t('tokens.passkey.newNamePlaceholder')"
        class="w-full"
        @keyup.enter="doRename"
      />
    </div>

    <template #footer>
      <div class="flex items-center justify-end gap-2 w-full">
        <Button
          :label="t('common.action.cancel')"
          severity="secondary"
          text
          @click="showRenameDialog = false"
        />
        <Button :label="t('common.action.save')" :loading="renaming" @click="doRename" />
      </div>
    </template>
  </Dialog>
</template>
