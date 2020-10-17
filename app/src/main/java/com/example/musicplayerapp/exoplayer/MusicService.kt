package com.example.musicplayerapp.exoplayer

import android.app.PendingIntent
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.media.MediaBrowserServiceCompat
import com.example.musicplayerapp.exoplayer.callbacks.MusicPlaybackPreparer
import com.example.musicplayerapp.exoplayer.callbacks.MusicPlayerEventListener
import com.example.musicplayerapp.exoplayer.callbacks.MusicPlayerNotificationListener
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
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
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    /**deals with cancellations of coroutines**/

    private lateinit var mediaSession: MediaSessionCompat  /**Allows interaction with media controllers, volume keys, media buttons, and
     * transport controls*/


    private lateinit var mediaSessionConnector: MediaSessionConnector  /**enable transport control to be displayed**/

    private lateinit var musicNotificationManager: MusicNotificationManager

    var isForegroundService = false

    private var curPlayingSong: MediaMetadataCompat? = null

    override fun onCreate() {
        super.onCreate()
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

        }

        /**setup music playback preparer**/
        val musicPlaybackPreparer = MusicPlaybackPreparer(firebaseMusicSource) {
            /***call everytime the user choose a new song**/
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

        /**set music player event listener**/
        exoPlayer.addListener(MusicPlayerEventListener(this))

        /**show notification**/
        musicNotificationManager.showNotification(exoPlayer)
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


    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot? {

    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {

    }
}