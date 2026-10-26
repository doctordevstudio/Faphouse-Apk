package com.drdev.faphousepro.data.download

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

class DownloadRepository(context: Context) {

    companion object {
        private const val TAG = "FH-Repo"
        private const val DIR = "downloads"
        private const val INDEX = "index.json"
    }

    private val appCtx = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val listSer = ListSerializer(DownloadEntity.serializer())

    private val _persisted = MutableStateFlow<List<DownloadEntity>>(emptyList())
    val persisted: StateFlow<List<DownloadEntity>> = _persisted.asStateFlow()

    private val indexFile: File get() = File(appCtx.filesDir, "$DIR/$INDEX")

    fun downloadsRoot(): File = File(appCtx.filesDir, DIR).apply { mkdirs() }
    fun folderFor(id: String): File = File(downloadsRoot(), id).apply { mkdirs() }

    suspend fun load() = withContext(Dispatchers.IO) {
        if (!indexFile.exists()) return@withContext
        try {
            val text = indexFile.readText()
            _persisted.value = json.decodeFromString(listSer, text)
        } catch (t: Throwable) {
            Log.w(TAG, "index load failed: ${t.message}")
        }
    }

    suspend fun upsert(entity: DownloadEntity) = withContext(Dispatchers.IO) {
        _persisted.update { current ->
            val idx = current.indexOfFirst { it.id == entity.id }
            if (idx >= 0) current.toMutableList().also { it[idx] = entity }
            else current + entity
        }
        persist()
    }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        _persisted.update { it.filterNot { e -> e.id == id } }
        persist()
        folderFor(id).deleteRecursively()
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        _persisted.value.forEach { folderFor(it.id).deleteRecursively() }
        _persisted.value = emptyList()
        persist()
    }

    private suspend fun persist() = withContext(Dispatchers.IO) {
        try {
            downloadsRoot().mkdirs()
            val text = json.encodeToString(listSer, _persisted.value)
            val tmp = File(indexFile.parentFile, "${indexFile.name}.tmp")
            tmp.writeText(text)
            tmp.renameTo(indexFile)
        } catch (t: Throwable) {
            Log.w(TAG, "index persist failed: ${t.message}")
        }
    }
}