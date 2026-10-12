<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import Tag from 'primevue/tag'
import type { CloudProvider } from '@/__generated/model/enums'

const props = defineProps<{ provider?: CloudProvider; fullName?: boolean }>()
const { t } = useI18n()
const isApple = computed(() => props.provider === 'ICLOUD')
const label = computed(() =>
  props.fullName
    ? t(isApple.value ? 'tokens.account.providerIcloud' : 'tokens.account.providerXiaomi')
    : isApple.value
      ? 'Apple'
      : 'Xiaomi',
)
</script>

<template>
  <Tag
    :value="label"
    severity="secondary"
    :class="isApple ? 'provider-apple' : 'provider-xiaomi'"
  />
</template>
