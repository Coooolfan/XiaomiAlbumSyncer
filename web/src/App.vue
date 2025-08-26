<script setup lang="ts">
import { onMounted, ref } from 'vue'
import Button from "primevue/button"

const demoStr = ref('unloaded')

onMounted(async () => {
  const resp = await fetch('/api/hello')
  demoStr.value = await resp.text()
})
</script>

<template>
  <div class="min-h-screen bg-gray-100 p-8">
    <div class="max-w-4xl mx-auto">
      <h1 class="text-4xl font-bold text-blue-600 mb-4">You did it!</h1>
      <p class="text-gray-700 mb-4">
        Visit <a href="https://vuejs.org/" target="_blank" rel="noopener" class="text-blue-500 hover:text-blue-700 underline">vuejs.org</a> to read the
        documentation
      </p>
      <p class="text-green-600 font-semibold mb-4">{{ demoStr }}</p>
      <Button label="PrimeVue Button" class="bg-purple-500 hover:bg-purple-600" />
    </div>
  </div>
</template>
