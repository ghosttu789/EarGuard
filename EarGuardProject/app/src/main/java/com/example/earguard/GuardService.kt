package com.example.earguard

import android.app.*
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.content.*
import android.content.pm.ServiceInfo
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class GuardService : Service() {

    companion object {
        @Volatile var earbudBattery: Int = -1   // -1 = unknown
        @Volatile var running = false
        const val CHANNEL = "earguard"
        const val NOTIF_ID = 1
    }

    private lateinit var session: MediaSession
    private val handler = Handler(Looper.getMainLooper())

    // AirPods (and many clones) report battery through Apple's +IPHONEACCEV command
    private val vendorReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val args = i.extras?.get(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_ARGS)
                    as? Array<*> ?: return
            val n = args.getOrNull(0)?.toString()?.toIntOrNull() ?: return
            for (k in 0 until n) {
                val key = args.getOrNull(1 + 2 * k)?.toString()?.toIntOrNull()
                val value = args.getOrNull(2 + 2 * k)?.toString()?.toIntOrNull()
                if (key == 1 && value != null) {      // key 1 = battery, value 0..9
                    earbudBattery = (value + 1) * 10
                    updateNotification()
                }
            }
        }
    }

    // Fallback: ask Android for the connected device's battery (hidden API, may not work)
    private val poll = object : Runnable {
        override fun run() {
            if (earbudBattery < 0) readBatteryViaReflection()
            updateNotification()
            handler.postDelayed(this, 30_000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        running = true

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Ear Guard", NotificationManager.IMPORTANCE_LOW)
        )

        session = MediaSession(this, "EarGuard").apply {
            setCallback(object : MediaSession.Callback() {
                // Returning true = "handled", so pause/play never reaches your music app
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean = true
            })
            setPlaybackState(
                PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                            PlaybackState.ACTION_PLAY_PAUSE)
                    .setState(PlaybackState.STATE_PLAYING, 0, 1f)
                    .build()
            )
            isActive = true
        }

        val filter = IntentFilter(BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT)
        filter.addCategory(BluetoothHeadset.VENDOR_SPECIFIC_HEADSET_EVENT_COMPANY_ID_CATEGORY + ".76")
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(vendorReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(vendorReceiver, filter)
        }

        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, n)
        }
        handler.post(poll)
    }

    private fun readBatteryViaReflection() {
        try {
            val adapter = getSystemService(BluetoothManager::class.java).adapter ?: return
            for (d: BluetoothDevice in adapter.bondedDevices) {
                val level = BluetoothDevice::class.java.getMethod("getBatteryLevel").invoke(d) as Int
                if (level in 0..100) { earbudBattery = level; return }
            }
        } catch (e: Exception) { /* not available on this phone */ }
    }

    private fun buildNotification(): Notification {
        val text = if (earbudBattery >= 0) "Earbuds battery: $earbudBattery%"
                   else "Earbuds battery: unknown"
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("Ear Guard is on")
            .setContentText("$text  •  ear pause blocked")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        try { unregisterReceiver(vendorReceiver) } catch (e: Exception) {}
        session.isActive = false
        session.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
