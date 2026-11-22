package com.hunter.ozonjob.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hunter.ozonjob.booking.HuntState
import com.hunter.ozonjob.data.ShiftSlot
import com.hunter.ozonjob.data.api.ApiConfig
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(viewModel: HotShiftViewModel) {
    val authorized by viewModel.authorized.collectAsState()
    val message by viewModel.message.collectAsState()
    val huntState by viewModel.huntState.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }
    LaunchedEffect(huntState) {
        if (huntState is HuntState.Booked) snackbar.showSnackbar("Смена забронирована")
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        if (authorized) {
            BookingScreen(viewModel, Modifier.padding(padding))
        } else {
            LoginScreen(viewModel, Modifier.padding(padding))
        }
    }
}

// ------------------ Вход ------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginScreen(viewModel: HotShiftViewModel, modifier: Modifier = Modifier) {
    val phone by viewModel.phone.collectAsState()
    val code by viewModel.code.collectAsState()
    val sendingSms by viewModel.sendingSms.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("HotShift", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Ловим горячие смены на Ozon Job",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = phone,
            onValueChange = viewModel::onPhoneChange,
            label = { Text("Телефон") },
            prefix = { Text("+7") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = code,
            onValueChange = viewModel::onCodeChange,
            label = { Text("Код из SMS") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = viewModel::sendSms,
                enabled = !sendingSms,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (sendingSms) "Отправляем…" else "Получить код")
            }
            Button(
                onClick = viewModel::verifyCode,
                modifier = Modifier.weight(1f)
            ) {
                Text("Войти")
            }
        }

        if (sendingSms) {
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator()
        }
    }
}

// ------------------ Выбор смены и охота ------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookingScreen(viewModel: HotShiftViewModel, modifier: Modifier = Modifier) {
    val days by viewModel.days.collectAsState()
    val selectedDay by viewModel.selectedDay.collectAsState()
    val duration by viewModel.durationHours.collectAsState()
    val daySlots by viewModel.daySlots.collectAsState()
    val loadingSlots by viewModel.loadingSlots.collectAsState()
    val selectedStartTimes by viewModel.selectedStartTimes.collectAsState()
    val bookingSlot by viewModel.bookingSlot.collectAsState()
    val huntState by viewModel.huntState.collectAsState()
    val hotHour by viewModel.hotHour.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadSlots() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Выберите день", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(days) { day ->
                    FilterChip(
                        selected = day == selectedDay,
                        onClick = { viewModel.onDayChange(day) },
                        label = { Text(dayLabel(day)) }
                    )
                }
            }
        }

        item {
            Text("На какое время", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(4, 6, 8, 12).forEach { hours ->
                    FilterChip(
                        selected = hours == duration,
                        onClick = { viewModel.onDurationChange(hours) },
                        label = { Text("%d ч".format(hours)) }
                    )
                }
            }
        }

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(onClick = viewModel::loadSlots, enabled = !loadingSlots) {
                    Text("Обновить")
                }
                if (loadingSlots) CircularProgressIndicator(Modifier.height(24.dp))
            }
        }

        item {
            Text("Слоты", style = MaterialTheme.typography.titleMedium)
        }

        val slots = daySlots?.slots.orEmpty()
        if (!loadingSlots && slots.isEmpty()) {
            item {
                Text(
                    "Свободных слотов нет\n(или ещё не настроен ApiConfig — см. README)",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        items(slots, key = { it.id }) { slot ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(slot.timeLabel(selectedDay), fontWeight = FontWeight.SemiBold)
                        Text(
                            if (slot.available) "Свободен" else "Занят",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (slot.available) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { viewModel.bookManually(slot) },
                        enabled = slot.available && bookingSlot != slot.id
                    ) {
                        Text(if (bookingSlot == slot.id) "…" else "Забронировать")
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text("Горячая бронь", style = MaterialTheme.typography.titleMedium)
            Text(
                "Слоты появляются около %02d:00. Выберите нужное время начала и включите " +
                    "охоту: приложение начнёт опрос заранее и мгновенно забронирует первый подходящий слот."
                        .format(hotHour),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { viewModel.onHotHourChange((hotHour + 23) % 24) },
                    label = { Text("−") }
                )
                AssistChip(
                    onClick = { },
                    label = { Text("%02d:00".format(hotHour)) }
                )
                AssistChip(
                    onClick = { viewModel.onHotHourChange((hotHour + 1) % 24) },
                    label = { Text("+") }
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("Предпочтительное время начала:", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                distinctStartTimes(slots, selectedDay).forEach { time ->
                    FilterChip(
                        selected = time in selectedStartTimes,
                        onClick = { viewModel.toggleStartTime(time) },
                        label = { Text(time.format(DateTimeFormatter.ofPattern("HH:mm"))) }
                    )
                }
            }
            if (selectedStartTimes.isEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Ничего не выбрано — охотимся за любым свободным слотом",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        item {
            HuntCard(
                huntState = huntState,
                onStart = viewModel::startHunt,
                onStop = viewModel::stopHunt
            )
        }
    }
}

@Composable
private fun HuntCard(huntState: HuntState, onStart: () -> Unit, onStop: () -> Unit) {
    val running = huntState !is HuntState.Idle
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                when (val state = huntState) {
                    HuntState.Idle -> "Охота выключена"
                    is HuntState.Waiting ->
                        "Ждём %02d:%02d %s".format(state.target.hour, state.target.minute, state.config.date)
                    is HuntState.Polling ->
                        "Ловим смены до ${state.config.hotHour + ApiConfig.HUNT_DURATION_MINUTES / 60}:%02d… попыток: ${state.attempts}"
                            .format(ApiConfig.HUNT_DURATION_MINUTES % 60)
                    is HuntState.Booked -> "Забронировано: ${state.slot.id}"
                    is HuntState.Failed -> "Не получилось: ${state.reason}"
                },
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onStart, enabled = !running, modifier = Modifier.weight(1f)) {
                    Text("Встать на охоту")
                }
                Button(onClick = onStop, enabled = running, modifier = Modifier.weight(1f)) {
                    Text("Остановить")
                }
            }
        }
    }
}

private fun dayLabel(day: LocalDate): String {
    val date = day.format(DateTimeFormatter.ofPattern("dd.MM"))
    val weekday = day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("ru"))
    return "$weekday $date"
}

private fun distinctStartTimes(
    slots: List<ShiftSlot>,
    fallbackDate: LocalDate
): List<LocalTime> =
    slots.map { it.startLocal(fallbackDate).toLocalTime() }.distinct().sorted()
