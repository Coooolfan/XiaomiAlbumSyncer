<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import Tag from 'primevue/tag'
import type { CloudProvider } from '@/__generated/model/enums'
import { cloudProviderRules } from '@/utils/cloudProviders'

const props = defineProps<{ provider?: CloudProvider; fullName?: boolean }>()
const { t } = useI18n()
const source = computed(() => (props.provider ? cloudProviderRules[props.provider] : undefined))
</script>

<template>
  <Tag
    v-if="source"
    :value="fullName ? t(source.labelKey) : source.shortLabel"
    severity="secondary"
    :class="source.tagClass"
  />
</template>
