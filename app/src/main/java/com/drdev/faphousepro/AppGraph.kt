package com.drdev.faphousepro

import android.content.Context
import com.drdev.faphousepro.data.AppState
import com.drdev.faphousepro.data.download.DownloadRepository
import com.drdev.faphousepro.network.ApiClient
import com.drdev.faphousepro.security.NativeSecurityBridge
import com.drdev.faphousepro.security.SecureStorage

object AppGraph {

    @Volatile private var initialised = false

    lateinit var bridge: NativeSecurityBridge
        private set
    lateinit var storage: SecureStorage
        private set
    lateinit var apiClient: ApiClient
        private set
    lateinit var downloads: DownloadRepository
        private set
    val appState: AppState by lazy { AppState() }

    @Volatile var integrityOk: Boolean = false
        internal set

    fun init(context: Context) {
        if (initialised) return
        synchronized(this) {
            if (initialised) return

            bridge = NativeSecurityBridge(context.applicationContext)
            bridge.bootstrap()

            storage = SecureStorage(context.applicationContext)
            apiClient = ApiClient(bridge, storage)
            downloads = DownloadRepository(context.applicationContext)

            integrityOk = runCatching {
                bridge.integritySelfCheck(
                    expectedSig = BuildConfig.SIGNATURE_HASH,
                    expectedVer = BuildConfig.VERSION_NAME,
                    expectedCode = BuildConfig.VERSION_CODE
                )
            }.getOrDefault(false)

            initialised = true
        }
    }

    val isReady: Boolean get() = initialised
}