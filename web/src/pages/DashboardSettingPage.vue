<script setup lang="ts">
import { ref, inject, computed, onMounted, type Ref } from 'vue'
import Button from 'primevue/button'
import Card from 'primevue/card'
import Textarea from 'primevue/textarea'
import InputText from 'primevue/inputtext'

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
onMounted(() => {
  // 初始化拉取系统配置
  fetchSystemConfig()
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
          <p class="text-xs text-slate-400">如果您使用 Docker 部署此项目，请不要改动此项目。如果您使用其他方式部署此项目，请输入 exiftool 可执行文件路径。</p>
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
      </template>
      <template #footer>
        <Button label="更新 passToken" :loading="updating" @click="onUpdatePassToken" />
      </template>
    </Card>
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
