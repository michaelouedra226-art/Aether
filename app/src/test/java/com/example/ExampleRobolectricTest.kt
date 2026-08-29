package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.scanner.MediaScanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Aether", appName)
    }

    @Test
    fun `verify whatsapp audio exclusion patterns`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scanner = MediaScanner(context)

        // WhatsApp voice notes and audio paths
        assertTrue(scanner.isWhatsAppAudio("/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio/AUD-20240101-WA0001.mp3", "AUD-20240101-WA0001.mp3", 4000L))
        assertTrue(scanner.isWhatsAppAudio("/storage/emulated/0/WhatsApp/Media/WhatsApp Voice Notes/PTT-20240210-WA0002.opus", "PTT-20240210-WA0002.opus", 12000L))
        assertTrue(scanner.isWhatsAppAudio("/storage/emulated/0/Music/AUD-20240315-WA0003.m4a", "AUD-20240315-WA0003.m4a", 15000L))

        // Legitimate music tracks
        assertFalse(scanner.isWhatsAppAudio("/storage/emulated/0/Music/Daft Punk/Discovery/01 One More Time.flac", "01 One More Time.flac", 320000L))
        assertFalse(scanner.isWhatsAppAudio("/storage/emulated/0/Download/Cyberpunk_Soundtrack.mp3", "Cyberpunk_Soundtrack.mp3", 240000L))
    }
}
