package com.coooolfan.xiaomialbumsyncer

import org.noear.solon.Solon
import org.noear.solon.annotation.SolonMain

@SolonMain
class App {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            Solon.start(App::class.java, args).block()
        }
    }
}