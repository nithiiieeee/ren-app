package com.nithieeee.ren

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition

class AudioService : Service() {

    companion object {
        const val CHANNEL_ID = "ren_radio_media_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PLAY = "com.nithieeee.ren.ACTION_PLAY"
        const val ACTION_PAUSE = "com.nithieeee.ren.ACTION_PAUSE"
        const val ACTION_NEXT = "com.nithieeee.ren.ACTION_NEXT"
        const val ACTION_PREV = "com.nithieeee.ren.ACTION_PREV"
        const val ACTION_STOP = "com.nithieeee.ren.ACTION_STOP"

        private var instance: AudioService? = null
        private var currentTitle: String = "Ren Radio"
        private var currentIsPlaying: Boolean = false
        private var currentArtworkUrl: String = ""
        private var currentBitmap: Bitmap? = null

        fun updateNotification(context: Context, title: String, isPlaying: Boolean, artworkUrl: String) {
            currentTitle = title
            currentIsPlaying = isPlaying
            currentArtworkUrl = artworkUrl

            val intent = Intent(context, AudioService::class.java)
            if (isPlaying) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } else {
                instance?.showNotification(title, false, currentBitmap)
            }
        }
    }

    private lateinit var mediaSession: MediaSessionCompat

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()

        mediaSession = MediaSessionCompat(this, "RenRadioMediaSession").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    MainActivity.evaluateJs("window.renPlay()")
                }

                override fun onPause() {
                    MainActivity.evaluateJs("window.renPause()")
                }

                override fun onSkipToNext() {
                    MainActivity.evaluateJs("window.renNextStation()")
                }

                override fun onSkipToPrevious() {
                    MainActivity.evaluateJs("window.renPrevStation()")
                }

                override fun onStop() {
                    MainActivity.evaluateJs("window.renPause()")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                ACTION_PLAY -> MainActivity.evaluateJs("window.renPlay()")
                ACTION_PAUSE -> MainActivity.evaluateJs("window.renPause()")
                ACTION_NEXT -> MainActivity.evaluateJs("window.renNextStation()")
                ACTION_PREV -> MainActivity.evaluateJs("window.renPrevStation()")
                ACTION_STOP -> {
                    MainActivity.evaluateJs("window.renPause()")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }

        loadArtworkAndShowNotification(currentTitle, currentIsPlaying, currentArtworkUrl)
        return START_STICKY
    }

    private fun loadArtworkAndShowNotification(title: String, isPlaying: Boolean, artworkUrl: String) {
        val defaultBitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_launcher_foreground)

        if (artworkUrl.isEmpty()) {
            currentBitmap = defaultBitmap
            showNotification(title, isPlaying, defaultBitmap)
            return
        }

        val fullUrl = if (artworkUrl.startsWith("http://") || artworkUrl.startsWith("https://")) {
            artworkUrl
        } else {
            "file:///android_asset/www/$artworkUrl"
        }

        try {
            Glide.with(applicationContext)
                .asBitmap()
                .load(fullUrl)
                .into(object : CustomTarget<Bitmap>(512, 512) {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        currentBitmap = resource
                        showNotification(title, isPlaying, resource)
                    }

                    override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                    }

                    override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                        currentBitmap = defaultBitmap
                        showNotification(title, isPlaying, defaultBitmap)
                    }
                })
        } catch (e: Exception) {
            currentBitmap = defaultBitmap
            showNotification(title, isPlaying, defaultBitmap)
        }
    }

    private fun showNotification(title: String, isPlaying: Boolean, artBitmap: Bitmap?) {
        val bitmap = artBitmap ?: BitmapFactory.decodeResource(resources, R.drawable.ic_launcher_foreground)
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val actions = PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS

        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                .build()
        )

        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Ren Radio")
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "Lo-Fi Stream")
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, bitmap)
                .build()
        )

        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevPendingIntent = PendingIntent.getService(
            this, 1, Intent(this, AudioService::class.java).apply { action = ACTION_PREV },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val togglePendingIntent = PendingIntent.getService(
            this, 2, Intent(this, AudioService::class.java).apply {
                action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextPendingIntent = PendingIntent.getService(
            this, 3, Intent(this, AudioService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseTitle = if (isPlaying) "Pause" else "Play"

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Ren Radio · Streaming Live")
            .setSubText("Ren Radio")
            .setSmallIcon(R.drawable.ic_stat_name)
            .setLargeIcon(bitmap)
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .addAction(NotificationCompat.Action(android.R.drawable.ic_media_previous, "Previous", prevPendingIntent))
            .addAction(NotificationCompat.Action(playPauseIcon, playPauseTitle, togglePendingIntent))
            .addAction(NotificationCompat.Action(android.R.drawable.ic_media_next, "Next", nextPendingIntent))
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ren Radio Media Controls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Media controls and notification for Ren Radio background playback"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        mediaSession.release()
        instance = null
        super.onDestroy()
    }
}
