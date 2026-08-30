package com.example.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.Size
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.AetherApp
import com.example.MainActivity
import com.example.R
import com.example.data.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sin

@UnstableApi
class MusicPlaybackService : MediaSessionService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var positionTickerJob: Job? = null
    private var notificationJob: Job? = null

    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    private var currentTracks: MutableList<Track> = mutableListOf()
    private var lastSavedTrackId: Long? = null
    private var lastSavedPositionTime = 0L

    // Bitmap cache for notification to prevent OOM & allocation churn
    private var cachedAlbumId: Long? = null
    private var cachedBitmap: Bitmap? = null

    companion object {
        const val TAG = "MusicPlaybackService"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "aether_playback_channel"
        private const val MAX_ART_DIMENSION = 192
    }

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()

        // 1. Mandatory immediate minimal foreground notification in onCreate to prevent ANR
        val initialNotif = buildInitialNotification()
        startForeground(NOTIFICATION_ID, initialNotif)

        // 2. Initialize the single ExoPlayer instance
        initExoPlayer()

        // 3. Initialize Media3 MediaSession
        initMediaSession()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Lecture Aether Music",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Contrôles de lecture et notifications audio"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun initExoPlayer() {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true) // true handles audio focus automatically
            .setHandleAudioBecomingNoisy(true)        // auto pause when headphones disconnected
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        exoPlayer?.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateFullState()
                updateNotification()
                // Persist session change on real track transition
                saveSessionStateNow()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateFullState()
                updateNotification()
                if (isPlaying) {
                    startPositionTicker()
                } else {
                    stopPositionTicker()
                    saveSessionStateNow()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                updateFullState()
                if (playbackState == Player.STATE_ENDED) {
                    val settingsManager = AetherApp.instance.settingsManager
                    serviceScope.launch {
                        val currentSettings = settingsManager.settingsFlow.first()
                        if (currentSettings.endOfQueueAction == "PAUSE") {
                            exoPlayer?.pause()
                        }
                    }
                }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                updateFullState()
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                updateFullState()
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "ExoPlayer playback error: ${error.message}", error)
                val connector = AetherApp.instance.playerConnector
                connector.setErrorMessage("Erreur de lecture: ${error.errorCodeName}")

                // Rule: Skip or message, never crash app!
                if (exoPlayer?.hasNextMediaItem() == true) {
                    exoPlayer?.seekToNextMediaItem()
                    exoPlayer?.prepare()
                    exoPlayer?.play()
                } else {
                    exoPlayer?.pause()
                }
            }
        })
    }

    private fun initMediaSession() {
        val player = exoPlayer ?: return
        val sessionIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            PlayerCommands.ACTION_PLAY_QUEUE -> {
                val trackIds = intent.getLongArrayExtra(PlayerCommands.EXTRA_TRACK_IDS)
                val startIndex = intent.getIntExtra(PlayerCommands.EXTRA_START_INDEX, 0)
                val startPosMs = intent.getLongExtra(PlayerCommands.EXTRA_POSITION_MS, 0L)
                if (trackIds != null && trackIds.isNotEmpty()) {
                    handlePlayQueue(trackIds, startIndex, startPosMs)
                }
            }
            PlayerCommands.ACTION_PLAY -> {
                exoPlayer?.play()
            }
            PlayerCommands.ACTION_PAUSE -> {
                exoPlayer?.pause()
            }
            PlayerCommands.ACTION_TOGGLE_PLAY_PAUSE -> {
                if (exoPlayer?.isPlaying == true) {
                    exoPlayer?.pause()
                } else {
                    exoPlayer?.play()
                }
            }
            PlayerCommands.ACTION_NEXT -> {
                if (exoPlayer?.hasNextMediaItem() == true) {
                    exoPlayer?.seekToNextMediaItem()
                } else {
                    exoPlayer?.seekTo(0, 0L)
                }
            }
            PlayerCommands.ACTION_PREV -> {
                val currentPos = exoPlayer?.currentPosition ?: 0L
                if (currentPos > 3000L) {
                    exoPlayer?.seekTo(0L)
                } else if (exoPlayer?.hasPreviousMediaItem() == true) {
                    exoPlayer?.seekToPreviousMediaItem()
                } else {
                    exoPlayer?.seekTo(0L)
                }
            }
            PlayerCommands.ACTION_SEEK -> {
                val posMs = intent.getLongExtra(PlayerCommands.EXTRA_POSITION_MS, 0L)
                exoPlayer?.seekTo(posMs)
            }
            PlayerCommands.ACTION_SEEK_TO_INDEX -> {
                val index = intent.getIntExtra(PlayerCommands.EXTRA_START_INDEX, 0)
                if (index in 0 until (exoPlayer?.mediaItemCount ?: 0)) {
                    exoPlayer?.seekTo(index, 0L)
                    exoPlayer?.play()
                }
            }
            PlayerCommands.ACTION_SET_SHUFFLE -> {
                val enabled = intent.getBooleanExtra(PlayerCommands.EXTRA_SHUFFLE_ENABLED, false)
                exoPlayer?.shuffleModeEnabled = enabled
            }
            PlayerCommands.ACTION_SET_REPEAT -> {
                val modeStr = intent.getStringExtra(PlayerCommands.EXTRA_REPEAT_MODE)
                val mode = when (modeStr) {
                    RepeatMode.ALL.name -> Player.REPEAT_MODE_ALL
                    RepeatMode.ONE.name -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
                exoPlayer?.repeatMode = mode
            }
            PlayerCommands.ACTION_SET_SPEED -> {
                val speed = intent.getFloatExtra(PlayerCommands.EXTRA_SPEED, 1.0f)
                exoPlayer?.playbackParameters = PlaybackParameters(speed)
            }
            PlayerCommands.ACTION_ADD_TO_QUEUE -> {
                val trackId = intent.getLongExtra(PlayerCommands.EXTRA_TRACK_ID, -1L)
                if (trackId != -1L) {
                    handleAddTrackToQueue(trackId)
                }
            }
            PlayerCommands.ACTION_PLAY_NEXT -> {
                val trackId = intent.getLongExtra(PlayerCommands.EXTRA_TRACK_ID, -1L)
                if (trackId != -1L) {
                    handlePlayTrackNext(trackId)
                }
            }
            PlayerCommands.ACTION_REMOVE_QUEUE_INDEX -> {
                val removeIndex = intent.getIntExtra(PlayerCommands.EXTRA_REMOVE_INDEX, -1)
                if (removeIndex in 0 until (exoPlayer?.mediaItemCount ?: 0)) {
                    exoPlayer?.removeMediaItem(removeIndex)
                    if (removeIndex in currentTracks.indices) {
                        currentTracks.removeAt(removeIndex)
                    }
                    updateFullState()
                }
            }
            PlayerCommands.ACTION_REORDER_QUEUE -> {
                val fromIndex = intent.getIntExtra(PlayerCommands.EXTRA_FROM_INDEX, -1)
                val toIndex = intent.getIntExtra(PlayerCommands.EXTRA_TO_INDEX, -1)
                if (fromIndex in 0 until (exoPlayer?.mediaItemCount ?: 0) &&
                    toIndex in 0 until (exoPlayer?.mediaItemCount ?: 0)
                ) {
                    exoPlayer?.moveMediaItem(fromIndex, toIndex)
                    if (fromIndex in currentTracks.indices && toIndex in currentTracks.indices) {
                        val item = currentTracks.removeAt(fromIndex)
                        currentTracks.add(toIndex, item)
                    }
                    updateFullState()
                }
            }
            PlayerCommands.ACTION_CLEAR_QUEUE -> {
                exoPlayer?.stop()
                exoPlayer?.clearMediaItems()
                currentTracks.clear()
                updateFullState()
            }
            PlayerCommands.ACTION_STOP -> {
                performCleanShutdown()
                return START_NOT_STICKY
            }
        }

        return START_NOT_STICKY
    }

    private fun createMediaItem(track: Track): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setArtworkUri(track.albumArtUri)
            .build()

        return MediaItem.Builder()
            .setMediaId(track.id.toString())
            .setUri(track.uri)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun handlePlayQueue(trackIds: LongArray, startIndex: Int, startPositionMs: Long) {
        serviceScope.launch {
            val repo = AetherApp.instance.audioRepository
            val tracks = repo.getTracksByIds(trackIds.toList())
            if (tracks.isEmpty()) return@launch

            currentTracks = tracks.toMutableList()
            val mediaItems = tracks.map { track -> createMediaItem(track) }

            val validIndex = startIndex.coerceIn(0, mediaItems.lastIndex)
            exoPlayer?.setMediaItems(mediaItems, validIndex, startPositionMs)
            exoPlayer?.prepare()
            exoPlayer?.play()

            updateFullState()
            updateNotification()
        }
    }

    private fun handleAddTrackToQueue(trackId: Long) {
        serviceScope.launch {
            val track = AetherApp.instance.audioRepository.getTrackById(trackId) ?: return@launch
            currentTracks.add(track)
            val mediaItem = createMediaItem(track)
            exoPlayer?.addMediaItem(mediaItem)
            updateFullState()
        }
    }

    private fun handlePlayTrackNext(trackId: Long) {
        serviceScope.launch {
            val track = AetherApp.instance.audioRepository.getTrackById(trackId) ?: return@launch
            val currentIndex = exoPlayer?.currentMediaItemIndex ?: 0
            val insertIndex = (currentIndex + 1).coerceAtMost(exoPlayer?.mediaItemCount ?: 0)

            if (insertIndex in 0..currentTracks.size) {
                currentTracks.add(insertIndex, track)
            } else {
                currentTracks.add(track)
            }

            val mediaItem = createMediaItem(track)
            exoPlayer?.addMediaItem(insertIndex, mediaItem)
            updateFullState()
        }
    }

    private fun startPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = serviceScope.launch {
            var step = 0
            while (isActive && exoPlayer?.isPlaying == true) {
                val pos = exoPlayer?.currentPosition ?: 0L
                val dur = exoPlayer?.duration?.takeIf { it > 0 } ?: 0L
                AetherApp.instance.playerConnector.updatePosition(pos, dur)

                // Generate pleasant visualizer waves for UI only if active
                step++
                val freqs = List(8) { i ->
                    val s = sin((step * 0.35f + i * 0.8f).toDouble()).toFloat()
                    ((s + 1f) / 2f).coerceIn(0.15f, 0.95f)
                }
                AetherApp.instance.playerConnector.updateFrequencies(freqs)

                // Save session position every 10 seconds to avoid DataStore disk thrashing
                val now = System.currentTimeMillis()
                if (now - lastSavedPositionTime > 10000L) {
                    lastSavedPositionTime = now
                    saveSessionStateNow()
                }

                delay(500)
            }
        }
    }

    private fun saveSessionStateNow() {
        val curTrack = getCurrentTrack() ?: return
        val pos = exoPlayer?.currentPosition ?: 0L
        val shuffle = exoPlayer?.shuffleModeEnabled ?: false
        val rep = when (exoPlayer?.repeatMode) {
            Player.REPEAT_MODE_ALL -> "ALL"
            Player.REPEAT_MODE_ONE -> "ONE"
            else -> "OFF"
        }
        val qIndex = exoPlayer?.currentMediaItemIndex ?: 0
        val qIds = currentTracks.map { it.id }

        val needHistoryRecord = curTrack.id != lastSavedTrackId
        if (needHistoryRecord) {
            lastSavedTrackId = curTrack.id
        }

        serviceScope.launch(Dispatchers.IO) {
            if (needHistoryRecord) {
                AetherApp.instance.audioRepository.recordPlaybackHistory(curTrack.id, pos)
            }
            AetherApp.instance.settingsManager.savePlaybackState(
                trackId = curTrack.id,
                positionMs = pos,
                queueIds = qIds,
                queueIndex = qIndex,
                shuffle = shuffle,
                repeat = rep
            )
        }
    }

    private fun stopPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = null
        val pos = exoPlayer?.currentPosition ?: 0L
        val dur = exoPlayer?.duration?.takeIf { it > 0 } ?: 0L
        AetherApp.instance.playerConnector.updatePosition(pos, dur)
        AetherApp.instance.playerConnector.updateFrequencies(emptyList())
    }

    private fun getCurrentTrack(): Track? {
        val player = exoPlayer ?: return null
        val index = player.currentMediaItemIndex
        return if (index in currentTracks.indices) currentTracks[index] else null
    }

    private fun updateFullState() {
        val player = exoPlayer ?: return
        val currentTrack = getCurrentTrack()
        val isPlaying = player.isPlaying
        val pos = player.currentPosition
        val dur = player.duration.takeIf { it > 0 } ?: 0L
        val isShuffle = player.shuffleModeEnabled
        val repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_ALL -> RepeatMode.ALL
            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
            else -> RepeatMode.OFF
        }
        val speed = player.playbackParameters.speed
        val index = player.currentMediaItemIndex

        val state = PlaybackState(
            currentTrack = currentTrack,
            isPlaying = isPlaying,
            isLoading = player.playbackState == Player.STATE_BUFFERING,
            currentPositionMs = pos,
            durationMs = dur,
            isShuffle = isShuffle,
            repeatMode = repeatMode,
            playbackSpeed = speed,
            queue = currentTracks.toList(),
            currentQueueIndex = index
        )

        AetherApp.instance.playerConnector.updateServiceState(state)
    }

    private fun updateNotification() {
        val track = getCurrentTrack() ?: return
        val isPlaying = exoPlayer?.isPlaying == true

        // Cancel previous notification decode coroutine to avoid accumulation
        notificationJob?.cancel()
        notificationJob = serviceScope.launch {
            val albumBitmap = withContext(Dispatchers.IO) {
                loadAlbumArtBitmap(track.albumId)
            }

            if (!isActive) return@launch

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
                Intent(this@MusicPlaybackService, MusicPlaybackService::class.java).apply {
                    action = PlayerCommands.ACTION_PREV
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val playPauseIntent = PendingIntent.getService(
                this@MusicPlaybackService,
                2,
                Intent(this@MusicPlaybackService, MusicPlaybackService::class.java).apply {
                    action = PlayerCommands.ACTION_TOGGLE_PLAY_PAUSE
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val nextIntent = PendingIntent.getService(
                this@MusicPlaybackService,
                3,
                Intent(this@MusicPlaybackService, MusicPlaybackService::class.java).apply {
                    action = PlayerCommands.ACTION_NEXT
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val playPauseIcon = if (isPlaying) R.drawable.ic_pause_action else R.drawable.ic_play_action
            val playPauseTitle = if (isPlaying) "Pause" else "Lecture"

            val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2)

            val notifBuilder = NotificationCompat.Builder(this@MusicPlaybackService, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_play_action)
                .setContentTitle(track.title)
                .setContentText(track.artist)
                .setSubText(track.album)
                .setContentIntent(contentIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(isPlaying)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .addAction(R.drawable.ic_prev_action, "Précédent", prevIntent)
                .addAction(playPauseIcon, playPauseTitle, playPauseIntent)
                .addAction(R.drawable.ic_next_action, "Suivant", nextIntent)
                .setStyle(mediaStyle)

            if (albumBitmap != null) {
                notifBuilder.setLargeIcon(albumBitmap)
            }

            try {
                startForeground(NOTIFICATION_ID, notifBuilder.build())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to startForeground with notification: ${e.message}")
            }
        }
    }

    private fun buildInitialNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_play_action)
            .setContentTitle("Aether Music")
            .setContentText("Prêt pour la lecture")
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(false)
            .setSilent(true)
            .build()
    }

    /**
     * Memory-safe artwork decoder:
     * - Reuses cached bitmap if album ID is unchanged
     * - Downsamples using inSampleSize and RGB_565 config (50% memory savings)
     * - Limits resolution strictly to 192x192
     */
    private fun loadAlbumArtBitmap(albumId: Long): Bitmap? {
        if (albumId == cachedAlbumId && cachedBitmap != null && !cachedBitmap!!.isRecycled) {
            return cachedBitmap
        }

        return try {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val artworkUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    albumId
                )
                contentResolver.loadThumbnail(artworkUri, Size(MAX_ART_DIMENSION, MAX_ART_DIMENSION), null)
            } else {
                val artworkUri = Uri.parse("content://media/external/audio/albumart/$albumId")
                contentResolver.openInputStream(artworkUri)?.use { stream ->
                    // First decode dimensions
                    val options = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.RGB_565
                        inSampleSize = 2 // Half resolution minimum
                    }
                    BitmapFactory.decodeStream(stream, null, options)
                }
            }

            if (bitmap != null) {
                cachedAlbumId = albumId
                cachedBitmap = bitmap
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    private fun performCleanShutdown() {
        try {
            stopPositionTicker()
            notificationJob?.cancel()
            notificationJob = null
            serviceScope.coroutineContext.cancelChildren()

            exoPlayer?.stop()
            exoPlayer?.release()
            exoPlayer = null
            mediaSession?.release()
            mediaSession = null
            currentTracks.clear()

            cachedBitmap = null
            cachedAlbumId = null

            AetherApp.instance.playerConnector.updateServiceState(PlaybackState())
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "Error during clean shutdown: ${e.message}")
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        performCleanShutdown()
    }

    override fun onDestroy() {
        performCleanShutdown()
        super.onDestroy()
    }
}
