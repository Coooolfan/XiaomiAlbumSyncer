import { onBeforeUnmount, ref, watch } from 'vue'
import { i18n } from '@/i18n'
import { api } from '@/ApiInstance'
import type { CrontabDto } from '@/__generated/model/dto'
import type { CrontabConfig } from '@/__generated/model/static'
import {
  createEmptyCronForm,
  mapCrontabToForm,
  type LocalCronForm,
  type Writable,
} from '@/utils/crontabForm'

type Crontab = CrontabDto['CrontabController/DEFAULT_CRONTAB']

function resolveDefaultTimeZone() {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'
  } catch {
    return 'UTC'
  }
}

export function useCronForm(getDefaultAccountId: () => number) {
  const defaultTz = resolveDefaultTimeZone()
  const showCronDialog = ref(false)
  const isEditing = ref(false)
  const editingId = ref<number | null>(null)
  const cronForm = ref<LocalCronForm>(createEmptyCronForm(defaultTz, 0))
  const formErrors = ref<Record<string, string>>({})
  const timeZones = ref<string[]>([])
  const targetPathMountWarning = ref(false)
  let mountCheckTimer: number | undefined
  let mountCheckSeq = 0

  function clearMountCheckTimer() {
    if (mountCheckTimer) {
      window.clearTimeout(mountCheckTimer)
      mountCheckTimer = undefined
    }
  }

  function clearMountWarningState() {
    clearMountCheckTimer()
    mountCheckSeq += 1
    targetPathMountWarning.value = false
  }

  function shouldSkipMountCheck(): boolean {
    if (!showCronDialog.value) return true

    const targetPath = cronForm.value.config.targetPath?.trim() ?? ''
    return !targetPath
  }

  function scheduleTargetPathMountCheck() {
    clearMountCheckTimer()

    if (shouldSkipMountCheck()) {
      clearMountWarningState()
      return
    }

    const requestSeq = ++mountCheckSeq
    mountCheckTimer = window.setTimeout(() => {
      void runTargetPathMountCheck(requestSeq)
    }, 300)
  }

  async function runTargetPathMountCheck(requestSeq: number) {
    if (requestSeq !== mountCheckSeq) return

    if (shouldSkipMountCheck()) {
      if (requestSeq === mountCheckSeq) {
        targetPathMountWarning.value = false
      }
      return
    }

    const path = cronForm.value.config.targetPath.trim()

    try {
      const response = await api.systemConfigController.checkMountPath({
        body: { path },
      })

      if (requestSeq !== mountCheckSeq) return
      targetPathMountWarning.value = response.inDocker === true && response.mounted === false
    } catch (err) {
      if (requestSeq !== mountCheckSeq) return
      console.warn('检测保存路径挂载状态失败', err)
      targetPathMountWarning.value = false
    }
  }

  function openCreateCron() {
    isEditing.value = false
    editingId.value = null
    const accountId = getDefaultAccountId()
    cronForm.value = createEmptyCronForm(defaultTz, accountId)
    formErrors.value = {}
    showCronDialog.value = true
  }

  function openEditCron(item: Crontab) {
    isEditing.value = true
    editingId.value = item.id
    cronForm.value = mapCrontabToForm(item, defaultTz)
    formErrors.value = {}
    showCronDialog.value = true
  }

  function buildCronFormErrors(): Record<string, string> {
    const errors: Record<string, string> = {}
    if (!cronForm.value.name || cronForm.value.name.trim() === '')
      errors.name = i18n.global.t('cronform.errors.required')
    if (cronForm.value.enabled) {
      if (!cronForm.value.config.expression || cronForm.value.config.expression.trim() === '') {
        errors.expression = i18n.global.t('cronform.errors.required')
      } else {
        const crontabExpression = cronForm.value.config.expression.split(' ')
        if (crontabExpression.length < 6) {
          errors.expression = i18n.global.t('cronform.errors.invalidExpression')
        } else {
          if (crontabExpression[0] === '*') {
            errors.expression = i18n.global.t('cronform.errors.tooFrequentPerSecond')
          } else if (crontabExpression[1] === '*') {
            errors.expression = i18n.global.t('cronform.errors.tooFrequentPerMinute')
          }
        }
      }
      if (!cronForm.value.config.timeZone || cronForm.value.config.timeZone.trim() === '')
        errors.timeZone = i18n.global.t('cronform.errors.requiredSelect')
    }
    const pathValue = cronForm.value.useExpressionPath
      ? cronForm.value.config.expressionTargetPath
      : cronForm.value.config.targetPath
    if (!pathValue || pathValue.trim() === '')
      errors.targetPath = i18n.global.t('cronform.errors.required')
    if (!cronForm.value.accountId)
      errors.accountId = i18n.global.t('cronform.errors.requiredSelect')

    return errors
  }

  function validateCronForm(fields?: readonly string[]): boolean {
    const errors = buildCronFormErrors()
    if (!fields) {
      formErrors.value = errors
      return Object.keys(errors).length === 0
    }
    const next = { ...formErrors.value }
    for (const field of fields) {
      if (errors[field]) next[field] = errors[field]
      else delete next[field]
    }
    formErrors.value = next
    return fields.every((field) => !(field in errors))
  }

  function buildTimeZones() {
    const fallbackTimeZones = [
      'UTC',
      'Asia/Shanghai',
      'Asia/Tokyo',
      'Europe/Berlin',
      'America/New_York',
    ]

    try {
      const intl = Intl as unknown as { supportedValuesOf?: (key: 'timeZone') => string[] }
      const list = intl.supportedValuesOf?.('timeZone')
      timeZones.value = list && list.length > 0 ? list : fallbackTimeZones
    } catch {
      timeZones.value = fallbackTimeZones
    }
  }

  watch(
    () => cronForm.value.accountId,
    () => {
      if (!isEditing.value) {
        cronForm.value.albumIds = []
      }
    },
  )

  // 模板模式下，将 $ 之前的字面量前缀同步为 targetPath，供挂载检查与后端兜底使用
  watch(
    () => [cronForm.value.useExpressionPath, cronForm.value.config.expressionTargetPath],
    () => {
      if (!cronForm.value.useExpressionPath) return
      const expr = cronForm.value.config.expressionTargetPath
      const literal = expr?.split('$', 1)[0]?.replace(/\/+$/, '') ?? ''
      ;(cronForm.value.config as Writable<CrontabConfig>).targetPath = literal || '/'
    },
  )

  watch(
    () => cronForm.value.config.targetPath,
    () => {
      scheduleTargetPathMountCheck()
    },
  )

  watch(showCronDialog, (visible) => {
    if (visible) {
      scheduleTargetPathMountCheck()
      return
    }
    clearMountWarningState()
  })

  onBeforeUnmount(() => {
    clearMountWarningState()
  })

  return {
    showCronDialog,
    isEditing,
    editingId,
    cronForm,
    formErrors,
    timeZones,
    targetPathMountWarning,
    openCreateCron,
    openEditCron,
    validateCronForm,
    buildTimeZones,
  }
}
