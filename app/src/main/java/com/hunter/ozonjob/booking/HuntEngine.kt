package com.hunter.ozonjob.booking

import com.hunter.ozonjob.data.OzonJobRepository
import com.hunter.ozonjob.data.ShiftSlot
import com.hunter.ozonjob.data.api.ApiConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class HuntConfig(
    val date: LocalDate,
    val durationHours: Int,
    /** Желаемое время начала смены. Пусто — берём любую подходящую смену. */
    val startTimes: Set<LocalTime>,
    /** Час, когда сервер выкладывает горячие слоты (обычно 6 утра). */
    val hotHour: Int,
    val hotMinute: Int = 0
)

sealed interface HuntState {
    data object Idle : HuntState
    data class Waiting(val target: LocalDateTime, val config: HuntConfig) : HuntState
    data class Polling(val config: HuntConfig, val attempts: Long) : HuntState
    data class Booked(val slot: ShiftSlot) : HuntState
    data class Failed(val reason: String) : HuntState
}

class HuntEngine(
    private val scope: CoroutineScope,
    private val repository: OzonJobRepository
) {

    private val _state = MutableStateFlow<HuntState>(HuntState.Idle)
    val state: StateFlow<HuntState> = _state

    private var job: Job? = null

    fun isRunning(): Boolean = job?.isActive == true

    fun start(config: HuntConfig) {
        stop()
        job = scope.launch { run(config) }
    }

    fun stop() {
        job?.cancel()
        job = null
        if (_state.value != HuntState.Idle && _state.value !is HuntState.Booked) {
            _state.value = HuntState.Idle
        }
    }

    private suspend fun run(config: HuntConfig) {
        val target = LocalDateTime.of(config.date, LocalTime.of(config.hotHour, config.hotMinute))
        _state.value = HuntState.Waiting(target, config)

        val now = LocalDateTime.now()
        val preArmAt = target.minusNanos(ApiConfig.PRE_ARM_MS * 1_000_000)

        if (now.isBefore(preArmAt)) {
            while (LocalDateTime.now().isBefore(preArmAt)) {
                delay(500)
            }
        }

        _state.value = HuntState.Polling(config, 0)
        val huntEnd = target.plusMinutes(ApiConfig.HUNT_DURATION_MINUTES.toLong())
        val fastPollEnd = target.plusMinutes(ApiConfig.FAST_POLL_MINUTES)
        var attempts = 0L

        while (LocalDateTime.now().isBefore(huntEnd)) {
            attempts++
            _state.value = HuntState.Polling(config, attempts)
            val result = repository.slots(config.date, config.durationHours)
            val slots = result.getOrNull().orEmpty()
            val wanted = matchSlots(slots, config)
            for (slot in wanted) {
                val booked = repository.book(slot)
                if (booked.isSuccess) {
                    _state.value = HuntState.Booked(slot)
                    return
                }
            }
            val interval = if (LocalDateTime.now().isBefore(fastPollEnd)) {
                ApiConfig.POLL_INTERVAL_FAST_MS
            } else {
                ApiConfig.POLL_INTERVAL_SLOW_MS
            }
            delay(interval)
        }
        _state.value = HuntState.Failed(
            "Не дождались слотов до ${huntEnd.hour}:%02d".format(huntEnd.minute)
        )
    }

    private fun matchSlots(slots: List<ShiftSlot>, config: HuntConfig): List<ShiftSlot> {
        return slots.filter { slot ->
            slot.available &&
                (config.startTimes.isEmpty() || slot.startLocal(config.date).toLocalTime() in config.startTimes)
        }
    }
}
