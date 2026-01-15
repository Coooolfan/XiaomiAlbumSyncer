<template>
  <div class="h-full overflow-y-auto pr-2 text-sm text-slate-600 space-y-5">
    <div class="space-y-2">
      <h3 class="font-medium text-slate-900">Cron 表达式指南</h3>
      <p>
        Xiaomi Album Syncer 使用标准的 Quartz Cron 表达式，支持 6 或 7 个字段（秒 分 时 日 月 周
        [年]）。<span class="font-bold">而不是 Unix Cron。</span>
      </p>
    </div>

    <div class="space-y-2">
      <h3 class="font-medium text-slate-900">字段说明</h3>
      <div class="overflow-x-auto">
        <table class="w-full text-xs text-left">
          <thead class="text-slate-500 font-medium border-b border-slate-100">
            <tr>
              <th class="py-1">字段</th>
              <th class="py-1">允许值</th>
              <th class="py-1">特殊字符</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-50">
            <tr>
              <td class="py-1">秒</td>
              <td class="py-1">0-59</td>
              <td class="py-1 font-mono">, - * /</td>
            </tr>
            <tr>
              <td class="py-1">分</td>
              <td class="py-1">0-59</td>
              <td class="py-1 font-mono">, - * /</td>
            </tr>
            <tr>
              <td class="py-1">时</td>
              <td class="py-1">0-23</td>
              <td class="py-1 font-mono">, - * /</td>
            </tr>
            <tr>
              <td class="py-1">日</td>
              <td class="py-1">1-31</td>
              <td class="py-1 font-mono">, - * ? / L W</td>
            </tr>
            <tr>
              <td class="py-1">月</td>
              <td class="py-1">1-12 或 JAN-DEC</td>
              <td class="py-1 font-mono">, - * /</td>
            </tr>
            <tr>
              <td class="py-1">周</td>
              <td class="py-1">1-7 或 SUN-SAT</td>
              <td class="py-1 font-mono">, - * ? / L #</td>
            </tr>
            <tr>
              <td class="py-1 text-slate-400">年 (可选)</td>
              <td class="py-1 text-slate-400">留空, 1970-2099</td>
              <td class="py-1 font-mono text-slate-400">, - * /</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div class="space-y-2">
      <h3 class="font-medium text-slate-900">特殊字符</h3>
      <div class="grid grid-cols-2 gap-2 text-xs">
        <div class="p-2 bg-slate-50 rounded border border-slate-100">
          <div class="font-mono text-blue-600 mb-0.5">*</div>
          <div class="text-slate-500 text-[10px]">所有可能的值</div>
        </div>
        <div class="p-2 bg-slate-50 rounded border border-slate-100">
          <div class="font-mono text-blue-600 mb-0.5">?</div>
          <div class="text-slate-500 text-[10px]">不指定值 (仅用于日和周)</div>
        </div>
        <div class="p-2 bg-slate-50 rounded border border-slate-100">
          <div class="font-mono text-blue-600 mb-0.5">-</div>
          <div class="text-slate-500 text-[10px]">范围 (如 10-12)</div>
        </div>
        <div class="p-2 bg-slate-50 rounded border border-slate-100">
          <div class="font-mono text-blue-600 mb-0.5">,</div>
          <div class="text-slate-500 text-[10px]">列表 (如 1,3,5)</div>
        </div>
        <div class="p-2 bg-slate-50 rounded border border-slate-100">
          <div class="font-mono text-blue-600 mb-0.5">/</div>
          <div class="text-slate-500 text-[10px]">步长 (如 0/15 每15分钟)</div>
        </div>
        <div class="p-2 bg-slate-50 rounded border border-slate-100">
          <div class="font-mono text-blue-600 mb-0.5">L</div>
          <div class="text-slate-500 text-[10px]">最后一天</div>
        </div>
      </div>
    </div>

    <div class="space-y-2">
      <h3 class="font-medium text-slate-900">常用示例</h3>
      <div class="space-y-3 text-xs">
        <div>
          <div class="text-slate-500 mb-1">每天晚上 23:00 执行</div>
          <code class="block w-full bg-slate-800 text-slate-200 p-2 rounded"> 0 0 23 * * ? </code>
        </div>
        <div>
          <div class="text-slate-500 mb-1">每隔 30 分钟执行一次</div>
          <code class="block w-full bg-slate-800 text-slate-200 p-2 rounded"> 0 */30 * * * ? </code>
        </div>
        <div>
          <div class="text-slate-500 mb-1">每周一凌晨 1:00 执行</div>
          <code class="block w-full bg-slate-800 text-slate-200 p-2 rounded"> 0 0 1 ? * MON </code>
        </div>
        <div>
          <div class="text-slate-500 mb-1">每月 1 号凌晨 1:00 执行</div>
          <code class="block w-full bg-slate-800 text-slate-200 p-2 rounded"> 0 0 1 1 * ? </code>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// No logic needed, purely presentational
</script>
