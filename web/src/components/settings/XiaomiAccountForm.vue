<script setup lang="ts">
import { ref, computed, onUnmounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import Button from 'primevue/button'
import Textarea from 'primevue/textarea'
import InputText from 'primevue/inputtext'
import OptionCard from '@/components/OptionCard.vue'
import { useToast } from 'primevue/usetoast'
import type { ProviderAccountDto } from '@/__generated/model/dto'
import type { QrLoginSessionView } from '@/__generated/model/static'
import type { QrLoginStatus } from '@/__generated/model/enums'
import { api } from '@/ApiInstance'
import { useAccountsStore } from '@/stores/accounts'

type Account = ProviderAccountDto['ProviderAccountController/DEFAULT_PROVIDER_ACCOUNT']
const props = defineProps<{ account?: Account; isInsecureContext: boolean }>()
const emit = defineEmits<{ completed: []; cancel: []; back: []; title: [value: string] }>()
const accountsStore = useAccountsStore()
const { t } = useI18n()
const toast = useToast()
const isEditMode = !!props.account
const saving = ref(false)
const dialogStep = ref<'method' | 'qr' | 'manual'>(isEditMode ? 'manual' : 'method')
const slideDir = ref<'slide-left' | 'slide-right'>('slide-left')
const form = ref({
  id: props.account?.id ?? 0,
  userId: props.account?.userId ?? '',
  nickname: props.account?.nickname ?? '',
  passToken: '',
})
const title = computed(() =>
  isEditMode
    ? t('tokens.account.edit')
    : dialogStep.value === 'qr'
      ? t('tokens.account.qrTitle')
      : dialogStep.value === 'manual'
        ? t('tokens.account.methodManualTitle')
        : t('tokens.account.providerXiaomi'),
)
watch(title, (value) => emit('title', value), { immediate: true })
// 扫码登录
const qrSession = ref<QrLoginSessionView | null>(null)
const qrStatus = ref<QrLoginStatus | 'CREATING'>('CREATING')
const qrError = ref('')
let qrTimer: ReturnType<typeof setTimeout> | undefined
// 每次开始/停止轮询自增，使过期的异步回调自行作废
let qrSeq = 0
watch(dialogStep, (step) => {
  if (step !== 'qr') stopQrPolling()
})
onUnmounted(stopQrPolling)

function stopQrPolling() {
  qrSeq++
  clearTimeout(qrTimer)
  qrTimer = undefined
}

function selectQr() {
  slideDir.value = 'slide-left'
  dialogStep.value = 'qr'
  refreshQrSession()
}

function selectManual() {
  slideDir.value = 'slide-left'
  dialogStep.value = 'manual'
}

function goBack() {
  if (dialogStep.value === 'method') emit('back')
  else {
    slideDir.value = 'slide-right'
    dialogStep.value = 'method'
  }
}

async function refreshQrSession() {
  stopQrPolling()
  qrSession.value = null
  qrStatus.value = 'CREATING'
  qrError.value = ''
  const seq = qrSeq
  try {
    const session = await api.xiaomiQrLoginController.create()
    if (seq !== qrSeq) return
    qrSession.value = session
    qrStatus.value = 'WAITING'
    scheduleQrPoll(session.sessionId, seq)
  } catch (e) {
    if (seq !== qrSeq) return
    qrStatus.value = 'FAILED'
    qrError.value = e instanceof Error ? e.message : String(e)
  }
}

// 上一次查询结束后再安排下一次，避免慢请求叠加
function scheduleQrPoll(sessionId: string, seq: number) {
  qrTimer = setTimeout(async () => {
    try {
      const result = await api.xiaomiQrLoginController.status({ sessionId })
      if (seq !== qrSeq) return
      qrStatus.value = result.status
      if (result.status === 'WAITING') {
        scheduleQrPoll(sessionId, seq)
        return
      }

      stopQrPolling()
      if (result.status === 'SUCCESS') {
        toast.add({
          severity: 'success',
          summary: t('common.toast.success'),
          detail: t('tokens.account.qrLoggedIn', { name: result.nickname || result.userId }),
          life: 3000,
        })
        emit('completed')
      } else if (result.status === 'FAILED') {
        qrError.value = result.error ?? ''
      }
    } catch (e) {
      if (seq !== qrSeq) return
      stopQrPolling()
      qrStatus.value = 'FAILED'
      qrError.value = e instanceof Error ? e.message : String(e)
    }
  }, 2000)
}

async function onSave() {
  if (!form.value.userId) {
    toast.add({
      severity: 'warn',
      summary: t('common.toast.warn'),
      detail: t('tokens.account.enterUserId'),
      life: 2500,
    })
    return
  }
  if (!form.value.passToken) {
    toast.add({
      severity: 'warn',
      summary: t('common.toast.warn'),
      detail: t('tokens.account.enterPassToken'),
      life: 2500,
    })
    return
  }

  const finalNickname = form.value.nickname || form.value.userId

  try {
    saving.value = true
    if (isEditMode) {
      await accountsStore.updateXiaomiAccount(form.value.id, {
        userId: form.value.userId,
        nickname: finalNickname,
        passToken: form.value.passToken,
      })
      toast.add({
        severity: 'success',
        summary: t('common.toast.success'),
        detail: t('tokens.account.updated'),
        life: 2000,
      })
    } else {
      await accountsStore.createXiaomiAccount({
        userId: form.value.userId,
        nickname: finalNickname,
        passToken: form.value.passToken,
      })
      toast.add({
        severity: 'success',
        summary: t('common.toast.success'),
        detail: t('tokens.account.added'),
        life: 2000,
      })
    }
    emit('completed')
  } catch (e) {
    const detail = e instanceof Error ? e.message : String(e) || t('common.toast.saveFailed')
    toast.add({ severity: 'error', summary: t('common.toast.error'), detail, life: 3000 })
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div>
    <div class="-mx-1 overflow-x-hidden px-1">
      <Transition :name="slideDir" mode="out-in">
        <!-- 添加方式选择 -->
        <div v-if="dialogStep === 'method'" key="method" class="flex flex-col gap-3 pt-2">
          <OptionCard
            icon="pi-qrcode"
            :label="t('tokens.account.methodQrTitle')"
            :desc="t('tokens.account.methodQrDesc')"
            @click="selectQr"
          />
          <OptionCard
            icon="pi-key"
            :label="t('tokens.account.methodManualTitle')"
            :desc="t('tokens.account.methodManualDesc')"
            @click="selectManual"
          />
          <div
            class="rounded-md bg-slate-50 dark:bg-slate-800 text-[13px] text-slate-500 dark:text-slate-400 px-3 py-2"
          >
            {{ t('tokens.account.adpHint') }}
          </div>
        </div>

        <!-- 扫码登录 -->
        <div v-else-if="dialogStep === 'qr'" key="qr" class="flex flex-col items-center gap-4 pt-2">
          <div
            class="w-[240px] h-[240px] flex items-center justify-center rounded-lg bg-slate-100 dark:bg-slate-800 overflow-hidden"
          >
            <i
              v-if="qrStatus === 'CREATING'"
              class="pi pi-spin pi-spinner text-3xl text-slate-500 dark:text-slate-400"
            ></i>
            <img
              v-else-if="qrSession"
              :src="qrSession.qrUrl"
              :alt="t('tokens.account.qrTitle')"
              class="w-full h-full object-contain"
              :class="{ 'opacity-30': qrStatus === 'EXPIRED' }"
            />
            <i v-else class="pi pi-exclamation-triangle text-3xl text-red-400"></i>
          </div>

          <div class="text-center text-sm">
            <p v-if="qrStatus === 'CREATING'" class="text-slate-500 dark:text-slate-400">
              {{ t('tokens.account.qrGenerating') }}
            </p>
            <template v-else-if="qrStatus === 'WAITING'">
              <p class="font-medium text-slate-700 dark:text-slate-200">
                {{ t('tokens.account.qrWaiting') }}
              </p>
              <p class="text-[13px] text-slate-500 dark:text-slate-400 mt-1">
                {{ t('tokens.account.qrTip') }}
              </p>
            </template>
            <p v-else-if="qrStatus === 'EXPIRED'" class="text-amber-600 dark:text-amber-400">
              {{ t('tokens.account.qrExpired') }}
            </p>
            <p v-else-if="qrStatus === 'FAILED'" class="text-red-600 dark:text-red-400">
              {{ t('tokens.account.qrFailed') }}<template v-if="qrError">：{{ qrError }}</template>
            </p>
          </div>
        </div>

        <!-- PassToken 表单 -->
        <div v-else key="manual" class="flex flex-col gap-4 pt-2">
          <div class="flex gap-3">
            <div class="flex-1">
              <label class="block text-sm font-medium text-slate-700 dark:text-slate-200 mb-1">{{
                t('tokens.account.userId')
              }}</label>
              <InputText
                v-model="form.userId"
                :placeholder="t('tokens.account.userIdPlaceholder')"
                class="w-full"
              />
            </div>
            <div class="flex-1">
              <label class="block text-sm font-medium text-slate-700 dark:text-slate-200 mb-1">{{
                t('tokens.account.nickname')
              }}</label>
              <InputText
                v-model="form.nickname"
                :placeholder="t('tokens.account.nicknamePlaceholder')"
                class="w-full"
              />
            </div>
          </div>

          <div>
            <label class="block text-sm font-medium text-slate-700 dark:text-slate-200 mb-1">{{
              t('tokens.account.passToken')
            }}</label>
            <Textarea
              v-model="form.passToken"
              rows="5"
              :placeholder="
                isEditMode
                  ? t('tokens.account.passTokenPlaceholderEdit')
                  : t('tokens.account.passTokenPlaceholder')
              "
              class="w-full"
            />
            <p v-if="isEditMode" class="text-[13px] text-slate-500 dark:text-slate-400 mt-1">
              {{ t('tokens.account.passTokenNote') }}
            </p>
          </div>

          <div v-if="isInsecureContext" class="text-[13px] text-red-600 dark:text-red-300">
            {{ t('tokens.account.insecureShort') }}
          </div>
        </div>
      </Transition>
    </div>
    <div class="flex items-center w-full mt-4">
      <Button
        v-if="!isEditMode"
        :label="t('common.action.back')"
        icon="pi pi-arrow-left"
        severity="secondary"
        text
        @click="goBack"
      />
      <div class="flex items-center justify-end gap-2 flex-1">
        <Button
          :label="t('common.action.cancel')"
          severity="secondary"
          text
          @click="emit('cancel')"
        />
        <Button
          v-if="dialogStep === 'qr' && (qrStatus === 'EXPIRED' || qrStatus === 'FAILED')"
          :label="t('tokens.account.qrRetry')"
          @click="refreshQrSession"
        />
        <Button
          v-else-if="dialogStep === 'manual'"
          :label="t('common.action.save')"
          :loading="saving"
          @click="onSave"
        />
      </div>
    </div>
  </div>
</template>
