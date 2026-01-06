<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import Panel from 'primevue/panel'
import SplitButton from 'primevue/splitbutton'
import Button from 'primevue/button'
import AlbumCard from '@/components/AlbumCard.vue'
import { api } from '@/ApiInstance'
import { useToast } from 'primevue/usetoast'
import type { AlbumDto } from '@/__generated/model/dto/AlbumDto'
import type { XiaomiAccountDto } from '@/__generated/model/dto/XiaomiAccountDto'

type Album = AlbumDto['AlbumsController/DEFAULT_ALBUM']
type XiaomiAccount = XiaomiAccountDto['XiaomiAccountController/DEFAULT_XIAOMI_ACCOUNT']

const emit = defineEmits<{
  (e: 'update:albums', albums: ReadonlyArray<Album>): void
}>()

const toast = useToast()
const albums = ref<ReadonlyArray<Album>>([])
const accounts = ref<ReadonlyArray<XiaomiAccount>>([])

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

function getHeader(group: { account: XiaomiAccount; albums: Album[] }) {
  const count = group.albums.length
  if (accounts.value.length === 1) {
    return `相册-共${count}个项目`
  }
  return `${group.account.nickname}-共${count}个相册`
}

function getRefreshModel(accountId: number) {
  return [
    {
      label: '从远程更新此账号相册列表',
      icon: 'pi pi-cloud-download',
      command: () => fetchLatestAlbums(accountId),
    },
  ]
}

async function fetchData() {
  try {
    // 并行获取账号和相册列表
    const [accountsList, albumsList] = await Promise.all([
      api.xiaomiAccountController.listAll(),
      api.albumsController.listAlbums(),
    ])

    accounts.value = accountsList
    albums.value = albumsList
    emit('update:albums', albumsList)
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
    const refreshedList = await api.albumsController.refreshAlbums({ accountId })

    // 更新本地状态：移除该账号的旧相册并添加新相册
    // 假设 refreshedList 包含该账号目前数据库中的所有相册（同步后）。

    const otherAccountAlbums = albums.value.filter((a) => a.account.id !== accountId)
    const newAllAlbums = [...otherAccountAlbums, ...refreshedList]
    albums.value = newAllAlbums

    emit('update:albums', newAllAlbums)
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

onMounted(() => {
  fetchData()
})
</script>

<template>
  <div class="space-y-4">
    <div v-if="accounts.length === 0">
      <Panel header="相册" toggleable>
        <template #icons>
          <Button icon="pi pi-refresh" rounded text @click="fetchData" />
        </template>
        <div class="text-xs text-slate-500">暂无账号</div>
      </Panel>
    </div>

    <Panel
      v-for="group in groupedAlbums"
      :key="group.account.id"
      :header="getHeader(group)"
      :collapsed="accounts.length > 1"
      toggleable
    >
      <template #icons>
        <SplitButton
          icon="pi pi-refresh"
          size="small"
          class="mr-1"
          severity="secondary"
          outlined
          rounded
          label="刷新"
          @click="() => fetchData()"
          :model="getRefreshModel(group.account.id)"
        />
      </template>

      <div class="space-y-2">
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
    </Panel>
  </div>
</template>
