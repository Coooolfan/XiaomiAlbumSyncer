<script setup lang="ts">
defineProps<{
  icon: string
  label: string
  desc: string
  selected?: boolean
  disabled?: boolean
}>()
</script>

<template>
  <button
    type="button"
    :disabled="disabled"
    class="flex items-center gap-4 rounded-lg border px-4 py-4 transition-colors text-left"
    :class="
      disabled
        ? 'border-slate-200 dark:border-slate-700 opacity-50 cursor-not-allowed'
        : selected
          ? 'border-slate-400 dark:border-slate-500 bg-slate-50 dark:bg-slate-800 cursor-pointer'
          : 'border-slate-200 dark:border-slate-700 hover:border-slate-300 dark:hover:border-slate-600 hover:bg-slate-50 dark:hover:bg-slate-800 cursor-pointer'
    "
  >
    <i class="pi text-2xl text-slate-600 dark:text-slate-300" :class="icon"></i>
    <span class="flex flex-col items-start text-left flex-1">
      <span class="flex items-center gap-2">
        <span class="font-medium text-slate-800 dark:text-slate-100">{{ label }}</span>
        <slot name="badge" />
      </span>
      <span class="mt-1 text-[13px] text-slate-500 dark:text-slate-400">{{ desc }}</span>
    </span>
    <i v-if="selected" class="pi pi-check-circle text-lg text-slate-600 dark:text-slate-300"></i>
  </button>
</template>
