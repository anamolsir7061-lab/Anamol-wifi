package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.util.WordListHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Wi-Fi Scanner", appName)
    }

    @Test
    fun `sample word list contains non empty passwords`() {
        val sampleWords = WordListHelper.getDefaultSampleWordList()
        assertTrue(sampleWords.isNotEmpty())
        assertTrue(sampleWords.contains("password"))
        assertTrue(sampleWords.contains("12345678"))
    }

    @Test
    fun `read word list from file uri`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testFile = File(context.cacheDir, "passwords.txt").apply {
            writeText("admin123\nsecret99\n   \nsuperwifi2024\n")
        }
        val uri = Uri.fromFile(testFile)

        val result = WordListHelper.readWordListFromUri(context, uri)
        assertTrue(result.isSuccess)
        val info = result.getOrNull()
        assertNotNull(info)
        assertEquals(3, info?.words?.size)
        assertEquals("admin123", info?.words?.get(0))
        assertEquals("secret99", info?.words?.get(1))
        assertEquals("superwifi2024", info?.words?.get(2))
    }
}
