package com.sysutils.batteryguard

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicBoolean

class LocalSinkholeVpnService : VpnService() {

    companion object {
        const val ACTION_START_BLOCK = "com.sysutils.batteryguard.START_BLOCK"
        const val ACTION_STOP_BLOCK = "com.sysutils.batteryguard.STOP_BLOCK"
        const val ACTION_STATE_CHANGED = "com.sysutils.batteryguard.STATE_CHANGED"

        @Volatile
        var isBlockingActive: Boolean = false
            private set
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val running = AtomicBoolean(false)
    private var drainThread: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_BLOCK -> {
                stopSinkhole()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START_BLOCK, null -> {
                startSinkhole()
            }
        }
        return START_STICKY
    }

    private fun startSinkhole() {
        if (running.get()) return

        val targetPackages = PrefsManager.getTargetPackage(this)
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        try {
            val builder = Builder()
                .setSession("BatterySaverNetSession")
                .setMtu(1500)
                // Локальный виртуальный адрес туннеля
                .addAddress("10.111.222.1", 32)
                // Перехватываем весь IPv4 и IPv6 трафик целевого пакета
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)
                // Направляем DNS-запросы внутрь туннеля, чтобы исключить утечку по DNS
                .addDnsServer("10.111.222.2")

            var addedAnyApp = false
            for (pkg in targetPackages) {
                try {
                    builder.addAllowedApplication(pkg)
                    addedAnyApp = true
                } catch (_: Exception) {
                    // Пакет не установлен на устройстве — пропускаем
                }
            }

            // Если ни один пакет из списка не найден, изолируем только само приложение-утилиту,
            // чтобы не блокировать весь телефон по ошибке
            if (!addedAnyApp) {
                builder.addAllowedApplication(packageName)
            }

            vpnInterface = builder.establish()
            if (vpnInterface != null) {
                running.set(true)
                isBlockingActive = true
                broadcastState()

                // Читаем входящие пакеты из буфера TUN-интерфейса и просто игнорируем их (Blackhole).
                // Благодаря этому сокеты не получают ICMP Reject и ведут себя как при потере радиосигнала.
                drainThread = Thread {
                    val descriptor = vpnInterface ?: return@Thread
                    val input = FileInputStream(descriptor.fileDescriptor)
                    val buffer = ByteArray(32767)
                    try {
                        while (running.get()) {
                            val read = input.read(buffer)
                            if (read < 0) break
                        }
                    } catch (_: Exception) {
                        // Поток закрыт при остановке VPN
                    }
                }.apply {
                    name = "TunSinkholeReader"
                    isDaemon = true
                    start()
                }
            }
        } catch (_: Exception) {
            stopSinkhole()
        }
    }

    private fun stopSinkhole() {
        running.set(false)
        isBlockingActive = false
        try {
            vpnInterface?.close()
        } catch (_: Exception) {
        }
        vpnInterface = null
        drainThread?.interrupt()
        drainThread = null
        broadcastState()
    }

    private fun broadcastState() {
        val intent = Intent(ACTION_STATE_CHANGED).apply {
            setPackage(packageName)
            putExtra("active", isBlockingActive)
        }
        sendBroadcast(intent)
    }

    override fun onRevoke() {
        stopSinkhole()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopSinkhole()
        super.onDestroy()
    }
}
