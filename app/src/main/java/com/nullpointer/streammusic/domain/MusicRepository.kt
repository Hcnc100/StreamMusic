package com.nullpointer.streammusic.domain

import android.support.v4.media.MediaMetadataCompat
import com.nullpointer.streammusic.core.states.StatesSource
import com.nullpointer.streammusic.models.Song
import kotlinx.coroutines.flow.Flow

interface MusicRepository {
    suspend fun fetchSongMedia()
    fun addListenerReady(action:(Boolean)->Unit):Boolean
}