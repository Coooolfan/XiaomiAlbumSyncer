<script setup lang="ts">
import { onMounted, ref } from 'vue'
import Button from 'primevue/button'
import Card from 'primevue/card'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import SelectButton from 'primevue/selectbutton'
import Tag from 'primevue/tag'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'
import { api } from '@/ApiInstance'
import type { McpTokenPermission } from '@/__generated/model/enums'
import type { McpTokenInfo } from '@/__generated/model/static'

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

const permissionOptions: Array<{ label: string; value: McpTokenPermission }> = [
  { label: '只读', value: 'READ_ONLY' },
  { label: '允许触发任务', value: 'ALLOW_TRIGGER' },
]

onMounted(loadTokens)

async function loadTokens() {
  loading.value = true
  try {
    tokens.value = await api.mcpTokenController.list()
  } catch (e) {
    toast.add({ severity: 'error', summary: '加载失败', detail: errorMessage(e), life: 3000 })
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
    toast.add({ severity: 'warn', summary: '提示', detail: '请输入 Token 名称', life: 2500 })
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
    toast.add({ severity: 'error', summary: '创建失败', detail: errorMessage(e), life: 3000 })
  } finally {
    creating.value = false
  }
}

async function copyCreatedToken() {
  try {
    await navigator.clipboard.writeText(createdToken.value)
    toast.add({
      severity: 'success',
      summary: '已复制',
      detail: 'Token 已复制到剪贴板',
      life: 1800,
    })
  } catch {
    toast.add({
      severity: 'warn',
      summary: '无法自动复制',
      detail: '请手动选中并复制 Token',
      life: 3000,
    })
  }
}

function clearCreatedToken() {
  createdToken.value = ''
}

function confirmRevoke(token: McpTokenInfo) {
  confirm.require({
    message: `确定要撤销 MCP Token“${token.name}”吗？使用它的客户端将立即无法访问。`,
    header: '确认撤销',
    icon: 'pi pi-exclamation-triangle',
    rejectLabel: '取消',
    acceptLabel: '撤销',
    rejectProps: { severity: 'secondary', text: true },
    acceptProps: { severity: 'danger' },
    accept: () => revokeToken(token.id),
  })
}

async function revokeToken(id: number) {
  try {
    await api.mcpTokenController.revoke({ id })
    toast.add({ severity: 'success', summary: '已撤销', detail: 'MCP Token 已撤销', life: 2000 })
    await loadTokens()
  } catch (e) {
    toast.add({ severity: 'error', summary: '撤销失败', detail: errorMessage(e), life: 3000 })
  }
}

function permissionLabel(permission: McpTokenPermission): string {
  return permission === 'ALLOW_TRIGGER' ? '允许触发任务' : '只读'
}

function formatDate(timestamp: number): string {
  return new Date(timestamp).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error) || '请求失败'
}
</script>

<template>
  <Card class="overflow-hidden shadow-sm ring-1 ring-slate-200/60 dark:ring-slate-700/60 mt-6">
    <template #title>
      <div class="flex items-center justify-between gap-3">
        <span>MCP Token 管理</span>
        <Button icon="pi pi-refresh" severity="secondary" rounded text @click="loadTokens" />
      </div>
    </template>

    <template #content>
      <p class="text-sm text-slate-600 dark:text-slate-300 mb-4">
        MCP 客户端通过 <code>Authorization: Bearer &lt;token&gt;</code> 访问
        <code>/mcp</code>。权限在创建时固定；如需变更，请撤销后重新创建。
      </p>

      <DataTable :value="tokens" :loading="loading" class="text-sm" stripedRows>
        <template #empty>
          <div class="text-center text-slate-500 dark:text-slate-400 py-4">尚未创建 MCP Token</div>
        </template>

        <Column field="name" header="名称" class="font-medium" />
        <Column header="权限">
          <template #body="{ data }">
            <Tag
              :value="permissionLabel(data.permission)"
              :severity="data.permission === 'ALLOW_TRIGGER' ? 'warn' : 'secondary'"
            />
          </template>
        </Column>
        <Column header="创建时间">
          <template #body="{ data }">{{ formatDate(data.createdAt) }}</template>
        </Column>
        <Column header="操作" style="width: 72px">
          <template #body="{ data }">
            <Button
              icon="pi pi-trash"
              severity="danger"
              text
              rounded
              size="small"
              v-tooltip.top="'撤销'"
              @click="confirmRevoke(data)"
            />
          </template>
        </Column>
      </DataTable>
    </template>

    <template #footer>
      <Button label="创建 MCP Token" icon="pi pi-plus" @click="openCreateDialog" />
    </template>
  </Card>

  <Dialog v-model:visible="showCreateDialog" modal header="创建 MCP Token" class="w-full sm:w-120">
    <div class="space-y-4">
      <div>
        <label class="text-sm font-medium text-slate-700 dark:text-slate-200 block mb-1"
          >Token 名称</label
        >
        <InputText
          v-model="tokenName"
          maxlength="100"
          placeholder="例如：Claude Desktop、家庭服务器"
          class="w-full"
          @keyup.enter="createToken"
        />
      </div>

      <div>
        <label class="text-sm font-medium text-slate-700 dark:text-slate-200 block mb-2"
          >权限</label
        >
        <SelectButton
          v-model="tokenPermission"
          :options="permissionOptions"
          option-label="label"
          option-value="value"
          :allow-empty="false"
          class="w-full"
        />
        <p class="text-xs text-slate-500 dark:text-slate-400 mt-2">
          “只读”只能查询数据；“允许触发任务”还可立即启动定时下载任务。
        </p>
      </div>
    </div>

    <template #footer>
      <div class="flex items-center justify-end gap-2 w-full">
        <Button label="取消" severity="secondary" text @click="showCreateDialog = false" />
        <Button label="创建" :loading="creating" @click="createToken" />
      </div>
    </template>
  </Dialog>

  <Dialog
    v-model:visible="showTokenDialog"
    modal
    header="请立即保存 MCP Token"
    class="w-full sm:w-150"
    @hide="clearCreatedToken"
  >
    <div class="space-y-4">
      <div
        class="rounded-md bg-amber-50 dark:bg-amber-950/35 text-amber-800 dark:text-amber-200 text-sm px-3 py-2 ring-1 ring-amber-200 dark:ring-amber-900/60"
      >
        该 Token 只会显示这一次。关闭后无法再次查看，丢失时需撤销并重新创建。
      </div>
      <div class="flex gap-2">
        <InputText :model-value="createdToken" readonly class="w-full font-mono text-sm" />
        <Button label="复制" icon="pi pi-copy" @click="copyCreatedToken" />
      </div>
    </div>

    <template #footer>
      <Button label="我已保存" @click="showTokenDialog = false" />
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

:deep(.p-selectbutton .p-togglebutton) {
  flex: 1;
}
</style>
