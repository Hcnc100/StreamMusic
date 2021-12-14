package com.nullpointer.streammusic.domain

import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import androidx.core.net.toUri
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.nullpointer.streammusic.core.states.StatesSource
import com.nullpointer.streammusic.data.remote.MusicDataSource
import com.nullpointer.streammusic.models.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class MusicRepositoryImpl(
    private val musicDataSource: MusicDataSource
) : MusicRepository {

    var listSongsMedia:List<MediaMetadataCompat> = emptyList()

    val listCallbackReady: MutableList<(Boolean) -> Unit> =
        mutableListOf()
    
    var stateResource: StatesSource =StatesSource.STATE_CREATED
        set(value) {
            if(value==StatesSource.STATE_INITIALIZED || value==StatesSource.STATE_ERROR){
                synchronized(listCallbackReady){
                    field=value
                    listCallbackReady.forEach {listener->
                        listener.invoke(stateResource==StatesSource.STATE_INITIALIZED)
                    }
                }
            }else{
                field=value
            }
        }


    override suspend fun fetchSongMedia() {
        stateResource = StatesSource.STATE_INITIALIZING
        listSongsMedia=musicDataSource.getAllSongs().map { song->
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST,song.subtitle)
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID,song.mediaId)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE,song.title)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE,song.title)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI,song.imgUrl)
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI,song.songUrl)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM,song.imgUrl)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE,song.subtitle)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION,song.subtitle)
                .build()
        }
        stateResource=StatesSource.STATE_INITIALIZED
    }

    fun asMediaSource(dataSourceFactory: DefaultDataSource.Factory)=
        listSongsMedia.map {song->
            ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(
                    song.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI).toUri()
                ))
        }

    

    fun asMediaItems()=listSongsMedia.map { songs->
        val desc= MediaDescriptionCompat.Builder()
            .setMediaUri(songs.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI).toUri())
            .setTitle(songs.description.title)
            .setSubtitle(songs.description.subtitle)
            .setMediaId(songs.description.mediaId)
            .setIconUri(songs.description.iconUri)
            .build()
        MediaBrowserCompat.MediaItem(desc, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
    }.toMutableList()

    override fun addListenerReady(action: (Boolean) -> Unit): Boolean {
        return if(stateResource==StatesSource.STATE_CREATED || stateResource==StatesSource.STATE_INITIALIZING){
            listCallbackReady+=action
            false
        }else{
            action(stateResource==StatesSource.STATE_INITIALIZED)
            true
        }
    }


}