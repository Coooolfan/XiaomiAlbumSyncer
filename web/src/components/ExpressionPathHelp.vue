<template>
  <div class="h-full overflow-y-auto pr-2 text-sm text-slate-600 dark:text-slate-300 space-y-5">
    <div class="space-y-2">
      <h3 class="font-medium text-slate-900 dark:text-slate-100">
        {{ t('help.expression.title') }}
      </h3>
      <i18n-t keypath="help.expression.intro" tag="p">
        <template #token>
          <code class="text-xs bg-slate-100 dark:bg-slate-800 px-1 py-0.5 rounded">${...}</code>
        </template>
      </i18n-t>
      <ul class="list-disc list-inside space-y-1 pl-1 text-xs">
        <i18n-t keypath="help.expression.fallbackNote" tag="li">
          <template #field>
            <code class="text-xs bg-slate-100 dark:bg-slate-800 px-1 py-0.5 rounded">{{
              t('cronform.field.targetPath')
            }}</code>
          </template>
        </i18n-t>
        <i18n-t keypath="help.expression.overrideNote" tag="li">
          <template #field>
            <code class="text-xs bg-slate-100 dark:bg-slate-800 px-1 py-0.5 rounded">{{
              t('cronform.field.targetPath')
            }}</code>
          </template>
        </i18n-t>
      </ul>
    </div>

    <div class="space-y-2">
      <h3 class="font-medium text-slate-900 dark:text-slate-100">
        {{ t('help.expression.tokensTitle') }}
      </h3>

      <div class="space-y-3">
        <div>
          <h4 class="text-xs font-semibold text-slate-700 dark:text-slate-200 mb-2">
            {{ t('help.expression.basicTitle') }}
          </h4>
          <div class="grid grid-cols-2 gap-2 text-xs">
            <div
              class="p-2 bg-slate-50 dark:bg-slate-900/40 rounded border border-slate-100 dark:border-slate-800"
            >
              <div class="font-mono text-blue-600 mb-0.5">${album}</div>
              <div class="text-slate-500 dark:text-slate-400 text-[10px]">
                {{ t('help.expression.token.album') }}
              </div>
            </div>
            <div
              class="p-2 bg-slate-50 dark:bg-slate-900/40 rounded border border-slate-100 dark:border-slate-800"
            >
              <div class="font-mono text-blue-600 mb-0.5">${fileName}</div>
              <div class="text-slate-500 dark:text-slate-400 text-[10px]">
                {{ t('help.expression.token.fileName') }}
              </div>
            </div>
            <div
              class="p-2 bg-slate-50 dark:bg-slate-900/40 rounded border border-slate-100 dark:border-slate-800"
            >
              <div class="font-mono text-blue-600 mb-0.5">${fileStem}</div>
              <div class="text-slate-500 dark:text-slate-400 text-[10px]">
                {{ t('help.expression.token.fileStem') }}
              </div>
            </div>
            <div
              class="p-2 bg-slate-50 dark:bg-slate-900/40 rounded border border-slate-100 dark:border-slate-800"
            >
              <div class="font-mono text-blue-600 mb-0.5">${fileExt}</div>
              <div class="text-slate-500 dark:text-slate-400 text-[10px]">
                {{ t('help.expression.token.fileExt') }}
              </div>
            </div>
            <div
              class="p-2 bg-slate-50 dark:bg-slate-900/40 rounded border border-slate-100 dark:border-slate-800"
            >
              <div class="font-mono text-blue-600 mb-0.5">${assetType}</div>
              <div class="text-slate-500 dark:text-slate-400 text-[10px]">
                {{ t('help.expression.token.assetType') }}
              </div>
            </div>
            <div
              class="p-2 bg-slate-50 dark:bg-slate-900/40 rounded border border-slate-100 dark:border-slate-800"
            >
              <div class="font-mono text-blue-600 mb-0.5">${recordingTypeId}</div>
              <div class="text-slate-500 dark:text-slate-400 text-[10px]">
                {{ t('help.expression.token.recordingTypeId') }}
              </div>
            </div>
            <div
              class="p-2 bg-slate-50 dark:bg-slate-900/40 rounded border border-slate-100 dark:border-slate-800"
            >
              <div class="font-mono text-blue-600 mb-0.5">${recordingType}</div>
              <div class="text-slate-500 dark:text-slate-400 text-[10px]">
                {{ t('help.expression.token.recordingType') }}
              </div>
            </div>
            <div
              class="p-2 bg-slate-50 dark:bg-slate-900/40 rounded border border-slate-100 dark:border-slate-800"
            >
              <div class="font-mono text-blue-600 mb-0.5">${title}</div>
              <div class="text-slate-500 dark:text-slate-400 text-[10px]">
                {{ t('help.expression.token.title') }}
              </div>
            </div>
            <div
              class="p-2 bg-slate-50 dark:bg-slate-900/40 rounded border border-slate-100 dark:border-slate-800"
            >
              <div class="font-mono text-blue-600 mb-0.5">${assetId}</div>
              <div class="text-slate-500 dark:text-slate-400 text-[10px]">
                {{ t('help.expression.token.assetId') }}
              </div>
            </div>
            <div
              class="p-2 bg-slate-50 dark:bg-slate-900/40 rounded border border-slate-100 dark:border-slate-800"
            >
              <div class="font-mono text-blue-600 mb-0.5">${size}</div>
              <div class="text-slate-500 dark:text-slate-400 text-[10px]">
                {{ t('help.expression.token.size') }}
              </div>
            </div>
            <div
              class="p-2 bg-slate-50 dark:bg-slate-900/40 rounded border border-slate-100 dark:border-slate-800"
            >
              <div class="font-mono text-blue-600 mb-0.5">${sha1}</div>
              <div class="text-slate-500 dark:text-slate-400 text-[10px]">
                {{ t('help.expression.token.sha1') }}
              </div>
            </div>
            <div
              class="p-2 bg-slate-50 dark:bg-slate-900/40 rounded border border-slate-100 dark:border-slate-800"
            >
              <div class="font-mono text-blue-600 mb-0.5">${crontabName}</div>
              <div class="text-slate-500 dark:text-slate-400 text-[10px]">
                {{ t('help.expression.token.crontabName') }}
              </div>
            </div>
            <div
              class="p-2 bg-slate-50 dark:bg-slate-900/40 rounded border border-slate-100 dark:border-slate-800"
            >
              <div class="font-mono text-blue-600 mb-0.5">${crontabId}</div>
              <div class="text-slate-500 dark:text-slate-400 text-[10px]">
                {{ t('help.expression.token.crontabId') }}
              </div>
            </div>
            <div
              class="p-2 bg-slate-50 dark:bg-slate-900/40 rounded border border-slate-100 dark:border-slate-800"
            >
              <div class="font-mono text-blue-600 mb-0.5">${historyId}</div>
              <div class="text-slate-500 dark:text-slate-400 text-[10px]">
                {{ t('help.expression.token.historyId') }}
              </div>
            </div>
          </div>
        </div>

        <div>
          <h4 class="text-xs font-semibold text-slate-700 dark:text-slate-200 mb-2">
            {{ t('help.expression.timeTitle') }}
          </h4>
          <div class="grid grid-cols-2 gap-2 text-xs">
            <div
              class="p-2 bg-slate-50 dark:bg-slate-900/40 rounded border border-slate-100 dark:border-slate-800 col-span-2"
            >
              <div class="font-mono text-purple-600 mb-0.5">${taken_&lt;pattern&gt;}</div>
              <i18n-t
                keypath="help.expression.token.takenPattern"
                tag="div"
                class="text-slate-500 dark:text-slate-400 text-[10px]"
              >
                <template #example>${taken_yyyy-MM}</template>
              </i18n-t>
            </div>
            <div
              class="p-2 bg-slate-50 dark:bg-slate-900/40 rounded border border-slate-100 dark:border-slate-800 col-span-2"
            >
              <div class="font-mono text-purple-600 mb-0.5">${download_&lt;pattern&gt;}</div>
              <i18n-t
                keypath="help.expression.token.downloadPattern"
                tag="div"
                class="text-slate-500 dark:text-slate-400 text-[10px]"
              >
                <template #example>${download_yyyyMMdd}</template>
              </i18n-t>
            </div>
            <div
              class="p-2 bg-slate-50 dark:bg-slate-900/40 rounded border border-slate-100 dark:border-slate-800"
            >
              <div class="font-mono text-blue-600 mb-0.5">${takenEpochMillis}</div>
              <div class="text-slate-500 dark:text-slate-400 text-[10px]">
                {{ t('help.expression.token.takenEpochMillis') }}
              </div>
            </div>
            <div
              class="p-2 bg-slate-50 dark:bg-slate-900/40 rounded border border-slate-100 dark:border-slate-800"
            >
              <div class="font-mono text-blue-600 mb-0.5">${downloadEpochMillis}</div>
              <div class="text-slate-500 dark:text-slate-400 text-[10px]">
                {{ t('help.expression.token.downloadEpochMillis') }}
              </div>
            </div>
            <div
              class="p-2 bg-slate-50 dark:bg-slate-900/40 rounded border border-slate-100 dark:border-slate-800"
            >
              <div class="font-mono text-blue-600 mb-0.5">${takenEpochSeconds}</div>
              <div class="text-slate-500 dark:text-slate-400 text-[10px]">
                {{ t('help.expression.token.takenEpochSeconds') }}
              </div>
            </div>
            <div
              class="p-2 bg-slate-50 dark:bg-slate-900/40 rounded border border-slate-100 dark:border-slate-800"
            >
              <div class="font-mono text-blue-600 mb-0.5">${downloadEpochSeconds}</div>
              <div class="text-slate-500 dark:text-slate-400 text-[10px]">
                {{ t('help.expression.token.downloadEpochSeconds') }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    <div class="space-y-2">
      <h3 class="font-medium text-slate-900 dark:text-slate-100">
        {{ t('help.expression.tipsTitle') }}
      </h3>
      <p>{{ t('help.expression.tipsIntro') }}</p>
      <ul class="list-disc list-inside space-y-1 pl-1 text-xs">
        <i18n-t keypath="help.expression.tipRecording" tag="li">
          <template #token>
            <code class="text-xs bg-slate-100 dark:bg-slate-800 px-1 py-0.5 rounded"
              >${assetId}</code
            >
          </template>
        </i18n-t>
        <i18n-t keypath="help.expression.tipOverwrite" tag="li">
          <template #misconfig>
            <span class="font-bold">{{ t('help.expression.misconfig') }}</span>
          </template>
        </i18n-t>
      </ul>
    </div>
    <div class="space-y-2">
      <h3 class="font-medium text-slate-900 dark:text-slate-100">
        {{ t('help.expression.examplesTitle') }}
      </h3>
      <div class="space-y-3 text-xs">
        <div>
          <div class="text-slate-500 dark:text-slate-400 mb-1">
            {{ t('help.expression.examples.albumMonth') }}
          </div>
          <code class="block w-full bg-slate-800 text-slate-200 p-2 rounded break-all">
            /data/${album}/${download_YYYYMM}/${fileName}
          </code>
        </div>
        <div>
          <div class="text-slate-500 dark:text-slate-400 mb-1">
            {{ t('help.expression.examples.typeDate') }}
          </div>
          <code class="block w-full bg-slate-800 text-slate-200 p-2 rounded break-all">
            /data/${assetType}/${taken_yyyy/MM}/${fileStem}.${fileExt}
          </code>
        </div>
        <div>
          <div class="text-slate-500 dark:text-slate-400 mb-1">
            {{ t('help.expression.examples.audioKeepName') }}
          </div>
          <code class="block w-full bg-slate-800 text-slate-200 p-2 rounded break-all">
            Recordings/${fileName}
          </code>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>
