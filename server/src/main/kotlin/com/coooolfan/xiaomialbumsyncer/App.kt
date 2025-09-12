package com.coooolfan.xiaomialbumsyncer

import org.babyfish.jimmer.client.EnableImplicitApi
import org.noear.solon.Solon
import org.noear.solon.annotation.SolonMain
import org.noear.solon.scheduling.annotation.EnableScheduling

@EnableImplicitApi
@SolonMain
@EnableScheduling
class App {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            Solon.start(App::class.java, args).block()
        }
    }
}