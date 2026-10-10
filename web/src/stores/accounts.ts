import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api } from '@/ApiInstance'
import type { ProviderAccountDto } from '@/__generated/model/dto'
import type { XiaomiAccountInput } from '@/__generated/model/static'

type Account = ProviderAccountDto['ProviderAccountController/DEFAULT_PROVIDER_ACCOUNT']

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
      const list = await api.providerAccountController.listAll()
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

  async function createAccount(body: XiaomiAccountInput) {
    const created = await api.providerAccountController.create({ body })
    await refreshAccounts()
    return created
  }

  async function updateAccount(id: number, body: XiaomiAccountInput) {
    const updated = await api.providerAccountController.update({ id, body })
    await refreshAccounts()
    return updated
  }

  async function deleteAccount(id: number) {
    await api.providerAccountController.delete({ id })
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
