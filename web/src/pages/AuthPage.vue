<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import Card from 'primevue/card'
import Password from 'primevue/password'
import Button from 'primevue/button'
import Divider from 'primevue/divider'
import { api } from '../ApiInstance'
import { isWebAuthnSupported, hasAvailablePasskeys, authenticateWithPasskey } from '@/utils/passkey'

const router = useRouter()
const { t } = useI18n()

const checkingInit = ref(true)
const isInit = ref(true) // 服务端是否已初始化
const password = ref('')
const confirmPassword = ref('')
const submitting = ref(false)
const errorMsg = ref<string | null>(null)

// Passkey 相关状态
const webAuthnSupported = ref(false)
const passkeyAvailable = ref(false)
const passkeyLoading = ref(false)

const modeTitle = computed(() => (isInit.value ? t('auth.login') : t('auth.initTitle')))
const submitLabel = computed(() => {
  if (!isInit.value) return t('auth.password.setAndEnter')
  return passkeyAvailable.value ? t('auth.password.signIn') : t('auth.login')
})

// 是否处于安全上下文
const isSecureCtx = computed(() => typeof window !== 'undefined' && window.isSecureContext)

async function checkInit() {
  checkingInit.value = true
  errorMsg.value = null
  try {
    const resp = await api.systemConfigController.isInit()
    isInit.value = resp.init

    // 检查 WebAuthn 支持和可用性
    if (isInit.value) {
      webAuthnSupported.value = isWebAuthnSupported()
      if (webAuthnSupported.value) {
        passkeyAvailable.value = await hasAvailablePasskeys()
      }
    }
  } catch (e) {
    // 后端未准备好或接口异常时，默认为未初始化，允许用户设置密码
    isInit.value = false
    errorMsg.value = e instanceof Error ? e.message : String(e)
  } finally {
    checkingInit.value = false
  }
}

async function handlePasswordSubmit() {
  errorMsg.value = null

  if (!password.value || (!isInit.value && !confirmPassword.value)) {
    errorMsg.value = t('auth.error.incompletePassword')
    return
  }
  if (!isInit.value && password.value !== confirmPassword.value) {
    errorMsg.value = t('auth.error.passwordMismatch')
    return
  }

  submitting.value = true
  try {
    if (!isInit.value) {
      // 初始化（注册）
      // 1. 创建配置（设置初始密码）
      await api.systemConfigController.initConfig({ body: { password: password.value } })
      // 2. 随后登录，便于服务端通过 Set-Cookie 设置会话
      await api.tokenController.login({ password: password.value })
    } else {
      // 登录
      await api.tokenController.login({ password: password.value })
    }

    // 成功后跳转 Dashboard
    router.replace('/dashboard/schedule')
  } catch (e) {
    console.error('Auth error:', e)
    const msg = e instanceof Error ? e.message : String(e)
    if (msg.includes('Auth failed')) {
      errorMsg.value = t('auth.error.wrongPassword')
    } else {
      errorMsg.value = msg
    }
  } finally {
    submitting.value = false
  }
}

async function handlePasskeyLogin() {
  errorMsg.value = null
  passkeyLoading.value = true

  try {
    await authenticateWithPasskey()
    router.replace('/dashboard/schedule')
  } catch (e) {
    console.error('Passkey auth error:', e)
    const msg = e instanceof Error ? e.message : String(e)
    if (msg.includes('The operation either timed out or was not allowed')) {
      errorMsg.value = t('auth.error.passkeyCancelled')
    } else if (msg.includes('No Passkey registered')) {
      errorMsg.value = t('auth.error.noPasskey')
    } else {
      errorMsg.value = msg
    }
  } finally {
    passkeyLoading.value = false
  }
}

onMounted(() => {
  checkInit()
})
</script>

<template>
  <div class="min-h-screen flex items-center justify-center p-4 relative z-10">
    <transition
      enter-active-class="transition ease-out duration-500"
      enter-from-class="opacity-0 translate-y-4 scale-[0.98]"
      enter-to-class="opacity-100 translate-y-0 scale-100"
    >
      <Card
        v-if="!checkingInit"
        class="w-full max-w-md shadow-xl ring-1 ring-black/5 dark:ring-white/10 rounded-2xl overflow-hidden bg-white/80 dark:bg-slate-900/80 backdrop-blur"
      >
        <template #title>
          <div class="text-xl font-semibold tracking-tight">
            {{ modeTitle }}
          </div>
        </template>

        <template #content>
          <div class="space-y-6">
            <!-- Passkey 登录按钮（仅在已初始化且支持时显示） -->
            <div v-if="isInit && webAuthnSupported && passkeyAvailable">
              <Button
                :label="t('auth.passkey.signIn')"
                icon="pi pi-shield"
                class="w-full !py-3 !text-base !font-semibold"
                :loading="passkeyLoading"
                @click="handlePasskeyLogin"
              />

              <Divider>
                <span class="text-xs text-slate-400 dark:text-slate-500">{{
                  t('auth.passkey.orPassword')
                }}</span>
              </Divider>
            </div>

            <div v-if="!isInit" class="text-sm text-slate-500 dark:text-slate-400">
              {{ t('auth.firstUseHint') }}
            </div>

            <div class="space-y-2">
              <label class="text-sm font-medium text-slate-700 dark:text-slate-200">{{
                t('auth.password.label')
              }}</label>
              <Password
                v-model="password"
                :feedback="false"
                toggleMask
                :input-class="'w-full p-inputtext p-component'"
                class="w-full"
                :placeholder="t('auth.password.placeholder')"
                @keyup.enter="handlePasswordSubmit"
              />
            </div>

            <div v-if="!isInit" class="space-y-2">
              <label class="text-sm font-medium text-slate-700 dark:text-slate-200">{{
                t('auth.password.confirmLabel')
              }}</label>
              <Password
                v-model="confirmPassword"
                :feedback="false"
                toggleMask
                :input-class="'w-full p-inputtext p-component'"
                class="w-full"
                :placeholder="t('auth.password.confirmPlaceholder')"
                @keyup.enter="handlePasswordSubmit"
              />
            </div>

            <transition
              enter-active-class="transition ease-out duration-300"
              enter-from-class="opacity-0 -translate-y-1"
              enter-to-class="opacity-100 translate-y-0"
              leave-active-class="transition ease-in duration-200"
              leave-from-class="opacity-100"
              leave-to-class="opacity-0"
            >
              <div
                v-if="errorMsg"
                class="text-sm text-red-600 dark:text-red-300 bg-red-50 dark:bg-red-950/40 border border-red-100 dark:border-red-900/70 rounded-md p-2"
              >
                {{ errorMsg }}
              </div>
            </transition>

            <Button
              :label="submitLabel"
              class="w-full !py-3 !text-base !font-semibold transition-transform hover:scale-[1.01] active:scale-[0.99]"
              :loading="submitting"
              :severity="isInit && passkeyAvailable ? 'secondary' : undefined"
              @click="handlePasswordSubmit"
            />

            <!-- WebAuthn 不支持提示 -->
            <div
              v-if="isInit && !webAuthnSupported"
              class="text-sm text-amber-700 dark:text-amber-300 bg-amber-50 dark:bg-amber-950/35 border border-amber-200 dark:border-amber-900/60 rounded-md p-2"
            >
              {{ t('auth.passkey.unsupported') }}
            </div>

            <div
              v-if="!isSecureCtx"
              class="text-sm text-red-700 dark:text-red-300 bg-red-50 dark:bg-red-950/40 border border-red-200 dark:border-red-900/70 rounded-md p-2"
            >
              {{ t('auth.insecureContextWarning') }}
            </div>

            <div class="text-center text-sm text-slate-500 dark:text-slate-400">
              <template v-if="isInit">{{ t('auth.resetPasswordHint') }}</template>
              <template v-else>{{ t('auth.alreadyInitializedHint') }}</template>
            </div>
          </div>
        </template>
      </Card>
    </transition>

    <transition
      enter-active-class="transition ease-out duration-500"
      enter-from-class="opacity-0"
      enter-to-class="opacity-100"
    >
      <div v-if="checkingInit" class="flex items-center gap-3 text-slate-500 dark:text-slate-400">
        <span
          class="inline-block h-2 w-2 rounded-full bg-slate-300 dark:bg-slate-600 animate-pulse"
        ></span>
        <span
          class="inline-block h-2 w-2 rounded-full bg-slate-300 dark:bg-slate-600 animate-pulse [animation-delay:120ms]"
        ></span>
        <span
          class="inline-block h-2 w-2 rounded-full bg-slate-300 dark:bg-slate-600 animate-pulse [animation-delay:240ms]"
        ></span>
        <span class="text-sm">{{ t('common.status.loading') }}</span>
      </div>
    </transition>
  </div>
</template>

<style scoped>
:deep(.p-password) {
  width: 100%;
}

:deep(.p-password-input) {
  width: 100%;
}
</style>
