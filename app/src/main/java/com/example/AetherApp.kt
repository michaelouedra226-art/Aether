package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.data.local.AppDatabase
import com.example.data.preferences.SettingsManager
import com.example.data.repository.AudioRepository
import com.example.data.scanner.MediaScanner
import com.example.playback.PlayerConnector

class AetherApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var settingsManager: SettingsManager
        private set

    lateinit var mediaScanner: MediaScanner
        private set

    lateinit var audioRepository: AudioRepository
        private set

    lateinit var playerConnector: PlayerConnector
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        createNotificationChannel()

        database = AppDatabase.getInstance(this)
        settingsManager = SettingsManager(this)
        mediaScanner = MediaScanner(this)
        audioRepository = AudioRepository(database, mediaScanner, settingsManager)
        playerConnector = PlayerConnector(this)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Aether Audio Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Contrôles de lecture audio Aether Music"
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "aether_playback_channel"
        const val NOTIFICATION_ID = 1001

        lateinit var instance: AetherApp
            private set
    }
}
