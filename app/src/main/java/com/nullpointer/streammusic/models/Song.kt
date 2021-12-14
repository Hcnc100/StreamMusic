package com.nullpointer.streammusic.models

import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat

data class Song(
    val mediaId: String = "",
    val title: String = "",
    val subtitle: String = "",
    val songUrl: String = "",
    val imgUrl: String = ""
) {
    companion object {
        fun fromMediaItem(mediaItem: MediaBrowserCompat.MediaItem): Song =
            Song(
                mediaItem.mediaId.toString(),
                mediaItem.description.title.toString(),
                mediaItem.description.subtitle.toString(),
                mediaItem.description.mediaUri.toString(),
                mediaItem.description.iconUri.toString()
            )

        fun fromMediaMetadata(mediaMetadataCompat: MediaMetadataCompat?):Song?{
            return mediaMetadataCompat?.description?.let {
                Song(
                    it.mediaId.toString(),
                    it.title.toString(),
                    it.subtitle.toString(),
                    it.mediaId.toString(),
                    it.iconUri.toString()
                )
            }
        }
    }
}
