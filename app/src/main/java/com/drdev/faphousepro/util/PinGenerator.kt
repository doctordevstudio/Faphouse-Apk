package com.drdev.faphousepro.util

import android.util.Log
import java.security.cert.X509Certificate

object PinGenerator {

    private const val TAG = "FH-PinGen"

    fun printPinsForHost(host: String, port: Int = 443) {
        try {
            val socket = javax.net.ssl.SSLSocketFactory.getDefault()
                .createSocket(host, port) as javax.net.ssl.SSLSocket
            socket.use {
                it.startHandshake()
                val chain = it.session.peerCertificates
                if (chain.isNullOrEmpty()) { Log.w(TAG, "no peer certs"); return }
                val leaf = chain[0] as X509Certificate
                Log.i(TAG, "pin[$host] leaf = ${spkiPin(leaf)}")
                if (chain.size > 1) {
                    val ca = chain[1] as X509Certificate
                    Log.i(TAG, "pin[$host] backup = ${spkiPin(ca)}")
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "handshake failed for $host", t)
        }
    }

    fun spkiPin(cert: X509Certificate): String {
        val spki = cert.publicKey.encoded
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(spki)
        val b64 = android.util.Base64.encodeToString(digest, android.util.Base64.NO_WRAP)
        return "sha256/$b64"
    }
}