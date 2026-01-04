<script setup lang="ts">
import { ref, onMounted } from 'vue'
import Panel from 'primevue/panel'
import SplitButton from 'primevue/splitbutton'
import AlbumCard from '@/components/AlbumCard.vue'
import { api } from '@/ApiInstance'
import { useToast } from 'primevue/usetoast'
import type { Dynamic_Album } from '@/__generated/model/dynamic'

const emit = defineEmits<{
  (e: 'update:albums', albums: ReadonlyArray<Dynamic_Album>): void
}>()

const toast = useToast()
const albums = ref<ReadonlyArray<Dynamic_Album>>([])

const albumsRefreshModel = ref([
  {
    label: '从远程更新整个相册列表',
    icon: 'pi pi-cloud-download',
    command: fetchLatestAlbums,
  },
])

async function fetchAlbums() {
  try {
    const list = await api.albumsController.listAlbums()
    albums.value = list
    emit('update:albums', list)
  } catch (err) {
    console.error('获取相册列表失败', err)
  }
}

async function fetchLatestAlbums() {
  try {
    toast.add({
      severity: 'info',
      summary: '正在从远程更新相册列表',
      detail: '请暂时不要离开此页面，同步正在进行',
      life: 5000,
    })
    const list = await api.albumsController.refreshAlbums()
    albums.value = list
    emit('update:albums', list)
    toast.add({ severity: 'success', summary: '已更新', life: 1600 })
  } catch (err) {
    toast.add({
      severity: 'error',
      summary: '更新失败',
      detail: '请确保您已配置有效的 passToken 与 UserId。\n并确保此已完成相册服务二次验证',
      life: 10000,
    })
    console.error('获取最新相册列表失败', err)
  }
}

onMounted(() => {
  fetchAlbums()
})
</script>

<template>
  <Panel header="相册" toggleable>
    <template #icons>
      <SplitButton
        icon="pi pi-refresh"
        severity="secondary"
        outlined
        rounded
        @click="fetchAlbums"
        :model="albumsRefreshModel"
      />
    </template>

    <div class="space-y-2">
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-3">
        <AlbumCard
          v-for="a in albums"
          :key="a.id"
          :name="a.name"
          :asset-count="a.assetCount"
          :last-update-time="a.lastUpdateTime"
        />
        <div v-if="!albums || albums.length === 0" class="text-xs text-slate-500">暂无相册</div>
      </div>
    </div>
  </Panel>
</template>
