<script setup lang="ts">
import { RouterView } from 'vue-router'
import { provide, ref, onMounted, watch } from 'vue'
import Toast from 'primevue/toast'
import Silk from '@/components/background/Silk.vue'
import LightRays from '@/components/background/LightRays.vue'
import ConfirmDialog from 'primevue/confirmdialog'

type BgMode = 'lightRays' | 'silk'
const BG_KEY = 'app:bgMode'

const backgroundMode = ref<BgMode>('lightRays')

onMounted(() => {
  const saved = localStorage.getItem(BG_KEY) as BgMode | null
  if (saved === 'silk' || saved === 'lightRays') {
    backgroundMode.value = saved
  }
})

watch(
  backgroundMode,
  (val) => {
    localStorage.setItem(BG_KEY, val)
  },
  { immediate: false },
)

function toggleBackground() {
  backgroundMode.value = backgroundMode.value === 'lightRays' ? 'silk' : 'lightRays'
}

function setBackgroundMode(mode: BgMode) {
  backgroundMode.value = mode
}

provide('backgroundMode', backgroundMode)
provide('toggleBackground', toggleBackground)
provide('setBackgroundMode', setBackgroundMode)
</script>

<template>
  <div class="silk-container">
    <Silk
      v-if="backgroundMode === 'silk'"
      :speed="2"
      :scale="1"
      :color="'#7B7481'"
      :noise-intensity="1"
      :rotation="0"
      class="w-full h-full"
    />
    <LightRays
      v-else
      rays-origin="top-left"
      rays-color="#f2f2f2"
      :rays-speed="0.8"
      :light-spread="0.8"
      :ray-length="1.2"
      :follow-mouse="true"
      :mouse-influence="0.2"
      :noise-amount="0.1"
      :distortion="0.05"
      class-name="custom-rays"
    />
  </div>
  <Toast position="bottom-right" />
  <ConfirmDialog />
  <div class="min-h-screen text-slate-800">
    <RouterView />
  </div>
</template>

<style scoped>
.silk-container {
  position: fixed;
  inset: 0;
  width: 100%;
  height: 100vh;
  overflow: hidden;
  z-index: -1;
  pointer-events: none;
}
</style>
