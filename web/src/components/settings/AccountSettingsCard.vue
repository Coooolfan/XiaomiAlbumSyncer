<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import Dialog from 'primevue/dialog'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import ProviderTag from '@/components/ProviderTag.vue'
import ICloudLoginForm from '@/components/settings/ICloudLoginForm.vue'
import XiaomiAccountForm from '@/components/settings/XiaomiAccountForm.vue'
import XiaomiE2eeForm from '@/components/settings/XiaomiE2eeForm.vue'
import SettingSection from '@/components/settings/SettingSection.vue'
import OptionCard from '@/components/OptionCard.vue'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import type { ProviderAccountDto } from '@/__generated/model/dto'
import type { CloudProvider } from '@/__generated/model/enums'
import { storeToRefs } from 'pinia'
import { api } from '@/ApiInstance'
import { useAccountsStore } from '@/stores/accounts'
import { useAlbumsStore } from '@/stores/albums'
import { useCrontabsStore } from '@/stores/crontabs'

type Account = ProviderAccountDto['ProviderAccountController/DEFAULT_PROVIDER_ACCOUNT']
const accountsStore = useAccountsStore()
const albumsStore = useAlbumsStore()
const crontabsStore = useCrontabsStore()
const { accounts, loading } = storeToRefs(accountsStore)
const { t } = useI18n()
const toast = useToast()
const confirm = useConfirm()
const icloudStates = ref<Record<number, string>>({})
const e2eeAccount = ref<Account | null>(null)
const e2eeStates = ref<Record<number, string>>({})
const showDialog = ref(false)
const selectedProvider = ref<CloudProvider | null>(null)
const editingAccount = ref<Account>()
const xiaomiTitle = ref('')
const isInsecureContext = ref(false)
const dialogHeader = computed(() =>
  selectedProvider.value === 'XIAOMI'
    ? xiaomiTitle.value
    : selectedProvider.value === 'ICLOUD'
      ? t('tokens.account.providerIcloud')
      : t('tokens.account.add'),
)

watch(
  accounts,
  async (list) => {
    const results = await Promise.allSettled(
      list
        .filter((a) => a.provider === 'ICLOUD')
        .map(async (a) => {
          const status = await api.icloudAccountController.status({ id: a.id })
          return [a.id, status.state] as const
        }),
    )
    icloudStates.value = Object.fromEntries(
      results.flatMap((r) => (r.status === 'fulfilled' ? [r.value] : [])),
    )
  },
  { immediate: true },
)

onMounted(() => {
  isInsecureContext.value = !window.isSecureContext
  accountsStore.fetchAccounts()
})

function openAddDialog() {
  editingAccount.value = undefined
  selectedProvider.value = null
  showDialog.value = true
}

function openEditDialog(account: Account) {
  editingAccount.value = account
  selectedProvider.value = account.provider
  showDialog.value = true
}

async function onXiaomiCompleted() {
  showDialog.value = false
  try {
    await Promise.all([
      accountsStore.refreshAccounts(),
      albumsStore.refreshAlbums(),
      crontabsStore.refreshCrontabs(),
    ])
  } catch (e) {
    toast.add({
      severity: 'error',
      summary: t('common.toast.error'),
      detail: e instanceof Error ? e.message : String(e),
      life: 5000,
    })
  }
}

async function onICloudCompleted(accountId: number) {
  showDialog.value = false
  toast.add({
    severity: 'success',
    summary: t('common.toast.success'),
    detail: t('tokens.icloud.connected'),
    life: 3000,
  })
  try {
    await Promise.all([
      albumsStore.refreshAlbumsForAccount(accountId),
      crontabsStore.refreshCrontabs(),
    ])
  } catch (e) {
    toast.add({
      severity: 'error',
      summary: t('common.toast.error'),
      detail: e instanceof Error ? e.message : String(e),
      life: 5000,
    })
  }
}

function confirmDelete(account: Account) {
  confirm.require({
    message: t('tokens.account.confirmDelete', { name: account.nickname || account.userId }),
    header: t('tokens.account.confirmDeleteTitle'),
    acceptClass: 'p-button-danger',
    rejectLabel: t('common.action.cancel'),
    acceptLabel: t('tokens.account.confirmDeleteAccept'),
    accept: async () => {
      try {
        await accountsStore.deleteAccount(account.id)
        toast.add({
          severity: 'success',
          summary: t('common.toast.success'),
          detail: t('tokens.account.deleted'),
          life: 2000,
        })
        await Promise.all([albumsStore.refreshAlbums(), crontabsStore.refreshCrontabs()])
      } catch (e) {
        const detail =
          e instanceof Error ? e.message : String(e) || t('tokens.account.deleteFailed')
        toast.add({ severity: 'error', summary: t('common.toast.error'), detail, life: 3000 })
      }
    },
  })
}
</script>

<template>
  <SettingSection :title="t('tokens.account.title')" :description="t('tokens.account.description')">
    <template #actions>
      <Button
        :label="t('tokens.account.add')"
        icon="pi pi-plus"
        size="small"
        severity="primary"
        @click="openAddDialog"
      />
    </template>

    <div
      v-if="isInsecureContext"
      class="mb-4 rounded-md bg-red-50 dark:bg-red-950/40 text-red-700 dark:text-red-300 text-[13px] px-3 py-2 ring-1 ring-red-200 dark:ring-red-900/70"
    >
      {{ t('tokens.account.insecureWarning') }}
    </div>

    <DataTable :value="accounts" :loading="loading" size="small" class="text-sm">
      <template #empty>{{ t('tokens.account.empty') }}</template>
      <Column field="nickname" :header="t('tokens.account.nickname')"></Column>
      <Column field="userId" :header="t('tokens.account.userId')"></Column>
      <Column :header="t('tokens.account.provider')">
        <template #body="{ data }">
          <ProviderTag :provider="data.provider" full-name />
        </template>
      </Column>
      <Column :header="t('tokens.icloud.status')">
        <template #body="{ data }">
          <Tag
            v-if="data.provider === 'ICLOUD' && icloudStates[data.id]"
            :value="t(`tokens.icloud.states.${icloudStates[data.id]}`)"
            :severity="icloudStates[data.id] === 'READY' ? 'success' : 'warn'"
          />
          <Tag
            v-if="data.provider === 'XIAOMI' && e2eeStates[data.id]"
            :value="t(`tokens.e2ee.states.${e2eeStates[data.id]}`)"
            :severity="e2eeStates[data.id] === 'READY' ? 'success' : 'info'"
          />
        </template>
      </Column>
      <Column :header="t('tokens.table.actions')" :style="{ width: '10rem' }">
        <template #body="slotProps">
          <div class="flex gap-2">
            <Button
              v-if="slotProps.data.provider === 'XIAOMI'"
              icon="pi pi-shield"
              text
              rounded
              severity="secondary"
              :aria-label="t('tokens.e2ee.title')"
              :title="t('tokens.e2ee.title')"
              @click="e2eeAccount = slotProps.data"
            />
            <Button
              icon="pi pi-pencil"
              text
              rounded
              severity="secondary"
              @click="openEditDialog(slotProps.data)"
            />
            <Button
              icon="pi pi-trash"
              text
              rounded
              severity="danger"
              @click="confirmDelete(slotProps.data)"
            />
          </div>
        </template>
      </Column>
    </DataTable>
  </SettingSection>

  <Dialog
    :visible="!!e2eeAccount"
    modal
    :header="t('tokens.e2ee.title')"
    class="w-full sm:w-[480px]"
    @update:visible="
      (visible) => {
        if (!visible) e2eeAccount = null
      }
    "
  >
    <XiaomiE2eeForm
      v-if="e2eeAccount"
      :key="e2eeAccount.id"
      :account-id="e2eeAccount.id"
      @updated="
        (status) => {
          e2eeStates[status.accountId] = status.state
        }
      "
    />
  </Dialog>

  <Dialog v-model:visible="showDialog" modal :header="dialogHeader" class="w-full sm:w-[480px]">
    <template v-if="showDialog">
      <div v-if="!selectedProvider" class="flex flex-col gap-3 pt-2">
        <OptionCard
          icon="pi-cloud"
          :label="t('tokens.account.providerXiaomi')"
          :desc="t('tokens.account.providerXiaomiDesc')"
          @click="selectedProvider = 'XIAOMI'"
        />
        <OptionCard
          icon="pi-apple"
          :label="t('tokens.account.providerIcloud')"
          :desc="t('tokens.account.providerIcloudDesc')"
          @click="selectedProvider = 'ICLOUD'"
        />
      </div>
      <XiaomiAccountForm
        v-else-if="selectedProvider === 'XIAOMI'"
        :account="editingAccount"
        :is-insecure-context="isInsecureContext"
        @title="xiaomiTitle = $event"
        @completed="onXiaomiCompleted"
        @cancel="showDialog = false"
        @back="selectedProvider = null"
      />
      <ICloudLoginForm
        v-else-if="selectedProvider === 'ICLOUD'"
        :account-id="editingAccount?.id"
        :apple-id="editingAccount?.userId"
        :nickname="editingAccount?.nickname"
        @completed="onICloudCompleted"
      />
      <div v-if="selectedProvider !== 'XIAOMI'" class="flex items-center w-full mt-4">
        <Button
          v-if="selectedProvider && !editingAccount"
          :label="t('common.action.back')"
          icon="pi pi-arrow-left"
          severity="secondary"
          text
          @click="selectedProvider = null"
        />
        <div class="flex-1" />
        <Button
          :label="t('common.action.cancel')"
          severity="secondary"
          text
          @click="showDialog = false"
        />
      </div>
    </template>
  </Dialog>
</template>
