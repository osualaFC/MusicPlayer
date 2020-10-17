package com.example.musicplayerapp.exoplayer

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.media.MediaBrowserServiceCompat
import com.example.musicplayerapp.exoplayer.callbacks.MusicPlaybackPreparer
import com.example.musicplayerapp.exoplayer.callbacks.MusicPlayerEventListener
import com.example.musicplayerapp.exoplayer.callbacks.MusicPlayerNotificationListener
import com.example.musicplayerapp.utils.Constants.MEDIA_ROOT_ID
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueNavigator
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

private const val SERVICE_TAG = "MusicService"

@AndroidEntryPoint
class MusicService : MediaBrowserServiceCompat() {

    @Inject
    lateinit var dataSourceFactory: DefaultDataSourceFactory

    @Inject
    lateinit var exoPlayer: SimpleExoPlayer

    @Inject
    lateinit var firebaseMusicSource: FirebaseMusicSource

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob) /**deals with cancellations of coroutines**/


    private lateinit var mediaSession: MediaSessionCompat  /**Allows interaction with media controllers, volume keys, media buttons, and
     * transport controls*/


    private lateinit var mediaSessionConnector: MediaSessionConnector  /**enable transport control to be displayed**/

    private lateinit var musicNotificationManager: MusicNotificationManager

    private lateinit var musicPlayerEventListener: MusicPlayerEventListener

    var isForegroundService = false

    private var curPlayingSong: MediaMetadataCompat? = null

    private var isPlayerInitialized= false

    companion object{
        var curSongDuration = 0L
            /**can only change the value within the service**/
            private set
    }

    override fun onCreate() {
        super.onCreate()

        /**launch music source**/
        serviceScope.launch {
            firebaseMusicSource.fetchMediaData()
        }

        /**intent for notification**/
        val activityIntent = packageManager?.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, 0)
        }

        mediaSession = MediaSessionCompat(this, SERVICE_TAG).apply {
            setSessionActivity(activityIntent)
            isActive = true
        }

        /***contains info about the mediaSession**/
        sessionToken = mediaSession.sessionToken

        /**initialize music notification manager***/
        musicNotificationManager = MusicNotificationManager(
            this,
            mediaSession.sessionToken,
            MusicPlayerNotificationListener(this)
        ) {
            /***update current duration of song played**/
            curSongDuration = exoPlayer.duration
        }

        /**setup music playback preparer**/
        val musicPlaybackPreparer = MusicPlaybackPreparer(firebaseMusicSource) {
            /***called every time the user choose a new song**/
            curPlayingSong = it
            /**plays the song if the user clicked on it***/
            preparePlayer(
                firebaseMusicSource.songs,
                it,
                true
            )
        }

        mediaSessionConnector = MediaSessionConnector(mediaSession)
        mediaSessionConnector.setPlayer(exoPlayer)
        mediaSessionConnector.setPlaybackPreparer(musicPlaybackPreparer)
        mediaSessionConnector.setQueueNavigator(MusicQueueNavigator())

        /**set music player event listener**/
        musicPlayerEventListener = MusicPlayerEventListener(this)
        exoPlayer.addListener(musicPlayerEventListener)

        /**show notification**/
        musicNotificationManager.showNotification(exoPlayer)
    }

    /***propagate music metadata to notification**/
    inner class MusicQueueNavigator: TimelineQueueNavigator(mediaSession){
        override fun getMediaDescription(player: Player, windowIndex: Int): MediaDescriptionCompat {
            return firebaseMusicSource.songs[windowIndex].description
        }
    }

    private fun preparePlayer(
        songs: List<MediaMetadataCompat>,
        itemToPlay: MediaMetadataCompat?,
        playNow: Boolean
    ) {
        /**first song or the song with the selected index**/
        val curSongIndex = if(curPlayingSong == null) 0 else songs.indexOf(itemToPlay)
        exoPlayer.prepare(firebaseMusicSource.asMediaSource(dataSourceFactory))

        /**makes sure every song starts from the beginning**/
        exoPlayer.seekTo(curSongIndex, 0L)
        exoPlayer.playWhenReady = playNow
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

    /**manages client that connects to root_id--primary file**/
    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot? {
        return BrowserRoot(MEDIA_ROOT_ID, null)
    }

    /** client can subscribe to a particular id --manages different files --playlist, album etc**/
    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        when(parentId){
            MEDIA_ROOT_ID ->{
                val resultSent = firebaseMusicSource.whenReady { isInitialized ->
                    if(isInitialized){
                        result.sendResult(firebaseMusicSource.asMediaItems())
                        if(!isPlayerInitialized && firebaseMusicSource.songs.isNotEmpty()){
                            preparePlayer(firebaseMusicSource.songs, firebaseMusicSource.songs[0], false)
                            isPlayerInitialized = true
                        }
                    }
                    else{
                        result.sendResult(null)
                    }

                }
                if(!resultSent){
                    result.detach()
                }
            }

        }

    }
}