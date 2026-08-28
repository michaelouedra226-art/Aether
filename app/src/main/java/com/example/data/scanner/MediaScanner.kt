package com.example.data.scanner

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.data.local.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaScanner(private val context: Context) {

    companion object {
        private val WHATSAPP_PATH_PATTERNS = listOf(
            "whatsapp",
            "com.whatsapp",
            "whatsapp audio",
            "whatsapp voice notes",
            "whatsapp animated gifs",
            "whatsapp documents",
            "whatsapp video",
            "voice notes",
            "/.statuses",
            "/statuses",
            "whatsapp/media"
        )

        private val WHATSAPP_NAME_PREFIXES = listOf(
            "aud-",
            "ptt-",
            "wa0",
            "wa1",
            "wa2",
            "wa3",
            "wa4",
            "wa5",
            "wa6",
            "wa7",
            "wa8",
            "wa9",
            "wa-",
            "whatsapp"
        )

        fun isWhatsAppAudio(path: String?, displayName: String?, folderName: String?): Boolean {
            val lowerPath = (path ?: "").lowercase()
            val lowerName = (displayName ?: "").lowercase()
            val lowerFolder = (folderName ?: "").lowercase()

            // 1. Path folder checks
            for (pattern in WHATSAPP_PATH_PATTERNS) {
                if (lowerPath.contains(pattern) || lowerFolder.contains(pattern)) {
                    return true
                }
            }

            // 2. Filename prefix checks (e.g. AUD-20240101-WA0001.opus, PTT-20240101-WA0002.opus)
            for (prefix in WHATSAPP_NAME_PREFIXES) {
                if (lowerName.startsWith(prefix) || lowerName.contains("whatsapp")) {
                    return true
                }
            }

            // 3. Regex checks for WhatsApp voice note / audio patterns
            if (lowerName.matches(Regex("^(aud|ptt|wa)[-_0-9].*", RegexOption.IGNORE_CASE))) {
                return true
            }

            return false
        }
    }

    suspend fun scanAudioFiles(whatsAppExclusion: Boolean = true): List<TrackEntity> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<TrackEntity>()
        val contentResolver: ContentResolver = context.contentResolver

        val collectionUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DISPLAY_NAME
        )

        // Only music tracks
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            contentResolver.query(
                collectionUri,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val mimeTypeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val displayNameCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: "Piste inconnue"
                    val artist = cursor.getString(artistCol)?.takeIf { it != "<unknown>" } ?: "Artiste inconnu"
                    val album = cursor.getString(albumCol)?.takeIf { it != "<unknown>" } ?: "Album inconnu"
                    val albumId = cursor.getLong(albumIdCol)
                    val durationMs = cursor.getLong(durationCol)
                    val path = cursor.getString(dataCol) ?: ""
                    val dateAdded = cursor.getLong(dateAddedCol)
                    val mimeType = cursor.getString(mimeTypeCol) ?: "audio/mpeg"
                    val sizeBytes = cursor.getLong(sizeCol)
                    val displayName = if (displayNameCol != -1) cursor.getString(displayNameCol) else File(path).name

                    val folderName = try {
                        val file = File(path)
                        file.parentFile?.name ?: "Musique"
                    } catch (e: Exception) {
                        "Musique"
                    }

                    // Strict WhatsApp Audio Filter
                    if (whatsAppExclusion && isWhatsAppAudio(path, displayName, folderName)) {
                        continue // NEVER include WhatsApp audio!
                    }

                    // Also filter ultra-short clips (< 5 seconds) if exclusion active
                    if (whatsAppExclusion && durationMs < 5000) {
                        continue
                    }

                    val bitRate = when {
                        mimeType.contains("flac") -> 1411
                        mimeType.contains("wav") -> 1411
                        mimeType.contains("aac") -> 256
                        else -> 320
                    }

                    tracks.add(
                        TrackEntity(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            albumId = albumId,
                            durationMs = durationMs,
                            path = path,
                            dateAdded = dateAdded,
                            folderName = folderName,
                            mimeType = mimeType,
                            sizeBytes = sizeBytes,
                            isExcluded = false,
                            bitRate = bitRate,
                            sampleRate = 44100
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        tracks
    }
}
