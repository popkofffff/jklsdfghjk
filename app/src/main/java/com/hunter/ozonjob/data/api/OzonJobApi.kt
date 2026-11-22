package com.hunter.ozonjob.data.api

import com.hunter.ozonjob.data.ShiftSlot
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.Query
import java.time.Duration

// ------------------------ Модели ------------------------
// Имена полей могут отличаться в реальном API; правится только тут.

@Serializable
data class SendCodeRequest(
    @SerialName("phone") val phone: String
)

@Serializable
data class SendCodeResponse(
    val requestId: String? = null,
    val status: String? = null
)

@Serializable
data class VerifyCodeRequest(
    @SerialName("phone") val phone: String,
    @SerialName("code") val code: String,
    @SerialName("requestId") val requestId: String? = null
)

@Serializable
data class VerifyCodeResponse(
    @SerialName("token") val token: String? = null,
    @SerialName("accessToken") val accessToken: String? = null
) {
    fun anyToken(): String? = token ?: accessToken
}

@Serializable
data class BookRequest(
    @SerialName("slotId") val slotId: String,
    @SerialName("durationHours") val durationHours: Int
)

@Serializable
data class BookResponse(
    val status: String? = null,
    val success: Boolean? = null
)

// Слот приходит из API как JSON; парсим терпимо, чтобы изменения схемы не роняли приложение.
@Serializable
data class SlotDto(
    @SerialName("id") val id: String? = null,
    @SerialName("slotId") val slotId: String? = null,
    @SerialName("startTime") val startTime: String? = null,
    @SerialName("durationHours") val durationHours: Int? = null,
    @SerialName("duration") val duration: Int? = null,
    @SerialName("available") val available: Boolean? = null,
    @SerialName("freeSlots") val freeSlots: Int? = null
) {
    fun toDomain(): ShiftSlot? {
        val sid = id ?: slotId ?: return null
        val time = startTime ?: return null
        val hours = durationHours ?: duration ?: 0
        return ShiftSlot(
            id = sid,
            startIso = time,
            durationHours = hours,
            available = available ?: (freeSlots == null || freeSlots > 0)
        )
    }
}

@Serializable
data class SlotsResponse(
    val slots: List<SlotDto> = emptyList(),
    val items: List<SlotDto> = emptyList(),
    val available: List<SlotDto> = emptyList(),
    val data: List<SlotDto> = emptyList()
) {
    fun all(): List<SlotDto> = slots + items + available + data
}

// ------------------------ Retrofit-интерфейс ------------------------

interface OzonJobApi {

    @POST(ApiConfig.PATH_SEND_CODE)
    suspend fun sendCode(@Body body: SendCodeRequest): SendCodeResponse

    @POST(ApiConfig.PATH_VERIFY_CODE)
    suspend fun verifyCode(@Body body: VerifyCodeRequest): VerifyCodeResponse

    @GET(ApiConfig.PATH_SLOTS)
    suspend fun slots(
        @Header("Authorization") token: String,
        @Query("date") date: String,
        @Query("durationHours") durationHours: Int
    ): SlotsResponse

    @POST(ApiConfig.PATH_BOOK)
    suspend fun book(
        @Header("Authorization") token: String,
        @Body body: BookRequest
    ): retrofit2.Response<BookResponse>

    /** Сырой запрос на бронь, если у реального API нестандартная схема тела. */
    @HTTP(method = "POST", path = ApiConfig.PATH_BOOK, hasBody = true)
    suspend fun bookRaw(
        @Header("Authorization") token: String,
        @Body body: JsonObject
    ): retrofit2.Response<JsonObject>
}

object ApiFactory {

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    fun create(): OzonJobApi {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", ApiConfig.USER_AGENT)
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(logging)
            .connectTimeout(Duration.ofSeconds(5))
            .readTimeout(Duration.ofSeconds(5))
            .build()

        return Retrofit.Builder()
            .baseUrl(ApiConfig.HOST)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OzonJobApi::class.java)
    }
}
