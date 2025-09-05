<script setup lang="ts">
import Card from 'primevue/card'
</script>

<template>
  <div class="max-w-3xl mx-auto px-4 py-8">
    <Card class="overflow-hidden shadow-sm ring-1 ring-slate-200/60">
      <template #title>计划（占位）</template>
      <template #content>
        <div class="space-y-3 text-sm text-slate-600">
          <p>这里将展示计划任务的配置与列表（如定时同步、最近运行、下次运行时间等）。</p>
          <p class="text-xs text-slate-400">
            当前为占位页面。待你提供需求后，我会补充任务列表、创建/编辑计划的表单以及执行历史等模块。
          </p>
        </div>
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
