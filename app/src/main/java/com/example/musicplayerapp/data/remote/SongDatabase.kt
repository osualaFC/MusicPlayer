package com.example.musicplayerapp.data.remote

import com.example.musicplayerapp.data.entities.Song
import com.example.musicplayerapp.utils.Constants.SONG_COLLECTION
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class SongDatabase {

    private val firestore = FirebaseFirestore.getInstance()
    private val songCollection = firestore.collection(SONG_COLLECTION)

    /**get all songs from firestore**/
    suspend fun getAllSongs(): List<Song> {
        return try {
            songCollection.get().await().toObjects(Song::class.java)
        } catch(e: Exception) {
            emptyList()
        }
    }
}
/**
 * await() returns any--so use toObjects to specify the type of objects
 * **/