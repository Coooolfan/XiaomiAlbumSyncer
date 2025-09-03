<script setup lang="ts">
import { ref } from 'vue'
import Button from 'primevue/button'
import Card from 'primevue/card'
import { api } from '../ApiInstance'

const loggingOut = ref(false)

async function logout() {
  loggingOut.value = true
  try {
    await api.tokenController.logout()
  } catch {
    // 忽略错误，仍然跳转
  } finally {
    loggingOut.value = false
    window.location.href = '/'
  }
}
</script>

<template>
  <div class="min-h-screen bg-gradient-to-br from-white to-slate-50">
    <!-- Topbar -->
    <header class="sticky top-0 z-10 backdrop-blur bg-white/70 border-b border-slate-200/60">
      <div class="max-w-6xl mx-auto px-4 py-3 flex items-center justify-between">
        <div class="flex items-center gap-2">
          <div
            class="h-8 w-8 rounded-lg bg-blue-600 text-white flex items-center justify-center font-bold"
          >
            X
          </div>
          <div class="font-semibold tracking-tight text-slate-700">Xiaomi Album Syncer</div>
        </div>

        <div class="flex items-center gap-3">
          <span class="hidden sm:inline text-xs text-slate-500">Cookie 已登录</span>
          <Button
            label="退出登录"
            severity="secondary"
            class="!py-2 !px-3"
            :loading="loggingOut"
            @click="logout"
          />
        </div>
      </div>
    </header>

    <!-- Content -->
    <main class="max-w-6xl mx-auto px-4 py-8">
      <div class="mb-6">
        <h1 class="text-2xl sm:text-3xl font-semibold text-slate-800">仪表盘</h1>
        <p class="text-slate-500 mt-1">欢迎回来！在这里快速查看系统状态并进入你的工作流。</p>
      </div>

      <div class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-6">
        <!-- 快速开始 -->
        <Card class="overflow-hidden shadow-sm ring-1 ring-slate-200/60">
          <template #title>
            <div class="flex items-center justify-between">
              <span>快速开始</span>
              <span
                class="text-xs bg-blue-50 text-blue-600 px-2 py-1 rounded-full border border-blue-100"
              >
                推荐
              </span>
            </div>
          </template>
          <template #content>
            <ol class="space-y-2 text-sm text-slate-600">
              <li>1. 在左侧导航中选择功能（演示占位）</li>
              <li>2. 配置下载路径与选项</li>
              <li>3. 开始同步相册与媒体</li>
            </ol>
            <div class="mt-4 flex items-center gap-3">
              <Button label="立即开始" class="!py-2.5 !px-4" />
              <Button label="了解更多" severity="secondary" class="!py-2.5 !px-4" />
            </div>
          </template>
        </Card>

        <!-- 系统状态 -->
        <Card class="overflow-hidden shadow-sm ring-1 ring-slate-200/60">
          <template #title>系统状态</template>
          <template #content>
            <div class="space-y-3 text-sm">
              <div class="flex items-center justify-between">
                <span class="text-slate-500">鉴权方式</span>
                <span
                  class="px-2 py-1 rounded-md text-xs bg-emerald-50 text-emerald-600 border border-emerald-100"
                >
                  Cookie
                </span>
              </div>
              <div class="flex items-center justify-between">
                <span class="text-slate-500">登录状态</span>
                <span
                  class="px-2 py-1 rounded-md text-xs bg-blue-50 text-blue-600 border border-blue-100"
                >
                  已登录
                </span>
              </div>
              <div class="flex items-center justify-between">
                <span class="text-slate-500">服务健康</span>
                <span
                  class="px-2 py-1 rounded-md text-xs bg-amber-50 text-amber-600 border border-amber-100"
                >
                  正常
                </span>
              </div>
            </div>
          </template>
        </Card>

        <!-- 最近活动（占位动画） -->
        <Card class="overflow-hidden shadow-sm ring-1 ring-slate-200/60">
          <template #title>最近活动</template>
          <template #content>
            <ul class="space-y-3">
              <li class="flex items-center gap-3">
                <div class="h-2 w-2 rounded-full bg-slate-300 animate-pulse"></div>
                <div class="flex-1 h-3 rounded bg-slate-100 animate-pulse"></div>
              </li>
              <li class="flex items-center gap-3">
                <div
                  class="h-2 w-2 rounded-full bg-slate-300 animate-pulse [animation-delay:120ms]"
                ></div>
                <div
                  class="flex-1 h-3 rounded bg-slate-100 animate-pulse [animation-delay:120ms]"
                ></div>
              </li>
              <li class="flex items-center gap-3">
                <div
                  class="h-2 w-2 rounded-full bg-slate-300 animate-pulse [animation-delay:240ms]"
                ></div>
                <div
                  class="flex-1 h-3 rounded bg-slate-100 animate-pulse [animation-delay:240ms]"
                ></div>
              </li>
            </ul>
            <p class="text-xs text-slate-400 mt-3">
              注：该区域为演示占位。接入真实数据后展示最近同步与下载事件。
            </p>
          </template>
        </Card>
      </div>

      <!-- 轻微出现动画的提示卡片 -->
      <transition
        appear
        enter-active-class="transition ease-out duration-500"
        enter-from-class="opacity-0 translate-y-2"
        enter-to-class="opacity-100 translate-y-0"
      >
        <div class="mt-8">
          <Card class="overflow-hidden shadow-sm ring-1 ring-slate-200/60">
            <template #title>提示</template>
            <template #content>
              <p class="text-sm text-slate-600">
                你可以在此页面添加更多模块，例如相册列表、下载进度、任务队列等。
              </p>
            </template>
          </Card>
        </div>
      </transition>
    </main>
  </div>
</template>

<style scoped>
/* 细微的悬浮态与过渡，增强现代感 */
:deep(.p-card) {
  transition:
    transform 180ms ease,
    box-shadow 180ms ease;
}
:deep(.p-card:hover) {
  transform: translateY(-1px);
  box-shadow: 0 8px 30px -12px rgba(2, 6, 23, 0.2);
}
</style>
