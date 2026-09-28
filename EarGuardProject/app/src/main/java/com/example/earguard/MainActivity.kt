package com.example.earguard

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class BatteryView(ctx: Context) : View(ctx) {
    var level: Int = -1
        set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val capW = w * 0.05f
        val body = RectF(4f, 4f, w - capW - 6f, h - 4f)

        p.style = Paint.Style.STROKE; p.strokeWidth = h * 0.05f; p.color = Color.WHITE
        c.drawRoundRect(body, h * 0.22f, h * 0.22f, p)
        p.style = Paint.Style.FILL
        c.drawRoundRect(RectF(w - capW - 2f, h * 0.35f, w - 2f, h * 0.65f), 6f, 6f, p)

        if (level >= 0) {
            p.color = when {
                level >= 50 -> Color.parseColor("#3DDC84")
                level >= 20 -> Color.parseColor("#FFB020")
                else -> Color.parseColor("#FF4D4D")
            }
            val pad = h * 0.1f
            val right = body.left + pad + (body.width() - 2 * pad) * level / 100f
            c.drawRoundRect(RectF(body.left + pad, body.top + pad, right, body.bottom - pad),
                h * 0.14f, h * 0.14f, p)
        }

        p.color = Color.WHITE
        p.textAlign = Paint.Align.CENTER
        p.isFakeBoldText = true
        p.textSize = h * 0.42f
        p.setShadowLayer(6f, 0f, 2f, Color.BLACK)
        val text = if (level >= 0) "$level%" else "?"
        c.drawText(text, body.centerX(), body.centerY() + p.textSize * 0.35f, p)
        p.clearShadowLayer()
    }
}

class MainActivity : Activity() {

    private lateinit var pill: TextView
    private lateinit var earbudsBattery: BatteryView
    private lateinit var phoneBattery: BatteryView
    private lateinit var hint: TextView
    private lateinit var button: Button
    private lateinit var access: Button
    private val handler = Handler(Looper.getMainLooper())

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()

    private fun rounded(colors: IntArray, radiusDp: Int) = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT, colors
    ).apply { cornerRadius = dp(radiusDp).toFloat() }

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            this.text = text; textSize = size; setTextColor(color); gravity = Gravity.CENTER
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private val refresh = object : Runnable {
        override fun run() {
            val on = GuardService.running
            pill.text = if (on) "●  Protecting: ear pause blocked" else "●  Off"
            pill.background = rounded(
                if (on) intArrayOf(Color.parseColor("#1E9E5A"), Color.parseColor("#3DDC84"))
                else intArrayOf(Color.parseColor("#555B70"), Color.parseColor("#6E748A")), 20)

            button.text = if (on) "TURN OFF" else "TURN ON"
            button.background = rounded(
                if (on) intArrayOf(Color.parseColor("#FF416C"), Color.parseColor("#FF4B2B"))
                else intArrayOf(Color.parseColor("#7F5CFF"), Color.parseColor("#2F80ED")), 32)

            val b = GuardService.earbudBattery
            earbudsBattery.level = b
            hint.text = if (b < 0) "These earbuds don't report their battery" else ""

            val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            phoneBattery.level = if (level >= 0) level * 100 / scale else -1

            val granted = (Settings.Secure.getString(contentResolver,
                "enabled_notification_listeners") ?: "").contains(packageName)
            access.visibility = if (granted) View.GONE else View.VISIBLE

            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#1B1F3B")
        window.navigationBarColor = Color.parseColor("#0D0F1F")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#1B1F3B"), Color.parseColor("#0D0F1F")))
        }

        val title = label("Ear Guard", 32f, Color.WHITE, true)
        pill = label("", 14f, Color.WHITE, true).apply { setPadding(dp(18), dp(8), dp(18), dp(8)) }
        earbudsBattery = BatteryView(this)
        phoneBattery = BatteryView(this)
        hint = label("", 12f, Color.parseColor("#9AA0B8"))
        button = Button(this).apply {
            setTextColor(Color.WHITE); textSize = 18f; isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD; stateListAnimator = null
        }
        access = Button(this).apply {
            text = "Step 1: Allow media access"
            setTextColor(Color.WHITE); textSize = 15f; isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD; stateListAnimator = null
            background = rounded(intArrayOf(Color.parseColor("#FF9A3D"), Color.parseColor("#FF5E62")), 28)
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }
        button.setOnClickListener {
            val svc = Intent(this, GuardService::class.java)
            if (GuardService.running) stopService(svc) else startForegroundService(svc)
        }

        fun gap(h: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }

        root.addView(title)
        root.addView(gap(12))
        root.addView(pill)
        root.addView(gap(36))
        root.addView(label("EARBUDS", 13f, Color.parseColor("#9AA0B8"), true))
        root.addView(gap(8))
        root.addView(earbudsBattery, LinearLayout.LayoutParams(dp(240), dp(110)))
        root.addView(gap(4))
        root.addView(hint)
        root.addView(gap(28))
        root.addView(label("PHONE", 13f, Color.parseColor("#9AA0B8"), true))
        root.addView(gap(8))
        root.addView(phoneBattery, LinearLayout.LayoutParams(dp(130), dp(56)))
        root.addView(gap(28))
        root.addView(access, LinearLayout.LayoutParams(dp(260), dp(52)))
        root.addView(gap(16))
        root.addView(button, LinearLayout.LayoutParams(dp(260), dp(64)))
        setContentView(root)

        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        if (perms.isNotEmpty()) requestPermissions(perms.toTypedArray(), 1)
    }

    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(refresh) }
}
