package com.nullpointer.streammusic.presentation

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nullpointer.streammusic.core.constants.Constants.MEDIA_ROOT_ID
import com.nullpointer.streammusic.core.constants.Constants.NETWORK_ERROR
import com.nullpointer.streammusic.core.constants.Constants.UPDATE_PLAYER_POSITION_INTERVAL
import com.nullpointer.streammusic.core.states.Resource
import com.nullpointer.streammusic.domain.MusicRepositoryImpl
import com.nullpointer.streammusic.models.Song
import com.nullpointer.streammusic.services.exoPlayer.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@ExperimentalCoroutinesApi
@HiltViewModel
class SongsViewModel @Inject constructor(
    private val songRepositoryImpl: MusicRepositoryImpl,
    application: Application
) : AndroidViewModel(application) {

    private val context: Context
        get() = getApplication()

    private val musicServiceConnection = MusicServiceConnection()

    private val _isConnected = MutableStateFlow<Resource<Boolean>>(Resource.Loading())
    val isConnected: StateFlow<Resource<Boolean>> get() = _isConnected

    private val _networkError = MutableStateFlow<Resource<Boolean>>(Resource.Loading())
    val networkError: StateFlow<Resource<Boolean>> get() = _networkError

    private val _playbackState = MutableStateFlow<PlaybackStateCompat?>(null)
    val playbackState: StateFlow<PlaybackStateCompat?> get() = _playbackState

    private val _currentPlayingSong = MutableStateFlow<MediaMetadataCompat?>(null)
    val currentPlayingSong: StateFlow<MediaMetadataCompat?> get() = _currentPlayingSong


    private val _currentSongDuration = MutableStateFlow(0L)
    val currentSongDuration: StateFlow<Long> get() = _currentSongDuration

    private val _currentPlayingPosition = MutableStateFlow(0L)
    val currentPlayingPosition: StateFlow<Long> get() = _currentPlayingPosition

    init {
        updateCurrentPlayerPosition()
    }

    fun playOrToggleSong(mediaItem:Song, toggle:Boolean=false){
        //get the state of player, if the song is not currently playing prepare another
        //else toggle the play/pause state
        val isPrepared = playbackState.value?.isPrepared ?: false
        if(isPrepared && mediaItem.mediaId== currentPlayingSong.value?.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)){
            playbackState.value?.let { playbackState->
                when{
                    playbackState.isPlaying->if(toggle) musicServiceConnection.transportControls.pause()
                    playbackState.isPlayingEnable->musicServiceConnection.transportControls.play()
                    else ->Unit
                }
            }
        }else{
            musicServiceConnection.transportControls.playFromMediaId(mediaItem.mediaId,null)
        }
    }

    private fun updateCurrentPlayerPosition() = viewModelScope.launch {
        while (true) {
            val pos = playbackState.value?.currentPlaybackPosition
            if (currentPlayingPosition.value != pos) {
                _currentPlayingPosition.value = pos ?: 0
                _currentSongDuration.value = MusicServices.currentSongDuration
            }
            delay(UPDATE_PLAYER_POSITION_INTERVAL)
        }
    }


    //get the list songs by the music services
    val listSongByUser: StateFlow<Resource<List<Song>>> = callbackFlow {
        musicServiceConnection.subscribe(MEDIA_ROOT_ID,
            object : MediaBrowserCompat.SubscriptionCallback() {
                override fun onChildrenLoaded(
                    parentId: String,
                    children: MutableList<MediaBrowserCompat.MediaItem>
                ) {
                    super.onChildrenLoaded(parentId, children)
                    val items = children.map { Song.fromMediaItem(it) }
                    trySend(Resource.Success(items))
                    close()
                }
            }
        )
        awaitClose{
            Timber.d("Flow closed")
        }

    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        Resource.Loading()
    )

    //control the music actions
    fun skipToNextSong() =
        musicServiceConnection.transportControls.skipToNext()

    fun skipToPreviousSong() =
        musicServiceConnection.transportControls.skipToPrevious()

    fun seekTo(pos: Long) =
        musicServiceConnection.transportControls.seekTo(pos)

    /**
     * This class is use to the connection from the music services
     * */
    inner class MusicServiceConnection {

        lateinit var mediaController: MediaControllerCompat

        private val mediaBrowserConnectionCallback = MediaBrowserConnectionCallback(context)

        private val mediaBrowser = MediaBrowserCompat(
            context,
            ComponentName(
                context,
                MusicServices::class.java
            ), mediaBrowserConnectionCallback, null
        ).apply {
            connect()
        }

        val transportControls: MediaControllerCompat.TransportControls
            get() = mediaController.transportControls

        fun subscribe(parenId: String, callback: MediaBrowserCompat.SubscriptionCallback) {
            mediaBrowser.subscribe(parenId, callback)
        }

        fun unsubscribe(parenId: String, callback: MediaBrowserCompat.SubscriptionCallback) {
            mediaBrowser.unsubscribe(parenId, callback)
        }

        private inner class MediaBrowserConnectionCallback(
            private val context: Context
        ) : MediaBrowserCompat.ConnectionCallback() {
            override fun onConnected() {
                mediaController = MediaControllerCompat(context, mediaBrowser.sessionToken).apply {
                    registerCallback(MediaControllerCallback())
                }
                _isConnected.value = Resource.Success(true)
            }

            override fun onConnectionSuspended() {
                super.onConnectionSuspended()
                _isConnected.value = Resource.Failure(Exception("The connection was suspended"))
            }

            override fun onConnectionFailed() {
                super.onConnectionFailed()
                _isConnected.value =
                    Resource.Failure(Exception("Couldn't connect to media browser"))
            }
        }

        private inner class MediaControllerCallback : MediaControllerCompat.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
                _playbackState.value = state
            }

            override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
                _currentPlayingSong.value = metadata
            }

            override fun onSessionEvent(event: String?, extras: Bundle?) {
                super.onSessionEvent(event, extras)
                when (event) {
                    NETWORK_ERROR -> _networkError.value = Resource.Failure(
                        Exception("Couldn't connect to the server. Please check your internet connection.")
                    )
                }
            }

            override fun onSessionDestroyed() {
                mediaBrowserConnectionCallback.onConnectionSuspended()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        musicServiceConnection.unsubscribe(MEDIA_ROOT_ID,
            object : MediaBrowserCompat.SubscriptionCallback() {})
    }

}