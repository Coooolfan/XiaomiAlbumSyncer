<script setup lang="ts">
import { computed } from 'vue'
import RadioButton from 'primevue/radiobutton'
import type { SyncMode } from '@/__generated/model/enums'

const props = defineProps<{
  modelValue: SyncMode
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: SyncMode): void
}>()

const selectedMode = computed({
  get: () => props.modelValue,
  set: (value: SyncMode) => emit('update:modelValue', value),
})

// 同步模式选项配置
const syncModeOptions = [
  {
    value: 'ADD_ONLY' as const,
    label: '仅新增',
    description: '只下载云端新增的文件到本地，保留本地已有文件',
    recommended: true,
  },
  {
    value: 'SYNC_ALL_CHANGES' as const,
    label: '同步所有变化',
    description: '同步云端的新增、修改、删除操作到本地',
    recommended: false,
  },
]
</script>

<template>
  <div class="space-y-3">
    <label class="block text-xs font-medium text-slate-500">同步模式</label>
    
    <div class="space-y-3">
      <div
        v-for="option in syncModeOptions"
        :key="option.value"
        class="flex items-start gap-3 p-3 border border-slate-200 rounded-lg hover:border-slate-300 transition-colors cursor-pointer"
        :class="{
          'border-blue-500 bg-blue-50': selectedMode === option.value,
          'border-slate-200': selectedMode !== option.value,
        }"
        @click="selectedMode = option.value"
      >
        <RadioButton
          :value="option.value"
          v-model="selectedMode"
          :inputId="`sync-mode-${option.value}`"
          class="mt-0.5"
        />
        
        <div class="flex-1 min-w-0">
          <div class="flex items-center gap-2">
            <label
              :for="`sync-mode-${option.value}`"
              class="text-sm font-medium text-slate-700 cursor-pointer"
            >
              {{ option.label }}
            </label>
            <span
              v-if="option.recommended"
              class="px-2 py-0.5 text-xs font-medium text-blue-600 bg-blue-100 rounded-full"
            >
              推荐
            </span>
          </div>
          <p class="text-xs text-slate-500 mt-1">
            {{ option.description }}
          </p>
        </div>
      </div>
    </div>
    
    <div class="text-[10px] text-slate-400">
      <strong>提示：</strong>仅新增模式适合希望保留本地所有文件的用户；同步所有变化模式适合希望本地与云端完全一致的用户。
    </div>
  </div>
</template>