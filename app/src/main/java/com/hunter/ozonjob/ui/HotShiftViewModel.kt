package com.hunter.ozonjob.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hunter.ozonjob.Locator
import com.hunter.ozonjob.booking.HuntConfig
import com.hunter.ozonjob.booking.HuntService
import com.hunter.ozonjob.booking.HuntState
import com.hunter.ozonjob.data.DaySlots
import com.hunter.ozonjob.data.ShiftSlot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

class HotShiftViewModel(app: Application) : AndroidViewModel(app) {

    private val tokenStore = Locator.tokenStore
    private val repository = Locator.repository

    val huntState: StateFlow<HuntState> = Locator.huntEngine.state

    // ------------------ Авторизация ------------------

    private val _phone = MutableStateFlow(tokenStore.phone)
    val phone: StateFlow<String> = _phone

    private val _code = MutableStateFlow("")
    val code: StateFlow<String> = _code

    private val _authorized = MutableStateFlow(tokenStore.isAuthorized)
    val authorized: StateFlow<Boolean> = _authorized

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _sendingSms = MutableStateFlow(false)
    val sendingSms: StateFlow<Boolean> = _sendingSms

    fun onPhoneChange(value: String) {
        _phone.value = value.filter { it.isDigit() }.take(11)
    }

    fun onCodeChange(value: String) {
        _code.value = value.filter { it.isDigit() }.take(6)
    }

    fun sendSms() {
        if (_phone.value.length < 10) {
            _message.value = "Введите полный номер телефона"
            return
        }
        viewModelScope.launch {
            _sendingSms.value = true
            val result = repository.sendCode(_phone.value)
            _sendingSms.value = false
            _message.value = result.fold(
                onSuccess = { "Код отправлен по SMS" },
                onFailure = { "Ошибка: ${it.message} (проверьте ApiConfig)" }
            )
        }
    }

    fun verifyCode() {
        if (_code.value.length < 4) {
            _message.value = "Введите код из SMS"
            return
        }
        viewModelScope.launch {
            val result = repository.verifyCode(_phone.value, _code.value)
            _message.value = result.fold(
                onSuccess = { null },
                onFailure = { "Ошибка: ${it.message}" }
            )
            _authorized.value = result.isSuccess
        }
    }

    fun dismissMessage() {
        _message.value = null
    }

    // ------------------ Смены ------------------

    private val _durationHours = MutableStateFlow(tokenStore.durationHours)
    val durationHours: StateFlow<Int> = _durationHours

    private val _selectedDay = MutableStateFlow(LocalDate.now())
    val selectedDay: StateFlow<LocalDate> = _selectedDay

    private val _days = MutableStateFlow(LocalDate.now().let { start ->
        (0 until 14).map { start.plusDays(it.toLong()) }
    })
    val days: StateFlow<List<LocalDate>> = _days

    private val _daySlots = MutableStateFlow<DaySlots?>(null)
    val daySlots: StateFlow<DaySlots?> = _daySlots

    private val _loadingSlots = MutableStateFlow(false)
    val loadingSlots: StateFlow<Boolean> = _loadingSlots

    private val _selectedStartTimes = MutableStateFlow<Set<LocalTime>>(emptySet())
    val selectedStartTimes: StateFlow<Set<LocalTime>> = _selectedStartTimes

    private val _bookingSlot = MutableStateFlow<String?>(null)
    val bookingSlot: StateFlow<String?> = _bookingSlot

    fun onDurationChange(hours: Int) {
        _durationHours.value = hours
        tokenStore.durationHours = hours
        loadSlots()
    }

    fun onDayChange(day: LocalDate) {
        _selectedDay.value = day
        loadSlots()
    }

    fun toggleStartTime(time: LocalTime) {
        val current = _selectedStartTimes.value
        _selectedStartTimes.value =
            if (time in current) current - time else current + time
    }

    fun loadSlots() {
        viewModelScope.launch {
            _loadingSlots.value = true
            val result = repository.slots(_selectedDay.value, _durationHours.value)
            _loadingSlots.value = false
            result.fold(
                onSuccess = { _daySlots.value = it },
                onFailure = {
                    _message.value = "Не удалось загрузить слоты: ${it.message} (проверьте ApiConfig)"
                }
            )
        }
    }

    fun bookManually(slot: ShiftSlot) {
        viewModelScope.launch {
            _bookingSlot.value = slot.id
            val result = repository.book(slot)
            _bookingSlot.value = null
            result.fold(
                onSuccess = { _message.value = "Смена забронирована" },
                onFailure = { _message.value = "Не удалось забронировать: ${it.message}" }
            )
            loadSlots()
        }
    }

    // ------------------ Горячая бронь ------------------

    private val _hotHour = MutableStateFlow(tokenStore.hotHour)
    val hotHour: StateFlow<Int> = _hotHour

    fun onHotHourChange(hour: Int) {
        _hotHour.value = hour
        tokenStore.hotHour = hour
    }

    fun startHunt() {
        HuntService.start(
            getApplication(),
            HuntConfig(
                date = _selectedDay.value,
                durationHours = _durationHours.value,
                startTimes = _selectedStartTimes.value,
                hotHour = _hotHour.value
            )
        )
    }

    fun stopHunt() {
        HuntService.stop(getApplication())
        Locator.huntEngine.stop()
    }
}
