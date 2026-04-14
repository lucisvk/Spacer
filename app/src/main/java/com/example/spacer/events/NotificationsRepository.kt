package com.example.spacer.events

import com.example.spacer.network.SupabaseManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserNotificationRow(
    val id: String,
    @SerialName("user_id")
    val userId: String,
    val title: String,
    val body: String,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("read_at")
    val readAt: String? = null
)

class NotificationsRepository {
    private val supabase = SupabaseManager.client

    suspend fun listUnread(): Result<List<UserNotificationRow>> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val rows = supabase.from("user_notifications")
                .select {
                    filter {
                        eq("user_id", user.id)
                    }
                }
                .decodeList<UserNotificationRow>()
                .filter { it.readAt.isNullOrBlank() }
                .sortedByDescending { it.createdAt ?: "" }
            Result.success(rows)
        } catch (e: Exception) {
            Result.success(emptyList())
        }
    }

    suspend fun markRead(notificationId: String): Result<Unit> {
        return try {
            supabase.from("user_notifications").update(
                {
                    set("read_at", java.time.OffsetDateTime.now().toString())
                }
            ) {
                filter { eq("id", notificationId) }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
