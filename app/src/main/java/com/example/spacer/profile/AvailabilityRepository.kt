package com.example.spacer.profile

import com.example.spacer.network.SupabaseManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalTime

@Serializable
private data class AvailabilityPrefsRow(
    @SerialName("user_id") val userId: String,
    @SerialName("calendar_provider") val calendarProvider: String? = null,
    @SerialName("calendar_connected") val calendarConnected: Boolean = false,
    @SerialName("show_to_friends_only") val showToFriendsOnly: Boolean = true,
    @SerialName("auto_decline_conflicts") val autoDeclineConflicts: Boolean = false
)

@Serializable
private data class AvailabilityPrefsUpsert(
    @SerialName("user_id") val userId: String,
    @SerialName("calendar_provider") val calendarProvider: String? = null,
    @SerialName("calendar_connected") val calendarConnected: Boolean = false,
    @SerialName("show_to_friends_only") val showToFriendsOnly: Boolean = true,
    @SerialName("auto_decline_conflicts") val autoDeclineConflicts: Boolean = false
)

@Serializable
private data class WeeklyWindowRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("day_of_week") val dayOfWeek: Int,
    @SerialName("starts_at") val startsAt: String,
    @SerialName("ends_at") val endsAt: String
)

@Serializable
private data class WeeklyWindowInsert(
    @SerialName("user_id") val userId: String,
    @SerialName("day_of_week") val dayOfWeek: Int,
    @SerialName("starts_at") val startsAt: String,
    @SerialName("ends_at") val endsAt: String
)

@Serializable
private data class SpecificAvailabilityRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("starts_at") val startsAt: String,
    @SerialName("ends_at") val endsAt: String,
    @SerialName("is_available") val isAvailable: Boolean,
    val note: String? = null
)

@Serializable
private data class SpecificAvailabilityInsert(
    @SerialName("user_id") val userId: String,
    @SerialName("starts_at") val startsAt: String,
    @SerialName("ends_at") val endsAt: String,
    @SerialName("is_available") val isAvailable: Boolean,
    val note: String? = null
)

data class AvailabilityPreferencesUi(
    val calendarProvider: String?,
    val calendarConnected: Boolean,
    val showToFriendsOnly: Boolean,
    val autoDeclineConflicts: Boolean
)

data class WeeklyAvailabilityWindowUi(
    val id: String,
    val dayOfWeek: Int,
    val startsAt: String,
    val endsAt: String
)

data class SpecificAvailabilityWindowUi(
    val id: String,
    val startsAt: String,
    val endsAt: String,
    val isAvailable: Boolean,
    val note: String?
)

class AvailabilityRepository {
    private val supabase = SupabaseManager.client

    suspend fun loadPreferences(): Result<AvailabilityPreferencesUi> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val row = supabase.from("user_availability_preferences")
                .select {
                    filter { eq("user_id", user.id) }
                    limit(1)
                }
                .decodeList<AvailabilityPrefsRow>()
                .firstOrNull()
            Result.success(
                AvailabilityPreferencesUi(
                    calendarProvider = row?.calendarProvider,
                    calendarConnected = row?.calendarConnected ?: false,
                    showToFriendsOnly = row?.showToFriendsOnly ?: true,
                    autoDeclineConflicts = row?.autoDeclineConflicts ?: false
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun savePreferences(
        calendarProvider: String?,
        calendarConnected: Boolean,
        showToFriendsOnly: Boolean,
        autoDeclineConflicts: Boolean
    ): Result<Unit> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            supabase.from("user_availability_preferences").upsert(
                AvailabilityPrefsUpsert(
                    userId = user.id,
                    calendarProvider = calendarProvider,
                    calendarConnected = calendarConnected,
                    showToFriendsOnly = showToFriendsOnly,
                    autoDeclineConflicts = autoDeclineConflicts
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listWeeklyWindows(): Result<List<WeeklyAvailabilityWindowUi>> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val rows = supabase.from("user_weekly_availability_windows")
                .select {
                    filter { eq("user_id", user.id) }
                    order(column = "day_of_week", order = Order.ASCENDING)
                }
                .decodeList<WeeklyWindowRow>()
            Result.success(
                rows.map {
                    WeeklyAvailabilityWindowUi(
                        id = it.id,
                        dayOfWeek = it.dayOfWeek,
                        startsAt = it.startsAt,
                        endsAt = it.endsAt
                    )
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addWeeklyWindow(dayOfWeek: Int, startsAt: LocalTime, endsAt: LocalTime): Result<Unit> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            supabase.from("user_weekly_availability_windows").insert(
                WeeklyWindowInsert(
                    userId = user.id,
                    dayOfWeek = dayOfWeek,
                    startsAt = startsAt.toString(),
                    endsAt = endsAt.toString()
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeWeeklyWindow(windowId: String): Result<Unit> {
        return try {
            supabase.from("user_weekly_availability_windows").delete {
                filter { eq("id", windowId) }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listSpecificAvailability(): Result<List<SpecificAvailabilityWindowUi>> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val rows = supabase.from("user_specific_availability")
                .select {
                    filter { eq("user_id", user.id) }
                    order(column = "starts_at", order = Order.ASCENDING)
                }
                .decodeList<SpecificAvailabilityRow>()
            Result.success(
                rows.map {
                    SpecificAvailabilityWindowUi(
                        id = it.id,
                        startsAt = it.startsAt,
                        endsAt = it.endsAt,
                        isAvailable = it.isAvailable,
                        note = it.note
                    )
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addSpecificAvailability(
        startsAt: Instant,
        endsAt: Instant,
        isAvailable: Boolean,
        note: String? = null
    ): Result<Unit> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            supabase.from("user_specific_availability").insert(
                SpecificAvailabilityInsert(
                    userId = user.id,
                    startsAt = startsAt.toString(),
                    endsAt = endsAt.toString(),
                    isAvailable = isAvailable,
                    note = note?.trim()?.ifBlank { null }
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeSpecificAvailability(windowId: String): Result<Unit> {
        return try {
            supabase.from("user_specific_availability").delete {
                filter { eq("id", windowId) }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
