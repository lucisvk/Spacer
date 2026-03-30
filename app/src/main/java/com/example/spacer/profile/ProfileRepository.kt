package com.example.spacer.profile

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.example.spacer.network.SupabaseManager
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

@Serializable
data class ProfileRow(
    val id: String,
    val email: String? = null,
    val username: String? = null,
    /** Display name in DB column `name` (legacy docs used `full_name`). */
    @SerialName("name")
    val fullName: String? = null,
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    @SerialName("about_me")
    val aboutMe: String? = null
)

@Serializable
data class UserStatsRow(
    @SerialName("user_id")
    val userId: String,
    @SerialName("hosted_count")
    val hostedCount: Int = 0,
    @SerialName("attended_count")
    val attendedCount: Int = 0,
    @SerialName("friends_count")
    val friendsCount: Int = 0
)

data class ProfileSnapshot(
    val profile: ProfileRow,
    val stats: UserStatsRow
)

@Serializable
data class SearchUserRow(
    val id: String,
    val username: String? = null,
    val email: String? = null,
    @SerialName("name")
    val fullName: String? = null,
    @SerialName("avatar_url")
    val avatarUrl: String? = null
)

@Serializable
data class EventRow(
    val id: String,
    val title: String,
    val description: String? = null,
    @SerialName("host_id")
    val hostId: String,
    @SerialName("starts_at")
    val startsAt: String,
    @SerialName("ends_at")
    val endsAt: String? = null,
    val location: String? = null
)

@Serializable
private data class EventAttendeeRow(
    @SerialName("event_id")
    val eventId: String,
    @SerialName("user_id")
    val userId: String,
    val status: String = "attending"
)

data class FriendListItem(
    val id: String,
    val fullName: String,
    val username: String,
    val avatarUrl: String?
)

@Serializable
private data class FriendRequestInsert(
    @SerialName("sender_id")
    val senderId: String,
    @SerialName("receiver_id")
    val receiverId: String,
    val status: String = "pending"
)

@Serializable
private data class UserBlockInsert(
    @SerialName("blocker_id")
    val blockerId: String,
    @SerialName("blocked_id")
    val blockedId: String
)

@Serializable
private data class UserReportInsert(
    @SerialName("reporter_id")
    val reporterId: String,
    @SerialName("reported_id")
    val reportedId: String,
    val reason: String
)

@Serializable
private data class AccountDeletionRequestInsert(
    @SerialName("user_id")
    val userId: String,
    val reason: String? = null
)

class ProfileRepository {
    private val supabase = SupabaseManager.client

    private fun parseDateSafely(value: String): OffsetDateTime? {
        return try {
            OffsetDateTime.parse(value)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    suspend fun load(): Result<ProfileSnapshot> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))

            val profile = supabase.from("profiles")
                .select {
                    filter { eq("id", user.id) }
                    limit(1)
                }
                .decodeSingle<ProfileRow>()

            val stats = supabase.from("user_stats")
                .select {
                    filter { eq("user_id", user.id) }
                    limit(1)
                }
                .decodeSingle<UserStatsRow>()

            Result.success(ProfileSnapshot(profile = profile, stats = stats))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateProfile(fullName: String, aboutMe: String): Result<Unit> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))

            supabase.from("profiles")
                .update(
                    {
                        set("name", fullName)
                        set("about_me", aboutMe)
                    }
                ) {
                    filter { eq("id", user.id) }
                }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchUsers(query: String): Result<List<SearchUserRow>> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val q = query.trim()
            if (q.isEmpty()) return Result.success(emptyList())
            if (q.length < 1) return Result.success(emptyList())

            val safe = q.replace("%", "").replace("_", "").trim()
            if (safe.isEmpty()) return Result.success(emptyList())
            val pattern = "%$safe%"

            // Separate queries avoid PostgREST `or` + `neq` edge cases and work better with RLS.
            val byUsername = supabase.from("profiles")
                .select {
                    filter {
                        ilike("username", pattern)
                        neq("id", user.id)
                    }
                    limit(40)
                }
                .decodeList<SearchUserRow>()

            val byName = supabase.from("profiles")
                .select {
                    filter {
                        ilike("name", pattern)
                        neq("id", user.id)
                    }
                    limit(40)
                }
                .decodeList<SearchUserRow>()

            val byEmail = runCatching {
                supabase.from("profiles")
                    .select {
                        filter {
                            ilike("email", pattern)
                            neq("id", user.id)
                        }
                        limit(40)
                    }
                    .decodeList<SearchUserRow>()
            }.getOrElse { emptyList() }

            val merged = (byUsername + byName + byEmail)
                .distinctBy { it.id }
                .take(40)

            Result.success(merged)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendFriendRequest(targetUserId: String): Result<Unit> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            if (targetUserId == user.id) {
                return Result.failure(IllegalArgumentException("You can't add yourself"))
            }

            supabase.from("friend_requests").insert(
                FriendRequestInsert(
                    senderId = user.id,
                    receiverId = targetUserId
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun blockUser(targetUserId: String): Result<Unit> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            if (targetUserId == user.id) {
                return Result.failure(IllegalArgumentException("You can't block yourself"))
            }

            supabase.from("user_blocks").insert(
                UserBlockInsert(
                    blockerId = user.id,
                    blockedId = targetUserId
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reportUser(targetUserId: String, reason: String): Result<Unit> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val cleanedReason = reason.trim()
            if (cleanedReason.length < 4) {
                return Result.failure(IllegalArgumentException("Please provide a report reason"))
            }
            if (targetUserId == user.id) {
                return Result.failure(IllegalArgumentException("You can't report yourself"))
            }

            supabase.from("user_reports").insert(
                UserReportInsert(
                    reporterId = user.id,
                    reportedId = targetUserId,
                    reason = cleanedReason
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun requestAccountDeletion(reason: String?): Result<Unit> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            supabase.from("account_deletion_requests").insert(
                AccountDeletionRequestInsert(
                    userId = user.id,
                    reason = reason?.trim()?.ifBlank { null }
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPastHostedEvents(): Result<List<EventRow>> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val now = OffsetDateTime.now()

            val events = supabase.from("app_events")
                .select {
                    filter { eq("host_id", user.id) }
                    order(column = "starts_at", order = io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                }
                .decodeList<EventRow>()
                .filter { event -> parseDateSafely(event.startsAt)?.isBefore(now) == true }

            Result.success(events)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPastAttendedEvents(): Result<List<EventRow>> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            val now = OffsetDateTime.now()

            val attendeeRows = supabase.from("event_attendees")
                .select {
                    filter {
                        eq("user_id", user.id)
                        eq("status", "attending")
                    }
                }
                .decodeList<EventAttendeeRow>()

            val events = attendeeRows.mapNotNull { attendee ->
                runCatching {
                    supabase.from("app_events")
                        .select {
                            filter { eq("id", attendee.eventId) }
                            limit(1)
                        }
                        .decodeSingle<EventRow>()
                }.getOrNull()
            }.filter { event -> parseDateSafely(event.startsAt)?.isBefore(now) == true }
                .sortedByDescending { parseDateSafely(it.startsAt) }

            Result.success(events)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFriends(): Result<List<FriendListItem>> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))

            val outgoing = supabase.from("friend_requests")
                .select {
                    filter {
                        eq("sender_id", user.id)
                        eq("status", "accepted")
                    }
                }
                .decodeList<FriendRequestInsert>()
                .map { it.receiverId }

            val incoming = supabase.from("friend_requests")
                .select {
                    filter {
                        eq("receiver_id", user.id)
                        eq("status", "accepted")
                    }
                }
                .decodeList<FriendRequestInsert>()
                .map { it.senderId }

            val friendIds = (outgoing + incoming).distinct()
            val friends = friendIds.mapNotNull { id ->
                runCatching {
                    val p = supabase.from("profiles")
                        .select {
                            filter { eq("id", id) }
                            limit(1)
                        }
                        .decodeSingle<ProfileRow>()
                    FriendListItem(
                        id = p.id,
                        fullName = p.fullName?.ifBlank { p.username ?: "User" } ?: (p.username ?: "User"),
                        username = p.username ?: "user",
                        avatarUrl = p.avatarUrl
                    )
                }.getOrNull()
            }.sortedBy { it.fullName.lowercase() }

            Result.success(friends)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun unfriend(targetUserId: String): Result<Unit> {
        return try {
            val user = supabase.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Not logged in"))

            supabase.from("friend_requests").delete {
                filter {
                    eq("sender_id", user.id)
                    eq("receiver_id", targetUserId)
                    eq("status", "accepted")
                }
            }

            supabase.from("friend_requests").delete {
                filter {
                    eq("sender_id", targetUserId)
                    eq("receiver_id", user.id)
                    eq("status", "accepted")
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

