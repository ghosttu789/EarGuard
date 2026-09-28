package com.example.earguard

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService

/**
 * Watches your music apps. When one goes from PLAYING to PAUSED while Ear Guard is ON,
 * it presses PLAY again after a short moment. That undoes the pause the earbuds cause
 * when you take them out.
 */
class MediaWatcher : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())
    private val attached = ArrayList<Pair<MediaController, MediaController.Callback>>()
    private var lastResume = 0L
    private var msm: MediaSessionManager? = null

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { list -> attach(list) }

    override fun onListenerConnected() {
        try {
            val m = getSystemService(MediaSessionManager::class.java)
            msm = m
            val cn = ComponentName(this, MediaWatcher::class.java)
            m.addOnActiveSessionsChangedListener(sessionsListener, cn)
            attach(m.getActiveSessions(cn))
        } catch (e: SecurityException) { /* access not granted yet */ }
    }

    override fun onListenerDisconnected() {
        detachAll()
        try { msm?.removeOnActiveSessionsChangedListener(sessionsListener) } catch (e: Exception) {}
    }

    private fun detachAll() {
        for ((c, cb) in attached) { try { c.unregisterCallback(cb) } catch (e: Exception) {} }
        attached.clear()
    }

    private fun attach(list: List<MediaController>?) {
        detachAll()
        list?.forEach { c ->
            if (c.packageName == packageName) return@forEach   // ignore our own session
            val cb = object : MediaController.Callback() {
                var wasPlaying = c.playbackState?.state == PlaybackState.STATE_PLAYING
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    when (state?.state) {
                        PlaybackState.STATE_PLAYING -> wasPlaying = true
                        PlaybackState.STATE_PAUSED -> {
                            if (wasPlaying) {
                                wasPlaying = false
                                if (GuardService.running) handler.postDelayed({ resume(c) }, 400)
                            }
                        }
                        else -> {}
                    }
                }
            }
            c.registerCallback(cb, handler)
            attached.add(c to cb)
        }
    }

    private fun resume(c: MediaController) {
        if (c.playbackState?.state != PlaybackState.STATE_PAUSED) return
        val now = System.currentTimeMillis()
        if (now - lastResume < 2000) return                     // avoid loops
        val duration = c.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val pos = c.playbackState?.position ?: 0L
        if (duration > 0 && pos >= duration - 3000) return      // song simply ended
        lastResume = now
        c.transportControls.play()
    }
}
