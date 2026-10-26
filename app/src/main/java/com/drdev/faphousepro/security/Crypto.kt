package com.drdev.faphousepro.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64

object Crypto {

    private const val GCM_TAG_BITS = 128
    private const val NONCE_LEN = 12
    private const val TAG_LEN = 16

    data class Envelope(val d: String, val s: String, val t: String, val n: String)

    private val rng = SecureRandom()

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    private fun b64UrlNoPad(bytes: ByteArray): String =
        Base64.UrlSafe.encode(bytes).trimEnd('=')

    fun buildEnvelope(
        plaintext: String,
        aesKey: ByteArray,
        hmacKey: ByteArray,
        timestampSeconds: Long = System.currentTimeMillis() / 1000L,
        nonceOverride: ByteArray? = null
    ): Envelope {
        require(aesKey.size == 32)
        require(hmacKey.size == 32)

        val nonce = nonceOverride ?: ByteArray(NONCE_LEN).also { rng.nextBytes(it) }
        require(nonce.size == NONCE_LEN)

        val t = timestampSeconds.toString()
        val n = b64UrlNoPad(nonce)
        val aad = "$t:$n".toByteArray(Charsets.UTF_8)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(aesKey, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, nonce)
        )
        cipher.updateAAD(aad)
        val ctAndTag = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(NONCE_LEN + ctAndTag.size)
        System.arraycopy(nonce, 0, combined, 0, NONCE_LEN)
        System.arraycopy(ctAndTag, 0, combined, NONCE_LEN, ctAndTag.size)

        val d = Base64.Default.encode(combined)
        val payloadShaHex = hex(sha256(combined))
        val signing = "$t:$n:$payloadShaHex".toByteArray(Charsets.UTF_8)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hmacKey, "HmacSHA256"))
        val s = hex(mac.doFinal(signing))

        return Envelope(d = d, s = s, t = t, n = n)
    }

    fun openEnvelope(
        env: Envelope,
        aesKey: ByteArray,
        hmacKey: ByteArray,
        maxClockSkewSeconds: Long = 90L
    ): String {
        require(aesKey.size == 32)
        require(hmacKey.size == 32)

        val combined = Base64.Default.decode(env.d)
        require(combined.size >= NONCE_LEN + TAG_LEN)

        val payloadShaHex = hex(sha256(combined))
        val signing = "${env.t}:${env.n}:$payloadShaHex".toByteArray(Charsets.UTF_8)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hmacKey, "HmacSHA256"))
        val expected = hex(mac.doFinal(signing))
        if (!MessageDigest.isEqual(expected.toByteArray(), env.s.toByteArray())) {
            throw SecurityException("hmac mismatch")
        }

        val serverTs = env.t.toLongOrNull() ?: throw SecurityException("bad timestamp")
        val now = System.currentTimeMillis() / 1000L
        if (kotlin.math.abs(now - serverTs) > maxClockSkewSeconds) {
            throw SecurityException("timestamp skew")
        }

        val nonce = combined.copyOfRange(0, NONCE_LEN)
        val ctAndTag = combined.copyOfRange(NONCE_LEN, combined.size)
        val aad = "${env.t}:${env.n}".toByteArray(Charsets.UTF_8)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(aesKey, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, nonce)
        )
        cipher.updateAAD(aad)
        return String(cipher.doFinal(ctAndTag), Charsets.UTF_8)
    }

    fun envelopeToJson(env: Envelope): String =
        """{"d":"${env.d}","s":"${env.s}","t":"${env.t}","n":"${env.n}"}"""
}