<script setup lang="ts">
defineProps<{
  title: string
  description?: string
}>()
</script>

<template>
  <section>
    <header class="flex items-start justify-between gap-4">
      <div class="min-w-0">
        <h2 class="text-[15px] font-semibold tracking-tight text-slate-800 dark:text-slate-100">
          {{ title }}
        </h2>
        <p v-if="description" class="mt-0.5 text-xs text-slate-400 dark:text-slate-500">
          {{ description }}
        </p>
      </div>
      <div v-if="$slots.actions" class="flex shrink-0 items-center gap-1">
        <slot name="actions" />
      </div>
    </header>

    <div class="mt-4">
      <slot />
    </div>

    <div v-if="$slots.footer" class="mt-4 flex justify-end">
      <slot name="footer" />
    </div>
  </section>
</template>
