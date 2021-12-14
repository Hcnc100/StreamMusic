package com.nullpointer.streammusic.services.exoPlayer

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.ResultReceiver
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.content.ContextCompat
import androidx.media.MediaBrowserServiceCompat
import coil.imageLoader
import coil.request.ImageRequest
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueNavigator
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.nullpointer.streammusic.R
import com.nullpointer.streammusic.core.constants.Constants
import com.nullpointer.streammusic.core.constants.Constants.MEDIA_ROOT_ID
import com.nullpointer.streammusic.core.constants.Constants.NETWORK_ERROR
import com.nullpointer.streammusic.core.constants.Constants.NOTIFICATION_ID
import com.nullpointer.streammusic.domain.MusicRepositoryImpl
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import timber.log.Timber
import javax.inject.Inject
import android.app.NotificationManager

import android.app.NotificationChannel
import android.os.Build


@ExperimentalCoroutinesApi
@AndroidEntryPoint
class MusicServices : MediaBrowserServiceCompat() {

    companion object{
        var currentSongDuration=0L
            private set
    }

    //lifecycle
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)


    //Exoplayer
    @Inject
    lateinit var exoPlayer: ExoPlayer
    @Inject
    lateinit var dataSourceFactory: DefaultDataSource.Factory
    @Inject
    lateinit var musicRepo: MusicRepositoryImpl
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var mediaSessionConnector: MediaSessionConnector
    private lateinit var musicPlayerEventListener: Player.Listener

    private var isForegroundService = false
    private var currentPlayingSong: MediaMetadataCompat? = null

    private var isPlayerInitialized = false


    override fun onCreate() {
        super.onCreate()

        //request player songs from database
        serviceScope.launch {
            musicRepo.fetchSongMedia()
        }

        //get intent for notification
        val activityIntent = packageManager?.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, 0)
        }

        //get media session for media control
        //this allow the interaction with media controllers, volume keys, etx
        mediaSession = MediaSessionCompat(this, "Music Services").apply {
            setSessionActivity(activityIntent)
            isActive = true
        }.also {
            //this token can use for control this session
            sessionToken = it.sessionToken
        }

        //this class allow the communication between session and the interface in this case
        mediaSessionConnector = MediaSessionConnector(mediaSession).apply {
            //this class prepare the songs
            setPlaybackPreparer(MyPlaybackPreparer())
            //this class allow the action as skip, next
            setQueueNavigator(MusicQueueNavigation())
            setPlayer(exoPlayer)
        }

        //add listener to music
        exoPlayer.addListener(getMusicEventListener().also {
            musicPlayerEventListener = it
        })

        //enable notification from services
        MusicNotificationManager().apply {
            showNotification(exoPlayer)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        exoPlayer.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        exoPlayer.removeListener(musicPlayerEventListener)
        exoPlayer.release()
    }


    /**
     * prepare the list songs,consideration the song passed for parameter
     * else take the first song, and play when is ready
     * */
    private fun preparePlayer(
        songs: List<MediaMetadataCompat>,
        itemToPLay: MediaMetadataCompat?,
        playNow: Boolean
    ) = with(exoPlayer) {
        val currentSongIndex = if (currentPlayingSong == null) 0 else songs.indexOf(itemToPLay)
        setMediaSources(musicRepo.asMediaSource(
            dataSourceFactory
        ))
        prepare()
        seekTo(currentSongIndex, 0L)
        playWhenReady = playNow
    }

    /**
     * Return listener to change state in the player
     * */
    private fun getMusicEventListener() = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            super.onPlaybackStateChanged(playbackState)
            if (playbackState == Player.STATE_READY) {
                stopForeground(false)
            }

        }

        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error)
            Timber.d("Error al preparar las canciónes")
        }
    }


    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot = BrowserRoot(MEDIA_ROOT_ID, null)

    /**
     * Return list with information about this songs
     * */
    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        when (parentId) {
            MEDIA_ROOT_ID -> {
                //how this media is load in async method send detach
                //after, send the current result when is ready
                val resultSend = musicRepo.addListenerReady { isInitialized ->
                    //if the init is success send the media
                    if (isInitialized) {
                        //get media and convert in Media Item
                        result.sendResult(musicRepo.asMediaItems())
                        //if the player is no init, and the songs is not empty, prepare
                        //the songs, but no play thi song
                        if (!isPlayerInitialized && musicRepo.listSongsMedia.isNotEmpty()) {
                            preparePlayer(
                                musicRepo.listSongsMedia,
                                musicRepo.listSongsMedia[0],
                                false
                            )
                            isPlayerInitialized = true
                        }
                    } else {
                        //else send null and network error
                        mediaSession.sendSessionEvent(NETWORK_ERROR, null)
                        result.sendResult(null)
                    }
                }
                if (!resultSend) {
                    result.detach()
                }
            }
            else -> Unit
        }
    }

    /**
     * This class prepare the songs
     * */
    private inner class MyPlaybackPreparer : MediaSessionConnector.PlaybackPreparer {
        override fun getSupportedPrepareActions(): Long =
            PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID or
                    PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID

        //when the media is ready, get the media and call to prepare function,
        // passed the song from mediaId
        override fun onPrepareFromMediaId(
            mediaId: String,
            playWhenReady: Boolean,
            extras: Bundle?
        ) {
            musicRepo.addListenerReady {
                val itemToPlay = musicRepo.listSongsMedia.find { mediaId == it.description.mediaId }
                currentPlayingSong = itemToPlay
                preparePlayer(
                    musicRepo.listSongsMedia, itemToPlay, true
                )
            }

        }

        override fun onPrepare(playWhenReady: Boolean) = Unit

        override fun onCommand(
            player: Player,
            command: String,
            extras: Bundle?,
            cb: ResultReceiver?
        ): Boolean = false

        override fun onPrepareFromSearch(query: String, playWhenReady: Boolean, extras: Bundle?) =
            Unit

        override fun onPrepareFromUri(uri: Uri, playWhenReady: Boolean, extras: Bundle?) = Unit
    }

    /**
     * This class prepare the notification from services
     * */
    private inner class MusicNotificationManager {
        private val notificationManager: PlayerNotificationManager

        init {
            //in the new versions from android, need a notification channel
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager =  getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                val notificationChannel = NotificationChannel(
                    Constants.NOTIFICATION_CHANNEL_ID,
                    "Player",
                    NotificationManager.IMPORTANCE_LOW
                )
                notificationManager.createNotificationChannel(notificationChannel)
            }

            //and create a notification Manager from player
            notificationManager = PlayerNotificationManager.Builder(
                this@MusicServices, NOTIFICATION_ID,
                Constants.NOTIFICATION_CHANNEL_ID
            ).apply {
                //add the current information for songs
                //and add the actions from player notification
                setMediaDescriptionAdapter(DescriptionAdapter())
                setNotificationListener(getNotificationListener())
            }.build().apply {
                setSmallIcon(R.drawable.ic_launcher_foreground)
                setMediaSessionToken(sessionToken!!)
            }
        }

        //add player to the notification
        fun showNotification(player: Player) {
            notificationManager.setPlayer(player)
        }


        private fun getNotificationListener() =
            object : PlayerNotificationManager.NotificationListener {

                //when the notification is cancel, stop the services
                override fun onNotificationCancelled(
                    notificationId: Int,
                    dismissedByUser: Boolean
                ) {
                    super.onNotificationCancelled(notificationId, dismissedByUser)
                    isForegroundService = false
                    stopForeground(true)
                    stopSelf()
                }

                //when the notification is  posted, start the services
                override fun onNotificationPosted(
                    notificationId: Int,
                    notification: Notification,
                    ongoing: Boolean
                ) {
                    super.onNotificationPosted(notificationId, notification, ongoing)
                    if (ongoing && !isForegroundService) {
                        ContextCompat.startForegroundService(
                            this@MusicServices,
                            Intent(applicationContext,MusicServices::class.java)
                        )
                        startForeground(NOTIFICATION_ID, notification)
                        isForegroundService = true
                    }
                }
            }

        /**
         * This class get the current song information
         * */
        private inner class DescriptionAdapter
            : PlayerNotificationManager.MediaDescriptionAdapter {

            private val mediaControllerCompat =
                MediaControllerCompat(this@MusicServices, sessionToken!!)

            override fun getCurrentContentTitle(player: Player): CharSequence {
                currentSongDuration=exoPlayer.duration
                return mediaControllerCompat.metadata.description.title.toString()
            }

            override fun createCurrentContentIntent(player: Player): PendingIntent? =
                mediaControllerCompat.sessionActivity

            override fun getCurrentContentText(player: Player): CharSequence =
                mediaControllerCompat.metadata.description.subtitle.toString()

            override fun getCurrentLargeIcon(
                player: Player,
                callback: PlayerNotificationManager.BitmapCallback
            ): Bitmap? {
                val request = ImageRequest.Builder(this@MusicServices)
                    .data(mediaControllerCompat.metadata.description.iconUri)
                    .target { result: Drawable ->
                        (result as BitmapDrawable).bitmap.also {
                            callback.onBitmap(it)
                        }
                    }.build()
                this@MusicServices.imageLoader.enqueue(request)
                return null
            }

        }
    }

    /**
    * This class provides the songs navigation, is important for actions as
    * next, prev, etc
    * */
    private inner class MusicQueueNavigation : TimelineQueueNavigator(mediaSession) {
        override fun getMediaDescription(player: Player, windowIndex: Int): MediaDescriptionCompat {
            return musicRepo.listSongsMedia[windowIndex].description
        }
    }
}