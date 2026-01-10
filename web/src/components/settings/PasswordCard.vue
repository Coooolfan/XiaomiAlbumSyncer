<script setup lang="ts">
import { ref, onMounted } from 'vue'
import Button from 'primevue/button'
import Card from 'primevue/card'
import InputText from 'primevue/inputtext'
import Dialog from 'primevue/dialog'
import { useToast } from 'primevue/usetoast'
import { api } from '@/ApiInstance'

const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const updatingPassword = ref(false)
const showPasswordConfirmVisible = ref(false)
const isInsecureContext = ref(false)
const toast = useToast()

onMounted(() => {
  try {
    isInsecureContext.value = typeof window !== 'undefined' && !window.isSecureContext
  } catch {
    isInsecureContext.value = false
  }
})

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
</script>

<template>
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

  <!-- 更新密码 确认 -->
  <Dialog
    v-model:visible="showPasswordConfirmVisible"
    modal
    header="更新密码"
    class="w-full sm:w-105"
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
          @click="
            () => {
              showPasswordConfirmVisible = false
            }
          "
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
