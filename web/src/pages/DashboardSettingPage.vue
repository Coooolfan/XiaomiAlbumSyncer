<script setup lang="ts">
import { ref, inject, computed, onMounted, type Ref } from 'vue'
import Button from 'primevue/button'
import Card from 'primevue/card'
import Textarea from 'primevue/textarea'
import InputText from 'primevue/inputtext'
import Dialog from 'primevue/dialog'

import { useToast } from 'primevue/usetoast'
import { api } from '@/ApiInstance'

type BgMode = 'lightRays' | 'silk'
const BG_KEY = 'app:bgMode'

const backgroundMode = inject<Ref<BgMode>>('backgroundMode')
const toggleBackground = inject<() => void>('toggleBackground')
const setBackgroundMode = inject<(mode: BgMode) => void>('setBackgroundMode')

const bgModeLocal = ref<BgMode>('lightRays')
onMounted(() => {
  const saved = localStorage.getItem(BG_KEY) as BgMode | null
  if (saved === 'silk' || saved === 'lightRays') {
    bgModeLocal.value = saved
    setBackgroundMode?.(saved)
  } else {
    localStorage.setItem(BG_KEY, bgModeLocal.value)
    setBackgroundMode?.(bgModeLocal.value)
  }
})

const bgLabel = computed(() => (bgModeLocal.value === 'silk' ? '背景：丝绸' : '背景：光束'))

function onToggleBg() {
  if (toggleBackground) {
    toggleBackground()
    bgModeLocal.value =
      backgroundMode?.value ?? (bgModeLocal.value === 'lightRays' ? 'silk' : 'lightRays')
  } else {
    bgModeLocal.value = bgModeLocal.value === 'lightRays' ? 'silk' : 'lightRays'
  }
  localStorage.setItem(BG_KEY, bgModeLocal.value)
}
const passToken = ref('')
const userId = ref('')
const updating = ref(false)
const toast = useToast()
const showPassConfirmVisible = ref(false)
const isInsecureContext = ref(false)

// ===== 密码更新 =====
const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const updatingPassword = ref(false)
const showPasswordConfirmVisible = ref(false)

// ===== 从 V2 导入 =====
const importingFromV2 = ref(false)
const showImportConfirmVisible = ref(false)
const showImportBlockingVisible = ref(false)

// ===== 系统配置：exifToolPath =====
const exifToolPath = ref('')
const loadingConfig = ref(false)
const savingConfig = ref(false)

async function fetchSystemConfig() {
  loadingConfig.value = true
  try {
    const cfg = await api.systemConfigController.getSystemConfig()
    exifToolPath.value = cfg?.exifToolPath ?? ''
  } catch (e) {
    console.error('获取系统配置失败', e)
    toast.add({ severity: 'error', summary: '获取失败', detail: '无法获取系统配置', life: 2200 })
  } finally {
    loadingConfig.value = false
  }
}

async function onUpdateSystemConfig() {
  if (!exifToolPath.value || exifToolPath.value.trim() === '') {
    toast.add({ severity: 'warn', summary: '提示', detail: '请输入 exiftool 路径', life: 2500 })
    return
  }
  try {
    savingConfig.value = true
    await api.systemConfigController.updateSystemConfig({
      body: { exifToolPath: exifToolPath.value },
    })
    toast.add({ severity: 'success', summary: '成功', detail: '系统配置已保存', life: 2000 })
  } catch (e) {
    const detail = e instanceof Error ? e.message : String(e) || '保存失败'
    toast.add({ severity: 'error', summary: '错误', detail, life: 3000 })
  } finally {
    savingConfig.value = false
  }
}

async function onUpdatePassToken() {
  if (!passToken.value) {
    toast.add({ severity: 'warn', summary: '提示', detail: '请输入 passToken', life: 2500 })
    return
  }
  try {
    updating.value = true
    await api.systemConfigController.updatePassToken({
      body: { passToken: passToken.value, userId: userId.value },
    })
    toast.add({ severity: 'success', summary: '成功', detail: 'passToken 已更新', life: 2000 })
    passToken.value = ''
    userId.value = ''
  } catch (e) {
    const detail = e instanceof Error ? e.message : String(e) || '更新失败'
    toast.add({ severity: 'error', summary: '错误', detail, life: 3000 })
  } finally {
    updating.value = false
  }
}

function requestUpdatePassToken() {
  showPassConfirmVisible.value = true
}

async function confirmUpdatePassToken() {
  showPassConfirmVisible.value = false
  await onUpdatePassToken()
}

async function onUpdatePassword() {
  if (!oldPassword.value || !newPassword.value || !confirmPassword.value) {
    toast.add({ severity: 'warn', summary: '提示', detail: '请完整填写所有密码字段', life: 2500 })
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    toast.add({ severity: 'warn', summary: '提示', detail: '两次输入的新密码不一致', life: 2500 })
    return
  }
  try {
    updatingPassword.value = true
    await api.systemConfigController.updatePassword({
      body: { oldPassword: oldPassword.value, password: newPassword.value },
    })
    toast.add({ severity: 'success', summary: '成功', detail: '密码已更新', life: 2000 })
    oldPassword.value = ''
    newPassword.value = ''
    confirmPassword.value = ''
  } catch (e) {
    const detail = e instanceof Error ? e.message : String(e) || '更新失败'
    toast.add({ severity: 'error', summary: '错误', detail, life: 3000 })
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
    toast.add({ severity: isNotEmpty ? 'warn' : 'error', summary: isNotEmpty ? '非空库' : '错误', detail, life: 3200 })
  } finally {
    importingFromV2.value = false
    showImportBlockingVisible.value = false
  }
}
onMounted(() => {
  // 初始化拉取系统配置
  fetchSystemConfig()
  try {
    // 非安全上下文提示（例如 http 或部分自签名环境）
    isInsecureContext.value = typeof window !== 'undefined' && !window.isSecureContext
  } catch {
    isInsecureContext.value = false
  }
})
</script>

<template>
  <div class="max-w-3xl mx-auto px-4 py-8">
    <Card
      class="overflow-hidden shadow-sm ring-1 ring-slate-200/60 mb-6"
      pt:footer:class="text-right"
    >
      <template #title>
        <div class="flex items-center justify-between">
          <span>系统配置</span>
          <Button
            icon="pi pi-refresh"
            severity="secondary"
            rounded
            text
            @click="fetchSystemConfig"
          />
        </div>
      </template>
      <template #content>
        <div class="space-y-2">
          <span class="text-sm text-slate-600">exiftool 路径</span>
          <InputText
            v-model="exifToolPath"
            :disabled="loadingConfig"
            placeholder="输入 exiftool 可执行文件路径"
            class="w-full"
          />
          <p class="text-xs text-slate-400">
            如果您使用 Docker 部署此项目，请不要改动此配置。如果您使用其他方式部署此项目，请输入
            exiftool 可执行文件路径。
          </p>
        </div>
      </template>
      <template #footer>
        <Button label="保存" :loading="savingConfig" @click="onUpdateSystemConfig" />
      </template>
    </Card>
    <Card class="overflow-hidden shadow-sm ring-1 ring-slate-200/60">
      <template #title>外观</template>
      <template #content>
        <div class="flex items-center justify-between">
          <span class="text-sm text-slate-600">背景</span>
          <Button :label="bgLabel" @click="onToggleBg" />
        </div>
        <p class="text-xs text-slate-400 mt-3">在 LightRays 与 Silk 之间切换，偏好将被本地保存。</p>
      </template>
    </Card>
    <Card
      class="overflow-hidden shadow-sm ring-1 ring-slate-200/60 mt-6"
      pt:footer:class="text-right"
    >
      <template #title>更新 PassToken</template>
      <template #content>
        <Textarea v-model="passToken" rows="5" placeholder="输入 passToken" class="w-full" />
        <InputText v-model="userId" rows="1" placeholder="输入 userId" class="w-full mt-2" />
        <div
          v-if="isInsecureContext"
          class="mt-3 rounded-md bg-red-50 text-red-700 text-xs px-3 py-2 ring-1 ring-red-200"
        >
          警告：当前处于不安全上下文，提交的 passToken 将在网络上以明文传输到服务器，可能被窃取。
          请仅在受信网络环境使用或通过 HTTPS 访问本页面。
        </div>
      </template>
      <template #footer>
        <Button label="更新 passToken" :loading="updating" @click="requestUpdatePassToken" />
      </template>
    </Card>
    <Card
      class="overflow-hidden shadow-sm ring-1 ring-slate-200/60 mt-6"
      pt:footer:class="text-right"
    >
      <template #title>修改密码</template>
      <template #content>
        <InputText
          v-model="oldPassword"
          :type="'password'"
          placeholder="输入当前密码"
          class="w-full"
        />
        <InputText
          v-model="newPassword"
          :type="'password'"
          placeholder="输入新密码"
          class="w-full mt-2"
        />
        <InputText
          v-model="confirmPassword"
          :type="'password'"
          placeholder="再次输入新密码"
          class="w-full mt-2"
        />
        <div
          v-if="isInsecureContext"
          class="mt-3 rounded-md bg-red-50 text-red-700 text-xs px-3 py-2 ring-1 ring-red-200"
        >
          警告：当前处于不安全上下文，提交的密码将在网络上以明文传输到服务器，可能被窃取。
          请仅在受信网络环境使用或通过 HTTPS 访问本页面。
        </div>
      </template>
      <template #footer>
        <Button label="更新密码" :loading="updatingPassword" @click="requestUpdatePassword" />
      </template>
    </Card>
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
    <!-- 更新 PassToken 确认 -->
    <Dialog
      v-model:visible="showPassConfirmVisible"
      modal
      header="更新 PassToken"
      class="w-full sm:w-[420px]"
    >
      <div class="text-sm text-slate-700">
        确定要更新 passToken 吗？
        <span v-if="isInsecureContext" class="text-red-600 font-medium">
          当前为不安全上下文，提交将以明文传输。
        </span>
      </div>
      <template #footer>
        <div class="flex items-center justify-end gap-2 w-full">
          <Button
            label="取消"
            severity="secondary"
            text
            @click="
              () => {
                showPassConfirmVisible = false
              }
            "
          />
          <Button
            label="确定"
            severity="warning"
            :loading="updating"
            @click="confirmUpdatePassToken"
          />
        </div>
      </template>
    </Dialog>
    <!-- 更新密码 确认 -->
    <Dialog
      v-model:visible="showPasswordConfirmVisible"
      modal
      header="更新密码"
      class="w-full sm:w-[420px]"
    >
      <div class="text-sm text-slate-700">
        确定要更新密码吗？
        <span v-if="isInsecureContext" class="text-red-600 font-medium">
          当前为不安全上下文，提交将以明文传输。
        </span>
      </div>
      <template #footer>
        <div class="flex items-center justify-end gap-2 w-full">
          <Button
            label="取消"
            severity="secondary"
            text
            @click="() => { showPasswordConfirmVisible = false }"
          />
          <Button
            label="确定"
            severity="warning"
            :loading="updatingPassword"
            @click="confirmUpdatePassword"
          />
        </div>
      </template>
    </Dialog>
    <!-- 从 V2 导入 二次确认 -->
    <Dialog
      v-model:visible="showImportConfirmVisible"
      modal
      header="从 V2 导入数据"
      class="w-full sm:w-[460px]"
    >
      <div class="text-sm text-slate-700">
        确定要从 V2 导入数据吗？
        <div class="mt-2 text-xs text-red-600">
          注意：仅在空库环境下执行，导入过程可能耗时，执行期间将阻塞。
        </div>
      </div>
      <template #footer>
        <div class="flex items-center justify-end gap-2 w-full">
          <Button label="取消" severity="secondary" text @click="() => { showImportConfirmVisible = false }" />
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
      class="w-full sm:w-[420px]"
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
  </div>
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
