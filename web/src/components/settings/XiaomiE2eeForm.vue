<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import Tag from 'primevue/tag'
import { api } from '@/ApiInstance'
import type { XiaomiE2eeStatus } from '@/__generated/model/static'

const props = defineProps<{ accountId: number }>()
const emit = defineEmits<{ updated: [status: XiaomiE2eeStatus] }>()
const { t } = useI18n()
const status = ref<XiaomiE2eeStatus>()
const busy = ref(false)
const error = ref('')
const code = ref('')
const sent = ref(false)
const cooldown = ref(0)
let timer: ReturnType<typeof setInterval> | undefined
let disposed = false

async function run(action: () => Promise<void>) {
  busy.value = true
  error.value = ''
  try {
    await action()
  } catch (e) {
    if (!disposed) error.value = e instanceof Error ? e.message : String(e)
  } finally {
    if (!disposed) busy.value = false
  }
}

function update(value: XiaomiE2eeStatus) {
  if (disposed) return
  status.value = value
  emit('updated', value)
}

function refresh() {
  return run(async () => update(await api.xiaomiE2eeController.status({ id: props.accountId })))
}

function send() {
  return run(async () => {
    await api.xiaomiE2eeController.push({ id: props.accountId })
    if (disposed) return
    sent.value = true
    code.value = ''
    cooldown.value = 60
    clearInterval(timer)
    timer = setInterval(() => {
      cooldown.value--
      if (cooldown.value <= 0) clearInterval(timer)
    }, 1000)
  })
}

function verify() {
  if (!/^[0-9]{6}$/.test(code.value)) {
    error.value = t('tokens.e2ee.invalidCode')
    return
  }
  return run(async () => {
    update(
      await api.xiaomiE2eeController.verify({ id: props.accountId, body: { code: code.value } }),
    )
    code.value = ''
    sent.value = false
  })
}

onMounted(refresh)
onUnmounted(() => {
  disposed = true
  code.value = ''
  clearInterval(timer)
})
</script>

<template>
  <form class="flex flex-col gap-4" @submit.prevent="verify">
    <p class="text-sm text-slate-500 dark:text-slate-400">{{ t('tokens.e2ee.description') }}</p>
    <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>
    <div v-if="status" class="flex flex-wrap items-center gap-2">
      <Tag
        :value="t(`tokens.e2ee.states.${status.state}`)"
        :severity="status.state === 'READY' ? 'success' : 'info'"
      />
      <span v-if="status.cachedKeyCount" class="text-sm text-slate-500">
        {{ t('tokens.e2ee.cachedKeys', { count: status.cachedKeyCount }) }}
      </span>
    </div>
    <Message v-if="status?.state === 'READY'" severity="success" :closable="false">
      {{ t('tokens.e2ee.readyHint') }}
    </Message>
    <template v-if="status && status.state !== 'DISABLED' && status.state !== 'SESSION_EXPIRED'">
      <p class="text-sm">{{ t('tokens.e2ee.codeHint') }}</p>
      <Button
        type="button"
        :label="cooldown ? t('tokens.e2ee.resendIn', { seconds: cooldown }) : t('tokens.e2ee.send')"
        :disabled="busy || cooldown > 0"
        severity="secondary"
        @click="send"
      />
      <template v-if="sent">
        <label for="xiaomi-e2ee-code">{{ t('tokens.e2ee.code') }}</label>
        <InputText
          id="xiaomi-e2ee-code"
          v-model="code"
          inputmode="numeric"
          autocomplete="one-time-code"
          maxlength="6"
          pattern="[0-9]{6}"
          required
          :disabled="busy"
        />
        <Button type="submit" :label="t('tokens.e2ee.verify')" :loading="busy" />
      </template>
    </template>
    <Button type="button" :label="t('tokens.e2ee.refresh')" text :loading="busy" @click="refresh" />
  </form>
</template>
