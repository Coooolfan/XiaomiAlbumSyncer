<script setup lang="ts">
import { ref, onMounted } from 'vue'
import Button from 'primevue/button'
import Card from 'primevue/card'
import InputText from 'primevue/inputtext'
import { useToast } from 'primevue/usetoast'
import { api } from '@/ApiInstance'

const exifToolPath = ref('')
const loadingConfig = ref(false)
const savingConfig = ref(false)
const toast = useToast()

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

onMounted(() => {
  fetchSystemConfig()
})
</script>

<template>
  <Card
    class="overflow-hidden shadow-sm ring-1 ring-slate-200/60 mb-6"
    pt:footer:class="text-right"
  >
    <template #title>
      <div class="flex items-center justify-between">
        <span>系统配置</span>
        <Button icon="pi pi-refresh" severity="secondary" rounded text @click="fetchSystemConfig" />
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
