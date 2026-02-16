<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterView } from 'vue-router'
import Toast from 'primevue/toast'
import Silk from '@/components/background/Silk.vue'
import LightRays from '@/components/background/LightRays.vue'
import ConfirmDialog from 'primevue/confirmdialog'
import { storeToRefs } from 'pinia'
import { usePreferencesStore } from '@/stores/preferences'

const preferencesStore = usePreferencesStore()
const { backgroundMode } = storeToRefs(preferencesStore)

const systemDark = ref(false)
let darkMediaQuery: MediaQueryList | null = null

function updateSystemDark(event?: MediaQueryListEvent) {
  if (event) {
    systemDark.value = event.matches
    return
  }
  if (typeof window !== 'undefined') {
    systemDark.value = window.matchMedia('(prefers-color-scheme: dark)').matches
  }
}

const silkColor = computed(() => (systemDark.value ? '#3A4352' : '#7B7481'))
const rayColor = computed(() => (systemDark.value ? '#A6B0C1' : '#F2F2F2'))

onMounted(() => {
  if (typeof window === 'undefined') return
  darkMediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
  updateSystemDark()
  if (typeof darkMediaQuery.addEventListener === 'function') {
    darkMediaQuery.addEventListener('change', updateSystemDark)
  } else {
    darkMediaQuery.addListener(updateSystemDark)
  }
})

onBeforeUnmount(() => {
  if (!darkMediaQuery) return
  if (typeof darkMediaQuery.removeEventListener === 'function') {
    darkMediaQuery.removeEventListener('change', updateSystemDark)
  } else {
    darkMediaQuery.removeListener(updateSystemDark)
  }
})
</script>

<template>
  <div class="silk-container">
    <Silk
      v-if="backgroundMode === 'silk'"
      :speed="2"
      :scale="1"
      :color="silkColor"
      :noise-intensity="1"
      :rotation="0"
      class="w-full h-full"
    />
    <LightRays
      v-else
      rays-origin="top-left"
      :rays-color="rayColor"
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
  <div class="min-h-screen text-slate-800 dark:text-slate-100">
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
