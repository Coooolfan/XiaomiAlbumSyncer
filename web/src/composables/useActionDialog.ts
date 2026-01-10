import { ref } from 'vue'

export function useActionDialog() {
  const targetId = ref<number | null>(null)
  const visible = ref(false)
  const loading = ref(false)

  function open(id: number) {
    targetId.value = id
    visible.value = true
  }

  function close() {
    targetId.value = null
    visible.value = false
  }

  return {
    targetId,
    visible,
    loading,
    open,
    close,
  }
}
