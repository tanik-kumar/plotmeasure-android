package com.tanik.biharmapmeasure.plotmeasure.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class CrashReportStore(
    context: Context,
) {
    private val storageFile = File(context.filesDir, "plotmeasure/last-crash.txt")

    suspend fun consumeCrashReport(): String? =
        withContext(Dispatchers.IO) {
            if (!storageFile.exists()) {
                return@withContext null
            }
            val report = storageFile.readText().trim().ifBlank { null }
            storageFile.writeText("")
            report
        }

    fun persistCrashReport(threadName: String, throwable: Throwable) {
        storageFile.parentFile?.mkdirs()
        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))
        val report =
            buildString {
                appendLine("Time: ${System.currentTimeMillis()}")
                appendLine("Thread: $threadName")
                appendLine()
                append(writer.toString())
            }
        storageFile.writeText(report)
    }
}
