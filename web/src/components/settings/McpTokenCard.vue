<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import Button from 'primevue/button'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import SelectButton from 'primevue/selectbutton'
import Tag from 'primevue/tag'
import SettingSection from '@/components/settings/SettingSection.vue'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'
import { api } from '@/ApiInstance'
import type { McpTokenPermission } from '@/__generated/model/enums'
import type { McpTokenInfo } from '@/__generated/model/static'

const { t, locale } = useI18n()
const toast = useToast()
const confirm = useConfirm()

const tokens = ref<ReadonlyArray<McpTokenInfo>>([])
const loading = ref(false)
const creating = ref(false)
const showCreateDialog = ref(false)
const showTokenDialog = ref(false)
const tokenName = ref('')
const tokenPermission = ref<McpTokenPermission>('READ_ONLY')
const createdToken = ref('')

const permissionOptions = computed<Array<{ label: string; value: McpTokenPermission }>>(() => [
  { label: t('tokens.mcp.permReadOnly'), value: 'READ_ONLY' },
  { label: t('tokens.mcp.permAllowTrigger'), value: 'ALLOW_TRIGGER' },
])

onMounted(loadTokens)

async function loadTokens() {
  loading.value = true
  try {
    tokens.value = await api.mcpTokenController.list()
  } catch (e) {
    toast.add({
      severity: 'error',
      summary: t('common.toast.fetchFailed'),
      detail: errorMessage(e),
      life: 3000,
    })
  } finally {
    loading.value = false
  }
}

function openCreateDialog() {
  tokenName.value = ''
  tokenPermission.value = 'READ_ONLY'
  showCreateDialog.value = true
}

async function createToken() {
  const name = tokenName.value.trim()
  if (!name) {
    toast.add({
      severity: 'warn',
      summary: t('common.toast.warn'),
      detail: t('tokens.mcp.enterName'),
      life: 2500,
    })
    return
  }

  creating.value = true
  try {
    const created = await api.mcpTokenController.create({
      body: { name, permission: tokenPermission.value },
    })
    createdToken.value = created.token
    showCreateDialog.value = false
    showTokenDialog.value = true
    await loadTokens()
  } catch (e) {
    toast.add({
      severity: 'error',
      summary: t('tokens.mcp.createFailed'),
      detail: errorMessage(e),
      life: 3000,
    })
  } finally {
    creating.value = false
  }
}

async function copyCreatedToken() {
  try {
    await navigator.clipboard.writeText(createdToken.value)
    toast.add({
      severity: 'success',
      summary: t('common.toast.copied'),
      detail: t('tokens.mcp.copiedDetail'),
      life: 1800,
    })
  } catch {
    toast.add({
      severity: 'warn',
      summary: t('tokens.mcp.copyFailed'),
      detail: t('tokens.mcp.copyManual'),
      life: 3000,
    })
  }
}

function clearCreatedToken() {
  createdToken.value = ''
}

function confirmRevoke(token: McpTokenInfo) {
  confirm.require({
    message: t('tokens.mcp.confirmRevoke', { name: token.name }),
    header: t('tokens.mcp.confirmRevokeTitle'),
    icon: 'pi pi-exclamation-triangle',
    rejectLabel: t('common.action.cancel'),
    acceptLabel: t('tokens.mcp.revoke'),
    rejectProps: { severity: 'secondary', text: true },
    acceptProps: { severity: 'danger' },
    accept: () => revokeToken(token.id),
  })
}

async function revokeToken(id: number) {
  try {
    await api.mcpTokenController.revoke({ id })
    toast.add({
      severity: 'success',
      summary: t('tokens.mcp.revoked'),
      detail: t('tokens.mcp.revokedDetail'),
      life: 2000,
    })
    await loadTokens()
  } catch (e) {
    toast.add({
      severity: 'error',
      summary: t('tokens.mcp.revokeFailed'),
      detail: errorMessage(e),
      life: 3000,
    })
  }
}

function permissionLabel(permission: McpTokenPermission): string {
  return permission === 'ALLOW_TRIGGER'
    ? t('tokens.mcp.permAllowTrigger')
    : t('tokens.mcp.permReadOnly')
}

function formatDate(timestamp: number): string {
  return new Date(timestamp).toLocaleString(locale.value, {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error) || t('tokens.mcp.requestFailed')
}
</script>

<template>
  <SettingSection :title="t('tokens.mcp.title')" :description="t('tokens.mcp.description')">
    <template #actions>
      <Button
        icon="pi pi-refresh"
        severity="secondary"
        rounded
        text
        size="small"
        @click="loadTokens"
      />
    </template>

    <i18n-t
      keypath="tokens.mcp.intro"
      tag="p"
      class="text-sm text-slate-600 dark:text-slate-300 mb-4"
    >
      <code>Authorization: Bearer &lt;token&gt;</code>
      <code>/mcp</code>
    </i18n-t>

    <DataTable :value="tokens" :loading="loading" class="text-sm" stripedRows>
      <template #empty>
        <div class="text-center text-slate-500 dark:text-slate-400 py-4">
          {{ t('tokens.mcp.empty') }}
        </div>
      </template>

      <Column field="name" :header="t('common.field.name')" class="font-medium" />
      <Column :header="t('tokens.mcp.permission')">
        <template #body="{ data }">
          <Tag
            :value="permissionLabel(data.permission)"
            :severity="data.permission === 'ALLOW_TRIGGER' ? 'warn' : 'secondary'"
          />
        </template>
      </Column>
      <Column :header="t('tokens.table.createdAt')">
        <template #body="{ data }">{{ formatDate(data.createdAt) }}</template>
      </Column>
      <Column :header="t('tokens.table.actions')" style="width: 72px">
        <template #body="{ data }">
          <Button
            icon="pi pi-trash"
            severity="danger"
            text
            rounded
            size="small"
            v-tooltip.top="t('tokens.mcp.revoke')"
            @click="confirmRevoke(data)"
          />
        </template>
      </Column>
    </DataTable>

    <template #footer>
      <Button
        :label="t('tokens.mcp.create')"
        icon="pi pi-plus"
        size="small"
        @click="openCreateDialog"
      />
    </template>
  </SettingSection>

  <Dialog
    v-model:visible="showCreateDialog"
    modal
    :header="t('tokens.mcp.create')"
    class="w-full sm:w-120"
  >
    <div class="space-y-4">
      <div>
        <label class="text-sm font-medium text-slate-700 dark:text-slate-200 block mb-1">{{
          t('tokens.mcp.nameLabel')
        }}</label>
        <InputText
          v-model="tokenName"
          maxlength="100"
          :placeholder="t('tokens.mcp.namePlaceholder')"
          class="w-full"
          @keyup.enter="createToken"
        />
      </div>

      <div>
        <label class="text-sm font-medium text-slate-700 dark:text-slate-200 block mb-2">{{
          t('tokens.mcp.permission')
        }}</label>
        <SelectButton
          v-model="tokenPermission"
          :options="permissionOptions"
          option-label="label"
          option-value="value"
          :allow-empty="false"
          class="w-full"
        />
        <p class="text-xs text-slate-500 dark:text-slate-400 mt-2">
          {{ t('tokens.mcp.permissionHint') }}
        </p>
      </div>
    </div>

    <template #footer>
      <div class="flex items-center justify-end gap-2 w-full">
        <Button
          :label="t('common.action.cancel')"
          severity="secondary"
          text
          @click="showCreateDialog = false"
        />
        <Button :label="t('common.action.create')" :loading="creating" @click="createToken" />
      </div>
    </template>
  </Dialog>

  <Dialog
    v-model:visible="showTokenDialog"
    modal
    :header="t('tokens.mcp.saveTokenTitle')"
    class="w-full sm:w-150"
    @hide="clearCreatedToken"
  >
    <div class="space-y-4">
      <div
        class="rounded-md bg-amber-50 dark:bg-amber-950/35 text-amber-800 dark:text-amber-200 text-sm px-3 py-2 ring-1 ring-amber-200 dark:ring-amber-900/60"
      >
        {{ t('tokens.mcp.saveTokenWarning') }}
      </div>
      <div class="flex gap-2">
        <InputText :model-value="createdToken" readonly class="w-full font-mono text-sm" />
        <Button :label="t('common.action.copy')" icon="pi pi-copy" @click="copyCreatedToken" />
      </div>
    </div>

    <template #footer>
      <Button :label="t('tokens.mcp.saved')" @click="showTokenDialog = false" />
    </template>
  </Dialog>
</template>

<style scoped>
:deep(.p-selectbutton .p-togglebutton) {
  flex: 1;
}
</style>
