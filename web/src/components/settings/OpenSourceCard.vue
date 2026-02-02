<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import Card from 'primevue/card'
import Tag from 'primevue/tag'
import { api } from '@/ApiInstance'
import type { SystemInfoResponse } from '@/__generated/model/static'

const systemInfo = ref<SystemInfoResponse | null>(null)
const repoUrl = 'https://github.com/coooolfan/xiaomialbumsyncer'

onMounted(async () => {
  try {
    systemInfo.value = await api.systemConfigController.getSystemInfo()
  } catch (error) {
    console.error('Failed to load system info:', error)
  }
})

const docsTag = computed(() => {
  const version = systemInfo.value?.appVersion?.trim()
  if (!version || version === 'dev') return 'main'
  return version
})

const docsBaseUrl = computed(() => `${repoUrl}/blob/${docsTag.value}`)

const frontendCore = [
  { name: 'Vue 3', desc: '前端框架', license: 'MIT' },
  { name: 'Vue Router', desc: '路由管理', license: 'MIT' },
  { name: 'Pinia', desc: '状态管理', license: 'MIT' },
]

const uiStack = [
  { name: 'PrimeVue', desc: 'UI 组件库', license: 'MIT' },
  { name: 'PrimeIcons', desc: '图标库', license: 'MIT' },
  { name: 'PrimeUIX Themes', desc: '主题样式', license: 'MIT' },
  { name: 'Tailwind CSS', desc: '工具类样式', license: 'MIT' },
]

const tooling = [
  { name: 'Vite (rolldown)', desc: '前端构建与打包', license: 'MIT' },
  { name: 'TypeScript', desc: '类型系统', license: 'Apache-2.0' },
  { name: 'ESLint', desc: '代码检查', license: 'MIT' },
  { name: 'Prettier', desc: '代码格式化', license: 'MIT' },
]

const features = [
  { name: 'SimpleWebAuthn', desc: '通行密钥支持', license: 'MIT' },
  { name: 'OGL', desc: 'WebGL 渲染', license: 'Unlicense' },
]

const backendCore = [
  { name: 'Solon', desc: '后端框架', license: 'Apache-2.0' },
  { name: 'Sa-Token', desc: '权限认证', license: 'Apache-2.0' },
  { name: 'Jimmer', desc: 'ORM 框架', license: 'Apache-2.0' },
  { name: 'Flyway', desc: '数据库迁移', license: 'Apache-2.0' },
  { name: 'OkHttp', desc: 'HTTP 客户端', license: 'Apache-2.0' },
]
</script>

<template>
  <Card class="overflow-hidden shadow-sm ring-1 ring-slate-200/60 mt-6 mb-6">
    <template #title>
      <div class="flex items-center gap-2">
        <span>开源与致谢</span>
        <Tag value="GPL-3.0" severity="info" />
      </div>
    </template>
    <template #content>
      <p class="text-xs text-slate-500">
        本项目遵循 GPL-3.0 开源协议。以下列出主要依赖及其许可证（以仓库清单为准）。
      </p>

      <div class="mt-4 grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div class="space-y-2">
          <div class="text-sm font-medium text-slate-700">前端核心</div>
          <ul class="text-xs text-slate-500 list-disc pl-4 space-y-1">
            <li v-for="item in frontendCore" :key="item.name">
              <span class="text-slate-700">{{ item.name }}</span> · {{ item.desc }} ·
              <span class="text-slate-400">{{ item.license }}</span>
            </li>
          </ul>
        </div>

        <div class="space-y-2">
          <div class="text-sm font-medium text-slate-700">UI / 样式</div>
          <ul class="text-xs text-slate-500 list-disc pl-4 space-y-1">
            <li v-for="item in uiStack" :key="item.name">
              <span class="text-slate-700">{{ item.name }}</span> · {{ item.desc }} ·
              <span class="text-slate-400">{{ item.license }}</span>
            </li>
          </ul>
        </div>

        <div class="space-y-2">
          <div class="text-sm font-medium text-slate-700">工程化</div>
          <ul class="text-xs text-slate-500 list-disc pl-4 space-y-1">
            <li v-for="item in tooling" :key="item.name">
              <span class="text-slate-700">{{ item.name }}</span> · {{ item.desc }} ·
              <span class="text-slate-400">{{ item.license }}</span>
            </li>
          </ul>
        </div>

        <div class="space-y-2">
          <div class="text-sm font-medium text-slate-700">特性组件</div>
          <ul class="text-xs text-slate-500 list-disc pl-4 space-y-1">
            <li v-for="item in features" :key="item.name">
              <span class="text-slate-700">{{ item.name }}</span> · {{ item.desc }} ·
              <span class="text-slate-400">{{ item.license }}</span>
            </li>
          </ul>
        </div>
      </div>

      <div class="mt-4 pt-4 border-t border-slate-200/60">
        <div class="text-sm font-medium text-slate-700">后端主要依赖</div>
        <ul class="mt-2 text-xs text-slate-500 list-disc pl-4 space-y-1">
          <li v-for="item in backendCore" :key="item.name">
            <span class="text-slate-700">{{ item.name }}</span> · {{ item.desc }} ·
            <span class="text-slate-400">{{ item.license }}</span>
          </li>
        </ul>
        <p class="text-xs text-slate-400 mt-3">
          详细依赖清单见
          <a
            class="text-blue-600 hover:underline"
            :href="`${docsBaseUrl}/web/README/DEPENDENCIES.md`"
            target="_blank"
          >
            web/README/DEPENDENCIES.md
          </a>
          与
          <a
            class="text-blue-600 hover:underline"
            :href="`${docsBaseUrl}/server/README/DEPENDENCIES.md`"
            target="_blank"
          >
            server/README/DEPENDENCIES.md
          </a>
        </p>
      </div>
    </template>
  </Card>
</template>

<style scoped>
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
