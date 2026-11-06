package com.sysutils.batteryguard

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

class OverlayWidgetService : Service() {

    companion object {
        const val ACTION_SHOW_WIDGET = "com.sysutils.batteryguard.SHOW_WIDGET"
        const val ACTION_HIDE_WIDGET = "com.sysutils.batteryguard.HIDE_WIDGET"
        const val ACTION_REFRESH_STYLE = "com.sysutils.batteryguard.REFRESH_STYLE"

        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private lateinit var windowManager: WindowManager
    private var rootLayout: FrameLayout? = null
    private var mainCircle: LinearLayout? = null
    private var statusTitleText: TextView? = null
    private var timerSubText: TextView? = null
    private var windowParams: WindowManager.LayoutParams? = null

    private var countDownTimer: CountDownTimer? = null
    private var remainingSeconds: Int = 0
    private var isMinimized: Boolean = false

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateWidgetAppearance()
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForegroundNotification()
        registerStateReceiver()
        createOverlayWidget()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE_WIDGET -> {
                stopVpnIfActive()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_REFRESH_STYLE -> {
                applySizeAndAlpha()
                updateWidgetAppearance()
            }
        }
        return START_STICKY
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerStateReceiver() {
        val filter = IntentFilter(LocalSinkholeVpnService.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stateReceiver, filter)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createOverlayWidget() {
        if (rootLayout != null) return

        val savedPos = PrefsManager.getSavedPosition(this)
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedPos.first
            y = savedPos.second
        }
        windowParams = params

        val container = FrameLayout(this)

        val circle = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            elevation = dpToPx(6).toFloat()
        }

        val titleTv = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        }

        val subTv = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#E0E0E0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        }

        circle.addView(titleTv)
        circle.addView(subTv)
        container.addView(circle)

        rootLayout = container
        mainCircle = circle
        statusTitleText = titleTv
        timerSubText = subTv

        applySizeAndAlpha()
        updateWidgetAppearance()

        // Обработка касаний: одиночный клик = Старт/Стоп блокировки,
        // двойной клик или долгое нажатие = свернуть/развернуть кнопку в мини-точку,
        // перетаскивание пальцем = переместить в удобное место экрана.
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        var downTime = 0L

        container.setOnTouchListener { _, event ->
            val lp = windowParams ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = lp.x
                    initialY = lp.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    downTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > dpToPx(8) || abs(dy) > dpToPx(8)) {
                        isDragging = true
                        lp.x = initialX + dx
                        lp.y = initialY + dy
                        try {
                            windowManager.updateViewLayout(container, lp)
                        } catch (_: Exception) {
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        PrefsManager.savePosition(this, lp.x, lp.y)
                    } else {
                        val pressDuration = System.currentTimeMillis() - downTime
                        if (pressDuration > 650L) {
                            // Долгое удержание (0.65 сек) — компактный мини-режим / обычный режим
                            isMinimized = !isMinimized
                            vibrateShort(30)
                            applySizeAndAlpha()
                            updateWidgetAppearance()
                        } else {
                            // Короткое нажатие — мгновенное включение/выключение паузы сети
                            toggleNetworkFreeze()
                        }
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(container, params)
    }

    private fun toggleNetworkFreeze() {
        vibrateShort(45)
        if (LocalSinkholeVpnService.isBlockingActive) {
            // Если уже активно — выключаем и возвращаем связь мгновенно
            stopVpnIfActive()
        } else {
            // Включаем локальный фильтр на заданный таймер
            val seconds = PrefsManager.getTimerSeconds(this)
            val startIntent = Intent(this, LocalSinkholeVpnService::class.java).apply {
                action = LocalSinkholeVpnService.ACTION_START_BLOCK
            }
            startService(startIntent)
            startCountdown(seconds)
        }
    }

    private fun startCountdown(totalSeconds: Int) {
        countDownTimer?.cancel()
        remainingSeconds = totalSeconds
        updateWidgetAppearance()

        countDownTimer = object : CountDownTimer(totalSeconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                remainingSeconds = ((millisUntilFinished + 999) / 1000).toInt()
                updateWidgetAppearance()
            }

            override fun onFinish() {
                remainingSeconds = 0
                vibrateShort(70)
                stopVpnIfActive()
            }
        }.start()
    }

    private fun stopVpnIfActive() {
        countDownTimer?.cancel()
        countDownTimer = null
        remainingSeconds = 0
        val stopIntent = Intent(this, LocalSinkholeVpnService::class.java).apply {
            action = LocalSinkholeVpnService.ACTION_STOP_BLOCK
        }
        startService(stopIntent)
        updateWidgetAppearance()
    }

    private fun applySizeAndAlpha() {
        val circle = mainCircle ?: return
        val baseSizeDp = PrefsManager.getButtonSizeDp(this)
        val sizePx = if (isMinimized) dpToPx(34) else dpToPx(baseSizeDp)
        circle.layoutParams = FrameLayout.LayoutParams(sizePx, sizePx)
        rootLayout?.alpha = PrefsManager.getButtonAlpha(this)
    }

    private fun updateWidgetAppearance() {
        val circle = mainCircle ?: return
        val titleTv = statusTitleText ?: return
        val subTv = timerSubText ?: return

        val active = LocalSinkholeVpnService.isBlockingActive

        val bgDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            if (active) {
                // Красный цвет — трафик целевого приложения заморожен (пауза связи)
                setColor(Color.parseColor("#D32F2F"))
                setStroke(dpToPx(2), Color.parseColor("#FFCDD2"))
            } else {
                // Тёмно-зелёный / графитовый — интернет работает штатно
                setColor(Color.parseColor("#2E7D32"))
                setStroke(dpToPx(2), Color.parseColor("#A5D6A7"))
            }
        }
        circle.background = bgDrawable

        if (isMinimized) {
            titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            titleTv.text = if (active) "${remainingSeconds}" else "●"
            subTv.visibility = View.GONE
        } else {
            subTv.visibility = View.VISIBLE
            if (active) {
                titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                titleTv.text = "${remainingSeconds}с"
                subTv.text = "ПАУЗА"
            } else {
                titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                titleTv.text = "СЕТЬ"
                subTv.text = "${PrefsManager.getTimerSeconds(this)} сек"
            }
        }
    }

    private fun vibrateShort(durationMs: Long) {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        } catch (_: Exception) {
        }
    }

    private fun startForegroundNotification() {
        val channelId = "battery_saver_overlay_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "System Battery Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background battery optimization control"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val notification = builder
            .setContentTitle("Battery Guard активен")
            .setContentText("Быстрая кнопка управления сетью включена")
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(1001, notification)
    }

    private fun dpToPx(dp: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()

    override fun onDestroy() {
        isRunning = false
        countDownTimer?.cancel()
        try {
            unregisterReceiver(stateReceiver)
        } catch (_: Exception) {
        }
        rootLayout?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        rootLayout = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
