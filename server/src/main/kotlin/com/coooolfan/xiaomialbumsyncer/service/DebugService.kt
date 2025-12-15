package com.coooolfan.xiaomialbumsyncer.service

import org.noear.solon.annotation.Managed


@Managed
class DebugService {

    fun getDebugInfo(): String {
        val sb = StringBuilder()

        sb.appendLine(">>> 开始扫描虚拟线程...")

        // 获取所有线程的堆栈
        val allStackTraces = Thread.getAllStackTraces()

        for (entry in allStackTraces.entries) {
            val t: Thread = entry.key!!
            val stack = entry.value

            if (!t.isVirtual) continue

            // 2. 打印详情
            sb.append(formatThreadInfo(t, stack))
        }
        sb.appendLine("<<< 扫描结束\n")

        sb.append(formatJvmInfo())

        return sb.toString()
    }

    private fun formatJvmInfo(): String {
        val sb = StringBuilder()
        val runtime = Runtime.getRuntime()
        val mb = 1024 * 1024

        sb.appendLine(">>> JVM 基本信息")
        sb.appendLine("Java Version: ${System.getProperty("java.version")}")
        sb.appendLine("Java Vendor: ${System.getProperty("java.vendor")}")
        sb.appendLine("Java VM Name: ${System.getProperty("java.vm.name")}")
        sb.appendLine("OS: ${System.getProperty("os.name")} ${System.getProperty("os.arch")}")
        sb.appendLine("Available Processors: ${runtime.availableProcessors()}")

        sb.appendLine()
        sb.appendLine("Max Memory: ${runtime.maxMemory() / mb} MB")
        sb.appendLine("Total Memory: ${runtime.totalMemory() / mb} MB")
        sb.appendLine("Free Memory: ${runtime.freeMemory() / mb} MB")
        sb.appendLine("Used Memory: ${(runtime.totalMemory() - runtime.freeMemory()) / mb} MB")
        sb.appendLine("<<< JVM 信息结束\n")

        return sb.toString()
    }

    private fun formatThreadInfo(t: Thread, stack: Array<StackTraceElement>): String {
        val sb = StringBuilder()
        sb.append("Found VirtualThread: ").append(t.name) // 名字通常是 "VirtualThread-xxx"
            .append(" [ID=").append(t.threadId()).append("]\n")
            .append("State: ").append(t.state).append("\n")
            .append("Stack Trace:\n")

        // 打印堆栈
        for (e in stack) {
            sb.append("    at ").append(e.toString()).append("\n")
        }

        return sb.toString()
    }
}