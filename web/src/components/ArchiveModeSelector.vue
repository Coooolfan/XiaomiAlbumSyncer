<script setup lang="ts">
import { computed } from 'vue'
import RadioButton from 'primevue/radiobutton'
import InputNumber from 'primevue/inputnumber'
import type { ArchiveMode } from '@/__generated/model/enums'

const props = defineProps<{
  modelValue: ArchiveMode
  archiveDays: number
  cloudSpaceThreshold: number
  // 新增验证错误属性
  archiveDaysError?: string
  cloudSpaceThresholdError?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: ArchiveMode): void
  (e: 'update:archiveDays', value: number): void
  (e: 'update:cloudSpaceThreshold', value: number): void
  // 新增验证事件
  (e: 'validate'): void
}>()

const selectedMode = computed({
  get: () => props.modelValue,
  set: (value: ArchiveMode) => {
    emit('update:modelValue', value)
    // 模式切换时触发验证
    emit('validate')
  },
})

const archiveDaysValue = computed({
  get: () => props.archiveDays,
  set: (value: number) => {
    emit('update:archiveDays', value)
    // 值变化时触发验证
    emit('validate')
  },
})

const cloudSpaceThresholdValue = computed({
  get: () => props.cloudSpaceThreshold,
  set: (value: number) => {
    emit('update:cloudSpaceThreshold', value)
    // 值变化时触发验证
    emit('validate')
  },
})

// 验证逻辑
const validateArchiveDays = (days: number): string | undefined => {
  if (props.modelValue !== 'TIME') return undefined

  if (!Number.isInteger(days) || days <= 0) {
    return '保留天数必须是正整数'
  }

  if (days > 365) {
    return '保留天数不能超过365天'
  }

  return undefined
}

const validateCloudSpaceThreshold = (threshold: number): string | undefined => {
  if (props.modelValue !== 'SPACE') return undefined

  if (!Number.isInteger(threshold) || threshold < 1 || threshold > 100) {
    return '空间阈值必须在 1-100 之间'
  }

  return undefined
}

// 计算验证错误（如果没有传入外部错误，使用内部验证）
const archiveDaysValidationError = computed(() => {
  return props.archiveDaysError || validateArchiveDays(props.archiveDays)
})

const cloudSpaceThresholdValidationError = computed(() => {
  return props.cloudSpaceThresholdError || validateCloudSpaceThreshold(props.cloudSpaceThreshold)
})

// 检查是否有验证错误
const hasValidationErrors = computed(() => {
  return !!(archiveDaysValidationError.value || cloudSpaceThresholdValidationError.value)
})

// 暴露验证方法给父组件
defineExpose({
  validate: () => {
    const archiveDaysError = validateArchiveDays(props.archiveDays)
    const cloudSpaceThresholdError = validateCloudSpaceThreshold(props.cloudSpaceThreshold)

    return {
      isValid: !archiveDaysError && !cloudSpaceThresholdError,
      errors: {
        archiveDays: archiveDaysError,
        cloudSpaceThreshold: cloudSpaceThresholdError,
      },
    }
  },
  hasErrors: hasValidationErrors,
})

// 归档模式选项配置
const archiveModeOptions = [
  {
    value: 'DISABLED' as const,
    label: '关闭归档',
    description: '不执行任何归档操作，所有照片保留在同步文件夹中',
    showConfig: false,
  },
  {
    value: 'TIME' as const,
    label: '根据时间归档',
    description: '自动归档超过指定天数的照片，释放同步文件夹空间',
    showConfig: true,
    configType: 'days',
  },
  {
    value: 'SPACE' as const,
    label: '根据空间归档',
    description: '当云端空间不足时，自动归档旧照片，直到空间使用率低于阈值',
    showConfig: true,
    configType: 'threshold',
  },
]
</script>

<template>
  <div class="space-y-3">
    <label class="block text-xs font-medium text-slate-500">归档模式</label>

    <div class="space-y-3">
      <div
        v-for="option in archiveModeOptions"
        :key="option.value"
        class="border border-slate-200 rounded-lg hover:border-slate-300 transition-colors"
        :class="{
          'border-blue-500 bg-blue-50': selectedMode === option.value,
          'border-slate-200': selectedMode !== option.value,
        }"
      >
        <!-- 模式选择区域 -->
        <div class="flex items-start gap-3 p-3 cursor-pointer" @click="selectedMode = option.value">
          <RadioButton
            :value="option.value"
            v-model="selectedMode"
            :inputId="`archive-mode-${option.value}`"
            class="mt-0.5"
          />

          <div class="flex-1 min-w-0">
            <label
              :for="`archive-mode-${option.value}`"
              class="text-sm font-medium text-slate-700 cursor-pointer block"
            >
              {{ option.label }}
            </label>
            <p class="text-xs text-slate-500 mt-1">
              {{ option.description }}
            </p>
          </div>
        </div>

        <!-- 配置区域 -->
        <div
          v-if="selectedMode === option.value && option.showConfig"
          class="px-3 pb-3 border-t border-slate-200 pt-3 mt-3"
        >
          <!-- 时间模式配置 -->
          <div v-if="option.configType === 'days'" class="space-y-2">
            <label class="block text-xs font-medium text-slate-500">保留天数</label>
            <div class="flex items-center gap-2">
              <InputNumber
                v-model="archiveDaysValue"
                :min="1"
                :max="365"
                showButtons
                buttonLayout="horizontal"
                inputClass="w-16 text-center"
                class="flex-shrink-0"
                :class="{ 'p-invalid': archiveDaysValidationError }"
              >
                <template #incrementbuttonicon>
                  <span class="pi pi-plus" />
                </template>
                <template #decrementbuttonicon>
                  <span class="pi pi-minus" />
                </template>
              </InputNumber>
              <span class="text-xs text-slate-500">天</span>
            </div>
            <div v-if="archiveDaysValidationError" class="text-xs text-red-500">
              {{ archiveDaysValidationError }}
            </div>
            <div class="text-[10px] text-slate-400">归档早于当前日期减去保留天数的照片</div>
          </div>

          <!-- 空间模式配置 -->
          <div v-if="option.configType === 'threshold'" class="space-y-2">
            <label class="block text-xs font-medium text-slate-500">空间阈值</label>
            <div class="flex items-center gap-2">
              <InputNumber
                v-model="cloudSpaceThresholdValue"
                :min="1"
                :max="100"
                showButtons
                buttonLayout="horizontal"
                inputClass="w-16 text-center"
                class="flex-shrink-0"
                :class="{ 'p-invalid': cloudSpaceThresholdValidationError }"
              >
                <template #incrementbuttonicon>
                  <span class="pi pi-plus" />
                </template>
                <template #decrementbuttonicon>
                  <span class="pi pi-minus" />
                </template>
              </InputNumber>
              <span class="text-xs text-slate-500">%</span>
            </div>
            <div v-if="cloudSpaceThresholdValidationError" class="text-xs text-red-500">
              {{ cloudSpaceThresholdValidationError }}
            </div>
            <div class="text-[10px] text-slate-400">
              当云端空间使用率超过此阈值时，自动归档旧照片
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="text-[10px] text-slate-400">
      <strong>说明：</strong
      >归档操作会将照片从同步文件夹移动到归档文件夹，并可选择删除云端副本以释放空间。
    </div>
  </div>
</template>
