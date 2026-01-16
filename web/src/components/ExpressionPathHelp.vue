<template>
  <div class="h-full overflow-y-auto pr-2 text-sm text-slate-600 space-y-5">
    <div class="space-y-2">
      <h3 class="font-medium text-slate-900">ExpressionTargetPath 使用指南</h3>
      <p>
        Xiaomi Album Syncer 允许你自定义下载文件的保存路径，并通过
        <code class="text-xs bg-slate-100 px-1 py-0.5 rounded">${...}</code> 插值填充动态信息。
      </p>
      <ul class="list-disc list-inside space-y-1 pl-1 text-xs">
        <li>
          当此项为空时，或者不包含任何表达式时，此配置将被忽略，回退至<code
            class="text-xs bg-slate-100 px-1 py-0.5 rounded"
            >保存路径</code
          >逻辑。
        </li>
        <li>
          当此项有效时，<code class="text-xs bg-slate-100 px-1 py-0.5 rounded">保存路径</code>
          不参与逻辑（需自行写全路径或接受相对路径）。
        </li>
      </ul>
    </div>

    <div class="space-y-2">
      <h3 class="font-medium text-slate-900">插值项一览</h3>

      <div class="space-y-3">
        <div>
          <h4 class="text-xs font-semibold text-slate-700 mb-2">基础信息</h4>
          <div class="grid grid-cols-2 gap-2 text-xs">
            <div class="p-2 bg-slate-50 rounded border border-slate-100">
              <div class="font-mono text-blue-600 mb-0.5">${album}</div>
              <div class="text-slate-500 text-[10px]">相册名称</div>
            </div>
            <div class="p-2 bg-slate-50 rounded border border-slate-100">
              <div class="font-mono text-blue-600 mb-0.5">${fileName}</div>
              <div class="text-slate-500 text-[10px]">文件名</div>
            </div>
            <div class="p-2 bg-slate-50 rounded border border-slate-100">
              <div class="font-mono text-blue-600 mb-0.5">${fileStem}</div>
              <div class="text-slate-500 text-[10px]">文件名(无后缀)</div>
            </div>
            <div class="p-2 bg-slate-50 rounded border border-slate-100">
              <div class="font-mono text-blue-600 mb-0.5">${fileExt}</div>
              <div class="text-slate-500 text-[10px]">扩展名</div>
            </div>
            <div class="p-2 bg-slate-50 rounded border border-slate-100">
              <div class="font-mono text-blue-600 mb-0.5">${assetType}</div>
              <div class="text-slate-500 text-[10px]">资源类型</div>
            </div>
            <div class="p-2 bg-slate-50 rounded border border-slate-100">
              <div class="font-mono text-blue-600 mb-0.5">${title}</div>
              <div class="text-slate-500 text-[10px]">资源标题</div>
            </div>
            <div class="p-2 bg-slate-50 rounded border border-slate-100">
              <div class="font-mono text-blue-600 mb-0.5">${assetId}</div>
              <div class="text-slate-500 text-[10px]">资源 ID</div>
            </div>
            <div class="p-2 bg-slate-50 rounded border border-slate-100">
              <div class="font-mono text-blue-600 mb-0.5">${size}</div>
              <div class="text-slate-500 text-[10px]">文件大小</div>
            </div>
            <div class="p-2 bg-slate-50 rounded border border-slate-100">
              <div class="font-mono text-blue-600 mb-0.5">${sha1}</div>
              <div class="text-slate-500 text-[10px]">文件 SHA1</div>
            </div>
            <div class="p-2 bg-slate-50 rounded border border-slate-100">
              <div class="font-mono text-blue-600 mb-0.5">${crontabName}</div>
              <div class="text-slate-500 text-[10px]">任务名称</div>
            </div>
            <div class="p-2 bg-slate-50 rounded border border-slate-100">
              <div class="font-mono text-blue-600 mb-0.5">${crontabId}</div>
              <div class="text-slate-500 text-[10px]">任务 ID</div>
            </div>
            <div class="p-2 bg-slate-50 rounded border border-slate-100">
              <div class="font-mono text-blue-600 mb-0.5">${historyId}</div>
              <div class="text-slate-500 text-[10px]">历史 ID</div>
            </div>
          </div>
        </div>

        <div>
          <h4 class="text-xs font-semibold text-slate-700 mb-2">时间相关</h4>
          <div class="grid grid-cols-2 gap-2 text-xs">
            <div class="p-2 bg-slate-50 rounded border border-slate-100 col-span-2">
              <div class="font-mono text-purple-600 mb-0.5">${taken_&lt;pattern&gt;}</div>
              <div class="text-slate-500 text-[10px]">拍摄时间格式化 (如 ${taken_yyyy-MM})</div>
            </div>
            <div class="p-2 bg-slate-50 rounded border border-slate-100 col-span-2">
              <div class="font-mono text-purple-600 mb-0.5">${download_&lt;pattern&gt;}</div>
              <div class="text-slate-500 text-[10px]">下载时间格式化 (如 ${download_yyyyMMdd})</div>
            </div>
            <div class="p-2 bg-slate-50 rounded border border-slate-100">
              <div class="font-mono text-blue-600 mb-0.5">${takenEpochMillis}</div>
              <div class="text-slate-500 text-[10px]">拍摄毫秒戳</div>
            </div>
            <div class="p-2 bg-slate-50 rounded border border-slate-100">
              <div class="font-mono text-blue-600 mb-0.5">${downloadEpochMillis}</div>
              <div class="text-slate-500 text-[10px]">下载毫秒戳</div>
            </div>
            <div class="p-2 bg-slate-50 rounded border border-slate-100">
              <div class="font-mono text-blue-600 mb-0.5">${takenEpochSeconds}</div>
              <div class="text-slate-500 text-[10px]">拍摄秒戳</div>
            </div>
            <div class="p-2 bg-slate-50 rounded border border-slate-100">
              <div class="font-mono text-blue-600 mb-0.5">${downloadEpochSeconds}</div>
              <div class="text-slate-500 text-[10px]">下载秒戳</div>
            </div>
          </div>
        </div>
      </div>
    </div>
    <div class="space-y-2">
      <h3 class="font-medium text-slate-900">提示</h3>
      <p>请确保生成的路径对每一资产来说是唯一的，否则先前的文件将会被覆盖。注意以下项目：</p>
      <ul class="list-disc list-inside space-y-1 pl-1 text-xs">
        <li>
          录音文件的同名非常常见，请确保添加<code class="text-xs bg-slate-100 px-1 py-0.5 rounded"
            >${assetId}</code
          >以避免文件重名
        </li>
        <li>程序不会也没有能力检查由于 <span class="font-bold">错误配置</span> 导致的文件覆盖</li>
      </ul>
    </div>
    <div class="space-y-2">
      <h3 class="font-medium text-slate-900">示例</h3>
      <div class="space-y-3 text-xs">
        <div>
          <div class="text-slate-500 mb-1">按相册与月份归档</div>
          <code class="block w-full bg-slate-800 text-slate-200 p-2 rounded break-all">
            /data/${album}/${download_YYYYMM}/${fileName}
          </code>
        </div>
        <div>
          <div class="text-slate-500 mb-1">按类型与拍摄日期归档</div>
          <code class="block w-full bg-slate-800 text-slate-200 p-2 rounded break-all">
            /data/${assetType}/${taken_yyyy/MM}/${fileStem}.${fileExt}
          </code>
        </div>
        <div>
          <div class="text-slate-500 mb-1">音频文件保持原名 (相对路径)</div>
          <code class="block w-full bg-slate-800 text-slate-200 p-2 rounded break-all">
            Recordings/${fileName}
          </code>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// No logic needed, purely presentational
</script>
