<script setup lang="ts">
import { ref } from 'vue'
import Button from 'primevue/button'

const props = defineProps<{
  title: string
  subtitle?: string
  defaultCollapsed?: boolean
  isFirst?: boolean
}>()

const isCollapsed = ref(props.defaultCollapsed ?? false)

function toggleCollapse() {
  isCollapsed.value = !isCollapsed.value
}
</script>

<template>
  <div class="pb-4 border-b border-slate-200 dark:border-slate-700 last:border-b-0">
    <!-- 标题和按钮 -->
    <div class="flex items-center justify-between pl-4 pt-4" :class="{ 'pb-4': !isCollapsed }">
      <div class="flex items-center gap-2">
        <div class="flex items-baseline gap-2">
          <span class="text-lg font-semibold text-slate-700 dark:text-slate-200 leading-none">
            {{ title }}
          </span>
          <span v-if="subtitle" class="text-sm text-slate-500 dark:text-slate-400">
            {{ subtitle }}
          </span>
        </div>
        <Button
          :icon="isCollapsed ? 'pi pi-chevron-right' : 'pi pi-chevron-down'"
          severity="secondary"
          text
          rounded
          size="small"
          @click="toggleCollapse"
          class="w-8 h-8 flex-shrink-0"
          :aria-label="isCollapsed ? '展开' : '折叠'"
        />
      </div>
      <div class="flex items-center gap-2">
        <slot name="actions" />
      </div>
    </div>

    <!-- 内容区域 -->
    <div v-show="!isCollapsed" class="pl-4">
      <slot />
    </div>
  </div>
</template>
