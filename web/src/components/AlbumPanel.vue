<script setup lang="ts">
import { onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import Button from 'primevue/button'
import Menu from 'primevue/menu'
import AlbumCard from '@/components/AlbumCard.vue'
import { useToast } from 'primevue/usetoast'
import { ref } from 'vue'
import type { AlbumDto } from '@/__generated/model/dto/AlbumDto'
import type { XiaomiAccountDto } from '@/__generated/model/dto/XiaomiAccountDto'
import { storeToRefs } from 'pinia'
import { useAlbumsStore } from '@/stores/albums'
import { useAccountsStore } from '@/stores/accounts'

type Album = AlbumDto['AlbumsController/DEFAULT_ALBUM']
type XiaomiAccount = XiaomiAccountDto['XiaomiAccountController/DEFAULT_XIAOMI_ACCOUNT']

const { t } = useI18n()
const toast = useToast()
const albumsStore = useAlbumsStore()
const accountsStore = useAccountsStore()

const { albums } = storeToRefs(albumsStore)
const { accounts } = storeToRefs(accountsStore)

const groupMenus = ref<Array<InstanceType<typeof Menu> | null>>([])

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

const groupMenuItems = computed(() => [
  {
    label: t('album.panel.updateAccountAlbums'),
    icon: 'pi pi-cloud-download',
  },
])

function getGroupMenuItems(accountId: number) {
  return groupMenuItems.value.map((item) => ({
    ...item,
    command: () => fetchLatestAlbums(accountId),
  }))
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
      summary: t('album.toast.fetchFailed'),
      detail: t('album.toast.fetchFailedDetail'),
      life: 5000,
    })
  }
}

async function fetchLatestAlbums(accountId: number) {
  try {
    toast.add({
      severity: 'info',
      summary: t('album.toast.updating'),
      detail: t('album.toast.updatingDetail'),
      life: 5000,
    })
    // 刷新特定账号
    await albumsStore.refreshAlbumsForAccount(accountId)

    // 更新本地状态：移除该账号的旧相册并添加新相册
    // 假设 refreshedList 包含该账号目前数据库中的所有相册（同步后）。

    toast.add({ severity: 'success', summary: t('album.toast.updated'), life: 1600 })
  } catch (err) {
    toast.add({
      severity: 'error',
      summary: t('album.toast.updateFailed'),
      detail: t('album.toast.updateFailedDetail'),
      life: 10000,
    })
    console.error('获取最新相册列表失败', err)
  }
}

onMounted(() => {
  accountsStore.fetchAccounts()
  albumsStore.fetchAlbums()
})
</script>

<template>
  <div
    class="overflow-hidden rounded-lg border border-slate-200/80 bg-white/70 backdrop-blur-sm dark:border-slate-800/80 dark:bg-slate-900/60"
  >
    <div
      v-if="accounts.length === 0"
      class="px-4 py-6 text-center text-xs text-slate-400 dark:text-slate-500"
    >
      {{ t('album.panel.noAccounts') }}
    </div>

    <template v-else>
      <div
        v-for="(group, gi) in groupedAlbums"
        :key="group.account.id"
        class="border-slate-200/60 dark:border-slate-800/60"
        :class="gi > 0 ? 'border-t' : ''"
      >
        <div
          class="flex items-center justify-between bg-slate-50/60 px-4 py-2 dark:bg-slate-800/30"
        >
          <div
            class="flex items-center gap-2 text-xs font-medium text-slate-500 dark:text-slate-400"
          >
            <i class="pi pi-user text-[11px]" />
            <span class="truncate">{{ group.account.nickname }}</span>
            <span class="text-slate-300 dark:text-slate-600">·</span>
            <span>{{
              t('album.panel.albumCount', { n: group.albums.length }, group.albums.length)
            }}</span>
          </div>
          <div class="flex items-center gap-0.5">
            <template v-if="group.albums.length === 0">
              <Button
                icon="pi pi-cloud-download"
                :label="t('album.panel.updateFromRemote')"
                severity="secondary"
                text
                size="small"
                class="!px-2 !py-1 text-xs"
                @click="fetchLatestAlbums(group.account.id)"
              />
            </template>
            <template v-else>
              <Button
                icon="pi pi-refresh"
                severity="secondary"
                text
                rounded
                size="small"
                v-tooltip.bottom="t('album.panel.refreshList')"
                @click="fetchData"
              />
              <Button
                icon="pi pi-ellipsis-h"
                severity="secondary"
                text
                rounded
                size="small"
                v-tooltip.bottom="t('album.panel.more')"
                @click="(e) => groupMenus[gi]?.toggle(e)"
              />
              <Menu
                :ref="(el) => (groupMenus[gi] = el as InstanceType<typeof Menu> | null)"
                :model="getGroupMenuItems(group.account.id)"
                popup
              />
            </template>
          </div>
        </div>

        <div
          v-if="group.albums.length === 0"
          class="px-4 py-4 text-xs text-slate-400 dark:text-slate-500"
        >
          {{ t('album.panel.noAlbums') }}
        </div>
        <div
          v-else
          class="grid grid-cols-2 gap-2 p-3 sm:grid-cols-3 lg:grid-cols-4 2xl:grid-cols-5"
        >
          <AlbumCard
            v-for="a in group.albums"
            :key="a.id"
            :name="a.name"
            :remote-id="a.remoteId"
            :asset-count="a.assetCount"
            :last-update-time="a.lastUpdateTime"
            :shadow="a.shadow"
          />
        </div>
      </div>
    </template>
  </div>
</template>
