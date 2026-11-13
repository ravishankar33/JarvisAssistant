package com.example.jarvis

import android.content.Context
import android.content.res.AssetManager
import org.vosk.Model
import java.io.File
import java.io.IOException

object VoskModelLoader {

    private const val ASSET_MODEL_DIR = "vosk-model-small-en-us-0.15"
    private const val TARGET_DIR_NAME = "vosk-model"
    private const val READY_MARKER = ".copy_complete_v1"

    @Throws(IOException::class)
    fun loadModel(context: Context): Model {
        val targetDir = File(context.filesDir, TARGET_DIR_NAME)
        val marker = File(targetDir, READY_MARKER)

        if (!marker.exists()) {
            if (targetDir.exists()) targetDir.deleteRecursively()
            copyAssetFolder(context.assets, ASSET_MODEL_DIR, targetDir)
            marker.createNewFile()
        }

        return Model(targetDir.absolutePath)
    }

    @Throws(IOException::class)
    private fun copyAssetFolder(
        assets: AssetManager,
        assetPath: String,
        destDir: File
    ) {
        val entries = assets.list(assetPath)
            ?: throw IOException("Asset path not found: $assetPath")

        if (entries.isEmpty()) {
            throw IOException("Vosk model missing in assets/$assetPath")
        }

        if (!destDir.exists() && !destDir.mkdirs()) {
            throw IOException("Cannot create ${destDir.absolutePath}")
        }

        for (entry in entries) {
            val childAsset = "$assetPath/$entry"
            val childDest = File(destDir, entry)
            val subEntries = assets.list(childAsset)

            if (!subEntries.isNullOrEmpty()) {
                copyAssetFolder(assets, childAsset, childDest)
            } else {
                assets.open(childAsset).use { input ->
                    childDest.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
}
