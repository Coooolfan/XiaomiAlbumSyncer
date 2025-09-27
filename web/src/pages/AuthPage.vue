<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import Card from 'primevue/card'
import Password from 'primevue/password'
import Button from 'primevue/button'
import Divider from 'primevue/divider'
import { api } from '../ApiInstance'

const router = useRouter()

const checkingInit = ref(true)
const isInit = ref(true) // 服务端是否已初始化
const password = ref('')
const confirmPassword = ref('')
const submitting = ref(false)
const errorMsg = ref<string | null>(null)

const modeTitle = computed(() => (isInit.value ? '登录' : '初始化 / 注册'))
const submitLabel = computed(() => (isInit.value ? '登录' : '设置密码并进入'))

async function checkInit() {
  checkingInit.value = true
  errorMsg.value = null
  try {
    const resp = await api.systemConfigController.isInit()
    isInit.value = !!resp.init
  } catch (e) {
    // 后端未准备好或接口异常时，默认为未初始化，允许用户设置密码
    isInit.value = false
    errorMsg.value = e instanceof Error ? e.message : String(e)
  } finally {
    checkingInit.value = false
  }
}

async function handleSubmit() {
  errorMsg.value = null

  if (!password.value || (!isInit.value && !confirmPassword.value)) {
    errorMsg.value = '请完整填写密码'
    return
  }
  if (!isInit.value && password.value !== confirmPassword.value) {
    errorMsg.value = '两次密码输入不一致'
    return
  }

  submitting.value = true
  try {
    if (!isInit.value) {
      // 初始化（注册）
      // 1. 创建配置（设置初始密码）
      await api.systemConfigController.initConfig({ body: { password: password.value } })
      // 2. 随后登录，便于服务端通过 Set-Cookie 设置会话
      await api.tokenController.login({ login: password.value })
    } else {
      // 登录
      await api.tokenController.login({ login: password.value })
    }

    // 成功后跳转 Dashboard
    router.replace('/dashboard/schedule')
  } catch (e) {
    console.error('Auth error:', e)
    errorMsg.value = e instanceof Error ? e.message : String(e)
  } finally {
    submitting.value = false
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
        class="w-full max-w-md shadow-xl ring-1 ring-black/5 rounded-2xl overflow-hidden bg-white/80 backdrop-blur"
      >
        <template #title>
          <div class="flex items-center justify-between">
            <div class="text-xl font-semibold tracking-tight">
              {{ modeTitle }}
            </div>
            <span
              class="text-xs px-2 py-1 rounded-full bg-blue-50 text-blue-600 border border-blue-100"
            >
              Cookie 鉴权
            </span>
          </div>
        </template>

        <template #content>
          <div class="space-y-6">
            <div v-if="!isInit" class="text-sm text-slate-500">
              首次使用，请设置登录密码。该密码仅保存在后端。
            </div>

            <div class="space-y-2">
              <label class="text-sm font-medium text-slate-700">密码</label>
              <Password
                v-model="password"
                :feedback="false"
                toggleMask
                :input-class="'w-full p-inputtext p-component'"
                class="w-full"
                placeholder="请输入密码"
                @keyup.enter="handleSubmit"
              />
            </div>

            <div v-if="!isInit" class="space-y-2">
              <label class="text-sm font-medium text-slate-700">确认密码</label>
              <Password
                v-model="confirmPassword"
                :feedback="false"
                toggleMask
                :input-class="'w-full p-inputtext p-component'"
                class="w-full"
                placeholder="请再次输入密码"
                @keyup.enter="handleSubmit"
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
                class="text-sm text-red-600 bg-red-50 border border-red-100 rounded-md p-2"
              >
                {{ errorMsg }}
              </div>
            </transition>

            <Button
              :label="submitLabel"
              class="w-full !py-3 !text-base !font-semibold transition-transform hover:scale-[1.01] active:scale-[0.99]"
              :loading="submitting"
              @click="handleSubmit"
            />

            <Divider>
              <span class="text-xs text-slate-400">或</span>
            </Divider>

            <div class="text-center text-sm text-slate-500">
              <template v-if="isInit"> 如需重置密码，请联系管理员或清空服务端配置 </template>
              <template v-else> 已初始化？请直接使用上方密码登录 </template>
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
      <div v-if="checkingInit" class="flex items-center gap-3 text-slate-500">
        <span class="inline-block h-2 w-2 rounded-full bg-slate-300 animate-pulse"></span>
        <span
          class="inline-block h-2 w-2 rounded-full bg-slate-300 animate-pulse [animation-delay:120ms]"
        ></span>
        <span
          class="inline-block h-2 w-2 rounded-full bg-slate-300 animate-pulse [animation-delay:240ms]"
        ></span>
        <span class="text-sm">正在加载...</span>
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
