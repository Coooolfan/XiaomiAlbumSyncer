<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import InputText from 'primevue/inputtext'
import Select from 'primevue/select'
import Button from 'primevue/button'
import Message from 'primevue/message'
import { api } from '@/ApiInstance'
import { useAccountsStore } from '@/stores/accounts'

const props = defineProps<{ accountId?: number; appleId?: string; nickname?: string }>()
const emit = defineEmits<{ completed: [accountId: number] }>()
const { t } = useI18n()
const accounts = useAccountsStore()
const appleId = ref(props.appleId ?? '')
const nickname = ref(props.nickname ?? '')
const password = ref('')
const domain = ref('com')
const code = ref('')
const accountId = ref(props.accountId)
const state = ref('LOGIN')
const busy = ref(false)
const error = ref('')
const regions = [
  { label: t('tokens.icloud.global'), value: 'com' },
  { label: t('tokens.icloud.china'), value: 'cn' },
]

onMounted(async () => {
  if (!props.accountId) return
  try {
    const result = await api.icloudController.status({ id: props.accountId })
    domain.value = result.domain
    state.value = result.state === 'MFA_REQUIRED' ? 'MFA_REQUIRED' : 'LOGIN'
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  }
})

async function submit() {
  busy.value = true
  error.value = ''
  try {
    const result =
      state.value === 'MFA_REQUIRED' && accountId.value
        ? await api.icloudController.verify({ id: accountId.value, body: { code: code.value } })
        : await api.icloudController.login({
            body: {
              appleId: appleId.value,
              password: password.value,
              nickname: nickname.value,
              domain: domain.value,
              accountId: props.accountId,
            },
          })
    accountId.value = result.accountId
    state.value = result.state
    password.value = ''
    await accounts.refreshAccounts()
    if (result.state === 'READY') emit('completed', result.accountId)
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <form class="flex flex-col gap-4 pt-2" @submit.prevent="submit">
    <Message severity="info" :closable="false">{{ t('tokens.icloud.requirements') }}</Message>
    <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>
    <template v-if="state === 'MFA_REQUIRED'">
      <p class="text-sm">{{ t('tokens.icloud.codeHint') }}</p>
      <label for="icloud-code">{{ t('tokens.icloud.code') }}</label>
      <InputText
        id="icloud-code"
        v-model="code"
        inputmode="numeric"
        autocomplete="one-time-code"
        maxlength="6"
        required
        :disabled="busy"
      />
      <Button type="submit" :label="t('tokens.icloud.verify')" :loading="busy" />
      <Button
        type="button"
        :label="t('tokens.icloud.restart')"
        text
        :disabled="busy"
        @click="state = 'LOGIN'"
      />
    </template>
    <template v-else>
      <label for="icloud-id">{{ t('tokens.icloud.appleId') }}</label>
      <InputText
        id="icloud-id"
        v-model="appleId"
        autocomplete="username"
        required
        :disabled="!!props.accountId || busy"
      />
      <label for="icloud-password">{{ t('tokens.icloud.password') }}</label>
      <InputText
        id="icloud-password"
        v-model="password"
        type="password"
        autocomplete="current-password"
        required
        :disabled="busy"
      />
      <label for="icloud-nickname">{{ t('tokens.account.nickname') }}</label>
      <InputText id="icloud-nickname" v-model="nickname" :disabled="busy" />
      <label for="icloud-region">{{ t('tokens.icloud.region') }}</label>
      <Select
        id="icloud-region"
        v-model="domain"
        :options="regions"
        option-label="label"
        option-value="value"
        :disabled="busy"
      />
      <p class="text-sm text-slate-500">{{ t('tokens.icloud.renewHint') }}</p>
      <Button type="submit" :label="t('tokens.icloud.login')" :loading="busy" />
    </template>
  </form>
</template>
