package com.coooolfan.xiaomialbumsyncer.config.jimmer

import org.babyfish.jimmer.sql.JSqlClient
import org.babyfish.jimmer.sql.TransientResolver
import org.babyfish.jimmer.sql.di.TransientResolverProvider
import org.noear.solon.Solon

class SolonTransientResolverProvider : TransientResolverProvider {

    override fun get(
        type: Class<TransientResolver<*, *>>,
        sqlClient: JSqlClient?
    ): TransientResolver<*, *> {
        return Solon.context().getBeanOrNew(type)
    }

}