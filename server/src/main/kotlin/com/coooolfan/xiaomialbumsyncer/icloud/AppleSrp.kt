package com.coooolfan.xiaomialbumsyncer.icloud

import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Apple 的 SRP-6a / SHA-256 / 2048 位认证，支持 s2k 与 s2k_fo。 */
internal class AppleSrp(private val username: String, private val secret: ByteArray = ByteArray(256).also {
    SecureRandom().nextBytes(it)
    it[0] = (it[0].toInt() or 0x80).toByte()
}) {
    private val a = BigInteger(1, secret)
    val publicKey: ByteArray = bytes(G.modPow(a, N))

    fun proof(password: String, salt: ByteArray, serverKey: ByteArray, iterations: Int, protocol: String): Pair<ByteArray, ByteArray> {
        require(protocol in setOf("s2k", "s2k_fo")) { "不支持的 Apple 密码协议" }
        require(iterations in 1..1_000_000) { "Apple 密码迭代次数无效" }
        val b = BigInteger(1, serverKey)
        require(b.mod(N) != BigInteger.ZERO) { "Apple SRP 公钥无效" }
        val pwdHash = hash(password.toByteArray(Charsets.UTF_8))
        val passwordBytes = if (protocol == "s2k_fo") pwdHash.joinToString("") { "%02x".format(it) }.toByteArray() else pwdHash
        val derived = pbkdf2(passwordBytes, salt, iterations)
        val x = BigInteger(1, hash(salt, hash(byteArrayOf(':'.code.toByte()), derived)))
        val u = BigInteger(1, hash(pad(publicKey), pad(bytes(b))))
        require(u != BigInteger.ZERO) { "Apple SRP challenge 无效" }
        val k = BigInteger(1, hash(bytes(N), pad(bytes(G))))
        val s = (b - k * G.modPow(x, N)).mod(N).modPow(a + u * x, N)
        val key = hash(bytes(s))
        val hn = hash(bytes(N))
        val hg = hash(pad(bytes(G)))
        val xor = ByteArray(32) { (hn[it].toInt() xor hg[it].toInt()).toByte() }
        val m1 = hash(xor, hash(username.toByteArray()), salt, publicKey, bytes(b), key)
        return m1 to hash(publicKey, m1, key)
    }

    private fun pbkdf2(password: ByteArray, salt: ByteArray, iterations: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(password, "HmacSHA256"))
        var u = mac.doFinal(salt + byteArrayOf(0, 0, 0, 1))
        val result = u.copyOf()
        repeat(iterations - 1) {
            u = mac.doFinal(u)
            for (i in result.indices) result[i] = (result[i].toInt() xor u[i].toInt()).toByte()
        }
        return result
    }

    companion object {
        private val N = BigInteger("AC6BDB41324A9A9BF166DE5E1389582FAF72B6651987EE07FC3192943DB56050A37329CBB4" +
            "A099ED8193E0757767A13DD52312AB4B03310DCD7F48A9DA04FD50E8083969EDB767B0CF60" +
            "95179A163AB3661A05FBD5FAAAE82918A9962F0B93B855F97993EC975EEAA80D740ADBF4FF" +
            "747359D041D5C33EA71D281E446B14773BCA97B43A23FB801676BD207A436C6481F1D2B907" +
            "8717461A5B9D32E688F87748544523B524B0D57D5EA77A2775D2ECFA032CFBDBF52FB37861" +
            "60279004E57AE6AF874E7303CE53299CCC041C7BC308D82A5698F3A8D0C38271AE35F8E9DB" +
            "FBB694B5C803D89F7AE435DE236D525F54759B65E372FCD68EF20FA7111F9E4AFF73", 16)
        private val G = BigInteger.TWO
        private fun bytes(n: BigInteger): ByteArray = n.toByteArray().let { if (it.size > 1 && it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it }
        private fun pad(value: ByteArray) = ByteArray(256 - value.size) + value
        private fun hash(vararg values: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").run {
            values.forEach(::update)
            digest()
        }
    }
}
