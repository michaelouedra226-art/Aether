package com.example.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Size
import androidx.core.app.NotificationCompat
import com.example.AetherApp
import com.example.MainActivity
import com.example.R
import com.example.data.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicPlaybackService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var mediaSession: MediaSession? = null

    companion object {
        const val ACTION_PLAY = "com.example.service.ACTION_PLAY"
        const val ACTION_PAUSE = "com.example.service.ACTION_PAUSE"
        const val ACTION_NEXT = "com.example.service.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.example.service.ACTION_PREVIOUS"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"

        fun startService(context: Context, action: String? = null) {
            val intent = Intent(context, MusicPlaybackService::class.java).apply {
                if (action != null) this.action = action
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, MusicPlaybackService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        initMediaSession()

        // Post immediate minimal notification to comply with Android 8+ foreground service requirements
        val initialNotification = createInitialNotification()
        startForeground(AetherApp.NOTIFICATION_ID, initialNotification)

        val playerEngine = AetherApp.instance.playerEngine
        serviceScope.launch {
            playerEngine.playbackState.collect { state ->
                if (state.currentTrack != null) {
                    updateNotification(state.currentTrack, state.isPlaying, state.currentPositionMs, state.durationMs)
                }
            }
        }
    }

    private fun createInitialNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, AetherApp.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_play_action)
            .setContentTitle("Aether Music")
            .setContentText("Prêt pour la lecture")
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(false)
            .setSilent(true)
            .build()
    }

    private fun initMediaSession() {
        mediaSession = MediaSession(this, "AetherMediaSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    AetherApp.instance.playerEngine.play()
                }

                override fun onPause() {
                    AetherApp.instance.playerEngine.pause()
                }

                override fun onSkipToNext() {
                    AetherApp.instance.playerEngine.next()
                }

                override fun onSkipToPrevious() {
                    AetherApp.instance.playerEngine.previous()
                }

                override fun onSeekTo(pos: Long) {
                    AetherApp.instance.playerEngine.seekTo(pos)
                }

                override fun onStop() {
                    AetherApp.instance.playerEngine.pause()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val playerEngine = AetherApp.instance.playerEngine
        when (intent?.action) {
            ACTION_PLAY -> playerEngine.play()
            ACTION_PAUSE -> playerEngine.pause()
            ACTION_NEXT -> playerEngine.next()
            ACTION_PREVIOUS -> playerEngine.previous()
            ACTION_STOP -> {
                playerEngine.pause()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    private fun updateNotification(track: Track, isPlaying: Boolean, currentPos: Long, duration: Long) {
        serviceScope.launch {
            val albumBitmap = withContext(Dispatchers.IO) {
                loadAlbumArtBitmap(track.albumId)
            }

            val contentIntent = PendingIntent.getActivity(
                this@MusicPlaybackService,
                0,
                Intent(this@MusicPlaybackService, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val prevIntent = PendingIntent.getService(
                this@MusicPlaybackService,
                1,
                Intent(this@MusicPlaybackService, MusicPlaybackService::class.java).apply { action = ACTION_PREVIOUS },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val playPauseIntent = PendingIntent.getService(
                this@MusicPlaybackService,
                2,
                Intent(this@MusicPlaybackService, MusicPlaybackService::class.java).apply {
                    action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val nextIntent = PendingIntent.getService(
                this@MusicPlaybackService,
                3,
                Intent(this@MusicPlaybackService, MusicPlaybackService::class.java).apply { action = ACTION_NEXT },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val playPauseIcon = if (isPlaying) R.drawable.ic_pause_action else R.drawable.ic_play_action
            val playPauseTitle = if (isPlaying) "Pause" else "Lecture"

            val notificationBuilder = NotificationCompat.Builder(this@MusicPlaybackService, AetherApp.NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_play_action)
                .setContentTitle(track.title)
                .setContentText(track.artist)
                .setSubText(track.album)
                .setContentIntent(contentIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(isPlaying)
                .setSilent(true)
                .addAction(R.drawable.ic_prev_action, "Précédent", prevIntent)
                .addAction(playPauseIcon, playPauseTitle, playPauseIntent)
                .addAction(R.drawable.ic_next_action, "Suivant", nextIntent)
                .setStyle(
                    androidx.media.app.NotificationCompat.MediaStyle()
                        .setShowActionsInCompactView(0, 1, 2)
                        .setMediaSession(
                            mediaSession?.sessionToken?.let { token ->
                                android.support.v4.media.session.MediaSessionCompat.Token.fromToken(token)
                            }
                        )
                )

            if (albumBitmap != null) {
                notificationBuilder.setLargeIcon(albumBitmap)
            }

            val notification = notificationBuilder.build()
            startForeground(AetherApp.NOTIFICATION_ID, notification)
        }
    }

    private fun loadAlbumArtBitmap(albumId: Long): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val artworkUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    albumId
                )
                contentResolver.loadThumbnail(artworkUri, Size(256, 256), null)
            } else {
                val artworkUri = Uri.parse("content://media/external/audio/albumart/$albumId")
                val stream = contentResolver.openInputStream(artworkUri)
                stream?.use { BitmapFactory.decodeStream(it) }
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun onDestroy() {
        mediaSession?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
