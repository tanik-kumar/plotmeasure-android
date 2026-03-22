package com.tanik.biharmapmeasure.plotmeasure.data

import android.content.Context
import com.tanik.biharmapmeasure.plotmeasure.model.PdfProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

class JsonProjectRepository(
    context: Context,
    private val json: Json,
) {
    private val storageFile = File(context.filesDir, "plotmeasure/projects.json")
    private val mutex = Mutex()

    suspend fun loadProjects(): List<PdfProject> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                readProjects()
            }
        }

    suspend fun loadProject(projectId: String): PdfProject? =
        loadProjects().firstOrNull { it.id == projectId }

    suspend fun saveProject(project: PdfProject) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val projects = readProjects().toMutableList()
                val existingIndex = projects.indexOfFirst { it.id == project.id }
                if (existingIndex >= 0) {
                    projects[existingIndex] = project
                } else {
                    projects += project
                }
                writeProjects(projects.sortedByDescending { it.updatedAt })
            }
        }
    }

    private fun readProjects(): List<PdfProject> {
        if (!storageFile.exists()) {
            return emptyList()
        }
        val raw = storageFile.readText()
        if (raw.isBlank()) {
            return emptyList()
        }
        return runCatching {
            json.decodeFromString(ListSerializer(PdfProject.serializer()), raw)
        }.getOrElse {
            quarantineCorruptFile()
            emptyList()
        }
    }

    private fun writeProjects(projects: List<PdfProject>) {
        storageFile.parentFile?.mkdirs()
        storageFile.writeText(json.encodeToString(ListSerializer(PdfProject.serializer()), projects))
    }

    private fun quarantineCorruptFile() {
        if (!storageFile.exists()) {
            return
        }
        val backupFile =
            File(
                storageFile.parentFile,
                "${storageFile.nameWithoutExtension}-corrupt-${System.currentTimeMillis()}.${storageFile.extension}",
            )
        runCatching {
            storageFile.copyTo(backupFile, overwrite = true)
            storageFile.writeText("")
        }
    }
}
