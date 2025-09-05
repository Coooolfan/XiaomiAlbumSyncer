<script setup lang="ts">
import { ref, inject, computed, onMounted, type Ref } from 'vue'
import Button from 'primevue/button'
import Card from 'primevue/card'

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
</script>

<template>
  <div class="max-w-3xl mx-auto px-4 py-8">
    <Card class="overflow-hidden shadow-sm ring-1 ring-slate-200/60">
      <template #title>外观</template>
      <template #content>
        <div class="flex items-center justify-between">
          <span class="text-sm text-slate-600">背景</span>
          <Button :label="bgLabel" class="!py-2.5 !px-4" @click="onToggleBg" />
        </div>
        <p class="text-xs text-slate-400 mt-3">在 LightRays 与 Silk 之间切换，偏好将被本地保存。</p>
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
