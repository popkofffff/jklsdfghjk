package com.hunter.ozonjob.booking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.hunter.ozonjob.Locator
import com.hunter.ozonjob.R
import com.hunter.ozonjob.data.api.ApiConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

class HuntService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        createChannel()
        scope.launch {
            Locator.huntEngine.state.collect { state -> notify(stateText(state)) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Locator.huntEngine.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            ACTION_START -> {
                val date = intent.getStringExtra(EXTRA_DATE)?.let(LocalDate::parse)
                    ?: LocalDate.now()
                val duration = intent.getIntExtra(EXTRA_DURATION, Locator.tokenStore.durationHours)
                val hotHour = intent.getIntExtra(EXTRA_HOT_HOUR, Locator.tokenStore.hotHour)
                val hotMinute = intent.getIntExtra(EXTRA_HOT_MINUTE, 0)
                val startTimes = intent.getStringArrayExtra(EXTRA_START_TIMES)
                    ?.mapNotNull { runCatching { LocalTime.parse(it) }.getOrNull() }
                    ?.toSet()
                    .orEmpty()

                startForeground(NOTIFICATION_ID, buildNotification("Готовим охоту…"))
                Locator.huntEngine.start(
                    HuntConfig(
                        date = date,
                        durationHours = duration,
                        startTimes = startTimes,
                        hotHour = hotHour,
                        hotMinute = hotMinute
                    )
                )
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun stateText(state: HuntState): String = when (state) {
        HuntState.Idle -> "Охота остановлена"
        is HuntState.Waiting ->
            "Ждём ${state.target.hour}:${"%02d".format(state.target.minute)} ${state.config.date}"
        is HuntState.Polling ->
            "Ловим смены до ${state.config.hotHour + ApiConfig.HUNT_DURATION_MINUTES / 60}:%02d… попыток: ${state.attempts}".format(ApiConfig.HUNT_DURATION_MINUTES % 60)
        is HuntState.Booked -> "Смена забронирована: ${state.slot.timeLabel(LocalDate.now())}"
        is HuntState.Failed -> "Не получилось: ${state.reason}"
    }

    private fun notify(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setContentTitle("HotShift")
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .build()

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.hunt_channel),
            NotificationManager.IMPORTANCE_HIGH
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "com.hunter.ozonjob.START_HUNT"
        const val ACTION_STOP = "com.hunter.ozonjob.STOP_HUNT"
        const val EXTRA_DATE = "date"
        const val EXTRA_DURATION = "duration_hours"
        const val EXTRA_HOT_HOUR = "hot_hour"
        const val EXTRA_HOT_MINUTE = "hot_minute"
        const val EXTRA_START_TIMES = "start_times"

        private const val CHANNEL_ID = "hunt"
        private const val NOTIFICATION_ID = 42

        fun start(context: Context, config: HuntConfig) {
            val intent = Intent(context, HuntService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_DATE, config.date.toString())
                putExtra(EXTRA_DURATION, config.durationHours)
                putExtra(EXTRA_HOT_HOUR, config.hotHour)
                putExtra(EXTRA_HOT_MINUTE, config.hotMinute)
                putExtra(EXTRA_START_TIMES, config.startTimes.map { it.toString() }.toTypedArray())
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, HuntService::class.java).apply { action = ACTION_STOP }
            )
        }
    }
}
