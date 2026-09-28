package com.example.earguard

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var earbuds: TextView
    private lateinit var phone: TextView
    private lateinit var button: Button
    private val handler = Handler(Looper.getMainLooper())

    private val refresh = object : Runnable {
        override fun run() {
            val on = GuardService.running
            status.text = if (on) "Ear Guard: ON (pause/play blocked)" else "Ear Guard: OFF"
            button.text = if (on) "Turn OFF" else "Turn ON"

            val b = GuardService.earbudBattery
            earbuds.text = "Earbuds battery: " + if (b >= 0) "$b%" else "unknown"

            val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            phone.text = "Phone battery: " + if (level >= 0) "${level * 100 / scale}%" else "unknown"

            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        status = TextView(this).apply { textSize = 20f; gravity = Gravity.CENTER }
        earbuds = TextView(this).apply { textSize = 28f; gravity = Gravity.CENTER; setPadding(0, 48, 0, 8) }
        phone = TextView(this).apply { textSize = 18f; gravity = Gravity.CENTER; setPadding(0, 0, 0, 48) }
        button = Button(this)

        button.setOnClickListener {
            val svc = Intent(this, GuardService::class.java)
            if (GuardService.running) stopService(svc) else startForegroundService(svc)
        }

        root.addView(status); root.addView(earbuds); root.addView(phone); root.addView(button)
        setContentView(root)

        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        if (perms.isNotEmpty()) requestPermissions(perms.toTypedArray(), 1)
    }

    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(refresh) }
}
