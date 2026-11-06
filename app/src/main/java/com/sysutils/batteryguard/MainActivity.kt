package com.sysutils.batteryguard

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var targetPkgInput: EditText
    private lateinit var timerValueText: TextView
    private lateinit var alphaValueText: TextView
    private lateinit var sizeValueText: TextView
    private lateinit var statusBannerText: TextView
    private lateinit var toggleOverlayBtn: Button

    companion object {
        private const val REQ_VPN_PERMISSION = 201
        private const val REQ_OVERLAY_PERMISSION = 202
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUserInterface()
    }

    override fun onResume() {
        super.onResume()
        refreshStatusBanner()
    }

    private fun buildUserInterface() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#12151C"))
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(32))
        }

        // Заголовок
        val headerTitle = TextView(this).apply {
            text = "System Battery & Net Guard"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.DEFAULT_BOLD
        }
        val headerSubtitle = TextView(this).apply {
            text = "Локальный таймер изоляции трафика выбранного приложения без отключения 4G/LTE и GPS"
            setTextColor(Color.parseColor("#9AA5B4"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(4), 0, dp(16))
        }
        root.addView(headerTitle)
        root.addView(headerSubtitle)

        // Статус-баннер
        statusBannerText = TextView(this).apply {
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
        }
        root.addView(statusBannerText, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(18) })

        // Карточка 1: Целевое приложение
        val appCard = createCardLayout()
        appCard.addView(createSectionTitle("1. Какое приложение изолировать"))
        appCard.addView(createHelperText(
            "Трафик блокируется только для указанного пакета. Навигатор, система и остальные приложения продолжают работать с интернетом."
        ))

        targetPkgInput = EditText(this).apply {
            setText(PrefsManager.getTargetPackage(this@MainActivity))
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#6E7785"))
            hint = "Например: ru.yandex.taximeter"
            setBackgroundColor(Color.parseColor("#151922"))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        appCard.addView(targetPkgInput, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val pickInstalledBtn = createSecondaryButton("Выбрать из списка").apply {
            setOnClickListener { showInstalledAppsDialog() }
        }
        val presetTaxiBtn = createSecondaryButton("Яндекс Про").apply {
            setOnClickListener {
                targetPkgInput.setText("ru.yandex.taximeter")
                PrefsManager.setTargetPackage(this@MainActivity, "ru.yandex.taximeter")
                Toast.makeText(this@MainActivity, "Установлен пакет ru.yandex.taximeter", Toast.LENGTH_SHORT).show()
            }
        }

        btnRow.addView(pickInstalledBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            rightMargin = dp(6)
        })
        btnRow.addView(presetTaxiBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = dp(6)
        })
        appCard.addView(btnRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) })

        root.addView(appCard, cardMarginParams())

        // Карточка 2: Таймер автоотключения
        val timerCard = createCardLayout()
        timerCard.addView(createSectionTitle("2. Таймер заморозки связи"))
        timerCard.addView(createHelperText(
            "При нажатии на плавающую кнопку связь для выбранного приложения мгновенно замирает на это время, а затем автоматически включается обратно."
        ))

        val currentTimer = PrefsManager.getTimerSeconds(this)
        timerValueText = TextView(this).apply {
            text = "Длительность паузы: $currentTimer сек."
            setTextColor(Color.parseColor("#64B5F6"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(10), 0, dp(6))
        }
        timerCard.addView(timerValueText)

        val timerSeek = SeekBar(this).apply {
            max = 115 // от 5 до 120 секунд
            progress = (currentTimer - 5).coerceIn(0, 115)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val sec = progress + 5
                    timerValueText.text = "Длительность паузы: $sec сек."
                    PrefsManager.setTimerSeconds(this@MainActivity, sec)
                    notifyOverlayRefresh()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        timerCard.addView(timerSeek)

        // Быстрые пресеты таймера
        val presetsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        listOf(15, 30, 45, 60).forEach { sec ->
            val btn = createSecondaryButton("${sec}с").apply {
                setOnClickListener {
                    PrefsManager.setTimerSeconds(this@MainActivity, sec)
                    timerSeek.progress = sec - 5
                    timerValueText.text = "Длительность паузы: $sec сек."
                    notifyOverlayRefresh()
                }
            }
            presetsLayout.addView(btn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(4)
            })
        }
        timerCard.addView(presetsLayout, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

        root.addView(timerCard, cardMarginParams())

        // Карточка 3: Внешний вид и размер плавающей кнопки
        val styleCard = createCardLayout()
        styleCard.addView(createSectionTitle("3. Настройка плавающей кнопки"))
        styleCard.addView(createHelperText(
            "• Одиночное нажатие — включить/выключить паузу сети.\n" +
            "• Перетаскивание пальцем — подвинуть кнопку в любое место.\n" +
            "• Долгое удержание (0.7 сек) — свернуть в незаметную точку."
        ))

        val curAlpha = (PrefsManager.getButtonAlpha(this) * 100).toInt()
        alphaValueText = TextView(this).apply {
            text = "Непрозрачность кнопки: $curAlpha%"
            setTextColor(Color.WHITE)
            setPadding(0, dp(10), 0, dp(4))
        }
        styleCard.addView(alphaValueText)

        val alphaSeek = SeekBar(this).apply {
            max = 75 // от 25% до 100%
            progress = (curAlpha - 25).coerceIn(0, 75)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val pct = progress + 25
                    alphaValueText.text = "Непрозрачность кнопки: $pct%"
                    PrefsManager.setButtonAlpha(this@MainActivity, pct / 100f)
                    notifyOverlayRefresh()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        styleCard.addView(alphaSeek)

        val curSize = PrefsManager.getButtonSizeDp(this)
        sizeValueText = TextView(this).apply {
            text = "Размер кнопки: $curSize dp"
            setTextColor(Color.WHITE)
            setPadding(0, dp(10), 0, dp(4))
        }
        styleCard.addView(sizeValueText)

        val sizeSeek = SeekBar(this).apply {
            max = 52 // от 44 до 96 dp
            progress = (curSize - 44).coerceIn(0, 52)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val sz = progress + 44
                    sizeValueText.text = "Размер кнопки: $sz dp"
                    PrefsManager.setButtonSizeDp(this@MainActivity, sz)
                    notifyOverlayRefresh()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        styleCard.addView(sizeSeek)

        root.addView(styleCard, cardMarginParams())

        // Главная кнопка запуска / остановки виджета
        toggleOverlayBtn = Button(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setOnClickListener {
                PrefsManager.setTargetPackage(this@MainActivity, targetPkgInput.text.toString())
                if (OverlayWidgetService.isRunning) {
                    stopOverlayService()
                } else {
                    startOverlayWithPermissions()
                }
            }
        }
        root.addView(toggleOverlayBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun startOverlayWithPermissions() {
        // 1. Проверяем разрешение рисовать поверх других окон
        if (!Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("Разрешение «Поверх других окон»")
                .setMessage("Чтобы круглая кнопка управления отображалась поверх экрана, включите разрешение для «System Battery Saver» на следующем экране.")
                .setPositiveButton("Открыть настройки") { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent, REQ_OVERLAY_PERMISSION)
                }
                .setNegativeButton("Отмена", null)
                .show()
            return
        }

        // 2. Проверяем системное разрешение на запуск локального VPN-фильтра
        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            @Suppress("DEPRECATION")
            startActivityForResult(vpnIntent, REQ_VPN_PERMISSION)
            return
        }

        // 3. Всё готово — запускаем плавающую кнопку
        launchOverlayService()
    }

    private fun launchOverlayService() {
        val intent = Intent(this, OverlayWidgetService::class.java).apply {
            action = OverlayWidgetService.ACTION_SHOW_WIDGET
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "Плавающая кнопка запущена! Можете свернуть программу.", Toast.LENGTH_LONG).show()
        refreshStatusBanner()
    }

    private fun stopOverlayService() {
        val intent = Intent(this, OverlayWidgetService::class.java).apply {
            action = OverlayWidgetService.ACTION_HIDE_WIDGET
        }
        startService(intent)
        refreshStatusBanner()
    }

    private fun notifyOverlayRefresh() {
        if (OverlayWidgetService.isRunning) {
            val intent = Intent(this, OverlayWidgetService::class.java).apply {
                action = OverlayWidgetService.ACTION_REFRESH_STYLE
            }
            startService(intent)
        }
    }

    private fun refreshStatusBanner() {
        val running = OverlayWidgetService.isRunning
        val bg = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(if (running) Color.parseColor("#1B5E20") else Color.parseColor("#263238"))
        }
        statusBannerText.background = bg
        statusBannerText.text = if (running) {
            "● Статус: Плавающая кнопка АКТИВНА на экране"
        } else {
            "○ Статус: Кнопка выключена"
        }

        val btnBg = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(if (running) Color.parseColor("#C62828") else Color.parseColor("#1565C0"))
        }
        toggleOverlayBtn.background = btnBg
        toggleOverlayBtn.text = if (running) {
            "ВЫКЛЮЧИТЬ ПЛАВАЮЩУЮ КНОПКУ"
        } else {
            "ВКЛЮЧИТЬ ПЛАВАЮЩУЮ КНОПКУ"
        }
    }

    private fun showInstalledAppsDialog() {
        val pm = packageManager
        val apps = pm.getInstalledApplications(0)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || it.packageName.contains("yandex") }
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }

        val labels = apps.map { "${pm.getApplicationLabel(it)}\n(${it.packageName})" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Выберите приложение")
            .setItems(labels) { _, which ->
                val chosenPkg = apps[which].packageName
                targetPkgInput.setText(chosenPkg)
                PrefsManager.setTargetPackage(this, chosenPkg)
            }
            .setNegativeButton("Закрыть", null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_OVERLAY_PERMISSION) {
            if (Settings.canDrawOverlays(this)) {
                startOverlayWithPermissions()
            } else {
                Toast.makeText(this, "Необходимо выдать разрешение «Поверх других окон»", Toast.LENGTH_SHORT).show()
            }
        } else if (requestCode == REQ_VPN_PERMISSION) {
            if (resultCode == Activity.RESULT_OK) {
                launchOverlayService()
            } else {
                Toast.makeText(this, "Разрешение на локальный фильтр не выдано", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createCardLayout(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor("#1E2430"))
                setStroke(dp(1), Color.parseColor("#2E3748"))
            }
        }
    }

    private fun cardMarginParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(16) }

    private fun createSectionTitle(text: String): TextView =
        TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
        }

    private fun createHelperText(text: String): TextView =
        TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor("#9AA5B4"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(4), 0, dp(6))
        }

    private fun createSecondaryButton(title: String): Button =
        Button(this).apply {
            text = title
            isAllCaps = false
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#2D3748"))
            }
        }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
}
