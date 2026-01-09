import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api } from '@/ApiInstance'
import type { XiaomiAccountDto } from '@/__generated/model/dto'
import type { XiaomiAccountCreate, XiaomiAccountUpdate } from '@/__generated/model/static'

type Account = XiaomiAccountDto['XiaomiAccountController/DEFAULT_XIAOMI_ACCOUNT']

type FetchOptions = { force?: boolean }

export const useAccountsStore = defineStore('accounts', () => {
  const accounts = ref<ReadonlyArray<Account>>([])
  const loading = ref(false)
  const loaded = ref(false)
  const error = ref<unknown | null>(null)

  async function fetchAccounts(options: FetchOptions = {}) {
    if (loaded.value && !options.force) return accounts.value
    loading.value = true
    error.value = null
    try {
      const list = await api.xiaomiAccountController.listAll()
      accounts.value = list
      loaded.value = true
      return list
    } catch (err) {
      error.value = err
      throw err
    } finally {
      loading.value = false
    }
  }

  async function refreshAccounts() {
    return fetchAccounts({ force: true })
  }

  async function createAccount(body: XiaomiAccountCreate) {
    const created = await api.xiaomiAccountController.create({ body })
    await refreshAccounts()
    return created
  }

  async function updateAccount(id: number, body: XiaomiAccountUpdate) {
    const updated = await api.xiaomiAccountController.update({ id, body })
    await refreshAccounts()
    return updated
  }

  async function deleteAccount(id: number) {
    await api.xiaomiAccountController.delete({ id })
    await refreshAccounts()
  }

  function reset() {
    accounts.value = []
    loaded.value = false
    error.value = null
  }

  return {
    accounts,
    loading,
    loaded,
    error,
    fetchAccounts,
    refreshAccounts,
    createAccount,
    updateAccount,
    deleteAccount,
    reset,
  }
})
