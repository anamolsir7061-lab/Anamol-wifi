package com.example.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

data class WordListInfo(
    val fileName: String,
    val words: List<String>,
    val fileSizeFormatted: String = ""
)

object WordListHelper {

    /**
     * Reads a text file (.txt, .lst, .dict, etc.) from an Android content Uri
     * into a clean List of words (passwords/strings).
     */
    suspend fun readWordListFromUri(context: Context, uri: Uri): Result<WordListInfo> = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val fileName = getFileName(context, uri) ?: "wordlist.txt"
            
            var totalBytes = 0L
            val words = mutableListOf<String>()

            contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    var line = reader.readLine()
                    while (line != null) {
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty()) {
                            words.add(trimmed)
                        }
                        line = reader.readLine()
                    }
                }
            } ?: return@withContext Result.failure(Exception("Unable to open input stream for the selected file."))

            val sizeFormatted = formatFileSize(context, uri)
            Result.success(
                WordListInfo(
                    fileName = fileName,
                    words = words,
                    fileSizeFormatted = sizeFormatted
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Extracts display name of a file Uri.
     */
    fun getFileName(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            return cursor.getString(nameIndex)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return uri.lastPathSegment
    }

    private fun formatFileSize(context: Context, uri: Uri): String {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                        val bytes = cursor.getLong(sizeIndex)
                        return when {
                            bytes < 1024 -> "$bytes B"
                            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
                            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return ""
    }

    /**
     * Returns a default built-in word list for quick testing.
     */
    fun getDefaultSampleWordList(): List<String> {
        return listOf(
            "password",
            "12345678",
            "123456789",
            "admin123",
            "welcome123",
            "qwerty1234",
            "office@2024",
            "guest12345",
            "wifi2024!",
            "letmein123"
        )
    }
}
