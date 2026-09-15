<script setup lang="ts">
import { watch } from 'vue'
import { RouterView } from 'vue-router'
import Toast from 'primevue/toast'
import Silk from '@/components/background/Silk.vue'
import LightRays from '@/components/background/LightRays.vue'
import ConfirmDialog from 'primevue/confirmdialog'
import { useTheme } from '@/composables/useTheme'
import { useLocale } from '@/composables/useLocale'

const { isDark } = useTheme()
useLocale()

watch(
  isDark,
  (dark) => {
    document.documentElement.classList.toggle('dark', dark)
  },
  { immediate: true },
)
</script>

<template>
  <div class="silk-container">
    <Silk
      v-if="isDark"
      :speed="2"
      :scale="1"
      color="#3A4352"
      :noise-intensity="1"
      :rotation="0"
      class="w-full h-full"
    />
    <LightRays
      v-else
      rays-origin="top-left"
      rays-color="#F2F2F2"
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
