<template>
  <div class="h-full overflow-y-auto pr-2 text-sm text-slate-600 dark:text-slate-300 space-y-5">
    <div class="space-y-2">
      <h3 class="font-medium text-slate-900 dark:text-slate-100">{{ t('help.cron.title') }}</h3>
      <p>
        {{ t('help.cron.intro') }}<span class="font-bold">{{ t('help.cron.notUnix') }}</span>
      </p>
    </div>

    <div class="space-y-2">
      <h3 class="font-medium text-slate-900 dark:text-slate-100">
        {{ t('help.cron.fieldsTitle') }}
      </h3>
      <div class="overflow-x-auto">
        <table class="w-full text-xs text-left">
          <thead
            class="text-slate-500 dark:text-slate-400 font-medium border-b border-slate-100 dark:border-slate-800"
          >
            <tr>
              <th class="py-1">{{ t('help.cron.fieldTable.field') }}</th>
              <th class="py-1">{{ t('help.cron.fieldTable.allowed') }}</th>
              <th class="py-1">{{ t('help.cron.fieldTable.special') }}</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-50 dark:divide-slate-800/60">
            <tr>
              <td class="py-1">{{ t('help.cron.fieldTable.second') }}</td>
              <td class="py-1">0-59</td>
              <td class="py-1 font-mono">, - * /</td>
            </tr>
            <tr>
              <td class="py-1">{{ t('help.cron.fieldTable.minute') }}</td>
              <td class="py-1">0-59</td>
              <td class="py-1 font-mono">, - * /</td>
            </tr>
            <tr>
              <td class="py-1">{{ t('help.cron.fieldTable.hour') }}</td>
              <td class="py-1">0-23</td>
              <td class="py-1 font-mono">, - * /</td>
            </tr>
            <tr>
              <td class="py-1">{{ t('help.cron.fieldTable.day') }}</td>
              <td class="py-1">1-31</td>
              <td class="py-1 font-mono">, - * ? / L W</td>
            </tr>
            <tr>
              <td class="py-1">{{ t('help.cron.fieldTable.month') }}</td>
              <td class="py-1">{{ t('help.cron.fieldTable.monthValue') }}</td>
              <td class="py-1 font-mono">, - * /</td>
            </tr>
            <tr>
              <td class="py-1">{{ t('help.cron.fieldTable.week') }}</td>
              <td class="py-1">{{ t('help.cron.fieldTable.weekValue') }}</td>
              <td class="py-1 font-mono">, - * ? / L #</td>
            </tr>
            <tr>
              <td class="py-1 text-slate-400 dark:text-slate-500">
                {{ t('help.cron.fieldTable.yearOptional') }}
              </td>
              <td class="py-1 text-slate-400 dark:text-slate-500">
                {{ t('help.cron.fieldTable.yearValue') }}
              </td>
              <td class="py-1 font-mono text-slate-400 dark:text-slate-500">, - * /</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div class="space-y-2">
      <h3 class="font-medium text-slate-900 dark:text-slate-100">
        {{ t('help.cron.specialTitle') }}
      </h3>
      <div class="grid grid-cols-2 gap-2 text-xs">
        <div
          class="p-2 bg-slate-50 dark:bg-slate-900/40 rounded border border-slate-100 dark:border-slate-800"
        >
          <div class="font-mono text-blue-600 mb-0.5">*</div>
          <div class="text-slate-500 dark:text-slate-400 text-[10px]">
            {{ t('help.cron.special.asterisk') }}
          </div>
        </div>
        <div
          class="p-2 bg-slate-50 dark:bg-slate-900/40 rounded border border-slate-100 dark:border-slate-800"
        >
          <div class="font-mono text-blue-600 mb-0.5">?</div>
          <div class="text-slate-500 dark:text-slate-400 text-[10px]">
            {{ t('help.cron.special.question') }}
          </div>
        </div>
        <div
          class="p-2 bg-slate-50 dark:bg-slate-900/40 rounded border border-slate-100 dark:border-slate-800"
        >
          <div class="font-mono text-blue-600 mb-0.5">-</div>
          <div class="text-slate-500 dark:text-slate-400 text-[10px]">
            {{ t('help.cron.special.dash') }}
          </div>
        </div>
        <div
          class="p-2 bg-slate-50 dark:bg-slate-900/40 rounded border border-slate-100 dark:border-slate-800"
        >
          <div class="font-mono text-blue-600 mb-0.5">,</div>
          <div class="text-slate-500 dark:text-slate-400 text-[10px]">
            {{ t('help.cron.special.comma') }}
          </div>
        </div>
        <div
          class="p-2 bg-slate-50 dark:bg-slate-900/40 rounded border border-slate-100 dark:border-slate-800"
        >
          <div class="font-mono text-blue-600 mb-0.5">/</div>
          <div class="text-slate-500 dark:text-slate-400 text-[10px]">
            {{ t('help.cron.special.slash') }}
          </div>
        </div>
        <div
          class="p-2 bg-slate-50 dark:bg-slate-900/40 rounded border border-slate-100 dark:border-slate-800"
        >
          <div class="font-mono text-blue-600 mb-0.5">L</div>
          <div class="text-slate-500 dark:text-slate-400 text-[10px]">
            {{ t('help.cron.special.l') }}
          </div>
        </div>
      </div>
    </div>

    <div class="space-y-2">
      <h3 class="font-medium text-slate-900 dark:text-slate-100">
        {{ t('help.cron.examplesTitle') }}
      </h3>
      <div class="space-y-3 text-xs">
        <div>
          <div class="text-slate-500 dark:text-slate-400 mb-1">
            {{ t('help.cron.examples.daily') }}
          </div>
          <code class="block w-full bg-slate-800 text-slate-200 p-2 rounded"> 0 0 23 * * ? </code>
        </div>
        <div>
          <div class="text-slate-500 dark:text-slate-400 mb-1">
            {{ t('help.cron.examples.every30Minutes') }}
          </div>
          <code class="block w-full bg-slate-800 text-slate-200 p-2 rounded"> 0 */30 * * * ? </code>
        </div>
        <div>
          <div class="text-slate-500 dark:text-slate-400 mb-1">
            {{ t('help.cron.examples.monday') }}
          </div>
          <code class="block w-full bg-slate-800 text-slate-200 p-2 rounded"> 0 0 1 ? * MON </code>
        </div>
        <div>
          <div class="text-slate-500 dark:text-slate-400 mb-1">
            {{ t('help.cron.examples.monthly') }}
          </div>
          <code class="block w-full bg-slate-800 text-slate-200 p-2 rounded"> 0 0 1 1 * ? </code>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>
