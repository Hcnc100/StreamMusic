package com.nullpointer.streammusic.data.remote

import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.app
import com.nullpointer.streammusic.models.Song
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await

class MusicDataSource {
    private val songsCollection = Firebase.firestore.collection("Songs")

    suspend fun getAllSongs(): List<Song> =
        songsCollection.get().await().toObjects(Song::class.java)

}