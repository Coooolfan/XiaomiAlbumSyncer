<script setup lang="ts">
import { onMounted, computed, ref } from 'vue'
import Card from 'primevue/card'
import Button from 'primevue/button'
import AlbumCard from '@/components/AlbumCard.vue'
import CollapsibleCard from '@/components/CollapsibleCard.vue'
import { useToast } from 'primevue/usetoast'
import type { AlbumDto } from '@/__generated/model/dto/AlbumDto'
import type { XiaomiAccountDto } from '@/__generated/model/dto/XiaomiAccountDto'
import type { CloudSpaceInfo } from '@/__generated/model/static'
import { storeToRefs } from 'pinia'
import { useAlbumsStore } from '@/stores/albums'
import { useAccountsStore } from '@/stores/accounts'
import { api } from '@/ApiInstance'

type Album = AlbumDto['AlbumsController/DEFAULT_ALBUM']
type XiaomiAccount = XiaomiAccountDto['XiaomiAccountController/DEFAULT_XIAOMI_ACCOUNT']

const toast = useToast()
const albumsStore = useAlbumsStore()
const accountsStore = useAccountsStore()

const { albums } = storeToRefs(albumsStore)
const { accounts } = storeToRefs(accountsStore)

// 云端空间信息管理
const spaceInfoMap = ref<Map<number, CloudSpaceInfo | null>>(new Map())
const loadingSpaceMap = ref<Map<number, boolean>>(new Map())

const groupedAlbums = computed(() => {
  const groups: Array<{ account: XiaomiAccount; albums: Album[] }> = []

  // 创建一个映射以便通过账号ID快速查找相册
  const albumsByAccount = new Map<number, Album[]>()
  for (const album of albums.value) {
    const accId = album.account.id
    if (!albumsByAccount.has(accId)) {
      albumsByAccount.set(accId, [])
    }
    albumsByAccount.get(accId)!.push(album)
  }

  // 遍历所有账号以确保每个账号都有一个分组
  for (const account of accounts.value) {
    groups.push({
      account: account,
      albums: albumsByAccount.get(account.id) ?? [],
    })
  }

  return groups
})

function getAccountName(group: { account: XiaomiAccount; albums: Album[] }) {
  if (accounts.value.length === 1) {
    return '相册'
  }
  return group.account.nickname
}

function getAlbumCount(group: { account: XiaomiAccount; albums: Album[] }) {
  const count = group.albums.length
  if (accounts.value.length === 1) {
    return `共${count}个项目`
  }
  return `共${count}个相册`
}

async function fetchData() {
  try {
    await Promise.all([
      accountsStore.fetchAccounts({ force: true }),
      albumsStore.fetchAlbums({ force: true }),
    ])
  } catch (err) {
    console.error('获取数据失败', err)
    toast.add({
      severity: 'error',
      summary: '获取数据失败',
      detail: '无法加载账号或相册列表',
      life: 5000,
    })
  }
}

async function fetchLatestAlbums(accountId: number) {
  try {
    toast.add({
      severity: 'info',
      summary: '正在从远程更新相册列表',
      detail: '请暂时不要离开此页面，同步正在进行',
      life: 5000,
    })
    // 刷新特定账号
    await albumsStore.refreshAlbumsForAccount(accountId)

    // 更新本地状态：移除该账号的旧相册并添加新相册
    // 假设 refreshedList 包含该账号目前数据库中的所有相册（同步后）。

    toast.add({ severity: 'success', summary: '已更新', life: 1600 })
  } catch (err) {
    toast.add({
      severity: 'error',
      summary: '更新失败',
      detail: '请确保您已配置有效的 passToken 与 UserId。\n并确保已完成相册服务的二次验证',
      life: 10000,
    })
    console.error('获取最新相册列表失败', err)
  }
}

// 云端空间相关函数
async function loadSpaceInfo(accountId: number) {
  loadingSpaceMap.value.set(accountId, true)
  try {
    const info = await api.cloudController.getCloudSpace({ accountId })
    spaceInfoMap.value.set(accountId, info)
  } catch (e) {
    console.error('加载云端空间失败:', e)
    spaceInfoMap.value.set(accountId, null)
  } finally {
    loadingSpaceMap.value.set(accountId, false)
  }
}

function formatSize(bytes: number) {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + ' ' + sizes[i]
}

function getSegmentData(spaceInfo: CloudSpaceInfo) {
  const segments: Array<{
    key: string
    label: string
    size: number
    percent: number
    color: string
  }> = []

  const total = spaceInfo.totalQuota
  const usedDetail = spaceInfo.usedDetail || {}

  const colorMap: Record<string, string> = {
    GalleryImage: 'bg-blue-500',
    Recorder: 'bg-purple-500',
    Creation: 'bg-pink-500',
    AppList: 'bg-amber-500',
    Drive: 'bg-emerald-500',
  }

  const sortedEntries = Object.entries(usedDetail).sort(
    (a, b) => (b[1] as { size: number }).size - (a[1] as { size: number }).size,
  )

  sortedEntries.forEach(([key, item]) => {
    const usageItem = item as { size: number; text: string }
    if (usageItem.size > 0) {
      segments.push({
        key,
        label: usageItem.text,
        size: usageItem.size,
        percent: (usageItem.size / total) * 100,
        color: colorMap[key] || 'bg-slate-500',
      })
    }
  })

  return segments
}

onMounted(async () => {
  await accountsStore.fetchAccounts()
  albumsStore.fetchAlbums()
  // 加载所有账号的云端空间信息
  accounts.value.forEach((account) => {
    loadSpaceInfo(account.id)
  })
})
</script>

<template>
  <Card v-if="accounts.length === 0" class="overflow-hidden shadow-sm ring-1 ring-slate-200/60">
    <template #title>
      <div class="flex items-center justify-between">
        <div class="font-medium text-slate-700 dark:text-white">相册</div>
        <Button icon="pi pi-refresh" severity="secondary" rounded text @click="fetchData" />
      </div>
    </template>
    <template #content>
      <div class="text-xs text-slate-500">暂无账号</div>
    </template>
  </Card>

  <Card v-else class="overflow-hidden shadow-sm ring-1 ring-slate-200/60">
    <template #title>
      <div class="flex items-center justify-between">
        <div class="font-medium text-slate-700 dark:text-white">相册</div>
        <div class="flex items-center gap-2">
          <Button
            icon="pi pi-refresh"
            severity="secondary"
            rounded
            text
            @click="() => fetchData()"
          />
        </div>
      </div>
    </template>
    <template #content>
      <div>
        <CollapsibleCard
          v-for="(group, index) in groupedAlbums"
          :key="group.account.id"
          :title="getAccountName(group)"
          :subtitle="getAlbumCount(group)"
          :default-collapsed="accounts.length > 1"
          :is-first="index === 0"
          :storage-key="'album-' + group.account.id"
        >
          <template #actions>
            <Button
              icon="pi pi-cloud-download"
              severity="secondary"
              rounded
              text
              @click="() => fetchLatestAlbums(group.account.id)"
            />
          </template>

          <!-- 相册内容 -->
          <div class="space-y-4">
            <!-- 云端空间信息 -->
            <div>
              <div
                v-if="loadingSpaceMap.get(group.account.id) && !spaceInfoMap.get(group.account.id)"
                class="text-center py-4 text-slate-400"
              >
                <i class="pi pi-spin pi-spinner text-xl" />
              </div>

              <div v-else-if="spaceInfoMap.get(group.account.id)" class="space-y-3">
                <!-- 详细分段式进度条 -->
                <div
                  class="w-full bg-slate-200 dark:bg-slate-700 rounded-full h-5 overflow-hidden flex"
                >
                  <div
                    v-for="segment in getSegmentData(spaceInfoMap.get(group.account.id)!)"
                    :key="segment.key"
                    class="h-5 transition-all duration-500 flex items-center justify-center"
                    :class="segment.color"
                    :style="{ width: segment.percent + '%' }"
                    :title="`${segment.label}: ${formatSize(segment.size)}`"
                  >
                    <span
                      v-if="segment.percent > 8"
                      class="text-[10px] text-white font-medium px-1 truncate"
                    >
                      {{ segment.label }}
                    </span>
                  </div>
                </div>

                <!-- 详细图例说明 -->
                <div class="flex items-center justify-between text-xs flex-wrap gap-y-2">
                  <div class="flex items-center gap-3 flex-wrap">
                    <div
                      v-for="segment in getSegmentData(spaceInfoMap.get(group.account.id)!)"
                      :key="segment.key"
                      class="flex items-center gap-1"
                    >
                      <div class="w-3 h-3 rounded" :class="segment.color"></div>
                      <span class="text-slate-600 dark:text-slate-300"
                        >{{ segment.label }} {{ formatSize(segment.size) }}</span
                      >
                    </div>
                    <div class="flex items-center gap-1">
                      <div class="w-3 h-3 rounded bg-slate-200 dark:bg-slate-700"></div>
                      <span class="text-slate-600 dark:text-slate-300"
                        >剩余
                        {{
                          formatSize(
                            spaceInfoMap.get(group.account.id)!.totalQuota -
                              spaceInfoMap.get(group.account.id)!.used,
                          )
                        }}</span
                      >
                    </div>
                  </div>
                  <div class="flex items-center gap-2">
                    <span class="text-slate-600 dark:text-slate-300 font-medium">
                      总空间 {{ formatSize(spaceInfoMap.get(group.account.id)!.totalQuota) }}
                    </span>
                    <span class="text-slate-600 dark:text-slate-300 font-medium">
                      已使用{{ spaceInfoMap.get(group.account.id)!.usagePercent }}%
                    </span>
                  </div>
                </div>
              </div>

              <div v-else class="text-center py-4 text-slate-400">
                <i class="pi pi-cloud-download text-2xl mb-1" />
                <p class="text-xs">无法加载云端空间信息</p>
              </div>
            </div>

            <!-- 相册列表 -->
            <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-3">
              <AlbumCard
                v-for="a in group.albums"
                :key="a.id"
                :name="a.name"
                :asset-count="a.assetCount"
                :last-update-time="a.lastUpdateTime"
              />
            </div>
            <div v-if="group.albums.length === 0" class="text-xs text-slate-500">暂无相册</div>
          </div>
        </CollapsibleCard>
      </div>
    </template>
  </Card>
</template>
