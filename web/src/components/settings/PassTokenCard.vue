<script setup lang="ts">
import { ref, onMounted } from 'vue'
import Button from 'primevue/button'
import Card from 'primevue/card'
import Textarea from 'primevue/textarea'
import InputText from 'primevue/inputtext'
import Dialog from 'primevue/dialog'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import type { XiaomiAccountDto } from '@/__generated/model/dto'
import { storeToRefs } from 'pinia'
import { useAccountsStore } from '@/stores/accounts'
import { useAlbumsStore } from '@/stores/albums'
import { useCrontabsStore } from '@/stores/crontabs'

// 类型定义
type Account = XiaomiAccountDto['XiaomiAccountController/DEFAULT_XIAOMI_ACCOUNT']

const accountsStore = useAccountsStore()
const albumsStore = useAlbumsStore()
const crontabsStore = useCrontabsStore()

const { accounts, loading } = storeToRefs(accountsStore)
const saving = ref(false)
const showDialog = ref(false)
const isEditMode = ref(false)
const isInsecureContext = ref(false)

// 表单数据
const form = ref({
  id: 0,
  userId: '',
  nickname: '',
  passToken: '',
})

const toast = useToast()
const confirm = useConfirm()

onMounted(() => {
  try {
    isInsecureContext.value = typeof window !== 'undefined' && !window.isSecureContext
  } catch {
    isInsecureContext.value = false
  }
  accountsStore.fetchAccounts()
})

function openCreateDialog() {
  isEditMode.value = false
  form.value = { id: 0, userId: '', nickname: '', passToken: '' }
  showDialog.value = true
}

function openEditDialog(account: Account) {
  isEditMode.value = true
  // 根据 update 接口定义，passToken 是必填的
  form.value = {
    id: account.id,
    userId: account.userId,
    nickname: account.nickname,
    passToken: '',
  }
  showDialog.value = true
}

async function onSave() {
  if (!form.value.userId) {
    toast.add({ severity: 'warn', summary: '提示', detail: '请输入 UserId', life: 2500 })
    return
  }
  if (!form.value.passToken) {
    toast.add({ severity: 'warn', summary: '提示', detail: '请输入 PassToken', life: 2500 })
    return
  }

  const finalNickname = form.value.nickname || form.value.userId

  try {
    saving.value = true
    if (isEditMode.value) {
      await accountsStore.updateAccount(form.value.id, {
        userId: form.value.userId,
        nickname: finalNickname,
        passToken: form.value.passToken,
      })
      toast.add({ severity: 'success', summary: '成功', detail: '账号已更新', life: 2000 })
    } else {
      await accountsStore.createAccount({
        userId: form.value.userId,
        nickname: finalNickname,
        passToken: form.value.passToken,
      })
      toast.add({ severity: 'success', summary: '成功', detail: '账号已添加', life: 2000 })
    }
    showDialog.value = false
    await Promise.all([albumsStore.refreshAlbums(), crontabsStore.refreshCrontabs()])
  } catch (e) {
    const detail = e instanceof Error ? e.message : String(e) || '保存失败'
    toast.add({ severity: 'error', summary: '错误', detail, life: 3000 })
  } finally {
    saving.value = false
  }
}

function confirmDelete(account: Account) {
  const confirmMessage =
    '确定要删除账号 ' +
    (account.nickname || account.userId) +
    ' 吗？所有与此账号相关的数据会被一并删除'

  confirm.require({
    message: confirmMessage,
    header: '删除确认',
    acceptClass: 'p-button-danger',
    rejectLabel: '取消',
    acceptLabel: '确认删除',
    accept: async () => {
      try {
        await accountsStore.deleteAccount(account.id)
        toast.add({ severity: 'success', summary: '成功', detail: '账号已删除', life: 2000 })
        await Promise.all([albumsStore.refreshAlbums(), crontabsStore.refreshCrontabs()])
      } catch (e) {
        const detail = e instanceof Error ? e.message : String(e) || '删除失败'
        toast.add({ severity: 'error', summary: '错误', detail, life: 3000 })
      }
    },
  })
}
</script>

<template>
  <Card class="overflow-hidden shadow-sm ring-1 ring-slate-200/60 dark:ring-slate-700/60 mt-6">
    <template #title>
      <div class="flex items-center justify-between">
        <span>小米账号管理</span>
        <Button
          label="添加账号"
          icon="pi pi-plus"
          size="small"
          severity="primary"
          @click="openCreateDialog"
        />
      </div>
    </template>
    <template #content>
      <div
        v-if="isInsecureContext"
        class="mb-4 rounded-md bg-red-50 dark:bg-red-950/40 text-red-700 dark:text-red-300 text-xs px-3 py-2 ring-1 ring-red-200 dark:ring-red-900/70"
      >
        警告：当前处于不安全上下文，提交的 passToken 将在网络上以明文传输到服务器，谨防中间人攻击。
        请仅在受信网络环境使用或通过 HTTPS 访问本页面。
      </div>

      <DataTable :value="accounts" :loading="loading" size="small" class="text-sm">
        <template #empty>暂无账号，请点击上方按钮添加。</template>
        <Column field="nickname" header="昵称"></Column>
        <Column field="userId" header="User ID"></Column>
        <Column header="操作" :style="{ width: '10rem' }">
          <template #body="slotProps">
            <div class="flex gap-2">
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
    </template>
  </Card>

  <!-- 添加/编辑 弹窗 -->
  <Dialog
    v-model:visible="showDialog"
    modal
    :header="isEditMode ? '编辑账号' : '添加账号'"
    class="w-full sm:w-[480px]"
  >
    <div class="flex flex-col gap-4 pt-2">
      <div class="flex gap-3">
        <div class="flex-1">
          <label class="block text-sm font-medium text-slate-700 dark:text-slate-200 mb-1"
            >User ID</label
          >
          <InputText v-model="form.userId" placeholder="输入 userId" class="w-full" />
        </div>
        <div class="flex-1">
          <label class="block text-sm font-medium text-slate-700 dark:text-slate-200 mb-1"
            >昵称 (可选)</label
          >
          <InputText v-model="form.nickname" placeholder="输入昵称" class="w-full" />
        </div>
      </div>

      <div>
        <label class="block text-sm font-medium text-slate-700 dark:text-slate-200 mb-1"
          >PassToken</label
        >
        <Textarea
          v-model="form.passToken"
          rows="5"
          :placeholder="isEditMode ? '如需修改，请输入新的 PassToken' : '输入 PassToken'"
          class="w-full"
        />
        <p v-if="isEditMode" class="text-xs text-slate-500 dark:text-slate-400 mt-1">
          注意：更新账号信息时必须重新提供 PassToken。
        </p>
      </div>

      <div v-if="isInsecureContext" class="text-xs text-red-600 dark:text-red-300">
        当前环境不安全，PassToken 将明文传输。
      </div>
    </div>

    <template #footer>
      <div class="flex items-center justify-end gap-2 w-full mt-4">
        <Button label="取消" severity="secondary" text @click="showDialog = false" />
        <Button label="保存" severity="primary" :loading="saving" @click="onSave" />
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
