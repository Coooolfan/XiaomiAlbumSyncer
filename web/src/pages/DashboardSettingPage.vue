<script setup lang="ts">
import SystemConfigCard from '@/components/settings/SystemConfigCard.vue'
import AppearanceCard from '@/components/settings/AppearanceCard.vue'
import PassTokenCard from '@/components/settings/PassTokenCard.vue'
import PasswordCard from '@/components/settings/PasswordCard.vue'
import PasskeyCard from '@/components/settings/PasskeyCard.vue'
import ImportV2Card from '@/components/settings/ImportV2Card.vue'
import OpenSourceCard from '@/components/settings/OpenSourceCard.vue'
</script>

<template>
  <div class="max-w-3xl mx-auto px-4 py-8">
    <SystemConfigCard />
    <AppearanceCard />
    <PassTokenCard />
    <PasswordCard />
    <PasskeyCard />
    <ImportV2Card />
    <OpenSourceCard />
  </div>
</template>
