package com.hunter.ozonjob.data

import com.hunter.ozonjob.data.api.ApiConfig
import com.hunter.ozonjob.data.api.ApiFactory
import com.hunter.ozonjob.data.api.BookRequest
import com.hunter.ozonjob.data.api.SendCodeRequest
import com.hunter.ozonjob.data.api.SlotDto
import com.hunter.ozonjob.data.api.VerifyCodeRequest
import com.hunter.ozonjob.data.prefs.TokenStore
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class OzonJobRepository(
    private val tokenStore: TokenStore,
    private val api: com.hunter.ozonjob.data.api.OzonJobApi = ApiFactory.create()
) {

    private var pendingRequestId: String? = null

    suspend fun sendCode(phoneDigits: String): Result<String?> = runCatching {
        val response = api.sendCode(SendCodeRequest(phone = normalizePhone(phoneDigits)))
        pendingRequestId = response.requestId
        response.requestId
    }

    suspend fun verifyCode(phoneDigits: String, code: String): Result<String> = runCatching {
        val verifier = api.verifyCode(
            VerifyCodeRequest(
                phone = normalizePhone(phoneDigits),
                code = code,
                requestId = pendingRequestId
            )
        )
        val token = verifier.anyToken()
            ?: error("Сервер не вернул токен — проверьте PATH_VERIFY_CODE в ApiConfig")
        tokenStore.token = token
        tokenStore.phone = normalizePhone(phoneDigits)
        token
    }

    suspend fun slots(date: LocalDate, durationHours: Int): Result<DaySlots> = runCatching {
        val token = tokenStore.token
        val response = api.slots(
            token = bearer(token),
            date = date.format(DateTimeFormatter.ISO_LOCAL_DATE),
            durationHours = durationHours
        )
        DaySlots(
            date = date,
            durationHours = durationHours,
            slots = response.all().mapNotNull(SlotDto::toDomain).distinctBy { it.id }
        )
    }

    suspend fun book(slot: ShiftSlot): Result<Unit> {
        val failedStatuses = setOf("failed", "error", "full", "unavailable")
        repeat(ApiConfig.BOOK_RETRIES) { attempt ->
            val attemptResult = runCatching {
                val response = api.book(
                    token = bearer(tokenStore.token),
                    body = BookRequest(slotId = slot.id, durationHours = slot.durationHours)
                )
                if (response.isSuccessful) {
                    val bodyStatus = response.body()?.status?.lowercase()
                    check(bodyStatus !in failedStatuses) { "Бронь не принята: $bodyStatus" }
                    Unit
                } else {
                    error("HTTP ${response.code()}")
                }
            }
            if (attemptResult.isSuccess) return attemptResult
            if (attempt == ApiConfig.BOOK_RETRIES - 1) return attemptResult
            delay(ApiConfig.BOOK_RETRY_DELAY_MS)
        }
        return Result.failure(IllegalStateException("Не удалось забронировать"))
    }

    private fun bearer(token: String) =
        if (token.startsWith("Bearer", ignoreCase = true)) token else "Bearer $token"

    private fun normalizePhone(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.length == 11 && digits.startsWith("8") -> "+7" + digits.substring(1)
            digits.length == 11 && digits.startsWith("7") -> "+$digits"
            digits.length == 10 -> "+7$digits"
            else -> "+$digits"
        }
    }
}
