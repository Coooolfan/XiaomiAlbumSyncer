package com.coooolfan.xiaomialbumsyncer.icloud

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.HexFormat

class AppleSrpTest {
    private val hex = HexFormat.of()
    /** 与 py3srp RFC5054 模式计算的固定向量交叉验证，包含非 ASCII 密码。 */
    @Test fun matchesReferenceProofs() {
        run {
            val srp = AppleSrp("test@example.com", ByteArray(256) { it.toByte() })
            val (m1, m2) = srp.proof("测试 Password 🍎", hex.parseHex("0102030405060708090a0b0c0d0e0f10"),
                hex.parseHex("65aa65b38ef6c2f92779aa69eb3638e030e01f26ca197a8f7ef0f193997ca904695bf9174e1363a0bca8ae8d23118fbbe45c65dd538657c7903c299948bec920eb2e8444343f319f7af240114f237aab0b781c6aae030b5467b18dd2c4a218bceaf58bbe58ccf41de87778dbcd4237ed20a5e602022fa76ac5472e728b0f6ef23132fdd7a7c2f93a472dafec2ca2f971fecafc0dc71a47f7f35bed8e2f4f0c08a48f1999932d2c76b94d33546f80e4ea6e5fc23b29bc3d09a31c8f53f5593a6eb0cc0af1fa8ba360edb228384795569654e1baea5e227dce0ff944ecefadd6ced28ffadd9f35b2446fcd80743e44cd786974372cc642e40bae2fde5ac910d87c"), 1000, "s2k")
            assertEquals("4f831d03773f1c0b7b63e3962973f5bcb2a7da41c9fcc7355a3bff06c5c645ad", hex.formatHex(m1))
            assertEquals("49ba53e1aae809aa300e143923a5f07dcd9f7a342ca45f5818ce035f4f5ff58c", hex.formatHex(m2))
        }
        run {
            val srp = AppleSrp("test@example.com", ByteArray(256) { it.toByte() })
            val (m1, m2) = srp.proof("测试 Password 🍎", hex.parseHex("0102030405060708090a0b0c0d0e0f10"),
                hex.parseHex("a2653be002a67b4b8663634febda5d9775ec9019d3af23b007b50c41818173603ad431589d02c3f438a62ac0b2d38cbc5197bddc79b6e1b872dfde2df13008a4d350424b1e08c1baf1087c020b43c099fb7931335d6b54fe61d759dce61a89177e04e1c85a83482660de6625031fc5102c67221be4fd2e16cfbd126c4fb1c76b01dfffcfa060fa5f34673e00396597788f3a7b643451fcaa3d18637f518f9b12fa8582ae963cf2041ed0a360eb3d3a98c7503a17778cb0618cf1f74c4e1a43acaddccd016a959145b0092b9a838f82409316ed01a7e372d4f77e5c06ec714f2e966195d26ba0020a233d6d8f96cbd66604ad0b8161949e8f72aa6975610a502b"), 1000, "s2k_fo")
            assertEquals("7a51bf60411a04c2af0f6a836991dedab14c5fe82419ef021a1f8ad6f7a8c3d8", hex.formatHex(m1))
            assertEquals("f307e4bcb7a296ee9bc56fba51cd810dedffee3a398a1d798ce34e5e6a789f23", hex.formatHex(m2))
        }
    }
    @Test fun rejectsInvalidServerChallenge() {
        assertThrows(IllegalArgumentException::class.java) { AppleSrp("id").proof("password", byteArrayOf(1), byteArrayOf(0), 1000, "s2k") }
        assertThrows(IllegalArgumentException::class.java) { AppleSrp("id").proof("password", byteArrayOf(1), byteArrayOf(2), 0, "s2k") }
    }
}
