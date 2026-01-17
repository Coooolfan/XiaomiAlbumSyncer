<script setup lang="ts">
import { ref, onMounted } from 'vue'
import Button from 'primevue/button'
import Card from 'primevue/card'
import InputText from 'primevue/inputtext'
import Dialog from 'primevue/dialog'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import { api } from '@/ApiInstance'
import { isWebAuthnSupported, registerPasskey } from '@/utils/passkey'
import type { PasskeyCredentialInfo } from '@/utils/passkey'

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
    toast.add({ severity: 'error', summary: '加载失败', detail, life: 3000 })
  } finally {
    loading.value = false
  }
}

function formatDate(timestamp: number | null | undefined): string {
  if (!timestamp) return '-'
  return new Date(timestamp).toLocaleString('zh-CN', {
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
    toast.add({ severity: 'warn', summary: '提示', detail: '请输入当前密码', life: 2500 })
    return
  }
  if (!registerName.value.trim()) {
    toast.add({ severity: 'warn', summary: '提示', detail: '请输入 Passkey 名称', life: 2500 })
    return
  }

  registering.value = true
  try {
    await registerPasskey(registerPassword.value, registerName.value.trim())
    toast.add({ severity: 'success', summary: '成功', detail: 'Passkey 注册成功', life: 2000 })
    showRegisterDialog.value = false
    await loadCredentials()
  } catch (e) {
    console.error('Register passkey error:', e)
    const msg = e instanceof Error ? e.message : String(e)
    if (msg.includes('Auth failed')) {
      toast.add({ severity: 'error', summary: '错误', detail: '密码错误', life: 3000 })
    } else if (msg.includes('The operation either timed out or was not allowed')) {
      toast.add({
        severity: 'warn',
        summary: '已取消',
        detail: 'Passkey 注册被取消或超时',
        life: 3000,
      })
    } else {
      toast.add({ severity: 'error', summary: '注册失败', detail: msg, life: 3000 })
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
    toast.add({ severity: 'warn', summary: '提示', detail: '名称不能为空', life: 2500 })
    return
  }

  renaming.value = true
  try {
    await api.passkeyController.updateCredentialName({
      credentialId: renameCredentialId.value,
      body: { name: renameName.value.trim() },
    })
    toast.add({ severity: 'success', summary: '成功', detail: '名称已更新', life: 2000 })
    showRenameDialog.value = false
    await loadCredentials()
  } catch (e) {
    const detail = e instanceof Error ? e.message : String(e)
    toast.add({ severity: 'error', summary: '更新失败', detail, life: 3000 })
  } finally {
    renaming.value = false
  }
}

function confirmDelete(cred: PasskeyCredentialInfo) {
  confirm.require({
    message: `确定要删除 Passkey "${cred.name}" 吗？此操作不可撤销。`,
    header: '确认删除',
    icon: 'pi pi-exclamation-triangle',
    rejectLabel: '取消',
    acceptLabel: '删除',
    rejectProps: { severity: 'secondary', text: true },
    acceptProps: { severity: 'danger' },
    accept: () => doDelete(cred.id),
  })
}

async function doDelete(credentialId: string) {
  try {
    await api.passkeyController.deleteCredential({ credentialId })
    toast.add({ severity: 'success', summary: '成功', detail: 'Passkey 已删除', life: 2000 })
    await loadCredentials()
  } catch (e) {
    const detail = e instanceof Error ? e.message : String(e)
    toast.add({ severity: 'error', summary: '删除失败', detail, life: 3000 })
  }
}
</script>

<template>
  <Card
    class="overflow-hidden shadow-sm ring-1 ring-slate-200/60 mt-6"
    pt:footer:class="text-right"
  >
    <template #title>
      <div class="flex items-center gap-2">
        <span>Passkey 管理</span>
        <span class="text-xs font-normal text-slate-500">(WebAuthn)</span>
      </div>
    </template>
    <template #content>
      <!-- WebAuthn 不支持警告 -->
      <div
        v-if="!webAuthnSupported"
        class="mb-4 rounded-md bg-amber-50 text-amber-700 text-sm px-3 py-2 ring-1 ring-amber-200"
      >
        <i class="pi pi-exclamation-triangle mr-2"></i>
        当前浏览器不支持
        Passkey（WebAuthn）。请使用支持的现代浏览器（Chrome、Safari、Firefox、Edge）。
      </div>

      <!-- 不安全上下文警告 -->
      <div
        v-if="isInsecureContext"
        class="mb-4 rounded-md bg-red-50 text-red-700 text-xs px-3 py-2 ring-1 ring-red-200"
      >
        <i class="pi pi-shield mr-2"></i>
        警告：当前处于不安全上下文（非 HTTPS），WebAuthn 功能可能受限。
      </div>

      <!-- 说明文字 -->
      <p class="text-sm text-slate-600 mb-4">
        Passkey 是一种无密码登录方式，使用设备的生物识别（指纹、面容）或 PIN 进行验证。
        您可以在多个设备上注册 Passkey，实现便捷安全的登录。
      </p>

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
          <div class="text-center text-slate-500 py-4">尚未注册任何 Passkey</div>
        </template>

        <Column field="name" header="名称" class="font-medium" />
        <Column header="创建时间">
          <template #body="{ data }">
            {{ formatDate(data.createdAt) }}
          </template>
        </Column>
        <Column header="最后使用">
          <template #body="{ data }">
            {{ formatDate(data.lastUsedAt) }}
          </template>
        </Column>
        <Column header="操作" style="width: 120px">
          <template #body="{ data }">
            <div class="flex gap-1">
              <Button
                icon="pi pi-pencil"
                severity="secondary"
                text
                rounded
                size="small"
                v-tooltip.top="'重命名'"
                @click="openRenameDialog(data)"
              />
              <Button
                icon="pi pi-trash"
                severity="danger"
                text
                rounded
                size="small"
                v-tooltip.top="'删除'"
                @click="confirmDelete(data)"
              />
            </div>
          </template>
        </Column>
      </DataTable>
    </template>

    <template #footer>
      <Button
        label="注册新 Passkey"
        icon="pi pi-plus"
        :disabled="!webAuthnSupported"
        @click="openRegisterDialog"
      />
    </template>
  </Card>

  <!-- 注册对话框 -->
  <Dialog
    v-model:visible="showRegisterDialog"
    modal
    header="注册新 Passkey"
    class="w-full sm:w-105"
  >
    <div class="space-y-4">
      <p class="text-sm text-slate-600">
        注册前需要验证您的密码。注册后，您可以使用此设备的 Passkey 登录。
      </p>

      <div>
        <label class="text-sm font-medium text-slate-700 block mb-1">当前密码</label>
        <InputText
          v-model="registerPassword"
          type="password"
          placeholder="输入当前密码"
          class="w-full"
          @keyup.enter="doRegister"
        />
      </div>

      <div>
        <label class="text-sm font-medium text-slate-700 block mb-1">Passkey 名称</label>
        <InputText
          v-model="registerName"
          placeholder="例如：MacBook Pro、iPhone 15"
          class="w-full"
          @keyup.enter="doRegister"
        />
        <p class="text-xs text-slate-500 mt-1">为此 Passkey 取一个便于识别的名称</p>
      </div>

      <div
        v-if="isInsecureContext"
        class="rounded-md bg-red-50 text-red-700 text-xs px-3 py-2 ring-1 ring-red-200"
      >
        警告：当前处于不安全上下文，密码将以明文传输。
      </div>
    </div>

    <template #footer>
      <div class="flex items-center justify-end gap-2 w-full">
        <Button label="取消" severity="secondary" text @click="showRegisterDialog = false" />
        <Button label="注册" :loading="registering" @click="doRegister" />
      </div>
    </template>
  </Dialog>

  <!-- 重命名对话框 -->
  <Dialog v-model:visible="showRenameDialog" modal header="重命名 Passkey" class="w-full sm:w-96">
    <div>
      <label class="text-sm font-medium text-slate-700 block mb-1">新名称</label>
      <InputText
        v-model="renameName"
        placeholder="输入新名称"
        class="w-full"
        @keyup.enter="doRename"
      />
    </div>

    <template #footer>
      <div class="flex items-center justify-end gap-2 w-full">
        <Button label="取消" severity="secondary" text @click="showRenameDialog = false" />
        <Button label="保存" :loading="renaming" @click="doRename" />
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
