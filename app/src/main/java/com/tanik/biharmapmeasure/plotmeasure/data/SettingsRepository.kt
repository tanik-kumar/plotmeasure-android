package com.tanik.biharmapmeasure.plotmeasure.data

import android.content.Context
import com.tanik.biharmapmeasure.plotmeasure.model.PlotMeasureSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class SettingsRepository(
    context: Context,
    private val json: Json,
) {
    private val storageFile = File(context.filesDir, "plotmeasure/settings.json")
    private val mutex = Mutex()

    suspend fun loadSettings(): PlotMeasureSettings =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (!storageFile.exists()) {
                    PlotMeasureSettings()
                } else {
                    val raw = storageFile.readText()
                    if (raw.isBlank()) {
                        PlotMeasureSettings()
                    } else {
                        runCatching {
                            json.decodeFromString(PlotMeasureSettings.serializer(), raw)
                        }.getOrElse {
                            storageFile.writeText("")
                            PlotMeasureSettings()
                        }.sanitizeForStability()
                    }
                }
            }
        }

    suspend fun saveSettings(settings: PlotMeasureSettings) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                storageFile.parentFile?.mkdirs()
                storageFile.writeText(json.encodeToString(PlotMeasureSettings.serializer(), settings))
            }
        }
    }

    private fun PlotMeasureSettings.sanitizeForStability(): PlotMeasureSettings {
        return copy(
            snapToEdgeEnabled = false,
            showLoupe = false,
        )
    }
}
