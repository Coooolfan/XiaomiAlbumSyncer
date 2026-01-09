import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api } from '@/ApiInstance'
import type { AlbumDto } from '@/__generated/model/dto'

type Album = AlbumDto['AlbumsController/DEFAULT_ALBUM']

type FetchOptions = { force?: boolean }

export const useAlbumsStore = defineStore('albums', () => {
  const albums = ref<ReadonlyArray<Album>>([])
  const loading = ref(false)
  const loaded = ref(false)
  const error = ref<unknown | null>(null)

  async function fetchAlbums(options: FetchOptions = {}) {
    if (loaded.value && !options.force) return albums.value
    loading.value = true
    error.value = null
    try {
      const list = await api.albumsController.listAlbums()
      albums.value = list
      loaded.value = true
      return list
    } catch (err) {
      error.value = err
      throw err
    } finally {
      loading.value = false
    }
  }

  async function refreshAlbums() {
    return fetchAlbums({ force: true })
  }

  async function refreshAlbumsForAccount(accountId: number) {
    const refreshedList = await api.albumsController.refreshAlbums({ accountId })
    const otherAccountAlbums = albums.value.filter((a) => a.account.id !== accountId)
    albums.value = [...otherAccountAlbums, ...refreshedList]
    loaded.value = true
    return refreshedList
  }

  function reset() {
    albums.value = []
    loaded.value = false
    error.value = null
  }

  return {
    albums,
    loading,
    loaded,
    error,
    fetchAlbums,
    refreshAlbums,
    refreshAlbumsForAccount,
    reset,
  }
})
